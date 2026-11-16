package io.legado.app.ui.config

import android.app.Activity
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Samples a background-only image supplied by the host. No business views are captured or hidden.
 * The caller owns draft/alpha semantics and cancels this session when its stable host disappears.
 */
internal fun showNgColorEyedropper(
    activity: Activity,
    themeSnapshot: NgThemeSnapshot,
    backgroundRenderer: (Int, Int) -> Bitmap,
    onPicked: (Int) -> Unit,
    onCancelled: () -> Unit,
    onFailure: (Throwable) -> Unit,
): NgColorEyedropperSession = NgColorEyedropperSession(
    activity, themeSnapshot, backgroundRenderer, onPicked, onCancelled, onFailure,
).also { it.start() }

internal class NgColorEyedropperSession internal constructor(
    private val activity: Activity,
    private val themeSnapshot: NgThemeSnapshot,
    private val backgroundRenderer: (Int, Int) -> Bitmap,
    private val onPicked: (Int) -> Unit,
    private val onCancelled: () -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val source = activity.window.decorView
    private val lifecycle = (activity as? LifecycleOwner)?.lifecycle
    private var finished = false
    private var observing = false
    private var dialog: ComponentDialog? = null
    private var content: ComposeView? = null
    private var displayedBitmap: Bitmap? = null
    private var drawListener: ViewTreeObserver.OnDrawListener? = null
    private val timeout = Runnable { fail("The window did not provide a frame") }
    private val capture = Runnable { captureBackground() }
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) cancel()
    }
    private val componentCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) = cancel()
        override fun onLowMemory() = cancel()
    }
    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = cancel()
    }
    private val layoutListener = View.OnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
        if (displayedBitmap != null && (r - l != or - ol || b - t != ob - ot)) cancel()
    }

    internal fun start() {
        // Always start asynchronously so the caller can first retain the cancellation handle.
        handler.post {
            if (finished) return@post
            if (!hostAvailable()) {
                cancel()
                return@post
            }
            if (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) {
                fail("The window is protected")
                return@post
            }
            observing = true
            activity.registerComponentCallbacks(componentCallbacks)
            source.addOnAttachStateChangeListener(attachListener)
            source.addOnLayoutChangeListener(layoutListener)
            lifecycle?.addObserver(lifecycleObserver)
            if (finished) return@post
            handler.postDelayed(timeout, 3_000L)
            drawListener = object : ViewTreeObserver.OnDrawListener {
                private var queued = false
                override fun onDraw() {
                    if (queued || finished) return
                    queued = true
                    // The next frame observes the rendered buffer, not the pending draw pass.
                    source.postOnAnimation(capture)
                }
            }.also { source.viewTreeObserver.addOnDrawListener(it) }
            source.invalidate()
        }
    }

    fun cancel() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { cancel() }
            return
        }
        finish(onCancelled)
    }

    private fun hostAvailable(): Boolean = !activity.isFinishing && !activity.isDestroyed &&
        source.isAttachedToWindow &&
        (lifecycle == null || lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))

    private fun captureBackground() {
        clearDrawListener()
        if (finished) return
        if (!hostAvailable()) {
            cancel()
            return
        }
        val width = source.width
        val height = source.height
        // Never downsample: a selected pixel must remain an actual source pixel. Bound allocation
        // to 48 MB rather than risking an unbounded bitmap on unusually large displays.
        if (width <= 0 || height <= 0 || width.toLong() * height > 12_000_000L) {
            fail("The window dimensions cannot be captured")
            return
        }
        val bitmap = try {
            backgroundRenderer(width, height)
        } catch (error: OutOfMemoryError) {
            finish { onFailure(error) }
            return
        } catch (error: Exception) {
            finish { onFailure(error) }
            return
        }
        if (finished || !hostAvailable() || source.width != width || source.height != height) {
            bitmap.recycle()
            cancel()
            return
        }
        if (bitmap.isRecycled || bitmap.width != width || bitmap.height != height) {
            if (!bitmap.isRecycled) bitmap.recycle()
            fail("The background dimensions do not match the viewport")
            return
        }
        showSnapshot(bitmap)
    }

    private fun showSnapshot(bitmap: Bitmap) {
        handler.removeCallbacks(timeout)
        displayedBitmap = bitmap
        val picker = object : ComponentDialog(activity), ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
            override fun onStop() {
                super.onStop()
                viewModelStore.clear()
            }
        }
        dialog = picker
        picker.requestWindowFeature(Window.FEATURE_NO_TITLE)
        picker.setCanceledOnTouchOutside(false)
        val pickerContent = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(snapshot = themeSnapshot, updateSystemBars = false) {
                    NgColorEyedropperScreen(
                        bitmap = bitmap,
                        onCancel = ::cancel,
                        onPicked = { color -> finish { onPicked(color) } },
                    )
                }
            }
        }
        content = pickerContent
        picker.setContentView(pickerContent)
        picker.window?.decorView?.setViewTreeViewModelStoreOwner(picker)
        picker.setOnDismissListener { cancel() }
        try {
            picker.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setFlags(
                    activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = activity.window.attributes.layoutInDisplayCutoutMode
                    }
                }
                window.statusBarColor = AndroidColor.TRANSPARENT
                window.navigationBarColor = AndroidColor.TRANSPARENT
                val sourceInsets = WindowCompat.getInsetsController(activity.window, source)
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = sourceInsets.isAppearanceLightStatusBars
                    isAppearanceLightNavigationBars = sourceInsets.isAppearanceLightNavigationBars
                    ViewCompat.getRootWindowInsets(source)?.let { insets ->
                        if (!insets.isVisible(WindowInsetsCompat.Type.statusBars())) {
                            hide(WindowInsetsCompat.Type.statusBars())
                        }
                        if (!insets.isVisible(WindowInsetsCompat.Type.navigationBars())) {
                            hide(WindowInsetsCompat.Type.navigationBars())
                        }
                    }
                }
            }
            picker.show()
            picker.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        } catch (error: RuntimeException) {
            finish { onFailure(error) }
        }
    }

    private fun fail(message: String) = finish { onFailure(IllegalStateException(message)) }

    private fun finish(callback: () -> Unit) {
        if (finished) return
        finished = true
        handler.removeCallbacks(timeout)
        source.removeCallbacks(capture)
        clearDrawListener()
        if (observing) {
            observing = false
            lifecycle?.removeObserver(lifecycleObserver)
            activity.unregisterComponentCallbacks(componentCallbacks)
            source.removeOnAttachStateChangeListener(attachListener)
            source.removeOnLayoutChangeListener(layoutListener)
        }
        dialog?.setOnDismissListener(null)
        content?.disposeComposition()
        content = null
        dialog?.dismiss()
        dialog = null
        displayedBitmap?.let { bitmap -> handler.post { bitmap.recycle() } }
        displayedBitmap = null
        callback()
    }

    private fun clearDrawListener() {
        drawListener?.let {
            if (source.viewTreeObserver.isAlive) source.viewTreeObserver.removeOnDrawListener(it)
        }
        drawListener = null
    }
}

