package io.legado.app.service

import android.content.Context
import io.legado.app.model.AutoTaskRule
import io.legado.app.utils.GSON
import com.google.gson.reflect.TypeToken

/** Main-process-only checkpoint, independent of database migrations and task import/export. */
internal class AutoTaskContinuationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("auto_task_continuations", Context.MODE_PRIVATE)
    private val type = object : TypeToken<Map<String, AutoTaskPendingAttempt>>() {}.type

    private fun load(): Map<String, AutoTaskPendingAttempt> = try {
        GSON.fromJson<Map<String, AutoTaskPendingAttempt>>(preferences.getString("pending", "{}"), type) ?: emptyMap()
    } catch (_: Exception) {
        AutoTaskDiagnostics.record("continuation", "checkpoint.invalid", "reset=true")
        emptyMap()
    }

    private fun save(value: Map<String, AutoTaskPendingAttempt>) {
        check(preferences.edit().putString("pending", GSON.toJson(value)).commit()) {
            "AutoTask continuation checkpoint could not be persisted"
        }
    }

    fun reconcile(rules: List<AutoTaskRule>, dueIds: Set<String>): List<String> = synchronized(lock) {
        val updated = AutoTaskContinuationPolicy.reconcile(load(), rules.filter { it.enable }.associate { it.id to it.lastRunAt }, dueIds)
        save(updated)
        AutoTaskContinuationPolicy.orderedIds(updated)
    }

    fun started(id: String, lastRunAt: Long, now: Long) = synchronized(lock) {
        val all = load().toMutableMap()
        if (all[id]?.lastRunAt == lastRunAt) {
            all[id] = AutoTaskPendingAttempt(lastRunAt, now)
            save(all)
        }
    }

    fun completed(id: String, lastRunAt: Long) = synchronized(lock) {
        val all = load().toMutableMap()
        if (all[id]?.lastRunAt == lastRunAt) {
            all.remove(id)
            save(all)
        }
    }

    fun defer(id: String, lastRunAt: Long, until: Long) = synchronized(lock) {
        val all = load().toMutableMap()
        all[id]?.takeIf { it.lastRunAt == lastRunAt }?.let {
            all[id] = it.copy(retryAfter = until)
            save(all)
        }
    }

    fun eligibleIds(now: Long): Set<String> = synchronized(lock) {
        load().filterValues { it.retryAfter <= now }.keys
    }

    fun nextRetryAt(): Long? = synchronized(lock) { load().values.minOfOrNull { it.retryAfter } }

    fun hasPending(): Boolean = synchronized(lock) { load().isNotEmpty() }
    fun clear() = synchronized(lock) { save(emptyMap()) }

    companion object { private val lock = Any() }
}
