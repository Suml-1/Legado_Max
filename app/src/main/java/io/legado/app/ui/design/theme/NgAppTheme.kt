package io.legado.app.ui.design.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NgColorConfigStore
import io.legado.app.help.config.NgThemeModeStore
import io.legado.app.help.config.NgThemePresentationMode
import io.legado.app.help.config.NgThemeRuntimeAssets
import io.legado.app.help.config.NgInterfaceFontStore
import io.legado.app.help.config.NgVisualSystem
import io.legado.app.help.config.NgVisualSystemStore
import io.legado.app.help.config.resolveThemeNightMode
import io.legado.app.utils.isNightMode

private val LocalNgThemeSnapshot = staticCompositionLocalOf<NgThemeSnapshot> {
    error("NgThemeSnapshot is not available outside NgAppTheme")
}

private val LocalNgVisualSystem = staticCompositionLocalOf {
    NgVisualSystem.DEFAULT
}

object NgTheme {
    val snapshot: NgThemeSnapshot
        @Composable
        @ReadOnlyComposable
        get() = LocalNgThemeSnapshot.current

    val colors: NgColorScheme
        @Composable
        @ReadOnlyComposable
        get() = snapshot.colors

    val spacing: NgSpacingTokens
        @Composable
        @ReadOnlyComposable
        get() = snapshot.spacing

    val shapes: NgShapeTokens
        @Composable
        @ReadOnlyComposable
        get() = snapshot.shapes

    val typography: NgTypographyTokens
        @Composable
        @ReadOnlyComposable
        get() = snapshot.typography

    /** Custom text styles inherit only the interface font, keeping their existing metrics. */
    val fontFamily: FontFamily?
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge.fontFamily

    val effects: NgEffectTokens
        @Composable
        @ReadOnlyComposable
        get() = snapshot.effects

    val visualSystem: NgVisualSystem
        @Composable
        @ReadOnlyComposable
        get() = LocalNgVisualSystem.current

    val usesLiquidGlass: Boolean
        @Composable
        @ReadOnlyComposable
        get() = visualSystem == NgVisualSystem.LIQUID_GLASS && !snapshot.isEInk
}

/** Captures already resolved locals for display-only content without another theme subscription. */
@Immutable
class NgThemeContext internal constructor(
    internal val snapshot: NgThemeSnapshot,
    internal val visualSystem: NgVisualSystem,
    internal val colorScheme: ColorScheme,
    internal val typography: Typography,
    internal val shapes: Shapes,
)

@Composable
fun rememberNgThemeContext(): NgThemeContext {
    val snapshot = NgTheme.snapshot
    val visualSystem = NgTheme.visualSystem
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    return remember(snapshot, visualSystem, colorScheme, typography, shapes) {
        NgThemeContext(snapshot, visualSystem, colorScheme, typography, shapes)
    }
}

/** Keeps captured content colors and fonts while the surrounding drawer retains its own roles. */
@Composable
fun NgThemeContextProvider(context: NgThemeContext, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalNgThemeSnapshot provides context.snapshot,
        LocalNgVisualSystem provides context.visualSystem,
    ) {
        MaterialTheme(colorScheme = context.colorScheme, typography = context.typography,
            shapes = context.shapes, content = content)
    }
}

