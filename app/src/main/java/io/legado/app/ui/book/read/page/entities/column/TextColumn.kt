package io.legado.app.ui.book.read.page.entities.column

import android.graphics.Canvas
import android.os.Build
import androidx.annotation.Keep
import io.legado.app.help.PaintPool
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
        // 优先取本行画笔：标题分段后的次行用缩放后的字号绘制，
        // 不能再按 isTitle 一律取首行标题画笔（测量与绘制必须同源）
        val srcPaint = textLine.lineTextPaint
            ?: if (textLine.isTitle) {
                ChapterProvider.titlePaint
            } else {
                ChapterProvider.contentPaint
            }
        val drawColor = if (textLine.isReadAloud || isSearchResult) {
            ReadBookConfig.textAccentColor
        } else {
            textColor ?: ReadBookConfig.textColor
        }
        // 用池化副本绘制：变细擦除要临时改 style/color/strokeWidth、高亮字体要临时换 typeface，
        // 而 titlePaint/contentPaint 会被后台预渲染线程（TextPageRender）并发使用，
        // 改共享画笔会让对侧在同一窗口里画出"变细/变色"的字
        val textPaint = PaintPool.obtain()
        textPaint.set(srcPaint)
        if (textPaint.color != drawColor) {
            textPaint.color = drawColor
        }
        // 高亮规则指定字体时替换画笔字体（副本用完即回收，无需还原）
        if (fontPath.isNotEmpty()) {
            HighlightFontCache.getTypefaceFor(fontPath, textPaint.typeface)?.let {
                textPaint.typeface = it
            }
        }
        val y = textLine.lineBase - textLine.lineTop
        if (underlineMode == 7) {
            textPaint.textSkewX = -0.25f
        }
        drawTextInternal(canvas, textPaint, y, eraseBgColor())
        PaintPool.recycle(textPaint)
        if (selected && !isSearchResult) {
            canvas.drawRect(start, 0f, end, textLine.height, view.selectedPaint)
        }
    }

    private fun drawTextInternal(canvas: Canvas, textPaint: android.graphics.Paint, y: Float, eraseBg: Int?) {
        val x = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val letterSpacing = textPaint.letterSpacing * textPaint.textSize
            start + letterSpacing * 0.5f
        } else {
            start
        }
        canvas.drawText(charData, x, y, textPaint)
        // 变细：用"字后面实际的颜色"擦掉字心边缘；drawThinStroke 内部会还原画笔
        ChapterProvider.drawThinStroke(canvas, textPaint, textLine.isTitle, charData, 0, charData.length, x, y, eraseBg)
    }
}
