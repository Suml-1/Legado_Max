package io.legado.app.service

import org.junit.Assert.*
import org.junit.Test

class AutoTaskContinuationPolicyTest {
    @Test fun checkpointPreservesFirstRunEvenAfterGraceWindow() {
        val original = mapOf("new" to AutoTaskPendingAttempt(0, 10))
        val resumed = AutoTaskContinuationPolicy.reconcile(original, mapOf("new" to 0L), emptySet())
        assertEquals(original, resumed)
    }

    @Test fun deletedDisabledAndCompletedTasksAreRemoved() {
        val original = mapOf("removed" to AutoTaskPendingAttempt(0), "finished" to AutoTaskPendingAttempt(10), "keep" to AutoTaskPendingAttempt(10))
        val resumed = AutoTaskContinuationPolicy.reconcile(original, mapOf("finished" to 20L, "keep" to 10L), emptySet())
        assertEquals(setOf("keep"), resumed.keys)
    }

    @Test fun dueTasksAreAddedOnceAndKeepTheirAttemptOrder() {
        val original = mapOf("slow" to AutoTaskPendingAttempt(10, 100))
        val resumed = AutoTaskContinuationPolicy.reconcile(original, mapOf("slow" to 10L, "new" to 0L), setOf("slow", "new"))
        assertEquals(100L, resumed.getValue("slow").attemptedAt)
        assertEquals(listOf("new", "slow"), AutoTaskContinuationPolicy.orderedIds(resumed))
    }

    @Test fun oldestInterruptedAttemptGetsNextChanceWithoutStarvingUnstartedTasks() {
        val pending = mapOf("recent" to AutoTaskPendingAttempt(0, 500), "old" to AutoTaskPendingAttempt(0, 100), "new" to AutoTaskPendingAttempt(0))
        assertEquals(listOf("new", "old", "recent"), AutoTaskContinuationPolicy.orderedIds(pending))
    }

    @Test fun completionFollowedByNewDueOccurrenceReplacesOldAttempt() {
        val result = AutoTaskContinuationPolicy.reconcile(mapOf("task" to AutoTaskPendingAttempt(0, 500)), mapOf("task" to 1000L), setOf("task"))
        assertEquals(AutoTaskPendingAttempt(1000, 0), result["task"])
    }

    @Test fun serviceBudgetIsAnchoredToOriginalInstanceNotNewStartCommand() {
        assertEquals(150_000L, AutoTaskExecutionBudget.remaining(1000, 1000))
        assertEquals(30_000L, AutoTaskExecutionBudget.remaining(1000, 121000))
        assertEquals(0L, AutoTaskExecutionBudget.remaining(1000, 151000))
        assertEquals(0L, AutoTaskExecutionBudget.remaining(1000, 251000))
        assertTrue(AutoTaskExecutionBudget.SERVICE_BUDGET_MS < 180_000)
        assertTrue(AutoTaskExecutionBudget.BATCH_BUDGET_MS < AutoTaskExecutionBudget.SERVICE_BUDGET_MS)
    }
}
