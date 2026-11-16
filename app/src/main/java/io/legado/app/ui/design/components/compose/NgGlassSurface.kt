package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import android.view.View
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.NgDrawerAppearanceConfig
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgDrawerPalette
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.help.config.ReadFloatingAppearanceConfig
import io.legado.app.help.config.ReadFloatingColorStyle
import io.legado.app.ui.book.read.ReadFloatingPalette
import kotlin.math.max

/**
 * NG 既有玻璃组件的视觉体系兼容入口。
 *
 * 旧调用无需感知透明／液态后端；只需按组件语义补充 [role]。页面若提供统一的
 * 液态 backdrop host，同一树中的既有调用会自动切换。调用方若提供 [backdrop]，
 * 必须传入已经与当前界面对齐的背景内容，不能把整张主题图再次缩放到局部区域。
 */
@Composable
fun NgGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = NgGlassDefaults.shape(),
    style: NgGlassStyle = NgGlassDefaults.style(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    backdrop: (@Composable BoxScope.() -> Unit)? = null,
    materialViewport: NgGlassMaterialViewport? = null,
    role: NgMaterialRole = NgMaterialRole.SOFT_SURFACE,
    liquidCornerRadius: Dp? = null,
    viewBackdropSource: View? = null,
    surfaceDecoration: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    NgVisualSurface(
        modifier = modifier,
        role = role,
        cornerRadius = liquidCornerRadius ?: NgTheme.shapes.dialogDp.dp,
        shape = shape,
        style = style,
        contentPadding = contentPadding,
        viewBackdropSource = viewBackdropSource,
        transparentBackdrop = backdrop,
        surfaceDecoration = surfaceDecoration,
        materialViewport = materialViewport,
        content = content,
    )
}

@Composable
internal fun NgTransparentGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    style: NgGlassStyle,
    contentPadding: PaddingValues,
    backdrop: (@Composable BoxScope.() -> Unit)?,
    materialViewport: NgGlassMaterialViewport?,
    surfaceDecoration: (@Composable BoxScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    val segmented = shape as? NgSegmentedGlassShape
    val contentShape = remember(shape) { segmented?.let(::NgGlassContentShape) }
    Surface(
        modifier = modifier,
        shape = if (segmented != null) RectangleShape else shape,
        color = Color.Transparent,
        contentColor = style.contentColor,
        shadowElevation = style.shadowElevation
    ) {
        Box(
            modifier = if (segmented != null) Modifier else Modifier.clip(shape),
            propagateMinConstraints = true
        ) {
            if (backdrop != null) {
                val backdropModifier = if (style.blurRadius > 0.dp) {
                    Modifier
                        .matchParentSize()
                        .blur(
                            radius = style.blurRadius,
                            edgeTreatment = BlurredEdgeTreatment.Unbounded
                        )
                } else {
                    Modifier.matchParentSize()
                }
                Box(
                    modifier = if (segmented != null) backdropModifier.clip(shape) else backdropModifier,
                    content = backdrop
                )
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .ngGlassLayer(shape, style, materialViewport)
            )

            if (surfaceDecoration != null) {
                Box(Modifier.matchParentSize().clip(shape), content = surfaceDecoration)
            }

            Column(
                modifier = Modifier.then(contentShape?.let { Modifier.clip(it) } ?: Modifier)
                    .padding(contentPadding),
                content = content
            )
        }
    }
}

@Immutable
data class NgGlassStyle(
    val containerTop: Color,
    val containerBottom: Color,
    val accentGlow: Color,
    val borderColor: Color,
    val edgeHighlight: Color,
    val surfaceGloss: Color,
    val depthEdge: Color,
    val contentColor: Color,
    val blurRadius: Dp,
    val shadowElevation: Dp,
    val borderWidth: Dp,
    val highlightWidth: Dp
)

/**
 * 可选的固定材质坐标空间。只改变渐变与光场的采样尺寸；真实裁切、圆角和描边
 * 仍跟随承载面边界。为 null 时保持既有按实际尺寸绘制的行为。
 */
@Immutable
data class NgGlassMaterialViewport(
    val width: Dp,
    val height: Dp,
)

object NgGlassDefaults {

