package io.legado.app.ui.design.theme

import androidx.annotation.ColorInt
import com.materialkolor.hct.Hct
import io.legado.app.help.config.NgDrawerAppearanceConfig
import kotlin.math.abs
import kotlin.math.min

internal data class NgDrawerSurfaceColors(
    @param:ColorInt val top: Int,
    @param:ColorInt val bottom: Int,
)

internal data class NgDrawerImageMask(@param:ColorInt val color: Int, val alpha: Float)

internal data class NgDrawerSemanticColors(
    @param:ColorInt val content: Int,
    @param:ColorInt val secondaryContent: Int,
    @param:ColorInt val indicator: Int,
    @param:ColorInt val onIndicator: Int,
    @param:ColorInt val action: Int,
    @param:ColorInt val outline: Int,
)

/**
 * 全局 NG 抽屉自己的大面积材质色板。
 *
 * 日间沿用 [NgColorScheme.surfaceTint] 的受控染色；夜间保留主题容器自己的色相，
 * 只用明度区分承载层级，避免把强调色铺成大面积底色。阅读浮窗仍使用自己的色板。
 */
internal object NgDrawerPalette {

    private const val OPAQUE_WHITE = -0x1
    private const val DARK_CONTENT_CARD_LIFT = 0.12f
    private const val DARK_CONTENT_CARD_TONE_GAP = 4.0
    private const val TEXT_MIN_CONTRAST = 4.5
    private const val CONTROL_MIN_CONTRAST = 3.0
    private const val CONTROL_MAX_CONTRAST = 4.2

    private const val LIGHT_TOP_CHROMA_CAP = 32.0
    private const val LIGHT_BOTTOM_CHROMA_CAP = 42.0

    private const val TOP_CHROMA_REACH = 0.68
    private const val BOTTOM_CHROMA_REACH = 0.86
    private const val TOP_TONE_REACH = 0.28
    private const val BOTTOM_TONE_REACH = 0.44
    private const val TOP_HUE_SHIFT_REACH = 0.68
    private const val BOTTOM_HUE_SHIFT_REACH = 1.0

    /**
     * 红橙主题在高明度大面积表面上会比同色的小面积控件更容易呈现粉感。
     * 这里只对暖色高明度表面做很小的顺时针色相补偿，让材质保持橙色观感；
     * 冷色、低明度和主题本身的语义色均不受影响。
     */
    private const val WARM_SURFACE_HUE_CENTER = 48.0
    private const val WARM_SURFACE_HUE_RADIUS = 42.0
    private const val WARM_SURFACE_MAX_HUE_SHIFT = 18.0
    private const val WARM_SURFACE_TONE_START = 58.0
    private const val WARM_SURFACE_TONE_RANGE = 34.0

    fun resolveSurfaceColors(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int,
        @ColorInt backgroundColor: Int? = null,
    ): NgDrawerSurfaceColors {
        val colors = snapshot.colors
        if (!snapshot.isEInk && backgroundColor != null) {
            val background = NgColorMath.opaque(backgroundColor)
            val content = NgColorMath.contentColorFor(background)
            return NgDrawerSurfaceColors(
                top = NgColorMath.blend(background,
                    if (NgColorMath.isLight(content)) 0xFF000000.toInt() else OPAQUE_WHITE, 0.035f),
                bottom = background,
            )
        }
        val neutralTop = NgColorMath.blend(
            colors.drawerContainer,
            colors.surface,
            if (snapshot.isDark) 0.16f else 0.24f,
        )
        val strength = NgDrawerAppearanceConfig.strengthFraction(primaryStrengthPercent)
        if (snapshot.isEInk || snapshot.isDark || strength == 0.0) {
            return NgDrawerSurfaceColors(
                top = neutralTop,
                bottom = colors.drawerContainer,
            )
        }
        val seed = Hct.fromInt(NgColorMath.opaque(colors.surfaceTint))
        return NgDrawerSurfaceColors(
            top = resolveSurfaceTone(
                neutral = neutralTop,
                seed = seed,
                strength = strength,
                chromaReach = TOP_CHROMA_REACH,
                chromaCap = LIGHT_TOP_CHROMA_CAP,
                toneReach = TOP_TONE_REACH,
                hueShiftReach = TOP_HUE_SHIFT_REACH,
            ),
            bottom = resolveSurfaceTone(
                neutral = colors.drawerContainer,
                seed = seed,
                strength = strength,
                chromaReach = BOTTOM_CHROMA_REACH,
                chromaCap = LIGHT_BOTTOM_CHROMA_CAP,
                toneReach = BOTTOM_TONE_REACH,
                hueShiftReach = BOTTOM_HUE_SHIFT_REACH,
            ),
        )
    }

