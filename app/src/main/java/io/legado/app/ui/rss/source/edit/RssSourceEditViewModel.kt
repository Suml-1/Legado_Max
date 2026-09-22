package io.legado.app.ui.rss.source.edit

import android.app.Application
import android.content.Intent
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppCacheManager
import io.legado.app.help.ConcurrentRateLimiter.Companion.concurrentRecordMap
import io.legado.app.help.RuleComplete
import io.legado.app.help.http.CookieStore
import io.legado.app.help.source.removeSortCache
import io.legado.app.model.SharedJsScope
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers


class RssSourceEditViewModel(application: Application) : BaseViewModel(application) {
    var autoComplete = false
    var rssSource: RssSource? = null

    /**
     * 加载待编辑的订阅源并回调界面。
     *
     * 用 executeLazy + start 保证回调先挂后跑：onFinally 一旦漏执行，界面不会绑定数据（空白页），
     * 退出时还会误判为"已保存过"而误报 RESULT_OK。
     */
    fun initData(intent: Intent, onFinally: () -> Unit) {
        executeLazy {
            val key = intent.getStringExtra("sourceUrl")
            if (key != null) {
                appDb.rssSourceDao.getByKey(key)?.let {
                    rssSource = it
                }
            }
        }.onFinally {
            onFinally()
        }.start()
    }

    /**
     * 保存订阅源。
     *
     * 用 executeLazy（LAZY 启动）挂完回调再 start：链式协程的回调依赖"任务还没完成时已挂上回调"
     * 这一时序，回调一旦漏掉，调用方（订阅源列表/阅读页/排序页）就拿不到保存结果，
     * 表现为"保存了但没生效、要再保存一次"。
     */
    fun save(
        source: RssSource,
        finally: (() -> Unit)? = null,
        success: ((RssSource) -> Unit)
    ) {
        executeLazy {
            if (source.sourceUrl.isBlank() || source.sourceName.isBlank()) {
                throw NoStackTraceException(context.getString(R.string.non_null_name_url))
            }
            val oldSource = rssSource ?: RssSource()
            if (!source.equal(oldSource)) {
                source.lastUpdateTime = System.currentTimeMillis()
                if (oldSource.sortUrl != source.sortUrl) {
                    oldSource.removeSortCache()
                }
                if (oldSource.jsLib != source.jsLib) {
                    SharedJsScope.remove(oldSource.jsLib)
                }
            }
            rssSource?.let {
                appDb.rssSourceDao.delete(it)
                //更新收藏的源地址
                if (it.sourceUrl != source.sourceUrl) {
                    appDb.rssStarDao.updateOrigin(source.sourceUrl, it.sourceUrl)
                    appDb.rssArticleDao.updateOrigin(source.sourceUrl, it.sourceUrl)
                    appDb.cacheDao.deleteSourceVariables(it.sourceUrl)
                    AppCacheManager.clearSourceVariables()
                }
            }
            appDb.rssSourceDao.insert(source)
            rssSource = source
            concurrentRecordMap.remove(source.sourceUrl) // 删除并发限制缓存
            source
        }.onSuccess {
            success(it)
        }.onError {
            context.toastOnUi(it.localizedMessage)
            it.printOnDebug()
        }.onFinally {
            finally?.invoke()
        }.start()
    }

    fun pasteSource(onSuccess: (source: RssSource) -> Unit) {
        execute(context = Dispatchers.Main) {
            var source: RssSource? = null
            context.getClipText()?.let { json ->
                source = GSON.fromJsonObject<RssSource>(json).getOrThrow()
            }
            source
        }.onError {
            context.toastOnUi(it.localizedMessage)
        }.onSuccess {
            if (it != null) {
                onSuccess(it)
            } else {
                context.toastOnUi("格式不对")
            }
        }
    }

    fun importSource(text: String, finally: (source: RssSource) -> Unit) {
        execute {
            val text1 = text.trim()
            GSON.fromJsonObject<RssSource>(text1).getOrThrow().let {
                finally.invoke(it)
            }
        }.onError {
            context.toastOnUi(it.stackTraceStr)
        }
    }

    fun clearCookie(url: String) {
        execute {
            CookieStore.removeCookie(url)
        }
    }

    fun ruleComplete(rule: String?, preRule: String? = null, type: Int = 1): String? {
        if (autoComplete) {
            return RuleComplete.autoComplete(rule, preRule, type)
        }
        return rule
    }

}