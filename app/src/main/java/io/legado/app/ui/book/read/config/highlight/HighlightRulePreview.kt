package io.legado.app.ui.book.read.config.highlight

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ReplacementSpan
import io.legado.app.ui.book.read.page.provider.HighlightFontCache
import java.text.BreakIterator
import kotlin.math.ceil

/**
 * 高亮规则配置页的预览文本构建器。
 *
 * 按规则正则与统一样式模型生成可显示的预览内容，用于编辑页和规则列表卡片；
 * 不参与阅读页最终绘制。留白、行距这类会影响排版结果的样式必须按可用宽度重新断行，
 * 所以构建时要传入画笔与绘制宽度（见 [HighlightPreviewTextView]）。
 */
object HighlightRulePreview {

    /**
     * 构建预览内容。
     *
     * @param defaultTextColor 规则未指定字色时的文字颜色（跟随主题）
     * @param paint 目标控件的画笔，用于量宽与断行
     * @param width 目标控件的可用宽度（px），留白会从这段宽度里扣除
     */
    fun build(
        rule: HighlightRule,
        defaultTextColor: Int,
        paint: TextPaint,
        width: Int,
    ): CharSequence {
        val text = rule.normalizedSampleText()
        val regex = runCatching { rule.toRegex() }.getOrNull() ?: return text
        val style = HighlightRuleStyle.from(rule)
        val styledPaint = TextPaint(paint).apply {
            if (style.font.isNotBlank()) {
                HighlightFontCache.getTypefaceFor(style.font, paint.typeface)?.let { typeface = it }
            }
        }
        val decoration = decoration(style, defaultTextColor)
        val characters = BreakIterator.getCharacterInstance().apply { setText(text) }
        val words = BreakIterator.getLineInstance().apply { setText(text) }
        val result = SpannableStringBuilder()
        var usedWidth = 0f

        fun newLine() {
            result.append('\n')
            usedWidth = 0f
        }

        fun appendRun(start: Int, end: Int, matched: Boolean) {
            var cursor = start
            val runPaint = if (matched) styledPaint else paint
            while (cursor < end) {
                if (text[cursor] == '\n') {
                    newLine()
                    cursor++
                    continue
                }
                val paragraphEnd = text.indexOf('\n', cursor).let { if (it < 0) end else minOf(it, end) }
                // 命中字距在预览里同样占位：断行按"字符宽 + 留白"算，命中段外侧才有留白
                var before = if (matched) style.letterSpacingBefore.previewSpacing() else 0f
                var after = if (matched) style.letterSpacingAfter.previewSpacing() else 0f
                val available = (width - usedWidth - before - after).coerceAtLeast(0f)
                val count = runPaint.breakText(text, cursor, paragraphEnd, true, available, null)
                var limit = cursor + count
                if (limit < paragraphEnd && !characters.isBoundary(limit)) {
                    limit = characters.preceding(limit).coerceAtLeast(cursor)
                }
                if (limit == cursor) {
                    if (usedWidth > 0f) {
                        newLine()
                        continue
                    }
                    limit = characters.following(cursor).coerceAtMost(paragraphEnd)
                    // 极窄预览下也要保证至少一个完整字形可见：先把留白按剩余空间等比压缩
                    val room = (width - ceil(runPaint.measureText(text, cursor, limit))).coerceAtLeast(0f)
                    if (before + after > room) {
                        val scale = if (before + after > 0f) room / (before + after) else 0f
                        before *= scale
                        after *= scale
                    }
                } else if (limit < paragraphEnd) {
                    val wordEnd = if (words.isBoundary(limit)) limit else words.preceding(limit)
                    if (wordEnd > cursor) limit = wordEnd
                }
                val offset = result.length
                result.append(text, cursor, limit)
                if (matched) {
                    val span = PreviewSpan(style, defaultTextColor, before, after, decoration, styledPaint.typeface)
                    result.setSpan(span, offset, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    usedWidth += span.getSize(runPaint, text, cursor, limit, null)
                } else {
                    usedWidth += ceil(runPaint.measureText(text, cursor, limit))
                }
                cursor = limit
                if (cursor < paragraphEnd) newLine()
            }
        }

        var cursor = 0
        regex.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (start >= end) return@forEach
            appendRun(cursor, start, false)
            appendRun(start, end, true)
            cursor = end
        }
        appendRun(cursor, text.length, false)
        return result
    }

