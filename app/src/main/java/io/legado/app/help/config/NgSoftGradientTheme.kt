package io.legado.app.help.config

import android.content.Context
import androidx.annotation.ColorInt
import com.materialkolor.hct.Hct
import io.legado.app.constant.PreferKey
import io.legado.app.ui.design.theme.NgColorGenerationMode
import io.legado.app.ui.design.theme.NgColorSpec
import io.legado.app.ui.design.theme.NgColorSystem
import io.legado.app.ui.design.theme.NgContrastLevel
import io.legado.app.ui.design.theme.NgManualColorSet
import io.legado.app.ui.design.theme.NgPaletteStyle
import io.legado.app.ui.design.theme.NgTopBarTextMode
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import kotlin.math.abs

internal enum class NgSoftGradientColorPreset(
    val storageValue: String,
    val darkStatusBarIcons: Boolean,
) {
    CLEAR_BLUE("clear_blue", true),
    DUSK_VIOLET("dusk_violet", false),
    YOUNG_BAMBOO("young_bamboo", false),
    FOREST_AFTER_RAIN("forest_after_rain", false),
    CHERRY_GLOW("cherry_glow", false),
    APRICOT("apricot", false),
    AMBER("amber", false),
    INDIGO_SEA("indigo_sea", false),
    CELADON("celadon", false),
    MOON_WHITE("moon_white", false),
    COCOA("cocoa", false),
    GRAPHITE("graphite", false);

    companion object {
        fun fromStorage(value: String?): NgSoftGradientColorPreset =
            entries.firstOrNull { it.storageValue == value } ?: CLEAR_BLUE
    }
}

internal enum class NgSoftGradientColorMode(val storageValue: String) {
    PRESET("preset"),
    CUSTOM("custom");

    companion object {
        fun fromStorage(value: String?): NgSoftGradientColorMode =
            entries.firstOrNull { it.storageValue == value } ?: PRESET
    }
}

internal enum class NgSoftGradientLightFieldPreset(val storageValue: String) {
    BALANCED("balanced"),
    CLEAR("clear"),
    STILL_SEA("still_sea"),
    AQUA("aqua"),
    FLOW_SHADOW("flow_shadow");

    companion object {
        fun fromStorage(value: String?): NgSoftGradientLightFieldPreset =
            entries.firstOrNull { it.storageValue == value } ?: BALANCED
    }
}

internal enum class NgThemeGradientMotion {
    NONE,
    FLOW_SHADOW,
}

internal data class NgThemeGradientRadialLayer(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    /** 相对于光心到画布最远角距离的半径倍率。 */
    val radius: Float = 1f,
    val colors: List<Int> = emptyList(),
    val stops: List<Float> = emptyList(),
) {
    fun normalized(): NgThemeGradientRadialLayer? {
        val normalizedColors = colors.take(MAX_COLORS)
        if (normalizedColors.size < MIN_COLORS || !radius.isFinite()) return null
        return copy(
            centerX = centerX.finiteOrDefault(0.5f).coerceIn(MIN_CENTER, MAX_CENTER),
            centerY = centerY.finiteOrDefault(0.5f).coerceIn(MIN_CENTER, MAX_CENTER),
            radius = radius.coerceIn(MIN_RADIUS, MAX_RADIUS),
            colors = normalizedColors,
            stops = normalizedGradientStops(stops, normalizedColors.size),
        )
    }

    private companion object {
        const val MIN_COLORS = 2
        const val MAX_COLORS = 6
        const val MIN_CENTER = -1f
        const val MAX_CENTER = 2f
        const val MIN_RADIUS = 0.05f
        const val MAX_RADIUS = 2f
    }
}

internal data class NgThemeGradientProfile(
    val startX: Float = 0f,
    val startY: Float = 0f,
    val endX: Float = 1f,
    val endY: Float = 1f,
    val colors: List<Int> = emptyList(),
    val stops: List<Float> = emptyList(),
    val radialLayers: List<NgThemeGradientRadialLayer> = emptyList(),
    val motion: NgThemeGradientMotion = NgThemeGradientMotion.NONE,
) {
    fun normalized(): NgThemeGradientProfile? {
        val normalizedColors = colors.take(MAX_COLORS)
        if (normalizedColors.size < MIN_COLORS) return null
        val normalizedStartX = startX.finiteOrDefault(0f)
            .coerceIn(MIN_COORDINATE, MAX_COORDINATE)
        val normalizedStartY = startY.finiteOrDefault(0f)
            .coerceIn(MIN_COORDINATE, MAX_COORDINATE)
        var normalizedEndX = endX.finiteOrDefault(1f)
            .coerceIn(MIN_COORDINATE, MAX_COORDINATE)
        var normalizedEndY = endY.finiteOrDefault(1f)
            .coerceIn(MIN_COORDINATE, MAX_COORDINATE)
        if (
            abs(normalizedEndX - normalizedStartX) < MIN_VECTOR_LENGTH &&
            abs(normalizedEndY - normalizedStartY) < MIN_VECTOR_LENGTH
        ) {
            normalizedEndX = normalizedStartX
            normalizedEndY = if (normalizedStartY <= 1f) {
                normalizedStartY + 1f
            } else {
                normalizedStartY - 1f
            }
        }
        return copy(
            startX = normalizedStartX,
            startY = normalizedStartY,
            endX = normalizedEndX,
            endY = normalizedEndY,
            colors = normalizedColors,
            stops = normalizedGradientStops(stops, normalizedColors.size),
            radialLayers = radialLayers.take(MAX_RADIAL_LAYERS)
                .mapNotNull(NgThemeGradientRadialLayer::normalized),
        )
    }

    private companion object {
        const val MIN_COLORS = 2
        const val MAX_COLORS = 8
        const val MAX_RADIAL_LAYERS = 8
        const val MIN_COORDINATE = -1f
        const val MAX_COORDINATE = 2f
        const val MIN_VECTOR_LENGTH = 0.001f
    }
}

