package io.legado.app.ui.design.theme

import android.content.Context
import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NgEffectThemeSelection
import io.legado.app.help.config.NgColorConfigStore
import io.legado.app.help.config.NgSoftGradientColorPreset
import io.legado.app.help.config.NgSoftGradientTheme
import io.legado.app.help.config.NgMulticolorTheme
import io.legado.app.help.config.NgThemeModeStore
import io.legado.app.help.config.NgThemePresentationMode
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.primaryColor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class NgLegacyThemeInput(
    @ColorInt val primaryColor: Int,
    @ColorInt val accentColor: Int,
    @ColorInt val backgroundColor: Int,
    @ColorInt val bottomBackground: Int,
    @ColorInt val errorColor: Int,
    val isDark: Boolean,
    val isEInk: Boolean
)

/**
 * 将常规主题或 NG 内置模式解析为稳定的组件语义。
 *
 * 这里不读写偏好、不修复旧状态，也不决定当前选择的是哪个主题。
 */
object NgThemeResolver {

    fun resolve(context: Context): NgThemeSnapshot {
        if (AppConfig.isEInkMode) return resolveEInk()
        if (NgThemeModeStore.current(context) == NgThemePresentationMode.SOFT_GRADIENT) {
            val snapshot = resolve(
                context = context,
                colors = NgSoftGradientTheme.colors(context),
                isDark = false,
            )
            return snapshot.copy(
                colors = snapshot.colors.copy(
                    selectedContainer = NgSoftGradientTheme.selectedContainer,
                ),
                backdropContent = NgBackdropContentTokens(
                    topNavigationActive = WHITE,
                    topNavigationInactive = SOFT_GRADIENT_INACTIVE_TOP_NAVIGATION,
                    primaryContent = WHITE,
                    secondaryContent = SOFT_GRADIENT_SECONDARY_BACKDROP_CONTENT,
                    textShadow = SOFT_GRADIENT_BACKDROP_TEXT_SHADOW,
                ),
                systemBars = snapshot.systemBars.copy(
                    darkStatusBarIcons = NgSoftGradientTheme.darkStatusBarIcons(context),
                ),
            )
        }
        if (NgThemeModeStore.current(context) == NgThemePresentationMode.MULTICOLOR) {
            return resolve(
                context = context,
                colors = NgMulticolorTheme.colors,
                isDark = false,
            ).let { snapshot ->
                snapshot.copy(
                    systemBars = snapshot.systemBars.copy(
                        darkStatusBarIcons = true,
                        darkNavigationBarIcons = true,
                    ),
                )
            }
        }
        if (NgThemeModeStore.current(context) == NgThemePresentationMode.EFFECT_THEME) {
            return resolve(
                context = context,
                colors = NgEffectThemeSelection.colors(context),
                isDark = ThemeConfig.isDarkTheme(context),
            )
        }
        return resolve(
            context = context,
            colors = NgColorConfigStore.current(context),
            isDark = ThemeConfig.isDarkTheme(context)
        )
    }

    fun resolve(
        context: Context,
        colors: NgColorSystem,
        isDark: Boolean
    ): NgThemeSnapshot {
        val resolved = resolveColorScheme(context, colors, isDark)
        return snapshot(resolved, isDark, false)
    }

    internal fun resolveColorScheme(
        context: Context,
        colors: NgColorSystem,
        isDark: Boolean
    ): NgColorScheme {
        val errorColor = ContextCompat.getColor(context, R.color.error)
        val resolved = when (colors.mode) {
            NgColorGenerationMode.PALETTE -> generatePaletteColorScheme(
                colors = colors,
                seed = if (isDark) colors.darkSeed else colors.lightSeed,
                isDark = isDark
            ).toNgColorScheme(
                topBarFromSecondary = false,
                isDark = isDark
            )

            NgColorGenerationMode.MANUAL -> manualNgColorScheme(
                manual = colors.manualColors(isDark),
                isDark = isDark,
                errorColor = errorColor
            )
        }
        return resolved.copy(
            onTopBar = when (colors.topBarTextMode(isDark)) {
                NgTopBarTextMode.AUTO -> resolved.onTopBar
                NgTopBarTextMode.LIGHT -> WHITE
                NgTopBarTextMode.DARK -> BLACK
            }
        )
    }

