package io.legado.app.ui.design.components.compose

import android.graphics.Color as AndroidColor
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt

/** Optional bounds in the host window; ordinary dialogs keep their existing window behavior. */
internal val LocalNgPaneBounds = staticCompositionLocalOf<IntRect?> { null }

/** Limits a dialog's window, dim layer and input to one pane without recreating its content. */
internal class NgPaneWindowBinding(
    private val window: Window,
    private val scrimView: View? = null,
    private val unscopedWidth: Int? = null,
    private val unscopedHeight: Int? = null,
) {
    private val original = WindowManager.LayoutParams().also { it.copyFrom(window.attributes) }
    private val originalBackground = scrimView?.background
    private var bounds: IntRect? = null
    private var applied = false
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        if (bounds != null) applyBounds()
    }

    init {
        window.decorView.addOnLayoutChangeListener(layoutListener)
    }

    fun update(bounds: IntRect?) {
        this.bounds = bounds?.takeIf { it.width > 0 && it.height > 0 }
        if (this.bounds != null) {
            applyBounds()
        } else if (applied) {
            window.attributes = window.attributes.apply {
                width = unscopedWidth ?: original.width
                height = unscopedHeight ?: original.height
                x = original.x
                y = original.y
                gravity = original.gravity
                flags = (flags and MANAGED_FLAGS.inv()) or (original.flags and MANAGED_FLAGS)
            }
            scrimView?.background = originalBackground
            applied = false
        }
    }

    private fun applyBounds() {
        val region = bounds ?: return
        val attributes = window.attributes
        val targetFlags = (attributes.flags and DIM_AND_OUTSIDE_FLAGS.inv()) or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        val gravity = Gravity.TOP or Gravity.LEFT
        if (attributes.width != region.width || attributes.height != region.height ||
            attributes.x != region.left || attributes.y != region.top ||
            attributes.gravity != gravity || attributes.flags != targetFlags
        ) {
            window.attributes = attributes.apply {
                width = region.width
                height = region.height
                x = region.left
                y = region.top
                this.gravity = gravity
                flags = targetFlags
            }
        }
        if (!applied && original.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
            scrimView?.setBackgroundColor(
                AndroidColor.argb((original.dimAmount * 255).roundToInt(), 0, 0, 0),
            )
        }
        applied = true
    }

    fun dispose() {
        window.decorView.removeOnLayoutChangeListener(layoutListener)
        update(null)
    }

    private companion object {
        const val DIM_AND_OUTSIDE_FLAGS = WindowManager.LayoutParams.FLAG_DIM_BEHIND or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        const val MANAGED_FLAGS = DIM_AND_OUTSIDE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    }
}

/** For a Compose dialog or ModalBottomSheet which already draws its own scrim. */
@Composable
internal fun NgPaneWindowEffect(
    bounds: IntRect? = LocalNgPaneBounds.current,
    window: Window? = null,
    unscopedWidth: Int? = null,
) {
    val view = LocalView.current
    val dialogWindow = window ?: remember(view) {
        generateSequence(view.parent) { it.parent }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
    }
    val binding = remember(dialogWindow, unscopedWidth) {
        dialogWindow?.let {
            NgPaneWindowBinding(
                it,
                unscopedWidth = unscopedWidth,
                unscopedHeight = ViewGroup.LayoutParams.WRAP_CONTENT.takeIf { unscopedWidth != null },
            )
        }
    }
    DisposableEffect(binding) {
        onDispose { binding?.dispose() }
    }
    SideEffect { binding?.update(bounds) }
}

/** Keeps nested editors and confirmations in the pane inherited from their drawer. */
@Composable
internal fun NgPaneDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val bounds = LocalNgPaneBounds.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = if (bounds == null) properties else DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            securePolicy = properties.securePolicy,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        NgPaneDialogContent(
            bounds = bounds,
            onDismissRequest = onDismissRequest.takeIf { properties.dismissOnClickOutside },
            unscopedWidth = if (properties.usePlatformDefaultWidth) ViewGroup.LayoutParams.WRAP_CONTENT
                else ViewGroup.LayoutParams.MATCH_PARENT,
            content = content,
        )
    }
}

/** Also supports a ComponentDialog, whose Compose root is not a DialogWindowProvider. */
@Composable
internal fun NgPaneDialogContent(
    bounds: IntRect?,
    onDismissRequest: (() -> Unit)?,
    window: Window? = null,
    unscopedWidth: Int? = null,
    content: @Composable () -> Unit,
) {
    NgPaneWindowEffect(bounds, window, unscopedWidth)
    Box(
        modifier = if (bounds == null) Modifier else Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (bounds != null) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = .32f)).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onDismissRequest?.invoke() },
                ),
            )
        }
        Box(
            modifier = if (bounds == null) Modifier else Modifier.widthIn(max = 560.dp).padding(24.dp),
        ) {
            content()
        }
    }
}
