package io.legado.app.help.config

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefString
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString

internal enum class NgThemeModeGroup {
    STANDARD,
    INTERNAL,
}

internal enum class NgThemePresentationMode {
    STANDARD,
    SOFT_GRADIENT,
    MULTICOLOR,
    EFFECT_THEME,
    EINK,
}

/** Custom themes own their renderer; built-in modes keep a separate last selection. */
internal object NgThemeModeStore {

    private const val PRESENTATION_STANDARD = "standard"
    private const val PRESENTATION_SOFT_GRADIENT = "soft_gradient"
    private const val PRESENTATION_MULTICOLOR = "multicolor"
    private const val PRESENTATION_EFFECT_THEME = "effect_theme"
    private const val STANDARD_MODE_FOLLOW = "0"
    private const val STANDARD_MODE_DAY = "1"
    private val standardModes = setOf("0", "1", "2")

    private fun requestedPresentation(context: Context): NgThemePresentationMode = when {
        AppConfig.isEInkMode -> NgThemePresentationMode.EINK
        context.getPrefString(
            PreferKey.ngThemePresentationMode,
            PRESENTATION_MULTICOLOR,
        ) == PRESENTATION_SOFT_GRADIENT -> NgThemePresentationMode.SOFT_GRADIENT
        context.getPrefString(
            PreferKey.ngThemePresentationMode,
            PRESENTATION_MULTICOLOR,
        ) == PRESENTATION_MULTICOLOR -> NgThemePresentationMode.MULTICOLOR
        context.getPrefString(
            PreferKey.ngThemePresentationMode,
            PRESENTATION_MULTICOLOR,
        ) == PRESENTATION_EFFECT_THEME -> NgThemePresentationMode.EFFECT_THEME
        else -> NgThemePresentationMode.STANDARD
    }

    /** The selected settings group may use the foundation until an appearance is explicitly applied. */
    fun current(context: Context): NgThemePresentationMode {
        val requested = requestedPresentation(context)
        return if (requested in customPresentations && !hasCustomAppearance(context, requested)) {
            NgThemePresentationMode.MULTICOLOR
        } else requested
    }

    private val customPresentations = setOf(
        NgThemePresentationMode.STANDARD, NgThemePresentationMode.EFFECT_THEME,
    )

    private fun hasCustomAppearance(context: Context, presentation: NgThemePresentationMode): Boolean {
        if (presentation == NgThemePresentationMode.STANDARD &&
            context.getPrefBoolean(PreferKey.ngCustomAppearanceEdited, false)
        ) return true
        val selected = NgThemeLibraryStore.activeTheme(context) ?: return false
        return presentation == NgThemePresentationMode.STANDARD || selected.effect != null
    }

    fun isCustomFoundation(context: Context): Boolean =
        currentGroup(context) == NgThemeModeGroup.STANDARD && current(context) == NgThemePresentationMode.MULTICOLOR

    fun currentGroup(context: Context): NgThemeModeGroup = when (requestedPresentation(context)) {
        NgThemePresentationMode.STANDARD,
        NgThemePresentationMode.EFFECT_THEME -> NgThemeModeGroup.STANDARD
        NgThemePresentationMode.SOFT_GRADIENT,
        NgThemePresentationMode.MULTICOLOR,
        NgThemePresentationMode.EINK -> NgThemeModeGroup.INTERNAL
    }

    fun standardThemeMode(context: Context): String {
        val stored = context.getPrefString(PreferKey.ngStandardThemeMode)
        if (stored != null && stored in standardModes) return stored
        val requested = requestedPresentation(context)
        return if (requested in customPresentations && hasCustomAppearance(context, requested)) {
            AppConfig.themeMode.takeIf { it in standardModes } ?: STANDARD_MODE_FOLLOW
        } else STANDARD_MODE_FOLLOW
    }

    fun lastCustomMode(context: Context): NgThemePresentationMode {
        val active = requestedPresentation(context)
        if (active == NgThemePresentationMode.STANDARD || active == NgThemePresentationMode.EFFECT_THEME) {
            return active
        }
        return if (context.getPrefString(PreferKey.ngCustomThemePresentationMode) == PRESENTATION_EFFECT_THEME) {
            NgThemePresentationMode.EFFECT_THEME
        } else {
            NgThemePresentationMode.STANDARD
        }
    }

    fun lastInternalMode(context: Context): NgThemePresentationMode =
        when (context.getPrefString(PreferKey.ngInternalThemeMode)) {
            INTERNAL_MODE_EINK -> NgThemePresentationMode.EINK
            INTERNAL_MODE_SOFT_GRADIENT -> NgThemePresentationMode.SOFT_GRADIENT
            PRESENTATION_MULTICOLOR -> NgThemePresentationMode.MULTICOLOR
            else -> if (AppConfig.isEInkMode) NgThemePresentationMode.EINK else NgThemePresentationMode.MULTICOLOR
        }