    fun resolve(input: NgLegacyThemeInput): NgThemeSnapshot {
        if (input.isEInk) return resolveEInk()
        val primary = NgColorMath.opaque(input.accentColor)
        val background = NgColorMath.opaque(input.backgroundColor)
        val surface = NgColorMath.opaque(input.bottomBackground)
        val topBar = NgColorMath.opaque(input.primaryColor)
        val onPrimary = NgColorMath.contentColorFor(primary)
        val onBackground = NgColorMath.contentColorFor(background)
        val onSurface = NgColorMath.contentColorFor(surface)
        val primaryContainer = NgColorMath.blend(
            background,
            primary,
            if (input.isDark) 0.34f else 0.16f
        )
        val surfaceVariant = NgColorMath.blend(
            surface,
            onSurface,
            if (input.isDark) 0.12f else 0.06f
        )
        val error = NgColorMath.opaque(input.errorColor)
        val errorContainer = NgColorMath.blend(
            background,
            error,
            if (input.isDark) 0.30f else 0.14f
        )
        val inverseSurface = if (input.isDark) LIGHT_INVERSE_SURFACE else DARK_INVERSE_SURFACE
        val colors = NgColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = NgColorMath.contentColorFor(primaryContainer),
            secondary = NgColorMath.blend(primary, onSurface, 0.30f),
            tertiary = NgColorMath.blend(primary, background, if (input.isDark) 0.14f else 0.08f),
            background = background,
            onBackground = onBackground,
            surface = surface,
            surfaceTint = primary,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = NgColorMath.blend(surface, onSurface, 0.72f),
            surfaceContainerLow = NgColorMath.blend(surface, onSurface, 0.02f),
            surfaceContainer = NgColorMath.blend(surface, onSurface, 0.05f),
            surfaceContainerHigh = NgColorMath.blend(surface, onSurface, 0.08f),
            outline = NgColorMath.blend(surface, onSurface, 0.44f),
            outlineVariant = NgColorMath.blend(surface, onSurface, 0.20f),
            error = error,
            onError = NgColorMath.contentColorFor(error),
            errorContainer = errorContainer,
            onErrorContainer = NgColorMath.contentColorFor(errorContainer),
            inverseSurface = inverseSurface,
            inverseOnSurface = NgColorMath.contentColorFor(inverseSurface),
            scrim = BLACK,
            topBarContainer = topBar,
            onTopBar = NgColorMath.contentColorFor(topBar),
            cardContainer = NgColorMath.withAlpha(surface, 0.90f),
            dialogContainer = NgColorMath.withAlpha(surface, 0.96f),
            drawerContainer = NgColorMath.withAlpha(surface, 0.94f),
            inputContainer = if (input.isDark) surfaceVariant else WHITE,
            selectedContainer = primaryContainer
        )
        return snapshot(colors, input.isDark, false)
    }

    private fun generatePaletteColorScheme(
        colors: NgColorSystem,
        @ColorInt seed: Int,
        isDark: Boolean
    ): ColorScheme {
        val source = Hct.fromInt(NgColorMath.opaque(seed))
        val specVersion = when (colors.colorSpec) {
            NgColorSpec.MATERIAL_3_2021 -> ColorSpec.SpecVersion.SPEC_2021
            NgColorSpec.MATERIAL_3_EXPRESSIVE_2025 -> ColorSpec.SpecVersion.SPEC_2025
        }
        val platform = DynamicScheme.Platform.PHONE
        val contrast = colors.contrast.value
        val scheme = when (colors.paletteStyle) {
            NgPaletteStyle.TONAL_SPOT -> SchemeTonalSpot(source, isDark, contrast, specVersion, platform)
            NgPaletteStyle.NEUTRAL -> SchemeNeutral(source, isDark, contrast, specVersion, platform)
            NgPaletteStyle.VIBRANT -> SchemeVibrant(source, isDark, contrast, specVersion, platform)
            NgPaletteStyle.EXPRESSIVE -> SchemeExpressive(source, isDark, contrast, specVersion, platform)
            NgPaletteStyle.RAINBOW -> SchemeRainbow(source, isDark, contrast, specVersion, platform)
            NgPaletteStyle.FRUIT_SALAD -> SchemeFruitSalad(source, isDark, contrast, specVersion, platform)
            NgPaletteStyle.MONOCHROME -> SchemeMonochrome(source, isDark, contrast, specVersion, platform)
            NgPaletteStyle.FIDELITY -> SchemeFidelity(source, isDark, contrast, specVersion, platform)
            NgPaletteStyle.CONTENT -> SchemeContent(source, isDark, contrast, specVersion, platform)
        }
        return scheme.toMaterialColorScheme()
    }

    private fun DynamicScheme.toMaterialColorScheme(): ColorScheme {
        val base = if (isDark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(onPrimaryContainer),
            inversePrimary = Color(inversePrimary),
            secondary = Color(secondary),
            onSecondary = Color(onSecondary),
            secondaryContainer = Color(secondaryContainer),
            onSecondaryContainer = Color(onSecondaryContainer),
            tertiary = Color(tertiary),
            onTertiary = Color(onTertiary),
            tertiaryContainer = Color(tertiaryContainer),
            onTertiaryContainer = Color(onTertiaryContainer),
            background = Color(background),
            onBackground = Color(onBackground),
            surface = Color(surface),
            onSurface = Color(onSurface),
            surfaceVariant = Color(surfaceVariant),
            onSurfaceVariant = Color(onSurfaceVariant),
            surfaceTint = Color(surfaceTint),
            inverseSurface = Color(inverseSurface),
            inverseOnSurface = Color(inverseOnSurface),
            error = Color(error),
            onError = Color(onError),
            errorContainer = Color(errorContainer),
            onErrorContainer = Color(onErrorContainer),
            outline = Color(outline),
            outlineVariant = Color(outlineVariant),
            scrim = Color(scrim),
            surfaceBright = Color(surfaceBright),
            surfaceDim = Color(surfaceDim),
            surfaceContainer = Color(surfaceContainer),
            surfaceContainerHigh = Color(surfaceContainerHigh),
            surfaceContainerHighest = Color(surfaceContainerHighest),
            surfaceContainerLow = Color(surfaceContainerLow),
            surfaceContainerLowest = Color(surfaceContainerLowest)
        )
    }

    private fun manualNgColorScheme(
        manual: NgManualColorSet,
        isDark: Boolean,
        @ColorInt errorColor: Int
    ): NgColorScheme {
        val primary = manual.primary
        val secondary = manual.secondary
        val background = manual.background
        val surface = manual.labelContainer
        val primaryContainer = NgColorMath.blend(
            background,
            primary,
            if (isDark) 0.34f else 0.16f
        )
        val surfaceVariant = NgColorMath.blend(
            surface,
            manual.secondaryText,
            if (isDark) 0.12f else 0.06f
        )
        val errorContainer = NgColorMath.blend(
            background,
            errorColor,
            if (isDark) 0.30f else 0.14f
        )
        val inverseSurface = if (isDark) LIGHT_INVERSE_SURFACE else DARK_INVERSE_SURFACE
        return NgColorScheme(
            primary = primary,
            onPrimary = manual.primaryText,
            primaryContainer = primaryContainer,
            onPrimaryContainer = manual.primaryText,
            secondary = secondary,
            tertiary = NgColorMath.blend(primary, background, if (isDark) 0.14f else 0.08f),
            background = background,
            onBackground = manual.primaryText,
            surface = surface,
            surfaceTint = primary,
            onSurface = manual.primaryText,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = manual.secondaryText,
            surfaceContainerLow = NgColorMath.blend(surface, manual.secondaryText, 0.02f),
            surfaceContainer = NgColorMath.blend(surface, manual.secondaryText, 0.05f),
            surfaceContainerHigh = NgColorMath.blend(surface, manual.secondaryText, 0.08f),
            outline = NgColorMath.blend(surface, manual.secondaryText, 0.44f),
            outlineVariant = NgColorMath.blend(surface, manual.secondaryText, 0.20f),
            error = errorColor,
            onError = NgColorMath.contentColorFor(errorColor),
            errorContainer = errorContainer,
            onErrorContainer = NgColorMath.contentColorFor(errorContainer),
            inverseSurface = inverseSurface,
            inverseOnSurface = NgColorMath.contentColorFor(inverseSurface),
            scrim = BLACK,
            topBarContainer = secondary,
            onTopBar = NgColorMath.contentColorFor(secondary),
            cardContainer = NgColorMath.withAlpha(surface, 0.90f),
            dialogContainer = NgColorMath.withAlpha(surface, 0.96f),
            drawerContainer = NgColorMath.withAlpha(surface, 0.94f),
            inputContainer = if (isDark) surfaceVariant else WHITE,
            selectedContainer = primaryContainer
        )
    }

    private fun ColorScheme.toNgColorScheme(
        topBarFromSecondary: Boolean,
        isDark: Boolean
    ) = NgColorScheme(
        primary = primary.toArgb(),
        onPrimary = onPrimary.toArgb(),
        primaryContainer = primaryContainer.toArgb(),
        onPrimaryContainer = onPrimaryContainer.toArgb(),
        secondary = secondary.toArgb(),
        tertiary = tertiary.toArgb(),
        background = background.toArgb(),
        onBackground = onBackground.toArgb(),
        surface = surface.toArgb(),
        surfaceTint = surfaceTint.toArgb(),
        onSurface = onSurface.toArgb(),
        surfaceVariant = surfaceVariant.toArgb(),
        onSurfaceVariant = onSurfaceVariant.toArgb(),
        surfaceContainerLow = surfaceContainerLow.toArgb(),
        surfaceContainer = surfaceContainer.toArgb(),
        surfaceContainerHigh = surfaceContainerHigh.toArgb(),
        outline = outline.toArgb(),
        outlineVariant = outlineVariant.toArgb(),
        error = error.toArgb(),
        onError = onError.toArgb(),
        errorContainer = errorContainer.toArgb(),
        onErrorContainer = onErrorContainer.toArgb(),
        inverseSurface = inverseSurface.toArgb(),
        inverseOnSurface = inverseOnSurface.toArgb(),
        scrim = scrim.toArgb(),
        topBarContainer = if (topBarFromSecondary) secondary.toArgb() else surface.toArgb(),
        onTopBar = if (topBarFromSecondary) onSecondary.toArgb() else onSurface.toArgb(),
        cardContainer = surfaceContainerLow.toArgb(),
        dialogContainer = surfaceContainerHigh.toArgb(),
        drawerContainer = surfaceContainer.toArgb(),
        inputContainer = if (isDark) surfaceContainerHigh.toArgb() else WHITE,
        selectedContainer = primaryContainer.toArgb()
    )

    private fun resolveEInk(): NgThemeSnapshot {
        val colors = NgColorScheme(
            primary = BLACK,
            onPrimary = WHITE,
            primaryContainer = WHITE,
            onPrimaryContainer = BLACK,
            secondary = BLACK,
            tertiary = BLACK,
            background = WHITE,
            onBackground = BLACK,
            surface = WHITE,
            surfaceTint = BLACK,
            onSurface = BLACK,
            surfaceVariant = WHITE,
            onSurfaceVariant = BLACK,
            surfaceContainerLow = WHITE,
            surfaceContainer = WHITE,
            surfaceContainerHigh = WHITE,
            outline = BLACK,
            outlineVariant = BLACK,
            error = BLACK,
            onError = WHITE,
            errorContainer = WHITE,
            onErrorContainer = BLACK,
            inverseSurface = BLACK,
            inverseOnSurface = WHITE,
            scrim = BLACK,
            topBarContainer = WHITE,
            onTopBar = BLACK,
            cardContainer = WHITE,
            dialogContainer = WHITE,
            drawerContainer = WHITE,
            inputContainer = WHITE,
            selectedContainer = WHITE
        )
        return snapshot(colors, isDark = false, isEInk = true)
    }

    private fun snapshot(
        colors: NgColorScheme,
        isDark: Boolean,
        isEInk: Boolean
    ) = NgThemeSnapshot(
        isDark = isDark,
        isEInk = isEInk,
        colors = colors,
        effects = if (isEInk) {
            NgEffectTokens(
                blurEnabled = false,
                containerAlpha = 1f,
                dialogAlpha = 1f,
                drawerAlpha = 1f,
                blurRadiusDp = 0,
                cardElevationDp = 0,
                overlayElevationDp = 0
            )
        } else {
            NgEffectTokens()
        },
        motion = if (isEInk) NgMotionTokens(false, 0, 0, 0) else NgMotionTokens(),
        systemBars = NgSystemBarTokens(
            darkStatusBarIcons = NgColorMath.isLight(colors.background),
            darkNavigationBarIcons = NgColorMath.isLight(colors.surface)
        )
    )

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val DARK_INVERSE_SURFACE = 0xFF313033.toInt()
    private const val LIGHT_INVERSE_SURFACE = 0xFFF2F0F2.toInt()
    private val SOFT_GRADIENT_INACTIVE_TOP_NAVIGATION = 0xB8FFFFFF.toInt()
    private val SOFT_GRADIENT_SECONDARY_BACKDROP_CONTENT = 0xD9FFFFFF.toInt()
    private val SOFT_GRADIENT_BACKDROP_TEXT_SHADOW = 0x52000000.toInt()
}

