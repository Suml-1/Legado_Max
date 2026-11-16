package io.legado.app.ui.design.theme

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.hypot

/**
 * Static color fields and broad contours for the existing glass to sample.
 * Geometry and shaders are rebuilt only when the viewport changes, with no bitmap or frame loop.
 */
internal class NgMulticolorLightFieldDrawable : Drawable() {

    private class Layer(val path: Path?, val shader: Shader, val overlay: Shader? = null)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val coveragePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = -1 }
    private val compositePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val colorLayerPaint = Paint()
    private val sourceIn = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    private val sourceAtop = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    private val drawingBounds = RectF()
    private val warmBounds = RectF()
    private var layers: List<Layer> = emptyList()
    private var geometryDirty = true
    private var drawableAlpha = 255

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        geometryDirty = true
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        if (geometryDirty) rebuildLayers()
        val checkpoint = if (drawableAlpha < 255) {
            canvas.saveLayerAlpha(drawingBounds, drawableAlpha)
        } else {
            canvas.save()
        }
        canvas.clipRect(drawingBounds)
        layers.forEach { layer ->
            if (layer.overlay != null && layer.path != null) {
                drawColorSurface(canvas, layer.path, layer.shader, layer.overlay)
            } else {
                paint.shader = layer.shader
                if (layer.path == null) canvas.drawRect(drawingBounds, paint)
                else canvas.drawPath(layer.path, paint)
            }
        }
        paint.shader = null
        canvas.restoreToCount(checkpoint)
    }

    private fun drawColorSurface(canvas: Canvas, path: Path, base: Shader, overlay: Shader) {
        val checkpoint = canvas.saveLayer(drawingBounds, colorLayerPaint)
        // Rasterize the shared silhouette once; repeated antialiasing exposes a tinted fringe.
        canvas.drawPath(path, coveragePaint)
        compositePaint.shader = base
        compositePaint.xfermode = sourceIn
        canvas.drawRect(drawingBounds, compositePaint)
        // SRC_ATOP mixes the warm face while preserving the silhouette's original coverage.
        compositePaint.shader = overlay
        compositePaint.xfermode = sourceAtop
        canvas.drawRect(warmBounds, compositePaint)
        compositePaint.shader = null
        compositePaint.xfermode = null
        canvas.restoreToCount(checkpoint)
    }

    override fun setAlpha(alpha: Int) {
        val normalized = alpha.coerceIn(0, 255)
        if (drawableAlpha == normalized) return
        drawableAlpha = normalized
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        colorLayerPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun rebuildLayers() {
        drawingBounds.set(bounds)
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        fun x(value: Float) = bounds.left + width * value
        fun y(value: Float) = bounds.top + height * value
        // The entrance mask is transparent across the colored silhouette at this top edge.
        warmBounds.set(bounds.left.toFloat(), y(0.54f), bounds.right.toFloat(), bounds.bottom.toFloat())
        fun linear(
            startX: Float, startY: Float, endX: Float, endY: Float,
            vararg colors: Long,
            stops: FloatArray? = null,
        ) = LinearGradient(
            x(startX), y(startY), x(endX), y(endY),
            colors.map(Long::toInt).toIntArray(), stops, Shader.TileMode.CLAMP,
        )
        fun ellipse(
            centerX: Float, centerY: Float, radiusX: Float, radiusY: Float,
            colors: IntArray, stops: FloatArray? = null,
        ): Shader = RadialGradient(
            0f, 0f, 1f, colors, stops, Shader.TileMode.CLAMP,
        ).apply {
            setLocalMatrix(Matrix().apply {
                setScale(width * radiusX, height * radiusY)
                postTranslate(x(centerX), y(centerY))
            })
        }
        fun contour(block: Path.() -> Unit) = Path().apply(block)
        // Blue and rose share one continuous outer contour at their former crossing.
        val colorEdge = contour {
            moveTo(x(0.72f), y(-0.08f))
            cubicTo(x(0.52f), y(0.08f), x(0.54f), y(0.19f), x(0.74f), y(0.29f))
            cubicTo(x(0.80f), y(0.32f), x(0.84f), y(0.34f), x(0.82f), y(0.37f))
            cubicTo(x(0.78f), y(0.43f), x(0.65f), y(0.48f), x(0.56f), y(0.54f))
            // Meet the apricot edge at its two-thirds point with the same tangent.
            cubicTo(x(0.425f), y(0.63f), x(0.407556f), y(0.693444f), x(0.515556f), y(0.744444f))
            cubicTo(x(0.623556f), y(0.795444f), x(0.85f), y(0.90f), x(1.12f), y(0.93f))
        }
        val color = Path(colorEdge).apply {
            lineTo(x(1.12f), y(-0.08f))
            close()
        }
        val mintEdge = contour {
            moveTo(x(-0.12f), y(0.38f))
            cubicTo(x(0.23f), y(0.43f), x(0.47f), y(0.62f), x(0.62f), y(0.76f))
        }
        // Continue the mint surface behind the foreground instead of exposing a returning sliver.
        val mint = Path(mintEdge).apply {
            lineTo(x(1.12f), y(0.76f))
            lineTo(x(1.12f), y(1.10f))
            lineTo(x(-0.12f), y(1.10f))
            close()
        }
        // A parallel ribbon follows the same curve, fading out before the lower junction.
        val light = Path(colorEdge).apply {
            lineTo(x(1.04f), y(0.93f))
            cubicTo(x(0.77f), y(0.90f), x(0.543556f), y(0.795444f), x(0.435556f), y(0.744444f))
            cubicTo(x(0.327556f), y(0.693444f), x(0.345f), y(0.63f), x(0.48f), y(0.54f))
            cubicTo(x(0.57f), y(0.48f), x(0.70f), y(0.43f), x(0.74f), y(0.37f))
            cubicTo(x(0.76f), y(0.34f), x(0.72f), y(0.32f), x(0.66f), y(0.29f))
            cubicTo(x(0.46f), y(0.19f), x(0.44f), y(0.08f), x(0.64f), y(-0.08f))
            close()
        }
        // Keep the gradient across the lower arc, rather than darkening along the arc.
        // Correct for the viewport aspect ratio so this color direction also holds on tablets.
        val warmAxisX = 0.26f / width
        val warmAxisY = -0.55f / height
        val warmScale = 0.25f / (warmAxisX * warmAxisX + warmAxisY * warmAxisY)
        val warmEndX = 0.45f + warmAxisX * warmScale / width
        val warmEndY = 0.66f + warmAxisY * warmScale / height
        // Place the bright entrance at the outer join; shading deepens inside the warm face.
        // Darkening along the outer contour made its encoded-sRGB edge appear like a seam.
        val warmEntranceMask = ellipse(
            0.70f, 0.37f, 0.672f, 0.32f,
            intArrayOf(0x00FFFFFF, 0x00FFFFFF, 0xFFFFFFFF.toInt()),
            floatArrayOf(0f, 0.7809524f, 1f),
        )
        layers = listOf(
            Layer(null, linear(0.1f, 0f, 0.55f, 1f, 0xFF43CFF1, 0xFFAAF2E6, 0xFF96E9CF)),
            Layer(mint, linear(0f, 0.38f, 0.4f, 1f, 0xFFADEED7, 0xFF81E3B9, 0xFF77DDC0)),
            Layer(
                color,
                linear(
                    0.8f, 0f, 0.8f, 1f,
                    0xFF559AF5, 0xFF83B2F3, 0xFFACB5EF, 0xFFCEADE8, 0xFFFFA2AF, 0xFFF9CAB8,
                    stops = floatArrayOf(0f, 0.23f, 0.34f, 0.42f, 0.65f, 1f),
                ),
                ComposeShader(
                    linear(
                        0.45f, 0.66f, warmEndX, warmEndY,
                        0xFFFFD8A4, 0xFFFFAD85, 0xFFFFB3A6, 0xFFF9CAB8,
                        stops = floatArrayOf(0f, 0.37037037f, 0.74074074f, 1f),
                    ),
                    warmEntranceMask,
                    PorterDuff.Mode.DST_IN,
                ),
            ),
            Layer(light, linear(
                0f, 0f, 0f, 1f,
                0x66F0FFFF, 0x50F4FFFF, 0x20FFFFFF, 0x00FFFFFF,
                stops = floatArrayOf(0f, 0.24f, 0.42f, 0.57f),
            )),
            // Local illumination has no clipped diagonal edge.
            Layer(null, ellipse(
                0.18f, 1.12f, 0.85f, 0.32f,
                intArrayOf(0x30F2F8EB, 0x10F2F8EB, 0x00F2F8EB),
                floatArrayOf(0f, 0.55f, 1f),
            )),
            Layer(null, RadialGradient(
                x(0.48f), y(0.48f), hypot(width.toDouble(), height.toDouble()).toFloat() * 0.53f,
                intArrayOf(0x18FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP,
            )),
        )
        geometryDirty = false
    }
}