    /** 漫画工具面：不透明浅色渐变与轻阴影，避免正文纹理干扰操作。 */
    @Composable
    fun mangaPanelStyle(): NgGlassStyle {
        val base = style(containerAlpha = 1f)
        if (NgTheme.snapshot.isEInk) return base
        val colors = NgTheme.colors
        return remember(base, colors) {
            base.copy(
                containerTop = Color(colors.surfaceContainerLow),
                containerBottom = Color(colors.drawerContainer),
                accentGlow = Color.Transparent,
                borderColor = Color(colors.outlineVariant).copy(alpha = 0.35f),
                edgeHighlight = Color.White.copy(alpha = 0.65f),
                surfaceGloss = Color.Transparent,
                depthEdge = Color.Transparent,
                blurRadius = 0.dp,
                shadowElevation = 2.dp,
                borderWidth = 0.5.dp,
                highlightWidth = 0.5.dp,
            )
        }
    }

    /**
     * 默认透明度使用主题的 drawerAlpha，对应阅读菜单的轻液态玻璃层级。
     * 密集内容以后可以显式传入 dialogAlpha，但不在组件里绑定具体业务结构。
     */
    @Composable
    fun style(
        containerAlpha: Float = NgTheme.effects.drawerAlpha
    ): NgGlassStyle {
        val snapshot = NgTheme.snapshot
        return remember(snapshot, containerAlpha) {
            resolveNgGlassStyle(snapshot, containerAlpha)
        }
    }

    /**
     * 不接受主题种子染色的中性玻璃承载面。
     *
     * 用于书架管理 Dock 这类需要稳定白／深灰材质、但仍保留 NG 玻璃边缘与
     * 模糊层级的容器。主题主色只应出现在容器内的操作与选中反馈中。
     */
    @Composable
    fun neutralStyle(
        containerAlpha: Float = NgTheme.effects.drawerAlpha
    ): NgGlassStyle {
        val snapshot = NgTheme.snapshot
        val base = style(containerAlpha)
        val neutralContainer = colorResource(R.color.ng_surface_panel)
        return remember(snapshot.isEInk, base, neutralContainer, containerAlpha) {
            val resolvedAlpha = if (snapshot.isEInk) {
                1f
            } else {
                containerAlpha.coerceIn(0f, 1f)
            }
            base.copy(
                containerTop = neutralContainer.copy(
                    alpha = (resolvedAlpha + 0.04f).coerceAtMost(1f)
                ),
                containerBottom = neutralContainer.copy(alpha = resolvedAlpha),
                accentGlow = Color.Transparent
            )
        }
    }

    /** 与书籍详情页内容卡一致的奶白玻璃材质。 */
    @Composable
    fun bookDetailStyle(
        containerColor: Color = colorResource(R.color.ng_book_detail_card_surface)
    ): NgGlassStyle {
        val snapshot = NgTheme.snapshot
        val base = style(containerAlpha = 1f)
        val border = colorResource(R.color.ng_book_detail_card_stroke)
        return remember(snapshot.isEInk, base, containerColor, border) {
            base.copy(
                containerTop = containerColor,
                containerBottom = containerColor,
                accentGlow = Color.Transparent,
                borderColor = border,
                edgeHighlight = if (snapshot.isEInk) {
                    Color.Transparent
                } else {
                    base.edgeHighlight.copy(alpha = 0.30f)
                },
                surfaceGloss = if (snapshot.isEInk) {
                    Color.Transparent
                } else {
                    base.surfaceGloss.copy(alpha = 0.08f)
                },
                depthEdge = Color.Transparent,
                shadowElevation = 0.dp,
                borderWidth = 0.6.dp,
                highlightWidth = if (snapshot.isEInk) 0.dp else 0.6.dp
            )
        }
    }

    /**
     * 不使用高光、弧面和深度边缘的中性平面承载面。
     *
     * 用于操作轨等需要保留整体悬浮层级、但内部控件必须与承载面处在同一平面的场景。
     */
    @Composable
    fun flatNeutralStyle(
        containerAlpha: Float = NgTheme.effects.drawerAlpha
    ): NgGlassStyle {
        val snapshot = NgTheme.snapshot
        val base = neutralStyle(containerAlpha)
        val neutralContainer = colorResource(R.color.ng_surface_panel)
        return remember(snapshot.isEInk, base, neutralContainer, containerAlpha) {
            val resolvedAlpha = if (snapshot.isEInk) {
                1f
            } else {
                containerAlpha.coerceIn(0f, 1f)
            }
            base.copy(
                containerTop = neutralContainer.copy(alpha = resolvedAlpha),
                containerBottom = neutralContainer.copy(alpha = resolvedAlpha),
                accentGlow = Color.Transparent,
                edgeHighlight = Color.Transparent,
                surfaceGloss = Color.Transparent,
                depthEdge = Color.Transparent,
                highlightWidth = 0.dp
            )
        }
    }