    fun resolveSemanticColors(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int,
        @ColorInt backgroundColor: Int? = null,
    ): NgDrawerSemanticColors {
        val surfaces = resolveSurfaceColors(snapshot, primaryStrengthPercent, backgroundColor)
        return resolveSemanticColors(
            snapshot = snapshot,
            primaryStrengthPercent = primaryStrengthPercent,
            backgrounds = intArrayOf(surfaces.top, surfaces.bottom),
        )
    }

    private fun resolveSemanticColors(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int,
        backgrounds: IntArray,
    ): NgDrawerSemanticColors {
        val colors = snapshot.colors
        val strength = NgDrawerAppearanceConfig.strengthFraction(primaryStrengthPercent)
        val content = findContrastingTone(
            preferred = colors.onSurface,
            backgrounds = backgrounds,
            contrastThreshold = TEXT_MIN_CONTRAST,
            preserveChroma = false,
        )
        val secondaryContent = findContrastingTone(
            preferred = colors.onSurfaceVariant,
            backgrounds = backgrounds,
            contrastThreshold = TEXT_MIN_CONTRAST,
            preserveChroma = false,
        )
        val indicatorContrast = CONTROL_MIN_CONTRAST +
            (CONTROL_MAX_CONTRAST - CONTROL_MIN_CONTRAST) * strength
        val indicator = findContrastingTone(
            preferred = colors.primary,
            backgrounds = backgrounds,
            contrastThreshold = indicatorContrast,
            preserveChroma = true,
        )
        val action = findContrastingTone(
            preferred = colors.secondary,
            backgrounds = backgrounds,
            contrastThreshold = TEXT_MIN_CONTRAST,
            preserveChroma = true,
        )
        val outline = findContrastingTone(
            preferred = colors.outline,
            backgrounds = backgrounds,
            contrastThreshold = CONTROL_MIN_CONTRAST,
            preserveChroma = false,
        )
        return NgDrawerSemanticColors(
            content = content,
            secondaryContent = secondaryContent,
            indicator = indicator,
            onIndicator = NgColorMath.contentColorFor(indicator),
            action = action,
            outline = outline,
        )
    }

    fun applySemanticRoles(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int,
        @ColorInt backgroundColor: Int? = null,
    ): NgThemeSnapshot = if (backgroundColor != null && !snapshot.isEInk) {
        applyBackgroundRoles(snapshot, primaryStrengthPercent, backgroundColor)
    } else applyResolvedSemanticRoles(
        snapshot = snapshot,
        primaryStrengthPercent = primaryStrengthPercent,
        contentCardContainer = null,
    )

    /** 日间白卡、夜间主题容器；显式自定义底色仍按所选颜色派生。 */
    fun applyAdaptiveContentCardRoles(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int,
        @ColorInt backgroundColor: Int? = null,
    ): NgThemeSnapshot = if (backgroundColor != null && !snapshot.isEInk) {
        applyBackgroundRoles(snapshot, primaryStrengthPercent, backgroundColor)
    } else applyResolvedSemanticRoles(
        snapshot = snapshot,
        primaryStrengthPercent = primaryStrengthPercent,
        contentCardContainer = resolveAdaptiveContentCardColor(
            snapshot = snapshot,
            primaryStrengthPercent = primaryStrengthPercent,
        ),
    )

