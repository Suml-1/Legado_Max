package io.legado.app.ui.design.components.compose

import android.content.Context
import androidx.annotation.ColorInt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.NgDrawerAppearanceConfig
import io.legado.app.help.config.NgThemeDrawerProfile
import io.legado.app.help.config.NgDrawerProfileStore
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgDrawerPalette
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.design.theme.NgThemeSnapshot

enum class NgDrawerDragHandleVariant {
    STANDARD,
    COMPACT,
}

enum class NgDrawerContentCardStyle {
    LEGACY,
    ADAPTIVE,
}

private val LocalNgDrawerContentCardStyle = staticCompositionLocalOf {
    NgDrawerContentCardStyle.LEGACY
}

private val LocalNgDrawerHasThemeProfile = staticCompositionLocalOf { false }
private val LocalNgDrawerHasBackgroundImage = compositionLocalOf { false }
internal val LocalNgDrawerContentCardAlpha = compositionLocalOf { 1f }

/** Hosts with their own palette can opt out of the global drawer background profile. */
internal val LocalNgDrawerInheritThemeProfile = staticCompositionLocalOf { true }

/** Observe current drawer settings; theme previews may explicitly override this profile. */
@Composable
fun rememberNgDrawerThemeProfile(): NgThemeDrawerProfile? {
    if (!LocalNgDrawerInheritThemeProfile.current) return null
    val context = LocalContext.current
    val state by remember(context) { NgDrawerProfileStore.observe(context) }.collectAsState()
    return state.profile
}

/** Reusable by reader-owned drawer hosts without changing their geometry or floating tools. */
@Composable
internal fun NgDrawerThemeProvider(
    snapshot: NgThemeSnapshot,
    contentCardStyle: NgDrawerContentCardStyle,
    hasThemeProfile: Boolean,
    hasBackgroundImage: Boolean = false,
    content: @Composable () -> Unit,
) {
    val effectiveCardStyle = if (hasThemeProfile) NgDrawerContentCardStyle.ADAPTIVE else contentCardStyle
    NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
        CompositionLocalProvider(
            LocalNgDrawerContentCardStyle provides effectiveCardStyle,
            LocalNgDrawerHasThemeProfile provides hasThemeProfile,
            LocalNgDrawerHasBackgroundImage provides hasBackgroundImage,
            content = content,
        )
    }
}

/** Keep an opted-in content card opaque only while a drawer image is displayed. */
@Composable
internal fun ngDrawerImageContentCardColor(fallback: Color): Color =
    if (LocalNgDrawerHasBackgroundImage.current) fallback.copy(alpha = 1f) else fallback

/** Place before clipping so the image-backed card casts a soft, borderless shadow. */
@Composable
internal fun Modifier.ngDrawerImageContentCardShadow(
    shape: Shape,
    fallback: BorderStroke? = null,
): Modifier = if (LocalNgDrawerHasBackgroundImage.current) {
    shadow(
        elevation = 4.dp,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.12f),
        spotColor = Color.Black.copy(alpha = 0.18f),
    )
} else if (fallback != null) {
    border(fallback, shape)
} else this

/** Only theme-owned drawer controls use their paired onPrimary role. */
@Composable
internal fun ngDrawerPrimaryContentColor(): Color = if (LocalNgDrawerHasThemeProfile.current) {
    Color(NgTheme.colors.onPrimary)
} else Color.White

@Composable
internal fun ngDrawerThemedActionContainerColor(fallback: Color): Color =
    if (LocalNgDrawerHasThemeProfile.current) Color(NgTheme.colors.cardContainer) else fallback

/** Explicit fallbacks preserve established colors outside theme-owned drawers. */
@Composable
internal fun ngDrawerPrimaryTextColor(fallback: Color): Color =
    if (LocalNgDrawerHasThemeProfile.current) Color(NgTheme.colors.onSurface) else fallback

@Composable
internal fun ngDrawerSecondaryTextColor(fallback: Color): Color =
    if (LocalNgDrawerHasThemeProfile.current) Color(NgTheme.colors.onSurfaceVariant) else fallback

@Composable
internal fun ngDrawerAccentColor(fallback: Color): Color =
    if (LocalNgDrawerHasThemeProfile.current) Color(NgTheme.colors.primary) else fallback

@Composable
internal fun ngDrawerThemedContainerColor(fallback: Color): Color =
    if (LocalNgDrawerHasThemeProfile.current) Color(NgTheme.colors.cardContainer) else fallback

@Composable
internal fun ngDrawerThemedOutlineColor(fallback: Color): Color =
    if (LocalNgDrawerHasThemeProfile.current) Color(NgTheme.colors.outlineVariant) else fallback

/** 当前全局 NG 抽屉的外观快照。 */
@Immutable
data class NgDrawerAppearance(
    val transparencyPercent: Int,
    val primaryStrengthPercent: Int,
    val horizontalMarginDp: Int,
    val cornerRadiusDp: Int,
)

object NgDrawerDefaults {

