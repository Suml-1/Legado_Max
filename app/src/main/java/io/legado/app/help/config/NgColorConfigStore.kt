package io.legado.app.help.config

import android.content.Context
import androidx.core.content.edit
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.ui.design.theme.NgColorGenerationMode
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgColorSpec
import io.legado.app.ui.design.theme.NgColorSystem
import io.legado.app.ui.design.theme.NgContrastLevel
import io.legado.app.ui.design.theme.NgManualColorSet
import io.legado.app.ui.design.theme.NgPaletteStyle
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.design.theme.NgTopBarTextMode
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getPrefInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 独立的 NG 配色配置存储。
 *
 * 它只管理颜色，并把当前结果窄投影到旧四颜色偏好，供尚未迁移的 View 使用。
 * 背景图、界面栏透明、Dock 与主题模式均不在这里读写。
 */
object NgColorConfigStore {

    private val lock = Any()
    private var initialized = false
    private val mutableState = MutableStateFlow<NgColorSystem?>(null)

    fun observe(context: Context): StateFlow<NgColorSystem?> {
        ensureInitialized(context)
        return mutableState.asStateFlow()
    }

    fun current(context: Context): NgColorSystem {
        ensureInitialized(context)
        return requireNotNull(mutableState.value)
    }

    fun update(context: Context, colors: NgColorSystem) {
        val normalized = colors.normalized()
        persist(context, normalized)
        mutableState.value = normalized
        projectToLegacy(context, normalized)
        ThemeConfig.applyTheme(context)
    }

