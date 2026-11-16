package io.legado.app.ui.design.theme

import androidx.annotation.ColorInt
import com.materialkolor.hct.Hct
import kotlin.math.abs

/** Keep destructive dialog actions distinct and readable on night-mode surfaces. */
internal object NgButtonTextPalette {
    @ColorInt
    fun resolveDanger(
        @ColorInt preferred: Int,
        @ColorInt background: Int,
        isDark: Boolean,
    ): Int {
        if (!isDark || NgColorMath.contrastRatio(preferred, background) >= 4.5) return preferred

        val source = Hct.fromInt(NgColorMath.opaque(preferred))
        return (0..100).asSequence()
            .map { tone ->
                val candidate = Hct.from(source.hue, source.chroma, tone.toDouble()).toInt()
                candidate to tone
            }
            .filter { (color, _) -> NgColorMath.contrastRatio(color, background) >= 4.5 }
            .minByOrNull { (_, tone) -> abs(tone - source.tone) }
            ?.first ?: NgColorMath.contentColorFor(background)
    }
}
