package io.legado.app.service

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AutoTaskBatchPolicyTest {

    // 固定在 12:57，避开 */30 的整点边界，让断言不受时间抖动影响
    private val now = ZonedDateTime.of(2026, 10, 28, 12, 57, 0, 0, ZoneId.of("Asia/Shanghai"))
        .toInstant()
        .toEpochMilli()


    @Test
    fun cronBoundaryPassedMeansDue() {
        // 每 30 分钟一次，上次执行是两小时前 -> 早就该跑
        val lastRunAt = now - 2 * 60 * 60_000L
        assertEquals(true, AutoTaskBatchPolicy.isDue("*/30 * * * *", lastRunAt, now))
    }

    @Test
    fun justFinishedTaskIsNotDueAgain() {
        val lastRunAt = now - 60_000L
        assertEquals(false, AutoTaskBatchPolicy.isDue("*/30 * * * *", lastRunAt, now))
    }

    @Test
    fun invalidCronIsReportedAsNull() {
        // 与「未到期」区分开，调用方需要提示配置错误
        assertNull(AutoTaskBatchPolicy.isDue("not a cron", now, now))
        assertNull(AutoTaskBatchPolicy.isDue("", now, now))
        assertNull(AutoTaskBatchPolicy.isDue(null, now, now))
    }

    @Test
    fun neverRunTaskWaitsForGracePeriod() {
        // 从未执行过的任务：以 now-5min 为基准，*/30 的下一个触发点在未来 -> 不立即执行
        assertEquals(false, AutoTaskBatchPolicy.isDue("*/30 * * * *", 0L, now))
    }

    @Test
    fun firstRunBaseTimeUsesGracePeriod() {
        assertEquals(now - AutoTaskBatchPolicy.FIRST_RUN_GRACE_MS,
            AutoTaskBatchPolicy.resolveBaseTime(0L, now))
        assertEquals(now - 10_000L, AutoTaskBatchPolicy.resolveBaseTime(now - 10_000L, now))
    }

    @Test
    fun cancellationRecognizedThroughCauseChain() {
        // Rhino 会把取消包进 RhinoInterruptError 再抛出
        val wrapped = Error("rhino interrupt", CancellationException("Job was cancelled"))
        assertTrue(AutoTaskBatchPolicy.isCancellation(wrapped))
    }

    @Test
    fun plainCancellationRecognized() {
        assertTrue(AutoTaskBatchPolicy.isCancellation(CancellationException("Job was cancelled")))
    }

    @Test
    fun ordinaryFailureIsNotCancellation() {
        assertFalse(AutoTaskBatchPolicy.isCancellation(RuntimeException("timeout")))
        assertFalse(
            AutoTaskBatchPolicy.isCancellation(
                RuntimeException("outer", IllegalStateException("inner"))
            )
        )
    }

    @Test
    fun interruptedTaskStaysDueForNextWake() {
        // 核心回归点：任务被中断时不写 lastRunAt，下一次唤醒必须仍然判定为到期
        val lastRunAt = now - 24 * 60 * 60_000L
        assertEquals(true, AutoTaskBatchPolicy.isDue("0 */6 * * *", lastRunAt, now))
        // 若被错误地记为“刚跑过”，就要再等一个完整周期
        assertEquals(false, AutoTaskBatchPolicy.isDue("0 */6 * * *", now, now))
    }
}
