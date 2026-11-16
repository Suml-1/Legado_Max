package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefreshIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * NG 管理列表共用的长按拖拽状态。
 *
 * 拖动过程中只调用 [onMove] 修改 Compose 本地顺序；松手时才调用 [onFinished]，
 * 由页面一次性把可见 ID 顺序提交给宿主，避免每跨过一项都写 Store。
 */
@Stable
class NgLazyReorderState internal constructor(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onFinished: () -> Unit
) {
    var draggedKey: Any? by mutableStateOf(null)
        private set

    val isDragging: Boolean
        get() = draggedKey != null

    var draggedOffset by mutableFloatStateOf(0f)
        private set

    private var lastTargetIndex = -1
    private var scrollJob: Job? = null

    internal fun start(key: Any) {
        if (listState.layoutInfo.visibleItemsInfo.any { it.key == key }) {
            draggedKey = key
            draggedOffset = 0f
            lastTargetIndex = -1
        }
    }

    internal fun dragBy(deltaY: Float) {
        val key = draggedKey ?: return
        draggedOffset += deltaY

        val layoutInfo = listState.layoutInfo
        val dragged = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val draggedCenter = dragged.offset + draggedOffset + dragged.size / 2f
        val target = layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.key != key &&
                draggedCenter >= item.offset &&
                draggedCenter <= item.offset + item.size
        }

        if (target != null && target.index != lastTargetIndex) {
            draggedOffset += dragged.offset - target.offset
            lastTargetIndex = target.index
            onMove(dragged.index, target.index)
        }

        val top = dragged.offset + draggedOffset
        val bottom = top + dragged.size
        val overscroll = when {
            top < layoutInfo.viewportStartOffset -> top - layoutInfo.viewportStartOffset
            bottom > layoutInfo.viewportEndOffset -> bottom - layoutInfo.viewportEndOffset
            else -> 0f
        }.coerceIn(-32f, 32f)

        scrollJob?.cancel()
        if (overscroll != 0f) {
            scrollJob = scope.launch {
                val consumed = listState.scrollBy(overscroll)
                draggedOffset += consumed
            }
        }
    }

    internal fun finish() {
        val hadDrag = draggedKey != null
        scrollJob?.cancel()
        scrollJob = null
        draggedKey = null
        draggedOffset = 0f
        lastTargetIndex = -1
        if (hadDrag) {
            onFinished()
        }
    }
}

@Composable
fun rememberNgLazyReorderState(
    listState: LazyListState = rememberLazyListState(),
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onFinished: () -> Unit
): NgLazyReorderState {
    val scope = rememberCoroutineScope()
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnFinished by rememberUpdatedState(onFinished)
    return remember(listState, scope) {
        NgLazyReorderState(
            listState = listState,
            scope = scope,
            onMove = { fromIndex, toIndex -> currentOnMove(fromIndex, toIndex) },
            onFinished = { currentOnFinished() }
        )
    }
}

fun Modifier.ngReorderHandle(
    state: NgLazyReorderState,
    key: Any,
    enabled: Boolean,
    contentDescription: String? = null
): Modifier {
    return this
        .testTag("management_drag_$key")
        .then(
            if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else {
                Modifier
            }
        )
        .pointerInput(state, key, enabled) {
            if (!enabled) return@pointerInput
            try {
                detectDragGestures(
                    onDragStart = { state.start(key) },
                    onDragEnd = state::finish,
                    onDragCancel = state::finish,
                    onDrag = { change, amount ->
                        change.consume()
                        state.dragBy(amount.y)
                    }
                )
            } finally {
                // LazyColumn 重排或回收拖动项时会直接取消当前 pointerInput。
                // 此时 detectDragGestures 不保证调用 onDragCancel，必须在协程退出时
                // 再做一次幂等收尾，避免页面作用域中的边缘滚动任务继续存活。
                state.finish()
            }
        }
}

/**
 * 仅在长按成功后接管拖动；长按前不消费位移，让 LazyColumn 在超过 touch slop 后
 * 取消长按候选并继续正常滚动。
 */
