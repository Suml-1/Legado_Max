package io.legado.app.ui.config

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Looper
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import io.legado.app.R
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.design.components.view.NgBackdropSourceLayout
import kotlin.math.roundToInt

/**
 * Returns an owned, readable snapshot in decor coordinates. Only the window drawable and the
 * existing background-only host participate; drawing the decor would also capture page controls.
 */
internal fun renderNgColorThemeBackground(activity: Activity, width: Int, height: Int): Bitmap {
    check(Looper.myLooper() == Looper.getMainLooper()) {
        "The theme background must be captured on the UI thread"
    }
    require(width > 0 && height > 0 && width.toLong() * height <= 12_000_000L) {
        "The background dimensions cannot be captured"
    }
    val decor = activity.window.decorView
    check(decor.width == width && decor.height == height) {
        "The window dimensions changed before background capture"
    }
    val source = (decor.findViewById<View>(R.id.ng_liquid_glass_backdrop_source)
        as? NgBackdropSourceLayout)?.takeIf { it.isShown && it.width > 0 && it.height > 0 }
    val textures = ArrayList<TextureView>()
    source?.let { collectBackgroundTextures(it, textures) }
    textures.forEach { texture ->
        check(texture.isAvailable) { "The background texture has not provided a frame" }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // A software Canvas would silently remove the active RuntimeShader/TextureView frame.
        return renderHardwareBackground(width, height) { canvas ->
            drawThemeBackground(canvas, activity, source, emptyMap())
        }
    }

    val frames = LinkedHashMap<TextureView, Bitmap>()
    try {
        // TextureView.getBitmap must run outside View.draw. It already includes the texture's
        // setTransform matrix, so only normal View geometry is applied when compositing below.
        textures.forEach { texture ->
            require(texture.width.toLong() * texture.height <= 12_000_000L) {
                "The background texture dimensions cannot be captured"
            }
            frames[texture] = checkNotNull(texture.bitmap) {
                "The background texture could not be captured"
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            drawThemeBackground(Canvas(bitmap), activity, source, frames)
            return bitmap
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    } finally {
        frames.values.forEach(Bitmap::recycle)
    }
}

private fun collectBackgroundTextures(view: View, textures: MutableList<TextureView>) {
    if (view.visibility != View.VISIBLE || view.alpha <= 0f) return
    check(view !is SurfaceView) { "The background contains an unsupported surface" }
    if (view is TextureView) textures += view
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            collectBackgroundTextures(view.getChildAt(index), textures)
        }
    }
}

private fun drawThemeBackground(
    canvas: Canvas,
    activity: Activity,
    source: NgBackdropSourceLayout?,
    textureFrames: Map<TextureView, Bitmap>,
) {
    val decor = activity.window.decorView
    // The picker is another transparent window. Flatten here so the displayed pixels and sampled
    // pixels agree, and translucent wallpaper never reveals the covered page or editor controls.
    canvas.drawColor(activity.backgroundColor or 0xFF000000.toInt())
    val background = decor.background
    if (background != null) {
        val oldBounds = Rect(background.bounds)
        fun setBounds(bounds: Rect) {
            if (source == null) {
                background.bounds = bounds
            } else {
                source.withUntrackedWindowBackgroundBounds { background.bounds = bounds }
            }
        }
        try {
            setBounds(Rect(0, 0, decor.width, decor.height))
            background.draw(canvas)
        } finally {
            setBounds(oldBounds)
        }
    }
    if (source == null || source.alpha <= 0f) return
    val decorLocation = IntArray(2)
    val sourceLocation = IntArray(2)
    decor.getLocationInWindow(decorLocation)
    source.getLocationInWindow(sourceLocation)
    val saveCount = canvas.save()
    try {
        canvas.translate(
            (sourceLocation[0] - decorLocation[0]).toFloat(),
            (sourceLocation[1] - decorLocation[1]).toFloat(),
        )
        if (source.alpha < 1f) {
            canvas.saveLayerAlpha(
                0f, 0f, source.width.toFloat(), source.height.toFloat(),
                (source.alpha * 255).roundToInt().coerceIn(0, 255),
            )
        }
        source.draw(canvas)
        if (textureFrames.isNotEmpty()) {
            // BaseActivity places its scene host after the gradient/poster. Its TextureView is
            // the top background layer; software View.draw intentionally omits that layer.
            drawSoftwareTextureFrames(canvas, source, textureFrames, 1f)
        }
    } finally {
        canvas.restoreToCount(saveCount)
    }
}

