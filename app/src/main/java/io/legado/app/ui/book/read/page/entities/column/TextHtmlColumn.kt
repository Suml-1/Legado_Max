package io.legado.app.ui.book.read.page.entities.column

import android.graphics.Canvas
import android.os.Build
import android.text.TextPaint
import androidx.annotation.Keep
import io.legado.app.help.TextViewTagHandler.Companion.HR_PLACE_STR
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextLine.Companion.emptyTextLine
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.HighlightFontCache

/**
 * 带html样式的文字列
 */
@Keep
data class TextHtmlColumn(
    override var start: Float,
    override var end: Float,
    override val charData: String,
    val mTextSize: Float,
    val mTextColor: Int?,
    val linkUrl: String?,
    override val underlineMode: Int = 0,
    override val underlineColor: Int? = null,
    override val underlineWidth: Float = 1f,
    override val underlineOffset: Float = 2f,
    override val underlineSvgPath: String = "",
    override val bgColor: Int? = null,
    override val bgImage: String = "",
    override val bgImageFit: Int = 0,
    override val bgImageScale: Float = 1f,
    override val npLeft: Float = 0.1f,
    override val npTop: Float = 0.1f,
    override val npRight: Float = 0.1f,
    override val npBottom: Float = 0.1f,
    override val bgBleedMode: Int = 1,
    override val bgSpacingLeft: Float = 0f,
    override val bgSpacingRight: Float = 0f,
    override val bgSpacingTop: Float = 0f,
    override val bgSpacingBottom: Float = 0f,
    override val fontPath: String = "",
) : TextBaseColumn {

    override val textColor: Int? get() = mTextColor

    override var textLine: TextLine = emptyTextLine

    private val textPaint: TextPaint by lazy {
        TextPaint(ChapterProvider.contentPaint).apply {
            textSize = mTextSize
        }
    }

    override var selected: Boolean = false
        set(value) {
            if (field != value) {
                textLine.invalidate()
            }
            field = value
        }

    override var isSearchResult: Boolean = false
        set(value) {
            if (field != value) {
                textLine.invalidate()
                if (value) {
                    textLine.searchResultColumnCount++
                } else {
                    textLine.searchResultColumnCount--
                }
            }
            field = value
        }
    override var isCurrentSearchResult: Boolean = false
        set(value) {
            if (field != value) {
                textLine.invalidate()
            }
            field = value
        }

    override fun draw(view: ContentTextView, canvas: Canvas) {
        val y = textLine.lineBase - textLine.lineTop
        // 高亮规则指定字体时替换画笔字体（保留原字重/斜体）；textPaint 为列私有拷贝，无需还原
        if (fontPath.isNotEmpty()) {
            HighlightFontCache.getTypefaceFor(fontPath, textPaint.typeface)?.let {
                textPaint.typeface = it
            }
        }
        if (linkUrl != null) {
            textPaint.run {
                color = ReadBookConfig.textAccentColor
                isUnderlineText = true
                textSkewX = if (underlineMode == 7) -0.25f else 0f
            }
            drawText(view, canvas, y, textPaint)
            return
        }
        textPaint.run {
            color = if (textLine.isReadAloud || isSearchResult) {
                ReadBookConfig.textAccentColor
            } else {
                mTextColor ?: ReadBookConfig.textColor
            }
            isUnderlineText = false
            textSkewX = if (underlineMode == 7) -0.25f else 0f
        }
        drawText(view, canvas, y, textPaint)
    }

    private fun drawText(view: ContentTextView, canvas: Canvas, y: Float, textPaint: TextPaint) {
        if (charData == HR_PLACE_STR) {
            canvas.drawRect(start, 0f, end, 3f, textPaint)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val letterSpacing = textPaint.letterSpacing * textPaint.textSize
            val letterSpacingHalf = letterSpacing * 0.5f
            canvas.drawText(charData, start + letterSpacingHalf, y, textPaint)
        } else {
            canvas.drawText(charData, start, y, textPaint)
        }
        if (selected && !isSearchResult) {
            canvas.drawRect(start, 0f, end, textLine.height, view.selectedPaint)
        }
    }
}