internal object NgSoftGradientTheme {

    /** Neutral selected surface shared by every soft-gradient color preset. */
    internal val selectedContainer = 0xF2FFFFFF.toInt()
    internal val defaultCustomColor = 0xFFFAB27B.toInt()

    fun colorMode(context: Context): NgSoftGradientColorMode =
        NgSoftGradientColorMode.fromStorage(
            context.getPrefString(PreferKey.ngSoftGradientColorMode),
        )

    fun colorPreset(context: Context): NgSoftGradientColorPreset =
        NgSoftGradientColorPreset.fromStorage(
            context.getPrefString(PreferKey.ngSoftGradientColor),
        )

    @ColorInt
    fun customColor(context: Context): Int = NgColorMath.opaque(
        context.getPrefInt(PreferKey.ngSoftGradientCustomColor, defaultCustomColor),
    )

    fun lightFieldPreset(context: Context): NgSoftGradientLightFieldPreset =
        NgSoftGradientLightFieldPreset.fromStorage(
            context.getPrefString(PreferKey.ngSoftGradientLightField),
        )

    fun selectColor(context: Context, preset: NgSoftGradientColorPreset) {
        context.putPrefString(PreferKey.ngSoftGradientColor, preset.storageValue)
        context.putPrefString(
            PreferKey.ngSoftGradientColorMode,
            NgSoftGradientColorMode.PRESET.storageValue,
        )
        reapplyIfActive(context)
    }

    fun selectCustomColor(context: Context, @ColorInt color: Int) {
        context.putPrefInt(PreferKey.ngSoftGradientCustomColor, NgColorMath.opaque(color))
        context.putPrefString(
            PreferKey.ngSoftGradientColorMode,
            NgSoftGradientColorMode.CUSTOM.storageValue,
        )
        reapplyIfActive(context)
    }

    fun selectLightField(context: Context, preset: NgSoftGradientLightFieldPreset) {
        context.putPrefString(PreferKey.ngSoftGradientLightField, preset.storageValue)
        reapplyIfActive(context)
    }

    fun colors(context: Context): NgColorSystem = when (colorMode(context)) {
        NgSoftGradientColorMode.PRESET -> colors(colorPreset(context))
        NgSoftGradientColorMode.CUSTOM -> customTone(customColor(context)).colors
    }

    fun colors(preset: NgSoftGradientColorPreset): NgColorSystem = when (preset) {
        NgSoftGradientColorPreset.CLEAR_BLUE -> clearBlueColors
        NgSoftGradientColorPreset.DUSK_VIOLET -> duskVioletColors
        NgSoftGradientColorPreset.YOUNG_BAMBOO -> youngBambooColors
        NgSoftGradientColorPreset.FOREST_AFTER_RAIN -> forestAfterRainColors
        NgSoftGradientColorPreset.CHERRY_GLOW -> cherryGlowColors
        NgSoftGradientColorPreset.APRICOT -> apricotColors
        NgSoftGradientColorPreset.AMBER -> amberColors
        NgSoftGradientColorPreset.INDIGO_SEA -> indigoSeaColors
        NgSoftGradientColorPreset.CELADON -> celadonColors
        NgSoftGradientColorPreset.MOON_WHITE -> moonWhiteColors
        NgSoftGradientColorPreset.COCOA -> cocoaColors
        NgSoftGradientColorPreset.GRAPHITE -> graphiteColors
    }

    fun gradient(context: Context): NgThemeGradientProfile = when (colorMode(context)) {
        NgSoftGradientColorMode.PRESET -> gradient(
            colorPreset(context),
            lightFieldPreset(context),
        )
        NgSoftGradientColorMode.CUSTOM -> customGradient(
            customColor(context),
            lightFieldPreset(context),
        )
    }

    fun gradient(
        preset: NgSoftGradientColorPreset,
        lightField: NgSoftGradientLightFieldPreset,
    ): NgThemeGradientProfile = when (preset) {
        NgSoftGradientColorPreset.CLEAR_BLUE -> clearBlueGradients.getValue(lightField)
        NgSoftGradientColorPreset.DUSK_VIOLET -> duskVioletGradients.getValue(lightField)
        NgSoftGradientColorPreset.YOUNG_BAMBOO -> youngBambooGradients.getValue(lightField)
        NgSoftGradientColorPreset.FOREST_AFTER_RAIN ->
            forestAfterRainGradients.getValue(lightField)
        NgSoftGradientColorPreset.CHERRY_GLOW -> cherryGlowGradients.getValue(lightField)
        NgSoftGradientColorPreset.APRICOT -> apricotGradients.getValue(lightField)
        NgSoftGradientColorPreset.AMBER -> amberGradients.getValue(lightField)
        NgSoftGradientColorPreset.INDIGO_SEA -> indigoSeaGradients.getValue(lightField)
        NgSoftGradientColorPreset.CELADON -> celadonGradients.getValue(lightField)
        NgSoftGradientColorPreset.MOON_WHITE -> moonWhiteGradients.getValue(lightField)
        NgSoftGradientColorPreset.COCOA -> cocoaGradients.getValue(lightField)
        NgSoftGradientColorPreset.GRAPHITE -> graphiteGradients.getValue(lightField)
    }

    fun customGradient(
        @ColorInt color: Int,
        lightField: NgSoftGradientLightFieldPreset,
    ): NgThemeGradientProfile = customTone(color).gradients.getValue(lightField)

    fun customColors(@ColorInt color: Int): NgColorSystem = customTone(color).colors

    fun darkStatusBarIcons(context: Context): Boolean = when (colorMode(context)) {
        NgSoftGradientColorMode.PRESET -> colorPreset(context).darkStatusBarIcons
        NgSoftGradientColorMode.CUSTOM -> false
    }

