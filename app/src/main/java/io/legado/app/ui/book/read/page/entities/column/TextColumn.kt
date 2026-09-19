package io.legado.app.ui.book.read.page.entities.column

import android.graphics.Canvas
import android.os.Build
import androidx.annotation.Keep
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextLine.Companion.emptyTextLine
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.HighlightFontCache

/**
 * 文字列
 */
@Keep
data class TextColumn(
    override var start: Float,
    override var end: Float,
    override val charData: String,
    override val textColor: Int? = null,
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

    override var textLine: TextLine = emptyTextLine

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
        val textPaint = if (textLine.isTitle) {
            ChapterProvider.titlePaint
        } else {
            ChapterProvider.contentPaint
        }
        val drawColor = if (textLine.isReadAloud || isSearchResult) {
            ReadBookConfig.textAccentColor
        } else {
            textColor ?: ReadBookConfig.textColor
        }
        if (textPaint.color != drawColor) {
            textPaint.color = drawColor
        }
        val y = textLine.lineBase - textLine.lineTop
        // 高亮规则指定字体时临时替换画笔字体，绘制完立即还原，避免影响同行其他列；
        // 无条件保存/还原，避免依赖画笔原字体非空的隐含假设
        val oldTypeface = if (fontPath.isNotEmpty()) textPaint.typeface else null
        if (fontPath.isNotEmpty()) {
            HighlightFontCache.getTypefaceFor(fontPath, textPaint.typeface)?.let {
                textPaint.typeface = it
            }
        }
        if (underlineMode == 7) {
            val oldSkewX = textPaint.textSkewX
            textPaint.textSkewX = -0.25f
            drawTextInternal(canvas, textPaint, y)
            textPaint.textSkewX = oldSkewX
        } else {
            drawTextInternal(canvas, textPaint, y)
        }
        if (oldTypeface != null) {
            textPaint.typeface = oldTypeface
        }
        if (selected && !isSearchResult) {
            canvas.drawRect(start, 0f, end, textLine.height, view.selectedPaint)
        }
    }

    private fun drawTextInternal(canvas: Canvas, textPaint: android.text.TextPaint, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val letterSpacing = textPaint.letterSpacing * textPaint.textSize
            val letterSpacingHalf = letterSpacing * 0.5f
            canvas.drawText(charData, start + letterSpacingHalf, y, textPaint)
        } else {
            canvas.drawText(charData, start, y, textPaint)
        }
    }
}
