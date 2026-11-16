package io.legado.app.ui.design.theme

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import io.legado.app.help.config.NgThemeRuntimeAssets
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.SpanFactory
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock

/** Code spans own their paint, so TextView.typeface alone cannot theme them. */
internal class NgInterfaceFontMarkwonPlugin(context: Context) : AbstractMarkwonPlugin() {
    private val typeface = NgThemeRuntimeAssets.appTypeface(context)

    override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
        val font = typeface ?: return
        // Keep CorePlugin's relative code size, backgrounds and margins; change only the font.
        val factory = SpanFactory { _, _ -> InterfaceFontSpan(font) }
        builder.appendFactory(Code::class.java, factory)
        builder.appendFactory(FencedCodeBlock::class.java, factory)
        builder.appendFactory(IndentedCodeBlock::class.java, factory)
    }

    private class InterfaceFontSpan(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateMeasureState(textPaint: TextPaint) {
            textPaint.typeface = typeface
        }

        override fun updateDrawState(textPaint: TextPaint) {
            textPaint.typeface = typeface
        }
    }
}