    private fun reapplyIfActive(context: Context) {
        if (NgThemeModeStore.current(context) == NgThemePresentationMode.SOFT_GRADIENT) {
            ThemeConfig.applyThemeMode(context, DAY_THEME_MODE)
        }
    }

    private val clearBlueManualColors = NgManualColorSet(
        primary = 0xFF0A74A8.toInt(),
        secondary = 0xFFFFFFFF.toInt(),
        primaryText = 0xFF11181C.toInt(),
        secondaryText = 0xFF48616C.toInt(),
        background = 0xFFF4F8FA.toInt(),
        labelContainer = 0xFFE5F0F5.toInt(),
    )

    private val clearBlueColors = NgColorSystem(
        mode = NgColorGenerationMode.MANUAL,
        lightSeed = 0xFF0A74A8.toInt(),
        darkSeed = 0xFF0A74A8.toInt(),
        paletteStyle = NgPaletteStyle.TONAL_SPOT,
        contrast = NgContrastLevel.DEFAULT,
        colorSpec = NgColorSpec.MATERIAL_3_2021,
        manualLight = clearBlueManualColors,
        manualDark = clearBlueManualColors,
        lightTopBarTextMode = NgTopBarTextMode.LIGHT,
        darkTopBarTextMode = NgTopBarTextMode.LIGHT,
    )

    private val duskVioletManualColors = NgManualColorSet(
        primary = 0xFF7352B5.toInt(),
        secondary = 0xFFFFFFFF.toInt(),
        primaryText = 0xFF1D1722.toInt(),
        secondaryText = 0xFF66566F.toInt(),
        background = 0xFFFAF6FA.toInt(),
        labelContainer = 0xFFF0E7F3.toInt(),
    )

    private val duskVioletColors = NgColorSystem(
        mode = NgColorGenerationMode.MANUAL,
        lightSeed = 0xFF7352B5.toInt(),
        darkSeed = 0xFF7352B5.toInt(),
        paletteStyle = NgPaletteStyle.TONAL_SPOT,
        contrast = NgContrastLevel.DEFAULT,
        colorSpec = NgColorSpec.MATERIAL_3_2021,
        manualLight = duskVioletManualColors,
        manualDark = duskVioletManualColors,
        lightTopBarTextMode = NgTopBarTextMode.LIGHT,
        darkTopBarTextMode = NgTopBarTextMode.LIGHT,
    )

    private val youngBambooColors = softGradientColorSystem(
        primary = 0xFF007947,
        primaryText = 0xFF10221B,
        secondaryText = 0xFF48665A,
        background = 0xFFF4FAF7,
        labelContainer = 0xFFE4F3EB,
    )

    private val forestAfterRainColors = softGradientColorSystem(
        primary = 0xFF2B6447,
        primaryText = 0xFF172119,
        secondaryText = 0xFF53675A,
        background = 0xFFF7F9F3,
        labelContainer = 0xFFE9F0E4,
    )

    private val cherryGlowColors = softGradientColorSystem(
        primary = 0xFFC63F61,
        primaryText = 0xFF24171B,
        secondaryText = 0xFF75535D,
        background = 0xFFFFF7F8,
        labelContainer = 0xFFF8E6EB,
    )

    private val apricotColors = softGradientColorSystem(
        primary = 0xFFB75D3D,
        primaryText = 0xFF291A15,
        secondaryText = 0xFF76584A,
        background = 0xFFFFF8F2,
        labelContainer = 0xFFF9E9DE,
    )

    private val amberColors = softGradientColorSystem(
        primary = 0xFF8A651A,
        primaryText = 0xFF211B0F,
        secondaryText = 0xFF6C6042,
        background = 0xFFFFFCF3,
        labelContainer = 0xFFF5ECD0,
    )

    private val indigoSeaColors = softGradientColorSystem(
        primary = 0xFF345A9A,
        primaryText = 0xFF151C2A,
        secondaryText = 0xFF53617A,
        background = 0xFFF6F8FD,
        labelContainer = 0xFFE7EDF8,
    )

    private val celadonColors = softGradientColorSystem(
        primary = 0xFF3F6F63,
        primaryText = 0xFF15211E,
        secondaryText = 0xFF556A63,
        background = 0xFFF7FAF6,
        labelContainer = 0xFFE7F0E9,
    )

    private val moonWhiteColors = softGradientColorSystem(
        primary = 0xFF536B78,
        primaryText = 0xFF172025,
        secondaryText = 0xFF59696F,
        background = 0xFFF7F9F8,
        labelContainer = 0xFFE8EEEC,
    )

    private val cocoaColors = softGradientColorSystem(
        primary = 0xFF7A5043,
        primaryText = 0xFF231A17,
        secondaryText = 0xFF6D5A54,
        background = 0xFFFBF8F6,
        labelContainer = 0xFFF0E7E2,
    )

    private val graphiteColors = softGradientColorSystem(
        primary = 0xFF49545A,
        primaryText = 0xFF171B1D,
        secondaryText = 0xFF5C6467,
        background = 0xFFF7F8F8,
        labelContainer = 0xFFE9ECEC,
    )

    private fun softGradientColorSystem(
        primary: Long,
        primaryText: Long,
        secondaryText: Long,
        background: Long,
        labelContainer: Long,
    ): NgColorSystem {
        val manualColors = NgManualColorSet(
            primary = primary.toInt(),
            secondary = 0xFFFFFFFF.toInt(),
            primaryText = primaryText.toInt(),
            secondaryText = secondaryText.toInt(),
            background = background.toInt(),
            labelContainer = labelContainer.toInt(),
        )
        return NgColorSystem(
            mode = NgColorGenerationMode.MANUAL,
            lightSeed = primary.toInt(),
            darkSeed = primary.toInt(),
            paletteStyle = NgPaletteStyle.TONAL_SPOT,
            contrast = NgContrastLevel.DEFAULT,
            colorSpec = NgColorSpec.MATERIAL_3_2021,
            manualLight = manualColors,
            manualDark = manualColors,
            lightTopBarTextMode = NgTopBarTextMode.LIGHT,
            darkTopBarTextMode = NgTopBarTextMode.LIGHT,
        )
    }

