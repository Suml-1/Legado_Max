package io.legado.app.ui.main.explore.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.VerticalScrollbar

/**
 * 发现页的书源列表。
 *
 * 只负责渲染与滚动：数据（排序 / 搜索 / 分组过滤后的结果）与展开状态都由 Fragment 下发，
 * 因此在 Compose 侧不做任何排序过滤（performance.md §8.1）。
 */
@Composable
internal fun ExploreSourceList(
    modifier: Modifier = Modifier,
    sourceItems: List<ExploreSourceItem>,
    expandedSourceUrl: String?,
    bottomPaddingPx: Int,
    scrollToTopTick: Int,
    controller: ExploreKindsController,
    actions: ExploreSourceActions,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // 底部 contentPadding = 底栏高度 + 末条目自己的 12dp 下边距（原实现末项保留）
    val bottomPadding = remember(bottomPaddingPx, density) {
        with(density) { bottomPaddingPx.toDp() + AppDimens.exploreRowBottomPadding }
    }

    // 展开后把该行滚到列表顶部，对齐原实现的 scrollToPositionWithOffset(pos, 0)：
    // 分类区是往上长出来的，不滚上去会被底栏挡住
    LaunchedEffect(expandedSourceUrl) {
        if (expandedSourceUrl == null) return@LaunchedEffect
        val index = sourceItems.indexOfFirst { it.sourceUrl == expandedSourceUrl }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick > 0) {
            if (AppConfig.isEInkMode) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            items(items = sourceItems, key = { it.sourceUrl }) { sourceItem ->
                ExploreSourceRow(
                    sourceItem = sourceItem,
                    expanded = sourceItem.sourceUrl == expandedSourceUrl,
                    controller = controller,
                    actions = actions,
                )
            }
        }
        VerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}
