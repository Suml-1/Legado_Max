package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.ui.design.components.NgSurfaceVariant
import io.legado.app.ui.design.theme.NgTheme

@Composable
fun NgSurface(
    modifier: Modifier = Modifier,
    variant: NgSurfaceVariant = NgSurfaceVariant.CARD,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val color = when (variant) {
        NgSurfaceVariant.CANVAS -> NgTheme.colors.background
        NgSurfaceVariant.CARD,
        NgSurfaceVariant.BORDERLESS_CARD -> NgTheme.colors.cardContainer
        NgSurfaceVariant.PANEL -> NgTheme.colors.surfaceContainerHigh
        NgSurfaceVariant.OVERLAY -> NgTheme.colors.dialogContainer
    }
    val shape = when (variant) {
        NgSurfaceVariant.CANVAS -> NgTheme.shapes.smallDp
        NgSurfaceVariant.CARD -> NgTheme.shapes.mediumDp
        NgSurfaceVariant.BORDERLESS_CARD -> NgTheme.shapes.largeDp
        NgSurfaceVariant.PANEL -> NgTheme.shapes.largeDp
        NgSurfaceVariant.OVERLAY -> NgTheme.shapes.extraLargeDp
    }
    val elevation = when (variant) {
        NgSurfaceVariant.CANVAS,
        NgSurfaceVariant.CARD,
        NgSurfaceVariant.BORDERLESS_CARD,
        NgSurfaceVariant.PANEL -> NgTheme.effects.cardElevationDp
        NgSurfaceVariant.OVERLAY -> NgTheme.effects.overlayElevationDp
    }
    val alpha = when (variant) {
        NgSurfaceVariant.CANVAS -> 1f
        NgSurfaceVariant.CARD -> NgTheme.effects.containerAlpha
        NgSurfaceVariant.BORDERLESS_CARD -> 1f
        NgSurfaceVariant.PANEL -> (NgTheme.effects.containerAlpha + 0.14f).coerceAtMost(1f)
        NgSurfaceVariant.OVERLAY -> NgTheme.effects.dialogAlpha
    }.takeIf { !NgTheme.snapshot.isEInk } ?: 1f
    val border = if (
        variant == NgSurfaceVariant.CANVAS ||
        variant == NgSurfaceVariant.BORDERLESS_CARD
    ) {
        null
    } else {
        BorderStroke(
            1.dp,
            Color(
                if (NgTheme.snapshot.isEInk) {
                    NgTheme.colors.outline
                } else {
                    NgTheme.colors.outlineVariant
                }
            ).copy(
                alpha = when {
                    NgTheme.snapshot.isEInk -> 1f
                    variant == NgSurfaceVariant.OVERLAY -> 0.35f
                    else -> 0.25f
                }
            )
        )
    }

    Surface(
        modifier = modifier,
        color = Color(color).copy(alpha = alpha),
        shape = RoundedCornerShape(shape.dp),
        shadowElevation = elevation.dp,
        border = border
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * 可跨 Compose Dialog Window 采样宿主页面的 NG 浮层。
 *
 * 在创建 Dialog 前捕获 Activity 根视图，避免弹窗子窗口内的液态表面只能看到自己；
 * 普通透明玻璃、低版本与 E-Ink 继续保持原 OVERLAY 卡面。
 */
@Composable
fun NgVisualOverlayDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdropSource = LocalView.current.rootView
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        val snapshot = NgTheme.snapshot
        val cornerRadius = snapshot.shapes.extraLargeDp.dp
        val shape = RoundedCornerShape(cornerRadius)
        val containerAlpha = if (snapshot.isEInk) 1f else snapshot.effects.dialogAlpha
        val containerColor = Color(snapshot.colors.dialogContainer).copy(alpha = containerAlpha)
        val contentColor = Color(snapshot.colors.onSurface)
        val borderColor = Color(
            if (snapshot.isEInk) snapshot.colors.outline else snapshot.colors.outlineVariant
        ).copy(alpha = if (snapshot.isEInk) 1f else 0.35f)
        val edgeHighlight = NgGlassDefaults.style(containerAlpha).edgeHighlight
        val overlayElevation = snapshot.effects.overlayElevationDp.dp
        val style = remember(
            containerColor,
            contentColor,
            borderColor,
            edgeHighlight,
            overlayElevation,
        ) {
            NgGlassStyle(
                containerTop = containerColor,
                containerBottom = containerColor,
                accentGlow = Color.Transparent,
                borderColor = borderColor,
                edgeHighlight = edgeHighlight,
                surfaceGloss = Color.Transparent,
                depthEdge = Color.Transparent,
                contentColor = contentColor,
                blurRadius = 0.dp,
                shadowElevation = overlayElevation,
                borderWidth = 1.dp,
                highlightWidth = 0.dp,
            )
        }
        NgVisualSurface(
            modifier = modifier,
            role = NgMaterialRole.OVERLAY,
            cornerRadius = cornerRadius,
            shape = shape,
            style = style,
            contentPadding = contentPadding,
            viewBackdropSource = backdropSource,
            content = content,
        )
    }
}

@Composable
fun NgCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    NgSurface(
        modifier = modifier,
        variant = NgSurfaceVariant.CARD,
        contentPadding = contentPadding,
        content = content
    )
}
