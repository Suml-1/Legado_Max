package io.legado.app.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Build
import android.transition.Slide
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.help.config.NgThemeRuntimeAssets
import io.legado.app.ui.design.components.compose.NgGlassStyle
import io.legado.app.ui.design.components.compose.resolveNgFloatingGlassStyle
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import kotlin.math.roundToInt

data class NgActionPopupItem(
    val itemId: Int,
    val titleRes: Int = 0,
    val iconRes: Int = 0,
    val checked: Boolean = false,
    val dividerBefore: Boolean = false,
    val title: CharSequence? = null,
    val iconDrawable: Drawable? = null,
    val payload: Any? = null,
    val iconInsetDp: Int = 1,
    val hasSubMenu: Boolean = false
)

class NgActionPopup(
    context: Context,
    items: List<NgActionPopupItem>,
    private val widthDp: Int = 0,
    private val themeSnapshot: NgThemeSnapshot? = null,
    headerTitle: CharSequence? = null,
    onItemClick: (NgActionPopupItem) -> Unit
) : PopupWindow(
    resolveWidth(context, items, widthDp, headerTitle),
    ViewGroup.LayoutParams.WRAP_CONTENT
) {

    init {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 6.dpToPx(), 0, 6.dpToPx())
            background = GradientDrawable().apply {
                setColor(
                    themeSnapshot?.colors?.surfaceContainerHigh
                        ?: context.getCompatColor(R.color.ng_surface_card)
                )
                cornerRadius = 18.dpToPx().toFloat()
            }
        }
        headerTitle?.takeIf { it.isNotBlank() }?.let { title ->
            panel.addView(createHeader(context, title))
        }
        items.forEach { item ->
            if (item.dividerBefore) {
                panel.addView(createDivider(context))
            }
            panel.addView(createActionRow(context, item) {
                dismiss()
                onItemClick(item)
            })
        }
        contentView = panel
        isFocusable = true
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(0x00000000))
        elevation = 8.dpToPx().toFloat()
    }

    fun show(
        anchor: View,
        marginDp: Int = 8,
        verticalAnchorInsetDp: Int = 0
    ) {
        val margin = marginDp.dpToPx()
        val verticalAnchorInset = verticalAnchorInsetDp.dpToPx()
        val location = IntArray(2)
        val rootLocation = IntArray(2)
        anchor.getLocationOnScreen(location)
        anchor.rootView.getLocationOnScreen(rootLocation)
        val rootWidth = anchor.rootView.width
        val rootTop = 0
        val rootBottom = anchor.rootView.height
        val anchorLeft = location[0] - rootLocation[0]
        val anchorTop = location[1] - rootLocation[1]
        val maxX = (rootWidth - width - margin).coerceAtLeast(margin)
        val x = (anchorLeft + anchor.width - width).coerceIn(margin, maxX)
        contentView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupHeight = contentView.measuredHeight
        val visibleTop = anchorTop + verticalAnchorInset
        val visibleBottom = anchorTop + anchor.height - verticalAnchorInset
        val belowY = visibleBottom + margin
        val aboveY = visibleTop - popupHeight - margin
        val y = if (belowY + popupHeight > rootBottom - margin && aboveY >= rootTop + margin) {
            aboveY
        } else {
            belowY
                .coerceAtMost(rootBottom - popupHeight - margin)
                .coerceAtLeast(rootTop + margin)
        }
        showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, x, y)
    }

    fun show(
        anchorRoot: View,
        anchorBoundsInRoot: Rect,
        marginDp: Int = 8,
        verticalAnchorInsetDp: Int = 0
    ) {
        val margin = marginDp.dpToPx()
        val verticalAnchorInset = verticalAnchorInsetDp.dpToPx()
        val anchorRootLocation = IntArray(2)
        val windowRootLocation = IntArray(2)
        anchorRoot.getLocationOnScreen(anchorRootLocation)
        val windowRoot = anchorRoot.rootView
        windowRoot.getLocationOnScreen(windowRootLocation)
        val rootLeft = 0
        val rootTop = 0
        val rootRight = windowRoot.width
        val rootBottom = windowRoot.height
        val anchorOffsetX = anchorRootLocation[0] - windowRootLocation[0]
        val anchorOffsetY = anchorRootLocation[1] - windowRootLocation[1]
        val anchorTop = anchorOffsetY + anchorBoundsInRoot.top
        val anchorRight = anchorOffsetX + anchorBoundsInRoot.right
        val anchorBottom = anchorOffsetY + anchorBoundsInRoot.bottom
        val maxX = (rootRight - width - margin).coerceAtLeast(rootLeft + margin)
        val x = (anchorRight - width).coerceIn(rootLeft + margin, maxX)
        contentView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupHeight = contentView.measuredHeight
        val visibleTop = anchorTop + verticalAnchorInset
        val visibleBottom = anchorBottom - verticalAnchorInset
        val belowY = visibleBottom + margin
        val aboveY = visibleTop - popupHeight - margin
        val y = if (belowY + popupHeight > rootBottom - margin && aboveY >= rootTop + margin) {
            aboveY
        } else {
            belowY
                .coerceAtMost(rootBottom - popupHeight - margin)
                .coerceAtLeast(rootTop + margin)
        }
        showAtLocation(windowRoot, Gravity.NO_GRAVITY, x, y)
    }

    private fun createActionRow(
        context: Context,
        item: NgActionPopupItem,
        onClick: () -> Unit
    ): View {
        val color = themeSnapshot?.colors?.onSurface
            ?: context.getCompatColor(R.color.ng_on_surface)
        val textMaxWidth = (
            width - 12.dpToPx() - 20.dpToPx() - 10.dpToPx() - 12.dpToPx() -
                (if (item.checked || item.hasSubMenu) {
                    (TRAILING_INDICATOR_SIZE_DP + TRAILING_INDICATOR_MARGIN_DP).dpToPx()
                } else {
                    0
                })
            ).coerceAtLeast(0)
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(12.dpToPx(), 0, 12.dpToPx(), 0)
            minimumHeight = 44.dpToPx()
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(ImageView(context).apply {
                val drawable = item.iconDrawable ?: item.iconRes
                    .takeIf { it != 0 }
                    ?.let { ContextCompat.getDrawable(context, it) }
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(2.dpToPx(), 2.dpToPx(), 2.dpToPx(), 2.dpToPx())
                setImageDrawable(drawable?.mutate())
                setColorFilter(color)
                alpha = if (drawable == null) 0f else 1f
            }, LinearLayout.LayoutParams(20.dpToPx(), 20.dpToPx()).apply {
                marginEnd = 10.dpToPx()
            })
            addView(TextView(context).apply {
                NgThemeRuntimeAssets.applyAppTypeface(context, this)
                text = item.title ?: context.getString(item.titleRes)
                setTextColor(color)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                includeFontPadding = false
                maxLines = 1
                maxWidth = textMaxWidth
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            if (item.checked) {
                addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(2.dpToPx(), 2.dpToPx(), 2.dpToPx(), 2.dpToPx())
                    setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ng_ic_popup_selected))
                    setColorFilter(color)
                }, LinearLayout.LayoutParams(
                    TRAILING_INDICATOR_SIZE_DP.dpToPx(),
                    TRAILING_INDICATOR_SIZE_DP.dpToPx()
                ).apply {
                    marginStart = TRAILING_INDICATOR_MARGIN_DP.dpToPx()
                })
            } else if (item.hasSubMenu) {
                addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(2.dpToPx(), 2.dpToPx(), 2.dpToPx(), 2.dpToPx())
                    setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_arrow_right))
                    setColorFilter(color)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(
                    TRAILING_INDICATOR_SIZE_DP.dpToPx(),
                    TRAILING_INDICATOR_SIZE_DP.dpToPx()
                ).apply {
                    marginStart = TRAILING_INDICATOR_MARGIN_DP.dpToPx()
                })
            }
        }
    }

    private fun createHeader(context: Context, title: CharSequence): View {
        return TextView(context).apply {
            NgThemeRuntimeAssets.applyAppTypeface(context, this)
            text = title
            setTextColor(
                themeSnapshot?.colors?.onSurfaceVariant
                    ?: context.getCompatColor(R.color.ng_on_surface_variant)
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(12.dpToPx(), 0, 12.dpToPx(), 0)
            minimumHeight = 36.dpToPx()
        }
    }

    private fun createDivider(context: Context): View {
        return View(context).apply {
            setBackgroundColor(
                themeSnapshot?.colors?.outline
                    ?: context.getCompatColor(R.color.ng_outline)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                leftMargin = 12.dpToPx()
                rightMargin = 12.dpToPx()
                topMargin = 3.dpToPx()
                bottomMargin = 3.dpToPx()
            }
        }
    }

    companion object {
        private fun resolveWidth(
            context: Context,
            items: List<NgActionPopupItem>,
            widthDp: Int,
            headerTitle: CharSequence?
        ): Int {
            if (widthDp > 0) return widthDp.dpToPx()
            val textPaint = TextPaint().apply {
                typeface = NgThemeRuntimeAssets.appTypeface(context)
                textSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    16f,
                    context.resources.displayMetrics
                )
            }
            val rowBaseWidth = 12.dpToPx() + 20.dpToPx() + 10.dpToPx() + 12.dpToPx()
            val trailingIndicatorWidth =
                (TRAILING_INDICATOR_MARGIN_DP + TRAILING_INDICATOR_SIZE_DP).dpToPx()
            val itemWidth = items.maxOfOrNull { item ->
                textPaint.measureText(
                    item.title?.toString()
                        ?: item.titleRes.takeIf { it != 0 }?.let { context.getString(it) }
                        ?: ""
                ).toInt() + rowBaseWidth +
                    if (item.checked || item.hasSubMenu) trailingIndicatorWidth else 0
            } ?: 0
            val headerWidth = headerTitle
                ?.takeIf { it.isNotBlank() }
                ?.let { textPaint.measureText(it.toString()).roundToInt() + 24.dpToPx() }
                ?: 0
            val contentWidth = maxOf(itemWidth, headerWidth)
            val minWidth = 120.dpToPx()
            val maxWidth = (context.resources.displayMetrics.widthPixels - 16.dpToPx())
                .coerceAtMost(280.dpToPx())
                .coerceAtLeast(minWidth)
            return (contentWidth + 2.dpToPx()).coerceIn(minWidth, maxWidth)
        }

        private const val TRAILING_INDICATOR_SIZE_DP = 18
        private const val TRAILING_INDICATOR_MARGIN_DP = 6
    }
}