    /**
     * 阅读主菜单这类悬浮在高对比正文上的独立玻璃卡。
     *
     * 保留主题色雾化和液态边缘，直接透出承载面后方的实时内容。阅读菜单不提供截图
     * backdrop，也不对静态副本做模糊。
     */
    @Composable
    fun floatingStyle(
        transparencyPercent: Int? = null,
        primaryStrengthPercent: Int? = null,
        colorStyle: ReadFloatingColorStyle = ReadFloatingColorStyle.VIBRANT,
    ): NgGlassStyle {
        val snapshot = NgTheme.snapshot
        return remember(snapshot, transparencyPercent, primaryStrengthPercent, colorStyle) {
            resolveNgFloatingGlassStyle(
                snapshot = snapshot,
                transparencyPercent = transparencyPercent,
                primaryStrengthPercent = primaryStrengthPercent,
                colorStyle = colorStyle,
            )
        }
    }

    /** 全局底部抽屉专用材质，不复用阅读浮窗的轻薄染色曲线。 */
    @Composable
    fun drawerStyle(
        transparencyPercent: Int,
        primaryStrengthPercent: Int,
        backgroundColor: Int? = null,
    ): NgGlassStyle {
        val snapshot = NgTheme.snapshot
        return remember(snapshot, transparencyPercent, primaryStrengthPercent, backgroundColor) {
            resolveNgDrawerGlassStyle(
                snapshot = snapshot,
                transparencyPercent = transparencyPercent,
                primaryStrengthPercent = primaryStrengthPercent,
                backgroundColor = backgroundColor,
            )
        }
    }

    @Composable
    fun shape(): Shape = RoundedCornerShape(NgTheme.shapes.dialogDp.dp)
}

internal fun resolveNgGlassStyle(
    snapshot: NgThemeSnapshot,
    requestedContainerAlpha: Float = snapshot.effects.drawerAlpha
): NgGlassStyle {
    val colors = snapshot.colors
    val containerAlpha = if (snapshot.isEInk) {
        1f
    } else {
        requestedContainerAlpha.coerceIn(0f, 1f)
    }
    val containerTop = NgColorMath.blend(
        colors.drawerContainer,
        colors.surface,
        0.18f
    )
    val containerBottom = NgColorMath.blend(
        colors.drawerContainer,
        colors.surfaceTint,
        if (snapshot.isDark) 0.08f else 0.05f
    )
    val highlightBase = if (snapshot.isDark) {
        NgColorMath.blend(colors.surface, colors.onSurface, 0.38f)
    } else {
        NgColorMath.blend(colors.surface, 0xFFFFFFFF.toInt(), 0.72f)
    }
    val accentAlpha = when {
        snapshot.isEInk -> 0f
        snapshot.isDark -> 0.08f
        else -> 0.10f
    }

    return NgGlassStyle(
        containerTop = Color(
            NgColorMath.withAlpha(
                containerTop,
                (containerAlpha + 0.06f).coerceAtMost(1f)
            )
        ),
        containerBottom = Color(
            NgColorMath.withAlpha(containerBottom, containerAlpha)
        ),
        accentGlow = Color(
            NgColorMath.withAlpha(colors.surfaceTint, accentAlpha)
        ),
        borderColor = Color(
            NgColorMath.withAlpha(
                colors.outlineVariant,
                when {
                    snapshot.isEInk -> 1f
                    snapshot.isDark -> 0.26f
                    else -> 0.32f
                }
            )
        ),
        edgeHighlight = Color(
            NgColorMath.withAlpha(
                highlightBase,
                when {
                    snapshot.isEInk -> 0f
                    snapshot.isDark -> 0.28f
                    else -> 0.44f
                }
            )
        ),
        surfaceGloss = Color(
            NgColorMath.withAlpha(
                highlightBase,
                when {
                    snapshot.isEInk -> 0f
                    snapshot.isDark -> 0.10f
                    else -> 0.18f
                }
            )
        ),
        depthEdge = Color(
            NgColorMath.withAlpha(
                if (snapshot.isDark) 0xFF000000.toInt() else colors.onSurface,
                when {
                    snapshot.isEInk -> 0f
                    snapshot.isDark -> 0.18f
                    else -> 0.08f
                }
            )
        ),
        contentColor = Color(colors.onSurface),
        blurRadius = if (snapshot.effects.blurEnabled) {
            snapshot.effects.blurRadiusDp.dp
        } else {
            0.dp
        },
        shadowElevation = if (snapshot.isEInk) 0.dp else 2.dp,
        borderWidth = if (snapshot.isEInk) 1.dp else 0.6.dp,
        highlightWidth = if (snapshot.isEInk) 0.dp else 1.dp
    )
}

