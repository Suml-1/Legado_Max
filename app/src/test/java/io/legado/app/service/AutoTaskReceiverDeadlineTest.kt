package io.legado.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AutoTaskReceiverDeadlineTest {
    @Test
    fun blockedWorkerCannotPreventBroadcastFinish() {
        val timer = Executors.newSingleThreadScheduledExecutor()
        val completed = CountDownLatch(1)
        val timedOut = CountDownLatch(1)
        val count = AtomicInteger()
        try {
            val guard = AutoTaskReceiverDeadline(
                finish = { count.incrementAndGet(); completed.countDown() },
                onTimeout = { timedOut.countDown() },
                timeoutMs = 20, timer = timer,
            )
            // Deliberately never complete the worker; the watchdog runs independently.
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            assertTrue(timedOut.await(3, TimeUnit.SECONDS))
            assertTrue(guard.isFinished)
            guard.complete()
            guard.complete()
            assertEquals(1, count.get())
        } finally {
            timer.shutdownNow()
        }
    }

    @Test
    fun normalFinishCancelsDeadlineAndIsIdempotent() {
        val timer = Executors.newSingleThreadScheduledExecutor()
        val count = AtomicInteger()
        val timedOut = AtomicInteger()
        try {
            val guard = AutoTaskReceiverDeadline({ count.incrementAndGet() }, { timedOut.incrementAndGet() },
                timeoutMs = 60_000, timer = timer)
            assertFalse(guard.isFinished)
            guard.complete()
            guard.complete()
            assertTrue(guard.isFinished)
            assertEquals(1, count.get())
            assertEquals(0, timedOut.get())
        } finally {
            timer.shutdownNow()
        }
    }

    @Test
    fun callbackFailureDoesNotAllowSecondFinish() {
        val timer = Executors.newSingleThreadScheduledExecutor()
        val count = AtomicInteger()
        try {
            val guard = AutoTaskReceiverDeadline({ count.incrementAndGet(); error("callback failure") }, {},
                timeoutMs = 60_000, timer = timer)
            guard.complete()
            guard.complete()
            assertEquals(1, count.get())
        } finally {
            timer.shutdownNow()
        }
    }
}
