package io.legado.app.ui.design.components.compose

import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.ui.design.theme.NgTheme

/**
 * 为单一 Compose Activity 提供一次窗口背景采样。
 *
 * 通过一个位于内容后方的透明 View 提供页面级 source；公共 [NgVisualSurface] 复用
 * View-backed RenderNode 后端绘制当前 decor background。透明玻璃、低版本和 E-Ink
 * 不增加该宿主，也不制作静态截图。
 */
@Composable
fun NgWindowLiquidGlassBackdropHost(
    modifier: Modifier = Modifier,
    backgroundOverlay: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    if (
        !NgTheme.usesLiquidGlass ||
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    ) {
        Box(modifier = modifier, content = content)
        return
    }

    val context = LocalContext.current
    val sourceView = remember(context) {
        View(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
        }
    }
    val overlayColor = backgroundOverlay.toArgb()

    Box(modifier = modifier) {
        AndroidView(
            factory = { sourceView },
            modifier = Modifier.matchParentSize(),
            update = { it.setBackgroundColor(overlayColor) },
        )
        NgLiquidGlassViewBackdropProvider(sourceView) {
            Box(
                modifier = Modifier.matchParentSize(),
                content = content,
            )
        }
    }
}
