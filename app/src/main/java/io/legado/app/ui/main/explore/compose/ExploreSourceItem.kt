package io.legado.app.ui.main.explore.compose

import androidx.compose.runtime.Immutable
import io.legado.app.data.entities.BookSourcePart

/**
 * 发现页书源条目模型。
 *
 * 只承载列表渲染与长按菜单需要的字段：排序、搜索、分组过滤仍在 Fragment 侧完成，
 * Compose 侧不做任何数据加工（performance.md §8.1：列表项模型必须 `@Immutable`）。
 */
@Immutable
data class ExploreSourceItem(
    val sourceUrl: String,
    val sourceName: String,
    /** 没有登录地址的书源不显示"登录"菜单项 */
    val hasLoginUrl: Boolean,
)

internal fun List<BookSourcePart>.toExploreSourceItems(): List<ExploreSourceItem> {
    return map { part ->
        ExploreSourceItem(
            sourceUrl = part.bookSourceUrl,
            sourceName = part.bookSourceName,
            hasLoginUrl = part.hasLoginUrl,
        )
    }
}
