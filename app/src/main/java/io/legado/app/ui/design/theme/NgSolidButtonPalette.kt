package io.legado.app.ui.design.theme

import androidx.annotation.ColorInt
import com.materialkolor.hct.Hct

internal data class NgLightActionColors(
    @param:ColorInt val container: Int,
    @param:ColorInt val content: Int,
)

/** Resolve readable solid-action color pairs without changing unaffected themes. */
internal object NgSolidButtonPalette {
    private const val MIN_TEXT_CONTRAST = 4.5
    private const val MAX_LIGHT_CONTENT_CONTRAST = 1.25
    private val white = 0xFFFFFFFF.toInt()

    /** A light tonal action surface paired with the current theme's dark surface color. */
    fun resolveLightTonalAction(@ColorInt primary: Int, @ColorInt surface: Int): NgLightActionColors {
        val opaquePrimary = NgColorMath.opaque(primary)
        val source = Hct.fromInt(opaquePrimary)
        val container = if (source.tone >= 90.0 && source.chroma <= 24.0) {
            opaquePrimary
        } else {
            Hct.from(source.hue, source.chroma.coerceAtMost(24.0), 90.0).toInt()
        }
        val darkSurface = NgColorMath.opaque(surface)
        val content = if (!NgColorMath.isLight(darkSurface) &&
            NgColorMath.contrastRatio(container, darkSurface) >= MIN_TEXT_CONTRAST
        ) darkSurface else NgColorMath.contentColorFor(container)
        return NgLightActionColors(container, content)
    }

    fun resolvePrimary(
        @ColorInt container: Int,
        @ColorInt content: Int,
        @ColorInt surface: Int,
        isDark: Boolean,
    ): NgLightActionColors {
        if (!isDark || NgColorMath.contrastRatio(container, content) >= MIN_TEXT_CONTRAST ||
            NgColorMath.contrastRatio(content, white) > MAX_LIGHT_CONTENT_CONTRAST
        ) return NgLightActionColors(container, content)
        return resolveLightTonalAction(container, surface)
    }

    @ColorInt
    fun resolveContainer(
        @ColorInt container: Int,
        @ColorInt content: Int,
        isDark: Boolean,
    ): Int {
        if (!isDark || NgColorMath.contrastRatio(container, content) >= MIN_TEXT_CONTRAST ||
            NgColorMath.contrastRatio(content, white) > MAX_LIGHT_CONTENT_CONTRAST
        ) return container

        val source = Hct.fromInt(NgColorMath.opaque(container))
        var tone = minOf(source.tone, 48.0)
        while (tone >= 0.0) {
            val candidate = Hct.from(source.hue, source.chroma, tone).toInt()
            if (NgColorMath.contrastRatio(candidate, content) >= MIN_TEXT_CONTRAST) {
                return candidate
            }
            tone -= 1.0
        }
        return 0xFF000000.toInt()
    }
}