internal fun resolveNgFloatingGlassStyle(
    snapshot: NgThemeSnapshot,
    transparencyPercent: Int? = null,
    primaryStrengthPercent: Int? = null,
    colorStyle: ReadFloatingColorStyle = ReadFloatingColorStyle.VIBRANT,
): NgGlassStyle {
    if (snapshot.isEInk) {
        return resolveNgGlassStyle(snapshot, requestedContainerAlpha = 1f)
    }
    val colors = snapshot.colors
    val base = resolveNgGlassStyle(
        snapshot = snapshot,
        requestedContainerAlpha = if (snapshot.isDark) 0.84f else 0.86f
    )
    val normalizedPrimaryStrength = primaryStrengthPercent?.let(
        ReadFloatingAppearanceConfig::normalizePercent
    )
    val surfaceColors = ReadFloatingPalette.resolveSurfaceColors(
        snapshot = snapshot,
        primaryStrengthPercent = normalizedPrimaryStrength,
    )
    val semanticColors = ReadFloatingPalette.resolveSemanticColors(
        snapshot = snapshot,
        primaryStrengthPercent = normalizedPrimaryStrength,
        colorStyle = colorStyle,
    )
    val containerTop = surfaceColors.top
    val containerBottom = surfaceColors.bottom
    val highlightBase = if (snapshot.isDark) {
        NgColorMath.blend(colors.surface, colors.onSurface, 0.46f)
    } else {
        0xFFFFFFFF.toInt()
    }
    val defaultTopAlpha = if (snapshot.isDark) 0.84f else 0.80f
    val defaultBottomAlpha = if (snapshot.isDark) 0.80f else 0.76f
    val targetTopAlpha = transparencyPercent?.let {
        ReadFloatingAppearanceConfig.floatingSurfaceAlpha(it, defaultTopAlpha)
    } ?: defaultTopAlpha
    val targetBottomAlpha = transparencyPercent?.let {
        ReadFloatingAppearanceConfig.floatingSurfaceAlpha(it, defaultBottomAlpha)
    } ?: defaultBottomAlpha
    val defaultAccentAlpha = if (snapshot.isDark) 0.12f else 0.10f
    val accentColor = if (normalizedPrimaryStrength == null) colors.surfaceTint else containerBottom
    return base.copy(
        containerTop = Color(
            NgColorMath.withAlpha(
                containerTop,
                targetTopAlpha
            )
        ),
        containerBottom = Color(
            NgColorMath.withAlpha(
                containerBottom,
                targetBottomAlpha
            )
        ),
        accentGlow = Color(
            NgColorMath.withAlpha(
                accentColor,
                defaultAccentAlpha
            )
        ),
        borderColor = Color(
            NgColorMath.withAlpha(
                highlightBase,
                if (snapshot.isDark) 0.42f else 0.80f
            )
        ),
        edgeHighlight = Color(
            NgColorMath.withAlpha(
                highlightBase,
                if (snapshot.isDark) 0.62f else 0.98f
            )
        ),
        surfaceGloss = Color(
            NgColorMath.withAlpha(
                highlightBase,
                if (snapshot.isDark) 0.16f else 0.14f
            )
        ),
        depthEdge = Color(
            NgColorMath.withAlpha(
                semanticColors.outline,
                if (snapshot.isDark) 0.20f else 0.10f
            )
        ),
        contentColor = Color(semanticColors.content),
        blurRadius = 0.dp,
        shadowElevation = 0.dp,
        borderWidth = 0.6.dp,
        highlightWidth = 0.8.dp
    )
}