    @ColorInt
    fun resolveAdaptiveContentCardColor(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int,
        @ColorInt backgroundColor: Int? = null,
    ): Int {
        if (snapshot.isEInk) return OPAQUE_WHITE
        if (backgroundColor != null) {
            val background = NgColorMath.opaque(backgroundColor)
            val content = NgColorMath.contentColorFor(background)
            val lifted = NgColorMath.blend(background, OPAQUE_WHITE, DARK_CONTENT_CARD_LIFT)
            return if (NgColorMath.contrastRatio(content, lifted) >= TEXT_MIN_CONTRAST) lifted
                else NgColorMath.blend(background, 0xFF000000.toInt(), 0.08f)
        }
        if (!snapshot.isDark) return OPAQUE_WHITE
        val cardColor = NgColorMath.opaque(snapshot.colors.cardContainer)
        val card = Hct.fromInt(cardColor)
        val drawer = Hct.fromInt(NgColorMath.opaque(snapshot.colors.drawerContainer))
        val minimumTone = (drawer.tone + DARK_CONTENT_CARD_TONE_GAP).coerceAtMost(100.0)
        val red = (cardColor ushr 16) and 0xFF
        val green = (cardColor ushr 8) and 0xFF
        val blue = cardColor and 0xFF
        // RGB 灰阶的 HCT 色度并非严格为零，提亮时显式保灰，避免出现轻微色偏。
        val chroma = if (red == green && green == blue) 0.0 else card.chroma
        // 已有足够层次时保留原色；否则只抬明度，不混白或向强调色偏移。
        return if (card.tone >= minimumTone) cardColor
            else Hct.from(card.hue, chroma, minimumTone).toInt()
    }

    private fun applyResolvedSemanticRoles(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int,
        @ColorInt contentCardContainer: Int?,
    ): NgThemeSnapshot {
        val surfaces = resolveSurfaceColors(snapshot, primaryStrengthPercent)
        val backgrounds = if (contentCardContainer == null) {
            intArrayOf(surfaces.top, surfaces.bottom)
        } else {
            intArrayOf(surfaces.top, surfaces.bottom, contentCardContainer)
        }
        val semantic = resolveSemanticColors(
            snapshot = snapshot,
            primaryStrengthPercent = primaryStrengthPercent,
            backgrounds = backgrounds,
        )
        val colors = snapshot.colors
        val indicatorContainer = NgColorMath.blend(
            surfaces.bottom,
            semantic.indicator,
            if (snapshot.isDark) 0.28f else 0.14f,
        )
        return snapshot.copy(
            colors = colors.copy(
                primary = semantic.indicator,
                onPrimary = semantic.onIndicator,
                primaryContainer = indicatorContainer,
                onPrimaryContainer = NgColorMath.contentColorFor(indicatorContainer),
                secondary = semantic.action,
                onSurface = semantic.content,
                onSurfaceVariant = semantic.secondaryContent,
                outline = semantic.outline,
                outlineVariant = NgColorMath.blend(
                    surfaces.bottom,
                    semantic.outline,
                    0.48f,
                ),
                onTopBar = semantic.content,
                cardContainer = contentCardContainer ?: colors.cardContainer,
                selectedContainer = indicatorContainer,
            )
        )
    }

    /** Only the background is customized; all content roles remain automatically readable. */
    private fun applyBackgroundRoles(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int,
        @ColorInt backgroundColor: Int,
    ): NgThemeSnapshot {
        val surfaces = resolveSurfaceColors(snapshot, primaryStrengthPercent, backgroundColor)
        val card = resolveAdaptiveContentCardColor(snapshot, primaryStrengthPercent, backgroundColor)
        val semantic = resolveSemanticColors(snapshot, primaryStrengthPercent,
            intArrayOf(surfaces.top, surfaces.bottom, card))
        val selected = NgColorMath.blend(surfaces.bottom, semantic.indicator,
            if (snapshot.isDark) 0.28f else 0.14f)
        return snapshot.copy(colors = snapshot.colors.copy(
            primary = semantic.indicator,
            onPrimary = NgColorMath.readableContentColor(semantic.indicator, surfaces.bottom),
            primaryContainer = selected,
            onPrimaryContainer = NgColorMath.readableContentColor(selected, semantic.content),
            secondary = semantic.action,
            surface = card,
            surfaceVariant = card,
            surfaceContainerLow = surfaces.top,
            surfaceContainer = card,
            surfaceContainerHigh = card,
            onSurface = semantic.content,
            onSurfaceVariant = semantic.secondaryContent,
            onTopBar = semantic.content,
            cardContainer = card,
            dialogContainer = surfaces.bottom,
            drawerContainer = surfaces.bottom,
            inputContainer = card,
            selectedContainer = selected,
            outline = semantic.outline,
            outlineVariant = NgColorMath.blend(surfaces.bottom, semantic.outline, 0.48f),
        ))
    }

