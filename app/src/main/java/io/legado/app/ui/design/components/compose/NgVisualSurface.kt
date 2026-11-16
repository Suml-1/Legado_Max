package io.legado.app.ui.design.components.compose

import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.help.config.NgThemeModeStore
import io.legado.app.help.config.NgThemePresentationMode
import io.legado.app.help.config.NgVisualSystem
import io.legado.app.ui.design.components.view.NgViewLiquidGlassBackdropView
import io.legado.app.ui.design.theme.NgTheme

enum class NgMaterialRole {
    NAVIGATION,
    TOP_NAVIGATION,
    BOTTOM_NAVIGATION,
    OVERLAY,
    INTERACTIVE,
    CONTROL,
    ICON_ACTION,
    ACTION,
    CONTENT,
    SETTINGS,
    SOFT_SURFACE,
    /** 漫画使用不透明浅色工具面，正文不参与面板材质。 */
    MANGA_PANEL,
}

@Immutable
data class NgLiquidGlassSpec(
    val blurRadius: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    val surfaceAlphaScale: Float,
    val saturation: Float,
    val depthEffect: Float,
    val chromaticAberration: Float,
    val highlightAlphaScale: Float,
    val highlightWidth: Dp,
    val accentAlphaScale: Float = 0.30f,
    val surfaceGlossAlphaScale: Float = 0.24f,
    val depthEdgeAlphaScale: Float = 0.42f,
    val interiorRefractionAmount: Dp = 0.dp,
    val convexLightingStrength: Float = 0f,
)

object NgLiquidGlassDefaults {
    fun spec(role: NgMaterialRole): NgLiquidGlassSpec = when (role) {
        NgMaterialRole.NAVIGATION -> NgLiquidGlassSpec(
            blurRadius = 2.dp,
            refractionHeight = 8.dp,
            refractionAmount = 14.dp,
            surfaceAlphaScale = 0.24f,
            saturation = 1.28f,
            depthEffect = 0.34f,
            chromaticAberration = 0.04f,
            highlightAlphaScale = 0.78f,
            highlightWidth = 0.50.dp,
            accentAlphaScale = 0.22f,
            surfaceGlossAlphaScale = 0.32f,
            depthEdgeAlphaScale = 0.18f,
        )
        NgMaterialRole.TOP_NAVIGATION -> NgLiquidGlassSpec(
            blurRadius = 8.dp,
            refractionHeight = 6.dp,
            refractionAmount = 8.dp,
            surfaceAlphaScale = 0.44f,
            saturation = 1.15f,
            depthEffect = 0.18f,
            chromaticAberration = 0.04f,
            highlightAlphaScale = 0.60f,
            highlightWidth = 0.50.dp,
        )
        NgMaterialRole.BOTTOM_NAVIGATION -> NgLiquidGlassSpec(
            blurRadius = 10.dp,
            refractionHeight = 8.dp,
            refractionAmount = 10.dp,
            surfaceAlphaScale = 0.44f,
            saturation = 1.15f,
            depthEffect = 0.25f,
            chromaticAberration = 0.05f,
            highlightAlphaScale = 0.65f,
            highlightWidth = 0.50.dp,
        )
        NgMaterialRole.OVERLAY -> NgLiquidGlassSpec(
            8.dp, 10.dp, 14.dp, 0.48f, 1.18f, 0.20f, 0f, 0.45f, 0.40.dp
        )
        NgMaterialRole.INTERACTIVE -> NgLiquidGlassSpec(
            blurRadius = 1.dp,
            refractionHeight = 7.dp,
            refractionAmount = 16.dp,
            surfaceAlphaScale = 0.20f,
            saturation = 1.32f,
            depthEffect = 0.45f,
            chromaticAberration = 0.06f,
            highlightAlphaScale = 0.88f,
            highlightWidth = 0.55.dp,
            accentAlphaScale = 0.26f,
            surfaceGlossAlphaScale = 0.36f,
            depthEdgeAlphaScale = 0.16f,
        )
        NgMaterialRole.CONTROL -> NgLiquidGlassSpec(
            blurRadius = 10.dp,
            refractionHeight = 4.dp,
            refractionAmount = 5.dp,
            surfaceAlphaScale = 0.78f,
            saturation = 1.08f,
            depthEffect = 0.12f,
            chromaticAberration = 0.01f,
            highlightAlphaScale = 0.58f,
            highlightWidth = 0.45.dp,
            accentAlphaScale = 0.14f,
            surfaceGlossAlphaScale = 0.18f,
            depthEdgeAlphaScale = 0.22f,
        )
        NgMaterialRole.ICON_ACTION -> NgLiquidGlassSpec(
            8.dp, 6.dp, 8.dp, 0.78f, 1.15f, 0.18f, 0.04f, 0.75f, 0.50.dp
        )
        NgMaterialRole.ACTION -> NgLiquidGlassSpec(
            8.dp, 6.dp, 8.dp, 0.68f, 1.15f, 0.18f, 0.04f, 0.68f, 0.50.dp
        )
        NgMaterialRole.CONTENT -> NgLiquidGlassSpec(
            8.dp, 6.dp, 8.dp, 0.68f, 1.15f, 0.18f, 0.04f, 0.60f, 0.50.dp
        )
        NgMaterialRole.SETTINGS -> NgLiquidGlassSpec(
            8.dp, 6.dp, 8.dp, 0.60f, 1.15f, 0.18f, 0.04f, 0.60f, 0.50.dp
        )
        NgMaterialRole.SOFT_SURFACE -> NgLiquidGlassSpec(
            3.dp, 12.dp, 18.dp, 0.30f, 1.22f, 0.35f, 0.12f, 0.55f, 0.45.dp
        )
        NgMaterialRole.MANGA_PANEL -> spec(NgMaterialRole.SOFT_SURFACE).copy(
            surfaceAlphaScale = 1f,
        )
    }