    fun activateGroup(context: Context, group: NgThemeModeGroup) {
        when (group) {
            NgThemeModeGroup.STANDARD -> activateStandard(context, standardThemeMode(context))
            NgThemeModeGroup.INTERNAL -> activateInternal(context, lastInternalMode(context))
        }
    }

    /** Day/night is independent of whether the selected custom theme renders an effect. */
    fun activateStandard(context: Context, themeMode: String) {
        val presentation = if (lastCustomMode(context) == NgThemePresentationMode.EFFECT_THEME) {
            PRESENTATION_EFFECT_THEME
        } else {
            PRESENTATION_STANDARD
        }
        val normalized = themeMode.takeIf { it in standardModes } ?: STANDARD_MODE_FOLLOW
        context.putPrefString(PreferKey.ngStandardThemeMode, normalized)
        context.putPrefString(PreferKey.ngCustomThemePresentationMode, presentation)
        context.putPrefString(PreferKey.ngThemePresentationMode, presentation)
        val requested = if (presentation == PRESENTATION_EFFECT_THEME) NgThemePresentationMode.EFFECT_THEME
            else NgThemePresentationMode.STANDARD
        val displayMode = if (hasCustomAppearance(context, requested)) normalized else STANDARD_MODE_DAY
        ThemeConfig.applyThemeMode(context, displayMode)
    }

    /** Keeps the custom controls open while displaying the same asset-free foundation. */
    internal fun activateCustomFoundation(context: Context) {
        context.putPrefBoolean(PreferKey.ngCustomAppearanceEdited, false)
        context.putPrefString(PreferKey.ngCustomThemePresentationMode, PRESENTATION_STANDARD)
        context.putPrefString(PreferKey.ngThemePresentationMode, PRESENTATION_STANDARD)
        ThemeConfig.applyThemeMode(context, STANDARD_MODE_DAY)
    }

    /** Called only after an explicit manual background save or color edit. */
    internal fun activateManualAppearance(context: Context) {
        val mode = standardThemeMode(context)
        context.putPrefBoolean(PreferKey.ngCustomAppearanceEdited, true)
        context.putPrefString(PreferKey.ngCustomThemePresentationMode, PRESENTATION_STANDARD)
        activateStandard(context, mode)
    }

    fun activateInternal(context: Context, mode: NgThemePresentationMode) {
        require(
            mode == NgThemePresentationMode.SOFT_GRADIENT ||
                mode == NgThemePresentationMode.MULTICOLOR ||
                mode == NgThemePresentationMode.EINK
        )
        captureCustomMode(context)
        when (mode) {
            NgThemePresentationMode.SOFT_GRADIENT -> {
                context.putPrefString(PreferKey.ngThemePresentationMode, PRESENTATION_SOFT_GRADIENT)
                context.putPrefString(PreferKey.ngInternalThemeMode, INTERNAL_MODE_SOFT_GRADIENT)
                ThemeConfig.applyThemeMode(context, STANDARD_MODE_DAY)
            }
            NgThemePresentationMode.MULTICOLOR -> {
                context.putPrefString(PreferKey.ngThemePresentationMode, PRESENTATION_MULTICOLOR)
                context.putPrefString(PreferKey.ngInternalThemeMode, PRESENTATION_MULTICOLOR)
                ThemeConfig.applyThemeMode(context, STANDARD_MODE_DAY)
            }
            NgThemePresentationMode.EINK -> {
                context.putPrefString(PreferKey.ngThemePresentationMode, PRESENTATION_STANDARD)
                context.putPrefString(PreferKey.ngInternalThemeMode, INTERNAL_MODE_EINK)
                ThemeConfig.applyThemeMode(context, EINK_THEME_MODE)
            }
            else -> error("Not a built-in theme mode")
        }
    }

    /** Theme management is the only place that selects an external effect renderer. */
    internal fun selectManagedTheme(context: Context, effect: Boolean) {
        context.putPrefBoolean(PreferKey.ngCustomAppearanceEdited, false)
        val presentation = if (effect) PRESENTATION_EFFECT_THEME else PRESENTATION_STANDARD
        context.putPrefString(PreferKey.ngCustomThemePresentationMode, presentation)
        context.putPrefString(PreferKey.ngThemePresentationMode, presentation)
    }

    internal fun captureCustomMode(context: Context) {
        val presentation = requestedPresentation(context)
        if (presentation !in customPresentations) return
        if (current(context) != NgThemePresentationMode.MULTICOLOR) {
            AppConfig.themeMode.takeIf { it in standardModes }?.let {
                context.putPrefString(PreferKey.ngStandardThemeMode, it)
            }
        }
        context.putPrefString(
            PreferKey.ngCustomThemePresentationMode,
            if (presentation == NgThemePresentationMode.EFFECT_THEME) PRESENTATION_EFFECT_THEME else PRESENTATION_STANDARD,
        )
    }

    private const val INTERNAL_MODE_SOFT_GRADIENT = "soft_gradient"
    private const val INTERNAL_MODE_EINK = "eink"
    private const val EINK_THEME_MODE = "3"
}
