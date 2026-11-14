package io.legado.app.help.http

import io.legado.app.help.CacheManager
import io.legado.app.help.http.api.CookieManagerInterface
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.splitNotBlank

/**
 * 定时任务专属的 cookie 门面：任务的 `cookie.getCookie/saveCookie` 等操作按任务作用域
 * （scope = 虚拟源 key，即 `auto_task:{任务id}`）分桶存储，与书源/订阅源共享的
 * 全局域名 cookie 空间互不可见；删除任务时按作用域整体清理。
 *
 * 存储直接落在 [CacheManager]（持久化），键清单记录在 `autoTaskCookieKeys_{scope}` 下，
 * 以支持删除任务时枚举清理。
 */
class TaskCookieStore(private val scope: String) : CookieManagerInterface {

    private val keysIndexKey = "autoTaskCookieKeys_${MD5Utils.md5Encode16(scope)}"

    private fun storageKey(url: String): String {
        val domain = NetworkUtils.getSubDomain(url).ifBlank {
            MD5Utils.md5Encode16(url)
        }
        return "autoTaskCookie_${MD5Utils.md5Encode16(scope)}_$domain"
    }

    private fun rememberKey(key: String) {
        val keys = CacheManager.get(keysIndexKey)
            ?.splitNotBlank(",")
            ?.toMutableSet()
            ?: mutableSetOf()
        if (keys.add(key)) {
            CacheManager.put(keysIndexKey, keys.joinToString(","))
        }
    }

    override fun setCookie(url: String, cookie: String?) {
        val key = storageKey(url)
        if (cookie.isNullOrBlank()) {
            CacheManager.delete(key)
        } else {
            CacheManager.put(key, cookie)
            rememberKey(key)
        }
    }

    override fun replaceCookie(url: String, cookie: String) {
        if (cookie.isBlank()) {
            CacheManager.delete(storageKey(url))
            return
        }
        val oldCookie = getCookie(url)
        if (oldCookie.isBlank()) {
            setCookie(url, cookie)
        } else {
            val cookieMap = cookieToMap(oldCookie)
            cookieMap.putAll(cookieToMap(cookie))
            setCookie(url, mapToCookie(cookieMap))
        }
    }

    override fun getCookie(url: String): String {
        return CacheManager.get(storageKey(url)) ?: ""
    }

    override fun removeCookie(url: String) {
        CacheManager.delete(storageKey(url))
    }

    override fun cookieToMap(cookie: String): MutableMap<String, String> {
        return CookieStore.cookieToMap(cookie)
    }

    override fun mapToCookie(cookieMap: Map<String, String>?): String? {
        return CookieStore.mapToCookie(cookieMap)
    }

    companion object {

        /**
         * 删除一个任务作用域名下的全部 cookie（任务删除时调用）
         */
        fun removeScopeCookies(scope: String) {
            val keysIndexKey = "autoTaskCookieKeys_${MD5Utils.md5Encode16(scope)}"
            CacheManager.get(keysIndexKey)
                ?.splitNotBlank(",")
                ?.forEach { CacheManager.delete(it) }
            CacheManager.delete(keysIndexKey)
        }
    }
}
