package io.legado.app.ui.book.read

import androidx.annotation.ColorInt
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import io.legado.app.help.config.ReadFloatingAppearanceConfig
import io.legado.app.help.config.ReadFloatingColorStyle
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeVibrant
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgThemeSnapshot
import kotlin.math.abs

internal data class ReadFloatingSurfaceColors(
    @ColorInt val top: Int,
    @ColorInt val bottom: Int,
)

internal data class ReadFloatingSemanticColors(
    @ColorInt val content: Int,
    @ColorInt val secondaryContent: Int,
    @ColorInt val indicator: Int,
    @ColorInt val onIndicator: Int,
    @ColorInt val action: Int,
    @ColorInt val outline: Int,
)

private data class ReadFloatingStyleSeeds(
    @ColorInt val indicator: Int,
    @ColorInt val action: Int,
)

/** 阅读预设自己的浮窗色板，不写回应用主题。 */
internal object ReadFloatingPalette {

    private const val TEXT_MIN_CONTRAST = 4.5
    private const val CONTROL_MIN_CONTRAST = 3.0
    private const val INDICATOR_MAX_CONTRAST = 4.2
    private const val INDICATOR_MIN_CHROMA_FRACTION = 0.15
    private const val LIGHT_BASE_BOTTOM_TINT_FRACTION = 0.13f
    private const val DARK_BASE_BOTTOM_TINT_FRACTION = 0.10f
    private const val ENHANCED_TOP_TINT_FRACTION = 0.22f
    private const val ENHANCED_BOTTOM_TINT_FRACTION = 0.50f

    private data class SemanticMemo(
        val snapshot: NgThemeSnapshot,
        val strength: Int?,
        val style: ReadFloatingColorStyle,
        val colors: ReadFloatingSemanticColors,
    )

    @Volatile
    private var semanticMemo: SemanticMemo? = null

    fun applySeed(
        base: NgThemeSnapshot,
        @ColorInt seed: Int,
    ): NgThemeSnapshot {
        if (seed == 0) return base
        val primary = NgColorMath.opaque(seed)
        val colors = base.colors
        val primaryContainer = NgColorMath.blend(
            colors.background,
            primary,
            if (base.isDark) 0.34f else 0.16f,
        )
        return base.copy(
            colors = colors.copy(
                primary = primary,
                onPrimary = NgColorMath.contentColorFor(primary),
                surfaceTint = primary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = NgColorMath.contentColorFor(primaryContainer),
                selectedContainer = primaryContainer,
            )
        )
    }

    /**
     * 主色浓度使用三锚点曲线：0% 中性、50% 原生浮窗材质、100% 增强材质。
     * 两段都线性变化；增强端保留中性承载色，避免整面退化成纯主色。
     */
    fun resolveSurfaceColors(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int? = null,
    ): ReadFloatingSurfaceColors {
        val colors = snapshot.colors
        val neutralTop = NgColorMath.blend(
            colors.drawerContainer,
            colors.surface,
            if (snapshot.isDark) 0.16f else 0.24f,
        )
        if (primaryStrengthPercent == null) {
            return ReadFloatingSurfaceColors(
                top = neutralTop,
                bottom = NgColorMath.blend(
                    colors.drawerContainer,
                    colors.surfaceTint,
                    if (snapshot.isDark) 0.10f else 0.13f,
                ),
            )
        }
        val baseBottomTint = if (snapshot.isDark) {
            DARK_BASE_BOTTOM_TINT_FRACTION
        } else {
            LIGHT_BASE_BOTTOM_TINT_FRACTION
        }
        val baseProgress = ReadFloatingAppearanceConfig.primaryStrengthBaseProgress(
            primaryStrengthPercent
        )
        val enhanceProgress = ReadFloatingAppearanceConfig.primaryStrengthEnhanceProgress(
            primaryStrengthPercent
        )
        val topTint = ENHANCED_TOP_TINT_FRACTION * enhanceProgress
        val bottomTint = if (enhanceProgress == 0f) {
            baseBottomTint * baseProgress
        } else {
            baseBottomTint +
                (ENHANCED_BOTTOM_TINT_FRACTION - baseBottomTint) * enhanceProgress
        }
        return ReadFloatingSurfaceColors(
            top = NgColorMath.blend(neutralTop, colors.surfaceTint, topTint),
            bottom = NgColorMath.blend(colors.drawerContainer, colors.surfaceTint, bottomTint),
        )
    }