    /** A light neutral veil preserves the artwork; content cards own their opaque surfaces. */
    fun resolveImageMask(snapshot: NgThemeSnapshot): NgDrawerImageMask {
        val content = intArrayOf(snapshot.colors.onSurface, snapshot.colors.onSurfaceVariant)
        val black = 0xFF000000.toInt()
        fun contrast(background: Int) = content.minOf { NgColorMath.contrastRatio(it, background) }
        val darkMask = contrast(black) >= contrast(OPAQUE_WHITE)
        return NgDrawerImageMask(
            color = if (darkMask) black else OPAQUE_WHITE,
            alpha = if (darkMask) 0.18f else 0.12f,
        )
    }

    @ColorInt
    private fun resolveSurfaceTone(
        @ColorInt neutral: Int,
        seed: Hct,
        strength: Double,
        chromaReach: Double,
        chromaCap: Double,
        toneReach: Double,
        hueShiftReach: Double,
    ): Int {
        val base = Hct.fromInt(NgColorMath.opaque(neutral))
        val targetChroma = min(seed.chroma * chromaReach, chromaCap)
        val chroma = base.chroma + (targetChroma - base.chroma) * strength
        val tone = base.tone + (seed.tone - base.tone) * toneReach * strength
        val hue = resolveSurfaceHue(
            seedHue = seed.hue,
            baseTone = base.tone,
            strength = strength,
            hueShiftReach = hueShiftReach,
        )
        return Hct.from(hue, chroma, tone).toInt()
    }

    private fun resolveSurfaceHue(
        seedHue: Double,
        baseTone: Double,
        strength: Double,
        hueShiftReach: Double,
    ): Double {
        val warmWeight = (
            1.0 - hueDistance(seedHue, WARM_SURFACE_HUE_CENTER) /
                WARM_SURFACE_HUE_RADIUS
            ).coerceIn(0.0, 1.0)
        val highToneWeight = (
            (baseTone - WARM_SURFACE_TONE_START) / WARM_SURFACE_TONE_RANGE
            ).coerceIn(0.0, 1.0)
        return (
            seedHue + WARM_SURFACE_MAX_HUE_SHIFT * warmWeight * highToneWeight *
                strength * hueShiftReach
            ) % 360.0
    }

    private fun hueDistance(first: Double, second: Double): Double {
        val direct = abs(first - second)
        return min(direct, 360.0 - direct)
    }

    @ColorInt
    private fun findContrastingTone(
        @ColorInt preferred: Int,
        backgrounds: IntArray,
        contrastThreshold: Double,
        preserveChroma: Boolean,
    ): Int {
        val opaquePreferred = NgColorMath.opaque(preferred)
        if (minimumContrast(opaquePreferred, backgrounds) >= contrastThreshold) {
            return opaquePreferred
        }
        val source = Hct.fromInt(opaquePreferred)
        val chromaSteps = if (preserveChroma) {
            doubleArrayOf(1.0, 0.75, 0.5, 0.25, 0.0)
        } else {
            doubleArrayOf(0.0)
        }
        chromaSteps.forEach { chromaFraction ->
            val candidate = (0..100)
                .asSequence()
                .map { tone ->
                    val color = Hct.from(
                        source.hue,
                        source.chroma * chromaFraction,
                        tone.toDouble(),
                    ).toInt()
                    Triple(color, tone, minimumContrast(color, backgrounds))
                }
                .filter { it.third >= contrastThreshold }
                .minWithOrNull(
                    compareBy<Triple<Int, Int, Double>>(
                        { abs(it.second - source.tone) },
                        { -it.third },
                    )
                )
            if (candidate != null) return candidate.first
        }
        return if (
            minimumContrast(0xFF000000.toInt(), backgrounds) >=
            minimumContrast(0xFFFFFFFF.toInt(), backgrounds)
        ) {
            0xFF000000.toInt()
        } else {
            0xFFFFFFFF.toInt()
        }
    }

    private fun minimumContrast(
        @ColorInt foreground: Int,
        backgrounds: IntArray,
    ): Double = backgrounds.minOf { background ->
        NgColorMath.contrastRatio(foreground, background)
    }
}
