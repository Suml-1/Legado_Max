package io.legado.app.ui.book.read.config.highlight

import android.content.Context
import android.os.Build
import android.text.Layout
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * 高亮规则预览用的 TextView。
 *
 * 预览内容里的命中字距、行距会改变断行结果，必须在拿到实际可用宽度后再构建；
 * 因此这里把构建推迟到测量阶段，宽度变化或规则变化时重建一次文本。
 */
class HighlightPreviewTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatTextView(context, attrs) {

    private var rule: HighlightRule? = null
    private var previewColor = 0
    private var previewWidth = -1
    private var dirty = false

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 预览要能体现自定义留白，避免系统在字符间插入额外断行策略
            breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }
    }

    /** 设置预览规则；[textColor] 为规则未指定字色时的默认文字颜色 */
    fun setPreview(rule: HighlightRule, textColor: Int) {
        this.rule = rule.copy()
        previewColor = textColor
        dirty = true
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = (MeasureSpec.getSize(widthMeasureSpec) - compoundPaddingLeft - compoundPaddingRight)
            .coerceAtLeast(1)
        val rule = rule
        if (rule != null && (dirty || available != previewWidth)) {
            previewWidth = available
            dirty = false
            text = HighlightRulePreview.build(rule, previewColor, paint, available)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