    /** 命中区间的装饰 Span：背景图/背景色优先，其次下划线类样式，都没有时返回 null 表示只改字色 */
    private fun decoration(style: HighlightRuleStyle, defaultTextColor: Int): ReplacementSpan? {
        val textColor = style.textColor ?: defaultTextColor
        val accentColor = style.resolvedAccentColor
        return when {
            style.bgImage.isNotBlank() -> BgImageSpan(
                textColor,
                style.bgImage,
                style.bgImageFit,
                style.bgImageScale,
                style.npLeft,
                style.npTop,
                style.npRight,
                style.npBottom,
                style.bgBleedMode,
                style.bgSpacingLeft,
                style.bgSpacingRight,
                style.bgSpacingTop,
                style.bgSpacingBottom,
                style.underlineMode,
                accentColor,
                style.underlineWidth,
                style.underlineSvgPath,
                style.underlineOffset,
            )

            style.bgColor != null -> BgColorSpan(
                textColor,
                style.bgColor,
                style.underlineMode,
                accentColor,
                style.underlineWidth,
                style.underlineSvgPath,
                style.underlineOffset,
            )

            else -> when (style.underlineMode) {
                1 -> SolidUnderlineSpan(textColor, accentColor, style.underlineWidth, style.underlineOffset)
                2 -> DashUnderlineSpan(textColor, accentColor, style.underlineWidth, style.underlineOffset)
                3 -> WaveUnderlineSpan(textColor, accentColor, style.underlineWidth, style.underlineOffset)
                4 -> DoubleUnderlineSpan(textColor, accentColor, style.underlineWidth, style.underlineOffset)
                5 -> style.underlineSvgPath.takeIf { it.isNotBlank() }
                    ?.let { SvgUnderlineSpan(textColor, accentColor, style.underlineWidth, it) }
                6 -> StrikeThroughSpan(textColor, accentColor, style.underlineWidth)
                7 -> ItalicTextSpan(textColor)
                8 -> BoxTextSpan(textColor, accentColor, style.underlineWidth)
                else -> null
            }
        }
    }

    /**
     * 命中区间的占位 Span：负责留白与字色/字体的实际表现。
     *
     * 留白加在推进宽度上（[getSize]），绘制时按留白右移（[draw]），
     * 这样留白落在命中段外侧，命中段内部不会被撑开。
     */
    private class PreviewSpan(
        private val ruleStyle: HighlightRuleStyle,
        private val defaultTextColor: Int,
        private val before: Float,
        private val after: Float,
        private val decoration: ReplacementSpan?,
        private val typeface: Typeface?,
    ) : ReplacementSpan() {

        private fun styledPaint(paint: Paint): TextPaint = TextPaint(paint).apply {
            // 注意：apply 内层接收者是 Paint，style 会被解析成 Paint.style，所以这里用具名字段
            color = ruleStyle.textColor ?: defaultTextColor
            typeface = this@PreviewSpan.typeface
        }

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?,
        ): Int {
            val styled = styledPaint(paint)
            if (fm != null) {
                styled.getFontMetricsInt(fm)
                // 装饰线（下划线远近等）会影响行高，交给装饰 Span 补足
                decoration?.getSize(styled, text, start, end, fm)
                if (ruleStyle.lineSpacingEnabled) {
                    // 命中行行距：把上下留白并进字形盒，行高随之增加（与阅读页命中行口径一致）
                    val top = ceil(ruleStyle.lineSpacingTop.previewSpacing()).toInt()
                    val bottom = ceil(ruleStyle.lineSpacingBottom.previewSpacing()).toInt()
                    fm.top -= top
                    fm.ascent -= top
                    fm.descent += bottom
                    fm.bottom += bottom
                }
            }
            return ceil(styled.measureText(text, start, end) + before + after).toInt()
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
            val styled = styledPaint(paint)
            if (decoration == null) {
                canvas.drawText(text, start, end, x + before, y.toFloat(), styled)
            } else {
                // 装饰线的上下界对齐字形上下界，与阅读页按字形绘制口径一致
                val metrics = styled.fontMetricsInt
                decoration.draw(
                    canvas,
                    text,
                    start,
                    end,
                    x + before,
                    y + metrics.ascent,
                    y,
                    y + metrics.descent,
                    styled,
                )
            }
        }
    }
}

/** 留白取值收敛：非有限值（NaN/Inf）与越界值一并回落到 0，上限沿用编辑页的输入范围 */
private fun Float.previewSpacing(): Float =
    takeIf { it.isFinite() }
        ?.coerceIn(HighlightRuleStore.MIN_MATCH_SPACING, HighlightRuleStore.MAX_MATCH_SPACING)
        ?: 0f
