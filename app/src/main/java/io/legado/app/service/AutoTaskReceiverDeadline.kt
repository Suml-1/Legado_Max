package io.legado.app.service

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Finishes a goAsync broadcast even if its worker is stuck in non-cancellable storage/Binder IO. */
internal class AutoTaskReceiverDeadline(
    finish: () -> Unit,
    onTimeout: () -> Unit,
    timeoutMs: Long = 8_000L,
    timer: ScheduledExecutorService = watchdog,
) {
    private val finished = AtomicBoolean()
    private val finishCallback = finish
    val isFinished: Boolean get() = finished.get()
    private val deadline = timer.schedule({
        if (completeOnce()) runCatching(onTimeout)
    }, timeoutMs, TimeUnit.MILLISECONDS)

    fun complete() {
        deadline.cancel(false)
        completeOnce()
    }

    private fun completeOnce(): Boolean {
        if (!finished.compareAndSet(false, true)) return false
        runCatching(finishCallback)
        return true
    }

    companion object {
        private val watchdog = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "AutoTaskReceiverDeadline").apply { isDaemon = true }
        }
    }
}
