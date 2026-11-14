package io.legado.app.service

/** Diagnostic state only: never participates in scheduling or decides whether a task is due. */
internal class AutoTaskBatchTrace(
    val id: Int,
    private val total: Int,
    private val concurrency: Int,
    private val clock: () -> Long,
) {
    enum class Outcome { COMPLETED, FAILED, INTERRUPTED }

    private data class RunningTask(
        val name: String,
        val startedAt: Long,
        var stage: String = "script",
    )

    private val startedAt = clock()
    private val active = linkedMapOf<String, RunningTask>()
    private var started = 0
    private var completed = 0
    private var failed = 0
    private var interrupted = 0

    @Synchronized
    fun start(taskId: String, name: String) {
        if (taskId in active) return
        active[taskId] = RunningTask(name, clock())
        started++
    }

    @Synchronized
    fun stage(taskId: String, stage: String) {
        active[taskId]?.stage = stage
    }

    @Synchronized
    fun taskSummary(taskId: String): String {
        val task = active[taskId] ?: return "task=${field(taskId)} active=false"
        return "task=${field(taskId)} name=${field(task.name)} " +
            "stage=${task.stage} elapsedMs=${elapsed(task.startedAt)}"
    }

    @Synchronized
    fun finish(taskId: String, outcome: Outcome) {
        if (active.remove(taskId) == null) return
        when (outcome) {
            Outcome.COMPLETED -> completed++
            Outcome.FAILED -> failed++
            Outcome.INTERRUPTED -> interrupted++
        }
    }

    @Synchronized
    fun summary(): String {
        val running = active.entries.take(MAX_ACTIVE_DETAILS).joinToString("; ") { (id, task) ->
            "${field(id)}:${field(task.name)}:${task.stage}:${elapsed(task.startedAt)}ms"
        }
        return "batch=$id total=$total concurrency=$concurrency started=$started " +
            "completed=$completed failed=$failed interrupted=$interrupted " +
            "pending=${(total - started).coerceAtLeast(0)} active=${active.size} " +
            "elapsedMs=${elapsed(startedAt)} running=[$running] " +
            "omittedActive=${(active.size - MAX_ACTIVE_DETAILS).coerceAtLeast(0)}"
    }

    private fun elapsed(since: Long) = (clock() - since).coerceAtLeast(0L)

    companion object {
        private const val MAX_ACTIVE_DETAILS = 8

        /** Names/IDs may be imported: keep them single-line, quoted and bounded. */
        fun field(value: String?): String = "\"" + value.orEmpty().take(120)
            .map { if (it.isISOControl() || it == '\u2028' || it == '\u2029') ' ' else it }
            .joinToString("")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"") + "\""
    }
}