    private val clearBlueGradients = mapOf(
        NgSoftGradientLightFieldPreset.BALANCED to gradientProfile(
            baseColors = listOf(
                0xFF468FC3,
                0xFF5CB3D6,
                0xFF88D7E5,
                0xFFB8EDF1,
                0xFF50A7BC,
            ),
            stops = listOf(0f, 0.25f, 0.52f, 0.78f, 1f),
            radialLayers = listOf(
                radial(0.96f, 0.08f, 0.62f, 0x79316DA4, 0x00316DA4),
                radial(-0.12f, 0.30f, 0.70f, 0x735FCBE4, 0x005FCBE4),
                radial(0.46f, 0.63f, 0.58f, 0xA6D8F7F4, 0x00D8F7F4),
                radial(1.04f, 0.72f, 0.38f, 0x5D8DE6E7, 0x008DE6E7),
                radial(0.16f, 0.22f, 0.18f, 0x52E4FAF7, 0x00E4FAF7),
                radial(0.84f, 0.43f, 0.16f, 0x463C8CB7, 0x003C8CB7),
                radial(0.22f, 0.77f, 0.20f, 0x48C7F1EE, 0x00C7F1EE),
                vignette(0.50f, 0.52f, 0.78f, 0x3D145A72),
            ),
        ),
        NgSoftGradientLightFieldPreset.CLEAR to gradientProfile(
            baseColors = listOf(
                0xFF5DA5D2,
                0xFF72C2DE,
                0xFFA2E3EB,
                0xFFD0F5F3,
                0xFF70C2D0,
            ),
            stops = listOf(0f, 0.24f, 0.50f, 0.78f, 1f),
            radialLayers = listOf(
                radial(1.02f, 0.06f, 0.60f, 0x57377FB3, 0x00377FB3),
                radial(-0.08f, 0.28f, 0.72f, 0x7A83DCEB, 0x0083DCEB),
                radial(0.44f, 0.62f, 0.60f, 0xB5E5FBF7, 0x00E5FBF7),
                radial(0.98f, 0.74f, 0.40f, 0x6CA8ECE8, 0x00A8ECE8),
                radial(0.18f, 0.21f, 0.20f, 0x5BF3FEFA, 0x00F3FEFA),
                radial(0.82f, 0.42f, 0.17f, 0x3D559FC3, 0x00559FC3),
                radial(0.24f, 0.78f, 0.21f, 0x55DCF8F2, 0x00DCF8F2),
                vignette(0.50f, 0.54f, 0.82f, 0x25176880),
            ),
        ),
        NgSoftGradientLightFieldPreset.STILL_SEA to gradientProfile(
            baseColors = listOf(
                0xFF347CB3,
                0xFF4598C2,
                0xFF6CBED2,
                0xFF9AD9DE,
                0xFF28798F,
            ),
            stops = listOf(0f, 0.26f, 0.53f, 0.78f, 1f),
            radialLayers = listOf(
                radial(0.98f, 0.08f, 0.60f, 0x9E1A4C89, 0x001A4C89),
                radial(-0.10f, 0.34f, 0.66f, 0x594EB8D8, 0x004EB8D8),
                radial(0.44f, 0.62f, 0.55f, 0x8ABCE9EA, 0x00BCE9EA),
                radial(1.02f, 0.72f, 0.36f, 0x526CC7D0, 0x006CC7D0),
                radial(0.16f, 0.23f, 0.17f, 0x49D5F2EE, 0x00D5F2EE),
                radial(0.84f, 0.43f, 0.15f, 0x55246192, 0x00246192),
                radial(0.22f, 0.77f, 0.19f, 0x42AEE2E3, 0x00AEE2E3),
                vignette(0.50f, 0.52f, 0.72f, 0x5C073F58),
            ),
        ),
        NgSoftGradientLightFieldPreset.AQUA to gradientProfile(
            baseColors = listOf(
                0xFF3D8DB5,
                0xFF52B4C7,
                0xFF7ED6D7,
                0xFFB2ECE2,
                0xFF2A8992,
            ),
            stops = listOf(0f, 0.25f, 0.52f, 0.79f, 1f),
            radialLayers = listOf(
                radial(0.98f, 0.08f, 0.60f, 0x6B2D6EA5, 0x002D6EA5),
                radial(-0.12f, 0.32f, 0.70f, 0x715AD2D9, 0x005AD2D9),
                radial(0.46f, 0.64f, 0.58f, 0xA7C9F6EA, 0x00C9F6EA),
                radial(1.04f, 0.70f, 0.38f, 0x6B8CE8D7, 0x008CE8D7),
                radial(0.16f, 0.22f, 0.18f, 0x50E5FAEE, 0x00E5FAEE),
                radial(0.84f, 0.42f, 0.16f, 0x473B809F, 0x003B809F),
                radial(0.22f, 0.78f, 0.20f, 0x4CC1F3DE, 0x00C1F3DE),
                vignette(0.50f, 0.53f, 0.78f, 0x3D065762),
            ),
        ),
    ).withFlowShadow()

