/*
 * Portions of the rounded-rectangle refraction shader and backdrop-layer
 * architecture are adapted from Kyant0/AndroidLiquidGlass (Backdrop).
 *
 * Copyright 2025 Kyant
 * Licensed under the Apache License, Version 2.0.
 * This file has been modified for Legado's AndroidX Compose 1.7 runtime and
 * NG visual-system semantics.
 */
package io.legado.app.ui.design.components.compose

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import kotlin.math.ceil
import kotlin.math.roundToInt

@Stable
class NgLiquidGlassBackdrop internal constructor() {
    internal var graphicsLayer by mutableStateOf<GraphicsLayer?>(null, neverEqualPolicy())
    internal var coordinates by mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy())
}

@Composable
fun rememberNgLiquidGlassBackdrop(): NgLiquidGlassBackdrop = remember {
    NgLiquidGlassBackdrop()
}

fun Modifier.ngRecordLiquidGlassBackdrop(
    backdrop: NgLiquidGlassBackdrop,
): Modifier = this then NgBackdropRecorderElement(backdrop)

internal fun Modifier.ngDrawLiquidGlassBackdrop(
    backdrop: NgLiquidGlassBackdrop,
    shape: Shape,
    cornerRadius: Dp,
    blurRadius: Dp,
    refractionHeight: Dp,
    refractionAmount: Dp,
    interiorRefractionAmount: Dp,
    convexLightingStrength: Float,
    saturation: Float,
    depthEffect: Float,
    chromaticAberration: Float,
): Modifier = this
    .clip(shape)
    .then(
        NgLiquidBackdropElement(
            backdrop = backdrop,
            shape = shape,
            cornerRadius = cornerRadius,
            blurRadius = blurRadius,
            refractionHeight = refractionHeight,
            refractionAmount = refractionAmount,
            interiorRefractionAmount = interiorRefractionAmount,
            convexLightingStrength = convexLightingStrength,
            saturation = saturation,
            depthEffect = depthEffect,
            chromaticAberration = chromaticAberration,
        )
    )