fun Modifier.ngReorderAfterLongPress(
    state: NgLazyReorderState,
    key: Any,
    enabled: Boolean,
    contentDescription: String? = null
): Modifier {
    return this
        .testTag("management_drag_$key")
        .then(
            if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else {
                Modifier
            }
        )
        .pointerInput(state, key, enabled) {
            if (!enabled) return@pointerInput
            try {
                detectDragGesturesAfterLongPress(
                    onDragStart = { state.start(key) },
                    onDragEnd = state::finish,
                    onDragCancel = state::finish,
                    onDrag = { change, amount ->
                        change.consume()
                        state.dragBy(amount.y)
                    }
                )
            } finally {
                state.finish()
            }
        }
}

@Composable
fun Modifier.ngDraggedItem(
    state: NgLazyReorderState,
    key: Any
): Modifier {
    val dragging = state.draggedKey == key
    return this
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            translationY = if (dragging) state.draggedOffset else 0f
        }
}

/**
 * 向右侧滑只发出删除请求并自动回弹；确认弹窗和真正删除仍由宿主持有。
 */
@Composable
fun NgSwipeToDelete(
    deletable: Boolean,
    reordering: Boolean,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val currentDelete by rememberUpdatedState(onDeleteRequested)
    val currentDeletable by rememberUpdatedState(deletable)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            when (target) {
                SwipeToDismissBoxValue.StartToEnd -> currentDeletable
                SwipeToDismissBoxValue.EndToStart -> false
                SwipeToDismissBoxValue.Settled -> true
            }
        },
        positionalThreshold = { distance -> distance * 0.25f }
    )
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
            currentDelete()
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = deletable,
        enableDismissFromEndToStart = false,
        gesturesEnabled = deletable && !reordering,
        backgroundContent = {},
        content = content
    )
}

enum class NgPullRefreshIndicatorVariant {
    MATERIAL,
    SINGLE_SPINNER,
}

/** 把 Material 3 实验性的刷新 API 隔离在组件层，业务页面只消费稳定的 NG 接口。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NgPullRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showIndicator: Boolean = true,
    indicatorVariant: NgPullRefreshIndicatorVariant = NgPullRefreshIndicatorVariant.MATERIAL,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (enabled && !isRefreshing) onRefresh()
        },
        modifier = modifier.testTag("management_refresh"),
        state = state,
        indicator = {
            if (showIndicator) {
                when (indicatorVariant) {
                    NgPullRefreshIndicatorVariant.MATERIAL -> {
                        PullToRefreshDefaults.Indicator(
                            state = state,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                            containerColor = Color(NgTheme.colors.cardContainer),
                            color = Color(NgTheme.colors.primary)
                        )
                    }

                    NgPullRefreshIndicatorVariant.SINGLE_SPINNER -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .size(40.dp)
                                .pullToRefreshIndicator(
                                    state = state,
                                    isRefreshing = isRefreshing,
                                    containerColor = Color(NgTheme.colors.cardContainer),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(NgTheme.colors.primary),
                                strokeWidth = 2.5.dp,
                            )
                        }
                    }
                }
            }
        },
        content = content
    )
}

/**
 * NG 管理列表共用的左侧滑动多选状态。
 *
 * 首项决定本次手势是选中还是取消；手指折返时恢复离开区间的状态，行为与旧管理列表的
 * ToggleAndReverse 模式一致。
 */
