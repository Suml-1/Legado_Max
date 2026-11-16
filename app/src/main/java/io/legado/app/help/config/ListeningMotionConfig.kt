package io.legado.app.help.config

import androidx.annotation.ColorInt
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

enum class ListeningMotionEffect(val storageValue: String) {
    FLAME("flame"),
    FLUID("fluid"),
    CARTOON("cartoon");

    companion object {
        fun fromStorage(value: String?): ListeningMotionEffect =
            entries.firstOrNull { it.storageValue == value } ?: FLAME
    }
}

enum class ListeningCartoonType(val storageValue: String) {
    SAKURA("sakura"),
    CATS("cats"),
    RAIN_NIGHT("rain_night");

    companion object {
        fun fromStorageOrNull(value: String?): ListeningCartoonType? =
            entries.firstOrNull { it.storageValue == value }

        fun fromStorage(value: String?): ListeningCartoonType =
            fromStorageOrNull(value) ?: SAKURA
    }
}

/** 场景素材对应的默认主色；主题与听书播放器共用，避免同一场景出现两套取色。 */
@ColorInt
internal fun ListeningCartoonType.scenePrimaryColor(): Int = when (this) {
    ListeningCartoonType.SAKURA -> 0xFFFF61FF.toInt()
    ListeningCartoonType.CATS -> 0xFF4E900C.toInt()
    ListeningCartoonType.RAIN_NIGHT -> 0xFF081818.toInt()
}

enum class ListeningFluidType(val storageValue: String) {
    SMOKE("smoke"),
    WATER("water"),
    EDGE("edge");

    companion object {
        fun fromStorage(value: String?): ListeningFluidType =
            entries.firstOrNull { it.storageValue == value } ?: SMOKE
    }
}

enum class ListeningFireStyle(val storageValue: String) {
    GODFIRE("godfire"),
    INFERNO("inferno"),
    RIFT("rift");

    companion object {
        fun fromStorage(value: String?): ListeningFireStyle =
            entries.firstOrNull { it.storageValue == value } ?: GODFIRE
    }
}

enum class ListeningMotionColorMode(val storageValue: String) {
    ORIGINAL("original"),
    COVER("cover"),
    CUSTOM("custom");

    companion object {
        fun fromStorage(value: String?): ListeningMotionColorMode =
            entries.firstOrNull { it.storageValue == value } ?: COVER
    }
}

data class ListeningMotionSettings(
    val enabled: Boolean = false,
    val effect: ListeningMotionEffect = ListeningMotionEffect.FLAME,
    val fireStyle: ListeningFireStyle = ListeningFireStyle.GODFIRE,
    val fluidType: ListeningFluidType = ListeningFluidType.SMOKE,
    val cartoonType: ListeningCartoonType = ListeningCartoonType.SAKURA,
    val colorMode: ListeningMotionColorMode = ListeningMotionColorMode.COVER,
    val customColor: Int = ListeningMotionConfig.DEFAULT_CUSTOM_COLOR,
    val intensity: Int = ListeningMotionConfig.DEFAULT_INTENSITY,
)

internal fun normalizeListeningMotionIntensity(value: Int): Int = value.coerceIn(0, 100)

object ListeningMotionConfig {

    const val DEFAULT_INTENSITY = 40
    const val DEFAULT_FLUID_INTENSITY = 100
    const val DEFAULT_CARTOON_INTENSITY = 100
    const val DEFAULT_CUSTOM_COLOR = -0x002EC5D9

    var enabled: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.listeningMotionEnabled, false)
        set(value) = appCtx.putPrefBoolean(PreferKey.listeningMotionEnabled, value)

    var effect: ListeningMotionEffect
        get() = ListeningMotionEffect.fromStorage(
            appCtx.getPrefString(PreferKey.listeningMotionEffect)
        )
        set(value) = appCtx.putPrefString(PreferKey.listeningMotionEffect, value.storageValue)

    var fireStyle: ListeningFireStyle
        get() = ListeningFireStyle.fromStorage(
            appCtx.getPrefString(PreferKey.listeningMotionFireStyle)
        )
        set(value) = appCtx.putPrefString(PreferKey.listeningMotionFireStyle, value.storageValue)

    var fluidType: ListeningFluidType
        get() = ListeningFluidType.fromStorage(
            appCtx.getPrefString(PreferKey.listeningMotionFluidType)
        )
        set(value) = appCtx.putPrefString(PreferKey.listeningMotionFluidType, value.storageValue)

    var cartoonType: ListeningCartoonType
        get() = ListeningCartoonType.fromStorage(
            appCtx.getPrefString(PreferKey.listeningMotionCartoonType)
        )
        set(value) = appCtx.putPrefString(
            PreferKey.listeningMotionCartoonType,
            value.storageValue,
        )

    var colorMode: ListeningMotionColorMode
        get() = ListeningMotionColorMode.fromStorage(
            appCtx.getPrefString(PreferKey.listeningMotionColorMode)
        )
        set(value) = appCtx.putPrefString(PreferKey.listeningMotionColorMode, value.storageValue)

    var customColor: Int
        get() = appCtx.getPrefInt(PreferKey.listeningMotionCustomColor, DEFAULT_CUSTOM_COLOR)
        set(value) = appCtx.putPrefInt(PreferKey.listeningMotionCustomColor, value)

    var intensity: Int
        get() = normalizeListeningMotionIntensity(
            appCtx.getPrefInt(PreferKey.listeningMotionIntensity, DEFAULT_INTENSITY)
        )
        set(value) = appCtx.putPrefInt(
            PreferKey.listeningMotionIntensity,
            normalizeListeningMotionIntensity(value),
        )

    var fluidIntensity: Int
        get() = normalizeListeningMotionIntensity(
            appCtx.getPrefInt(
                PreferKey.listeningMotionFluidIntensity,
                DEFAULT_FLUID_INTENSITY,
            )
        )
        set(value) = appCtx.putPrefInt(
            PreferKey.listeningMotionFluidIntensity,
            normalizeListeningMotionIntensity(value),
        )

    var cartoonIntensity: Int
        get() = normalizeListeningMotionIntensity(
            appCtx.getPrefInt(
                PreferKey.listeningMotionCartoonIntensity,
                DEFAULT_CARTOON_INTENSITY,
            )
        )
        set(value) = appCtx.putPrefInt(
            PreferKey.listeningMotionCartoonIntensity,
            normalizeListeningMotionIntensity(value),
        )

    fun intensityFor(effect: ListeningMotionEffect): Int = when (effect) {
        ListeningMotionEffect.FLAME -> intensity
        ListeningMotionEffect.FLUID -> fluidIntensity
        ListeningMotionEffect.CARTOON -> cartoonIntensity
    }

    fun current(): ListeningMotionSettings {
        val currentEffect = effect
        return ListeningMotionSettings(
            enabled = enabled,
            effect = currentEffect,
            fireStyle = fireStyle,
            fluidType = fluidType,
            cartoonType = cartoonType,
            colorMode = colorMode,
            customColor = customColor,
            intensity = intensityFor(currentEffect),
        )
    }
}
