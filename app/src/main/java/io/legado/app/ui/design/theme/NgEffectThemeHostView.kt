package io.legado.app.ui.design.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import io.legado.app.help.config.*
import io.legado.app.constant.PreferKey
import io.legado.app.utils.defaultSharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import io.legado.app.ui.book.read.aloud.motionEnvironmentAllowed
import io.legado.app.ui.design.components.view.NgBackdropSourceLayout

/** Page ownership and time continuity are host capabilities, independent of any theme renderer. */
internal class NgEffectThemeHostView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    NgBackdropSourceLayout(context, attrs) {
    private val surface = EffectThemeSurface(context)
    private val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var theme: NgManagedTheme? = null
    private var hostActive = false
    private var granted = false
    private var registered = false
    private var observers: CoroutineScope? = null
    private val preferences = context.defaultSharedPreferences
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PreferKey.ngThemePresentationMode) post { syncSelection() }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updateRenderer()
    }
    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        addView(surface, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }
    fun bind(theme: NgManagedTheme?) {
        this.theme = theme
        updateRenderer()
        if (hostActive && isAttachedToWindow) {
            if (isSessionEligible()) NgEffectThemeCoordinator.activate(this)
            else NgEffectThemeCoordinator.deactivate(this)
        }
    }
    private fun syncSelection() {
        bind(if (NgThemeModeStore.current(context) == NgThemePresentationMode.EFFECT_THEME)
            NgEffectThemeSelection.current(context) else null)
    }
    fun setHostActive(active: Boolean) {
        hostActive = active
        if (active && isAttachedToWindow) NgEffectThemeCoordinator.activate(this)
        else NgEffectThemeCoordinator.deactivate(this)
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        observers = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            scope.launch { NgThemeLibraryStore.observe(context).collect { syncSelection() } }
        }
        if (hostActive) NgEffectThemeCoordinator.activate(this)
    }
    override fun onDetachedFromWindow() {
        hostActive = false
        observers?.cancel(); observers = null
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        NgEffectThemeCoordinator.deactivate(this)
        if (registered) { runCatching { context.unregisterReceiver(receiver) }; registered = false }
        super.onDetachedFromWindow()
    }
    internal fun isSessionEligible() = hostActive && isAttachedToWindow && theme?.effect?.targets?.contains("app") == true
    internal fun grantEffectSession() {
        if (!isSessionEligible()) { NgEffectThemeCoordinator.deactivate(this); return }
        granted = true
        updateRenderer()
        surface.setActive(true)
    }
    internal fun releaseEffectSession(onReleased: () -> Unit) {
        granted = false
        surface.release(onReleased)
    }
    private fun updateRenderer() {
        surface.bind(theme, "app", environment = mapOf("host.primaryColor" to String.format("#%08X", theme?.colors?.lightSeed ?: 0)),
            animationAllowed = motionEnvironmentAllowed(granted && isSessionEligible(), power?.isPowerSaveMode == true),
            timelineOriginNanos = NgEffectThemeCoordinator.timelineOriginNanos)
    }
}

@Composable
internal fun NgEffectThemeBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val library by NgThemeLibraryStore.observe(context).collectAsState()
    val theme = remember(library) { NgEffectThemeSelection.current(context) }
    if (NgThemeModeStore.current(context) != NgThemePresentationMode.EFFECT_THEME || theme == null) return
    val view = LocalView.current
    val owner = remember(view) { view.findViewTreeLifecycleOwner() }
    val host = remember(context) { NgEffectThemeHostView(context) }
    AndroidView(factory = { host }, update = { it.bind(theme) }, modifier = modifier)
    DisposableEffect(host, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> host.setHostActive(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> host.setHostActive(false)
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        host.setHostActive(owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true)
        onDispose { owner?.lifecycle?.removeObserver(observer); host.setHostActive(false) }
    }
}

private object NgEffectThemeCoordinator {

    val timelineOriginNanos: Long = System.nanoTime()

    private var activeHost: NgEffectThemeHostView? = null
    private var pendingHost: NgEffectThemeHostView? = null
    private var releaseInProgress = false

    fun activate(host: NgEffectThemeHostView) {
        pendingHost = host
        if (releaseInProgress) return
        if (activeHost === host) {
            pendingHost = null
            host.grantEffectSession()
            return
        }
        val previous = activeHost
        if (previous == null) {
            grantPendingHost()
            return
        }
        activeHost = null
        releaseInProgress = true
        previous.releaseEffectSession(::onReleaseCompleted)
    }

    fun deactivate(host: NgEffectThemeHostView) {
        if (pendingHost === host) pendingHost = null
        if (activeHost !== host) return
        activeHost = null
        if (releaseInProgress) return
        releaseInProgress = true
        host.releaseEffectSession(::onReleaseCompleted)
    }

    private fun onReleaseCompleted() {
        releaseInProgress = false
        grantPendingHost()
    }

    private fun grantPendingHost() {
        val next = pendingHost?.takeIf(NgEffectThemeHostView::isSessionEligible)
        pendingHost = null
        if (next == null) return
        activeHost = next
        next.grantEffectSession()
    }
}
