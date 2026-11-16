package io.legado.app.ui.design.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** 漫画工具面保持浅色，只从外部主题获取主色，不消费小说阅读配色。 */
@Composable
internal fun NgMangaPanelTheme(content: @Composable () -> Unit) {
    val source = NgTheme.snapshot
    val snapshot = remember(source) { resolveNgMangaPanelTheme(source) }
    NgAppTheme(snapshot = snapshot, updateSystemBars = false, content = content)
}

internal fun resolveNgMangaPanelTheme(source: NgThemeSnapshot): NgThemeSnapshot {
    if (source.isEInk) return source
    val white = 0xFFFFFFFF.toInt()
    val text = 0xFF18201F.toInt()
    val primary = NgColorMath.opaque(source.colors.primary)
    // 近白中性底轻染主色；不沿用主题 surface 或阅读纸张的浓色/透明度。
    fun tint(amount: Float) = NgColorMath.blend(0xFFF5F7F7.toInt(), primary, amount)
    val top = tint(0.035f)
    val bottom = tint(0.05f)
    val selected = tint(0.12f)
    return source.copy(
        isDark = false,
        backdropContent = NgBackdropContentTokens(),
        colors = NgColorScheme(
            primary = primary,
            onPrimary = source.colors.onPrimary,
            primaryContainer = selected,
            onPrimaryContainer = text,
            secondary = primary,
            tertiary = primary,
            background = bottom,
            onBackground = text,
            surface = top,
            surfaceTint = primary,
            onSurface = text,
            surfaceVariant = selected,
            onSurfaceVariant = 0xFF66716F.toInt(),
            surfaceContainerLow = top,
            surfaceContainer = bottom,
            surfaceContainerHigh = selected,
            outline = 0xFF879390.toInt(),
            outlineVariant = tint(0.14f),
            error = source.colors.error,
            onError = source.colors.onError,
            errorContainer = source.colors.errorContainer,
            onErrorContainer = source.colors.onErrorContainer,
            inverseSurface = text,
            inverseOnSurface = white,
            scrim = 0xFF000000.toInt(),
            topBarContainer = top,
            onTopBar = text,
            cardContainer = white,
            dialogContainer = white,
            drawerContainer = bottom,
            inputContainer = white,
            selectedContainer = selected,
        ),
    )
}
