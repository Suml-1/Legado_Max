package io.legado.app.ui.design.components.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import io.legado.app.R
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor

/** 白色高不透明表面、强调色描边与强调文字的 NG 次按钮。 */
class NgSecondaryButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        val styledAttributes = context.obtainStyledAttributes(
            attrs,
            R.styleable.StrokeTextView,
            defStyleAttr,
            0
        )
        val radius = styledAttributes.getDimensionPixelOffset(
            R.styleable.StrokeTextView_radius,
            12.dpToPx()
        )
        styledAttributes.recycle()

        val accent = if (isInEditMode) {
            context.getCompatColor(R.color.accent)
        } else {
            ThemeStore.accentColor(context)
        }
        val disabled = context.getCompatColor(R.color.disabled)
        val white = context.getCompatColor(R.color.white)
        background = Selector.shapeBuild()
            .setCornerRadius(radius)
            .setStrokeWidth(1.dpToPx())
            .setDefaultStrokeColor(accent)
            .setDisabledStrokeColor(disabled)
            .setDefaultBgColor(ColorUtils.withAlpha(white, 0.86f))
            .setDisabledBgColor(ColorUtils.withAlpha(white, 0.58f))
            .setPressedBgColor(ColorUtils.withAlpha(white, 0.96f))
            .create()
        setTextColor(
            Selector.colorBuild()
                .setDefaultColor(accent)
                .setDisabledColor(disabled)
                .create()
        )
    }
}
