package io.legado.app.ui.design.components.view

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * A background-only subtree whose revisions can be used by backdrop RenderNode caches.
 * Foreground controls must remain outside this host: their invalidations do not change
 * the sampled wallpaper. Descendant invalidations include ImageView drawable updates,
 * gradient animation frames and TextureView's frame-available invalidations.
 */
internal open class NgBackdropSourceLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var contentRevision: Long = 0L
        private set

    private var backgroundRevision = 0L
    private var windowBackgroundObserver: WindowBackgroundObserver? = null
    private var observedBackgroundRevision = -1L

    /** Observe replacement as well as in-place tint/state changes of the window drawable. */
    val windowBackgroundRevision: Long
        get() {
            observeWindowBackground()
            val revision = windowBackgroundObserver?.revision ?: 0L
            if (revision != observedBackgroundRevision) {
                observedBackgroundRevision = revision
                backgroundRevision++
            }
            return backgroundRevision
        }

    /** Only temporary setBounds/restoreBounds calls belong here, never drawable.draw(). */
    fun withUntrackedWindowBackgroundBounds(action: () -> Unit) {
        val observer = windowBackgroundObserver
        if (observer == null) {
            action()
            return
        }
        observer.untrackedBoundsDepth++
        try {
            action()
        } finally {
            observer.untrackedBoundsDepth--
        }
    }

    override fun onDescendantInvalidated(child: View, target: View) {
        contentRevision++
        super.onDescendantInvalidated(child, target)
    }

    override fun invalidate() {
        contentRevision++
        super.invalidate()
    }

    @Suppress("DEPRECATION")
    override fun invalidate(dirty: Rect) {
        contentRevision++
        super.invalidate(dirty)
    }

    @Suppress("DEPRECATION")
    override fun invalidate(left: Int, top: Int, right: Int, bottom: Int) {
        contentRevision++
        super.invalidate(left, top, right, bottom)
    }

    override fun invalidateDrawable(drawable: Drawable) {
        if (verifyDrawable(drawable)) contentRevision++
        super.invalidateDrawable(drawable)
    }

    @Suppress("DEPRECATION")
    override fun setBackgroundDrawable(background: Drawable?) {
        if (getBackground() !== background) contentRevision++
        super.setBackgroundDrawable(background)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) contentRevision++
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        contentRevision++
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        contentRevision++
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this) contentRevision++
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        contentRevision++
    }

    override fun onDetachedFromWindow() {
        releaseWindowBackground()
        super.onDetachedFromWindow()
    }

    private fun observeWindowBackground() {
        val drawable = rootView.background
        val observer = windowBackgroundObserver
        if (drawable === observer?.drawable && drawable?.callback === observer) {
            return
        }
        releaseWindowBackground()
        if (drawable != null) {
            // Nested background hosts share one observer instead of stacking callback proxies.
            val next = drawable.callback as? WindowBackgroundObserver
                ?: WindowBackgroundObserver(drawable)
            next.retain()
            windowBackgroundObserver = next
        }
        observedBackgroundRevision = -1L
        backgroundRevision++
    }

    private fun releaseWindowBackground() {
        windowBackgroundObserver?.release()
        windowBackgroundObserver = null
    }

    private class WindowBackgroundObserver(val drawable: Drawable) : Drawable.Callback {
        private val originalCallback = drawable.callback
        private var referenceCount = 0
        var revision = 0L
            private set
        var untrackedBoundsDepth = 0

        fun retain() {
            referenceCount++
            drawable.callback = this
        }

        fun release() {
            referenceCount--
            if (referenceCount == 0 && drawable.callback === this) {
                drawable.callback = originalCallback
            }
        }

        override fun invalidateDrawable(who: Drawable) {
            if (untrackedBoundsDepth == 0) revision++
            originalCallback?.invalidateDrawable(who)
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            originalCallback?.scheduleDrawable(who, what, `when`)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            originalCallback?.unscheduleDrawable(who, what)
        }
    }
}
