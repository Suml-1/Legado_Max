package io.legado.app.ui.design.components.compose

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.R

enum class NgActionBarButtonSurfaceVariant {
    LIGHT_GLASS,
    THEMED,
    THEMED_HIGH_CONTRAST,
    NEUTRAL,
}

enum class NgActionBarButtonSizeVariant {
    REGULAR,
    COMPACT,
}

/**
 * Reading NG 底部操作栏按钮。
 *
 * 几何与图标文字布局对齐已经验收的书籍详情页操作按钮；页面只选择语义 Variant，
 * 不再自行组合纯色大胶囊或临时透明度。
 */
@Composable
fun NgActionBarButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: NgButtonVariant = NgButtonVariant.OUTLINE,
    surfaceVariant: NgActionBarButtonSurfaceVariant =
        NgActionBarButtonSurfaceVariant.LIGHT_GLASS,
    sizeVariant: NgActionBarButtonSizeVariant = NgActionBarButtonSizeVariant.REGULAR,
) {
    val colors = NgTheme.colors
    val primaryContent = ngDrawerPrimaryContentColor()
    val primaryColors = rememberNgSolidButtonColors(Color(colors.primary), primaryContent)
    val compact = sizeVariant == NgActionBarButtonSizeVariant.COMPACT
    val cornerRadius = if (compact) 8.dp else 12.dp
    val shape = RoundedCornerShape(cornerRadius)
    val background = when (surfaceVariant) {
        NgActionBarButtonSurfaceVariant.LIGHT_GLASS -> Color.White.copy(alpha = 0.82f)
        NgActionBarButtonSurfaceVariant.THEMED ->
            colorResource(R.color.background_menu).copy(alpha = 0.9f)
        NgActionBarButtonSurfaceVariant.THEMED_HIGH_CONTRAST ->
            Color(colors.surfaceContainerHigh).copy(alpha = 0.94f)
        NgActionBarButtonSurfaceVariant.NEUTRAL ->
            ngDrawerContentCardColor()
    }
    val outlineAccent = if (
        surfaceVariant == NgActionBarButtonSurfaceVariant.NEUTRAL ||
        surfaceVariant == NgActionBarButtonSurfaceVariant.THEMED_HIGH_CONTRAST
    ) {
        Color(colors.onSurface)
    } else {
        Color(colors.primary)
    }
    val buttonModifier = modifier.height(if (compact) 38.dp else 42.dp)
    val contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 14.dp)

    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 18.dp else 20.dp)
        )
        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        Text(
            text = text,
            fontSize = if (compact) 13.sp else 14.sp,
            maxLines = 1
        )
    }

    val usesLiquidSurface = variant != NgButtonVariant.PRIMARY &&
        variant != NgButtonVariant.PRIMARY_LIGHT_CONTENT &&
        NgTheme.usesLiquidGlass &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        hasCurrentNgLiquidGlassBackdrop()
    if (usesLiquidSurface) {
        NgLiquidActionBarButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            cornerRadius = cornerRadius,
            background = background,
            accent = if (variant == NgButtonVariant.DANGER) {
                Color(colors.error)
            } else {
                outlineAccent
            },
            contentPadding = contentPadding,
            content = content,
        )
        return
    }

    when (variant) {
        NgButtonVariant.PRIMARY -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(primaryColors.container),
                contentColor = Color(primaryColors.content)
            ),
            contentPadding = contentPadding,
            content = content
        )

        NgButtonVariant.PRIMARY_LIGHT_CONTENT -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(primaryColors.container),
                contentColor = Color(primaryColors.content)
            ),
            contentPadding = contentPadding,
            content = content
        )

        NgButtonVariant.DANGER -> NgOutlinedActionBarButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            background = background,
            accent = Color(colors.error),
            contentPadding = contentPadding,
            content = content
        )

        else -> NgOutlinedActionBarButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            background = background,
            accent = outlineAccent,
            contentPadding = contentPadding,
            content = content
        )
    }
}

@Composable
private fun NgLiquidActionBarButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: RoundedCornerShape,
    cornerRadius: androidx.compose.ui.unit.Dp,
    background: Color,
    accent: Color,
    contentPadding: PaddingValues,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val resolvedAccent = accent.copy(alpha = if (enabled) 1f else 0.38f)
    val style = NgGlassDefaults.flatNeutralStyle(containerAlpha = background.alpha).copy(
        containerTop = background,
        containerBottom = background,
        contentColor = resolvedAccent,
    )
    NgVisualSurface(
        modifier = modifier
            .clip(shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.55f),
        role = NgMaterialRole.ACTION,
        cornerRadius = cornerRadius,
        shape = shape,
        style = style,
        contentPadding = contentPadding,
    ) {
        CompositionLocalProvider(LocalContentColor provides resolvedAccent) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
private fun NgOutlinedActionBarButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: RoundedCornerShape,
    background: Color,
    accent: Color,
    contentPadding: PaddingValues,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        border = BorderStroke(
            width = 1.dp,
            color = accent.copy(alpha = if (enabled) 1f else 0.38f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = background,
            contentColor = accent,
            disabledContainerColor = background.copy(alpha = 0.45f),
            disabledContentColor = accent.copy(alpha = 0.38f)
        ),
        contentPadding = contentPadding,
        content = content
    )
}