    /**
     * 柔光渐变缺少图片背景的局部纹理；为大面积液态承载面补全表面弱透镜，
     * 让卡片中央也参与折射，而不是只在圆角边缘产生位移。
     */
    fun spec(
        role: NgMaterialRole,
        usesSoftGradient: Boolean,
    ): NgLiquidGlassSpec {
        val base = spec(role)
        if (!usesSoftGradient) return base
        return when (role) {
            NgMaterialRole.BOTTOM_NAVIGATION -> base.copy(
                refractionHeight = 6.dp,
                refractionAmount = 6.dp,
                depthEffect = 0.16f,
                interiorRefractionAmount = 13.dp,
                convexLightingStrength = 0.60f,
            )
            NgMaterialRole.OVERLAY -> base.copy(
                refractionHeight = 6.dp,
                refractionAmount = 6.dp,
                depthEffect = 0.16f,
                interiorRefractionAmount = 14.dp,
                convexLightingStrength = 0.60f,
            )
            NgMaterialRole.CONTENT -> base.copy(
                refractionHeight = 6.dp,
                refractionAmount = 6.dp,
                depthEffect = 0.16f,
                interiorRefractionAmount = 14.dp,
                convexLightingStrength = 0.60f,
            )
            NgMaterialRole.SETTINGS -> NgSoftGradientSettingsMaterial.liquidSpec
            else -> base
        }
    }
}

internal object NgSoftGradientSettingsMaterial {
    const val TRANSPARENT_SURFACE_ALPHA = 0.33f
    const val LIQUID_SURFACE_ALPHA = 0.58f

    val liquidSpec = NgLiquidGlassSpec(
        blurRadius = 3.dp,
        refractionHeight = 6.dp,
        refractionAmount = 6.dp,
        surfaceAlphaScale = 0.50f,
        saturation = 1.22f,
        depthEffect = 0.16f,
        chromaticAberration = 0.06f,
        highlightAlphaScale = 0.70f,
        highlightWidth = 0.55.dp,
        accentAlphaScale = 0.35f,
        surfaceGlossAlphaScale = 0.45f,
        depthEdgeAlphaScale = 0.55f,
        interiorRefractionAmount = 12.dp,
        convexLightingStrength = 0.60f,
    )
}

private val LocalNgLiquidGlassBackdrop = staticCompositionLocalOf<NgLiquidGlassBackdrop?> {
    null
}

private val LocalNgLiquidGlassViewBackdropSource = staticCompositionLocalOf<View?> {
    null
}

