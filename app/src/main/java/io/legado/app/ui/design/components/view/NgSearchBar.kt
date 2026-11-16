package io.legado.app.ui.design.components.view

import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import io.legado.app.help.config.NgThemeRuntimeAssets
import io.legado.app.R

/**
 * Reading NG 的统一搜索框。页面和抽屉只负责提供 hint 与查询监听，
 * 图标、清除动作、字号、圆角和留白由组件统一维护。
 */
class NgSearchBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    val editText = AppCompatEditText(context).also { NgThemeRuntimeAssets.applyAppTypeface(context, it) }
    private val clearButton = AppCompatImageButton(context)
    private var hintText: CharSequence? = null

    var hint: CharSequence?
        get() = hintText
        set(value) {
            hintText = value
            updateHint()
        }

    val query: String
        get() = editText.text?.toString().orEmpty()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = 44.dp
        setPadding(16.dp, 0, 8.dp, 0)
        setBackgroundResource(R.drawable.ng_bg_search_pill)

        val searchIcon = AppCompatImageView(context).apply {
            setImageResource(R.drawable.ic_search)
            contentDescription = context.getString(R.string.search)
            ImageViewCompat.setImageTintList(
                this,
                ContextCompat.getColorStateList(context, R.color.ng_search_icon)
            )
        }
        addView(searchIcon, LayoutParams(22.dp, 22.dp))

        val hintAttributes = context.obtainStyledAttributes(
            attrs,
            intArrayOf(android.R.attr.hint),
            defStyleAttr,
            0
        )
        val styledHint = hintAttributes.getText(0)
        hintAttributes.recycle()
        editText.apply {
            background = null
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(ContextCompat.getColor(context, R.color.ng_search_content))
            setHintTextColor(ContextCompat.getColor(context, R.color.ng_search_hint))
            textSize = 15f
            setPadding(0, 0, 0, 0)
            setOnFocusChangeListener { _, _ -> updateHint() }
        }
        hint = styledHint
        addView(
            editText,
            LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = 10.dp
            }
        )

        clearButton.apply {
            setImageResource(R.drawable.ic_baseline_close)
            background = null
            contentDescription = context.getString(R.string.clear)
            setPadding(9.dp, 9.dp, 9.dp, 9.dp)
            isVisible = false
            ImageViewCompat.setImageTintList(
                this,
                ContextCompat.getColorStateList(context, R.color.ng_search_hint)
            )
            setOnClickListener {
                editText.setText("")
                editText.requestFocus()
            }
        }
        addView(clearButton, LayoutParams(38.dp, 38.dp))

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                clearButton.isVisible = !s.isNullOrEmpty()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    fun setQuery(query: CharSequence?) {
        editText.setText(query)
        editText.setSelection(editText.text?.length ?: 0)
    }

    fun setContainerColor(@ColorInt color: Int) {
        backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun updateHint() {
        editText.hint = if (editText.hasFocus()) null else hintText
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density + 0.5f).toInt()
}
