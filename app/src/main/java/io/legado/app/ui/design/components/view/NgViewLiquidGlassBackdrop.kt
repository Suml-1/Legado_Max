/*
 * The rounded-rectangle lens shader used by this renderer is adapted from
 * Kyant0/AndroidLiquidGlass (Backdrop), licensed under Apache License 2.0.
 */
package io.legado.app.ui.design.components.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NgVisualSystem
import io.legado.app.help.config.NgVisualSystemStore
import io.legado.app.ui.design.components.compose.NgLiquidGlassDefaults
import io.legado.app.ui.design.components.compose.NgLiquidGlassSpec
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.ROUNDED_RECT_REFRACTION_SHADER
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 为 View/独立 ComposeView 边界提供实时背景采样。
 *
 * [sourceView] 必须是承载面后方的真实内容 View，不能包含 [owner]，避免递归录制。
 * 若页面提供统一背景 source，渲染器会先合成背景，再录制该实时内容 View。
 */
internal class NgViewLiquidGlassRenderer(
    private val owner: View,
) {

    var sourceView: View? = null
        set(value) {
            if (field === value) return
            detachPreDrawListener()
            field = value
            // A recording may carry RenderNode properties set by the sampled View (e.g.
            // overscroll stretch). Re-recording its display list does not reset them.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) renderNode?.discardDisplayList()
            renderNode = null
            recordedBackdrop = null
            appliedEffect = null
            attachPreDrawListener()
            owner.invalidate()
        }

    var role: NgMaterialRole = NgMaterialRole.NAVIGATION
    var specOverride: NgLiquidGlassSpec? = null
    var visualSystemOverride: NgVisualSystem? = null
    var cornerRadiusPx: Float = 0f
    @ColorInt var surfaceColor: Int = Color.TRANSPARENT
    var surfaceAlpha: Float = 0f
    var drawsSurface: Boolean = false

    private var renderNode: RenderNode? = null
    private var refractionShader: RuntimeShader? = null
    private var recordedBackdrop: BackdropRecording? = null
    private var appliedEffect: EffectParameters? = null
    private var drawnSurface: SurfaceParameters? = null
    private var observedTree: ViewTreeObserver? = null
    private val ownerLocation = IntArray(2)
    private val sourceLocation = IntArray(2)
    private val rootLocation = IntArray(2)
    private val oldDrawableBounds = Rect()
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        invalidateChangedBackdrop()
        true
    }

    private data class SourceGeometry(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val scrollX: Int,
        val scrollY: Int,
    )

    private data class BackdropRecording(
        val source: NgBackdropSourceLayout,
        val sourceRevision: Long,
        val sourceGeometry: SourceGeometry,
        val pageSource: NgBackdropSourceLayout?,
        val pageRevision: Long?,
        val pageGeometry: SourceGeometry?,
        val root: View,
        val rootGeometry: SourceGeometry,
        val backgroundRevision: Long,
        val width: Int,
        val height: Int,
        val padding: Int,
    )

    private data class EffectParameters(
        val width: Int,
        val height: Int,
        val density: Float,
        val cornerRadius: Float,
        val spec: NgLiquidGlassSpec,
    )

    private data class SurfaceParameters(val color: Int, val alpha: Float, val draws: Boolean)

    private fun effectParameters(spec: NgLiquidGlassSpec) = EffectParameters(
        owner.width, owner.height, owner.resources.displayMetrics.density, cornerRadiusPx, spec,
    )

    private fun surfaceParameters() = SurfaceParameters(surfaceColor, surfaceAlpha, drawsSurface)

    private fun backdropPadding(spec: NgLiquidGlassSpec, density: Float): Float =
        (spec.blurRadius.value * density - spec.refractionHeight.value * density).coerceAtLeast(0f)

    private fun pageBackdropSource(source: View): View? =
        owner.rootView.findViewById<View>(R.id.ng_liquid_glass_backdrop_source)
            ?.takeIf { it !== source && it.isAttachedToWindow && !it.containsDescendant(owner) }

    /** Only explicit background hosts promise revisions for all of their drawing changes. */
    private fun backdropRecording(source: View, padding: Int): BackdropRecording? {
        if (source !is NgBackdropSourceLayout || source.rootView !== owner.rootView) return null
        val pageSource = pageBackdropSource(source)
        if (pageSource != null && pageSource !is NgBackdropSourceLayout) return null
        owner.getLocationInWindow(ownerLocation)
        fun geometry(view: View): SourceGeometry {
            view.getLocationInWindow(sourceLocation)
            return SourceGeometry(
                sourceLocation[0] - ownerLocation[0], sourceLocation[1] - ownerLocation[1],
                view.width, view.height, view.scrollX, view.scrollY,
            )
        }
        val versionedPage = pageSource as? NgBackdropSourceLayout
        return BackdropRecording(
            source, source.contentRevision, geometry(source),
            versionedPage, versionedPage?.contentRevision, versionedPage?.let(::geometry),
            owner.rootView, geometry(owner.rootView), source.windowBackgroundRevision,
            owner.width, owner.height, padding,
        )
    }

    private fun invalidateChangedBackdrop() {
        if (!isEnabled() || !owner.isShown || owner.width <= 0 || owner.height <= 0) return
        val spec = resolvedSpec()
        val padding = ceil(backdropPadding(spec, owner.resources.displayMetrics.density)).toInt()
        val recording = backdropRecording(requireNotNull(sourceView), padding)
        // Arbitrary content Views (e.g. a scrolling page behind a Dock) keep live sampling.
        // A stable background must not invalidate its own root again on every preDraw.
        if (recording == null || recording != recordedBackdrop ||
            appliedEffect != effectParameters(spec) || drawnSurface != surfaceParameters()
        ) {
            owner.invalidate()
        }
    }

    fun onAttachedToWindow() {
        attachPreDrawListener()
    }

    fun onDetachedFromWindow() {
        detachPreDrawListener()
        renderNode = null
        refractionShader = null
        recordedBackdrop = null
        appliedEffect = null
        drawnSurface = null
    }

    fun isEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || AppConfig.isEInkMode) return false
        if ((visualSystemOverride ?: NgVisualSystemStore.current(owner.context)) != NgVisualSystem.LIQUID_GLASS) return false
        val source = sourceView ?: return false
        return source.isAttachedToWindow && !source.containsDescendant(owner)
    }

    fun draw(canvas: Canvas): Boolean {
        if (!isEnabled() || owner.width <= 0 || owner.height <= 0) return false
        if (!canvas.isHardwareAccelerated) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val source = sourceView ?: return false
        drawBackdrop(canvas, source)
        if (drawsSurface) drawSurface(canvas)
        drawnSurface = surfaceParameters()
        return true
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun drawBackdrop(canvas: Canvas, source: View) {
        val density = owner.resources.displayMetrics.density
        val spec = resolvedSpec()
        val blurPx = spec.blurRadius.value * density
        val refractionHeightPx = spec.refractionHeight.value * density
        val refractionAmountPx = spec.refractionAmount.value * density
        val interiorRefractionAmountPx = spec.interiorRefractionAmount.value * density
        // lens 不按折射位移外扩录制范围，避免把 Dock 外的列表内容折进来。
        val padding = backdropPadding(spec, density)
        val paddingInt = ceil(padding).toInt()
        val paddedWidth = (owner.width + paddingInt * 2).coerceAtLeast(1)
        val paddedHeight = (owner.height + paddingInt * 2).coerceAtLeast(1)
        val node = renderNode ?: RenderNode("NgViewLiquidGlass").also { renderNode = it }
        node.setPosition(-paddingInt, -paddingInt, owner.width + paddingInt, owner.height + paddingInt)

        owner.getLocationInWindow(ownerLocation)
        val recording = backdropRecording(source, paddingInt)
        if (recording == null || recording != recordedBackdrop || !node.hasDisplayList()) {
            val recordingCanvas = node.beginRecording(paddedWidth, paddedHeight)
            try {
                drawWindowBackground(recordingCanvas, paddingInt, recording?.source)
                pageBackdropSource(source)?.let { drawSource(recordingCanvas, it, paddingInt) }
                drawSource(recordingCanvas, source, paddingInt)
            } finally {
                node.endRecording()
            }
            // Keep the revision from before recording: a real update during draw must not be lost.
            recordedBackdrop = recording
        }

        val effect = effectParameters(spec)
        if (effect != appliedEffect) {
            node.setRenderEffect(
                createRenderEffect(
                    width = owner.width.toFloat(),
                    height = owner.height.toFloat(),
                    padding = padding,
                    cornerRadius = cornerRadiusPx,
                    blurRadius = blurPx,
                    refractionHeight = refractionHeightPx,
                    refractionAmount = refractionAmountPx,
                    interiorRefractionAmount = interiorRefractionAmountPx,
                    convexLightingStrength = spec.convexLightingStrength,
                    saturation = spec.saturation,
                    depthEffect = spec.depthEffect,
                    chromaticAberration = spec.chromaticAberration,
                )
            )
            clipPath.rewind()
            clipPath.addRoundRect(
                RectF(0f, 0f, owner.width.toFloat(), owner.height.toFloat()),
                cornerRadiusPx, cornerRadiusPx, Path.Direction.CW,
            )
            appliedEffect = effect
        }
        val saveCount = canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRenderNode(node)
        canvas.restoreToCount(saveCount)
    }

    private fun drawSource(canvas: Canvas, source: View, padding: Int) {
        source.getLocationInWindow(sourceLocation)
        val saveCount = canvas.save()
        // Window coordinates include ancestor scrolling; direct draw still needs this View's scroll.
        canvas.translate(
            (padding + sourceLocation[0] - ownerLocation[0] - source.scrollX).toFloat(),
            (padding + sourceLocation[1] - ownerLocation[1] - source.scrollY).toFloat(),
        )
        source.draw(canvas)
        canvas.restoreToCount(saveCount)
    }

    private fun drawWindowBackground(canvas: Canvas, padding: Int, source: NgBackdropSourceLayout?) {
        val root = owner.rootView
        val background = root.background ?: return
        root.getLocationInWindow(rootLocation)
        background.copyBounds(oldDrawableBounds)
        val saveCount = canvas.save()
        canvas.translate(
            (padding + rootLocation[0] - ownerLocation[0]).toFloat(),
            (padding + rootLocation[1] - ownerLocation[1]).toFloat(),
        )
        fun setBounds(bounds: Rect) {
            if (source != null) {
                source.withUntrackedWindowBackgroundBounds { background.bounds = bounds }
            } else {
                background.bounds = bounds
            }
        }
        try {
            setBounds(Rect(0, 0, root.width, root.height))
            background.draw(canvas)
        } finally {
            setBounds(oldDrawableBounds)
            canvas.restoreToCount(saveCount)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createRenderEffect(
        width: Float,
        height: Float,
        padding: Float,
        cornerRadius: Float,
        blurRadius: Float,
        refractionHeight: Float,
        refractionAmount: Float,
        interiorRefractionAmount: Float,
        convexLightingStrength: Float,
        saturation: Float,
        depthEffect: Float,
        chromaticAberration: Float,
    ): RenderEffect? {
        val colorMatrix = ColorMatrix().apply {
            setSaturation(saturation.coerceAtLeast(0f))
        }
        var effect: RenderEffect? = RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(colorMatrix)
        )
        if (blurRadius > 0f) {
            val inputEffect = requireNotNull(effect)
            effect = RenderEffect.createBlurEffect(
                blurRadius,
                blurRadius,
                inputEffect,
                Shader.TileMode.CLAMP,
            )
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            refractionHeight > 0f &&
            refractionAmount > 0f
        ) {
            val shader = refractionShader
                ?: RuntimeShader(ROUNDED_RECT_REFRACTION_SHADER).also {
                    refractionShader = it
                }
            shader.setFloatUniform("size", width, height)
            shader.setFloatUniform("offset", -padding, -padding)
            shader.setFloatUniform(
                "cornerRadii",
                cornerRadius,
                cornerRadius,
                cornerRadius,
                cornerRadius,
            )
            shader.setFloatUniform("refractionHeight", refractionHeight)
            shader.setFloatUniform("refractionAmount", -refractionAmount)
            shader.setFloatUniform(
                "interiorRefractionAmount",
                -interiorRefractionAmount,
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
            val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
            effect = effect?.let { RenderEffect.createChainEffect(lens, it) } ?: lens
        }
        return effect
    }

    private fun drawSurface(canvas: Canvas) {
        val spec = resolvedSpec()
        val alpha = (surfaceAlpha * spec.surfaceAlphaScale).coerceIn(0f, 1f)
        surfacePaint.style = Paint.Style.FILL
        surfacePaint.shader = null
        surfacePaint.color = ColorUtils.setAlphaComponent(
            surfaceColor,
            (alpha * 255f).roundToInt(),
        )
        canvas.drawRoundRect(
            0f,
            0f,
            owner.width.toFloat(),
            owner.height.toFloat(),
            cornerRadiusPx,
            cornerRadiusPx,
            surfacePaint,
        )

        val highlightAlpha = (150f * spec.highlightAlphaScale).roundToInt()
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = spec.highlightWidth.value * owner.resources.displayMetrics.density
        highlightPaint.shader = LinearGradient(
            0f,
            0f,
            owner.width.toFloat(),
            owner.height.toFloat(),
            intArrayOf(
                ColorUtils.setAlphaComponent(Color.WHITE, highlightAlpha),
                ColorUtils.setAlphaComponent(Color.WHITE, highlightAlpha / 3),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP,
        )
        val inset = highlightPaint.strokeWidth / 2f
        canvas.drawRoundRect(
            inset,
            inset,
            owner.width - inset,
            owner.height - inset,
            (cornerRadiusPx - inset).coerceAtLeast(0f),
            (cornerRadiusPx - inset).coerceAtLeast(0f),
            highlightPaint,
        )
        highlightPaint.shader = null
    }

    private fun resolvedSpec(): NgLiquidGlassSpec =
        specOverride ?: NgLiquidGlassDefaults.spec(role)

    private fun attachPreDrawListener() {
        if (!owner.isAttachedToWindow || observedTree?.isAlive == true) return
        val tree = sourceView?.rootView?.viewTreeObserver ?: return
        tree.addOnPreDrawListener(preDrawListener)
        observedTree = tree
    }

    private fun detachPreDrawListener() {
        observedTree?.takeIf(ViewTreeObserver::isAlive)
            ?.removeOnPreDrawListener(preDrawListener)
        observedTree = null
    }

    private fun View.containsDescendant(candidate: View): Boolean {
        var current = candidate.parent
        while (current is View) {
            if (current === this) return true
            current = current.parent
        }
        return false
    }
}

internal class NgViewLiquidGlassBackdropView(context: android.content.Context) : View(context) {

    val renderer = NgViewLiquidGlassRenderer(this)

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        renderer.draw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        renderer.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        renderer.onDetachedFromWindow()
        super.onDetachedFromWindow()
    }
}
