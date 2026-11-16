package io.legado.app.ui.design.components.compose

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

/**
 * Like AndroidX's RippleHostView, use View.drawBackground's attached RenderNode.
 * Drawing the same RippleDrawable directly into a Compose GraphicsLayer can start
 * a RenderNodeAnimator without a ViewAnimationHostBridge (notably on Android 10).
 * This View has no control semantics or gestures and is drawn only by its owner.
 */
internal class NgSwitchRippleHost private constructor(
    context: Context,
    drawable: Drawable,
    private var onInvalidateRipple: (() -> Unit)?,
) : View(context) {
    private val ripple = NgSwitchRippleDrawable(drawable)
    private var disposed = false
    private var clearing = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = false
        isClickable = false
        isSaveEnabled = false
        background = ripple
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(0, 0)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) = Unit

    // The Compose control owns all five drawable states, including checked/focus.
    override fun refreshDrawableState() = Unit

    override fun invalidateDrawable(who: Drawable) {
        if (!disposed && !clearing) onInvalidateRipple?.invoke()
    }

    fun drawRipple(canvas: Canvas, width: Int, height: Int, state: IntArray) {
        if (disposed) return
        if (!isAttachedToWindow) {
            clearRipple()
            return
        }
        // View.drawBackground uses these bounds even though the container has no layout.
        left = 0
        top = 0
        right = width
        bottom = height
        ripple.setVisible(true, false)
        // Binding or reattaching a View may have reset the original drawable state.
        // Replay it even when NgSwitchArtwork's stateFlags have not changed.
        ripple.state = state
        super.draw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!disposed) onInvalidateRipple?.invoke()
    }

    override fun onDetachedFromWindow() {
        clearRipple()
        super.onDetachedFromWindow()
    }

    private fun clearRipple() {
        if (clearing) return
        clearing = true
        try {
            ripple.state = intArrayOf()
            ripple.setVisible(false, false)
            ripple.jumpToCurrentState()
            unscheduleDrawable(ripple)
        } finally {
            clearing = false
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        onInvalidateRipple = null
        clearRipple()
        val container = parent as? NgSwitchRippleContainer
        container?.removeView(this)
        background = null
        ripple.release()
        if (container != null && container.childCount == 0) {
            (container.parent as? ViewGroup)?.removeView(container)
        }
    }

    companion object {
        fun attach(
            owner: View,
            drawable: Drawable,
            onInvalidate: () -> Unit,
        ): NgSwitchRippleHost {
            var ancestor: View? = owner
            while (ancestor != null && ancestor !is ViewGroup) ancestor = ancestor.parent as? View
            val parent = checkNotNull(ancestor as? ViewGroup) { "Switch ripple needs a ViewGroup owner" }
            var container: NgSwitchRippleContainer? = null
            for (index in 0 until parent.childCount) {
                val child = parent.getChildAt(index)
                if (child is NgSwitchRippleContainer) {
                    container = child
                    break
                }
            }
            val target = container ?: NgSwitchRippleContainer(parent.context).also {
                parent.addView(it)
            }
            return NgSwitchRippleHost(owner.context, drawable, onInvalidate).also(target::addView)
        }
    }
}

/** Attached to the window for animation ownership, never a second drawing/input layer. */
private class NgSwitchRippleContainer(context: Context) : ViewGroup(context) {
    init {
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isSaveEnabled = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(0, 0)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) = Unit

    override fun dispatchDraw(canvas: Canvas) = Unit

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
}

/**
 * Keep the original themed RippleDrawable (radius, state colors and OEM animation).
 * Only prevent projection to an ancestor node: Compose supplies the draw position.
 * Unlike platform DrawableWrapper (API 23), this also works on our API 21 minimum
 * and forwards the original unbounded dirty region instead of clipping to bounds.
 */
@Suppress("DEPRECATION")
internal class NgSwitchRippleDrawable(val drawable: Drawable) : Drawable(), Drawable.Callback {
    init {
        drawable.callback = this
    }

    override fun draw(canvas: Canvas) = drawable.draw(canvas)

    override fun onBoundsChange(bounds: Rect) {
        drawable.bounds = bounds
    }

    override fun isStateful(): Boolean = drawable.isStateful

    override fun setState(stateSet: IntArray): Boolean = drawable.setState(stateSet)

    override fun getState(): IntArray = drawable.state

    override fun onLevelChange(level: Int): Boolean = drawable.setLevel(level)

    override fun setAlpha(alpha: Int) { drawable.alpha = alpha }

    override fun getAlpha(): Int = drawable.alpha

    override fun setColorFilter(colorFilter: ColorFilter?) { drawable.colorFilter = colorFilter }

    override fun getColorFilter(): ColorFilter? = drawable.colorFilter

    override fun getOpacity(): Int = drawable.opacity

    override fun setTint(tintColor: Int) = drawable.setTint(tintColor)

    override fun setTintList(tint: ColorStateList?) = drawable.setTintList(tint)

    override fun setTintMode(tintMode: PorterDuff.Mode?) = drawable.setTintMode(tintMode)

    override fun setHotspot(x: Float, y: Float) = drawable.setHotspot(x, y)

    override fun setHotspotBounds(left: Int, top: Int, right: Int, bottom: Int) =
        drawable.setHotspotBounds(left, top, right, bottom)

    override fun getHotspotBounds(outRect: Rect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) drawable.getHotspotBounds(outRect)
        else outRect.set(drawable.bounds)
    }

    override fun getDirtyBounds(): Rect = drawable.dirtyBounds

    override fun getOutline(outline: Outline) = drawable.getOutline(outline)

    override fun getPadding(padding: Rect): Boolean = drawable.getPadding(padding)

    override fun getIntrinsicWidth(): Int = drawable.intrinsicWidth

    override fun getIntrinsicHeight(): Int = drawable.intrinsicHeight

    override fun getMinimumWidth(): Int = drawable.minimumWidth

    override fun getMinimumHeight(): Int = drawable.minimumHeight

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val wrapperChanged = super.setVisible(visible, restart)
        return drawable.setVisible(visible, restart) || wrapperChanged
    }

    override fun jumpToCurrentState() = drawable.jumpToCurrentState()

    override fun setAutoMirrored(mirrored: Boolean) { drawable.isAutoMirrored = mirrored }

    override fun isAutoMirrored(): Boolean = drawable.isAutoMirrored

    override fun onLayoutDirectionChanged(layoutDirection: Int): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) drawable.setLayoutDirection(layoutDirection)
        else false

    // Public since API 29; defining the method is safe on older releases too.
    @SuppressLint("NewApi")
    override fun isProjected(): Boolean = false

    override fun invalidateDrawable(who: Drawable) = invalidateSelf()

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = scheduleSelf(what, `when`)

    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)

    fun release() {
        drawable.callback = null
        callback = null
    }
}
