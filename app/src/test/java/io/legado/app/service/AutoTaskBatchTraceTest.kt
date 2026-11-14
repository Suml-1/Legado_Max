package io.legado.app.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class AutoTaskBatchTraceTest {
    @Test
    fun snapshotSeparatesQueuedRunningAndFinishedTasks() {
        val clock = AtomicLong(1_000)
        val trace = AutoTaskBatchTrace(7, 3, 2, clock::get)
        trace.start("a", "第一项")
        trace.start("b", "第二项")
        trace.stage("b", "actions")
        clock.addAndGet(1_500)
        trace.finish("a", AutoTaskBatchTrace.Outcome.COMPLETED)

        val summary = trace.summary()
        assertTrue(summary.contains("batch=7 total=3 concurrency=2 started=2 completed=1"))
        assertTrue(summary.contains("pending=1 active=1 elapsedMs=1500"))
        assertTrue(summary.contains("\"b\":\"第二项\":actions:1500ms"))
        assertFalse(summary.contains("\"第一项\""))
    }

    @Test
    fun outcomesStayDistinctAndRepeatedFinishIsIgnored() {
        val trace = AutoTaskBatchTrace(1, 3, 3) { 0L }
        listOf("a", "b", "c").forEach { trace.start(it, it) }
        trace.finish("a", AutoTaskBatchTrace.Outcome.COMPLETED)
        trace.finish("b", AutoTaskBatchTrace.Outcome.FAILED)
        trace.finish("c", AutoTaskBatchTrace.Outcome.INTERRUPTED)
        trace.finish("c", AutoTaskBatchTrace.Outcome.COMPLETED)
        assertTrue(trace.summary().contains("completed=1 failed=1 interrupted=1 pending=0 active=0"))
    }

    @Test
    fun importedNamesAreBoundedQuotedAndSingleLine() {
        assertEquals("\"x y \\\"z\\\"\\\\\"", AutoTaskBatchTrace.field("x\ny\t\"z\"\\"))
        assertEquals(122, AutoTaskBatchTrace.field("a".repeat(500)).length)
        assertEquals("\"a b c\"", AutoTaskBatchTrace.field("a\u2028b\u2029c"))
        val trace = AutoTaskBatchTrace(1, 100, 100) { 0L }
        repeat(100) { trace.start("id$it", "name$it") }
        trace.start("id0", "duplicate")
        assertTrue(trace.summary().contains("started=100"))
        assertTrue(trace.summary().contains("active=100"))
        assertTrue(trace.summary().contains("omittedActive=92"))
        assertTrue(trace.summary().length < 2_000)
    }

    @Test
    fun concurrentWorkersDoNotLoseCounts() {
        val trace = AutoTaskBatchTrace(1, 200, 8) { 0L }
        val executor = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..200).map { id ->
                executor.submit {
                    trace.start(id.toString(), "任务$id")
                    trace.stage(id.toString(), "persist")
                    trace.summary()
                    trace.finish(id.toString(), AutoTaskBatchTrace.Outcome.COMPLETED)
                }
            }
            futures.forEach { it.get(5, TimeUnit.SECONDS) }
            assertTrue(trace.summary().contains("started=200 completed=200"))
            assertTrue(trace.summary().contains("pending=0 active=0"))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun unfinishedItemRemainsVisibleUntilParentCancellation() = runBlocking {
        val clock = AtomicLong(0)
        val trace = AutoTaskBatchTrace(1, 3, 1, clock::get)
        val blocked = CompletableDeferred<Unit>()
        val batch = launch {
            AutoTaskBatchRunner.run(listOf("done", "blocked", "pending"), 1) { id ->
                trace.start(id, id)
                var outcome = AutoTaskBatchTrace.Outcome.COMPLETED
                try {
                    if (id == "blocked") {
                        trace.stage(id, "actions")
                        blocked.complete(Unit)
                        awaitCancellation()
                    }
                } catch (error: CancellationException) {
                    outcome = AutoTaskBatchTrace.Outcome.INTERRUPTED
                    throw error
                } finally {
                    trace.finish(id, outcome)
                }
            }
        }
        try {
            withTimeout(5_000) { blocked.await() }
            clock.set(180_000)
            val snapshot = trace.summary()
            assertTrue(snapshot.contains("completed=1 failed=0 interrupted=0 pending=1 active=1"))
            assertTrue(snapshot.contains("actions:180000ms"))
        } finally {
            batch.cancelAndJoin()
        }
        assertTrue(trace.summary().contains("completed=1 failed=0 interrupted=1 pending=1 active=0"))
        assertTrue(batch.isCancelled)
    }
}
