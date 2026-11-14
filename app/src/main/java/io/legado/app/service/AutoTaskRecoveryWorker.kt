package io.legado.app.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Opportunity-based repair only: never starts a foreground service or runs JS from a Worker. */
class AutoTaskRecoveryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        AutoTaskDiagnostics.record("worker", "recovery.worker_enter", "attempt=$runAttemptCount")
        if (!AutoTaskScheduler.usesAlarms() || !applicationContext.getPrefBoolean(PreferKey.autoTaskService)) {
            return Result.success()
        }
        return try {
            AutoTaskScheduler.reconcileFromBackground(applicationContext, "workmanager")
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AutoTaskDiagnostics.record("worker", "recovery.worker_failed", "error=${error.javaClass.simpleName}")
            Result.retry()
        }
    }

    companion object {
        internal const val UNIQUE_NAME = "auto_task_recovery"

        /** Called on the scheduler's serial scope: quick off/on cannot cancel a newer registration. */
        fun sync(context: Context) {
            val manager = WorkManager.getInstance(context)
            if (AutoTaskScheduler.usesAlarms() && context.getPrefBoolean(PreferKey.autoTaskService) && AutoTaskScheduler.hasScheduledRules()) {
                manager.enqueueUniquePeriodicWork(
                    UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<AutoTaskRecoveryWorker>(15, TimeUnit.MINUTES)
                        .setInitialDelay(15, TimeUnit.MINUTES)
                        .addTag(UNIQUE_NAME)
                        .build()
                )
            } else manager.cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