    private fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            mutableState.value = read(context)
            initialized = true
        }
    }

    private fun read(context: Context): NgColorSystem {
        val prefs = context.defaultSharedPreferences
        val light = readStoredManual(context, false) ?: readLegacyManual(context, false)
        val dark = readStoredManual(context, true) ?: readLegacyManual(context, true)
        return NgColorSystem(
            mode = enumValue(
                prefs.getString(PreferKey.ngColorMode, null),
                NgColorGenerationMode.MANUAL
            ),
            lightSeed = prefs.getInt(PreferKey.ngColorLightSeed, light.primary),
            darkSeed = prefs.getInt(PreferKey.ngColorDarkSeed, dark.primary),
            paletteStyle = enumValue(
                prefs.getString(PreferKey.ngColorPaletteStyle, null),
                NgPaletteStyle.TONAL_SPOT
            ),
            contrast = enumValue(
                prefs.getString(PreferKey.ngColorContrast, null),
                NgContrastLevel.DEFAULT
            ),
            colorSpec = enumValue(
                prefs.getString(PreferKey.ngColorSpec, null),
                NgColorSpec.MATERIAL_3_2021
            ),
            manualLight = light,
            manualDark = dark,
            lightTopBarTextMode = enumValue(
                prefs.getString(PreferKey.ngColorLightTopBarTextMode, null),
                NgTopBarTextMode.AUTO
            ),
            darkTopBarTextMode = enumValue(
                prefs.getString(PreferKey.ngColorDarkTopBarTextMode, null),
                NgTopBarTextMode.AUTO
            )
        ).normalized()
    }

    private fun readStoredManual(context: Context, isDark: Boolean): NgManualColorSet? {
        val prefs = context.defaultSharedPreferences
        val primaryKey = if (isDark) PreferKey.ngColorDarkPrimary
        else PreferKey.ngColorLightPrimary
        if (!prefs.contains(primaryKey)) return null
        val fallback = readLegacyManual(context, isDark)
        return NgManualColorSet(
            primary = prefs.getInt(primaryKey, fallback.primary),
            secondary = prefs.getInt(
                if (isDark) PreferKey.ngColorDarkSecondary else PreferKey.ngColorLightSecondary,
                fallback.secondary
            ),
            primaryText = prefs.getInt(
                if (isDark) PreferKey.ngColorDarkPrimaryText
                else PreferKey.ngColorLightPrimaryText,
                fallback.primaryText
            ),
            secondaryText = prefs.getInt(
                if (isDark) PreferKey.ngColorDarkSecondaryText
                else PreferKey.ngColorLightSecondaryText,
                fallback.secondaryText
            ),
            background = prefs.getInt(
                if (isDark) PreferKey.ngColorDarkBackground else PreferKey.ngColorLightBackground,
                fallback.background
            ),
            labelContainer = prefs.getInt(
                if (isDark) PreferKey.ngColorDarkLabel else PreferKey.ngColorLightLabel,
                fallback.labelContainer
            )
        )
    }

    private fun readLegacyManual(context: Context, isDark: Boolean): NgManualColorSet {
        val primary = context.getPrefInt(
            if (isDark) PreferKey.cNAccent else PreferKey.cAccent,
            context.getCompatColor(if (isDark) R.color.md_deep_orange_800 else R.color.md_red_600)
        )
        val secondary = context.getPrefInt(
            if (isDark) PreferKey.cNPrimary else PreferKey.cPrimary,
            context.getCompatColor(if (isDark) R.color.md_blue_grey_600 else R.color.md_brown_500)
        )
        val background = context.getPrefInt(
            if (isDark) PreferKey.cNBackground else PreferKey.cBackground,
            context.getCompatColor(if (isDark) R.color.md_grey_900 else R.color.md_grey_100)
        )
        val labelContainer = context.getPrefInt(
            if (isDark) PreferKey.cNBBackground else PreferKey.cBBackground,
            context.getCompatColor(if (isDark) R.color.md_grey_850 else R.color.md_grey_200)
        )
        return NgManualColorSet(
            primary = primary,
            secondary = secondary,
            primaryText = NgColorMath.contentColorFor(background),
            secondaryText = NgColorMath.contentColorFor(labelContainer),
            background = background,
            labelContainer = labelContainer
        )
    }

    private fun persist(context: Context, colors: NgColorSystem) {
        context.defaultSharedPreferences.edit {
            putString(PreferKey.ngColorMode, colors.mode.name)
            putInt(PreferKey.ngColorLightSeed, colors.lightSeed)
            putInt(PreferKey.ngColorDarkSeed, colors.darkSeed)
            putString(PreferKey.ngColorPaletteStyle, colors.paletteStyle.name)
            putString(PreferKey.ngColorContrast, colors.contrast.name)
            putString(PreferKey.ngColorSpec, colors.colorSpec.name)
            putString(
                PreferKey.ngColorLightTopBarTextMode,
                colors.lightTopBarTextMode.name
            )
            putString(
                PreferKey.ngColorDarkTopBarTextMode,
                colors.darkTopBarTextMode.name
            )
            putManual(false, colors.manualLight)
            putManual(true, colors.manualDark)
        }
    }

    private fun android.content.SharedPreferences.Editor.putManual(
        isDark: Boolean,
        value: NgManualColorSet
    ) {
        putInt(
            if (isDark) PreferKey.ngColorDarkPrimary else PreferKey.ngColorLightPrimary,
            value.primary
        )
        putInt(
            if (isDark) PreferKey.ngColorDarkSecondary else PreferKey.ngColorLightSecondary,
            value.secondary
        )
        putInt(
            if (isDark) PreferKey.ngColorDarkPrimaryText else PreferKey.ngColorLightPrimaryText,
            value.primaryText
        )
        putInt(
            if (isDark) PreferKey.ngColorDarkSecondaryText
            else PreferKey.ngColorLightSecondaryText,
            value.secondaryText
        )
        putInt(
            if (isDark) PreferKey.ngColorDarkBackground else PreferKey.ngColorLightBackground,
            value.background
        )
        putInt(
            if (isDark) PreferKey.ngColorDarkLabel else PreferKey.ngColorLightLabel,
            value.labelContainer
        )
    }

    private fun projectToLegacy(context: Context, colors: NgColorSystem) {
        val light = NgThemeResolver.resolveColorScheme(context, colors, false)
        val dark = NgThemeResolver.resolveColorScheme(context, colors, true)
        val lightPrimary = if (colors.mode == NgColorGenerationMode.MANUAL) {
            colors.manualLight.secondary
        } else {
            light.topBarContainer
        }
        val lightBottom = if (colors.mode == NgColorGenerationMode.MANUAL) {
            colors.manualLight.labelContainer
        } else {
            light.surfaceContainerLow
        }
        val darkPrimary = if (colors.mode == NgColorGenerationMode.MANUAL) {
            colors.manualDark.secondary
        } else {
            dark.topBarContainer
        }
        val darkBottom = if (colors.mode == NgColorGenerationMode.MANUAL) {
            colors.manualDark.labelContainer
        } else {
            dark.surfaceContainerLow
        }
        context.defaultSharedPreferences.edit {
            putInt(PreferKey.cPrimary, lightPrimary)
            putInt(PreferKey.cAccent, light.primary)
            putInt(PreferKey.cBackground, light.background)
            putInt(PreferKey.cBBackground, lightBottom)
            putInt(PreferKey.cNPrimary, darkPrimary)
            putInt(PreferKey.cNAccent, dark.primary)
            putInt(PreferKey.cNBackground, dark.background)
            putInt(PreferKey.cNBBackground, darkBottom)
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