    /**
     * 为阅读浮窗单独派生内容色与操作色，不读取也不写回主界面配色配置。
     *
     * 文字操作色保持 4.5:1；滑轨、Dock、开关和选中态使用独立指示色。
     * 指示色的色度与目标对比度随主色浓度连续增强，避免 50% 与 100% 都被
     * 压进同一档深色文字，同时保证非文字控件至少达到 3:1。
     */
    fun resolveSemanticColors(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int? = null,
        colorStyle: ReadFloatingColorStyle = ReadFloatingColorStyle.VIBRANT,
    ): ReadFloatingSemanticColors {
        // These snapshots and results contain only immutable values. Publish one
        // complete entry so callers on different threads cannot observe a partial key.
        semanticMemo?.let { memo ->
            if (memo.snapshot == snapshot && memo.strength == primaryStrengthPercent && memo.style == colorStyle)
                return memo.colors
        }
        val surfaces = resolveSurfaceColors(snapshot, primaryStrengthPercent)
        val backgrounds = intArrayOf(surfaces.top, surfaces.bottom)
        val colors = snapshot.colors
        val strength = ReadFloatingAppearanceConfig.primaryStrengthFraction(
            primaryStrengthPercent ?: ReadFloatingAppearanceConfig.DEFAULT_PRIMARY_STRENGTH_PERCENT
        ).toDouble()
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
        val styleSeeds = resolveStyleSeeds(
            seed = colors.surfaceTint,
            style = colorStyle,
            isDark = snapshot.isDark,
        )
        val action = findContrastingTone(
            preferred = styleSeeds.action,
            backgrounds = backgrounds,
            contrastThreshold = TEXT_MIN_CONTRAST,
            preserveChroma = true,
        )
        val source = Hct.fromInt(NgColorMath.opaque(styleSeeds.indicator))
        val chromaScale = ReadFloatingAppearanceConfig.primaryStrengthChromaScale(
            primaryStrengthPercent ?: ReadFloatingAppearanceConfig.DEFAULT_PRIMARY_STRENGTH_PERCENT
        ).toDouble()
        val indicatorChromaFraction = if (chromaScale <= 1.0) {
            INDICATOR_MIN_CHROMA_FRACTION +
                (1.0 - INDICATOR_MIN_CHROMA_FRACTION) * chromaScale
        } else {
            chromaScale
        }
        val indicatorPreferred = Hct.from(
            source.hue,
            source.chroma * indicatorChromaFraction,
            source.tone,
        ).toInt()
        val indicatorContrast = CONTROL_MIN_CONTRAST +
            (INDICATOR_MAX_CONTRAST - CONTROL_MIN_CONTRAST) * strength
        val indicator = findContrastingTone(
            preferred = indicatorPreferred,
            backgrounds = backgrounds,
            contrastThreshold = indicatorContrast,
            preserveChroma = true,
        )
        val outline = findContrastingTone(
            preferred = colors.outline,
            backgrounds = backgrounds,
            contrastThreshold = CONTROL_MIN_CONTRAST,
            preserveChroma = false,
        )
        return ReadFloatingSemanticColors(
            content = content,
            secondaryContent = secondaryContent,
            indicator = indicator,
            onIndicator = NgColorMath.contentColorFor(indicator),
            action = action,
            outline = outline,
        ).also { semanticMemo = SemanticMemo(snapshot, primaryStrengthPercent, colorStyle, it) }
    }

    fun applySemanticRoles(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int,
        colorStyle: ReadFloatingColorStyle = ReadFloatingColorStyle.VIBRANT,
    ): NgThemeSnapshot {
        val semantic = resolveSemanticColors(
            snapshot = snapshot,
            primaryStrengthPercent = primaryStrengthPercent,
            colorStyle = colorStyle,
        )
        val colors = snapshot.colors
        val surfaces = resolveSurfaceColors(snapshot, primaryStrengthPercent)
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
                    resolveSurfaceColors(snapshot, primaryStrengthPercent).bottom,
                    semantic.outline,
                    0.48f,
                ),
                onTopBar = semantic.content,
                selectedContainer = indicatorContainer,
            )
        )
    }

    /** Keep destructive text readable against both ends of the reading dialog gradient. */
    @ColorInt
    fun resolveDangerTextColor(snapshot: NgThemeSnapshot, primaryStrengthPercent: Int): Int {
        val surfaces = resolveSurfaceColors(snapshot, primaryStrengthPercent)
        return findContrastingTone(
            preferred = snapshot.colors.error,
            backgrounds = intArrayOf(surfaces.top, surfaces.bottom),
            contrastThreshold = TEXT_MIN_CONTRAST,
            preserveChroma = true,
        )
    }

    private fun resolveStyleSeeds(
        @ColorInt seed: Int,
        style: ReadFloatingColorStyle,
        isDark: Boolean,
    ): ReadFloatingStyleSeeds {
        val source = Hct.fromInt(NgColorMath.opaque(seed))
        val scheme: DynamicScheme = when (style) {
            ReadFloatingColorStyle.VIBRANT -> SchemeVibrant(
                source,
                isDark,
                0.0,
                ColorSpec.SpecVersion.SPEC_2021,
                DynamicScheme.Platform.PHONE,
            )

            ReadFloatingColorStyle.EXPRESSIVE -> SchemeExpressive(
                source,
                isDark,
                0.0,
                ColorSpec.SpecVersion.SPEC_2021,
                DynamicScheme.Platform.PHONE,
            )

            ReadFloatingColorStyle.RAINBOW -> SchemeRainbow(
                source,
                isDark,
                0.0,
                ColorSpec.SpecVersion.SPEC_2021,
                DynamicScheme.Platform.PHONE,
            )

            ReadFloatingColorStyle.FRUIT_SALAD -> SchemeFruitSalad(
                source,
                isDark,
                0.0,
                ColorSpec.SpecVersion.SPEC_2021,
                DynamicScheme.Platform.PHONE,
            )
        }
        return ReadFloatingStyleSeeds(
            indicator = scheme.primary,
            action = scheme.secondary,
        )
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
            floatArrayOf(1f, 0.75f, 0.5f, 0.25f, 0f)
        } else {
            floatArrayOf(0f)
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
