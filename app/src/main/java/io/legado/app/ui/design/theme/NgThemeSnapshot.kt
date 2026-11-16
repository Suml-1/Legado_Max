package io.legado.app.ui.design.theme

import androidx.annotation.ColorInt

/**
 * 完整、只读的 NG 运行时主题快照。
 *
 * 旧 ThemeStore、未来主题包和种子色算法都应先解析为该结构，
 * View 与 Compose 组件不再分别推导主题语义。
 */
data class NgThemeSnapshot(
    val isDark: Boolean,
    val isEInk: Boolean,
    val colors: NgColorScheme,
    val backdropContent: NgBackdropContentTokens = NgBackdropContentTokens(),
    val shapes: NgShapeTokens = NgShapeTokens(),
    val spacing: NgSpacingTokens = NgSpacingTokens(),
    val typography: NgTypographyTokens = NgTypographyTokens(),
    val effects: NgEffectTokens = NgEffectTokens(),
    val motion: NgMotionTokens = NgMotionTokens(),
    val systemBars: NgSystemBarTokens
)

data class NgBackdropContentTokens(
    @param:ColorInt val topNavigationActive: Int? = null,
    @param:ColorInt val topNavigationInactive: Int? = null,
    @param:ColorInt val primaryContent: Int? = null,
    @param:ColorInt val secondaryContent: Int? = null,
    @param:ColorInt val textShadow: Int? = null,
)

data class NgColorScheme(
    @ColorInt val primary: Int,
    @ColorInt val onPrimary: Int,
    @ColorInt val primaryContainer: Int,
    @ColorInt val onPrimaryContainer: Int,
    @ColorInt val secondary: Int,
    @ColorInt val tertiary: Int,
    @ColorInt val background: Int,
    @ColorInt val onBackground: Int,
    @ColorInt val surface: Int,
    @ColorInt val surfaceTint: Int,
    @ColorInt val onSurface: Int,
    @ColorInt val surfaceVariant: Int,
    @ColorInt val onSurfaceVariant: Int,
    @ColorInt val surfaceContainerLow: Int,
    @ColorInt val surfaceContainer: Int,
    @ColorInt val surfaceContainerHigh: Int,
    @ColorInt val outline: Int,
    @ColorInt val outlineVariant: Int,
    @ColorInt val error: Int,
    @ColorInt val onError: Int,
    @ColorInt val errorContainer: Int,
    @ColorInt val onErrorContainer: Int,
    @ColorInt val inverseSurface: Int,
    @ColorInt val inverseOnSurface: Int,
    @ColorInt val scrim: Int,
    @ColorInt val topBarContainer: Int,
    @ColorInt val onTopBar: Int,
    @ColorInt val cardContainer: Int,
    @ColorInt val dialogContainer: Int,
    @ColorInt val drawerContainer: Int,
    @ColorInt val inputContainer: Int,
    @ColorInt val selectedContainer: Int
)

data class NgShapeTokens(
    val smallDp: Int = 8,
    val mediumDp: Int = 12,
    val largeDp: Int = 18,
    val extraLargeDp: Int = 24,
    val dialogDp: Int = 28
)

data class NgSpacingTokens(
    val extraSmallDp: Int = 4,
    val smallDp: Int = 8,
    val mediumDp: Int = 12,
    val largeDp: Int = 16,
    val extraLargeDp: Int = 20,
    val pageHorizontalDp: Int = 16,
    val cardContentDp: Int = 14
)

data class NgTypographyTokens(
    val pageTitleSp: Int = 24,
    val sectionTitleSp: Int = 20,
    val itemTitleSp: Int = 16,
    val compactItemTitleSp: Int = 14,
    val denseItemTitleSp: Int = 11,
    val denseItemSummarySp: Int = 10,
    val denseBadgeSp: Int = 11,
    val bodySp: Int = 15,
    val summarySp: Int = 13,
    val labelSp: Int = 12
)

data class NgEffectTokens(
    val blurEnabled: Boolean = true,
    val containerAlpha: Float = 0.50f,
    val dialogAlpha: Float = 0.88f,
    val drawerAlpha: Float = 0.80f,
    val blurRadiusDp: Int = 18,
    val cardElevationDp: Int = 0,
    val overlayElevationDp: Int = 8
)

data class NgMotionTokens(
    val enabled: Boolean = true,
    val shortDurationMs: Int = 150,
    val mediumDurationMs: Int = 250,
    val longDurationMs: Int = 400
)

data class NgSystemBarTokens(
    val darkStatusBarIcons: Boolean,
    val darkNavigationBarIcons: Boolean
)