@Composable
internal fun currentNgLiquidGlassBackdrop(): NgLiquidGlassBackdrop? =
    LocalNgLiquidGlassBackdrop.current

@Composable
private fun currentNgLiquidGlassViewBackdropSource(): View? {
    val providedSource = LocalNgLiquidGlassViewBackdropSource.current
    if (providedSource != null) return providedSource
    return LocalView.current.rootView.findViewById(
        R.id.ng_liquid_glass_backdrop_source,
    )
}

@Composable
internal fun hasCurrentNgLiquidGlassBackdrop(): Boolean =
    LocalNgLiquidGlassBackdrop.current != null ||
        currentNgLiquidGlassViewBackdropSource() != null

@Composable
fun NgLiquidGlassBackdropProvider(
    backdrop: NgLiquidGlassBackdrop,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalNgLiquidGlassBackdrop provides backdrop,
        content = content,
    )
}

@Composable
fun NgLiquidGlassViewBackdropProvider(
    sourceView: View,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalNgLiquidGlassViewBackdropSource provides sourceView,
        content = content,
    )
}

/**
 * 按语义角色在透明玻璃与液态玻璃之间选择渲染后端。
 *
 * 调用方仍负责页面结构；没有同一 Compose 树中的 [liquidBackdrop] 时会可靠地
 * 回退到现有透明玻璃，而不会伪造或重复绘制背景。
 */
@Composable
fun NgVisualSurface(
    modifier: Modifier = Modifier,
    role: NgMaterialRole,
    cornerRadius: Dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    style: NgGlassStyle = NgGlassDefaults.style(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    liquidBackdrop: NgLiquidGlassBackdrop? = LocalNgLiquidGlassBackdrop.current,
    viewBackdropSource: View? = null,
    transparentBackdrop: (@Composable BoxScope.() -> Unit)? = null,
    surfaceDecoration: (@Composable BoxScope.() -> Unit)? = null,
    materialViewport: NgGlassMaterialViewport? = null,
    visualSystemOverride: NgVisualSystem? = null,
    liquidSpecOverride: NgLiquidGlassSpec? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val supportsBackdropEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val visualSystem = visualSystemOverride ?: NgTheme.visualSystem
    val usesLiquidGlass = visualSystem == NgVisualSystem.LIQUID_GLASS &&
        !NgTheme.snapshot.isEInk && role != NgMaterialRole.MANGA_PANEL
    val usesSoftGradient = NgThemeModeStore.current(context) ==
        NgThemePresentationMode.SOFT_GRADIENT
    val resolvedViewBackdropSource =
        viewBackdropSource ?: currentNgLiquidGlassViewBackdropSource()
    if (
        !usesLiquidGlass ||
        (liquidBackdrop == null && resolvedViewBackdropSource == null) ||
        !supportsBackdropEffect
    ) {
        NgTransparentGlassSurface(
            modifier = modifier,
            shape = shape,
            style = style,
            contentPadding = contentPadding,
            backdrop = transparentBackdrop,
            surfaceDecoration = surfaceDecoration,
            materialViewport = materialViewport,
            content = content,
        )
        return
    }

    val spec = remember(role, liquidSpecOverride, usesSoftGradient) {
        liquidSpecOverride ?: NgLiquidGlassDefaults.spec(role, usesSoftGradient)
    }
    val liquidStyle = remember(style, spec) {
        style.copy(
            containerTop = style.containerTop.scaleAlpha(spec.surfaceAlphaScale),
            containerBottom = style.containerBottom.scaleAlpha(spec.surfaceAlphaScale),
            accentGlow = style.accentGlow.scaleAlpha(spec.accentAlphaScale),
            borderColor = Color.Transparent,
            edgeHighlight = style.edgeHighlight.scaleAlpha(spec.highlightAlphaScale),
            surfaceGloss = style.surfaceGloss.scaleAlpha(spec.surfaceGlossAlphaScale),
            depthEdge = style.depthEdge.scaleAlpha(spec.depthEdgeAlphaScale),
            shadowElevation = 0.dp,
            borderWidth = 0.dp,
            highlightWidth = spec.highlightWidth,
        )
    }
    if (liquidBackdrop != null) {
        NgLiquidGlassSurface(
            modifier = modifier,
            backdrop = liquidBackdrop,
            shape = shape,
            cornerRadius = cornerRadius,
            style = liquidStyle,
            spec = spec,
            contentPadding = contentPadding,
            surfaceDecoration = surfaceDecoration,
            content = content,
        )
    } else {
        NgViewLiquidGlassSurface(
            modifier = modifier,
            sourceView = requireNotNull(resolvedViewBackdropSource),
            visualSystemOverride = visualSystemOverride,
            role = role,
            shape = shape,
            cornerRadius = cornerRadius,
            style = liquidStyle,
            spec = spec,
            contentPadding = contentPadding,
            surfaceDecoration = surfaceDecoration,
            content = content,
        )
    }
}

@Composable
private fun NgLiquidGlassSurface(
    modifier: Modifier,
    backdrop: NgLiquidGlassBackdrop,
    shape: Shape,
    cornerRadius: Dp,
    style: NgGlassStyle,
    spec: NgLiquidGlassSpec,
    contentPadding: PaddingValues,
    surfaceDecoration: (@Composable BoxScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        contentColor = style.contentColor,
        shadowElevation = style.shadowElevation,
    ) {
        Box(
            modifier = Modifier.ngDrawLiquidGlassBackdrop(
                backdrop = backdrop,
                shape = shape,
                cornerRadius = cornerRadius,
                blurRadius = spec.blurRadius,
                refractionHeight = spec.refractionHeight,
                refractionAmount = spec.refractionAmount,
                interiorRefractionAmount = spec.interiorRefractionAmount,
                convexLightingStrength = spec.convexLightingStrength,
                saturation = spec.saturation,
                depthEffect = spec.depthEffect,
                chromaticAberration = spec.chromaticAberration,
            ),
            propagateMinConstraints = true,
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .ngGlassLayer(shape, style, materialViewport = null),
            )
            if (surfaceDecoration != null) {
                Box(Modifier.matchParentSize().clip(shape), content = surfaceDecoration)
            }
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content,
            )
        }
    }
}

