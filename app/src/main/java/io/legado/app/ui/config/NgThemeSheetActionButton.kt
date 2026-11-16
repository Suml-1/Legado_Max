package io.legado.app.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.components.compose.ngDrawerThemedActionContainerColor
import io.legado.app.ui.design.theme.NgTheme

private val NgThemeSheetActionContainerColor = Color(0x88FFFFF9)

/**
 * 主题设置抽屉的紧凑圆形操作按钮。
 *
 * 可见承载面与听书播放器顶部按钮保持一致，触控区独立保留标准尺寸。
 */
@Composable
internal fun NgThemeSheetActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    touchSize: Dp = 44.dp,
    containerColor: Color? = null,
    content: @Composable () -> Unit
) {
    val resolvedContainerColor = containerColor ?: ngDrawerThemedActionContainerColor(NgThemeSheetActionContainerColor)
    Box(
        modifier = modifier
            .size(touchSize)
            .clip(CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    resolvedContainerColor.copy(
                        alpha = if (enabled) {
                            resolvedContainerColor.alpha
                        } else {
                            resolvedContainerColor.alpha * 0.45f
                        }
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
internal fun NgThemeSheetSaveButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    touchSize: Dp = 44.dp
) {
    val colors = NgTheme.colors
    NgThemeSheetActionButton(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = modifier,
        enabled = enabled,
        touchSize = touchSize
    ) {
        Icon(
            imageVector = Icons.Rounded.Save,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(colors.onSurface)
        )
    }
}
