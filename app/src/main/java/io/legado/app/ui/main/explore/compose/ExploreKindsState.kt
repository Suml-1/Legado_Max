package io.legado.app.ui.main.explore.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.rule.ExploreKind

/**
 * 单个展开书源的分类区状态（加载中标志 + 分类列表）。
 *
 * 求值要跑书源规则（`@js:` 源还会发网络请求），所以折叠状态下不会触发加载，
 * 只有条目展开时才拉取；`refreshTick` 变化时（长按菜单刷新 / 登录后规则回调）才清缓存重建。
 */
@Stable
internal class ExploreKindsState {

    var loading by mutableStateOf(false)
        private set

    var kinds by mutableStateOf<List<ExploreKind>>(emptyList())
        private set

    /** 已处理过的重建信号；信号没变时按普通加载走，命中 `exploreKinds()` 的进程内缓存 */
    private var lastHandledRefreshTick = 0

    suspend fun load(controller: ExploreKindsController, sourceUrl: String, refreshTick: Int) {
        val forceRefresh = refreshTick != lastHandledRefreshTick
        lastHandledRefreshTick = refreshTick
        loading = true
        try {
            kinds = runCatching {
                if (forceRefresh) {
                    controller.reloadKinds(sourceUrl)
                } else {
                    controller.loadKinds(sourceUrl)
                }
            }.getOrElse { e ->
                AppLog.put("发现界面加载书源分类出错: $sourceUrl", e)
                emptyList()
            }
        } finally {
            loading = false
        }
    }
}
