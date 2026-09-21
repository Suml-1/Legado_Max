package io.legado.app.ui.book.read.page

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ImageSpan
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import io.legado.app.R
import io.legado.app.help.config.ReaderInfoPart
import io.legado.app.help.config.ReaderInfoTemplate
import io.legado.app.help.config.ReaderInfoValues
import splitties.init.appCtx
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 把页眉/页脚模板渲染成可显示的富文本。
 *
 * 电量图标走 [ImageSpan] + 自绘 Drawable：宽度直接取自 Drawable 尺寸，绘制走标准 Drawable
 * 路径，不依赖自定义 ReplacementSpan 的测量结果——之前那种写法在"该位置只有图标"时会露出测量异常。
 */
object ReaderInfoTemplateRenderer {

    /** 图标的占位字符，ImageSpan 必须挂在对象替换字符上 */
    private const val ICON_CHAR = '\uFFFC'

    /** 零宽空格，宽度为 0，用来避免图标 Span 独占整段文本 */
    private const val ZERO_WIDTH = "\u200B"

    /**
     * @param textSizeSp 该位置配置的字号（sp），电量图标按同比例缩放
     * @param color 该位置文字颜色，电量图标跟随
     */
    fun render(
        template: String,
        values: ReaderInfoValues,
        textSizeSp: Float,
        color: Int,
    ): CharSequence {
        val output = SpannableStringBuilder()
        ReaderInfoTemplate.parse(template, values).forEach { part ->
            when (part) {
                is ReaderInfoPart.Text -> output.append(part.value)
                is ReaderInfoPart.BatteryIcon -> {
                    val start = output.length
                    output.append(ICON_CHAR)
                    val iconDrawable =
                        BatteryIconDrawable(part.level, part.showLevel, textSizeSp, color)
                    output.setSpan(
                        ReaderInfoIconSpan(iconDrawable),
                        start,
                        output.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
        }
        // 图标 Span 若覆盖整段文本（该位置只填了一个电量图标），部分系统版本不会绘制它——
        // 表现就是"只放图标时什么都不显示，加了别的字符才出来"。两端补零宽空格，让图标永远
        // 旁边有普通字符；零宽空格宽度为 0，排版与外观都不受影响。
        if (output.isNotEmpty()) {
            if (output[0] == ICON_CHAR) {
                output.insert(0, ZERO_WIDTH)
            }
            if (output[output.length - 1] == ICON_CHAR) {
                output.append(ZERO_WIDTH)
            }
        }
        return output
    }
}

/**
 * 页眉/页脚里的图标 Span。
 *
 * 默认的 [ImageSpan] 会顺手把 [Paint.FontMetricsInt] 改成 drawable 的高度
 * （`ascent = -高度`、`descent = 0`），后果是"带图标的那一行"行高被撑大、
 * 图标还整体贴到行顶，与只有文字的页眉页脚位置高度对不上。
 *
 * 因此这里做两件事：
 * 1. `getSize` 自己返回宽度、不写 `fm`，行高完全由同行文字决定；
 * 2. 自己接管绘制，用传入 paint 的真实字体度量把图标钉在文字行框内居中，
 *    避免 drawable 预估值与实际字体不一致造成的上下偏移。
 */
private class ReaderInfoIconSpan(
    private val iconDrawable: BatteryIconDrawable,
) : ImageSpan(iconDrawable) {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        // 故意不走 ImageSpan 的实现：它按 drawable.bounds.right 取宽度，bounds 一旦被按 0 尺寸
        // 改写就得到 0 宽、图标彻底看不见；这里直接给 Drawable 自身尺寸。也不写 fm，保持
        // 行高由同行文字决定（ImageSpan 默认会把 fm 改成 drawable 高度，撑高整行）。
        return iconDrawable.getIntrinsicWidth().coerceAtLeast(1)
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
        paint: Paint,
    ) {
        val fontMetrics = paint.fontMetricsInt
        val lineTop = y + fontMetrics.ascent
        val lineHeight = fontMetrics.descent - fontMetrics.ascent
        val saveCount = canvas.save()
        canvas.translate(x, lineTop.toFloat())
        iconDrawable.drawInLine(canvas, lineHeight)
        canvas.restoreToCount(saveCount)
    }
}

/**
 * 页眉/页脚里的电量图标。
 *
 * 外观沿用阅读界面原有的组合：`ic_battery` 轮廓图标 + 图标内填充条（按电量）或电量数字。
 * 尺寸按字号等比缩放，缩放系数与旧实现 `BatteryView.scaleFactor()` 一致。
 */
private class BatteryIconDrawable(
    private val level: Int,
    private val showLevel: Boolean,
    textSizeSp: Float,
    color: Int,
) : Drawable() {

    private val metrics = appCtx.resources.displayMetrics
    private val textSizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, textSizeSp, metrics
    )
    private val scale = ((textSizePx / (11f * metrics.scaledDensity)) * 0.95f).coerceIn(0.66f, 1.9f)
    private val iconWidth = (28f * metrics.density * scale).roundToInt()
    private val iconHeight = (12f * metrics.density * scale).roundToInt()
    private val iconMarginStart = (4f * metrics.density * scale).roundToInt()
    private val fillMaxWidth = 17f * metrics.density * scale
    private val fillHeight = 8f * metrics.density * scale
    private val fillStartOffset = 4.2f * metrics.density * scale
    private val fillCorner = 1f * metrics.density * scale
    private val levelTextSize = textSizePx * 0.73f
    /** 原实现给图标设了 0.76 透明度，少了这一步图标会比原来更实、更抢眼 */
    private val iconAlpha = (255 * 0.76f).roundToInt()
    /** 原布局里电量数字整体略偏左、偏上（marginEnd 2.4dp / marginBottom 0.8dp） */
    private val levelTextMarginEnd = 2.4f * metrics.density * scale
    private val levelTextMarginBottom = 0.8f * metrics.density * scale
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    /** 该字号下的真实行高，图标在行高范围内垂直居中，与同行文字对齐 */
    private val lineHeight = TextPaint().apply { textSize = textSizePx }
        .fontMetricsInt.let { it.descent - it.ascent }

    private val icon: Drawable? = ContextCompat.getDrawable(appCtx, R.drawable.ic_battery)
        ?.let { original ->
            val copy = original.constantState?.newDrawable(appCtx.resources)?.mutate()
                ?: original.mutate()
            DrawableCompat.setTint(copy, color)
            copy.alpha = iconAlpha
            copy
        }

    init {
        // ImageSpan 是靠 drawable.bounds 算宽度的，而 bounds 默认是 (0,0,0,0)，
        // ImageSpan 构造时也不会帮忙设置——不设置就会得到零宽空白，图标完全看不见。
        setBounds(0, 0, getIntrinsicWidth(), getIntrinsicHeight())
    }

    /**
     * 注意：一律用相对坐标绘制，不修改本 Drawable 自身的 [bounds]——ImageSpan 依赖它计算宽度，
     * 一旦在 draw 里改掉，每次重绘都会把图标再往右推一次。
     */
    override fun draw(canvas: Canvas) {
        drawInLine(canvas, bounds.height())
    }

    /**
     * 在给定行高内绘制，左下角对齐 (0, 0)。
     *
     * @param lineHeight 同行文字的实际行高，图标与内部填充/数字都在其中垂直居中，
     *   这样带图标的位置和不带图标的位置高度、基线都一致。
     */
    fun drawInLine(canvas: Canvas, lineHeight: Int) {
        val iconTop = ((lineHeight - iconHeight) / 2f).roundToInt()
        drawIcon(canvas, iconTop)
        val safeLevel = level.coerceIn(0, 100)
        if (showLevel) {
            drawLevelText(canvas, safeLevel, iconTop)
        } else {
            drawFill(canvas, safeLevel, iconTop)
        }
    }

    /**
     * 复现原布局里 ImageView 的 centerCrop：`ic_battery` 原图是正方形，显示区域却是 28×12，
     * 必须等比放大到盖满显示区域、再居中裁掉上下多余部分（原图上下留白正好被裁掉，只留电池本体）。
     * 直接按目标矩形拉伸会让图标横向变形。
     */
    private fun drawIcon(canvas: Canvas, iconTop: Int) {
        val iconDrawable = icon ?: return
        val sourceWidth = iconDrawable.intrinsicWidth
        val sourceHeight = iconDrawable.intrinsicHeight
        if (sourceWidth <= 0 || sourceHeight <= 0) return
        val cropScale = max(
            iconWidth.toFloat() / sourceWidth,
            iconHeight.toFloat() / sourceHeight,
        )
        iconDrawable.setBounds(0, 0, sourceWidth, sourceHeight)
        val saveCount = canvas.save()
        canvas.clipRect(
            iconMarginStart, iconTop, iconMarginStart + iconWidth, iconTop + iconHeight
        )
        canvas.translate(iconMarginStart + iconWidth / 2f, iconTop + iconHeight / 2f)
        canvas.scale(cropScale, cropScale)
        canvas.translate(-sourceWidth / 2f, -sourceHeight / 2f)
        iconDrawable.draw(canvas)
        canvas.restoreToCount(saveCount)
    }

    private fun drawLevelText(canvas: Canvas, safeLevel: Int, iconTop: Int) {
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = levelTextSize
        val fontMetrics = paint.fontMetrics
        val centerX = iconMarginStart + (iconWidth - levelTextMarginEnd) / 2f
        val areaHeight = iconHeight - levelTextMarginBottom
        val baseline = iconTop + areaHeight / 2f -
            (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(safeLevel.toString(), centerX, baseline, paint)
    }

    private fun drawFill(canvas: Canvas, safeLevel: Int, iconTop: Int) {
        val fillWidth = fillMaxWidth * safeLevel / 100f
        if (fillWidth <= 0f) return
        val left = iconMarginStart + fillStartOffset
        val top = iconTop + (iconHeight - fillHeight) / 2f
        canvas.drawRoundRect(
            left, top, left + fillWidth, top + fillHeight, fillCorner, fillCorner, paint
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        icon?.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        icon?.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = iconMarginStart + iconWidth

    /** 取文字行高，图标在其中垂直居中，既与同行文字对齐又不会撑高页眉页脚 */
    override fun getIntrinsicHeight(): Int = max(1, lineHeight)
}