    private val duskVioletGradients = mapOf(
        NgSoftGradientLightFieldPreset.BALANCED to gradientProfile(
            baseColors = listOf(
                0xFF4A478F,
                0xFF6D55A8,
                0xFF9C67B4,
                0xFFD38AB9,
                0xFF704E93,
            ),
            stops = listOf(0f, 0.24f, 0.50f, 0.77f, 1f),
            radialLayers = listOf(
                radial(0.96f, 0.08f, 0.58f, 0xA02B285F, 0x002B285F),
                radial(-0.10f, 0.30f, 0.62f, 0x784E62C2, 0x004E62C2),
                radial(0.46f, 0.62f, 0.52f, 0xA6F2C4DE, 0x00F2C4DE),
                radial(1.02f, 0.72f, 0.34f, 0x756F2E76, 0x006F2E76),
                radial(0.16f, 0.22f, 0.17f, 0x62F8D9EB, 0x00F8D9EB),
                radial(0.84f, 0.43f, 0.15f, 0x5A3D215F, 0x003D215F),
                radial(0.22f, 0.77f, 0.18f, 0x59E395C1, 0x00E395C1),
                vignette(0.50f, 0.52f, 0.75f, 0x571F163D),
            ),
        ),
        NgSoftGradientLightFieldPreset.CLEAR to gradientProfile(
            baseColors = listOf(
                0xFF6860AA,
                0xFF8E6BB7,
                0xFFBC83C5,
                0xFFE9B2D0,
                0xFF8964A6,
            ),
            stops = listOf(0f, 0.24f, 0.50f, 0.78f, 1f),
            radialLayers = listOf(
                radial(1.00f, 0.06f, 0.58f, 0x6A40397E, 0x0040397E),
                radial(-0.08f, 0.28f, 0.66f, 0x716E75D2, 0x006E75D2),
                radial(0.44f, 0.62f, 0.56f, 0xB8F8DAE9, 0x00F8DAE9),
                radial(0.98f, 0.74f, 0.36f, 0x6B9A4B91, 0x009A4B91),
                radial(0.18f, 0.21f, 0.18f, 0x66FCE7F2, 0x00FCE7F2),
                radial(0.82f, 0.42f, 0.16f, 0x493F2D6B, 0x003F2D6B),
                radial(0.24f, 0.78f, 0.19f, 0x60F0B7D6, 0x00F0B7D6),
                vignette(0.50f, 0.54f, 0.80f, 0x3D2C1F4D),
            ),
        ),
        NgSoftGradientLightFieldPreset.STILL_SEA to gradientProfile(
            baseColors = listOf(
                0xFF35306F,
                0xFF504083,
                0xFF754893,
                0xFFA35D9B,
                0xFF46336D,
            ),
            stops = listOf(0f, 0.25f, 0.51f, 0.77f, 1f),
            radialLayers = listOf(
                radial(0.98f, 0.08f, 0.56f, 0xB01D194D, 0x001D194D),
                radial(-0.10f, 0.34f, 0.60f, 0x66504FA9, 0x00504FA9),
                radial(0.44f, 0.62f, 0.48f, 0x8BCE87BD, 0x00CE87BD),
                radial(1.02f, 0.72f, 0.32f, 0x763F1F61, 0x003F1F61),
                radial(0.16f, 0.23f, 0.15f, 0x55D7A8D1, 0x00D7A8D1),
                radial(0.84f, 0.43f, 0.14f, 0x66231148, 0x00231148),
                radial(0.22f, 0.77f, 0.17f, 0x55B968A8, 0x00B968A8),
                vignette(0.50f, 0.52f, 0.70f, 0x71130D35),
            ),
        ),
        NgSoftGradientLightFieldPreset.AQUA to gradientProfile(
            baseColors = listOf(
                0xFF464A98,
                0xFF665BB2,
                0xFF8A78C6,
                0xFFC5A2D9,
                0xFF55508A,
            ),
            stops = listOf(0f, 0.24f, 0.51f, 0.78f, 1f),
            radialLayers = listOf(
                radial(0.98f, 0.08f, 0.58f, 0x8A27275F, 0x0027275F),
                radial(-0.10f, 0.32f, 0.64f, 0x70526EC5, 0x00526EC5),
                radial(0.46f, 0.64f, 0.52f, 0x9DD5C7EA, 0x00D5C7EA),
                radial(1.02f, 0.70f, 0.34f, 0x68473582, 0x00473582),
                radial(0.16f, 0.22f, 0.16f, 0x5AD7E0F2, 0x00D7E0F2),
                radial(0.84f, 0.42f, 0.15f, 0x57322163, 0x00322163),
                radial(0.22f, 0.78f, 0.18f, 0x56D59BCB, 0x00D59BCB),
                vignette(0.50f, 0.53f, 0.75f, 0x55201842),
            ),
        ),
    ).withFlowShadow()

    private val youngBambooGradients = generatedToneGradients(
        balanced = listOf(
            0xFF007D65,
            0xFF2FA58C,
            0xFF65C294,
            0xFFB9E6D2,
            0xFF72BAA7,
        ),
        clear = listOf(
            0xFF238F79,
            0xFF51B99F,
            0xFF87D6B7,
            0xFFD5F2DF,
            0xFF8CC9B6,
        ),
        stillSea = listOf(
            0xFF005B4D,
            0xFF207969,
            0xFF4FA47E,
            0xFFA4CDB5,
            0xFF4A8A72,
        ),
        aqua = listOf(
            0xFF08766E,
            0xFF319C8D,
            0xFF69C4B0,
            0xFFC6E9D7,
            0xFF5EAE9F,
        ),
    )

    private val forestAfterRainGradients = generatedToneGradients(
        balanced = listOf(
            0xFF2B6447,
            0xFF4F865D,
            0xFF84BF96,
            0xFFD8E5AB,
            0xFF77AC98,
        ),
        clear = listOf(
            0xFF487B5E,
            0xFF71A878,
            0xFFA7D0A6,
            0xFFEEF0C6,
            0xFF9ABFAE,
        ),
        stillSea = listOf(
            0xFF204A35,
            0xFF3F694A,
            0xFF689B73,
            0xFFB4C58C,
            0xFF587F6F,
        ),
        aqua = listOf(
            0xFF2F6150,
            0xFF4C8870,
            0xFF79B697,
            0xFFD1E2BD,
            0xFF6FA692,
        ),
    )