/**
 * 只承载少量高频动作的横向悬浮图标栏。
 *
 * 与纵向 [NgActionPopup] 分开，避免把无文字工具栏的尺寸和玻璃材质混入标准菜单。
 */
class NgIconActionPopup(
    context: Context,
    items: List<NgActionPopupItem>,
    themeSnapshot: NgThemeSnapshot,
    onItemClick: (NgActionPopupItem) -> Unit
) : PopupWindow(
    (items.size * ITEM_SIZE_DP + PANEL_HORIZONTAL_PADDING_DP * 2).dpToPx(),
    (ITEM_SIZE_DP + PANEL_VERTICAL_PADDING_DP * 2).dpToPx()
) {

    init {
        val glassStyle = resolveNgFloatingGlassStyle(themeSnapshot)
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(
                PANEL_HORIZONTAL_PADDING_DP.dpToPx(),
                PANEL_VERTICAL_PADDING_DP.dpToPx(),
                PANEL_HORIZONTAL_PADDING_DP.dpToPx(),
                PANEL_VERTICAL_PADDING_DP.dpToPx()
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    glassStyle.containerTop.toArgb(),
                    glassStyle.containerBottom.toArgb()
                )
            ).apply {
                cornerRadius = CORNER_RADIUS_DP.dpToPx().toFloat()
                setStroke(
                    glassStyle.borderWidth.value.dpToPx().roundToInt().coerceAtLeast(1),
                    glassStyle.borderColor.toArgb()
                )
            }
        }
        items.forEach { item ->
            panel.addView(createIconAction(context, item, themeSnapshot) {
                dismiss()
                onItemClick(item)
            })
        }
        contentView = panel
        isFocusable = true
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(0x00000000))
        elevation = glassStyle.shadowElevation.value.dpToPx()
    }

    fun show(anchor: View, marginDp: Int = 8) {
        val margin = marginDp.dpToPx()
        val anchorLocation = IntArray(2)
        val rootLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        anchor.rootView.getLocationOnScreen(rootLocation)
        val rootLeft = rootLocation[0]
        val rootTop = rootLocation[1]
        val rootRight = rootLeft + anchor.rootView.width
        val rootBottom = rootTop + anchor.rootView.height
        val x = (anchorLocation[0] + anchor.width - width).coerceIn(
            rootLeft + margin,
            (rootRight - width - margin).coerceAtLeast(rootLeft + margin)
        )
        val belowY = anchorLocation[1] + anchor.height + margin
        val aboveY = anchorLocation[1] - height - margin
        val y = if (belowY + height <= rootBottom - margin) {
            belowY
        } else {
            aboveY.coerceAtLeast(rootTop + margin)
        }
        showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, x, y)
    }

    private fun createIconAction(
        context: Context,
        item: NgActionPopupItem,
        themeSnapshot: NgThemeSnapshot,
        onClick: () -> Unit
    ): View {
        val title = item.title ?: item.titleRes
            .takeIf { it != 0 }
            ?.let(context::getString)
            .orEmpty()
        return ImageView(context).apply {
            val drawable = item.iconDrawable ?: item.iconRes
                .takeIf { it != 0 }
                ?.let { ContextCompat.getDrawable(context, it) }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(
                ICON_PADDING_DP.dpToPx(),
                ICON_PADDING_DP.dpToPx(),
                ICON_PADDING_DP.dpToPx(),
                ICON_PADDING_DP.dpToPx()
            )
            setImageDrawable(drawable?.mutate())
            setColorFilter(
                if (item.checked) themeSnapshot.colors.primary
                else themeSnapshot.colors.onSurface
            )
            contentDescription = title
            TooltipCompat.setTooltipText(this, title)
            if (item.checked) {
                val selectedContainer = GradientDrawable().apply {
                    cornerRadius = ACTION_CORNER_RADIUS_DP.dpToPx().toFloat()
                    setColor(themeSnapshot.colors.selectedContainer)
                }
                background = RippleDrawable(
                    ColorStateList.valueOf(
                        ColorUtils.withAlpha(themeSnapshot.colors.primary, SELECTED_RIPPLE_ALPHA)
                    ),
                    selectedContainer,
                    null
                )
            } else {
                val ripple = TypedValue()
                if (context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless,
                    ripple,
                    true
                )) {
                    setBackgroundResource(ripple.resourceId)
                }
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ITEM_SIZE_DP.dpToPx(),
                ITEM_SIZE_DP.dpToPx()
            )
        }
    }

    private companion object {
        const val ITEM_SIZE_DP = 44
        const val PANEL_HORIZONTAL_PADDING_DP = 4
        const val PANEL_VERTICAL_PADDING_DP = 0
        const val ICON_PADDING_DP = 12
        const val CORNER_RADIUS_DP = 12
        const val ACTION_CORNER_RADIUS_DP = 10
        const val SELECTED_RIPPLE_ALPHA = 0.18f
    }
}