private fun drawSoftwareTextureFrames(
    canvas: Canvas,
    view: View,
    frames: Map<TextureView, Bitmap>,
    alpha: Float,
) {
    val saveCount = canvas.save()
    try {
        view.clipBounds?.let { canvas.clipRect(it) }
        if (view is TextureView) {
            val frame = frames[view] ?: return
            canvas.clipRect(0, 0, view.width, view.height)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                this.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
            }
            canvas.drawBitmap(frame, 0f, 0f, paint)
        } else if (view is ViewGroup) {
            if (view.clipChildren) canvas.clipRect(0, 0, view.width, view.height)
            if (view.clipToPadding) {
                canvas.clipRect(
                    view.paddingLeft, view.paddingTop,
                    view.width - view.paddingRight, view.height - view.paddingBottom,
                )
            }
            for (index in 0 until view.childCount) {
                val child = view.getChildAt(index)
                if (child.visibility != View.VISIBLE || child.alpha <= 0f) continue
                val childSaveCount = canvas.save()
                try {
                    canvas.translate(
                        (child.left - view.scrollX).toFloat(),
                        (child.top - view.scrollY).toFloat(),
                    )
                    canvas.concat(child.matrix)
                    drawSoftwareTextureFrames(canvas, child, frames, alpha * child.alpha)
                } finally {
                    canvas.restoreToCount(childSaveCount)
                }
            }
        }
    } finally {
        canvas.restoreToCount(saveCount)
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun renderHardwareBackground(
    width: Int,
    height: Int,
    draw: (Canvas) -> Unit,
): Bitmap {
    val reader = ImageReader.newInstance(
        width, height, PixelFormat.RGBA_8888, 1,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
    )
    reader.use {
        val node = RenderNode("NgColorThemeBackground")
        var renderer: HardwareRenderer? = null
        try {
            node.setPosition(0, 0, width, height)
            val canvas = node.beginRecording(width, height)
            try {
                draw(canvas)
            } finally {
                node.endRecording()
            }
            val hardwareRenderer = HardwareRenderer().also { renderer = it }
            hardwareRenderer.isOpaque = false
            hardwareRenderer.setSurface(reader.surface)
            hardwareRenderer.setContentRoot(node)
            // One frame, one consumer: the public present fence makes the image available without
            // polling or an ImageReader callback that would be blocked on this UI-thread call.
            val result = hardwareRenderer.createRenderRequest()
                .setWaitForPresent(true)
                .syncAndDraw()
            val failures = HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND or
                HardwareRenderer.SYNC_CONTEXT_IS_STOPPED or HardwareRenderer.SYNC_FRAME_DROPPED
            check(result and failures == 0) { "Background rendering failed: $result" }
            checkNotNull(reader.acquireNextImage()) {
                "The background renderer did not provide a frame"
            }.use { image ->
                checkNotNull(image.hardwareBuffer) { "The background frame has no buffer" }
                    .use { buffer ->
                        val hardwareBitmap = checkNotNull(
                            Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB)),
                        ) { "The background frame could not be read" }
                        try {
                            return checkNotNull(hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)) {
                                "The background frame could not be copied"
                            }
                        } finally {
                            hardwareBitmap.recycle()
                        }
                    }
            }
        } finally {
            try {
                renderer?.destroy()
            } finally {
                node.discardDisplayList()
            }
        }
    }
}
