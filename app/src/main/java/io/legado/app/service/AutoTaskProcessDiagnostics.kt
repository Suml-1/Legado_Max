package io.legado.app.service

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import io.legado.app.BuildConfig
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Captures system-reported process exits without doing preference or binder work on the caller. */
internal object AutoTaskProcessDiagnostics {
    private const val HISTORY_LIMIT = 5
    private val submitted = AtomicBoolean()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AutoTaskProcessDiagnostics").apply { isDaemon = true }
    }

    fun recordOnProcessStart(context: Context) {
        if (!submitted.compareAndSet(false, true)) return
        try {
            executor.execute {
                runCatching {
                    val app = context.applicationContext
                    if (!app.getPrefBoolean(PreferKey.autoTaskService)) return@runCatching
                    recordProcessStart()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        recordHistoricalExits(app)
                    }
                }.onFailure { error ->
                    AutoTaskDiagnostics.record("process-${Process.myPid()}",
                        "process.diagnostics_failed", "error=${error.javaClass.simpleName}")
                }
            }
        } catch (_: Throwable) {
            // Diagnostics are best effort and must never prevent application startup.
        }
    }

    private fun recordProcessStart() {
        val pid = Process.myPid()
        val details = AutoTaskProcessExitSummary.formatStart(
            sdk = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            version = BuildConfig.VERSION_NAME,
            pid = pid,
        )
        AutoTaskDiagnostics.record("process-$pid", "process.start", details)
    }

    private fun recordHistoricalExits(context: Context) {
        try {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return
            val now = System.currentTimeMillis()
            val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, HISTORY_LIMIT * 4)
                .filter { it.processName == context.packageName }
                .take(HISTORY_LIMIT)
            AutoTaskDiagnostics.record("process-${Process.myPid()}", "process.exit_history", "records=${exits.size}")
            exits.forEach { info ->
                    val details = AutoTaskProcessExitSummary.formatExit(
                        timestamp = info.timestamp,
                        pid = info.pid,
                        reason = info.reason,
                        reasonName = reasonName(info.reason),
                        status = info.status,
                        importance = info.importance,
                        pssKb = info.pss,
                        rssKb = info.rss,
                        sinceExitMs = (now - info.timestamp).coerceAtLeast(0L),
                    )
                    AutoTaskDiagnostics.record("process-${info.pid}", "process.exit_reason", details)
                }
        } catch (error: Throwable) {
            AutoTaskDiagnostics.record(
                "process-${Process.myPid()}",
                "process.exit_history_unavailable",
                "error=${error.javaClass.simpleName}",
            )
        }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_UNKNOWN -> "unknown"
        ApplicationExitInfo.REASON_EXIT_SELF -> "exit_self"
        ApplicationExitInfo.REASON_SIGNALED -> "signaled"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash_native"
        ApplicationExitInfo.REASON_ANR -> "anr"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization_failure"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission_change"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resource_usage"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user_requested"
        ApplicationExitInfo.REASON_USER_STOPPED -> "user_stopped"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency_died"
        ApplicationExitInfo.REASON_OTHER -> "other"
        ApplicationExitInfo.REASON_FREEZER -> "freezer"
        else -> "reason_$reason"
    }
}
