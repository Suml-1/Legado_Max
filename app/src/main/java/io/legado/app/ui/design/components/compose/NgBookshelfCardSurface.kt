package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.BookshelfCardMaterial
import io.legado.app.help.config.BookshelfCardStyle
import io.legado.app.help.config.NgThemeModeStore
import io.legado.app.help.config.NgThemePresentationMode
import io.legado.app.help.config.NgVisualSystem
import io.legado.app.ui.design.theme.NgTheme

/** Bookshelf-specific material routing; never changes the shared white-card defaults. */
@Composable
fun NgBookshelfCardSurface(
    appearance: BookshelfCardStyle,
    pressed: Boolean,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val solid = colorResource(R.color.ng_surface_card)
    if (appearance.material == BookshelfCardMaterial.SOLID || NgTheme.snapshot.isEInk) {
        Box(
            modifier.clip(shape).background(
                if (pressed) colorResource(R.color.ng_surface_pressed) else solid,
            ).border(0.6.dp, colorResource(R.color.ng_bookshelf_list_card_stroke), shape),
        ) {
            Box(Modifier.fillMaxSize().padding(contentPadding), content = content)
        }
        return
    }
    val dark = NgTheme.snapshot.isDark
    val alpha = 1f - appearance.transparency / 100f
    val base = NgGlassDefaults.bookDetailStyle(solid.copy(alpha = alpha))
    val style = base.copy(
        containerTop = solid.copy(alpha = if (pressed) (alpha + 0.08f).coerceAtMost(1f) else alpha),
        containerBottom = solid.copy(alpha = if (pressed) (alpha + 0.08f).coerceAtMost(1f) else alpha),
    )
    val spec = NgLiquidGlassDefaults.spec(
        NgMaterialRole.CONTENT,
        NgThemeModeStore.current(LocalContext.current) == NgThemePresentationMode.SOFT_GRADIENT,
    ).let {
        // The slider controls surface opacity directly, without a second alpha multiplier.
        it.copy(surfaceAlphaScale = 1f, highlightAlphaScale = it.highlightAlphaScale * if (dark) 0.5f else 1f)
    }
    NgVisualSurface(
        modifier = modifier,
        role = NgMaterialRole.CONTENT,
        cornerRadius = cornerRadius,
        style = style,
        visualSystemOverride = if (appearance.material == BookshelfCardMaterial.LIQUID) {
            NgVisualSystem.LIQUID_GLASS
        } else NgVisualSystem.TRANSPARENT_GLASS,
        liquidSpecOverride = spec,
    ) {
        Box(Modifier.fillMaxSize().padding(contentPadding), content = content)
    }
}
