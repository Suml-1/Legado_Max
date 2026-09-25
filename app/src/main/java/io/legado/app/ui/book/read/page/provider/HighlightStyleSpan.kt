package io.legado.app.ui.book.read.page.provider

import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ReplacementSpan
import android.text.style.UpdateAppearance
import io.legado.app.ui.book.read.config.highlight.HighlightRuleStyle

/**
 * 阅读排版阶段传递高亮规则样式的轻量 Span。
 *
 * 只携带下划线、背景色和背景图等样式参数，不直接绘制；
 * 最终绘制由 TextLine 根据列对象上的样式字段完成。
 */
class HighlightStyleSpan(
    val underlineMode: Int,
    val underlineColor: Int,
    val underlineWidth: Float = 1f,
    val underlineOffset: Float = 2f,
    val underlineSvgPath: String = "",
    val bgColor: Int? = null,
    val bgImage: String = "",
    val bgImageFit: Int = 0,
    val bgImageScale: Float = 1f,
    val npLeft: Float = 0.1f,
    val npTop: Float = 0.1f,
    val npRight: Float = 0.1f,
    val npBottom: Float = 0.1f,
    val bgBleedMode: Int = 1,
    val bgSpacingLeft: Float = 0f,
    val bgSpacingRight: Float = 0f,
    val bgSpacingTop: Float = 0f,
    val bgSpacingBottom: Float = 0f,
    val letterSpacingBefore: Float = 0f,
    val letterSpacingAfter: Float = 0f,
) : CharacterStyle(), UpdateAppearance {

    constructor(style: HighlightRuleStyle) : this(
        underlineMode = style.underlineMode,
        underlineColor = style.resolvedAccentColor,
        underlineWidth = style.underlineWidth,
        underlineOffset = style.underlineOffset,
        underlineSvgPath = style.underlineSvgPath,
        bgColor = style.bgColor,
        bgImage = style.bgImage,
        bgImageFit = style.bgImageFit,
        bgImageScale = style.bgImageScale,
        npLeft = style.npLeft,
        npTop = style.npTop,
        npRight = style.npRight,
        npBottom = style.npBottom,
        bgBleedMode = style.bgBleedMode,
        bgSpacingLeft = style.bgSpacingLeft,
        bgSpacingRight = style.bgSpacingRight,
        bgSpacingTop = style.bgSpacingTop,
        bgSpacingBottom = style.bgSpacingBottom,
        letterSpacingBefore = style.letterSpacingBefore,
        letterSpacingAfter = style.letterSpacingAfter,
    )

    override fun updateDrawState(tp: TextPaint) = Unit

}

/**
 * 命中字距的占位 Span：让命中段首/尾字符分别多占 [leading] / [trailing] 像素，
 * 其余表现不变。
 *
 * HTML 排版走 StaticLayout，只有把留白挂到字符自身的推进宽度上，断行与两端对齐
 * 才会按真实宽度计算；绘制时按 [leading] 右移，留白就落在字形与邻字之间，而不是
 * 挤进命中段内部。阅读页正文按列绘制，[draw] 只在直接绘制 Layout 的场景兜底。
 */
class BoundarySpacingSpan(
    private val leading: Float = 0f,
    private val trailing: Float = 0f,
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int = (paint.measureText(text, start, end) + leading + trailing).toInt().coerceAtLeast(0)

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
        canvas.drawText(text, start, end, x + leading, y.toFloat(), paint)
    }
}

/**
 * 只为排版量宽而存在的 Span：让所在字符多占 [extraWidth] 像素，其余表现不变。
 *
 * 九宫格"强制"策略要把背景左右两侧的邻字推开一个正文字距，做法就是把这个距离加进**邻字自己**
 * 的推进宽度——这样断行与两端对齐都按真实宽度计算，剩下的文字仍然整齐。
 * 阅读页正文按列绘制，[draw] 只在直接绘制 Layout 的场景兜底。
 */
class HighlightSpacingSpan(private val extraWidth: Float) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int = (paint.measureText(text, start, end) + extraWidth).toInt().coerceAtLeast(0)

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
        canvas.drawText(text, start, end, x, y.toFloat(), paint)
    }
}