@Composable
fun NgAppTheme(
    snapshot: NgThemeSnapshot? = null,
    updateSystemBars: Boolean = true,
    darkModeOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val resolvedSnapshot = snapshot ?: rememberNgThemeSnapshot(darkModeOverride)
    val view = LocalView.current
    val context = LocalContext.current
    val visualSystemFlow = remember(context) { NgVisualSystemStore.observe(context) }
    val observedVisualSystem by visualSystemFlow.collectAsState()
    val visualSystem = observedVisualSystem ?: NgVisualSystemStore.current(context)
    val interfaceFontRevision by NgInterfaceFontStore.revision.collectAsState()
    val appTypeface = NgThemeRuntimeAssets.appTypeface(context)
    val typography = remember(appTypeface, interfaceFontRevision) {
        Typography().withFontFamily(appTypeface?.let(::interfaceFontFamily))
    }
    if (updateSystemBars) {
        SideEffect {
            if (!view.isInEditMode) {
                view.context.findActivity()?.window?.let { window ->
                    WindowInsetsControllerCompat(window, view).apply {
                        isAppearanceLightStatusBars =
                            resolvedSnapshot.systemBars.darkStatusBarIcons
                        isAppearanceLightNavigationBars =
                            resolvedSnapshot.systemBars.darkNavigationBarIcons
                    }
                }
            }
        }
    }
    CompositionLocalProvider(
        LocalNgThemeSnapshot provides resolvedSnapshot,
        LocalNgVisualSystem provides visualSystem,
    ) {
        MaterialTheme(
            colorScheme = resolvedSnapshot.toMaterialColorScheme(),
            shapes = resolvedSnapshot.shapes.toMaterialShapes(),
            typography = typography,
            content = content
        )
    }
}

@Composable
private fun rememberNgThemeSnapshot(darkModeOverride: Boolean?): NgThemeSnapshot {
    val context = LocalContext.current
    val systemNightMode = LocalConfiguration.current.isNightMode
    val themeMode = AppConfig.themeMode ?: "0"
    val presentationMode = NgThemeModeStore.current(context)
    val isDark = resolveNgThemeNightMode(themeMode, systemNightMode, darkModeOverride)
    val colorFlow = remember(context) { NgColorConfigStore.observe(context) }
    val observedColors by colorFlow.collectAsState()
    val colors = observedColors ?: NgColorConfigStore.current(context)
    return remember(
        context,
        themeMode,
        presentationMode,
        isDark,
        colors,
        darkModeOverride,
    ) {
        if (presentationMode != NgThemePresentationMode.STANDARD) {
            NgThemeResolver.resolve(context)
        } else {
            NgThemeResolver.resolve(
                context = context,
                colors = colors,
                isDark = isDark
            )
        }
    }
}

internal fun resolveNgThemeNightMode(
    themeMode: String,
    systemNightMode: Boolean,
    darkModeOverride: Boolean?
): Boolean = darkModeOverride ?: resolveThemeNightMode(themeMode, systemNightMode)

private fun Typography.withFontFamily(fontFamily: FontFamily?): Typography {
    if (fontFamily == null) return this
    return copy(
        displayLarge = displayLarge.copy(fontFamily = fontFamily),
        displayMedium = displayMedium.copy(fontFamily = fontFamily),
        displaySmall = displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = titleLarge.copy(fontFamily = fontFamily),
        titleMedium = titleMedium.copy(fontFamily = fontFamily),
        titleSmall = titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = bodySmall.copy(fontFamily = fontFamily),
        labelLarge = labelLarge.copy(fontFamily = fontFamily),
        labelMedium = labelMedium.copy(fontFamily = fontFamily),
        labelSmall = labelSmall.copy(fontFamily = fontFamily),
    )
}

private fun NgThemeSnapshot.toMaterialColorScheme() = colors.run {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    base.copy(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        secondary = Color(secondary),
        tertiary = Color(tertiary),
        background = Color(background),
        onBackground = Color(onBackground),
        surface = Color(surface),
        surfaceTint = Color(surfaceTint),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        outline = Color(outline),
        outlineVariant = Color(outlineVariant),
        error = Color(error),
        onError = Color(onError),
        errorContainer = Color(errorContainer),
        onErrorContainer = Color(onErrorContainer),
        inverseSurface = Color(inverseSurface),
        inverseOnSurface = Color(inverseOnSurface),
        scrim = Color(scrim)
    )
}

private fun NgShapeTokens.toMaterialShapes(): Shapes {
    return Shapes(
        small = RoundedCornerShape(smallDp.dp),
        medium = RoundedCornerShape(mediumDp.dp),
        large = RoundedCornerShape(largeDp.dp)
    )
}

private fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
