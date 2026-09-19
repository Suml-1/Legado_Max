package io.legado.app.ui.book.read.config.highlight

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.style.ReplacementSpan
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.dpToPx

/**
 * 背景图+下划线 Span，用于高亮规则匹配区域
 * @param textColor 文字颜色
 * @param bgImagePath 背景图路径
 * @param bgImageFit 背景图适配方式：0=平铺, 1=拉伸, 2=裁剪, 3=九宫格
 * @param bgImageScale 背景图缩放比例
 * @param npLeft/npTop/npRight/npBottom 九宫格分割比例（0-1，左右相加、上下相加不超过 1），适配方式为九宫格时生效
 * @param underlineMode 下划线样式：0=无, 1=实线, 2=虚线, 3=波浪, 4=双线, 5=SVG, 6=删除线, 7=斜体, 8=方框
 * @param underlineColor 下划线颜色
 * @param underlineWidth 下划线粗细(dp)
 * @param underlineSvgPath SVG路径（用于自定义下划线）
 * @param underlineOffset 下划线与文字的距离(dp)
 */
class BgImageSpan(
    private val textColor: Int,
    private val bgImagePath: String,
    private val bgImageFit: Int = 0,
    private val bgImageScale: Float = 1f,
    private val npLeft: Float = 0.1f,
    private val npTop: Float = 0.1f,
    private val npRight: Float = 0.1f,
    private val npBottom: Float = 0.1f,
    private val bgBleedMode: Int = HighlightRule.BLEED_SMART,
    private val bgSpacingLeft: Float = 0f,
    private val bgSpacingRight: Float = 0f,
    private val bgSpacingTop: Float = 0f,
    private val bgSpacingBottom: Float = 0f,
    private val underlineMode: Int = 0,
    private val underlineColor: Int = 0,
    private val underlineWidth: Float = 1f,
    private val underlineSvgPath: String = "",
    private val underlineOffset: Float = 6f,
) : ReplacementSpan() {

    private val offsetPx = underlineOffset.toInt().dpToPx()  // 距离转换为像素

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        if (fm != null) {
            val metrics = paint.fontMetricsInt
            fm.top = metrics.top
            fm.ascent = metrics.ascent
            val needsOffset = underlineMode in 1..5
            fm.descent = metrics.descent + if (needsOffset) offsetPx else 0
            fm.bottom = metrics.bottom + if (needsOffset) offsetPx else 0
        }
        return paint.measureText(text, start, end).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val width = paint.measureText(text, start, end)
        val rectWidth = width
        val rectHeight = (bottom - top).toFloat()
        val scale = bgImageScale.coerceIn(0.1f, 5f)
        val textSize = paint.textSize
        val fontMetrics = paint.fontMetrics
        // 与正文渲染保持同一规则：上下以文字上下界为基准，不能用行盒内缩
        val sliceTop = y + fontMetrics.ascent
        val sliceBottom = y + fontMetrics.descent
        // 预览文本自身的行距余量，供智能策略上下外扩
        val lineGap = (bottom - top) * (ChapterProvider.lineSpacingExtra - 1f)
        val verticalBlankSpace = if (lineGap > 0f) lineGap / 2f else 0f

        val bitmap = TextLine.getBgBitmap(bgImagePath)
        if (bitmap != null) {
            val bgPaint = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
                isFilterBitmap = true
            }
            if (bgImageFit == 3) {
                // 九宫格：与正文渲染一致，按用户分割比例与外扩策略切图
                TextLine.drawNineSlice(
                    bitmap, canvas, x, sliceTop, x + width, sliceBottom,
                    npLeft, npTop, npRight, npBottom,
                    bgBleedMode,
                    leftBlankWidth = if (start > 0) measureBlankWidth(paint, text, start - 1) else 0f,
                    rightBlankWidth = if (end < text.length) measureBlankWidth(paint, text, end) else 0f,
                    verticalBlankSpace = verticalBlankSpace,
                    maxBleedX = textSize,
                    spacingLeft = bgSpacingLeft * textSize,
                    spacingRight = bgSpacingRight * textSize,
                    spacingTop = bgSpacingTop * textSize,
                    spacingBottom = bgSpacingBottom * textSize,
                )
            } else when (bgImageFit) {
                1 -> {
                    val sw = rectWidth * scale
                    val sh = rectHeight * scale
                    val dx = x + (rectWidth - sw) / 2f
                    val dy = top + (rectHeight - sh) / 2f
                    canvas.save()
                    canvas.clipRect(x, top.toFloat(), x + width, bottom.toFloat())
                    canvas.drawBitmap(bitmap, null, RectF(dx, dy, dx + sw, dy + sh), bgPaint)
                    canvas.restore()
                }
                2 -> {
                    val bw = bitmap.width.toFloat()
                    val bh = bitmap.height.toFloat()
                    val fitScale = (rectWidth / bw).coerceAtLeast(rectHeight / bh) * scale
                    val scaledW = bw * fitScale
                    val scaledH = bh * fitScale
                    val dx = x + (rectWidth - scaledW) / 2f
                    val dy = top + (rectHeight - scaledH) / 2f
                    canvas.save()
                    canvas.clipRect(x, top.toFloat(), x + width, bottom.toFloat())
                    canvas.drawBitmap(bitmap, null, RectF(dx, dy, dx + scaledW, dy + scaledH), bgPaint)
                    canvas.restore()
                }
                else -> {
                    val tileBitmap = if (scale != 1f) {
                        val sw = (bitmap.width * scale).toInt().coerceAtLeast(1)
                        val sh = (bitmap.height * scale).toInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(bitmap, sw, sh, true)
                    } else {
                        bitmap
                    }
                    val shader = BitmapShader(tileBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                    val matrix = Matrix()
                    matrix.setTranslate(x, top.toFloat())
                    shader.setLocalMatrix(matrix)
                    bgPaint.shader = shader
                    canvas.drawRect(x, top.toFloat(), x + width, bottom.toFloat(), bgPaint)
                }
            }
        }

        paint.color = textColor
        paint.shader = null
        if (underlineMode == 7) {
            val oldSkewX = paint.textSkewX
            paint.textSkewX = -0.25f
            canvas.drawText(text, start, end, x, y.toFloat(), paint)
            paint.textSkewX = oldSkewX
        } else {
            canvas.drawText(text, start, end, x, y.toFloat(), paint)
        }

        if (underlineMode != 0 && underlineMode != 7) {
            drawDecoration(canvas, x, x + width, y, paint)
        }
    }

    /** 邻接字符为空白时返回其宽度，供智能策略借用；有字形或越界时返回 0 */
    private fun measureBlankWidth(paint: Paint, text: CharSequence, index: Int): Float {
        if (index < 0 || index >= text.length) return 0f
        if (!text[index].isWhitespace()) return 0f
        return paint.measureText(text, index, index + 1)
    }

    private fun drawDecoration(canvas: Canvas, startX: Float, endX: Float, y: Int, paint: Paint) {
        val ulPaint = Paint(paint).apply {
            color = underlineColor
            style = Paint.Style.STROKE
            strokeWidth = underlineWidth.dpToPx()
            isAntiAlias = true
        }
        when (underlineMode) {
            1 -> canvas.drawLine(startX, (y + offsetPx).toFloat(), endX, (y + offsetPx).toFloat(), ulPaint)
            2 -> {
                ulPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
                canvas.drawLine(startX, (y + offsetPx).toFloat(), endX, (y + offsetPx).toFloat(), ulPaint)
            }
            3 -> {
                val path = android.graphics.Path()
                val waveAmplitude = 3.dpToPx().toFloat()
                val waveLength = 12.dpToPx().toFloat()
                val lineY = (y + offsetPx).toFloat()
                path.moveTo(startX, lineY)
                var currentX = startX
                while (currentX < endX) {
                    val nextX = (currentX + waveLength).coerceAtMost(endX)
                    val midX = (currentX + nextX) / 2
                    path.quadTo(midX, lineY - waveAmplitude, nextX, lineY)
                    currentX = nextX
                    if (currentX < endX) {
                        val nextX2 = (currentX + waveLength).coerceAtMost(endX)
                        val midX2 = (currentX + nextX2) / 2
                        path.quadTo(midX2, lineY + waveAmplitude, nextX2, lineY)
                        currentX = nextX2
                    }
                }
                canvas.drawPath(path, ulPaint)
            }
            4 -> {
                val lineY = y + offsetPx
                val lineGap = 3.dpToPx()
                val line2Y = lineY + lineGap + underlineWidth.dpToPx()
                canvas.drawLine(startX, lineY.toFloat(), endX, lineY.toFloat(), ulPaint)
                canvas.drawLine(startX, line2Y.toFloat(), endX, line2Y.toFloat(), ulPaint)
            }
            6 -> {
                val fm = paint.fontMetrics
                val centerY = y + (fm.ascent + fm.descent) / 2f
                canvas.drawLine(startX, centerY, endX, centerY, ulPaint)
            }
            8 -> {
                val fm = paint.fontMetrics
                val pad = 1.dpToPx().toFloat()
                val boxTop = y + fm.ascent - pad
                val boxBottom = y + fm.descent + pad
                canvas.drawRect(startX, boxTop, endX, boxBottom, ulPaint)
            }
        }
    }
}
