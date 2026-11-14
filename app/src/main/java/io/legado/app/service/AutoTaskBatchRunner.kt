package io.legado.app.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicInteger

/** Executes a finite batch with a strict bound on concurrently active actions. */
internal object AutoTaskBatchRunner {

    suspend fun <T> run(
        items: List<T>,
        maxConcurrency: Int,
        action: suspend (T) -> Unit,
    ) {
        require(maxConcurrency > 0) { "maxConcurrency must be positive" }
        if (items.isEmpty()) return
        val nextIndex = AtomicInteger()
        coroutineScope {
            List(minOf(maxConcurrency, items.size)) {
                async(Dispatchers.IO) {
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val index = nextIndex.getAndIncrement()
                        if (index >= items.size) break
                        action(items[index])
                    }
                }
            }.awaitAll()
        }
    }
}