/**
 * 阅读页专用的纵向悬浮工具菜单。
 *
 * 复用阅读页浮动工具的玻璃材质，但保留文字和勾选反馈；菜单本身只负责展示，
 * 具体动作仍交给原来的 MenuItem 回调处理。
 */
class NgReadingActionPopup(
    context: Context,
    items: List<NgActionPopupItem>,
    themeSnapshot: NgThemeSnapshot,
    glassStyle: NgGlassStyle = resolveNgFloatingGlassStyle(themeSnapshot),
    onItemClick: (NgActionPopupItem) -> Unit
) : PopupWindow(
    WIDTH_DP.dpToPx(),
    ViewGroup.LayoutParams.WRAP_CONTENT
) {

    init {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, PANEL_VERTICAL_PADDING_DP.dpToPx(), 0, PANEL_VERTICAL_PADDING_DP.dpToPx())
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    glassStyle.containerTop.toArgb(),
                    glassStyle.containerBottom.toArgb()
                )
            ).apply {
                cornerRadius = CORNER_RADIUS_DP.dpToPx().toFloat()
                setStroke(
                    glassStyle.borderWidth.value.dpToPx().roundToInt().coerceAtLeast(1),
                    glassStyle.borderColor.toArgb()
                )
            }
        }
        items.forEach { item ->
            if (item.dividerBefore) {
                panel.addView(createDivider(context, themeSnapshot))
            }
            panel.addView(createActionRow(context, item, themeSnapshot) {
                dismiss()
                onItemClick(item)
            })
        }
        contentView = ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                panel,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        isFocusable = true
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(0x00000000))
        elevation = glassStyle.shadowElevation.value.dpToPx()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && themeSnapshot.motion.enabled) {
            enterTransition = Slide(Gravity.END).apply {
                duration = themeSnapshot.motion.shortDurationMs.toLong()
            }
            exitTransition = Slide(Gravity.END).apply {
                duration = themeSnapshot.motion.shortDurationMs.toLong()
            }
        }
    }

    fun show(
        anchor: View,
        topBoundaryViewId: Int = R.id.title_bar_container,
        bottomBoundaryViewId: Int = R.id.bottom_glass_container,
        marginDp: Int = SCREEN_MARGIN_DP
    ) {
        val root = anchor.rootView
        val margin = marginDp.dpToPx()
        val rootLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        val rootLeft = rootLocation[0]
        val rootTop = rootLocation[1]
        val rootRight = rootLeft + root.width
        val rootBottom = rootTop + root.height

        val topBoundary = root.findViewById<View>(topBoundaryViewId)
        val bottomBoundary = root.findViewById<View>(bottomBoundaryViewId)
        val availableTop = topBoundary?.screenBottom()?.plus(margin) ?: (rootTop + margin)
        val availableBottom = bottomBoundary?.screenTop()?.minus(margin) ?: (rootBottom - margin)
        val availableHeight = (availableBottom - availableTop).coerceAtLeast(ROW_HEIGHT_DP.dpToPx())

        contentView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        height = contentView.measuredHeight.coerceAtMost(availableHeight)
        val x = (rootRight - width - margin).coerceAtLeast(rootLeft + margin)
        val y = availableTop + ((availableHeight - height) / 2).coerceAtLeast(0)
        showAtLocation(root, Gravity.NO_GRAVITY, x, y)
    }

    private fun createActionRow(
        context: Context,
        item: NgActionPopupItem,
        themeSnapshot: NgThemeSnapshot,
        onClick: () -> Unit
    ): View {
        val contentColor = themeSnapshot.colors.onSurface
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(ROW_HORIZONTAL_PADDING_DP.dpToPx(), 0, ROW_HORIZONTAL_PADDING_DP.dpToPx(), 0)
            isClickable = true
            isFocusable = true
            val ripple = TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
                setBackgroundResource(ripple.resourceId)
            }
            setOnClickListener { onClick() }

            addView(ImageView(context).apply {
                val drawable = item.iconDrawable ?: item.iconRes
                    .takeIf { it != 0 }
                    ?.let { ContextCompat.getDrawable(context, it) }
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val iconInset = item.iconInsetDp.dpToPx()
                setPadding(iconInset, iconInset, iconInset, iconInset)
                setImageDrawable(drawable?.mutate())
                setColorFilter(contentColor)
                alpha = if (drawable == null) 0f else 1f
            }, LinearLayout.LayoutParams(ICON_SIZE_DP.dpToPx(), ICON_SIZE_DP.dpToPx()).apply {
                marginEnd = ICON_TEXT_GAP_DP.dpToPx()
            })

            addView(TextView(context).apply {
                NgThemeRuntimeAssets.applyAppTypeface(context, this)
                text = item.title ?: item.titleRes
                    .takeIf { it != 0 }
                    ?.let(context::getString)
                    .orEmpty()
                setTextColor(contentColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP)
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ng_ic_popup_selected))
                setColorFilter(themeSnapshot.colors.primary)
                alpha = if (item.checked) 1f else 0f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(SELECTED_SIZE_DP.dpToPx(), SELECTED_SIZE_DP.dpToPx()).apply {
                marginStart = SELECTED_GAP_DP.dpToPx()
            })

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ROW_HEIGHT_DP.dpToPx()
            )
        }
    }

    private fun createDivider(context: Context, themeSnapshot: NgThemeSnapshot): View {
        return View(context).apply {
            setBackgroundColor(
                ColorUtils.withAlpha(themeSnapshot.colors.outlineVariant, DIVIDER_ALPHA)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                leftMargin = ROW_HORIZONTAL_PADDING_DP.dpToPx()
                rightMargin = ROW_HORIZONTAL_PADDING_DP.dpToPx()
                topMargin = DIVIDER_VERTICAL_MARGIN_DP.dpToPx()
                bottomMargin = DIVIDER_VERTICAL_MARGIN_DP.dpToPx()
            }
        }
    }

    private fun View.screenTop(): Int {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return location[1]
    }

    private fun View.screenBottom(): Int = screenTop() + height

    private companion object {
        const val WIDTH_DP = 156
        const val SCREEN_MARGIN_DP = 8
        const val CORNER_RADIUS_DP = 12
        const val PANEL_VERTICAL_PADDING_DP = 4
        const val ROW_HEIGHT_DP = 44
        const val ROW_HORIZONTAL_PADDING_DP = 10
        const val ICON_SIZE_DP = 20
        const val ICON_TEXT_GAP_DP = 8
        const val TEXT_SIZE_SP = 14f
        const val SELECTED_SIZE_DP = 14
        const val SELECTED_GAP_DP = 6
        const val DIVIDER_VERTICAL_MARGIN_DP = 4
        const val DIVIDER_ALPHA = 0.36f
    }
}
