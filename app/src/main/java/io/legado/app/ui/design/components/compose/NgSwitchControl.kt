package io.legado.app.ui.design.components.compose

import android.view.SoundEffectConstants
import android.view.ViewConfiguration as AndroidViewConfiguration
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.abs
import kotlin.math.cos

enum class NgSwitchControlVariant {
    REGULAR,
    COMPACT,
}

/** Compose owns the control; the original drawables retain its density-specific artwork. */
@Composable
fun NgSwitchControl(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: NgSwitchControlVariant = NgSwitchControlVariant.REGULAR,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val artwork = remember(context, configuration, variant) {
        NgSwitchArtwork(context, compact = variant == NgSwitchControlVariant.COMPACT)
    }
    // Only immediate visual/gesture state, like the old CompoundButton. The caller still
    // owns the value; applying it never generates an onCheckedChange callback.
    val state = remember { NgSwitchGestureState(checked) }
    val primary = NgTheme.colors.primary
    val isDark = NgTheme.snapshot.isDark
    SideEffect {
        state.checked = checked
        artwork.updateTint(primary, isDark)
    }
    NgSwitchControlContent(
        state = state,
        artwork = artwork,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        modifier = if (variant == NgSwitchControlVariant.COMPACT) {
            modifier.width(42.dp).height(28.dp).scale(0.82f)
        } else {
            modifier
        },
    )
}

private class NgSwitchGestureState(checked: Boolean) {
    var checked by mutableStateOf(checked)
    var position by mutableFloatStateOf(if (checked) 1f else 0f)
    var dragging by mutableStateOf(false)
    var settleGeneration by mutableIntStateOf(0)
}

private val NgSwitchPositionEasing = Easing { fraction ->
    ((cos((fraction + 1f) * Math.PI) / 2.0) + 0.5).toFloat()
}

@Composable
private fun NgSwitchControlContent(
    state: NgSwitchGestureState,
    artwork: NgSwitchArtwork,
    enabled: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val view = LocalView.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val callback by rememberUpdatedState(onCheckedChange)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val nativeConfiguration = remember(context) { AndroidViewConfiguration.get(context) }
    val touchSlop = nativeConfiguration.scaledTouchSlop.toFloat()
    val minimumFlingVelocity = nativeConfiguration.scaledMinimumFlingVelocity.toFloat()
    var artworkRevision by remember(artwork) { mutableIntStateOf(0) }

    DisposableEffect(artwork, view) {
        artwork.attach(view) { artworkRevision++ }
        onDispose { artwork.dispose() }
    }
    LaunchedEffect(state.checked, state.dragging, state.settleGeneration) {
        if (!state.dragging) {
            val target = if (state.checked) 1f else 0f
            if (state.position != target) {
                animate(
                    initialValue = state.position,
                    targetValue = target,
                    animationSpec = tween(durationMillis = 250, easing = NgSwitchPositionEasing),
                ) { value, _ -> state.position = value }
            }
        }
    }

    val viewConfiguration = LocalViewConfiguration.current
    val exactHitBounds = remember(viewConfiguration) {
        object : ViewConfiguration by viewConfiguration {
            override val minimumTouchTargetSize = DpSize.Zero
        }
    }
    // Compact controls sit next to edit/menu/drag actions. Preserve those hit boundaries.
    CompositionLocalProvider(LocalViewConfiguration provides exactHitBounds) {
        Canvas(
            modifier = modifier
                .size(
                    width = with(density) { artwork.preferredWidth.toDp() },
                    height = with(density) { artwork.preferredHeight.toDp() },
                )
                .pointerInput(artwork, enabled, isRtl) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Hit testing reads bounds that draw() normally publishes. A gesture can
                        // arrive before the first draw, which made every thumb press miss.
                        artwork.layout(size.width, size.height, isRtl, state.position)
                        artwork.setHotspot(down.position.x, down.position.y)
                        if (!artwork.hitThumb(down.position.x, down.position.y, touchSlop)) {
                            return@awaitEachGesture
                        }
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        var lastPosition = down.position
                        var dragInteraction: DragInteraction.Start? = null
                        var released = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                if (change.isConsumed) break
                                if (!change.pressed) {
                                    if (state.dragging) {
                                        val velocity = velocityTracker.calculateVelocity().x
                                        val newChecked = if (abs(velocity) > minimumFlingVelocity) {
                                            if (isRtl) velocity < 0 else velocity > 0
                                        } else {
                                            state.position > 0.5f
                                        }
                                        change.consume()
                                        released = true
                                        if (newChecked != state.checked) {
                                            state.checked = newChecked
                                            view.playSoundEffect(SoundEffectConstants.CLICK)
                                            callback?.invoke(newChecked)
                                        }
                                    }
                                    break
                                }
                                if (!state.dragging) {
                                    val delta = change.position - lastPosition
                                    if (abs(delta.x) > touchSlop || abs(delta.y) > touchSlop) {
                                        state.dragging = true
                                        lastPosition = change.position
                                        dragInteraction = DragInteraction.Start().also {
                                            interactionSource.tryEmit(it)
                                        }
                                        change.consume()
                                    }
                                } else {
                                    val delta = change.position.x - lastPosition.x
                                    var deltaPosition = if (artwork.scrollRange != 0) {
                                        delta / artwork.scrollRange
                                    } else if (delta > 0f) {
                                        1f
                                    } else {
                                        -1f
                                    }
                                    if (isRtl) deltaPosition = -deltaPosition
                                    val newPosition = (state.position + deltaPosition).coerceIn(0f, 1f)
                                    if (newPosition != state.position) {
                                        lastPosition = change.position
                                        state.position = newPosition
                                    }
                                    change.consume()
                                }
                                artwork.setHotspot(change.position.x, change.position.y)
                            }
                        } finally {
                            dragInteraction?.let {
                                interactionSource.tryEmit(
                                    if (released) DragInteraction.Stop(it)
                                    else DragInteraction.Cancel(it)
                                )
                                state.dragging = false
                                state.settleGeneration++
                            }
                        }
                    }
                }
                .toggleable(
                    value = state.checked,
                    enabled = enabled,
                    role = Role.Switch,
                    interactionSource = interactionSource,
                    indication = null, // Original ripple and original thumb hotspot.
                    onValueChange = { value ->
                        state.checked = value
                        state.settleGeneration++
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                        callback?.invoke(value)
                    },
                ),
        ) {
            // Drawable.Callback invalidates this draw phase, not surrounding list rows.
            @Suppress("UNUSED_VARIABLE") val revision = artworkRevision
            artwork.layout(size.width.toInt(), size.height.toInt(), isRtl, state.position)
            artwork.updateState(enabled, state.checked, pressed || state.dragging, focused, hovered)
            drawIntoCanvas { artwork.draw(it.nativeCanvas) }
        }
    }
}
