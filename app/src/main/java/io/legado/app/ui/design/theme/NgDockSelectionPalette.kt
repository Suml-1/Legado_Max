package io.legado.app.ui.design.theme

import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.materialkolor.hct.Hct
import io.legado.app.help.config.NgThemePresentationMode

/** Render-only colors; not persisted or part of a theme package protocol. */
data class NgDockSelectionColors(
    @param:ColorInt val container: Int,
    @param:ColorInt val content: Int,
)

/** Keep the selected surface in the primary hue, with enough depth for white content. */
internal object NgDockSelectionPalette {
    fun resolve(
        @ColorInt primary: Int,
        @ColorInt themeContainer: Int,
        isDark: Boolean,
        preserveExisting: Boolean = false,
    ): NgDockSelectionColors {
        if (preserveExisting) return NgDockSelectionColors(themeContainer, primary)
        val source = Hct.fromInt(NgColorMath.opaque(primary))
        // Do not introduce color into intentionally neutral seeds, or neon into vivid seeds.
        val chroma = source.chroma.coerceAtMost(48.0)
        // Leave headroom below tone 50 for readable white content after RGB quantization.
        val container = Hct.from(source.hue, chroma, if (isDark) 32.0 else 48.0).toInt()
        return NgDockSelectionColors(
            container = container,
            content = 0xFFFFFFFF.toInt(),
        )
    }
}

/** One cached color pair shared by the appearance form; no wallpaper reads or sampling. */
@Composable
internal fun rememberAppearanceDockSelectionColors(
    presentation: NgThemePresentationMode,
): NgDockSelectionColors {
    val snapshot = NgTheme.snapshot
    val colors = snapshot.colors
    return remember(presentation, colors.primary, colors.selectedContainer, snapshot.isDark, snapshot.isEInk) {
        NgDockSelectionPalette.resolve(
            primary = colors.primary,
            themeContainer = colors.selectedContainer,
            isDark = snapshot.isDark,
            preserveExisting = snapshot.isEInk || presentation == NgThemePresentationMode.SOFT_GRADIENT,
        )
    }
}
