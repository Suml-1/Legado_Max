package io.legado.app.ui.design.theme

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.config.NgThemeGradientMotion
import io.legado.app.help.config.NgThemeGradientProfile
import io.legado.app.ui.design.components.view.NgBackdropSourceLayout
import io.legado.app.utils.printOnDebug
import kotlin.math.hypot

/** Asset-free full-screen gradient shared by View and Compose theme hosts. */
internal class NgThemeGradientDrawable(
    profile: NgThemeGradientProfile,
) : Drawable() {

    private val profile = requireNotNull(profile.normalized()) {
        "Invalid NG theme gradient profile"
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
    }
    private val drawingBounds = RectF()
    private var drawableAlpha = 255
    private val shaderBounds = Rect()
    private var shaderAlpha = -1
    private var baseShader: Shader? = null
    private var radialShaders: List<Shader> = emptyList()
    private var flowShadow: NgSoftGradientFlowShadow? = null
    private var flowShadowActive = false
    private var flowShadowUnavailable = false

    internal val supportsFlowShadow: Boolean
        get() = profile.motion == NgThemeGradientMotion.FLOW_SHADOW

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        ensureShaders()
        drawingBounds.set(bounds)
        val activeFlowShadow = flowShadow.takeIf {
            supportsFlowShadow &&
                flowShadowActive &&
                !flowShadowUnavailable &&
                canvas.isHardwareAccelerated &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        }
        paint.shader = activeFlowShadow?.movingBaseShader ?: baseShader
        canvas.drawRect(drawingBounds, paint)

        radialShaders.forEach { shader ->
            paint.shader = shader
            canvas.drawRect(drawingBounds, paint)
        }
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) {
        val normalized = alpha.coerceIn(0, 255)
        if (drawableAlpha == normalized) return
        drawableAlpha = normalized
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    fun setFlowShadowProgress(progress: Float, amount: Float): Boolean {
        if (
            !supportsFlowShadow ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            flowShadowUnavailable
        ) {
            return false
        }
        return runCatching {
            val shadow = flowShadow ?: NgSoftGradientFlowShadow(
                gradientStartX = profile.startX,
                gradientStartY = profile.startY,
                gradientEndX = profile.endX,
                gradientEndY = profile.endY,
            ).also {
                flowShadow = it
            }
            shadow.setProgress(
                progress,
                amount.coerceIn(0f, 1f),
            )
            if (shaderBounds == bounds && shaderAlpha == drawableAlpha) {
                baseShader?.let { shadow.bindBase(it, bounds) }
            }
            flowShadowActive = amount > 0f
            invalidateSelf()
        }.onFailure {
            flowShadowUnavailable = true
            flowShadowActive = false
            it.printOnDebug()
        }.isSuccess
    }

    fun clearFlowShadow() {
        if (!flowShadowActive) return
        flowShadowActive = false
        invalidateSelf()
    }

    private fun ensureShaders() {
        if (shaderBounds == bounds && shaderAlpha == drawableAlpha) return
        shaderBounds.set(bounds)
        shaderAlpha = drawableAlpha
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        val newBaseShader = LinearGradient(
            bounds.left + width * profile.startX,
            bounds.top + height * profile.startY,
            bounds.left + width * profile.endX,
            bounds.top + height * profile.endY,
            profile.colors.map(::applyDrawableAlpha).toIntArray(),
            profile.stops.toFloatArray(),
            Shader.TileMode.CLAMP,
        )
        baseShader = newBaseShader
        radialShaders = profile.radialLayers.map { layer ->
            val centerX = bounds.left + width * layer.centerX
            val centerY = bounds.top + height * layer.centerY
            RadialGradient(
                centerX,
                centerY,
                farthestCornerDistance(centerX, centerY) * layer.radius,
                layer.colors.map(::applyDrawableAlpha).toIntArray(),
                layer.stops.toFloatArray(),
                Shader.TileMode.CLAMP,
            )
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            supportsFlowShadow &&
            !flowShadowUnavailable
        ) {
            runCatching {
                flowShadow?.bindBase(newBaseShader, bounds)
            }.onFailure {
                flowShadowUnavailable = true
                flowShadowActive = false
                it.printOnDebug()
            }
        }
    }

    private fun applyDrawableAlpha(color: Int): Int {
        val alpha = Color.alpha(color) * drawableAlpha / 255
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun farthestCornerDistance(centerX: Float, centerY: Float): Float = maxOf(
        distance(centerX, centerY, bounds.left.toFloat(), bounds.top.toFloat()),
        distance(centerX, centerY, bounds.right.toFloat(), bounds.top.toFloat()),
        distance(centerX, centerY, bounds.left.toFloat(), bounds.bottom.toFloat()),
        distance(centerX, centerY, bounds.right.toFloat(), bounds.bottom.toFloat()),
    ).coerceAtLeast(1f)

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(
        (x1 - x2).toDouble(),
        (y1 - y2).toDouble(),
    ).toFloat()

}

/** Shared backdrop host; only soft-gradient flow shadow schedules animation frames. */
internal class NgThemeGradientHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ImageView(context, attrs) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val isLowRamDevice =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    private var gradientDrawable: NgThemeGradientDrawable? = null
    private var hostActive = false
    private var receiverRegistered = false
    private var animatorScaleObserverRegistered = false
    private var animationsEnabled = false
    private var powerSaveMode = false
    private var screenInteractive = true
    private var eInkMode = false
    private var frameScheduled = false
    private var motionRunning = false
    private var motionRampStartNanos = 0L
    private var flowShadowUnavailable = false

    private val environmentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshEnvironmentState()
            updateAnimationState()
        }
    }
    private val animatorScaleObserver = object : ContentObserver(
        Handler(Looper.getMainLooper()),
    ) {
        override fun onChange(selfChange: Boolean) {
            refreshAnimationsEnabled()
            updateAnimationState()
        }
    }
    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        frameScheduled = false
        if (!shouldAnimate()) {
            updateAnimationState()
            return@FrameCallback
        }
        val progress = phaseFractionAt(frameTimeNanos, FLOW_SHADOW_PERIOD_NANOS)
        val rampFraction = ((frameTimeNanos - motionRampStartNanos).toDouble() /
            MOTION_RAMP_NANOS.toDouble()).coerceIn(0.0, 1.0)
        val motionScale = rampFraction * rampFraction * (3.0 - 2.0 * rampFraction)
        val motionApplied = gradientDrawable?.setFlowShadowProgress(
            progress.toFloat(),
            motionScale.toFloat(),
        ) == true
        if (!motionApplied) {
            flowShadowUnavailable = true
            updateAnimationState()
            return@FrameCallback
        }
        scheduleNextFrame()
    }

    init {
        scaleType = ScaleType.FIT_XY
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    fun setGradientDrawable(drawable: Drawable?) {
        if (this.drawable === drawable) return
        gradientDrawable?.clearFlowShadow()
        gradientDrawable = drawable as? NgThemeGradientDrawable
        flowShadowUnavailable = false
        setImageDrawable(drawable)
        updateEnvironmentObservers()
        updateAnimationState()
    }

    fun setHostActive(active: Boolean) {
        if (hostActive == active) return
        hostActive = active
        updateEnvironmentObservers()
        updateAnimationState()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateEnvironmentObservers()
        updateAnimationState()
    }

    override fun onDetachedFromWindow() {
        stopFrameLoop(resetToBaseline = true)
        unregisterEnvironmentObservers()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateEnvironmentObservers()
        updateAnimationState()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateEnvironmentObservers()
        updateAnimationState()
    }

    private fun updateAnimationState() {
        if (shouldAnimate()) {
            if (!motionRunning) {
                motionRunning = true
                motionRampStartNanos = System.nanoTime()
            }
            scheduleNextFrame()
        } else {
            stopFrameLoop(resetToBaseline = true)
        }
    }

    private fun shouldAnimate(): Boolean {
        if (
            gradientDrawable?.supportsFlowShadow != true ||
            !hostActive ||
            !isAttachedToWindow ||
            windowVisibility != View.VISIBLE ||
            visibility != View.VISIBLE ||
            !isShown ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            !isHardwareAccelerated ||
            flowShadowUnavailable ||
            isLowRamDevice ||
            eInkMode ||
            powerSaveMode ||
            !screenInteractive
        ) {
            return false
        }
        return animationsEnabled
    }

    private fun systemAnimationsEnabled(): Boolean = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) > 0f

    private fun scheduleNextFrame() {
        if (frameScheduled) return
        frameScheduled = true
        Choreographer.getInstance().postFrameCallbackDelayed(
            frameCallback,
            FRAME_DELAY_MILLIS,
        )
    }

    private fun stopFrameLoop(resetToBaseline: Boolean) {
        if (frameScheduled) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            frameScheduled = false
        }
        motionRunning = false
        if (resetToBaseline) gradientDrawable?.clearFlowShadow()
    }

    private fun updateEnvironmentObservers() {
        val shouldObserve = hostActive &&
            gradientDrawable?.supportsFlowShadow == true &&
            isAttachedToWindow &&
            windowVisibility == View.VISIBLE &&
            visibility == View.VISIBLE &&
            isShown
        if (shouldObserve) registerEnvironmentObservers() else unregisterEnvironmentObservers()
    }

    private fun registerEnvironmentObservers() {
        refreshEnvironmentState()
        refreshAnimationsEnabled()
        if (!receiverRegistered) {
            runCatching {
                ContextCompat.registerReceiver(
                    context,
                    environmentReceiver,
                    IntentFilter().apply {
                        addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                        addAction(Intent.ACTION_SCREEN_ON)
                        addAction(Intent.ACTION_SCREEN_OFF)
                    },
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }.onSuccess {
                receiverRegistered = true
            }
        }
        if (!animatorScaleObserverRegistered) {
            runCatching {
                context.contentResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                    false,
                    animatorScaleObserver,
                )
            }.onSuccess {
                animatorScaleObserverRegistered = true
            }
        }
    }

    private fun unregisterEnvironmentObservers() {
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(environmentReceiver) }
            receiverRegistered = false
        }
        if (animatorScaleObserverRegistered) {
            runCatching { context.contentResolver.unregisterContentObserver(animatorScaleObserver) }
            animatorScaleObserverRegistered = false
        }
    }

    private fun refreshAnimationsEnabled() {
        animationsEnabled = runCatching(::systemAnimationsEnabled).getOrDefault(true)
    }

    private fun refreshEnvironmentState() {
        powerSaveMode = powerManager?.isPowerSaveMode == true
        screenInteractive = powerManager?.isInteractive != false
        eInkMode = AppConfig.isEInkMode
    }

    private fun phaseFractionAt(frameTimeNanos: Long, periodNanos: Long): Double {
        val elapsedNanos = (frameTimeNanos - timelineOriginNanos)
            .coerceAtLeast(0L) % periodNanos
        return elapsedNanos.toDouble() / periodNanos.toDouble()
    }

    private companion object {
        const val FRAME_DELAY_MILLIS = 33L
        const val FLOW_SHADOW_PERIOD_NANOS = 7_900_000_000L
        const val MOTION_RAMP_NANOS = 200_000_000L
        val timelineOriginNanos: Long = System.nanoTime()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class NgSoftGradientFlowShadow(
    gradientStartX: Float,
    gradientStartY: Float,
    gradientEndX: Float,
    gradientEndY: Float,
) {

    val movingBaseShader = RuntimeShader(MOVING_BASE_SHADER).apply {
        setFloatUniform("gradientStart", gradientStartX, gradientStartY)
        setFloatUniform("gradientEnd", gradientEndX, gradientEndY)
    }
    private var boundBase: Shader? = null
    private val boundRect = Rect()

    fun bindBase(baseShader: Shader, bounds: Rect) {
        if (boundBase === baseShader && boundRect == bounds) return
        movingBaseShader.setInputShader("base", baseShader)
        movingBaseShader.setFloatUniform(
            "origin",
            bounds.left.toFloat(),
            bounds.top.toFloat(),
        )
        movingBaseShader.setFloatUniform(
            "size",
            bounds.width().toFloat(),
            bounds.height().toFloat(),
        )
        boundBase = baseShader
        boundRect.set(bounds)
    }

    fun setProgress(progress: Float, amount: Float) {
        movingBaseShader.setFloatUniform("flow", progress, amount)
    }

    private companion object {
        const val MOVING_BASE_SHADER = """
            uniform shader base;
            uniform float2 origin;
            uniform float2 size;
            uniform float2 flow;
            uniform float2 gradientStart;
            uniform float2 gradientEnd;

            half4 main(float2 coord) {
                float2 safeSize = max(size, float2(1.0));
                float2 uv = clamp(
                    (coord - origin) / safeSize,
                    float2(0.0),
                    float2(1.0)
                );
                float stage = flow.x < 0.5
                    ? flow.x * 2.0
                    : (flow.x - 0.5) * 2.0;
                float brightening = flow.x < 0.5 ? 1.0 : 0.0;
                float front = brightening > 0.5
                    ? mix(1.42, -0.42, stage)
                    : mix(-0.42, 1.42, stage);
                float frontMask = smoothstep(
                    front - 0.34,
                    front + 0.34,
                    uv.y
                );
                float coverage = brightening > 0.5
                    ? frontMask
                    : 1.0 - frontMask;
                float paletteT = brightening > 0.5
                    ? mix(0.0, 0.78, coverage)
                    : mix(0.78, 0.0, coverage);
                float2 animatedCoord = origin +
                    mix(gradientStart, gradientEnd, paletteT) * safeSize;
                float2 sampleCoord = mix(coord, animatedCoord, flow.y);
                return base.eval(sampleCoord);
            }
        """
    }
}

/** Programmatic backdrop for the full-screen Compose page outside BaseActivity. */
@Composable
internal fun NgThemeGradientBackground(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val drawable = remember(context, configuration.uiMode) {
        ThemeConfig.getGradientBgImage(context)
    } ?: return
    val view = LocalView.current
    val lifecycleOwner = remember(view) { view.findViewTreeLifecycleOwner() }
    val backgroundHost = remember(context) {
        NgThemeGradientHostView(context)
    }
    val backgroundSource = remember(context, backgroundHost) {
        NgBackdropSourceLayout(context).apply {
            id = R.id.ng_liquid_glass_backdrop_source
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
            addView(
                backgroundHost,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    AndroidView(
        factory = { backgroundSource },
        update = {
            backgroundHost.setGradientDrawable(drawable)
        },
        modifier = modifier.fillMaxSize(),
    )
    DisposableEffect(backgroundHost, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> backgroundHost.setHostActive(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP ->
                    backgroundHost.setHostActive(false)
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        backgroundHost.setHostActive(
            lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true,
        )
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            backgroundHost.setHostActive(false)
        }
    }
}
