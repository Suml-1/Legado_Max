package io.legado.app.ui.design.components.compose

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Region
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.DrawableUtils
import androidx.core.graphics.drawable.DrawableCompat
import io.legado.app.lib.theme.TintHelper
import kotlin.math.max

/**
 * Keeps the existing AppCompat switch assets and geometry. Only the native ripple
 * needs a View background RenderNode; Compose still owns the control itself.
 *
 * The geometry follows SwitchCompat 1.7.1 with showText=false, including the optical
 * insets and thumb shadow. Compact controls keep that geometry inside their old
 * constrained bounds; their 0.82 scale belongs to the Compose modifier.
 */
@SuppressLint("RestrictedApi")
internal class NgSwitchArtwork(
    private val context: Context,
    compact: Boolean,
) : Drawable.Callback {
    private var thumb: Drawable?
    private var track: Drawable?
    private val background: Drawable?
    private var rippleHost: NgSwitchRippleHost? = null
    private val splitTrack: Boolean
    private val gravity: Int
    private val paddingLeft: Int
    private val paddingTop: Int
    private val paddingRight: Int
    private val paddingBottom: Int
    private val thumbPadding = Rect()
    private val trackPadding = Rect()
    private val thumbInsets = Rect()
    private val clipBounds = Rect()
    private val switchWidth: Int
    private val switchHeight: Int
    private val thumbWidth: Int
    private var switchLeft = 0
    private var switchTop = 0
    private var switchRight = 0
    private var switchBottom = 0
    private var thumbOffset = 0
    private var drawableState = intArrayOf(android.R.attr.state_enabled)
    private var stateFlags = -1
    private var primary: Int? = null
    private var dark: Boolean? = null
    private val handler = Handler(Looper.getMainLooper())
    private var onInvalidate: (() -> Unit)? = null

    val preferredWidth: Int
    val preferredHeight: Int
    val scrollRange: Int

    init {
        val attributes = context.obtainStyledAttributes(
            null, STYLE_ATTRIBUTES, androidx.appcompat.R.attr.switchStyle, 0
        )
        try {
            thumb = attributes.compatDrawable(THUMB)
            track = attributes.compatDrawable(TRACK)
            background = attributes.compatDrawable(BACKGROUND)
            splitTrack = attributes.getBoolean(SPLIT_TRACK, false)
            gravity = attributes.getInt(GRAVITY, Gravity.TOP or Gravity.START)
            val padding = attributes.getDimensionPixelSize(PADDING, 0)
            paddingLeft = if (compact) 0 else attributes.getDimensionPixelSize(PADDING_LEFT, padding)
            paddingTop = if (compact) 0 else attributes.getDimensionPixelSize(PADDING_TOP, padding)
            paddingRight = if (compact) 0 else attributes.getDimensionPixelSize(PADDING_RIGHT, padding)
            paddingBottom = if (compact) 0 else attributes.getDimensionPixelSize(PADDING_BOTTOM, padding)

            thumb?.getPadding(thumbPadding)
            track?.getPadding(trackPadding)
            thumb?.let { thumbInsets.set(DrawableUtils.getOpticalBounds(it)) }
            thumbWidth = max(0, (thumb?.intrinsicWidth ?: 0) - thumbPadding.left - thumbPadding.right)
            switchWidth = max(
                attributes.getDimensionPixelSize(SWITCH_MIN_WIDTH, 0),
                2 * thumbWidth + max(trackPadding.left, thumbInsets.left) +
                    max(trackPadding.right, thumbInsets.right)
            )
            switchHeight = max(thumb?.intrinsicHeight ?: 0, track?.intrinsicHeight ?: 0)
            scrollRange = if (track == null) 0 else {
                switchWidth - thumbWidth - trackPadding.left - trackPadding.right -
                    thumbInsets.left - thumbInsets.right
            }
            preferredWidth = max(
                switchWidth + paddingLeft + paddingRight,
                max(
                    if (compact) 0 else attributes.getDimensionPixelSize(MIN_WIDTH, 0),
                    background?.minimumWidth ?: 0
                )
            )
            preferredHeight = max(
                switchHeight,
                max(
                    emptyTextHeight(attributes) + paddingTop + paddingBottom,
                    max(attributes.getDimensionPixelSize(MIN_HEIGHT, 0), background?.minimumHeight ?: 0)
                )
            )
        } finally {
            attributes.recycle()
        }
    }

    fun attach(owner: View, onInvalidate: () -> Unit) {
        this.onInvalidate = onInvalidate
        thumb?.callback = this
        track?.callback = this
        rippleHost = background?.let { NgSwitchRippleHost.attach(owner, it, onInvalidate) }
        applyState()
    }

    fun dispose() {
        onInvalidate = null
        rippleHost?.dispose()
        rippleHost = null
        handler.removeCallbacksAndMessages(this)
        thumb?.callback = null
        track?.callback = null
        thumb?.jumpToCurrentState()
        track?.jumpToCurrentState()
    }

    fun updateTint(primary: Int, isDark: Boolean) {
        if (this.primary == primary && dark == isDark) return
        this.primary = primary
        dark = isDark
        thumb = thumb?.let { TintHelper.tintSwitchDrawable(context, it, primary, true, isDark) }
        track = track?.let { TintHelper.tintSwitchDrawable(context, it, primary, false, isDark) }
        TintHelper.tintControlRipple(context, background, primary, isDark)
        thumb?.callback = if (onInvalidate == null) null else this
        track?.callback = if (onInvalidate == null) null else this
        applyState()
    }

    fun updateState(
        enabled: Boolean,
        checked: Boolean,
        pressed: Boolean,
        focused: Boolean,
        hovered: Boolean,
    ) {
        val flags = (if (enabled) 1 else 0) or (if (checked) 2 else 0) or
            (if (pressed) 4 else 0) or (if (focused) 8 else 0) or (if (hovered) 16 else 0)
        if (stateFlags == flags) return
        stateFlags = flags
        val state = intArrayOf(
            if (enabled) android.R.attr.state_enabled else -android.R.attr.state_enabled,
            if (checked) android.R.attr.state_checked else -android.R.attr.state_checked,
            if (pressed) android.R.attr.state_pressed else -android.R.attr.state_pressed,
            if (focused) android.R.attr.state_focused else -android.R.attr.state_focused,
            if (hovered) android.R.attr.state_hovered else -android.R.attr.state_hovered,
        )
        drawableState = state
        applyState()
    }

    private fun applyState() {
        thumb?.state = drawableState
        track?.state = drawableState
        background?.state = drawableState
        onInvalidate?.invoke()
    }

    fun setHotspot(x: Float, y: Float) {
        thumb?.let { DrawableCompat.setHotspot(it, x, y) }
        track?.let { DrawableCompat.setHotspot(it, x, y) }
        background?.let { DrawableCompat.setHotspot(it, x, y) }
    }

    fun layout(width: Int, height: Int, isRtl: Boolean, position: Float) {
        val direction = if (isRtl) 1 else 0
        thumb?.let { DrawableCompat.setLayoutDirection(it, direction) }
        track?.let { DrawableCompat.setLayoutDirection(it, direction) }
        background?.let { DrawableCompat.setLayoutDirection(it, direction) }
        val insetLeft = max(0, thumbInsets.left - trackPadding.left)
        val insetRight = max(0, thumbInsets.right - trackPadding.right)
        if (isRtl) {
            switchLeft = paddingLeft + insetLeft
            switchRight = switchLeft + switchWidth - insetLeft - insetRight
        } else {
            switchRight = width - paddingRight - insetRight
            switchLeft = switchRight - switchWidth + insetLeft + insetRight
        }
        switchTop = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.CENTER_VERTICAL -> (paddingTop + height - paddingBottom) / 2 - switchHeight / 2
            Gravity.BOTTOM -> height - paddingBottom - switchHeight
            else -> paddingTop
        }
        switchBottom = switchTop + switchHeight
        val logicalPosition = if (isRtl) 1f - position else position
        thumbOffset = (logicalPosition * scrollRange + 0.5f).toInt()
        val thumbInitialLeft = switchLeft + thumbOffset + if (track == null) 0 else trackPadding.left
        track?.setBounds(
            switchLeft + max(0, thumbInsets.left - trackPadding.left),
            switchTop + max(0, thumbInsets.top - trackPadding.top),
            switchRight - max(0, thumbInsets.right - trackPadding.right),
            switchBottom - max(0, thumbInsets.bottom - trackPadding.bottom),
        )
        background?.setBounds(0, 0, width, height)
        thumb?.let {
            val left = thumbInitialLeft - thumbPadding.left
            val right = thumbInitialLeft + thumbWidth + thumbPadding.right
            it.setBounds(left, switchTop, right, switchBottom)
            background?.let { bg ->
                DrawableCompat.setHotspotBounds(bg, left, switchTop, right, switchBottom)
            }
        }
    }

    fun hitThumb(x: Float, y: Float, touchSlop: Float): Boolean {
        if (thumb == null) return false
        // SwitchCompat deliberately uses its switch origin here, before track padding.
        val left = switchLeft + thumbOffset - touchSlop
        val right = left + thumbWidth + thumbPadding.left + thumbPadding.right + touchSlop
        return x > left && x < right && y > switchTop - touchSlop && y < switchBottom + touchSlop
    }

    @Suppress("DEPRECATION")
    fun draw(canvas: Canvas) {
        background?.let {
            rippleHost?.drawRipple(canvas, it.bounds.width(), it.bounds.height(), drawableState)
        }
        track?.let {
            if (splitTrack && thumb != null) {
                thumb!!.copyBounds(clipBounds)
                clipBounds.left += thumbInsets.left
                clipBounds.right -= thumbInsets.right
                val saveCount = canvas.save()
                canvas.clipRect(clipBounds, Region.Op.DIFFERENCE)
                it.draw(canvas)
                canvas.restoreToCount(saveCount)
            } else {
                it.draw(canvas)
            }
        }
        thumb?.draw(canvas)
    }

    override fun invalidateDrawable(who: Drawable) {
        onInvalidate?.invoke()
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        if (onInvalidate != null) handler.postAtTime(what, this, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        handler.removeCallbacks(what, this)
    }

    private fun TypedArray.compatDrawable(index: Int): Drawable? {
        val resource = getResourceId(index, 0)
        return (if (resource != 0) AppCompatResources.getDrawable(context, resource) else getDrawable(index))
            ?.mutate()
    }

    @Suppress("DEPRECATION")
    private fun emptyTextHeight(attributes: TypedArray): Int {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.density = context.resources.displayMetrics.density
        var size = 15f * context.resources.displayMetrics.scaledDensity
        var style = Typeface.NORMAL
        var family: String? = null
        var typeface = 1
        val appearanceId = attributes.getResourceId(TEXT_APPEARANCE, 0)
        if (appearanceId != 0) {
            val appearance = context.obtainStyledAttributes(appearanceId, TEXT_ATTRIBUTES)
            try {
                size = appearance.getDimension(0, size)
                typeface = appearance.getInt(1, typeface)
                style = appearance.getInt(2, style)
                family = appearance.getString(3)
            } finally {
                appearance.recycle()
            }
        }
        size = attributes.getDimension(TEXT_SIZE, size)
        style = attributes.getInt(TEXT_STYLE, style)
        if (attributes.hasValue(FONT_FAMILY)) family = attributes.getString(FONT_FAMILY)
        typeface = attributes.getInt(TYPEFACE, typeface)
        paint.textSize = size
        paint.typeface = if (family != null) Typeface.create(family, style) else Typeface.create(
            when (typeface) {
                2 -> Typeface.SERIF
                3 -> Typeface.MONOSPACE
                else -> Typeface.SANS_SERIF
            }, style
        )
        return StaticLayout(
            "", paint, 1, Layout.Alignment.ALIGN_NORMAL, 1f, 0f,
            attributes.getBoolean(INCLUDE_FONT_PADDING, true)
        ).height
    }

    private companion object {
        val STYLE_ATTRIBUTES = intArrayOf(
            android.R.attr.thumb,
            androidx.appcompat.R.attr.track,
            android.R.attr.background,
            androidx.appcompat.R.attr.splitTrack,
            androidx.appcompat.R.attr.switchMinWidth,
            android.R.attr.gravity,
            android.R.attr.padding,
            android.R.attr.paddingLeft,
            android.R.attr.paddingTop,
            android.R.attr.paddingRight,
            android.R.attr.paddingBottom,
            android.R.attr.minWidth,
            android.R.attr.minHeight,
            android.R.attr.textAppearance,
            android.R.attr.textSize,
            android.R.attr.textStyle,
            android.R.attr.fontFamily,
            android.R.attr.typeface,
            android.R.attr.includeFontPadding,
        ).sortedArray()
        val THUMB = STYLE_ATTRIBUTES.indexOf(android.R.attr.thumb)
        val TRACK = STYLE_ATTRIBUTES.indexOf(androidx.appcompat.R.attr.track)
        val BACKGROUND = STYLE_ATTRIBUTES.indexOf(android.R.attr.background)
        val SPLIT_TRACK = STYLE_ATTRIBUTES.indexOf(androidx.appcompat.R.attr.splitTrack)
        val SWITCH_MIN_WIDTH = STYLE_ATTRIBUTES.indexOf(androidx.appcompat.R.attr.switchMinWidth)
        val GRAVITY = STYLE_ATTRIBUTES.indexOf(android.R.attr.gravity)
        val PADDING = STYLE_ATTRIBUTES.indexOf(android.R.attr.padding)
        val PADDING_LEFT = STYLE_ATTRIBUTES.indexOf(android.R.attr.paddingLeft)
        val PADDING_TOP = STYLE_ATTRIBUTES.indexOf(android.R.attr.paddingTop)
        val PADDING_RIGHT = STYLE_ATTRIBUTES.indexOf(android.R.attr.paddingRight)
        val PADDING_BOTTOM = STYLE_ATTRIBUTES.indexOf(android.R.attr.paddingBottom)
        val MIN_WIDTH = STYLE_ATTRIBUTES.indexOf(android.R.attr.minWidth)
        val MIN_HEIGHT = STYLE_ATTRIBUTES.indexOf(android.R.attr.minHeight)
        val TEXT_APPEARANCE = STYLE_ATTRIBUTES.indexOf(android.R.attr.textAppearance)
        val TEXT_SIZE = STYLE_ATTRIBUTES.indexOf(android.R.attr.textSize)
        val TEXT_STYLE = STYLE_ATTRIBUTES.indexOf(android.R.attr.textStyle)
        val FONT_FAMILY = STYLE_ATTRIBUTES.indexOf(android.R.attr.fontFamily)
        val TYPEFACE = STYLE_ATTRIBUTES.indexOf(android.R.attr.typeface)
        val INCLUDE_FONT_PADDING = STYLE_ATTRIBUTES.indexOf(android.R.attr.includeFontPadding)

        val TEXT_ATTRIBUTES = intArrayOf(
            android.R.attr.textSize,
            android.R.attr.typeface,
            android.R.attr.textStyle,
            android.R.attr.fontFamily,
        )
    }
}
