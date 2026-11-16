package io.legado.app.ui.design.components.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import kotlin.math.min
import kotlin.math.roundToInt

internal fun shouldDismissDrawer(distance: Float, height: Float, velocity: Float, density: Float): Boolean =
    distance >= min(96f * density, height * 0.25f) ||
        (distance >= 24f * density && velocity >= 1200f * density)

/** 给固定贴底窗口补充关闭手势，不改变材质、几何或保存语义。 */
@Composable
fun NgDismissibleDrawer(onDismiss: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val density = LocalDensity.current.density
    val latestDismiss by rememberUpdatedState(onDismiss)
    val gesturesEnabled by rememberUpdatedState(enabled)
    var distance by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(1) }
    var settling by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    fun drag(delta: Float): Float {
        if (!gesturesEnabled || settling || dismissed) return 0f
        val previous = distance
        distance = (distance + delta).coerceIn(0f, height.toFloat())
        return distance - previous
    }
    suspend fun settle(velocity: Float) {
        if (distance <= 0f || settling || dismissed) return
        settling = true
        try {
            val dismiss = gesturesEnabled && shouldDismissDrawer(distance, height.toFloat(), velocity, density)
            Animatable(distance).animateTo(
                if (dismiss) height.toFloat() else 0f,
                animationSpec = tween(if (dismiss) 160 else 200),
            ) { distance = value }
            if (dismiss) {
                dismissed = true
                latestDismiss()
            }
        } finally {
            settling = false
        }
    }
    val connection = remember(density) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 抽屉已经下移时，上推先让抽屉归位，再交还给内容滚动。
                return if (source == NestedScrollSource.UserInput && distance > 0f) {
                    Offset(0f, drag(available.y))
                } else Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // 只接收子列表用不掉的下拉量：列表未到顶时不会移动抽屉。
                return if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    Offset(0f, drag(available.y))
                } else Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (distance <= 0f) return Velocity.Zero
                settle(available.y)
                return Velocity(0f, available.y)
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (distance <= 0f) return Velocity.Zero
                settle(available.y)
                return Velocity(0f, available.y)
            }
        }
    }
    Box(
        Modifier
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .offset { IntOffset(0, distance.roundToInt()) }
            .nestedScroll(connection)
            .draggable(
                state = rememberDraggableState { drag(it) },
                orientation = Orientation.Vertical,
                enabled = enabled,
                onDragStopped = { settle(it) },
            ),
    ) { content() }
}
