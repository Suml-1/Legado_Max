package io.legado.app.ui.book.read.config.highlight

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import io.legado.app.ui.book.read.page.provider.HighlightTypefaceSpan

/**
 * 高亮规则配置页的预览文本构建器。
 *
 * 根据规则正则和统一样式模型生成可直接显示在 TextView 中的预览内容，
 * 用于编辑页和规则列表卡片，不参与阅读页最终绘制。
 */
object HighlightRulePreview {

    fun build(rule: HighlightRule): CharSequence {
        val text = rule.normalizedSampleText()
        val spannable = SpannableStringBuilder(text)
        val regex = kotlin.runCatching { rule.toRegex() }.getOrNull() ?: return spannable
        val style = HighlightRuleStyle.from(rule)
        regex.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (style.font.isNotBlank()) {
                spannable.setSpan(
                    HighlightTypefaceSpan(style.font),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            val textColor = style.resolvedTextColor
            val accentColor = style.resolvedAccentColor
            val underlineWidth = style.underlineWidth
            val underlineOffset = style.underlineOffset
            val hasBgImage = style.bgImage.isNotBlank()
            val bgColor = style.bgColor

            if (hasBgImage) {
                spannable.setSpan(
                BgImageSpan(
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
                    underlineWidth,
                    style.underlineSvgPath,
                    underlineOffset,
                ),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            } else if (bgColor != null) {
                spannable.setSpan(
                    BgColorSpan(
                        textColor,
                        bgColor,
                        style.underlineMode,
                        accentColor,
                        underlineWidth,
                        style.underlineSvgPath,
                        underlineOffset,
                    ),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            } else {
                when (style.underlineMode) {
                    1 -> {
                        spannable.setSpan(
                            SolidUnderlineSpan(textColor, accentColor, underlineWidth, underlineOffset),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                    2 -> {
                        spannable.setSpan(
                            DashUnderlineSpan(textColor, accentColor, underlineWidth, underlineOffset),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                    3 -> {
                        spannable.setSpan(
                            WaveUnderlineSpan(textColor, accentColor, underlineWidth, underlineOffset),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                    4 -> {
                        spannable.setSpan(
                            DoubleUnderlineSpan(textColor, accentColor, underlineWidth, underlineOffset),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                    5 -> {
                        val svgPath = style.underlineSvgPath
                        if (!svgPath.isNullOrBlank()) {
                            spannable.setSpan(
                                SvgUnderlineSpan(textColor, accentColor, underlineWidth, svgPath),
                                start,
                                end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                        } else {
                            spannable.setSpan(
                                ForegroundColorSpan(textColor),
                                start,
                                end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                        }
                    }
                    6 -> {
                        spannable.setSpan(
                            StrikeThroughSpan(textColor, accentColor, underlineWidth),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                    7 -> {
                        spannable.setSpan(
                            ItalicTextSpan(textColor),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                    8 -> {
                        spannable.setSpan(
                            BoxTextSpan(textColor, accentColor, underlineWidth),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                    else -> {
                        spannable.setSpan(
                            ForegroundColorSpan(textColor),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                }
            }
        }
        return spannable
    }
}
