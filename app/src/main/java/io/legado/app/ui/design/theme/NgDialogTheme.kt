package io.legado.app.ui.design.theme

/** Align locally overridden themes with the resource-backed NG dialog surface. */
internal fun resolveNgDialogTheme(
    source: NgThemeSnapshot,
    isDark: Boolean,
    surfaceColor: Int,
): NgThemeSnapshot {
    if (source.isDark == isDark || source.isEInk) return source
    val palette = NgThemeResolver.resolve(
        NgLegacyThemeInput(
            primaryColor = surfaceColor,
            accentColor = source.colors.primary,
            backgroundColor = surfaceColor,
            bottomBackground = surfaceColor,
            errorColor = source.colors.error,
            isDark = isDark,
            isEInk = false,
        )
    )
    return source.copy(isDark = isDark, colors = palette.colors)
}
