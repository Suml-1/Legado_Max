package io.legado.app.ui.design.theme

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import io.legado.app.help.config.*
import io.legado.effect.api.EffectThemeCallbacks
import io.legado.effect.api.EffectThemeSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.util.concurrent.atomic.AtomicBoolean

/** Generic container only. All rendering, decoding, GL and animation code lives in the theme ZIP. */
internal class EffectThemeSurface(context: Context) : FrameLayout(context) {
    private val main = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var preparing: Job? = null
    private var generation = 0L
    private var theme: NgManagedTheme? = null
    private var target = "app"
    private var values: Map<String, String> = emptyMap()
    private var environment: Map<String, String> = emptyMap()
    private var animationAllowed = false
    private var timeline: Long? = null
    internal var isRenderReady = false
        private set
    private var active = false
    private var releasing = false
    private var key: String? = null
    private var failedKey: String? = null
    private var session: EffectThemeSession? = null
    private var sessionThemeId: String? = null
    private var sessionTarget = "app"
    private var lease: EffectThemeAssetLease? = null
    private val releaseWaiters = mutableListOf<() -> Unit>()

    init { isClickable = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }

    fun bind(theme: NgManagedTheme?, target: String, values: Map<String, String> = emptyMap(),
        environment: Map<String, String> = emptyMap(), animationAllowed: Boolean, timelineOriginNanos: Long?) {
        this.theme = theme
        this.target = target
        this.values = values
        this.environment = environment
        this.animationAllowed = animationAllowed
        timeline = timelineOriginNanos
        reconcile()
    }

    fun setActive(value: Boolean) {
        active = value
        if (value) reconcile() else releaseCurrent()
    }

    fun release(onReleased: () -> Unit = {}) {
        active = false
        releaseCurrent(onReleased)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope?.launch { EffectThemeRuntime.revision.collect { reconcile() } }
        reconcile()
    }

    override fun onDetachedFromWindow() {
        active = false
        releaseCurrent()
        scope?.cancel(); scope = null
        super.onDetachedFromWindow()
    }

    private fun identity(value: NgManagedTheme) =
        "${value.id}|${value.packageRootPath}|${value.publication?.sha256}|$target|${EffectThemeRuntime.revisionOf(value.id, target)}"

    private fun reconcile() {
        if (!active || !isAttachedToWindow || releasing) return
        val requested = theme?.takeIf { it.effect?.targets == listOf(target) }
        if (requested == null) { releaseCurrent(); return }
        val nextKey = identity(requested)
        if (session != null && key == nextKey) { updateSession(); return }
        if (preparing != null && key == nextKey || failedKey == nextKey) return
        if (session != null || preparing != null) { releaseCurrent(); return }
        key = nextKey
        val token = ++generation
        val requestedTarget = target
        preparing = scope?.launch {
            var pendingLease: EffectThemeAssetLease? = null
            try {
                val handle = withContext(Dispatchers.IO) {
                    if (!EffectThemeRuntime.status(context, requested, requestedTarget).canRun) return@withContext null
                    val loaded = EffectThemeRuntime.load(context, requested, requestedTarget) ?: return@withContext null
                    pendingLease = EffectThemeRuntime.assets(requested)
                    loaded
                }
                if (token != generation || !active || !isAttachedToWindow) return@launch
                if (handle == null) { failedKey = nextKey; return@launch }
                val assets = requireNotNull(pendingLease)
                val callbacks = object : EffectThemeCallbacks {
                    override fun onReady() { main.post { if (token == generation) isRenderReady = true } }
                    override fun onError(message: String) { main.post {
                        if (token == generation) fail(requested.id, nextKey, "render", message)
                    } }
                }
                val created = handle.plugin.createSession(context, assets, callbacks)
                session = created
                sessionThemeId = requested.id
                sessionTarget = requestedTarget
                lease = assets
                pendingLease = null
                val view = created.getView()
                check(view.parent == null) { "特效主题视图已被使用" }
                view.isClickable = false
                addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                updateSession()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Throwable) {
            if (error is VirtualMachineError || error is ThreadDeath) throw error
                if (token == generation) fail(requested.id, nextKey, "create", error.message ?: error.javaClass.simpleName)
            } finally {
                pendingLease?.close()
                if (token == generation) preparing = null
            }
        }
    }

    private fun updateSession() {
        val current = theme ?: return
        try {
            session?.update(EffectThemeRuntime.parameters(current, values) + environment, animationAllowed, timeline)
        } catch (error: Throwable) {
            if (error is VirtualMachineError || error is ThreadDeath) throw error
            fail(current.id, key.orEmpty(), "update", error.message ?: error.javaClass.simpleName)
        }
    }

    private fun fail(id: String, identity: String, stage: String, message: String) {
        failedKey = identity
        EffectThemeRuntime.reportFailure(id, stage, message, target)
        releaseCurrent()
    }

    private fun releaseCurrent(onReleased: () -> Unit = {}) {
        releaseWaiters += onReleased
        ++generation
        isRenderReady = false
        preparing?.cancel(); preparing = null
        if (releasing) return
        val old = session
        val oldThemeId = sessionThemeId
        val oldTarget = sessionTarget
        val oldLease = lease
        session = null; sessionThemeId = null; lease = null; key = null
        if (old == null) {
            removeAllViews()
            oldLease?.close()
            finishRelease()
            return
        }
        releasing = true
        val once = AtomicBoolean(false)
        try {
            old.release(Runnable {
                if (once.compareAndSet(false, true)) main.post {
                    runCatching { oldLease?.close() }
                    releasing = false
                    finishRelease()
                }
            })
        } catch (error: Throwable) {
            if (error is VirtualMachineError || error is ThreadDeath) throw error
            // Fail closed: a renderer that cannot confirm release must not overlap a successor.
            oldThemeId?.let { EffectThemeRuntime.reportFailure(it, "release", error.message ?: "释放失败", oldTarget) }
        }
        removeAllViews()
    }

    private fun finishRelease() {
        val callbacks = releaseWaiters.toList(); releaseWaiters.clear()
        callbacks.forEach { it() }
        if (active && theme?.effect?.targets == listOf(target)) reconcile()
    }
}