@Composable
private fun NgViewLiquidGlassSurface(
    modifier: Modifier,
    sourceView: View,
    visualSystemOverride: NgVisualSystem?,
    role: NgMaterialRole,
    shape: Shape,
    cornerRadius: Dp,
    style: NgGlassStyle,
    spec: NgLiquidGlassSpec,
    contentPadding: PaddingValues,
    surfaceDecoration: (@Composable BoxScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val segmented = shape as? NgSegmentedGlassShape
    val contentShape = remember(shape) { segmented?.let(::NgGlassContentShape) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    Surface(
        modifier = modifier,
        shape = if (segmented != null) RectangleShape else shape,
        color = Color.Transparent,
        contentColor = style.contentColor,
        shadowElevation = style.shadowElevation,
    ) {
        Box(propagateMinConstraints = true) {
            AndroidView(
                factory = { context -> NgViewLiquidGlassBackdropView(context) },
                modifier = Modifier.matchParentSize()
                    .then(
                        if (segmented != null) {
                            // Keep the expensive concave clip inside a cached hardware layer.
                            // Backdrop invalidations still refresh it; foreground interaction
                            // and the parent's visibility animation only composite the result.
                            Modifier.graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }.clip(shape)
                        } else {
                            Modifier
                        }
                    ),
                update = { view ->
                    view.renderer.sourceView = sourceView
                    view.renderer.visualSystemOverride = visualSystemOverride
                    view.renderer.role = role
                    view.renderer.specOverride = spec
                    view.renderer.cornerRadiusPx = with(density) { cornerRadius.toPx() }
                    view.renderer.drawsSurface = false
                    view.invalidate()
                },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .ngGlassLayer(shape, style, materialViewport = null),
            )
            if (surfaceDecoration != null) {
                Box(Modifier.matchParentSize().clip(shape), content = surfaceDecoration)
            }
            Column(
                modifier = Modifier.then(contentShape?.let { Modifier.clip(it) } ?: Modifier)
                    .padding(contentPadding),
                content = content,
            )
        }
    }
}

private fun Color.scaleAlpha(scale: Float): Color = copy(
    alpha = (alpha * scale).coerceIn(0f, 1f)
)