internal object NgColorMath {

    private const val DARK_CONTENT = 0xFF1D1B20.toInt()
    private const val LIGHT_CONTENT = 0xFFF8F5F8.toInt()

    fun opaque(@ColorInt color: Int): Int = color or 0xFF000000.toInt()

    fun withAlpha(@ColorInt color: Int, alpha: Float): Int {
        val clamped = alpha.coerceIn(0f, 1f)
        return (color and 0x00FFFFFF) or ((clamped * 255f).roundToInt() shl 24)
    }

    fun blend(@ColorInt start: Int, @ColorInt end: Int, ratio: Float): Int {
        val amount = ratio.coerceIn(0f, 1f)
        val inverse = 1f - amount
        return argb(
            alpha = (alpha(start) * inverse + alpha(end) * amount).roundToInt(),
            red = (red(start) * inverse + red(end) * amount).roundToInt(),
            green = (green(start) * inverse + green(end) * amount).roundToInt(),
            blue = (blue(start) * inverse + blue(end) * amount).roundToInt()
        )
    }

    fun scaleChroma(@ColorInt color: Int, fraction: Float): Int {
        val source = Hct.fromInt(opaque(color))
        val scaled = Hct.from(
            source.hue,
            source.chroma * fraction.coerceIn(0f, 1f),
            source.tone,
        ).toInt()
        return withAlpha(scaled, alpha(color) / 255f)
    }