    fun currentAppearance(): NgDrawerAppearance = NgDrawerAppearance(
        transparencyPercent = NgDrawerAppearanceConfig.DEFAULT_TRANSPARENCY_PERCENT,
        primaryStrengthPercent = NgDrawerAppearanceConfig.DEFAULT_PRIMARY_STRENGTH_PERCENT,
        horizontalMarginDp = NgDrawerAppearanceConfig.DEFAULT_HORIZONTAL_MARGIN_DP,
        cornerRadiusDp = NgDrawerAppearanceConfig.DEFAULT_CORNER_RADIUS_DP,
    )

    @Composable
    fun rememberAppearance(): NgDrawerAppearance = remember { currentAppearance() }

    @Composable
    fun style(
        appearance: NgDrawerAppearance,
        backgroundColor: Int? = null,
    ): NgGlassStyle = NgGlassDefaults.drawerStyle(
        transparencyPercent = appearance.transparencyPercent,
        primaryStrengthPercent = appearance.primaryStrengthPercent,
        backgroundColor = backgroundColor,
    )

    @ColorInt
    fun adaptiveContentCardColor(context: Context): Int {
        val snapshot = NgThemeResolver.resolve(context)
        return NgDrawerPalette.resolveAdaptiveContentCardColor(
            snapshot = snapshot,
            primaryStrengthPercent = NgDrawerAppearanceConfig.normalizePercent(
                currentAppearance().primaryStrengthPercent
            ),
            backgroundColor = NgDrawerProfileStore.current(context)
                ?.takeIf { it.source == "custom_color" }?.forNight(snapshot.isDark)?.backgroundColor,
        )
    }
}

/**
 * 全局 NG 底部抽屉的公共承载面。
 *
 * 当前设置仅选择背景来源，自定义颜色之外的内容颜色沿用自动语义。
 * 主题保存并应用同一设置。图片固定在外壳，不参与内容测量或滚动。
 */
