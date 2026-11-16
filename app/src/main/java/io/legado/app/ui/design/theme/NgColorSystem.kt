package io.legado.app.ui.design.theme

import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import com.google.gson.annotations.SerializedName
import io.legado.app.R

/**
 * NG 配色只描述颜色来源，不承载背景图、透明度、Dock 或主题模式。
 */
enum class NgColorGenerationMode {
    PALETTE,
    MANUAL
}

enum class NgPaletteStyle {
    TONAL_SPOT,
    NEUTRAL,
    VIBRANT,
    EXPRESSIVE,
    RAINBOW,
    FRUIT_SALAD,
    MONOCHROME,
    FIDELITY,
    CONTENT
}

enum class NgContrastLevel(val value: Double) {
    DEFAULT(0.0),
    MEDIUM(0.5),
    HIGH(1.0)
}

enum class NgColorSpec {
    MATERIAL_3_2021,
    MATERIAL_3_EXPRESSIVE_2025
}

/**
 * 顶栏文字不是第七个基础色，而是由当前配色派生出的内容色策略。
 */
enum class NgTopBarTextMode {
    AUTO,
    LIGHT,
    DARK
}

data class NgManualColorSet(
    @SerializedName("primary") @ColorInt val primary: Int,
    @SerializedName("secondary") @ColorInt val secondary: Int,
    @SerializedName("primaryText") @ColorInt val primaryText: Int,
    @SerializedName("secondaryText") @ColorInt val secondaryText: Int,
    @SerializedName("background") @ColorInt val background: Int,
    @SerializedName("labelContainer") @ColorInt val labelContainer: Int
)

data class NgColorSystem(
    @SerializedName("mode") val mode: NgColorGenerationMode,
    @SerializedName("lightSeed") @ColorInt val lightSeed: Int,
    @SerializedName("darkSeed") @ColorInt val darkSeed: Int,
    @SerializedName("paletteStyle") val paletteStyle: NgPaletteStyle,
    @SerializedName("contrast") val contrast: NgContrastLevel,
    @SerializedName("colorSpec") val colorSpec: NgColorSpec,
    @SerializedName("manualLight") val manualLight: NgManualColorSet,
    @SerializedName("manualDark") val manualDark: NgManualColorSet,
    @SerializedName("lightTopBarTextMode")
    val lightTopBarTextMode: NgTopBarTextMode = NgTopBarTextMode.AUTO,
    @SerializedName("darkTopBarTextMode")
    val darkTopBarTextMode: NgTopBarTextMode = NgTopBarTextMode.AUTO
) {
    fun manualColors(isDark: Boolean): NgManualColorSet =
        if (isDark) manualDark else manualLight

    fun topBarTextMode(isDark: Boolean): NgTopBarTextMode =
        if (isDark) darkTopBarTextMode else lightTopBarTextMode

    fun normalized(): NgColorSystem = copy(
        lightSeed = NgColorMath.opaque(lightSeed),
        darkSeed = NgColorMath.opaque(darkSeed)
    )
}

/**
 * 内置预设只是色板生成参数的快捷组合，不引入第三套运行模式。
 * 应用预设后仍可继续修改种子、风格、对比度或规范；任一参数改变后即视为自定义。
 */
internal data class NgColorPreset(
    val id: String,
    @StringRes val nameRes: Int,
    @ColorInt val lightSeed: Int,
    @ColorInt val darkSeed: Int,
    val paletteStyle: NgPaletteStyle = NgPaletteStyle.TONAL_SPOT,
    val contrast: NgContrastLevel = NgContrastLevel.DEFAULT,
    val colorSpec: NgColorSpec = NgColorSpec.MATERIAL_3_2021
) {
    fun applyTo(colors: NgColorSystem): NgColorSystem = colors.copy(
        mode = NgColorGenerationMode.PALETTE,
        lightSeed = lightSeed,
        darkSeed = darkSeed,
        paletteStyle = paletteStyle,
        contrast = contrast,
        colorSpec = colorSpec
    ).normalized()

    fun matches(colors: NgColorSystem): Boolean =
        colors.mode == NgColorGenerationMode.PALETTE &&
            colors.lightSeed == lightSeed &&
            colors.darkSeed == darkSeed &&
            colors.paletteStyle == paletteStyle &&
            colors.contrast == contrast &&
            colors.colorSpec == colorSpec
}

internal object NgBuiltInColorPresets {
    val all = listOf(
        NgColorPreset(
            id = "colors.grass",
            nameRes = R.string.ng_scheme_grass,
            lightSeed = 0xFF496A2E.toInt(),
            darkSeed = 0xFFB1D18A.toInt()
        ),
        NgColorPreset(
            id = "colors.lemon",
            nameRes = R.string.ng_scheme_lemon,
            lightSeed = 0xFF6D5E0F.toInt(),
            darkSeed = 0xFFDBC66E.toInt()
        ),
        NgColorPreset(
            id = "colors.monochrome",
            nameRes = R.string.ng_scheme_monochrome,
            lightSeed = 0xFF5C5C5C.toInt(),
            darkSeed = 0xFFC7C6C6.toInt(),
            paletteStyle = NgPaletteStyle.MONOCHROME
        ),
        NgColorPreset(
            id = "colors.clear_sky",
            nameRes = R.string.ng_scheme_clear_sky,
            lightSeed = 0xFF3B608F.toInt(),
            darkSeed = 0xFFA5C9FE.toInt()
        ),
        NgColorPreset(
            id = "colors.august",
            nameRes = R.string.ng_scheme_august,
            lightSeed = 0xFF8F4C37.toInt(),
            darkSeed = 0xFFFFB59F.toInt()
        ),
        NgColorPreset(
            id = "colors.new_wave",
            nameRes = R.string.ng_scheme_new_wave,
            lightSeed = 0xFF8B4A62.toInt(),
            darkSeed = 0xFFFFB0CA.toInt()
        ),
        NgColorPreset(
            id = "colors.spring",
            nameRes = R.string.ng_scheme_spring,
            lightSeed = 0xFF8F4A4D.toInt(),
            darkSeed = 0xFFFFB3B4.toInt()
        ),
        NgColorPreset(
            id = "colors.millennium",
            nameRes = R.string.ng_scheme_millennium,
            lightSeed = 0xFF565992.toInt(),
            darkSeed = 0xFFBFC2FF.toInt()
        ),
        NgColorPreset(
            id = "colors.hidden_sea_order",
            nameRes = R.string.ng_scheme_hidden_sea_order,
            lightSeed = 0xFF6B5E10.toInt(),
            darkSeed = 0xFFD9C76F.toInt()
        ),
        NgColorPreset(
            id = "colors.band",
            nameRes = R.string.ng_scheme_band,
            lightSeed = 0xFF8E4958.toInt(),
            darkSeed = 0xFFFFB1C0.toInt()
        )
    )

    fun matching(colors: NgColorSystem): NgColorPreset? =
        all.firstOrNull { it.matches(colors) }
}

fun formatNgColor(@ColorInt color: Int): String = "#%08X".format(color)

fun parseNgColor(value: String): Int? {
    val raw = value.trim().removePrefix("#")
    val normalized = when (raw.length) {
        6 -> "FF$raw"
        8 -> raw
        else -> return null
    }
    return normalized.toLongOrNull(16)?.toInt()
}
