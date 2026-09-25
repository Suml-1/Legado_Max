package io.legado.app.ui.widget.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val ScrollbarWidth = 6.dp
private val ScrollbarMinThumbHeightPx = 32f
private val ScrollbarShape = RoundedCornerShape(3.dp)

/**
 * 通用的可拖拽垂直滚动条。
 *
 * 用法：将滚动条和内容放在同一个 Box 中，滚动条对齐右侧。
 *
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     val state = rememberLazyListState()
 *     LazyColumn(state = state, modifier = Modifier.fillMaxSize()) { ... }
 *     VerticalScrollbar(state = state, modifier = Modifier.align(Alignment.CenterEnd))
 * }
 * ```
 */

// ==================== LazyListState ====================

@Composable
fun VerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier
) {
    val info = state.layoutInfo
    val totalItems = info.totalItemsCount
    if (totalItems == 0) return

    val visible = info.visibleItemsInfo
    if (visible.size >= totalItems) return

    val viewportHeight = info.viewportSize.height.toFloat()
    if (viewportHeight <= 0f) return

    val avgItemHeight = visible.sumOf { it.size }.toFloat() / visible.size
    val totalHeight = avgItemHeight * totalItems
    val maxScroll = totalHeight - viewportHeight
    if (maxScroll <= 0f) return

    val scrollOffset = state.firstVisibleItemIndex * avgItemHeight + state.firstVisibleItemScrollOffset
    val scrollFraction = (scrollOffset / maxScroll).coerceIn(0f, 1f)
    val contentFraction = (viewportHeight / totalHeight).coerceIn(0.05f, 1f)

    ScrollbarThumb(
        contentFraction = contentFraction,
        scrollFraction = scrollFraction,
        onDragFraction = { fraction ->
            val targetOffset = fraction * maxScroll
            val targetIndex = (targetOffset / avgItemHeight).toInt().coerceIn(0, totalItems - 1)
            val itemOffset = (targetOffset - targetIndex * avgItemHeight).toInt()
            state.requestScrollToItem(targetIndex, itemOffset)
        },
        modifier = modifier
    )
}

// ==================== LazyGridState ====================

@Composable
fun VerticalScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier
) {
    val info = state.layoutInfo
    val totalItems = info.totalItemsCount
    if (totalItems == 0) return

    val visible = info.visibleItemsInfo
    if (visible.isEmpty() || visible.size >= totalItems) return

    val viewportHeight = info.viewportSize.height.toFloat()
    if (viewportHeight <= 0f) return

    // 网格的行高用可见项平均高度近似：同一行内各项高度一致，误差只来自首末未对齐的滚动行
    val avgItemHeight = visible.sumOf { it.size.height }.toFloat() / visible.size
    val totalHeight = avgItemHeight * totalItems
    val maxScroll = totalHeight - viewportHeight
    if (maxScroll <= 0f) return

    val scrollOffset = state.firstVisibleItemIndex * avgItemHeight + state.firstVisibleItemScrollOffset
    val scrollFraction = (scrollOffset / maxScroll).coerceIn(0f, 1f)
    val contentFraction = (viewportHeight / totalHeight).coerceIn(0.05f, 1f)

    ScrollbarThumb(
        contentFraction = contentFraction,
        scrollFraction = scrollFraction,
        onDragFraction = { fraction ->
            val targetOffset = fraction * maxScroll
            val targetIndex = (targetOffset / avgItemHeight).toInt().coerceIn(0, totalItems - 1)
            val itemOffset = (targetOffset - targetIndex * avgItemHeight).toInt()
            state.requestScrollToItem(targetIndex, itemOffset)
        },
        modifier = modifier
    )
}

// ==================== ScrollState ====================

@Composable
fun VerticalScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier
) {
    val maxValue = state.maxValue
    if (maxValue <= 0) return

    ScrollbarThumb(
        contentFraction = null,
        scrollFraction = (state.value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f),
        onDragFraction = { fraction ->
            val target = (fraction * maxValue).toInt()
            state.dispatchRawDelta((target - state.value).toFloat())
        },
        modifier = modifier,
        scrollStateMaxValue = maxValue
    )
}

// ==================== 共享 Thumb ====================

@Composable
private fun ScrollbarThumb(
    contentFraction: Float?,
    scrollFraction: Float,
    onDragFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    scrollStateMaxValue: Int = 0
) {
    var trackHeightPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val thumbColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .width(ScrollbarWidth)
            .fillMaxHeight()
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                // 关键：在拖拽开始时快照 scrollFraction 到局部变量
                // 这样拖拽过程中即使 Compose 重组改变了 scrollFraction，
                // 我们仍然基于按下瞬间的位置计算，避免反馈循环
                detectVerticalDragGestures(
                    onDragStart = { _ ->
                    },
                    onDragEnd = {},
                    onDragCancel = {},
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (trackHeightPx <= 0f) return@detectVerticalDragGestures
                        // 用手指绝对位置：change.position.y / trackHeightPx
                        // 这样手指在 track 顶部 = 滚到开头，底部 = 滚到结尾
                        val fingerFraction = (change.position.y / trackHeightPx).coerceIn(0f, 1f)
                        onDragFraction(fingerFraction)
                    }
                )
            }
    ) {
        // 只在 trackHeightPx 有效时绘制 thumb
        if (trackHeightPx > 0f) {
            val thumbFrac = if (contentFraction != null) {
                contentFraction
            } else {
                val totalHeight = trackHeightPx + scrollStateMaxValue.toFloat()
                (trackHeightPx / totalHeight).coerceIn(0.05f, 1f)
            }

            if (thumbFrac < 0.99f) {
                val thumbHeightPx = (trackHeightPx * thumbFrac).coerceAtLeast(ScrollbarMinThumbHeightPx)
                val maxOffset = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
                val thumbOffsetPx = scrollFraction * maxOffset

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                        .width(ScrollbarWidth)
                        .height(with(density) { thumbHeightPx.toDp() })
                        .clip(ScrollbarShape)
                        .background(thumbColor.copy(alpha = 0.5f), ScrollbarShape)
                )
            }
        }
    }
}
