package io.legado.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.legado.app.service.AutoTaskScheduler

/** Receives a wake even when no service/process is alive; never evaluates task scripts. */
class AutoTaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!AutoTaskScheduler.usesAlarms()) return
        if (intent.action !in setOf(
                AutoTaskScheduler.ACTION_WAKE,
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            )) return
        val pending = goAsync()
        AutoTaskScheduler.receive(context, intent) { pending.finish() }
    }
}