internal fun resolveNgDrawerGlassStyle(
    snapshot: NgThemeSnapshot,
    transparencyPercent: Int,
    primaryStrengthPercent: Int,
    backgroundColor: Int? = null,
): NgGlassStyle {
    if (snapshot.isEInk) {
        return resolveNgGlassStyle(snapshot, requestedContainerAlpha = 1f)
    }
    val colors = snapshot.colors
    val base = resolveNgGlassStyle(
        snapshot = snapshot,
        requestedContainerAlpha = if (snapshot.isDark) 0.84f else 0.86f,
    )
    val normalizedTransparency = NgDrawerAppearanceConfig.normalizePercent(
        transparencyPercent
    )
    val normalizedStrength = NgDrawerAppearanceConfig.normalizePercent(
        primaryStrengthPercent
    )
    val surfaceColors = NgDrawerPalette.resolveSurfaceColors(
        snapshot = snapshot,
        primaryStrengthPercent = normalizedStrength,
        backgroundColor = backgroundColor,
    )
    val semanticColors = NgDrawerPalette.resolveSemanticColors(
        snapshot = snapshot,
        primaryStrengthPercent = normalizedStrength,
        backgroundColor = backgroundColor,
    )
    val highlightBase = if (snapshot.isDark) {
        NgColorMath.blend(
            if (backgroundColor == null) colors.surface else surfaceColors.top,
            if (backgroundColor == null) colors.onSurface else semanticColors.content,
            0.46f,
        )
    } else {
        0xFFFFFFFF.toInt()
    }
    val topAlpha = NgDrawerAppearanceConfig.surfaceAlpha(
        transparencyPercent = normalizedTransparency,
        defaultAlpha = if (snapshot.isDark) 0.84f else 0.80f,
    )
    val bottomAlpha = NgDrawerAppearanceConfig.surfaceAlpha(
        transparencyPercent = normalizedTransparency,
        defaultAlpha = if (snapshot.isDark) 0.80f else 0.76f,
    )
    val strength = if (backgroundColor != null) 0f
        else NgDrawerAppearanceConfig.strengthFraction(normalizedStrength).toFloat()
    val accentAlpha = (if (snapshot.isDark) 0.10f else 0.08f) * strength
    return base.copy(
        containerTop = Color(
            NgColorMath.withAlpha(surfaceColors.top, topAlpha)
        ),
        containerBottom = Color(
            NgColorMath.withAlpha(surfaceColors.bottom, bottomAlpha)
        ),
        accentGlow = Color(
            NgColorMath.withAlpha(surfaceColors.bottom, accentAlpha)
        ),
        borderColor = Color(
            NgColorMath.withAlpha(
                semanticColors.outline,
                if (snapshot.isDark) 0.34f else 0.26f,
            )
        ),
        edgeHighlight = Color(
            NgColorMath.withAlpha(
                highlightBase,
                if (snapshot.isDark) 0.56f else 0.86f,
            )
        ),
        surfaceGloss = Color(
            NgColorMath.withAlpha(
                highlightBase,
                if (snapshot.isDark) 0.14f else 0.12f,
            )
        ),
        depthEdge = Color(
            NgColorMath.withAlpha(
                semanticColors.outline,
                if (snapshot.isDark) 0.18f else 0.08f,
            )
        ),
        contentColor = Color(semanticColors.content),
        blurRadius = 0.dp,
        shadowElevation = 0.dp,
        borderWidth = 0.6.dp,
        highlightWidth = 0.8.dp,
    )
}