@Composable
fun NgBottomDrawerSurface(
    modifier: Modifier = Modifier,
    appearance: NgDrawerAppearance = NgDrawerDefaults.rememberAppearance(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentCardStyle: NgDrawerContentCardStyle = NgDrawerContentCardStyle.LEGACY,
    themeProfile: NgThemeDrawerProfile? = rememberNgDrawerThemeProfile(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val baseSnapshot = NgTheme.snapshot
    val backgroundColor = remember(themeProfile, baseSnapshot.isDark, baseSnapshot.isEInk) {
        themeProfile?.takeIf { !baseSnapshot.isEInk && it.source == "custom_color" }
            ?.forNight(baseSnapshot.isDark)?.backgroundColor
    }
    val imageSource = rememberNgDrawerImageSource(themeProfile)
    val imageReady = remember(imageSource) { mutableStateOf(false) }
    val normalized = remember(appearance) { appearance.normalized() }
    val radius = normalized.cornerRadiusDp.dp
    val shape = if (normalized.horizontalMarginDp == 0) {
        RoundedCornerShape(topStart = radius, topEnd = radius)
    } else {
        RoundedCornerShape(radius)
    }
    val semanticSnapshot = remember(
        baseSnapshot,
        normalized.primaryStrengthPercent,
        contentCardStyle,
        backgroundColor,
    ) {
        when (contentCardStyle) {
            NgDrawerContentCardStyle.LEGACY -> NgDrawerPalette.applySemanticRoles(
                snapshot = baseSnapshot,
                primaryStrengthPercent = normalized.primaryStrengthPercent,
                backgroundColor = backgroundColor,
            )

            NgDrawerContentCardStyle.ADAPTIVE ->
                NgDrawerPalette.applyAdaptiveContentCardRoles(
                    snapshot = baseSnapshot,
                    primaryStrengthPercent = normalized.primaryStrengthPercent,
                    backgroundColor = backgroundColor,
                )
        }
    }
    // Resolve the material from the original snapshot, before installing local content roles.
    val customGlassStyle = backgroundColor?.let { NgDrawerDefaults.style(normalized, it) }
    NgDrawerThemeProvider(
        snapshot = semanticSnapshot,
        contentCardStyle = contentCardStyle,
        hasThemeProfile = backgroundColor != null,
        hasBackgroundImage = imageReady.value,
    ) {
        val nestedScrollInteropConnection = rememberNestedScrollInteropConnection()
        NgGlassSurface(
            modifier = modifier
                .padding(horizontal = normalized.horizontalMarginDp.dp)
                .nestedScroll(nestedScrollInteropConnection),
            shape = shape,
            style = customGlassStyle ?: NgDrawerDefaults.style(normalized),
            surfaceDecoration = imageSource?.let { source ->
                {
                    NgDrawerBackdrop(
                        imagePath = source.path,
                        cacheKey = source.cacheKey,
                        modifier = Modifier.matchParentSize(),
                        onImageReadyChanged = { imageReady.value = it },
                    )
                }
            },
            contentPadding = contentPadding,
            content = content,
        )
    }
}

/** 固定白卡在自适应抽屉中读取局部卡色，其它页面继续使用原资源色。 */
@Composable
fun ngDrawerContentCardColor(): Color {
    val color = when (LocalNgDrawerContentCardStyle.current) {
        NgDrawerContentCardStyle.LEGACY -> colorResource(R.color.ng_surface_card)
        NgDrawerContentCardStyle.ADAPTIVE -> Color(NgTheme.colors.cardContainer).copy(alpha = 1f)
    }
    return color.copy(alpha = color.alpha * LocalNgDrawerContentCardAlpha.current.coerceIn(0f, 1f))
}

/**
 * 全局 NG 侧边抽屉承载面。
 *
 * 颜色、透明度与主色浓度复用底部抽屉设置，并作为侧栏唯一背景层；几何由侧边
 * 业务结构显式提供，不消费全局边距或圆角参数。图片共用同一受控背景层。
 */
@Composable
fun NgSideDrawerSurface(
    modifier: Modifier = Modifier,
    appearance: NgDrawerAppearance = NgDrawerDefaults.rememberAppearance(),
    shape: Shape = RectangleShape,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentCardStyle: NgDrawerContentCardStyle = NgDrawerContentCardStyle.LEGACY,
    themeProfile: NgThemeDrawerProfile? = rememberNgDrawerThemeProfile(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val baseSnapshot = NgTheme.snapshot
    val backgroundColor = remember(themeProfile, baseSnapshot.isDark, baseSnapshot.isEInk) {
        themeProfile?.takeIf { !baseSnapshot.isEInk && it.source == "custom_color" }
            ?.forNight(baseSnapshot.isDark)?.backgroundColor
    }
    val imageSource = rememberNgDrawerImageSource(themeProfile)
    val imageReady = remember(imageSource) { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val normalized = remember(appearance) { appearance.normalized() }
    val materialViewport = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    ) {
        NgGlassMaterialViewport(
            width = configuration.screenWidthDp.dp,
            height = (configuration.screenHeightDp * 0.68f).dp,
        )
    }
    val semanticSnapshot = remember(
        baseSnapshot,
        normalized.primaryStrengthPercent,
        contentCardStyle,
        backgroundColor,
    ) {
        when (contentCardStyle) {
            NgDrawerContentCardStyle.LEGACY -> NgDrawerPalette.applySemanticRoles(
                snapshot = baseSnapshot,
                primaryStrengthPercent = normalized.primaryStrengthPercent,
                backgroundColor = backgroundColor,
            )

            NgDrawerContentCardStyle.ADAPTIVE ->
                NgDrawerPalette.applyAdaptiveContentCardRoles(
                    snapshot = baseSnapshot,
                    primaryStrengthPercent = normalized.primaryStrengthPercent,
                    backgroundColor = backgroundColor,
                )
        }
    }
    // Resolve the material from the original snapshot, before installing local content roles.
    val customGlassStyle = backgroundColor?.let { NgDrawerDefaults.style(normalized, it) }
    NgDrawerThemeProvider(
        snapshot = semanticSnapshot,
        contentCardStyle = contentCardStyle,
        hasThemeProfile = backgroundColor != null,
        hasBackgroundImage = imageReady.value,
    ) {
        NgGlassSurface(
            modifier = modifier,
            shape = shape,
            style = customGlassStyle ?: NgDrawerDefaults.style(normalized),
            surfaceDecoration = imageSource?.let { source ->
                {
                    NgDrawerBackdrop(
                        imagePath = source.path,
                        cacheKey = source.cacheKey,
                        modifier = Modifier.matchParentSize(),
                        onImageReadyChanged = { imageReady.value = it },
                    )
                }
            },
            materialViewport = materialViewport,
            contentPadding = contentPadding,
            content = content,
        )
    }
}

private fun NgDrawerAppearance.normalized(): NgDrawerAppearance = copy(
    transparencyPercent = NgDrawerAppearanceConfig.normalizePercent(transparencyPercent),
    primaryStrengthPercent = NgDrawerAppearanceConfig.normalizePercent(primaryStrengthPercent),
    horizontalMarginDp = NgDrawerAppearanceConfig.normalizeHorizontalMarginDp(horizontalMarginDp),
    cornerRadiusDp = NgDrawerAppearanceConfig.normalizeCornerRadiusDp(cornerRadiusDp),
)

/**
 * NG 抽屉顶部的原生拖动抓手。
 *
 * 组件只负责视觉；[NgBottomDrawerSurface] 通过 Compose / View 嵌套滚动互操作，
 * 让宿主 BottomSheetBehavior 只在内部滚动内容到顶后接管下拉手势。
 */
@Composable
fun NgDrawerDragHandle(
    modifier: Modifier = Modifier,
    variant: NgDrawerDragHandleVariant = NgDrawerDragHandleVariant.STANDARD,
) {
    val height = when (variant) {
        NgDrawerDragHandleVariant.STANDARD -> 18.dp
        NgDrawerDragHandleVariant.COMPACT -> 12.dp
    }
    val width = when (variant) {
        NgDrawerDragHandleVariant.STANDARD -> 40.dp
        NgDrawerDragHandleVariant.COMPACT -> 36.dp
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(4.dp)
                .background(
                    Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.52f),
                    CircleShape,
                ),
        )
    }
}
