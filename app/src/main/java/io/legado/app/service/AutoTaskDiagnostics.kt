package io.legado.app.service

import io.legado.app.constant.AppLog
import io.legado.app.utils.externalCache
import splitties.init.appCtx
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Always-on, local-only lifecycle metadata, separate from optional full debug/HTTP logs.
 * Never pass scripts, cookies, headers, responses or exception messages to this API.
 * File IO and AppLog publication run on one daemon writer, not the service main thread.
 */
internal object AutoTaskDiagnostics {
    private val queue = AutoTaskDiagnosticQueue()
    private val started = AtomicBoolean()
    private val journal by lazy {
        // Exactly the directory already included by both About -> Save logs entry points.
        AutoTaskDiagnosticJournal(File(appCtx.externalCache, "logs"))
    }

    fun record(serviceId: String, event: String, details: String) {
        runCatching {
            val line = AutoTaskDiagnosticJournal.format(serviceId, event, details, System.currentTimeMillis())
            queue.offer(line)
            if (started.compareAndSet(false, true)) {
                try {
                    Thread(::writeLoop, "AutoTaskDiagnostics").apply { isDaemon = true }.start()
                } catch (error: Throwable) {
                    started.set(false)
                    throw error
                }
            }
        }
        // Diagnostics must not stop or fail a scheduled task, including when storage is full.
    }

    private fun writeLoop() {
        while (true) {
            val line = try {
                queue.take()
            } catch (_: InterruptedException) {
                continue
            }
            val saved = runCatching { journal.append(line) }.getOrDefault(false)
            runCatching {
                AppLog.put(if (saved) line else "$line journalWriteFailed=true")
            }
        }
    }
}
