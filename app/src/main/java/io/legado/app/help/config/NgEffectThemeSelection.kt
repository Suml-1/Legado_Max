package io.legado.app.help.config

import android.content.Context
import io.legado.app.ui.design.theme.NgColorSystem

/** The renderer is owned by the selected complete theme, never by a second scene package. */
internal object NgEffectThemeSelection {
    fun current(context: Context): NgManagedTheme? =
        NgThemeLibraryStore.activeTheme(context)?.takeIf { it.effect != null }

    fun theme(context: Context): NgManagedTheme? = current(context)

    fun colors(context: Context): NgColorSystem =
        current(context)?.colors ?: NgColorConfigStore.current(context)

    fun updateColors(context: Context, colors: NgColorSystem) {
        val theme = current(context) ?: return
        NgThemeLibraryStore.addOrReplace(context, theme.copy(colors = colors.normalized()), matchName = false)
        NgColorConfigStore.update(context, colors.normalized())
        ThemeConfig.applyTheme(context)
    }
}