    private val cherryGlowGradients = generatedToneGradients(
        balanced = listOf(
            0xFF97435E,
            0xFFD65371,
            0xFFF391A9,
            0xFFFEEEED,
            0xFFF8ABA6,
        ),
        clear = listOf(
            0xFFB65E78,
            0xFFE77990,
            0xFFF8B4C2,
            0xFFFFF5F4,
            0xFFFBC7C0,
        ),
        stillSea = listOf(
            0xFF733247,
            0xFFA7465E,
            0xFFC76F86,
            0xFFE8B7BF,
            0xFFD88C8C,
        ),
        aqua = listOf(
            0xFF87455F,
            0xFFB95E80,
            0xFFDA89A7,
            0xFFF4D4DA,
            0xFFCB89A2,
        ),
    )

    private val apricotGradients = generatedToneGradients(
        balanced = listOf(
            0xFFB76442,
            0xFFE38A5C,
            0xFFFAB27B,
            0xFFFFE5CE,
            0xFFFFC49A,
        ),
        clear = listOf(
            0xFFD17A52,
            0xFFF09C69,
            0xFFFBC393,
            0xFFFFF1E0,
            0xFFFFD2AE,
        ),
        stillSea = listOf(
            0xFF914B34,
            0xFFBD6747,
            0xFFE18E63,
            0xFFF2C3A3,
            0xFFE6A07C,
        ),
        aqua = listOf(
            0xFFA85A42,
            0xFFCE7756,
            0xFFE99C78,
            0xFFF8D5BE,
            0xFFDFA087,
        ),
    )

    private val amberGradients = generatedToneGradients(
        balanced = listOf(
            0xFF6F4C10,
            0xFFA97818,
            0xFFD9AD3C,
            0xFFF7E6A4,
            0xFFE2C267,
        ),
        clear = listOf(
            0xFF8D651E,
            0xFFB88D31,
            0xFFE6C85F,
            0xFFFFF2BF,
            0xFFEEDB8D,
        ),
        stillSea = listOf(
            0xFF51380F,
            0xFF75551A,
            0xFFA57A2A,
            0xFFD5BD72,
            0xFFB18F45,
        ),
        aqua = listOf(
            0xFF65521E,
            0xFF8E7830,
            0xFFB4A055,
            0xFFE4DCAC,
            0xFFC0AD70,
        ),
    )

    private val indigoSeaGradients = generatedToneGradients(
        balanced = listOf(
            0xFF263B73,
            0xFF365CA5,
            0xFF5D83C7,
            0xFFC5D7F5,
            0xFF7399D2,
        ),
        clear = listOf(
            0xFF36538D,
            0xFF4C75BB,
            0xFF80A3DC,
            0xFFE3ECFA,
            0xFFA2BCE7,
        ),
        stillSea = listOf(
            0xFF1C2A54,
            0xFF293F72,
            0xFF47659A,
            0xFF9FB4D9,
            0xFF5B78A9,
        ),
        aqua = listOf(
            0xFF293E6B,
            0xFF386087,
            0xFF5F8EAE,
            0xFFC1D9E8,
            0xFF749CB9,
        ),
    )

    private val celadonGradients = generatedToneGradients(
        balanced = listOf(
            0xFF315F58,
            0xFF4E8476,
            0xFF7BA995,
            0xFFDDE8CF,
            0xFF9DBE9F,
        ),
        clear = listOf(
            0xFF4A766D,
            0xFF6E9D8D,
            0xFFA4C6AE,
            0xFFF0F3DC,
            0xFFBAD2B5,
        ),
        stillSea = listOf(
            0xFF244740,
            0xFF385F55,
            0xFF5E8372,
            0xFFB9CAB0,
            0xFF779989,
        ),
        aqua = listOf(
            0xFF2F5A58,
            0xFF4B7C77,
            0xFF75A6A0,
            0xFFD3E8DD,
            0xFF91BBB2,
        ),
    )

    private val moonWhiteGradients = generatedToneGradients(
        balanced = listOf(
            0xFF536979,
            0xFF718996,
            0xFFA6BBC0,
            0xFFDFE1D4,
            0xFF829C9A,
        ),
        clear = listOf(
            0xFF6B7F8D,
            0xFF8FA3AC,
            0xFFC1D1D1,
            0xFFF1F0E4,
            0xFFAEBFBA,
        ),
        stillSea = listOf(
            0xFF3E505E,
            0xFF586C78,
            0xFF83999F,
            0xFFC2C7BD,
            0xFF6B817F,
        ),
        aqua = listOf(
            0xFF4E6672,
            0xFF6E8991,
            0xFF9DB5B7,
            0xFFDDE4DD,
            0xFF829C98,
        ),
    )

    private val cocoaGradients = generatedToneGradients(
        balanced = listOf(
            0xFF69463B,
            0xFF916153,
            0xFFBF8B76,
            0xFFEAD6C7,
            0xFFCDA68F,
        ),
        clear = listOf(
            0xFF825B4F,
            0xFFAA7A69,
            0xFFD0A491,
            0xFFF5E8DE,
            0xFFDFC2B0,
        ),
        stillSea = listOf(
            0xFF4F352E,
            0xFF704C42,
            0xFF966B5B,
            0xFFCDB1A1,
            0xFFAC8370,
        ),
        aqua = listOf(
            0xFF5E4741,
            0xFF7F625A,
            0xFFA9887D,
            0xFFDDD0C6,
            0xFFBA9A8F,
        ),
    )

    private val graphiteGradients = generatedToneGradients(
        balanced = listOf(
            0xFF343A40,
            0xFF505B64,
            0xFF7C8A91,
            0xFFD4D8D7,
            0xFF9AA4A5,
        ),
        clear = listOf(
            0xFF4A5259,
            0xFF69757C,
            0xFF9DA8AC,
            0xFFECEEEC,
            0xFFB7C0C0,
        ),
        stillSea = listOf(
            0xFF262B30,
            0xFF394149,
            0xFF5C6870,
            0xFFADB4B5,
            0xFF747F82,
        ),
        aqua = listOf(
            0xFF303D43,
            0xFF4A5A60,
            0xFF71858A,
            0xFFC9D1CF,
            0xFF87989A,
        ),
    )

