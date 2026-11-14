package io.legado.app.service

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean

/** Passive observer: does NOT wake the phone, keep a service alive, or acquire a WakeLock. */
internal object AutoTaskRecoveryMonitor {
    private var handler: Handler? = null
    private var thread: HandlerThread? = null
    private var generation = 0L

    @Synchronized
    fun sync(context: Context) {
        val app = context.applicationContext
        if (!AutoTaskScheduler.usesAlarms() || !app.getPrefBoolean(PreferKey.autoTaskService)) {
            generation++
            handler?.removeCallbacksAndMessages(null)
            thread?.quitSafely()
            handler = null
            thread = null
            return
        }
        if (handler != null) return
        val token = ++generation
        val worker = HandlerThread("AutoTaskRecoveryMonitor").apply { start() }
        val workerHandler = Handler(worker.looper)
        thread = worker
        handler = workerHandler
        var previousElapsed = SystemClock.elapsedRealtime()
        var previousUptime = SystemClock.uptimeMillis()
        workerHandler.postDelayed(object : Runnable {
            override fun run() {
                synchronized(this@AutoTaskRecoveryMonitor) {
                    if (generation != token || handler !== workerHandler) return
                    if (!app.getPrefBoolean(PreferKey.autoTaskService)) { sync(app); return }
                    val elapsed = SystemClock.elapsedRealtime()
                    val uptime = SystemClock.uptimeMillis()
                    val gap = elapsed - previousElapsed
                    val awakeGap = uptime - previousUptime
                    previousElapsed = elapsed
                    previousUptime = uptime
                    if (gap > INTERVAL_MS + 30_000) {
                        AutoTaskDiagnostics.record("monitor", "recovery.execution_resumed",
                            "elapsedGapMs=$gap awakeGapMs=$awakeGap")
                        AutoTaskScheduler.requestBackgroundReconcile(app, "execution_resumed")
                    }
                    workerHandler.postDelayed(this, INTERVAL_MS)
                }
            }
        }, INTERVAL_MS)
    }

    private const val INTERVAL_MS = 60_000L
}
