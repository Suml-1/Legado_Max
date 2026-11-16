package io.legado.app.ui.design.components.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

enum class NgSliderVariant {
    CONTINUOUS,
    INLINE,
    DISCRETE,
    COMPACT,
    /** 漫画主菜单的细轨道，未读部分保持中性灰。 */
    MANGA_PROGRESS,
    /** 试听音量浮层的细轨道与小实心滑块，保留独立的触控高度。 */
    AUDIO_PREVIEW,
}

internal fun ngSliderStepValue(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    stepDelta: Int = 0,
): Float {
    require(valueRange.start < valueRange.endInclusive) {
        "valueRange must have a positive length"
    }
    require(steps > 0) { "steps must be positive" }
    val intervals = steps + 1
    val rangeLength = valueRange.endInclusive - valueRange.start
    val currentIndex = (((value.coerceIn(valueRange) - valueRange.start) / rangeLength) * intervals)
        .roundToInt()
    val targetIndex = (currentIndex + stepDelta).coerceIn(0, intervals)
    return valueRange.start + rangeLength * targetIndex / intervals
}

/** 无常驻底色的滑轨微调按钮；图标保持轻量，整行高度承担触控区域。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NgSliderStepButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(NgTheme.colors.onSurface),
    onLongPressStep: (() -> Boolean)? = null,
    onLongPressFinished: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var repeating by remember { mutableStateOf(false) }
    val currentRepeat by rememberUpdatedState(onLongPressStep)
    val currentFinished by rememberUpdatedState(onLongPressFinished)
    val repeatEnabled = onLongPressStep != null
    LaunchedEffect(pressed, repeating, enabled, repeatEnabled) {
        if (pressed && repeating && enabled && repeatEnabled) {
            try {
                while (isActive && currentRepeat?.invoke() == true) {
                    delay(NG_SLIDER_STEP_REPEAT_INTERVAL_MS)
                }
            } finally {
                currentFinished?.invoke()
            }
        } else if ((!pressed || !enabled || !repeatEnabled) && repeating) {
            repeating = false
        }
    }
    Box(
        modifier = modifier
            .width(36.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(18.dp))
            .semantics { this.contentDescription = contentDescription }
            .then(
                if (repeatEnabled) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = {
                            if (pressed) repeating = true else onClick()
                        },
                    )
                } else {
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.width(18.dp).height(18.dp),
            tint = tint.copy(alpha = tint.alpha * if (enabled) 0.74f else 0.28f),
        )
    }
}

private const val NG_SLIDER_STEP_REPEAT_INTERVAL_MS = 120L

/**
 * Reading NG 的通用滑动轨道。
 *
 * 连续型沿用听书进度条的低矮胶囊轨道；离散型使用更清晰的粗轨道并增加刻度和档位吸附；
 * 紧凑型使用阅读底部进度条的细轨道和小滑块，适合窄高受限的浮动工具。
 * [steps] 与 Compose Slider 一致，表示最小值和最大值之间的中间档位数。
 */
