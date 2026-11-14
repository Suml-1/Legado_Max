package io.legado.app.service

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong

/** Pure JVM, bounded journal. Its callers must only pass safe diagnostic metadata. */
internal class AutoTaskDiagnosticJournal(
    private val directory: File,
    private val maxFileBytes: Long = 512 * 1024L,
) {
    init {
        require(maxFileBytes >= MAX_LINE_BYTES)
    }

    /** Synchronous on the dedicated writer thread, closed after every line (no buffered tail). */
    @Synchronized
    fun append(line: String): Boolean = runCatching {
        check(directory.isDirectory || directory.mkdirs()) { "Diagnostic directory unavailable" }
        val current = File(directory, CURRENT_FILE)
        val previous = File(directory, PREVIOUS_FILE)
        val bytes = (singleLine(line, MAX_LINE_BYTES / 4) + "\n").toByteArray(Charsets.UTF_8)
        if (current.length() + bytes.size > maxFileBytes) {
            // Fixed children only: never use caller-supplied names for rotation/deletion.
            check(!previous.exists() || previous.delete()) { "Cannot rotate previous diagnostic" }
            check(!current.exists() || current.renameTo(previous)) { "Cannot rotate diagnostic" }
        }
        java.io.FileOutputStream(current, true).use { it.write(bytes) }
        true
    }.getOrDefault(false)

    companion object {
        const val CURRENT_FILE = "auto-task-diagnostic.log"
        const val PREVIOUS_FILE = "auto-task-diagnostic.previous.log"
        const val MAX_LINE_BYTES = 16 * 1024
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSXXX")

        fun format(serviceId: String, event: String, details: String, at: Long): String =
            "${dateFormat.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))} " +
                "[AutoTaskDiag] service=${singleLine(serviceId, 100)} " +
                "event=${singleLine(event, 80)} ${singleLine(details, 3_500)}"

        fun singleLine(value: String, limit: Int): String = buildString {
            value.take(limit).forEach {
                append(if (it.isISOControl() || it == '\u2028' || it == '\u2029') ' ' else it)
            }
            if (value.length > limit) append("…")
        }
    }
}

/** Bounded pending work; drops oldest on overload, and reports that loss in the next written line. */
internal class AutoTaskDiagnosticQueue(capacity: Int = 256) {
    private val queue = ArrayBlockingQueue<String>(capacity)
    private val dropped = AtomicLong()

    @Synchronized
    fun offer(line: String) {
        if (queue.offer(line)) return
        if (queue.poll() != null) dropped.incrementAndGet()
        if (!queue.offer(line)) dropped.incrementAndGet()
    }

    fun take(): String {
        val line = queue.take()
        val lost = dropped.getAndSet(0)
        return if (lost == 0L) line else "$line droppedBeforeWrite=$lost"
    }
}
