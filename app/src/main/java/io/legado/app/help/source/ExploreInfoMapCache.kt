package io.legado.app.help.source

import androidx.collection.LruCache
import io.legado.app.utils.InfoMap

/**
 * 发现页按钮信息（infoMap）的进程内缓存。
 *
 * 同一份缓存在三处共享：发现页 UI（写入用户输入与 toggle/select 的当前项）、书源发现规则
 * （[exploreKinds] 求值 `@js:` 发现链接时读取）、发现书单请求（`WebBook` 把 infoMap 传给规则），
 * 因此它是"规则层也依赖的数据"，放在 `help/source` 而不是 UI 包——UI 包不该成为规则层的依赖来源。
 *
 * 原先挂在 `ExploreAdapter.Companion` 下，发现页 Compose 化后 Adapter 不再存在，故上移。
 */
val exploreInfoMapList = LruCache<String, InfoMap>(99)

/**
 * 取出（必要时新建）某个书源的 infoMap。
 *
 * 容量上限由 [exploreInfoMapList] 兜底，调用方不需要自己判断是否已存在。
 */
fun obtainExploreInfoMap(sourceUrl: String): InfoMap {
    return exploreInfoMapList[sourceUrl] ?: InfoMap(sourceUrl).also {
        exploreInfoMapList.put(sourceUrl, it)
    }
}
