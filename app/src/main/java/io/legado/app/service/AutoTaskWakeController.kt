package io.legado.app.service

/** Persistent wake intent, not proof that Android will deliver an alarm. */
internal data class AutoTaskWakeState(
    val generation: Long = 0,
    val primaryAt: Long = 0,
    val recoveryAt: Long = 0,
    val lastReceivedAt: Long = 0,
    val needsRecovery: Boolean = false,
    val installing: Boolean = false,
)

/**
 * Platform-free wake orchestration. All entry points share one lock, so a late alarm, an off/on
 * toggle and a foreground repair cannot overwrite each other's generation or dispatch twice.
 * No DB, script evaluation or batch execution belongs inside this lock.
 */
internal class AutoTaskWakeController(
    private val enabled: () -> Boolean,
    private val now: () -> Long,
    private val load: () -> AutoTaskWakeState,
    private val save: (AutoTaskWakeState) -> Boolean,
    private val alarms: Alarms,
    private val log: (String, String) -> Unit,
) {
    interface Alarms {
        fun install(at: Long, generation: Long, recovery: Boolean)
        fun cancel()
    }

    private var lastDispatchAt: Long? = null

    /**
     * Repair bookkeeping only. Deliberately separate from [lastDispatchAt] so a repair backoff can
     * never throttle (or be throttled by) a foreground dispatch.
     */
    private var lastRepairAt: Long? = null

    @Synchronized
    fun snapshot(): AutoTaskWakeState = load()

    @Synchronized
    fun publishPlan(expectedGeneration: Long, at: Long?, reason: String, stillActive: () -> Boolean = { true }): Boolean {
        if (!stillActive() || load().generation != expectedGeneration) {
            log("alarm.plan_stale", "expected=$expectedGeneration current=${load().generation} reason=$reason")
            return false
        }
        if (at == null) {
            cancel(reason)
            return true
        }
        return schedule(at, reason)
    }

    @Synchronized
    fun schedule(at: Long, reason: String): Boolean {
        if (!enabled()) {
            cancel("disabled")
            return false
        }
        val old = load()
        val primaryAt = at.coerceAtLeast(now() + MIN_DELAY_MS)
        val generation = old.generation + 1
        val state = AutoTaskWakeState(generation, primaryAt, primaryAt + RECOVERY_DELAY_MS,
            old.lastReceivedAt, needsRecovery = true, installing = true)
        // Old deliveries can repair this preparing state if the process dies before installation.
        if (!save(state)) {
            save(old) // SharedPreferences.commit(false) can still change its in-memory map.
            log("alarm.state_persist_failed", "generation=$generation phase=prepare")
            return false
        }
        var primaryOk = false
        var recoveryOk = false
        try {
            alarms.install(state.primaryAt, generation, recovery = false)
            primaryOk = true
        } catch (error: Exception) {
            log("alarm.schedule_failed", "kind=primary reason=$reason error=${error.javaClass.simpleName}")
        }
        try {
            alarms.install(state.recoveryAt, generation, recovery = true)
            recoveryOk = true
        } catch (error: Exception) {
            log("alarm.schedule_failed", "kind=recovery reason=$reason error=${error.javaClass.simpleName}")
        }
        val durable = save(state.copy(
            primaryAt = if (primaryOk) state.primaryAt else 0,
            recoveryAt = if (recoveryOk) state.recoveryAt else 0,
            needsRecovery = !primaryOk,
            installing = false,
        ))
        if (!durable) log("alarm.state_persist_failed", "generation=$generation phase=confirm")
        log("alarm.plan", "reason=$reason generation=$generation durable=$durable primaryAt=${if (primaryOk) state.primaryAt else 0} " +
            "recoveryAt=${if (recoveryOk) state.recoveryAt else 0}")
        return primaryOk || recoveryOk
    }

    @Synchronized
    fun cancel(reason: String) {
        val old = load()
        // Invalidate even already-queued deliveries; cancelling a PendingIntent alone is insufficient.
        save(AutoTaskWakeState(generation = old.generation + 1, lastReceivedAt = old.lastReceivedAt))
        lastDispatchAt = null
        try {
            alarms.cancel()
            log("alarm.cancelled", "reason=$reason")
        } catch (error: Exception) {
            log("alarm.cancel_failed", "reason=$reason error=${error.javaClass.simpleName}")
        }
    }

    @Synchronized
    fun cancelWhenDisabled(reason: String) {
        if (!enabled()) cancel(reason)
    }

    @Synchronized
    fun receive(generation: Long, scheduledAt: Long, recovery: Boolean, start: () -> Unit): Boolean {
        if (!enabled()) {
            cancel("disabled_delivery")
            return false
        }
        val state = load()
        val expectedAt = if (recovery) state.recoveryAt else state.primaryAt
        if (generation != state.generation || scheduledAt <= 0 || scheduledAt != expectedAt) {
            log("alarm.stale", "generation=$generation current=${state.generation} scheduledAt=$scheduledAt recovery=$recovery")
            if (state.installing || state.needsRecovery ||
                (state.primaryAt > 0L && now() > state.recoveryAt + MISSED_GRACE_MS)) {
                schedule(now() + RESTORE_DELAY_MS, "stale_delivery_repair")
            }
            return false
        }
        val receivedAt = now()
        if (receivedAt < scheduledAt - MIN_DELAY_MS) {
            log("alarm.early", "generation=$generation scheduledAt=$scheduledAt receivedAt=$receivedAt")
            return false
        }
        save(state.copy(lastReceivedAt = receivedAt))
        log("alarm.received", "generation=$generation scheduledAt=$scheduledAt lagMs=${receivedAt - scheduledAt} recovery=$recovery")
        // Rearm WITHOUT waiting for a service, DB read, permission promotion or long-running task.
        return dispatchWithFallback(if (recovery) "recovery_alarm" else "primary_alarm", start)
    }

    @Synchronized
    fun requestStart(reason: String, start: () -> Unit): Boolean {
        if (!enabled()) return false
        return dispatchWithFallback(reason, start)
    }

    @Synchronized
    fun recoverForeground(force: Boolean, serviceAlive: () -> Boolean, start: () -> Unit): Boolean {
        if (!enabled() || serviceAlive()) return false
        val time = now()
        // onResume can happen repeatedly before onCreate of the requested service is dispatched.
        if (lastDispatchAt?.let { time - it in 0 until FOREGROUND_THROTTLE_MS } == true) return false
        val state = load()
        if (!force && !state.needsRecovery && state.primaryAt > 0 && time < state.primaryAt + MISSED_GRACE_MS) {
            return false
        }
        log("recovery.foreground", "force=$force previousPrimaryAt=${state.primaryAt} needsRecovery=${state.needsRecovery}")
        return dispatchWithFallback("foreground", start)
    }

    @Synchronized
    fun noteStartFailure() {
        save(load().copy(needsRecovery = true))
    }

    /**
     * Background restore path: only re-installs alarms, never starts a service, worker or script.
     *
     * A plan the platform may still deliver is never postponed, so repeated calls from several
     * entry points (boot, package replace, time change) converge instead of walking the plan
     * forward. Repair attempts are rate limited by [lastRepairAt] alone, which keeps the
     * foreground throttle ([lastDispatchAt]) untouched.
     *
     * @return true only when a new plan was installed by this call.
     */
    @Synchronized
    fun reconcileBackground(reason: String, serviceAlive: () -> Boolean): Boolean {
        if (!enabled()) {
            log("alarm.repair_skipped", "reason=$reason why=disabled")
            return false
        }
        if (serviceAlive()) {
            log("alarm.repair_skipped", "reason=$reason why=service_alive")
            return false
        }
        val time = now()
        val state = load()
        // Replacing a pending alarm with a later one only loses a wake-up, so nothing is ever
        // moved forward: any still-future time wins, whatever the reason we were called for.
        // That covers a healthy plan, a late primary with a working backup, and the surviving
        // backup after a partial install (needsRecovery must not walk it backwards).
        // `installing` is the single exception: those times may be intent that never reached the
        // platform, so they are re-armed instead of trusted.
        val pendingFuture = listOf(state.primaryAt, state.recoveryAt).any { it > time }
        if (pendingFuture && !state.installing) {
            log("alarm.repair_skipped", "reason=$reason why=plan_pending primaryAt=${state.primaryAt} " +
                "recoveryAt=${state.recoveryAt} needsRecovery=${state.needsRecovery}")
            return false
        }
        val latest = maxOf(state.primaryAt, state.recoveryAt)
        val absent = latest <= 0L
        val expired = !absent && time > latest + MISSED_GRACE_MS
        // Repair only when nothing deliverable is armed: unknown plan, failed install, or a plan
        // whose grace window has fully passed. Times still inside the window may yet fire.
        if (!absent && !expired && !state.needsRecovery && !state.installing) {
            log("alarm.repair_skipped", "reason=$reason why=within_grace latest=$latest")
            return false
        }
        if (lastRepairAt?.let { time - it in 0 until REPAIR_THROTTLE_MS } == true) {
            log("alarm.repair_skipped", "reason=$reason why=throttled")
            return false
        }
        lastRepairAt = time
        val repaired = schedule(time + RESTORE_DELAY_MS, "background_repair:$reason")
        if (!repaired) log("alarm.repair_failed", "reason=$reason latest=$latest")
        return repaired
    }

    /**
     * Continuation for a service whose execution budget ran out: brings the next wake forward to
     * now + [RESTORE_DELAY_MS] but never past an already planned earlier alarm, and never starts
     * the service itself. The generation counter still invalidates older producers.
     */
    @Synchronized
    fun requestContinuation(reason: String, notBefore: Long = 0): Boolean {
        if (!enabled()) {
            log("alarm.continuation_skipped", "reason=$reason why=disabled")
            return false
        }
        val time = now()
        val state = load()
        val candidate = maxOf(time + RESTORE_DELAY_MS, notBefore)
        val plannedNext = listOf(state.primaryAt, state.recoveryAt).filter { it > time }.minOrNull()
        if (plannedNext != null && plannedNext <= candidate) {
            log("alarm.continuation_skipped", "reason=$reason why=plan_earlier plannedNext=$plannedNext")
            return false
        }
        val resumed = schedule(candidate, "continuation:$reason")
        if (!resumed) log("alarm.continuation_failed", "reason=$reason candidate=$candidate")
        return resumed
    }

    private fun dispatchWithFallback(reason: String, start: () -> Unit): Boolean {
        // A five-minute retry is bounded and also covers failures before service.onStartCommand.
        val time = now()
        val pending = load().primaryAt.takeIf { it > time }
        schedule(minOf(pending ?: Long.MAX_VALUE, time + RETRY_DELAY_MS), "${reason}_fallback")
        if (!enabled()) return false
        lastDispatchAt = now()
        return try {
            start()
            log("wake.dispatched", "reason=$reason")
            true
        } catch (error: Exception) {
            noteStartFailure()
            log("wake.dispatch_failed", "reason=$reason error=${error.javaClass.simpleName}")
            false
        }
    }

    companion object {
        const val MIN_DELAY_MS = 1_000L
        const val RESTORE_DELAY_MS = 60_000L
        const val RETRY_DELAY_MS = 5 * 60_000L
        const val RECOVERY_DELAY_MS = 5 * 60_000L
        const val MISSED_GRACE_MS = 30_000L
        const val FOREGROUND_THROTTLE_MS = 30_000L
        const val REPAIR_THROTTLE_MS = 30_000L
    }
}