@Composable
fun NgSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    visualSteps: Int = steps,
    emphasizedValue: Float? = null,
    variant: NgSliderVariant = NgSliderVariant.CONTINUOUS,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null
) {
    require(valueRange.start < valueRange.endInclusive) {
        "valueRange must have a positive length"
    }
    require(steps >= 0) { "steps must be non-negative" }
    require(visualSteps >= 0) { "visualSteps must be non-negative" }

    val colors = NgTheme.colors
    val currentValue = value.coerceIn(valueRange)
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished = rememberUpdatedState(onValueChangeFinished)
    val compact = variant == NgSliderVariant.COMPACT || variant == NgSliderVariant.MANGA_PROGRESS
    val audioPreview = variant == NgSliderVariant.AUDIO_PREVIEW
    val thumbSurfaceColor = if (audioPreview) ngDrawerContentCardColor() else Color(colors.surface)
    val thumbSize = when (variant) {
        NgSliderVariant.COMPACT, NgSliderVariant.MANGA_PROGRESS -> 6.dp
        NgSliderVariant.INLINE -> 10.dp
        NgSliderVariant.AUDIO_PREVIEW -> 7.5.dp
        else -> 12.dp
    }
    fun snapToStep(rawValue: Float): Float {
        if (steps == 0) return rawValue.coerceIn(valueRange)
        return ngSliderStepValue(rawValue, valueRange, steps)
    }

    fun valueForPosition(x: Float, width: Float, thumbRadius: Float): Float {
        val usableWidth = (width - thumbRadius * 2f).coerceAtLeast(1f)
        val fraction = ((x - thumbRadius) / usableWidth).coerceIn(0f, 1f)
        val rawValue = valueRange.start +
            (valueRange.endInclusive - valueRange.start) * fraction
        return snapToStep(rawValue)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(
                when {
                    compact -> 36.dp
                    audioPreview -> 44.dp
                    variant == NgSliderVariant.INLINE -> 40.dp
                    else -> 48.dp
                }
            )
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = currentValue,
                    range = valueRange,
                    steps = steps
                )
                if (!enabled) disabled()
                setProgress { target ->
                    if (!enabled) {
                        false
                    } else {
                        currentOnValueChange.value(snapToStep(target))
                        currentOnValueChangeFinished.value?.invoke()
                        true
                    }
                }
            }
            .pointerInput(enabled, valueRange, steps, visualSteps, variant) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val thumbRadius = thumbSize.toPx()
                    currentOnValueChange.value(
                        valueForPosition(down.position.x, size.width.toFloat(), thumbRadius)
                    )
                    var pressed: Boolean
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { change ->
                            if (change.pressed) {
                                currentOnValueChange.value(
                                    valueForPosition(
                                        change.position.x,
                                        size.width.toFloat(),
                                        thumbRadius
                                    )
                                )
                            }
                            change.consume()
                        }
                        pressed = event.changes.any { it.pressed }
                    } while (pressed)
                    currentOnValueChangeFinished.value?.invoke()
                }
            }
    ) {
        val thumbRadius = thumbSize.toPx()
        val innerThumbRadius = if (variant == NgSliderVariant.INLINE || audioPreview) {
            6.dp.toPx()
        } else {
            8.dp.toPx()
        }
        val trackHeight = when (variant) {
            NgSliderVariant.CONTINUOUS -> 6.dp.toPx()
            NgSliderVariant.INLINE -> 5.dp.toPx()
            NgSliderVariant.DISCRETE -> 10.dp.toPx()
            NgSliderVariant.COMPACT -> 2.dp.toPx()
            NgSliderVariant.MANGA_PROGRESS -> 3.dp.toPx()
            NgSliderVariant.AUDIO_PREVIEW -> 3.dp.toPx()
        }
        val trackStart = thumbRadius
        val trackWidth = (size.width - thumbRadius * 2f).coerceAtLeast(0f)
        val trackTop = (size.height - trackHeight) / 2f
        val trackRadius = trackHeight / 2f
        // 刻度与滑块仍按逻辑轨道定位；可见轨道向首尾各延伸一个圆角半径，
        // 让端点刻度完整落在轨道内，同时不改变触控与吸附范围。
        val visibleTrackStart = trackStart - trackRadius
        val visibleTrackWidth = trackWidth + trackHeight
        val rangeLength = valueRange.endInclusive - valueRange.start
        val fraction = ((currentValue - valueRange.start) / rangeLength).coerceIn(0f, 1f)
        val thumbX = trackStart + trackWidth * fraction
        val enabledAlpha = if (enabled) 1f else 0.45f
        val primary = Color(colors.primary).copy(alpha = enabledAlpha)
        val inactive = Color(
            if (variant == NgSliderVariant.MANGA_PROGRESS || audioPreview) colors.onSurface else colors.primary
        )
            .copy(alpha = 0.16f * enabledAlpha)
        val thumbSurface = thumbSurfaceColor.copy(alpha = enabledAlpha)

        drawRoundRect(
            color = inactive,
            topLeft = Offset(visibleTrackStart, trackTop),
            size = Size(visibleTrackWidth, trackHeight),
            cornerRadius = CornerRadius(trackRadius)
        )
        drawRoundRect(
            color = primary,
            topLeft = Offset(visibleTrackStart, trackTop),
            size = Size(
                width = (thumbX + trackRadius - visibleTrackStart)
                    .coerceIn(0f, visibleTrackWidth),
                height = trackHeight
            ),
            cornerRadius = CornerRadius(trackRadius)
        )

        if (variant == NgSliderVariant.DISCRETE) {
            val tickCount = visualSteps + 2
            repeat(tickCount) { index ->
                val tickFraction = index.toFloat() / (tickCount - 1)
                val tickX = trackStart + trackWidth * tickFraction
                val emphasized = emphasizedValue?.let { target ->
                    abs(
                        valueRange.start + rangeLength * tickFraction - target
                    ) < 0.0001f
                } == true
                drawCircle(
                    color = if (tickFraction <= fraction) thumbSurface else primary,
                    radius = if (emphasized) 3.5.dp.toPx() else 2.25.dp.toPx(),
                    center = Offset(tickX, size.height / 2f)
                )
                if (emphasized) {
                    drawCircle(
                        color = primary.copy(alpha = 0.55f * enabledAlpha),
                        radius = 5.dp.toPx(),
                        center = Offset(tickX, size.height / 2f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }

        if (compact) {
            drawCircle(
                color = primary,
                radius = thumbRadius,
                center = Offset(thumbX, size.height / 2f)
            )
        } else {
            drawCircle(
                color = thumbSurface,
                radius = thumbRadius,
                center = Offset(thumbX, size.height / 2f)
            )
            drawCircle(
                color = primary,
                radius = innerThumbRadius,
                center = Offset(thumbX, size.height / 2f)
            )
            if (!audioPreview) {
                drawCircle(
                    color = primary.copy(alpha = 0.42f * enabledAlpha),
                    radius = thumbRadius,
                    center = Offset(thumbX, size.height / 2f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}
