package io.legado.app.ui.design.components.view

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.setPadding
import androidx.core.widget.ImageViewCompat
import io.legado.app.R
import io.legado.app.help.config.NgThemeRuntimeAssets
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.utils.applyTint

class NgSettingsItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val leadingView = AppCompatImageView(context)
    private val titleView = AppCompatTextView(context).also { NgThemeRuntimeAssets.applyAppTypeface(context, it) }
    private val summaryView = AppCompatTextView(context).also { NgThemeRuntimeAssets.applyAppTypeface(context, it) }
    private val contentView = LinearLayout(context)
    private val trailingHost = FrameLayout(context)
    private val chevronView = AppCompatTextView(context).also { NgThemeRuntimeAssets.applyAppTypeface(context, it) }
    private val switchView = UserSwitchCompat(context)
    private val valueView = AppCompatTextView(context).also { NgThemeRuntimeAssets.applyAppTypeface(context, it) }
    private var appliedSnapshot: NgThemeSnapshot? = null

    var trailing: NgSettingsTrailing = NgSettingsTrailing.CHEVRON
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = 64.dp
        setPadding(16.dp, 10.dp, 14.dp, 10.dp)
        setBackgroundResource(R.drawable.ng_bg_settings_item)
        isClickable = true
        isFocusable = true

        leadingView.apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(7.dp)
            setBackgroundResource(R.drawable.ng_bg_settings_icon)
            contentDescription = null
        }
        addView(leadingView, LayoutParams(36.dp, 36.dp))

        contentView.orientation = VERTICAL
        contentView.gravity = Gravity.CENTER_VERTICAL
        titleView.apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        summaryView.apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        contentView.addView(
            titleView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
        contentView.addView(
            summaryView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = 4.dp
            }
        )
        addView(
            contentView,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 14.dp
            }
        )
        addView(
            trailingHost,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = 8.dp
            }
        )

        chevronView.apply {
            text = "›"
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
        }
        valueView.apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }

        val values = context.obtainStyledAttributes(
            attrs,
            R.styleable.NgSettingsItemView,
            defStyleAttr,
            0
        )
        setTitle(values.getText(R.styleable.NgSettingsItemView_ngTitle))
        setSummary(values.getText(R.styleable.NgSettingsItemView_ngSummary))
        setLeadingIcon(values.getResourceId(R.styleable.NgSettingsItemView_ngLeadingIcon, 0))
        setValue(values.getText(R.styleable.NgSettingsItemView_ngValue))
        setTrailing(
            when (values.getInt(R.styleable.NgSettingsItemView_ngTrailingType, 1)) {
                0 -> NgSettingsTrailing.NONE
                1 -> NgSettingsTrailing.CHEVRON
                2 -> NgSettingsTrailing.SWITCH
                3 -> NgSettingsTrailing.VALUE
                else -> NgSettingsTrailing.CHEVRON
            }
        )
        values.recycle()

        foreground = context.selectableItemBackground()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyNgTheme(appliedSnapshot ?: NgThemeResolver.resolve(context))
    }

    fun setTitle(value: CharSequence?) {
        titleView.text = value
        contentDescription = value
    }

    fun setSummary(value: CharSequence?) {
        summaryView.text = value
        summaryView.isGone = value.isNullOrBlank()
    }

    fun setLeadingIcon(@DrawableRes resId: Int) {
        if (resId == 0) {
            leadingView.isGone = true
        } else {
            leadingView.isGone = false
            leadingView.setImageResource(resId)
        }
    }

    fun setValue(value: CharSequence?) {
        valueView.text = value
    }

    fun setTrailing(value: NgSettingsTrailing) {
        trailing = value
        trailingHost.removeAllViews()
        val child = when (value) {
            NgSettingsTrailing.NONE,
            NgSettingsTrailing.CUSTOM -> null
            NgSettingsTrailing.CHEVRON -> chevronView
            NgSettingsTrailing.SWITCH -> switchView
            NgSettingsTrailing.VALUE -> valueView
        }
        child?.let {
            trailingHost.addView(
                it,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }
    }

    fun setCustomTrailing(view: View?) {
        trailing = NgSettingsTrailing.CUSTOM
        trailingHost.removeAllViews()
        view?.let { trailingHost.addView(it) }
    }

    fun setChecked(checked: Boolean) {
        switchView.isChecked = checked
    }

    fun isChecked(): Boolean = switchView.isChecked

    fun performSwitchClick(): Boolean = switchView.performClick()

    fun setOnUserCheckedChangeListener(listener: ((Boolean) -> Unit)?) {
        switchView.setOnUserCheckedChangeListener(listener)
    }

    fun applyNgTheme(snapshot: NgThemeSnapshot = NgThemeResolver.resolve(context)) {
        appliedSnapshot = snapshot
        val colors = snapshot.colors
        titleView.apply {
            setTextColor(ContextCompat.getColor(context, R.color.ng_settings_title))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, snapshot.typography.itemTitleSp.toFloat())
        }
        summaryView.apply {
            setTextColor(ContextCompat.getColor(context, R.color.ng_settings_summary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, snapshot.typography.summarySp.toFloat())
        }
        valueView.apply {
            setTextColor(colors.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, snapshot.typography.bodySp.toFloat())
        }
        ImageViewCompat.setImageTintList(
            leadingView,
            android.content.res.ColorStateList.valueOf(colors.primary)
        )
        chevronView.setTextColor(ContextCompat.getColor(context, R.color.ng_settings_arrow))
        switchView.applyTint(colors.primary, snapshot.isDark)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}

private class UserSwitchCompat(context: Context) : SwitchCompat(context) {

    private var isUserAction = false

    override fun performClick(): Boolean {
        isUserAction = true
        val handled = super.performClick()
        isUserAction = false
        return handled
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) isUserAction = true
        val handled = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            isUserAction = false
        }
        return handled
    }

    fun setOnUserCheckedChangeListener(listener: ((Boolean) -> Unit)?) {
        if (listener == null) {
            setOnCheckedChangeListener(null)
        } else {
            setOnCheckedChangeListener { _, checked ->
                if (isUserAction) listener(checked)
            }
        }
    }
}

private fun Context.selectableItemBackground(): android.graphics.drawable.Drawable? {
    val value = TypedValue()
    theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
    return value.resourceId.takeIf { it != 0 }?.let(::getDrawable)
}