private class NgBackdropRecorderElement(
    private val backdrop: NgLiquidGlassBackdrop,
) : ModifierNodeElement<NgBackdropRecorderNode>() {

    override fun create() = NgBackdropRecorderNode(backdrop)

    override fun update(node: NgBackdropRecorderNode) {
        node.updateBackdrop(backdrop)
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "ngRecordLiquidGlassBackdrop"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean =
        other is NgBackdropRecorderElement && other.backdrop === backdrop

    override fun hashCode(): Int = System.identityHashCode(backdrop)
}

private class NgBackdropRecorderNode(
    private var backdrop: NgLiquidGlassBackdrop,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    private var graphicsLayer: GraphicsLayer? = null

    fun updateBackdrop(value: NgLiquidGlassBackdrop) {
        if (backdrop === value) return
        if (backdrop.graphicsLayer === graphicsLayer) backdrop.graphicsLayer = null
        backdrop.coordinates = null
        backdrop = value
        backdrop.graphicsLayer = graphicsLayer
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val layer = graphicsLayer ?: return
        val outerScope = this
        recordLayer(layer, size.toIntSize()) {
            outerScope.drawContent()
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) backdrop.coordinates = coordinates
    }

    override fun onAttach() {
        graphicsLayer = requireGraphicsContext().createGraphicsLayer().also {
            backdrop.graphicsLayer = it
        }
    }

    override fun onDetach() {
        val layer = graphicsLayer
        if (backdrop.graphicsLayer === layer) backdrop.graphicsLayer = null
        backdrop.coordinates = null
        layer?.let(requireGraphicsContext()::releaseGraphicsLayer)
        graphicsLayer = null
    }

    private fun DrawScope.recordLayer(
        layer: GraphicsLayer,
        targetSize: IntSize,
        block: DrawScope.() -> Unit,
    ) {
        val density = requireDensity()
        layer.record(density, layoutDirection, targetSize) {
            block()
        }
    }
}

private data class NgLiquidBackdropElement(
    val backdrop: NgLiquidGlassBackdrop,
    val shape: Shape,
    val cornerRadius: Dp,
    val blurRadius: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    val interiorRefractionAmount: Dp,
    val convexLightingStrength: Float,
    val saturation: Float,
    val depthEffect: Float,
    val chromaticAberration: Float,
) : ModifierNodeElement<NgLiquidBackdropNode>() {

    override fun create() = NgLiquidBackdropNode(
        backdrop,
        shape,
        cornerRadius,
        blurRadius,
        refractionHeight,
        refractionAmount,
        interiorRefractionAmount,
        convexLightingStrength,
        saturation,
        depthEffect,
        chromaticAberration,
    )

    override fun update(node: NgLiquidBackdropNode) {
        node.backdrop = backdrop
        node.shape = shape
        node.cornerRadius = cornerRadius
        node.blurRadius = blurRadius
        node.refractionHeight = refractionHeight
        node.refractionAmount = refractionAmount
        node.interiorRefractionAmount = interiorRefractionAmount
        node.convexLightingStrength = convexLightingStrength
        node.saturation = saturation
        node.depthEffect = depthEffect
        node.chromaticAberration = chromaticAberration
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "ngDrawLiquidGlassBackdrop"
        properties["backdrop"] = backdrop
        properties["shape"] = shape
        properties["cornerRadius"] = cornerRadius
        properties["blurRadius"] = blurRadius
        properties["refractionHeight"] = refractionHeight
        properties["refractionAmount"] = refractionAmount
        properties["interiorRefractionAmount"] = interiorRefractionAmount
        properties["convexLightingStrength"] = convexLightingStrength
        properties["saturation"] = saturation
        properties["depthEffect"] = depthEffect
        properties["chromaticAberration"] = chromaticAberration
    }
}

private class NgLiquidBackdropNode(
    var backdrop: NgLiquidGlassBackdrop,
    var shape: Shape,
    var cornerRadius: Dp,
    var blurRadius: Dp,
    var refractionHeight: Dp,
    var refractionAmount: Dp,
    var interiorRefractionAmount: Dp,
    var convexLightingStrength: Float,
    var saturation: Float,
    var depthEffect: Float,
    var chromaticAberration: Float,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    private var coordinates: LayoutCoordinates? = null
    private var graphicsLayer: GraphicsLayer? = null
    private var refractionShader: RuntimeShader? = null

    override fun ContentDrawScope.draw() {
        val destinationLayer = graphicsLayer
        val sourceLayer = backdrop.graphicsLayer
        val sourceCoordinates = backdrop.coordinates
        val destinationCoordinates = coordinates
        if (
            destinationLayer != null &&
            sourceLayer != null &&
            sourceCoordinates != null &&
            destinationCoordinates != null
        ) {
            drawBackdropLayer(
                destinationLayer = destinationLayer,
                sourceLayer = sourceLayer,
                sourceCoordinates = sourceCoordinates,
                destinationCoordinates = destinationCoordinates,
            )
        }
        drawContent()
    }

    private fun DrawScope.drawBackdropLayer(
        destinationLayer: GraphicsLayer,
        sourceLayer: GraphicsLayer,
        sourceCoordinates: LayoutCoordinates,
        destinationCoordinates: LayoutCoordinates,
    ) {
        val blurPx = blurRadius.toPx().coerceAtLeast(0f)
        val refractionHeightPx = refractionHeight.toPx().coerceAtLeast(0f)
        val refractionAmountPx = refractionAmount.toPx().coerceAtLeast(0f)
        val interiorRefractionAmountPx = interiorRefractionAmount.toPx().coerceAtLeast(0f)
        // 与上游 lens 的 padding 语义一致：折射位移不能扩大采样区域，
        // 否则会把承载面外的旧列表内容远距离拉进来，形成视觉残留。
        val padding = (blurPx - refractionHeightPx).coerceAtLeast(0f)
        val paddedWidth = ceil(size.width + padding * 2f).toInt().coerceAtLeast(1)
        val paddedHeight = ceil(size.height + padding * 2f).toInt().coerceAtLeast(1)
        val offset = runCatching {
            sourceCoordinates.localPositionOf(destinationCoordinates, Offset.Zero)
        }.getOrElse {
            destinationCoordinates.positionInWindow() - sourceCoordinates.positionInWindow()
        }

        val density = requireDensity()
        destinationLayer.record(
            density,
            layoutDirection,
            IntSize(paddedWidth, paddedHeight),
        ) {
            withTransform({
                translate(padding - offset.x, padding - offset.y)
            }) {
                drawLayer(sourceLayer)
            }
        }
        destinationLayer.topLeft = IntOffset(-padding.roundToInt(), -padding.roundToInt())
        updateRenderEffect(
            layer = destinationLayer,
            contentSize = size,
            padding = padding,
            cornerRadiusPx = cornerRadius.toPx(),
            blurRadiusPx = blurPx,
            refractionHeightPx = refractionHeightPx,
            refractionAmountPx = refractionAmountPx,
            interiorRefractionAmountPx = interiorRefractionAmountPx,
            convexLightingStrength = convexLightingStrength,
            saturation = saturation,
            depthEffect = depthEffect,
            chromaticAberration = chromaticAberration,
        )
        drawLayer(destinationLayer)
    }

    private fun updateRenderEffect(
        layer: GraphicsLayer,
        contentSize: androidx.compose.ui.geometry.Size,
        padding: Float,
        cornerRadiusPx: Float,
        blurRadiusPx: Float,
        refractionHeightPx: Float,
        refractionAmountPx: Float,
        interiorRefractionAmountPx: Float,
        convexLightingStrength: Float,
        saturation: Float,
        depthEffect: Float,
        chromaticAberration: Float,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            layer.renderEffect = null
            return
        }
        var effect: AndroidRenderEffect? = if (saturation != 1f) {
            val colorMatrix = ColorMatrix().apply {
                setSaturation(saturation.coerceAtLeast(0f))
            }
            AndroidRenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(colorMatrix)
            )
        } else null
        if (blurRadiusPx > 0f) {
            effect = effect?.let { input ->
                AndroidRenderEffect.createBlurEffect(
                    blurRadiusPx,
                    blurRadiusPx,
                    input,
                    Shader.TileMode.CLAMP,
                )
            } ?: AndroidRenderEffect.createBlurEffect(
                blurRadiusPx,
                blurRadiusPx,
                Shader.TileMode.CLAMP,
            )
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            refractionHeightPx > 0f &&
            refractionAmountPx > 0f
        ) {
            val shader = refractionShader ?: RuntimeShader(ROUNDED_RECT_REFRACTION_SHADER).also {
                refractionShader = it
            }
            shader.setFloatUniform("size", contentSize.width, contentSize.height)
            shader.setFloatUniform("offset", -padding, -padding)
            shader.setFloatUniform(
                "cornerRadii",
                cornerRadiusPx,
                cornerRadiusPx,
                cornerRadiusPx,
                cornerRadiusPx,
            )
            shader.setFloatUniform("refractionHeight", refractionHeightPx)
            shader.setFloatUniform("refractionAmount", -refractionAmountPx)
            shader.setFloatUniform(
                "interiorRefractionAmount",
                -interiorRefractionAmountPx,
            )
            shader.setFloatUniform(
                "convexLightingStrength",
                convexLightingStrength.coerceIn(0f, 1f),
            )
            shader.setFloatUniform("depthEffect", depthEffect.coerceIn(0f, 1f))
            shader.setFloatUniform(
                "chromaticAberration",
                chromaticAberration.coerceIn(0f, 1f),
            )
            val lens = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
            effect = effect?.let { AndroidRenderEffect.createChainEffect(lens, it) } ?: lens
        }
        layer.renderEffect = effect?.asComposeRenderEffect()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) this.coordinates = coordinates
    }

    override fun onAttach() {
        graphicsLayer = requireGraphicsContext().createGraphicsLayer()
    }

    override fun onDetach() {
        graphicsLayer?.let(requireGraphicsContext()::releaseGraphicsLayer)
        graphicsLayer = null
        coordinates = null
        refractionShader = null
    }
}

