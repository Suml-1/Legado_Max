package io.legado.app.service

/** Persistent continuation metadata, deliberately excluding scripts, names, headers and credentials. */
@androidx.annotation.Keep
internal data class AutoTaskPendingAttempt(val lastRunAt: Long, val attemptedAt: Long = 0, val retryAfter: Long = 0)

internal object AutoTaskContinuationPolicy {
    /** Drop deleted/disabled tasks and tasks already completed by another execution entry. */
    fun reconcile(
        pending: Map<String, AutoTaskPendingAttempt>,
        enabledLastRuns: Map<String, Long>,
        dueIds: Set<String>,
    ): Map<String, AutoTaskPendingAttempt> = linkedMapOf<String, AutoTaskPendingAttempt>().apply {
        pending.forEach { (id, attempt) ->
            if (enabledLastRuns[id] == attempt.lastRunAt) put(id, attempt)
        }
        dueIds.forEach { id ->
            enabledLastRuns[id]?.let { last -> putIfAbsent(id, AutoTaskPendingAttempt(last)) }
        }
    }

    /** Unstarted tasks come first, then the oldest interrupted attempt: slow scripts cannot starve the tail. */
    fun orderedIds(pending: Map<String, AutoTaskPendingAttempt>): List<String> =
        pending.entries.sortedWith(compareBy({ it.value.attemptedAt }, { it.key })).map { it.key }
}

/** These limits do not reset when onStartCommand is delivered again to the same short service. */
internal object AutoTaskExecutionBudget {
    const val TASK_TIMEOUT_MS = 90_000L
    const val BATCH_BUDGET_MS = 120_000L
    const val SERVICE_BUDGET_MS = 150_000L
    const val WAKE_LOCK_MS = 155_000L

    fun remaining(createdAt: Long, now: Long): Long =
        (SERVICE_BUDGET_MS - (now - createdAt).coerceAtLeast(0)).coerceIn(0, SERVICE_BUDGET_MS)
}
