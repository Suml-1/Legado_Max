package io.legado.app.service

import io.legado.app.utils.CronSchedule
import kotlinx.coroutines.CancellationException

/**
 * 定时任务批量执行的纯判断逻辑，与 Android 组件解耦以便单元测试。
 */
internal object AutoTaskBatchPolicy {

    /** 从未执行过的任务以此宽限期为基准，避免新建任务被立刻判定为到期。 */
    const val FIRST_RUN_GRACE_MS = 5 * 60_000L

    private const val MAX_CAUSE_DEPTH = 8

    /**
     * 计算 cron 的下次触发基准时间。
     *
     * 首次执行（[lastRunAt] 为 0）时以当前时刻减去宽限期为基准；否则以上次执行时间为基准，
     * 这样长时间未运行的任务会立即到期。
     */
    fun resolveBaseTime(lastRunAt: Long, now: Long): Long {
        return if (lastRunAt > 0L) lastRunAt else now - FIRST_RUN_GRACE_MS
    }

    /**
     * 判断任务此刻是否到期。
     *
     * @return true 到期；false 未到期；null cron 无法解析（交由调用方提示配置错误）
     */
    fun isDue(cron: String?, lastRunAt: Long, now: Long): Boolean? {
        val schedule = CronSchedule.parse(cron?.trim().orEmpty()) ?: return null
        val nextRun = schedule.nextTimeAfter(resolveBaseTime(lastRunAt, now)) ?: return null
        return nextRun <= now
    }

    /**
     * 判断异常是否代表“作用域被取消”。
     *
     * 服务被系统回收时 lifecycleScope 被取消，运行中的任务会收到 [CancellationException]。
     * Rhino 会把它包进 `RhinoInterruptError` 再抛出，因此要沿 cause 链查找。
     *
     * 这类异常必须原样向上抛：它表示任务被中断，而不是执行失败，不能记为已运行。
     */
    fun isCancellation(error: Throwable): Boolean {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current is CancellationException) return true
            current = current.cause
            depth++
        }
        return false
    }

}
