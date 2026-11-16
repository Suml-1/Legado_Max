package io.legado.app.ui.design.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
internal fun ngBackdropPrimaryTextStyle(fallbackColor: Color): TextStyle =
    ngBackdropTextStyle(
        contentColor = NgTheme.snapshot.backdropContent.primaryContent,
        fallbackColor = fallbackColor,
    )

@Composable
internal fun ngBackdropSecondaryTextStyle(fallbackColor: Color): TextStyle =
    ngBackdropTextStyle(
        contentColor = NgTheme.snapshot.backdropContent.secondaryContent,
        fallbackColor = fallbackColor,
    )

@Composable
private fun ngBackdropTextStyle(
    contentColor: Int?,
    fallbackColor: Color,
): TextStyle {
    val tokens = NgTheme.snapshot.backdropContent
    val density = LocalDensity.current
    val shadow = tokens.textShadow?.let { shadowColor ->
        Shadow(
            color = Color(shadowColor),
            offset = Offset(0f, with(density) { 0.5.dp.toPx() }),
            blurRadius = with(density) { 1.5.dp.toPx() },
        )
    }
    return TextStyle(
        fontFamily = NgTheme.fontFamily,
        color = contentColor?.let(::Color) ?: fallbackColor,
        shadow = shadow,
    )
}