internal fun Modifier.ngGlassLayer(
    shape: Shape,
    style: NgGlassStyle,
    materialViewport: NgGlassMaterialViewport?,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val geometry = (shape as? NgSegmentedGlassShape)?.createGlassGeometry(size, layoutDirection, this)
    val outlinePath = when (outline) {
        is Outline.Rectangle -> null
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
    }
    val materialWidth = materialViewport
        ?.width
        ?.toPx()
        ?.coerceAtLeast(1f)
        ?: size.width
    val materialHeight = materialViewport
        ?.height
        ?.toPx()
        ?.coerceAtLeast(1f)
        ?: size.height
    val diagonalEnd = Offset(materialWidth, materialHeight)
    val containerBrush = Brush.linearGradient(
        colors = listOf(style.containerTop, style.containerBottom),
        start = Offset.Zero,
        end = diagonalEnd
    )
    val accentBrush = Brush.radialGradient(
        colors = listOf(style.accentGlow, Color.Transparent),
        center = Offset(materialWidth * 0.10f, materialHeight * 0.04f),
        radius = max(materialWidth, materialHeight) * 0.82f
    )
    val glossBrush = Brush.verticalGradient(
        colors = listOf(
            style.surfaceGloss,
            style.surfaceGloss.copy(alpha = style.surfaceGloss.alpha * 0.35f),
            Color.Transparent
        ),
        startY = 0f,
        endY = (materialHeight * 0.62f).coerceAtLeast(1f)
    )
    val highlightBrush = Brush.linearGradient(
        colors = listOf(
            style.edgeHighlight,
            style.edgeHighlight.copy(alpha = style.edgeHighlight.alpha * 0.35f),
            Color.Transparent
        ),
        start = Offset.Zero,
        end = diagonalEnd
    )
    val depthBrush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color.Transparent,
            style.depthEdge
        ),
        start = Offset.Zero,
        end = diagonalEnd
    )
    val depthBandHeight = (materialHeight * 0.30f)
        .coerceIn(1f, size.height.coerceAtLeast(1f))
    val depthFillBrush = Brush.verticalGradient(
        colors = listOf(Color.Transparent, style.depthEdge),
        startY = (size.height - depthBandHeight).coerceAtLeast(0f),
        endY = size.height.coerceAtLeast(1f),
    )
    val softHighlightBrush = Brush.linearGradient(
        colors = listOf(
            style.edgeHighlight.copy(alpha = style.edgeHighlight.alpha * 0.34f),
            style.edgeHighlight.copy(alpha = style.edgeHighlight.alpha * 0.12f),
            Color.Transparent
        ),
        start = Offset.Zero,
        end = diagonalEnd
    )

    onDrawBehind {
        drawGlassOutline(outline, outlinePath, containerBrush, geometry = geometry)
        if (style.accentGlow.alpha > 0f) {
            drawGlassOutline(outline, outlinePath, accentBrush, geometry = geometry)
        }
        if (style.surfaceGloss.alpha > 0f) {
            drawGlassOutline(outline, outlinePath, glossBrush, geometry = geometry)
        }
        if (style.depthEdge.alpha > 0f) {
            drawGlassOutline(outline, outlinePath, depthFillBrush, geometry = geometry)
        }
        if (style.highlightWidth > 0.dp && style.edgeHighlight.alpha > 0f) {
            drawGlassOutline(
                geometry = geometry,
                outline = outline,
                outlinePath = outlinePath,
                brush = softHighlightBrush,
                drawStyle = Stroke(width = style.highlightWidth.toPx() * 3f)
            )
        }
        if (style.borderWidth > 0.dp) {
            drawGlassOutline(
                geometry = geometry,
                outline = outline,
                outlinePath = outlinePath,
                brush = SolidColor(style.borderColor),
                drawStyle = Stroke(width = style.borderWidth.toPx())
            )
        }
        if (style.highlightWidth > 0.dp && style.edgeHighlight.alpha > 0f) {
            drawGlassOutline(
                geometry = geometry,
                outline = outline,
                outlinePath = outlinePath,
                brush = highlightBrush,
                drawStyle = Stroke(width = style.highlightWidth.toPx())
            )
        }
        if (style.borderWidth > 0.dp && style.depthEdge.alpha > 0f) {
            drawGlassOutline(
                geometry = geometry,
                outline = outline,
                outlinePath = outlinePath,
                brush = depthBrush,
                drawStyle = Stroke(width = style.borderWidth.toPx())
            )
        }
    }
}

private fun DrawScope.drawGlassOutline(
    outline: Outline,
    outlinePath: Path?,
    brush: Brush,
    drawStyle: DrawStyle = Fill,
    geometry: NgGlassGeometry? = null
) {
    if (geometry != null) {
        if (drawStyle is Stroke) {
            geometry.strokePaths.forEach { drawPath(it, brush, style = drawStyle) }
        } else {
            geometry.fillRects.forEach { drawRect(brush, it.topLeft, it.size) }
            geometry.fillPaths.forEach { drawPath(it, brush) }
        }
        return
    }
    when (outline) {
        is Outline.Rectangle -> drawRect(
            brush = brush,
            topLeft = outline.rect.topLeft,
            size = outline.rect.size,
            style = drawStyle
        )

        is Outline.Rounded,
        is Outline.Generic -> drawPath(
            path = requireNotNull(outlinePath),
            brush = brush,
            style = drawStyle
        )
    }
}