@Stable
class NgLazySlideSelectState internal constructor(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val isSelected: (index: Int) -> Boolean,
    private val onSelectionChange: (index: Int, selected: Boolean) -> Unit,
) {
    private var startIndex = -1
    private var endIndex = -1
    private var firstWasSelected = false
    private val originalSelections = mutableMapOf<Int, Boolean>()
    private val gestureSelections = mutableMapOf<Int, Boolean>()
    private var scrollJob: Job? = null

    internal fun itemIndexAt(y: Float): Int? {
        return listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            y >= item.offset && y <= item.offset + item.size
        }?.index
    }

    internal fun start(index: Int) {
        originalSelections.clear()
        gestureSelections.clear()
        firstWasSelected = isSelected(index)
        startIndex = index
        endIndex = index
        setSelected(index, !firstWasSelected)
    }

    internal fun update(y: Float) {
        itemIndexAt(y)?.let(::selectTo)

        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        if (viewportHeight <= 0) return
        val hotspot = (viewportHeight * 0.2f).coerceAtMost(160f)
        val scrollDelta = when {
            y < layoutInfo.viewportStartOffset + hotspot -> {
                -24f * (1f - (y - layoutInfo.viewportStartOffset) / hotspot)
            }

            y > layoutInfo.viewportEndOffset - hotspot -> {
                24f * (1f - (layoutInfo.viewportEndOffset - y) / hotspot)
            }

            else -> 0f
        }.coerceIn(-24f, 24f)

        scrollJob?.cancel()
        if (scrollDelta != 0f) {
            scrollJob = scope.launch {
                listState.scrollBy(scrollDelta)
                itemIndexAt(y)?.let(::selectTo)
            }
        }
    }

    private fun selectTo(index: Int) {
        if (startIndex < 0 || index == endIndex) return
        val oldStart = min(startIndex, endIndex)
        val oldEnd = max(startIndex, endIndex)
        val newStart = min(startIndex, index)
        val newEnd = max(startIndex, index)

        when {
            newStart > oldStart -> {
                for (position in oldStart until newStart) {
                    restoreSelection(position)
                }
            }

            newStart < oldStart -> {
                for (position in newStart until oldStart) {
                    setSelected(position, !firstWasSelected)
                }
            }
        }
        when {
            newEnd > oldEnd -> {
                for (position in (oldEnd + 1)..newEnd) {
                    setSelected(position, !firstWasSelected)
                }
            }

            newEnd < oldEnd -> {
                for (position in (newEnd + 1)..oldEnd) {
                    restoreSelection(position)
                }
            }
        }
        endIndex = index
    }

    private fun setSelected(index: Int, selected: Boolean) {
        val original = originalSelections.getOrPut(index) { isSelected(index) }
        val current = gestureSelections.getOrPut(index) { original }
        if (current != selected) {
            onSelectionChange(index, selected)
            gestureSelections[index] = selected
        }
    }

    private fun restoreSelection(index: Int) {
        originalSelections[index]?.let { selected ->
            setSelected(index, selected)
        }
    }

    internal fun finish() {
        scrollJob?.cancel()
        scrollJob = null
        startIndex = -1
        endIndex = -1
        originalSelections.clear()
        gestureSelections.clear()
    }
}

@Composable
fun rememberNgLazySlideSelectState(
    listState: LazyListState,
    isSelected: (index: Int) -> Boolean,
    onSelectionChange: (index: Int, selected: Boolean) -> Unit,
): NgLazySlideSelectState {
    val scope = rememberCoroutineScope()
    val currentIsSelected by rememberUpdatedState(isSelected)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    return remember(listState, scope) {
        NgLazySlideSelectState(
            listState = listState,
            scope = scope,
            isSelected = { index -> currentIsSelected(index) },
            onSelectionChange = { index, selected ->
                currentOnSelectionChange(index, selected)
            },
        )
    }
}

/**
 * 仅在列表左侧选择区的纵向位移超过 touch slop 后接管连续选择。
 * 普通点按不消费事件，继续由复选框自身处理，避免按下与抬起各改一次状态。
 */
fun Modifier.ngSlideSelect(
    state: NgLazySlideSelectState,
    enabled: Boolean = true,
    slideAreaStart: Dp = 16.dp,
    slideAreaEnd: Dp = 50.dp,
): Modifier = this
    .testTag("management_slide_select")
    .pointerInput(state, enabled, slideAreaStart, slideAreaEnd) {
        val slideAreaStartPx = slideAreaStart.toPx()
        val slideAreaEndPx = slideAreaEnd.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!enabled || down.position.x !in slideAreaStartPx..slideAreaEndPx) {
                return@awaitEachGesture
            }
            var selecting = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: break
                    if (!change.pressed) break
                    if (!selecting) {
                        val delta = change.position - down.position
                        val horizontalDistance = abs(delta.x)
                        val verticalDistance = abs(delta.y)
                        when {
                            verticalDistance > viewConfiguration.touchSlop &&
                                verticalDistance >= horizontalDistance -> {
                                val startIndex = state.itemIndexAt(down.position.y)
                                    ?: break
                                change.consume()
                                state.start(startIndex)
                                state.update(change.position.y)
                                selecting = true
                            }

                            horizontalDistance > viewConfiguration.touchSlop -> break
                        }
                    } else {
                        state.update(change.position.y)
                        change.consume()
                    }
                }
            } finally {
                if (selecting) state.finish()
            }
        }
    }