    private data class CustomTone(
        val colors: NgColorSystem,
        val gradients: Map<NgSoftGradientLightFieldPreset, NgThemeGradientProfile>,
    )

    @Volatile
    private var customToneCache: Pair<Int, CustomTone>? = null

    private fun customTone(@ColorInt color: Int): CustomTone {
        val normalizedColor = NgColorMath.opaque(color)
        customToneCache?.takeIf { it.first == normalizedColor }?.let { return it.second }

        val source = Hct.fromInt(normalizedColor)
        val neutral = source.chroma < CUSTOM_NEUTRAL_CHROMA_THRESHOLD
        val baseChroma = if (neutral) {
            source.chroma.coerceIn(0.0, CUSTOM_NEUTRAL_CHROMA_THRESHOLD)
        } else {
            source.chroma.coerceIn(CUSTOM_MIN_CHROMA, CUSTOM_MAX_CHROMA)
        }
        fun tone(tone: Double, chromaScale: Double): Long {
            val scaledChroma = if (neutral) {
                baseChroma * chromaScale
            } else {
                (baseChroma * chromaScale).coerceIn(CUSTOM_MIN_VISIBLE_CHROMA, CUSTOM_MAX_CHROMA)
            }
            return Hct.from(source.hue, scaledChroma, tone)
                .toInt()
                .toLong() and 0xFFFFFFFFL
        }

        val manualColors = NgManualColorSet(
            primary = tone(38.0, 0.90).toInt(),
            secondary = 0xFFFFFFFF.toInt(),
            primaryText = tone(12.0, 0.18).toInt(),
            secondaryText = tone(38.0, 0.24).toInt(),
            background = tone(98.0, 0.06).toInt(),
            labelContainer = tone(92.0, 0.12).toInt(),
        )
        val colors = NgColorSystem(
            mode = NgColorGenerationMode.MANUAL,
            lightSeed = normalizedColor,
            darkSeed = normalizedColor,
            paletteStyle = NgPaletteStyle.TONAL_SPOT,
            contrast = NgContrastLevel.DEFAULT,
            colorSpec = NgColorSpec.MATERIAL_3_2021,
            manualLight = manualColors,
            manualDark = manualColors,
            lightTopBarTextMode = NgTopBarTextMode.LIGHT,
            darkTopBarTextMode = NgTopBarTextMode.LIGHT,
        )
        val gradients = generatedToneGradients(
            balanced = listOf(
                tone(30.0, 0.95),
                tone(49.0, 1.00),
                tone(68.0, 0.82),
                tone(91.0, 0.18),
                tone(76.0, 0.58),
            ),
            clear = listOf(
                tone(38.0, 0.80),
                tone(58.0, 0.86),
                tone(75.0, 0.65),
                tone(95.0, 0.12),
                tone(83.0, 0.40),
            ),
            stillSea = listOf(
                tone(22.0, 0.90),
                tone(36.0, 0.96),
                tone(54.0, 0.76),
                tone(76.0, 0.26),
                tone(63.0, 0.55),
            ),
            aqua = listOf(
                tone(28.0, 0.62),
                tone(44.0, 0.70),
                tone(63.0, 0.56),
                tone(86.0, 0.14),
                tone(71.0, 0.40),
            ),
        )
        return CustomTone(colors = colors, gradients = gradients).also {
            customToneCache = normalizedColor to it
        }
    }

    private fun generatedToneGradients(
        balanced: List<Long>,
        clear: List<Long>,
        stillSea: List<Long>,
        aqua: List<Long>,
    ): Map<NgSoftGradientLightFieldPreset, NgThemeGradientProfile> = mapOf(
        NgSoftGradientLightFieldPreset.BALANCED to generatedToneProfile(
            NgSoftGradientLightFieldPreset.BALANCED,
            balanced,
        ),
        NgSoftGradientLightFieldPreset.CLEAR to generatedToneProfile(
            NgSoftGradientLightFieldPreset.CLEAR,
            clear,
        ),
        NgSoftGradientLightFieldPreset.STILL_SEA to generatedToneProfile(
            NgSoftGradientLightFieldPreset.STILL_SEA,
            stillSea,
        ),
        NgSoftGradientLightFieldPreset.AQUA to generatedToneProfile(
            NgSoftGradientLightFieldPreset.AQUA,
            aqua,
        ),
    ).withFlowShadow()

