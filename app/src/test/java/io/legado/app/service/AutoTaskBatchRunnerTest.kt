package io.legado.app.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AutoTaskBatchRunnerTest {

    @Test
    fun runsAllItemsWithConfiguredConcurrencyBound() = runBlocking {
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val completed = AtomicInteger()
        val workersReady = AtomicInteger()
        val allWorkersReady = CompletableDeferred<Unit>()

        AutoTaskBatchRunner.run((1..12).toList(), maxConcurrency = 3) {
            val current = active.incrementAndGet()
            maxActive.updateAndGet { previous -> maxOf(previous, current) }
            try {
                if (workersReady.incrementAndGet() == 3) {
                    allWorkersReady.complete(Unit)
                }
                withTimeout(2_000) { allWorkersReady.await() }
                delay(10)
                completed.incrementAndGet()
            } finally {
                active.decrementAndGet()
            }
        }

        assertEquals(12, completed.get())
        assertEquals(3, maxActive.get())
        assertEquals(0, active.get())
    }

    @Test
    fun sliceCancellationStopsTakingNewItemsAndReleasesActiveActions() = runBlocking {
        val started = AtomicInteger()
        val released = AtomicInteger()
        val result = kotlinx.coroutines.withTimeoutOrNull(100) {
            AutoTaskBatchRunner.run((1..50).toList(), 2) {
                started.incrementAndGet()
                try { delay(10_000) } finally { released.incrementAndGet() }
            }
            true
        }
        assertEquals(null, result)
        assertTrue(started.get() <= 2)
        assertEquals(started.get(), released.get())
    }

    @Test
    fun rejectsZeroConcurrency() = runBlocking {
        val failure = runCatching {
            AutoTaskBatchRunner.run(listOf(1), maxConcurrency = 0) {}
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }
}
