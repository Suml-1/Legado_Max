package io.legado.app.ui.design.components.view

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.ui.design.components.NgManagementTrailing
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.NgStatusTagStyle

/**
 * 管理型列表的公共卡片骨架。
 *
 * 业务层只提供图标、标题、摘要、状态标签和尾部语义；卡片的尺寸、留白、
 * 标签间距和点击区域由组件维护。标题旁标签与标题下状态标签分开承载，
 * 避免 Provider/TTS 与 Skill 为了复用而互相改变信息层级。
 */
class NgManagementListCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val selectionIndicator: View
    private val leadingHost: FrameLayout
    private val leadingImage: AppCompatImageView
    private val leadingText: AppCompatTextView
    private val titleView: AppCompatTextView
    private val headerTags: LinearLayout
    private val detailTags: LinearLayout
    private val summaryView: AppCompatTextView
    private val trailingView: AppCompatImageView

    val leadingActionView: View
        get() = leadingHost

    val trailingActionView: ImageView
        get() = trailingView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        LayoutInflater.from(context).inflate(
            R.layout.view_ng_management_list_card_content,
            this,
            true
        )
        selectionIndicator = findViewById(R.id.ng_management_selection_indicator)
        leadingHost = findViewById(R.id.ng_management_leading)
        leadingImage = findViewById(R.id.ng_management_icon)
        leadingText = findViewById(R.id.ng_management_icon_text)
        titleView = findViewById(R.id.ng_management_title)
        headerTags = findViewById(R.id.ng_management_header_tags)
        detailTags = findViewById(R.id.ng_management_detail_tags)
        summaryView = findViewById(R.id.ng_management_summary)
        trailingView = findViewById(R.id.ng_management_trailing)
    }

    fun setLeadingImage(
        @DrawableRes iconRes: Int,
        contentDescription: CharSequence? = null,
        @ColorInt tint: Int? = null
    ) {
        leadingText.isVisible = false
        leadingImage.isVisible = true
        leadingImage.setImageResource(iconRes)
        leadingImage.contentDescription = contentDescription
        leadingImage.imageTintList = tint?.let(ColorStateList::valueOf)
    }

    fun setLeadingText(value: CharSequence, contentDescription: CharSequence? = null) {
        leadingImage.isVisible = false
        leadingImage.setImageDrawable(null)
        leadingText.isVisible = true
        leadingText.text = value
        leadingText.contentDescription = contentDescription
    }

    fun setTitle(value: CharSequence) {
        titleView.text = value
        contentDescription = value
    }

    fun setSummary(value: CharSequence?) {
        summaryView.text = value
        summaryView.isVisible = !value.isNullOrBlank()
    }

    fun setHeaderTags(tags: List<NgStatusTagSpec>) {
        bindTags(headerTags, tags)
    }

    fun setDetailTags(tags: List<NgStatusTagSpec>) {
        bindTags(detailTags, tags)
    }

    fun setTrailing(
        trailing: NgManagementTrailing,
        contentDescription: CharSequence? = null
    ) {
        trailingView.isVisible = trailing != NgManagementTrailing.NONE
        if (trailing == NgManagementTrailing.NONE) {
            trailingView.setImageDrawable(null)
            trailingView.contentDescription = null
            return
        }
        trailingView.setImageResource(
            when (trailing) {
                NgManagementTrailing.DRAG -> R.drawable.ic_drag_handle
                NgManagementTrailing.MORE -> R.drawable.ic_more_vert
                NgManagementTrailing.NONE -> error("Handled above")
            }
        )
        trailingView.contentDescription = contentDescription
        val verticalPadding = if (trailing == NgManagementTrailing.DRAG) 20.dp else 9.dp
        trailingView.setPadding(8.dp, verticalPadding, 4.dp, verticalPadding)
        trailingView.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, R.color.ng_on_surface)
        )
    }

    fun setSelectionIndicatorVisible(visible: Boolean, @ColorInt color: Int? = null) {
        selectionIndicator.isVisible = visible
        selectionIndicator.backgroundTintList = color?.let(ColorStateList::valueOf)
    }

    private fun bindTags(container: LinearLayout, tags: List<NgStatusTagSpec>) {
        container.removeAllViews()
        container.isVisible = tags.isNotEmpty()
        tags.forEachIndexed { index, tag ->
            container.addView(
                NgStatusTagView(context).apply {
                    bind(tag.text, tag.variant, tag.style)
                },
                LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    when (tag.style) {
                        NgStatusTagStyle.INLINE -> 18.dp
                        NgStatusTagStyle.COMPACT -> 20.dp
                        NgStatusTagStyle.REGULAR,
                        NgStatusTagStyle.TTS_ROLE -> 24.dp
                    }
                ).apply {
                    if (index > 0) {
                        marginStart = when (tag.style) {
                            NgStatusTagStyle.INLINE -> 4.dp
                            NgStatusTagStyle.COMPACT -> 6.dp
                            NgStatusTagStyle.REGULAR,
                            NgStatusTagStyle.TTS_ROLE -> 8.dp
                        }
                    }
                }
            )
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density + 0.5f).toInt()
}