    private fun generatedToneProfile(
        preset: NgSoftGradientLightFieldPreset,
        baseColors: List<Long>,
    ): NgThemeGradientProfile {
        require(baseColors.size == 5)
        fun toneRadial(
            centerX: Float,
            centerY: Float,
            radius: Float,
            colorIndex: Int,
            alpha: Int,
        ) = radial(
            centerX,
            centerY,
            radius,
            baseColors[colorIndex].withAlpha(alpha),
            baseColors[colorIndex].withAlpha(0),
        )

        val stops: List<Float>
        val radialLayers: List<NgThemeGradientRadialLayer>
        when (preset) {
            NgSoftGradientLightFieldPreset.BALANCED -> {
                stops = listOf(0f, 0.24f, 0.50f, 0.77f, 1f)
                radialLayers = listOf(
                    toneRadial(0.96f, 0.08f, 0.58f, 0, 0x96),
                    toneRadial(-0.10f, 0.30f, 0.64f, 1, 0x74),
                    toneRadial(0.46f, 0.62f, 0.54f, 3, 0xA6),
                    toneRadial(1.02f, 0.72f, 0.35f, 2, 0x68),
                    toneRadial(0.16f, 0.22f, 0.17f, 3, 0x58),
                    toneRadial(0.84f, 0.43f, 0.15f, 0, 0x50),
                    toneRadial(0.22f, 0.77f, 0.18f, 3, 0x50),
                    vignette(0.50f, 0.52f, 0.75f, baseColors[0].withAlpha(0x48)),
                )
            }
            NgSoftGradientLightFieldPreset.CLEAR -> {
                stops = listOf(0f, 0.24f, 0.50f, 0.78f, 1f)
                radialLayers = listOf(
                    toneRadial(1.00f, 0.06f, 0.60f, 0, 0x62),
                    toneRadial(-0.08f, 0.28f, 0.68f, 1, 0x72),
                    toneRadial(0.44f, 0.62f, 0.58f, 3, 0xB0),
                    toneRadial(0.98f, 0.74f, 0.38f, 2, 0x62),
                    toneRadial(0.18f, 0.21f, 0.19f, 3, 0x60),
                    toneRadial(0.82f, 0.42f, 0.16f, 0, 0x3D),
                    toneRadial(0.24f, 0.78f, 0.20f, 3, 0x55),
                    vignette(0.50f, 0.54f, 0.81f, baseColors[0].withAlpha(0x2E)),
                )
            }
            NgSoftGradientLightFieldPreset.STILL_SEA -> {
                stops = listOf(0f, 0.25f, 0.51f, 0.77f, 1f)
                radialLayers = listOf(
                    toneRadial(0.98f, 0.08f, 0.56f, 0, 0xA0),
                    toneRadial(-0.10f, 0.34f, 0.60f, 1, 0x58),
                    toneRadial(0.44f, 0.62f, 0.50f, 3, 0x82),
                    toneRadial(1.02f, 0.72f, 0.33f, 2, 0x52),
                    toneRadial(0.16f, 0.23f, 0.15f, 3, 0x45),
                    toneRadial(0.84f, 0.43f, 0.14f, 0, 0x56),
                    toneRadial(0.22f, 0.77f, 0.17f, 3, 0x42),
                    vignette(0.50f, 0.52f, 0.70f, baseColors[0].withAlpha(0x62)),
                )
            }
            NgSoftGradientLightFieldPreset.AQUA -> {
                stops = listOf(0f, 0.24f, 0.51f, 0.78f, 1f)
                radialLayers = listOf(
                    toneRadial(0.98f, 0.08f, 0.58f, 0, 0x74),
                    toneRadial(-0.10f, 0.32f, 0.66f, 1, 0x70),
                    toneRadial(0.46f, 0.64f, 0.54f, 3, 0x9F),
                    toneRadial(1.02f, 0.70f, 0.35f, 2, 0x68),
                    toneRadial(0.16f, 0.22f, 0.16f, 3, 0x50),
                    toneRadial(0.84f, 0.42f, 0.15f, 0, 0x48),
                    toneRadial(0.22f, 0.78f, 0.18f, 3, 0x4C),
                    vignette(0.50f, 0.53f, 0.75f, baseColors[0].withAlpha(0x48)),
                )
            }
            NgSoftGradientLightFieldPreset.FLOW_SHADOW -> error(
                "Flow shadow is derived from the balanced profile",
            )
        }
        return gradientProfile(
            baseColors = baseColors,
            stops = stops,
            radialLayers = radialLayers,
        )
    }

    private fun Map<NgSoftGradientLightFieldPreset, NgThemeGradientProfile>
        .withFlowShadow(): Map<NgSoftGradientLightFieldPreset, NgThemeGradientProfile> =
        this + (
            NgSoftGradientLightFieldPreset.FLOW_SHADOW to
                getValue(NgSoftGradientLightFieldPreset.BALANCED).copy(
                    motion = NgThemeGradientMotion.FLOW_SHADOW,
                )
        )

    private fun gradientProfile(
        baseColors: List<Long>,
        stops: List<Float>,
        radialLayers: List<NgThemeGradientRadialLayer>,
    ): NgThemeGradientProfile = requireNotNull(
        NgThemeGradientProfile(
            startX = 0.08f,
            startY = 0f,
            endX = 0.78f,
            endY = 1f,
            colors = baseColors.map(Long::toInt),
            stops = stops,
            radialLayers = radialLayers,
        ).normalized(),
    )

    private fun radial(
        centerX: Float,
        centerY: Float,
        radius: Float,
        innerColor: Long,
        outerColor: Long,
    ) = NgThemeGradientRadialLayer(
        centerX = centerX,
        centerY = centerY,
        radius = radius,
        colors = listOf(innerColor.toInt(), outerColor.toInt()),
    )

    private fun vignette(
        centerX: Float,
        centerY: Float,
        clearUntil: Float,
        edgeColor: Long,
    ) = NgThemeGradientRadialLayer(
        centerX = centerX,
        centerY = centerY,
        radius = 1f,
        colors = listOf(0x00000000, 0x00000000, edgeColor.toInt()),
        stops = listOf(0f, clearUntil, 1f),
    )

    private fun Long.withAlpha(alpha: Int): Long =
        (this and 0x00FFFFFFL) or (alpha.coerceIn(0, 255).toLong() shl 24)

    private const val CUSTOM_NEUTRAL_CHROMA_THRESHOLD = 8.0
    private const val CUSTOM_MIN_CHROMA = 24.0
    private const val CUSTOM_MIN_VISIBLE_CHROMA = 8.0
    private const val CUSTOM_MAX_CHROMA = 64.0
    private const val DAY_THEME_MODE = "1"
}

private fun Float.finiteOrDefault(default: Float): Float = if (isFinite()) this else default

private fun normalizedGradientStops(stops: List<Float>, colorCount: Int): List<Float> {
    if (
        stops.size == colorCount &&
        stops.all { it.isFinite() } &&
        stops.zipWithNext().all { (first, second) -> first <= second }
    ) {
        return stops.map { it.coerceIn(0f, 1f) }
    }
    if (colorCount <= 1) return listOf(0f)
    return List(colorCount) { index -> index.toFloat() / (colorCount - 1) }
}
