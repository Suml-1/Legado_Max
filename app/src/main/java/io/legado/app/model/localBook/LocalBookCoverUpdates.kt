package io.legado.app.model.localBook

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

/** 保留进程内的逐书封面版本，书架重新可见时也能收到离开期间的更新。 */
internal class LocalBookCoverRevisionStore {
    private val revisions = ConcurrentHashMap<String, Int>()
    private val changes = MutableStateFlow(0)

    fun revisionOf(bookUrl: String): Int = revisions[bookUrl] ?: 0

    fun observe(bookUrl: String): Flow<Int> =
        changes.map { revisionOf(bookUrl) }.distinctUntilChanged()

    @Synchronized
    fun notifyChanged(bookUrl: String) {
        val revision = changes.value + 1
        revisions[bookUrl] = revision
        changes.value = revision
    }
}

internal val localBookCoverUpdates = LocalBookCoverRevisionStore()
