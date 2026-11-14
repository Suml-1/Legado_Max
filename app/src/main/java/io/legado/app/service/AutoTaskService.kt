package io.legado.app.service

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.model.AutoTask
import io.legado.app.model.AutoTaskRule
import io.legado.app.model.AutoTaskProtocol
import io.legado.app.utils.CronSchedule
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.startForegroundServiceCompat
import io.legado.app.utils.servicePendingIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import io.legado.app.data.appDb
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import splitties.systemservices.notificationManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class AutoTaskService : BaseService() {

    companion object {
        private const val DATA_SYNC_IDLE_CHECK_MS = 60_000L
        private const val DATA_SYNC_MIN_DELAY_MS = 1_000L
        private const val DIAGNOSTIC_HEARTBEAT_MS = 30_000L

        @Volatile
        internal var hasLiveInstance = false
            private set

        var isRun = false
            private set

        fun start(context: Context) {
            dispatchForeground(context, IntentAction.start)
        }

        fun stop(context: Context) {
            context.putPrefBoolean(PreferKey.autoTaskService, false)
            AutoTaskScheduler.cancel(context, "user_stop_request")
            dispatchLogged(IntentAction.stop) {
                context.stopService(Intent(context, AutoTaskService::class.java))
            }
        }

        fun refresh(context: Context) {
            dispatchForeground(context, IntentAction.refreshSchedule)
        }

        private fun dispatchForeground(
            context: Context,
            action: String
        ) {
            if (AutoTaskScheduler.usesAlarms()) {
                AutoTaskScheduler.requestStart(context, action)
                return
            }
            val intent = Intent(context, AutoTaskService::class.java).apply {
                this.action = action
            }
            dispatchLogged(action) {
                context.startForegroundServiceCompat(intent)
            }
        }

        private inline fun dispatchLogged(action: String, block: () -> Unit) {
            val requestId = "dispatch-${Process.myPid()}-${UUID.randomUUID().toString().take(8)}"
            AutoTaskDiagnostics.record(requestId, "dispatch.request", "action=$action")
            try {
                block()
                AutoTaskDiagnostics.record(requestId, "dispatch.returned", "action=$action")
            } catch (error: Throwable) {
                AutoTaskDiagnostics.record(requestId, "dispatch.failed", "action=$action error=${error.javaClass.simpleName}")
                throw error
            }
        }
    }

    private var notificationContent = appCtx.getString(R.string.service_starting)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val notificationStateLock = Any()
    private val activeTaskNames = LinkedHashMap<String, String>()
    private val taskLock = AutoTaskExecutionGuard.mutex
    private val continuation by lazy { AutoTaskContinuationStore(this) }
    private val wakeLease by lazy { AutoTaskWakeLease(this, serviceId) }
    private val budgetExpired = AtomicBoolean()
    private var serviceDeadline: ScheduledFuture<*>? = null
    private var dataSyncLoopJob: Job? = null
    private var diagnosticHeartbeat: Job? = null
    private val serviceId = "${Process.myPid()}-${UUID.randomUUID().toString().take(8)}"
    private val createdElapsed = SystemClock.elapsedRealtime()
    private val cycleSequence = AtomicInteger()
    private val pendingCycles = AtomicInteger()
    private val latestStartId = AtomicInteger()
    // Main-thread registration includes jobs which have not reached the IO dispatcher yet.
    private val alarmJobs = mutableMapOf<Int, Job>()
    @Volatile private var activeBatch: AutoTaskBatchTrace? = null
    @Volatile private var schedulerPhase = "created"
    @Volatile private var lastStopRequest = "none"
    // Observed in this service instance only; unknown does not mean the OS has no alarm.
    @Volatile private var nextAlarmDescription = "unknown"

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(this, AppConst.channelIdWeb)
            .setSmallIcon(R.drawable.ic_web_service_noti)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(getString(R.string.auto_task_service))
            .setContentText(notificationContent)
            .addAction(
                R.drawable.ic_stop_black_24dp,
                getString(R.string.cancel),
                servicePendingIntent<AutoTaskService>(IntentAction.stop)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    override fun onCreate() {
        hasLiveInstance = true
        diagnostic("service.create", "sdk=${Build.VERSION.SDK_INT} " +
            "version=${BuildConfig.VERSION_NAME} code=${BuildConfig.VERSION_CODE} " +
            "mode=${if (useAlarmFgsMode()) "alarm_short_service" else "data_sync_loop"}")
        super.onCreate()
        diagnostic("service.environment", permissionState())
        // A sibling of the executing batch: it must not keep awaitAll/coroutineScope alive.
        diagnosticHeartbeat = lifecycleScope.launch {
            while (isActive) {
                delay(DIAGNOSTIC_HEARTBEAT_MS)
                if (useAlarmFgsMode() || pendingCycles.get() > 0) {
                    diagnostic("service.heartbeat", runtimeSnapshot())
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId.set(startId)
        val scheduledAt = intent?.getLongExtra(AutoTaskScheduler.EXTRA_SCHEDULED_AT, 0L) ?: 0L
        val recovery = intent?.getBooleanExtra(AutoTaskScheduler.EXTRA_RECOVERY, false) == true
        val trigger = when {
            scheduledAt > 0L && recovery -> "recovery_alarm"
            scheduledAt > 0L -> "alarm"
            intent?.action == IntentAction.start -> "start"
            intent?.action == IntentAction.stop -> "stop"
            intent?.action == IntentAction.refreshSchedule -> "refresh"
            else -> "system"
        }
        diagnostic("service.start_command", "startId=$startId trigger=$trigger flags=$flags " +
            "scheduledAt=$scheduledAt deliveryLagMs=${if (scheduledAt > 0L) System.currentTimeMillis() - scheduledAt else 0L} " +
            "enabled=${getPrefBoolean(PreferKey.autoTaskService)}")
        if (intent?.action == IntentAction.stop) {
            putPrefBoolean(PreferKey.autoTaskService, false)
            dataSyncLoopJob?.cancel()
            dataSyncLoopJob = null
            cancelNextAlarm("user_stop")
            requestStop("user_stop")
            return START_NOT_STICKY
        }

        val result = super.onStartCommand(intent, flags, startId)
        if (result == START_NOT_STICKY) {
            diagnostic("service.start_rejected", "startId=$startId recoveryOwner=scheduler ${runtimeSnapshot()}")
            return result
        }
        if (useAlarmFgsMode()) {
            if (budgetExpired.get()) {
                AutoTaskScheduler.requestContinuation(this, "expired_service_request")
                requestStop("budget_exhausted")
                return START_NOT_STICKY
            }
            runDueAndReschedule(startId, trigger)
        } else {
            if (!getPrefBoolean(PreferKey.autoTaskService)) {
                requestStop("disabled", startId)
                return START_NOT_STICKY
            }
            cancelNextAlarm("data_sync_mode")
            runDueNoReschedule(startId, trigger)
            ensureDataSyncLoopRunning()
        }
        return result
    }

    private fun noteForegroundStartDenied(error: Throwable) {
        lastStopRequest = "foreground_denied"
        if (useAlarmFgsMode()) AutoTaskScheduler.noteStartFailure(this)
        diagnostic("service.foreground_denied", "error=${errorTypes(error)} " +
            "${permissionState()} ${runtimeSnapshot()}")
    }

    private fun expireExecutionBudget(reason: String) {
        if (!budgetExpired.compareAndSet(false, true)) return
        diagnostic("execution.budget_exhausted", "reason=$reason ${runtimeSnapshot()}")
        AutoTaskScheduler.requestContinuation(this, reason)
        lifecycleScope.coroutineContext.cancelChildren()
        wakeLease.close()
        requestStop(reason)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        lastStopRequest = "timeout"
        diagnostic("service.timeout", "startId=$startId fgsType=$fgsType ${runtimeSnapshot()}")
        expireExecutionBudget("system_timeout")
        super.onTimeout(startId, fgsType)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        lastStopRequest = "task_removed"
        diagnostic("service.task_removed", runtimeSnapshot())
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Snapshot before cancellation: otherwise an unfinished batch may already look empty.
        diagnostic("service.destroy", "lastStopRequest=$lastStopRequest ${runtimeSnapshot()}")
        hasLiveInstance = false
        serviceDeadline?.cancel(false)
        serviceDeadline = null
        wakeLease.close()
        if (activeBatch != null || pendingCycles.get() > 0) {
            AutoTaskScheduler.requestContinuation(this, "service_destroyed_incomplete")
        }
        diagnosticHeartbeat?.cancel()
        diagnosticHeartbeat = null
        isRun = false
        dataSyncLoopJob?.cancel()
        dataSyncLoopJob = null
        super.onDestroy()
        notificationManager.cancel(NotificationId.AutoTaskService)
    }

    private fun runDueAndReschedule(startId: Int, trigger: String) {
        val job = lifecycleScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                if (!getPrefBoolean(PreferKey.autoTaskService)) {
                    cancelNextAlarm("disabled")
                    return@launch
                }
                val remaining = AutoTaskExecutionBudget.remaining(createdElapsed, SystemClock.elapsedRealtime())
                if (remaining == 0L || withTimeoutOrNull(remaining.coerceAtLeast(1)) {
                    runDueCycle("$trigger:$startId", scheduleAlarm = true)
                    true
                } != true) {
                    diagnostic("execution.slice_expired", "${runtimeSnapshot()}")
                    AutoTaskScheduler.requestContinuation(this@AutoTaskService, "slice_expired")
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    alarmJobs.remove(startId)
                    if (alarmJobs.isEmpty()) {
                        requestStop(if (budgetExpired.get()) "budget_exhausted" else "cycle_finished", latestStartId.get())
                    }
                }
            }
        }
        alarmJobs[startId] = job
        observeJob(job, "alarm_cycle:$startId")
        job.start()
    }

    private fun runDueNoReschedule(startId: Int, trigger: String) {
        val job = lifecycleScope.launch(Dispatchers.IO) {
            if (!getPrefBoolean(PreferKey.autoTaskService)) {
                requestStop("disabled")
                return@launch
            }
            runDueCycle("$trigger:$startId", scheduleAlarm = false)
        }
        observeJob(job, "immediate_cycle:$startId")
    }

    private suspend fun runDueCycle(trigger: String, scheduleAlarm: Boolean) {
        val cycle = cycleSequence.incrementAndGet()
        val queuedAt = SystemClock.elapsedRealtime()
        pendingCycles.incrementAndGet()
        diagnostic("cycle.queued", "cycle=$cycle trigger=$trigger ${runtimeSnapshot()}")
        var outcome = "interrupted"
        try {
            if (!taskLock.tryLock()) {
                outcome = "busy"
                diagnostic("execution.busy", "owner=${AutoTaskExecutionGuard.owner} ${runtimeSnapshot()}")
                // Never forcibly release the old script's lock: side effects could otherwise overlap.
                AutoTaskScheduler.requestContinuation(this, "execution_busy", System.currentTimeMillis() + 5 * 60_000L)
                return
            }
            AutoTaskExecutionGuard.owner = serviceId
            try {
                isRun = true
                diagnostic("cycle.begin", "cycle=$cycle trigger=$trigger " +
                    "lockWaitMs=${SystemClock.elapsedRealtime() - queuedAt}")
                try {
                    if (budgetExpired.get()) return
                    wakeLease.acquire(if (useAlarmFgsMode())
                        AutoTaskExecutionBudget.remaining(createdElapsed, SystemClock.elapsedRealtime()).coerceAtLeast(1)
                    else AutoTaskExecutionBudget.WAKE_LOCK_MS)
                    if (!getPrefBoolean(PreferKey.autoTaskService)) {
                        outcome = "disabled"
                        return
                    }
                    if (scheduleAlarm) {
                        schedulerPhase = "scheduling"
                        // Preserve pre-scheduling: timeout must not prevent the next cron alarm.
                        scheduleNextRunFromRules()
                    }
                    schedulerPhase = "scanning"
                    processDueTasks(cycle)
                    outcome = "completed"
                } finally {
                    wakeLease.close()
                    isRun = false
                    schedulerPhase = "idle"
                }
            } finally {
                AutoTaskExecutionGuard.owner = null
                taskLock.unlock()
            }
        } catch (error: Throwable) {
            outcome = if (AutoTaskBatchPolicy.isCancellation(error)) "interrupted" else "failed"
            diagnostic("cycle.$outcome", "cycle=$cycle error=${errorTypes(error)}")
            throw error
        } finally {
            pendingCycles.decrementAndGet()
            diagnostic("cycle.end", "cycle=$cycle outcome=$outcome " +
                "elapsedMs=${SystemClock.elapsedRealtime() - queuedAt} ${runtimeSnapshot()}")
        }
    }

    private fun ensureDataSyncLoopRunning() {
        if (dataSyncLoopJob?.isActive == true) {
            diagnostic("loop.already_active", runtimeSnapshot())
            return
        }
        if (!hasEnabledRule()) {
            requestStop("no_enabled_rules")
            return
        }
        dataSyncLoopJob = lifecycleScope.launch(Dispatchers.IO) {
            AppLog.put("AutoTask dataSync loop started")
            diagnostic("loop.begin", runtimeSnapshot())
            while (isActive && !useAlarmFgsMode()) {
                if (!getPrefBoolean(PreferKey.autoTaskService)) {
                    break
                }
                val rules = AutoTask.getRules()
                if (!hasEnabledRule(rules)) {
                    break
                }
                val delayMs = computeDataSyncDelayMs(rules)
                diagnostic("loop.sleep", "delayMs=$delayMs expectedWakeAt=${System.currentTimeMillis() + delayMs}")
                delay(delayMs)
                if (!getPrefBoolean(PreferKey.autoTaskService)) {
                    break
                }
                runDueCycle("loop", scheduleAlarm = false)
            }
            AppLog.put("AutoTask dataSync loop stopped")
            dataSyncLoopJob = null
            if (!getPrefBoolean(PreferKey.autoTaskService) || !hasEnabledRule()) {
                requestStop("loop_disabled_or_empty")
            }
        }.also { observeJob(it, "data_sync_loop") }
    }

    private fun observeJob(job: Job, label: String) {
        job.invokeOnCompletion { error ->
            val outcome = when {
                error == null -> "completed"
                AutoTaskBatchPolicy.isCancellation(error) -> "interrupted"
                else -> "failed"
            }
            diagnostic("job.end", "job=$label outcome=$outcome error=${errorTypes(error)} ${runtimeSnapshot()}")
        }
    }

    private fun requestStop(reason: String, startId: Int? = null) {
        lastStopRequest = reason
        diagnostic("service.stop_request", "reason=$reason startId=$startId ${runtimeSnapshot()}")
        if (startId == null) {
            stopSelf()
        } else {
            val accepted = stopSelfResult(startId)
            diagnostic("service.stop_result", "reason=$reason startId=$startId accepted=$accepted")
        }
    }

    private fun diagnostic(event: String, details: String) {
        AutoTaskDiagnostics.record(serviceId, event,
            "serviceElapsedMs=${SystemClock.elapsedRealtime() - createdElapsed} $details")
    }

    private fun runtimeSnapshot(): String = "phase=$schedulerPhase pendingCycles=${pendingCycles.get()} " +
        "loopActive=${dataSyncLoopJob?.isActive} observedNextAlarm=$nextAlarmDescription " +
        (activeBatch?.summary() ?: "batch=none")

    private fun permissionState(): String = runCatching {
        val alarm = getSystemService(AlarmManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarm?.canScheduleExactAlarms()
        } else true
        "exactAlarmAllowed=$exact batteryExempt=${power?.isIgnoringBatteryOptimizations(packageName)} " +
            "deviceIdle=${power?.isDeviceIdleMode} powerSave=${power?.isPowerSaveMode} " +
            "threads=${AppConfig.threadCount} recordLog=${AppConfig.recordLog}"
    }.getOrDefault("environment=unavailable")

    // Do not copy script exceptions' messages/URLs/response bodies into the always-on journal.
    private fun errorTypes(error: Throwable?): String = generateSequence(error) { it.cause }
        .take(8).joinToString("->") { it.javaClass.simpleName }.ifBlank { "none" }

    private fun computeDataSyncDelayMs(rules: List<AutoTaskRule>): Long {
        if (continuation.hasPending()) return DATA_SYNC_IDLE_CHECK_MS
        val nextRunAt = computeNextRunAt(rules) ?: return DATA_SYNC_IDLE_CHECK_MS
        val delayMs = nextRunAt - System.currentTimeMillis()
        return delayMs.coerceAtLeast(DATA_SYNC_MIN_DELAY_MS)
    }

    private suspend fun processDueTasks(cycle: Int) {
        val rules = AutoTask.getRules()
        if (rules.isEmpty()) {
            continuation.clear()
            diagnostic("cycle.scan", "cycle=$cycle rules=0 enabled=0 due=0")
            setNotificationContent(getString(R.string.auto_task_no_task))
            if (useAlarmFgsMode()) {
                cancelNextAlarm("no_rules")
            }
            return
        }
        val enabled = rules.filter { it.enable }
        if (enabled.isEmpty()) {
            continuation.clear()
            diagnostic("cycle.scan", "cycle=$cycle rules=${rules.size} enabled=0 due=0")
            setNotificationContent(getString(R.string.auto_task_no_enabled))
            if (useAlarmFgsMode()) {
                cancelNextAlarm("no_enabled_rules")
            }
            return
        }

        val dueTasks = mutableListOf<AutoTaskRule>()
        val now = System.currentTimeMillis()
        var invalid = 0
        for (task in enabled) {
            currentCoroutineContext().ensureActive()
            val due = AutoTaskBatchPolicy.isDue(task.cron, task.lastRunAt, now)
            if (due == null) {
                invalid++
                updateCronError(task, getString(R.string.auto_task_cron_invalid))
            } else if (due) {
                dueTasks += task
            }
        }
        diagnostic("cycle.scan", "cycle=$cycle now=$now rules=${rules.size} " +
            "enabled=${enabled.size} due=${dueTasks.size} invalidCron=$invalid")

        val valid = enabled.filter { CronSchedule.parse(it.cron?.trim().orEmpty()) != null }
        val orderedIds = continuation.reconcile(valid, dueTasks.map { it.id }.toSet())
        val byId = valid.associateBy { it.id }
        val eligible = continuation.eligibleIds(now)
        val resumable = orderedIds.filter { it in eligible }.mapNotNull(byId::get)
        diagnostic("checkpoint.loaded", "pending=${resumable.size} freshlyDue=${dueTasks.size}")
        if (resumable.isEmpty()) {
            continuation.nextRetryAt()?.let { AutoTaskScheduler.requestContinuation(this, "retry_backoff", it) }
            setNotificationContent(getString(R.string.auto_task_running_state))
            return
        }

        // Reuse the user's update/search thread limit for scheduled tasks as well.
        val maxConcurrency = AppConfig.threadCount.coerceIn(1, 16)
        val trace = AutoTaskBatchTrace(cycle, resumable.size, maxConcurrency, SystemClock::elapsedRealtime)
        activeBatch = trace
        schedulerPhase = "running"
        diagnostic("batch.begin", trace.summary())
        var outcome = "interrupted"
        try {
            val slice = if (useAlarmFgsMode()) minOf(AutoTaskExecutionBudget.BATCH_BUDGET_MS,
                AutoTaskExecutionBudget.remaining(createdElapsed, SystemClock.elapsedRealtime()))
                else AutoTaskExecutionBudget.BATCH_BUDGET_MS
            val finished = withTimeoutOrNull(slice.coerceAtLeast(1)) {
                AutoTaskBatchRunner.run(resumable, maxConcurrency) { scheduled ->
                    currentCoroutineContext().ensureActive()
                    if (!getPrefBoolean(PreferKey.autoTaskService) || budgetExpired.get()) return@run
                    // Re-read before execution: edits/deletions/manual completion can occur while queued.
                    val task = appDb.autoTaskRuleDao.getById(scheduled.id)
                    if (task == null || !task.enable || task.lastRunAt != scheduled.lastRunAt ||
                        task.script != scheduled.script || task.cron != scheduled.cron) {
                        continuation.completed(scheduled.id, scheduled.lastRunAt)
                        return@run
                    }
                    continuation.started(task.id, task.lastRunAt, System.currentTimeMillis())
                    val returned = withTimeoutOrNull(AutoTaskExecutionBudget.TASK_TIMEOUT_MS) {
                        runTask(task, trace)
                        true
                    }
                    if (returned == null) {
                        continuation.defer(task.id, task.lastRunAt, System.currentTimeMillis() + 5 * 60_000L)
                        diagnostic("task.budget_exhausted", "task=${AutoTaskBatchTrace.field(task.id)} completionRecorded=false")
                        currentCoroutineContext().ensureActive()
                        AutoTask.update(task.id) { existing ->
                            if (existing.lastRunAt != task.lastRunAt) existing
                            else existing.copy(lastError = "本次自动执行超过 90 秒，未记为完成，保留待续跑。脚本应支持幂等重试。")
                        }
                    }
                }
                true
            }
            outcome = if (finished == true) "completed" else "yielded"
        } catch (error: Throwable) {
            outcome = if (AutoTaskBatchPolicy.isCancellation(error)) "interrupted" else "failed"
            throw error
        } finally {
            diagnostic("batch.end", "outcome=$outcome ${trace.summary()}")
            activeBatch = null
            continuation.nextRetryAt()?.let { AutoTaskScheduler.requestContinuation(this, "pending_batch", it) }
        }
    }

    private suspend fun scheduleNextRunFromRules() {
        if (!useAlarmFgsMode()) return
        val next = AutoTaskScheduler.scheduleFromRules(this)
        nextAlarmDescription = next?.toString() ?: "not_published"
        next?.let { AppLog.put("AutoTask next run at ${formatTime(it)}") }
    }

    private fun computeNextRunAt(rules: List<AutoTaskRule>): Long? {
        val enabled = rules.filter { it.enable }
        if (enabled.isEmpty()) return null
        val now = System.currentTimeMillis()
        var nextRunAt: Long? = null
        enabled.forEach { task ->
            val cron = task.cron?.trim().orEmpty()
            if (cron.isBlank()) return@forEach
            val schedule = CronSchedule.parse(cron) ?: return@forEach
            val baseTime = AutoTaskBatchPolicy.resolveBaseTime(task.lastRunAt, now)
            val next = schedule.nextTimeAfter(baseTime) ?: return@forEach
            val current = nextRunAt
            nextRunAt = if (current == null) next else minOf(current, next)
        }
        return nextRunAt
    }

    private fun cancelNextAlarm(reason: String) {
        AutoTaskScheduler.cancel(this, reason)
        nextAlarmDescription = "cancelled"
    }

    /** Android 15 起用闹钟驱动 shortService 单次唤醒，而不是常驻循环。 */
    private fun useAlarmFgsMode(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    }

    private fun hasEnabledRule(rules: List<AutoTaskRule>): Boolean {
        return rules.any { it.enable }
    }

    private fun hasEnabledRule(): Boolean {
        return hasEnabledRule(AutoTask.getRules())
    }

    private suspend fun runTask(task: AutoTaskRule, trace: AutoTaskBatchTrace) {
        trace.start(task.id, task.name)
        diagnostic("task.begin", "batch=${trace.id} ${trace.taskSummary(task.id)} " +
            "cron=${AutoTaskBatchTrace.field(task.cron)} lastRunAt=${task.lastRunAt}")
        markTaskStarted(task)
        var completionStatus: String? = null
        var outcome = AutoTaskBatchTrace.Outcome.INTERRUPTED
        val startAt = SystemClock.elapsedRealtime()
        try {
            val script = normalizeScript(task.script)
            if (script.isBlank()) {
                currentCoroutineContext().ensureActive()
                val now = System.currentTimeMillis()
                val message = getString(R.string.auto_task_script_empty)
                outcome = AutoTaskBatchTrace.Outcome.FAILED
                trace.stage(task.id, "persist")
                AutoTask.update(task.id) {
                    it.copy(lastRunAt = now, lastError = message)
                }
                completionStatus = message
                return
            }

            val source = AutoTask.buildSource(task)
            val result = source.evalJS(script)
            trace.stage(task.id, "actions")
            diagnostic("task.stage", "batch=${trace.id} ${trace.taskSummary(task.id)}")
            val logLines = mutableListOf<String>()
            AutoTaskProtocol.handle(result, this@AutoTaskService, task.name) { msg ->
                AppLog.put("AutoTask[${task.id}] ${task.name}: $msg")
                logLines.add(msg)
            }
            currentCoroutineContext().ensureActive()
            val cost = SystemClock.elapsedRealtime() - startAt
            val detail = result?.toString()?.take(200)
            val msg = if (detail.isNullOrBlank()) {
                "AutoTask[${task.id}] ${task.name} done (${cost}ms)."
            } else {
                "AutoTask[${task.id}] ${task.name} done (${cost}ms): $detail"
            }
            val lastRun = System.currentTimeMillis()
            val lastLog = buildLastLog(logLines, detail, cost, lastRun)
            currentCoroutineContext().ensureActive()
            trace.stage(task.id, "persist")
            diagnostic("task.stage", "batch=${trace.id} ${trace.taskSummary(task.id)}")
            AutoTask.update(task.id) {
                it.copy(
                    lastRunAt = lastRun,
                    lastResult = detail,
                    lastError = null,
                    lastLog = lastLog
                )
            }
            completionStatus = getString(
                R.string.auto_task_last_run,
                formatTime(lastRun)
            )
            // Completed means the attempt returned, not that every refreshToc action succeeded.
            outcome = AutoTaskBatchTrace.Outcome.COMPLETED
            AppLog.put(msg)
        } catch (error: Throwable) {
            if (AutoTaskBatchPolicy.isCancellation(error)) {
                // Covers inner task timeout AND outer slice/service cancellation. Delay reattempts
                // instead of replaying an ambiguous side effect every minute.
                continuation.defer(task.id, task.lastRunAt, System.currentTimeMillis() + 5 * 60_000L)
                diagnostic("task.interrupted", "batch=${trace.id} ${trace.taskSummary(task.id)} " +
                    "scopeActive=${currentCoroutineContext().isActive} error=${errorTypes(error)} completionRecorded=false")
                AppLog.put("AutoTask[${task.id}] ${task.name} interrupted; completion not recorded")
                // Re-throw service-scope cancellation, but treat a script-local cancellation as
                // an interrupted task so this worker can continue with other due tasks.
                currentCoroutineContext().ensureActive()
                return
            }
            diagnostic("task.error", "batch=${trace.id} ${trace.taskSummary(task.id)} error=${errorTypes(error)}")
            currentCoroutineContext().ensureActive()
            outcome = AutoTaskBatchTrace.Outcome.FAILED
            trace.stage(task.id, "persist_error")
            val msg = error.localizedMessage ?: error.toString()
            val lastRun = System.currentTimeMillis()
            val lastLog = buildErrorLog(msg, error, lastRun)
            AutoTask.update(task.id) {
                it.copy(
                    lastRunAt = lastRun,
                    lastError = msg,
                    lastLog = lastLog
                )
            }
            completionStatus = getString(R.string.auto_task_failed, msg)
            AppLog.put("AutoTask[${task.id}] ${task.name} failed: $msg", error)
        } finally {
            if (completionStatus != null) continuation.completed(task.id, task.lastRunAt)
            val taskSummary = trace.taskSummary(task.id)
            trace.finish(task.id, outcome)
            diagnostic("task.end", "batch=${trace.id} outcome=$outcome $taskSummary")
            markTaskFinished(task.id, completionStatus)
        }
    }

    private fun normalizeScript(script: String): String {
        return AutoTask.normalizeScript(script)
    }

    private fun formatTime(time: Long): String = synchronized(timeFormat) {
        timeFormat.format(Date(time))
    }

    private fun markTaskStarted(task: AutoTaskRule) {
        synchronized(notificationStateLock) {
            activeTaskNames[task.id] = task.name
            notificationContent = runningTaskNotificationLocked()
            upNotificationLocked()
        }
    }

    private fun markTaskFinished(taskId: String, completionStatus: String?) {
        synchronized(notificationStateLock) {
            activeTaskNames.remove(taskId)
            if (activeTaskNames.isNotEmpty()) {
                notificationContent = runningTaskNotificationLocked()
                upNotificationLocked()
            } else if (completionStatus != null) {
                notificationContent = completionStatus
                upNotificationLocked()
            }
        }
    }

    private fun runningTaskNotificationLocked(): String {
        return getString(
            R.string.auto_task_running,
            activeTaskNames.values.joinToString(", ")
        )
    }

    private fun setNotificationContent(content: String) {
        synchronized(notificationStateLock) {
            notificationContent = content
            upNotificationLocked()
        }
    }

    private fun upNotificationLocked() {
        runCatching {
            notificationBuilder.setContentText(notificationContent)
            notificationManager.notify(NotificationId.AutoTaskService, notificationBuilder.build())
        }.onFailure { error ->
            AppLog.put("AutoTask notification update failed", error)
        }
    }

    private fun updateCronError(task: AutoTaskRule, message: String) {
        if (task.lastError == message) return
        AutoTask.update(task.id) {
            val now = System.currentTimeMillis()
            it.copy(lastError = message, lastLog = buildErrorLog(message, null, now))
        }
    }

    private fun buildLastLog(
        lines: List<String>,
        detail: String?,
        cost: Long,
        runAt: Long
    ): String = AutoTask.buildLastLog(lines, detail, cost, runAt)

    private fun buildErrorLog(msg: String, error: Throwable?, runAt: Long): String =
        AutoTask.buildErrorLog(msg, error, runAt)

    override fun startForegroundNotification() {
        diagnostic("service.foreground_request", "type=${if (useAlarmFgsMode()) "shortService" else "dataSync"}")
        try {
            synchronized(notificationStateLock) {
                notificationBuilder.setContentText(notificationContent)
                val notification = notificationBuilder.build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NotificationId.AutoTaskService,
                        notification,
                        if (useAlarmFgsMode()) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                        } else {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        }
                    )
                } else {
                    startForeground(NotificationId.AutoTaskService, notification)
                }
            }
            if (useAlarmFgsMode() && serviceDeadline == null) {
                serviceDeadline = AutoTaskExecutionGuard.watchdog.schedule({
                    expireExecutionBudget("service_budget")
                }, AutoTaskExecutionBudget.remaining(createdElapsed, SystemClock.elapsedRealtime()).coerceAtLeast(1), TimeUnit.MILLISECONDS)
            }
            diagnostic("service.foreground_started", "type=${if (useAlarmFgsMode()) "shortService" else "dataSync"}")
        } catch (error: Throwable) {
            noteForegroundStartDenied(error)
            throw error
        }
    }
}
