package io.legado.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.IntentAction
import io.legado.app.constant.PreferKey
import io.legado.app.model.AutoTask
import io.legado.app.receiver.AutoTaskAlarmReceiver
import io.legado.app.utils.CronSchedule
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.getPrefBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Alarm delivery/recovery owns no task jobs. All rule work remains in AutoTaskService. */
internal object AutoTaskScheduler {
    const val ACTION_WAKE = "io.legado.app.AUTO_TASK_WAKE"
    const val EXTRA_GENERATION = "autoTaskWakeGeneration"
    const val EXTRA_SCHEDULED_AT = "autoTaskDiagnosticAlarmAt"
    const val EXTRA_RECOVERY = "autoTaskRecoveryAlarm"
    private const val PRIMARY_REQUEST = 100109
    private const val RECOVERY_REQUEST = 100110
    private const val LEGACY_REQUEST = 100108
    private const val KEY_PREFIX = "autoTaskWake."
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val foreground = AtomicBoolean()
    private val firstForeground = AtomicBoolean(true)
    private var instance: AutoTaskWakeController? = null
    private val logId = "scheduler-${Process.myPid()}"

    fun usesAlarms(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    @Synchronized
    private fun controller(context: Context): AutoTaskWakeController {
        instance?.let { return it }
        val app = context.applicationContext
        val prefs = app.defaultSharedPreferences
        return AutoTaskWakeController(
            enabled = { app.getPrefBoolean(PreferKey.autoTaskService) },
            now = System::currentTimeMillis,
            load = {
                AutoTaskWakeState(
                    prefs.getLong(KEY_PREFIX + "generation", 0),
                    prefs.getLong(KEY_PREFIX + "primaryAt", 0),
                    prefs.getLong(KEY_PREFIX + "recoveryAt", 0),
                    prefs.getLong(KEY_PREFIX + "lastReceivedAt", 0),
                    prefs.getBoolean(KEY_PREFIX + "needsRecovery", false),
                    prefs.getBoolean(KEY_PREFIX + "installing", false),
                )
            },
            save = { state ->
                val persisted = prefs.edit()
                    .putLong(KEY_PREFIX + "generation", state.generation)
                    .putLong(KEY_PREFIX + "primaryAt", state.primaryAt)
                    .putLong(KEY_PREFIX + "recoveryAt", state.recoveryAt)
                    .putLong(KEY_PREFIX + "lastReceivedAt", state.lastReceivedAt)
                    .putBoolean(KEY_PREFIX + "needsRecovery", state.needsRecovery)
                    .putBoolean(KEY_PREFIX + "installing", state.installing)
                    .commit()
                persisted
            },
            alarms = object : AutoTaskWakeController.Alarms {
                override fun install(at: Long, generation: Long, recovery: Boolean) {
                    val manager = app.getSystemService(AlarmManager::class.java)
                        ?: error("AlarmManager unavailable")
                    val intent = alarmIntent(app, recovery).apply {
                        putExtra(EXTRA_GENERATION, generation)
                        putExtra(EXTRA_SCHEDULED_AT, at)
                        putExtra(EXTRA_RECOVERY, recovery)
                    }
                    val operation = PendingIntent.getBroadcast(app,
                        if (recovery) RECOVERY_REQUEST else PRIMARY_REQUEST,
                        intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
                    if (exact) {
                        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
                    } else {
                        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
                    }
                    log("alarm.scheduled", "kind=${if (recovery) "recovery" else "primary"} " +
                        "generation=$generation triggerAt=$at exact=$exact")
                    cancelLegacy(app, manager)
                }

                override fun cancel() {
                    val manager = app.getSystemService(AlarmManager::class.java) ?: return
                    for (recovery in listOf(false, true)) {
                        PendingIntent.getBroadcast(app,
                            if (recovery) RECOVERY_REQUEST else PRIMARY_REQUEST,
                            alarmIntent(app, recovery), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                        )?.let { manager.cancel(it); it.cancel() }
                    }
                    cancelLegacy(app, manager)
                }
            },
            log = ::log,
        ).also { instance = it }
    }

    private fun alarmIntent(context: Context, recovery: Boolean) =
        Intent(context, AutoTaskAlarmReceiver::class.java).setAction(ACTION_WAKE)
            .putExtra(EXTRA_RECOVERY, recovery)

    private fun cancelLegacy(context: Context, manager: AlarmManager) {
        PendingIntent.getService(context, LEGACY_REQUEST,
            Intent(context, AutoTaskService::class.java).setAction(IntentAction.refreshSchedule),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.let { manager.cancel(it); it.cancel() }
    }

    /** Capture the epoch BEFORE reading rules; cancelled/obsolete services cannot publish old plans. */
    suspend fun scheduleFromRules(context: Context): Long? {
        val control = controller(context)
        val generation = control.snapshot().generation
        val rules = AutoTask.getRules()
        val now = System.currentTimeMillis()
        val next = rules.asSequence().filter { it.enable }.mapNotNull {
            CronSchedule.parse(it.cron?.trim().orEmpty())?.nextTimeAfter(now)
        }.minOrNull()
        val coroutine = currentCoroutineContext()
        coroutine.ensureActive()
        if (!control.publishPlan(generation, next, "cron") { coroutine.isActive }) return null
        val state = control.snapshot()
        return state.primaryAt.takeIf { it > 0L } ?: state.recoveryAt.takeIf { it > 0L }
    }

    fun cancel(context: Context, reason: String) {
        val app = context.applicationContext
        launchSafely {
            if (!usesAlarms() || !hasScheduledRules()) controller(app).cancel(reason)
            else controller(app).cancelWhenDisabled(reason)
            if (!app.getPrefBoolean(PreferKey.autoTaskService)) AutoTaskContinuationStore(app).clear()
            syncRecoveryWork(app)
        }
    }

    fun noteStartFailure(context: Context) {
        val app = context.applicationContext
        launchSafely { controller(app).noteStartFailure() }
    }

    /** Called by a short-lived receiver; no DB or JS work before the next wake is armed. */
    fun receive(context: Context, intent: Intent, finished: () -> Unit) {
        val app = context.applicationContext
        val enteredAt = SystemClock.elapsedRealtime()
        // On the receiver thread, before IO dispatch: distinguishes delivery from delayed application work.
        log("alarm.receiver_enter", "action=${AutoTaskBatchTrace.field(intent.action)} " +
            "generation=${intent.getLongExtra(EXTRA_GENERATION, -1)} elapsedAt=$enteredAt")
        val deadline = try {
            AutoTaskReceiverDeadline(finished, { log("alarm.receiver_timeout", "budgetMs=8000") })
        } catch (error: Exception) {
            finished()
            log("alarm.receiver_failed", "error=${error.javaClass.simpleName}")
            return
        }
        receiverScope.launch {
            log("alarm.worker_enter", "dispatchDelayMs=${SystemClock.elapsedRealtime() - enteredAt} deadlineExpired=${deadline.isFinished}")
            try {
                if (!usesAlarms()) return@launch
                val action = intent.action
                if (action == ACTION_WAKE) {
                    val generation = intent.getLongExtra(EXTRA_GENERATION, -1)
                    val at = intent.getLongExtra(EXTRA_SCHEDULED_AT, 0)
                    val recovery = intent.getBooleanExtra(EXTRA_RECOVERY, false)
                    log("alarm.delivery", "generation=$generation scheduledAt=$at recovery=$recovery")
                    controller(app).receive(generation, at, recovery) {
                        check(!deadline.isFinished) { "Broadcast deadline elapsed" }
                        startForeground(app, IntentAction.refreshSchedule, at, recovery)
                    }
                } else {
                    // Boot/package/time/permission events repair the plan, never run user scripts here.
                    // Boot/update can erase OS alarms while future persisted timestamps remain.
                    // Therefore force re-arm (not a healthy-plan skip), but preserve an earlier wake.
                    val control = controller(app)
                    val now = System.currentTimeMillis()
                    val earlier = control.snapshot().primaryAt.takeIf { it > now } ?: Long.MAX_VALUE
                    control.schedule(minOf(earlier, now + AutoTaskWakeController.RESTORE_DELAY_MS), "system_restore")
                    log("recovery.system_event", "action=${AutoTaskBatchTrace.field(action)}")
                }
            } catch (error: Exception) {
                log("alarm.receiver_failed", "error=${error.javaClass.simpleName}")
            } finally {
                deadline.complete()
                launchSafely { syncRecoveryWork(app) }
            }
        }
    }

    fun requestStart(context: Context, action: String) {
        val app = context.applicationContext
        launchSafely {
            syncRecoveryWork(app)
            if (foreground.get()) {
                controller(app).requestStart(action) { startForeground(app, action) }
            } else if (app.getPrefBoolean(PreferKey.autoTaskService) && hasScheduledRules()) {
                // Imports/MCP/script calls can change rules in the background; no FGS exemption.
                controller(app).requestContinuation("background_request")
            }
        }
    }

    private fun startForeground(context: Context, action: String, scheduledAt: Long = 0, recovery: Boolean = false) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager?.getNotificationChannel(AppConst.channelIdWeb) == null) {
            manager?.createNotificationChannel(NotificationChannel(AppConst.channelIdWeb,
                context.getString(R.string.web_service), NotificationManager.IMPORTANCE_DEFAULT).apply {
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }
        // Unlike the old getService/startService path, explicitly request foreground execution.
        ContextCompat.startForegroundService(context, Intent(context, AutoTaskService::class.java).apply {
            this.action = action
            putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
            putExtra(EXTRA_RECOVERY, recovery)
        })
    }

    /** Called after normal initialization, but foreground/alarm repair never waits for it. */
    fun onAppReady(context: Context) {
        val app = context.applicationContext
        launchSafely {
            syncRecoveryWork(app)
            if (!app.getPrefBoolean(PreferKey.autoTaskService)) return@launchSafely
            if (!usesAlarms()) return@launchSafely
            controller(app).reconcileBackground("process_bootstrap") { AutoTaskService.hasLiveInstance }
            recoverForeground(app)
        }
    }

    /** Only signal visibility on main. Disk, alarm IPC and repair all run on the IO scope. */
    fun onAppForeground(context: Context) {
        foreground.set(true)
        if (!usesAlarms()) return
        val app = context.applicationContext
        launchSafely {
            syncRecoveryWork(app)
            recoverForeground(app)
        }
    }

    fun onAppBackground() { foreground.set(false) }

    private fun recoverForeground(context: Context) {
        if (!foreground.get()) return
        val force = firstForeground.getAndSet(false)
        controller(context).recoverForeground(force, { AutoTaskService.hasLiveInstance }) {
            startForeground(context, IntentAction.refreshSchedule)
        }
    }

    /** Background repair never claims an exemption to start a foreground service. */
    suspend fun reconcileFromBackground(context: Context, reason: String): Boolean = withContext(Dispatchers.IO) {
        if (!usesAlarms() || !hasScheduledRules()) return@withContext false
        val control = controller(context.applicationContext)
        val repaired = control.reconcileBackground(reason) { AutoTaskService.hasLiveInstance }
        val state = control.snapshot()
        if (context.getPrefBoolean(PreferKey.autoTaskService) && !AutoTaskService.hasLiveInstance &&
            (state.needsRecovery || state.installing) && maxOf(state.primaryAt, state.recoveryAt) <= System.currentTimeMillis()) {
            error("AutoTask background repair failed; no future wake installed")
        }
        repaired
    }

    fun requestBackgroundReconcile(context: Context, reason: String) {
        val app = context.applicationContext
        if (!usesAlarms()) return
        launchSafely {
            if (hasScheduledRules()) controller(app).reconcileBackground(reason) { AutoTaskService.hasLiveInstance }
        }
    }

    fun requestContinuation(context: Context, reason: String, notBefore: Long = 0) {
        if (!usesAlarms()) return
        val app = context.applicationContext
        launchSafely { controller(app).requestContinuation(reason, notBefore) }
    }

    internal fun hasScheduledRules(): Boolean = AutoTask.getRules().any {
        it.enable && CronSchedule.parse(it.cron?.trim().orEmpty()) != null
    }

    private fun syncRecoveryWork(context: Context) {
        runCatching { AutoTaskRecoveryWorker.sync(context) }.onFailure {
            log("recovery.worker_registration_failed", "error=${it.javaClass.simpleName}")
        }
        AutoTaskRecoveryMonitor.sync(context)
    }

    private fun launchSafely(block: () -> Unit) {
        scope.launch {
            try { block() } catch (error: Exception) {
                log("recovery.failed", "error=${error.javaClass.simpleName}")
            }
        }
    }

    private fun log(event: String, details: String) = AutoTaskDiagnostics.record(logId, event, details)
}