/** Floor matches the rectangle occupied by each displayed source pixel, including all four edges. */
internal fun ngEyedropperPixelCoordinate(position: Float, viewport: Int, pixels: Int): Int {
    if (viewport <= 0 || pixels <= 0 || !position.isFinite()) return 0
    return floor(position.toDouble() * pixels / viewport).toInt().coerceIn(0, pixels - 1)
}

@Composable
private fun NgColorEyedropperScreen(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onPicked: (Int) -> Unit,
) {
    BackHandler(onBack = onCancel)
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var pixel by remember(bitmap) { mutableStateOf(IntOffset(bitmap.width / 2, bitmap.height / 2)) }
    val color = bitmap.getPixel(pixel.x, pixel.y) or 0xFF000000.toInt()
    val hex = remember(color) { String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF) }
    val description = stringResource(R.string.ng_color_eyedropper_surface)
    val glassStyle = NgGlassDefaults.neutralStyle(containerAlpha = 0.96f)
    val density = LocalDensity.current
    val lensSize = with(density) { 134.dp.toPx() }
    val edge = with(density) { 12.dp.toPx() }
    val position = Offset(
        (pixel.x + 0.5f) * viewport.width / bitmap.width,
        (pixel.y + 0.5f) * viewport.height / bitmap.height,
    )

    fun select(position: Offset) {
        pixel = IntOffset(
            ngEyedropperPixelCoordinate(position.x, viewport.width, bitmap.width),
            ngEyedropperPixelCoordinate(position.y, viewport.height, bitmap.height),
        )
    }

    Box(Modifier.fillMaxSize().onSizeChanged { viewport = it }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = description
                    stateDescription = hex
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val delta = when (event.key) {
                        Key.DirectionLeft -> IntOffset(-1, 0)
                        Key.DirectionRight -> IntOffset(1, 0)
                        Key.DirectionUp -> IntOffset(0, -1)
                        Key.DirectionDown -> IntOffset(0, 1)
                        else -> return@onKeyEvent false
                    }
                    pixel = IntOffset(
                        (pixel.x + delta.x).coerceIn(0, bitmap.width - 1),
                        (pixel.y + delta.y).coerceIn(0, bitmap.height - 1),
                    )
                    true
                }
                .focusable()
                .pointerInput(viewport, bitmap) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        select(down.position)
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            select(change.position)
                            change.consume()
                        } while (change.pressed)
                    }
                },
        ) {
            drawImage(
                image,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.None,
            )
            drawCircle(
                Color.Black.copy(alpha = 0.8f), 12.dp.toPx(), position,
                style = Stroke(4.dp.toPx()),
            )
            drawCircle(Color.White, 12.dp.toPx(), position, style = Stroke(2.dp.toPx()))
            drawCircle(Color.Black, 2.dp.toPx(), position)
            drawCircle(Color.White, 1.dp.toPx(), position)
        }
        NgGlassSurface(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp),
            shape = RoundedCornerShape(24.dp),
            style = glassStyle,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                stringResource(R.string.ng_color_eyedropper_hint),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 13.sp,
            )
        }
        if (viewport.width > 0 && viewport.height > 0) {
            val lensLeft = (position.x - lensSize / 2).coerceIn(
                edge, (viewport.width - lensSize - edge).coerceAtLeast(edge),
            )
            val gap = with(density) { 36.dp.toPx() }
            val preferredTop = if (position.y > viewport.height * 0.35f) {
                position.y - lensSize - gap
            } else {
                position.y + gap
            }
            val lensTop = preferredTop.coerceIn(
                edge, (viewport.height - lensSize - edge).coerceAtLeast(edge),
            )
            NgPixelMagnifier(
                bitmap = bitmap,
                pixel = pixel,
                color = color,
                modifier = Modifier.offset { IntOffset(lensLeft.roundToInt(), lensTop.roundToInt()) },
            )
        }
        NgGlassSurface(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            shape = RoundedCornerShape(22.dp),
            style = glassStyle,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Row(
                // Compact visual bar, while retaining a full-height touch row for both actions.
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.widthIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Text(
                        stringResource(R.string.cancel),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                    )
                }
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(22.dp).background(Color(color), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(NgTheme.colors.outlineVariant), RoundedCornerShape(6.dp)),
                    )
                    Text(
                        hex,
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
                NgFormActionButton(
                    text = stringResource(R.string.ng_color_eyedropper_use),
                    onClick = { onPicked(color) },
                    variant = NgButtonVariant.PRIMARY,
                    buttonHeight = 36.dp,
                    textSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun NgPixelMagnifier(bitmap: Bitmap, pixel: IntOffset, color: Int, modifier: Modifier) {
    Canvas(
        modifier.size(134.dp).shadow(8.dp, CircleShape).clip(CircleShape)
            .background(Color.White).border(3.dp, Color.White, CircleShape).padding(3.dp)
            .border(7.dp, Color(color), CircleShape).padding(7.dp).clip(CircleShape),
    ) {
        val cellWidth = size.width / 11f
        val cellHeight = size.height / 11f
        for (y in -5..5) for (x in -5..5) {
            val sourceX = (pixel.x + x).coerceIn(0, bitmap.width - 1)
            val sourceY = (pixel.y + y).coerceIn(0, bitmap.height - 1)
            drawRect(
                color = Color(bitmap.getPixel(sourceX, sourceY) or 0xFF000000.toInt()),
                topLeft = Offset((x + 5) * cellWidth, (y + 5) * cellHeight),
                size = Size(cellWidth + 0.5f, cellHeight + 0.5f),
            )
        }
        val selectedTopLeft = Offset(5 * cellWidth, 5 * cellHeight)
        val selectedSize = Size(cellWidth, cellHeight)
        drawRect(Color.Black, selectedTopLeft, selectedSize, style = Stroke(3.dp.toPx()))
        drawRect(Color.White, selectedTopLeft, selectedSize, style = Stroke(1.dp.toPx()))
    }
}
