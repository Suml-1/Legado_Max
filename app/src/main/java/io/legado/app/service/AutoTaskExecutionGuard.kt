package io.legado.app.service

import android.content.Context
import android.os.PowerManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex

/** Shared across service recreation: a cancelled but non-cooperative script cannot overlap a new batch. */
internal object AutoTaskExecutionGuard {
    val mutex = Mutex()
    @Volatile var owner: String? = null
    val watchdog = Executors.newSingleThreadScheduledExecutor { action ->
        Thread(action, "AutoTaskExecutionDeadline").apply { isDaemon = true }
    }
}

/** A bounded execution lease; not a permanent background keep-alive. */
internal class AutoTaskWakeLease(context: Context, private val serviceId: String) : AutoCloseable {
    private val lock = context.getSystemService(PowerManager::class.java)
        ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "legado:AutoTaskExecution")
        ?.apply { setReferenceCounted(false) }

    @Synchronized
    fun acquire(timeoutMs: Long) {
        val timeout = timeoutMs.coerceIn(1, AutoTaskExecutionBudget.WAKE_LOCK_MS)
        runCatching { lock?.acquire(timeout) }.onFailure {
            AutoTaskDiagnostics.record(serviceId, "execution.wakelock_failed", "error=${it.javaClass.simpleName}")
        }
        AutoTaskDiagnostics.record(serviceId, "execution.wakelock_acquire", "timeoutMs=$timeout held=${lock?.isHeld == true}")
    }

    @Synchronized
    override fun close() {
        runCatching { if (lock?.isHeld == true) lock.release() }
        AutoTaskDiagnostics.record(serviceId, "execution.wakelock_release", "held=${lock?.isHeld == true}")
    }
}