    fun contentColorFor(@ColorInt background: Int): Int {
        return if (
            contrastRatio(background, DARK_CONTENT) >=
            contrastRatio(background, LIGHT_CONTENT)
        ) {
            DARK_CONTENT
        } else {
            LIGHT_CONTENT
        }
    }

    fun readableContentColor(
        @ColorInt background: Int,
        @ColorInt preferred: Int,
        minimumContrast: Double = 4.5,
    ): Int = if (contrastRatio(preferred, background) >= minimumContrast) {
        preferred
    } else {
        contentColorFor(background)
    }

    fun isLight(@ColorInt color: Int): Boolean = luminance(color) >= 0.5

    fun contrastRatio(@ColorInt first: Int, @ColorInt second: Int): Double {
        val lighter = max(luminance(first), luminance(second))
        val darker = min(luminance(first), luminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(@ColorInt color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) {
                normalized / 12.92
            } else {
                ((normalized + 0.055) / 1.055).pow(2.4)
            }
        }
        return 0.2126 * channel(red(color)) +
                0.7152 * channel(green(color)) +
                0.0722 * channel(blue(color))
    }

    private fun alpha(color: Int): Int = color ushr 24

    private fun red(color: Int): Int = color ushr 16 and 0xFF

    private fun green(color: Int): Int = color ushr 8 and 0xFF

    private fun blue(color: Int): Int = color and 0xFF

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
        return (alpha.coerceIn(0, 255) shl 24) or
                (red.coerceIn(0, 255) shl 16) or
                (green.coerceIn(0, 255) shl 8) or
                blue.coerceIn(0, 255)
    }
}
