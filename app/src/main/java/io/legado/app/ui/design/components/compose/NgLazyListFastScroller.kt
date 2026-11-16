package io.legado.app.ui.design.components.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class NgLazyListFastScrollerVariant {
    /** 旧 FastScrollRecyclerView 同款细轨道与强调色拖柄。 */
    TRACK,

    /** 无常驻轨道的浮动双箭头拖柄，适合覆盖在内容列表右侧。 */
    FLOATING_HANDLE,
}

/**
 * NG 长列表快速滚动条。默认 Variant 的尺寸和显隐节奏与旧
 * FastScrollRecyclerView 保持一致；其它 Variant 只改变拖柄外观。
 */
@Composable
fun NgLazyListFastScroller(
    state: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
    variant: NgLazyListFastScrollerVariant = NgLazyListFastScrollerVariant.TRACK,
    trackColor: Color? = null,
    handleColor: Color? = null,
) {
    val visibleItemCount by remember {
        derivedStateOf { state.layoutInfo.visibleItemsInfo.size }
    }
    val canScroll = itemCount > visibleItemCount && visibleItemCount > 0
    val scrollFraction by remember(itemCount) {
        derivedStateOf {
            val visibleItems = state.layoutInfo.visibleItemsInfo
            val first = visibleItems.firstOrNull()
            val maxFirstIndex = (itemCount - visibleItems.size).coerceAtLeast(1)
            if (first == null || first.size <= 0) {
                0f
            } else {
                val itemOffset = (-first.offset).toFloat() / first.size
                ((first.index + itemOffset) / maxFirstIndex).coerceIn(0f, 1f)
            }
        }
    }
    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    NgScrollFastScroller(
        scrollFraction = scrollFraction,
        canScroll = canScroll,
        isScrollInProgress = state.isScrollInProgress,
        onScrollFractionChange = { fraction ->
            val index = (fraction * (itemCount - 1)).roundToInt()
            scrollJob?.cancel()
            scrollJob = scope.launch { state.scrollToItem(index) }
        },
        modifier = modifier,
        variant = variant,
        trackColor = trackColor,
        handleColor = handleColor,
    )
}

/** 与目录抽屉共用外观和显隐节奏的通用快速滚动条。 */
@Composable
fun NgScrollFastScroller(
    scrollFraction: Float,
    canScroll: Boolean,
    isScrollInProgress: Boolean,
    onScrollFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    variant: NgLazyListFastScrollerVariant = NgLazyListFastScrollerVariant.TRACK,
    trackColor: Color? = null,
    handleColor: Color? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(canScroll, isScrollInProgress, dragging) {
        when {
            !canScroll -> visible = false
            isScrollInProgress || dragging -> visible = true
            else -> {
                delay(1_000)
                visible = false
            }
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        label = "NgScrollFastScrollerAlpha",
    )
    val currentOnScrollFractionChange by rememberUpdatedState(onScrollFractionChange)
    val density = LocalDensity.current
    val verticalPadding = 8.dp
    val thumbHeight = when (variant) {
        NgLazyListFastScrollerVariant.TRACK -> 40.dp
        NgLazyListFastScrollerVariant.FLOATING_HANDLE -> 56.dp
    }
    val railWidth = when (variant) {
        NgLazyListFastScrollerVariant.TRACK -> 24.dp
        NgLazyListFastScrollerVariant.FLOATING_HANDLE -> 28.dp
    }

    BoxWithConstraints(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight(),
    ) {
        val heightPx = with(density) { maxHeight.toPx() }
        val paddingPx = with(density) { verticalPadding.toPx() }
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val travelPx = (heightPx - paddingPx * 2f - thumbHeightPx).coerceAtLeast(1f)
        val dragModifier = if (visible && canScroll) {
            Modifier.pointerInput(canScroll, heightPx, variant) {
                fun scrollTo(positionY: Float) {
                    val fraction = ((positionY - paddingPx - thumbHeightPx / 2f) / travelPx)
                        .coerceIn(0f, 1f)
                    currentOnScrollFractionChange(fraction)
                }
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        scrollTo(it.y)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onDrag = { change, _ ->
                        change.consume()
                        scrollTo(change.position.y)
                    },
                )
            }
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(dragModifier),
        ) {
            when (variant) {
                NgLazyListFastScrollerVariant.TRACK -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(vertical = verticalPadding)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(
                                (trackColor
                                    ?: Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.30f))
                                    .copy(alpha = (trackColor?.alpha ?: 0.30f) * alpha)
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset {
                                IntOffset(
                                    x = 0,
                                    y = (paddingPx + travelPx * scrollFraction.coerceIn(0f, 1f))
                                        .roundToInt(),
                                )
                            }
                            .width(8.dp)
                            .height(thumbHeight)
                            .background(
                                (handleColor ?: Color(NgTheme.colors.primary))
                                    .copy(alpha = alpha)
                            ),
                    )
                }

                NgLazyListFastScrollerVariant.FLOATING_HANDLE -> {
                    val shape = RoundedCornerShape(12.dp)
                    val isDark = NgTheme.snapshot.isDark
                    val isEInk = NgTheme.snapshot.isEInk
                    val primary = Color(NgTheme.colors.primary)
                    val containerColor = Color(
                        NgColorMath.blend(
                            NgTheme.colors.inputContainer,
                            NgTheme.colors.primary,
                            if (isDark) 0.12f else 0.06f,
                        )
                    ).copy(
                        alpha = when {
                            isEInk -> 1f
                            isDark -> 0.94f
                            else -> 0.90f
                        }
                    )
                    val shadowColor = Color.Black.copy(alpha = if (isDark) 0.28f else 0.12f)
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset {
                                IntOffset(
                                    x = 0,
                                    y = (paddingPx + travelPx * scrollFraction.coerceIn(0f, 1f))
                                        .roundToInt(),
                                )
                            }
                            .width(24.dp)
                            .height(thumbHeight)
                            .graphicsLayer { this.alpha = alpha }
                            .shadow(
                                elevation = if (isEInk) 0.dp else 2.dp,
                                shape = shape,
                                clip = false,
                                ambientColor = shadowColor,
                                spotColor = shadowColor,
                            )
                            .clip(shape)
                            .background(containerColor)
                            .border(
                                width = 0.5.dp,
                                color = primary.copy(alpha = if (isEInk) 0.42f else 0.14f),
                                shape = shape,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = primary.copy(alpha = 0.86f),
                        )
                        Box(Modifier.height(2.dp))
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = primary.copy(alpha = 0.86f),
                        )
                    }
                }
            }
        }
    }
}