internal const val ROUNDED_RECT_REFRACTION_SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float interiorRefractionAmount;
uniform float convexLightingStrength;
uniform float depthEffect;
uniform float chromaticAberration;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        return radii.z;
    }
    if (coord.y <= 0.0) return radii.x;
    return radii.w;
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    }
    float gradX = step(cornerCoord.y, cornerCoord.x);
    return sign(coord) * float2(gradX, 1.0 - gradX);
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 applyConvexThicknessLighting(
    half4 color,
    float2 normalizedCoord,
    float strength
) {
    if (strength <= 0.0) return color;
    // One cylindrical convex pane: the optical thickness has a single broad
    // crown in the center and continuously falls to zero at the left/right
    // sides. Lighting is derived from that same arc instead of painting two
    // independent edge lobes.
    float u = clamp(normalizedCoord.x, -1.0, 1.0);
    float thickness = sqrt(max(0.0, 1.0 - u * u));
    float slope = u / max(thickness, 0.22);
    float3 normal = normalize(float3(-0.55 * slope, 0.0, 1.0));
    float3 lightDirection = normalize(float3(-0.35, -0.15, 1.0));
    float3 viewDirection = float3(0.0, 0.0, 1.0);
    float3 halfDirection = normalize(lightDirection + viewDirection);
    float specular = pow(max(dot(normal, halfDirection), 0.0), 24.0);
    float fresnel = pow(1.0 - normal.z, 3.0);
    float scatter = 1.0 - exp(-1.6 * thickness);
    float highlight = strength * (0.035 * scatter + 0.075 * specular);
    float depth = strength * 0.025 * fresnel;
    color.rgb = color.rgb + (half3(1.0) - color.rgb) * highlight;
    color.rgb *= 1.0 - depth;
    return color;
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float2 normalizedCoord = centeredCoord / max(halfSize, float2(1.0));
    float lensPosition = clamp(normalizedCoord.x, -1.0, 1.0);
    // A single cylindrical lens. The mapping is fixed at the center and both
    // sides, with one smooth refraction lobe per half. The negative uniform
    // supplied by the host samples toward the center and expands its content.
    float lensWeight = lensPosition * (1.0 - lensPosition * lensPosition);
    float2 interiorOffset = float2(
        lensWeight * interiorRefractionAmount,
        0.0
    );
    float radius = radiusAt(centeredCoord, cornerRadii);
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return applyConvexThicknessLighting(
            content.eval(coord + interiorOffset),
            normalizedCoord,
            convexLightingStrength
        );
    }
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(
        gradSdRoundedRect(centeredCoord, halfSize, gradRadius) +
        depthEffect * normalize(centeredCoord)
    );
    float2 refractedCoord = coord + interiorOffset + d * grad;
    float dispersionIntensity = chromaticAberration *
        ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return applyConvexThicknessLighting(
        color,
        normalizedCoord,
        convexLightingStrength
    );
}
"""
