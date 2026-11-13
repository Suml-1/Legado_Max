package io.legado.app.model.jsSource

import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * 保留取消与策略元素，去掉调度器。
 *
 * 同步 JS 桥（runBlocking 形式）若继续带调度器，会把任务重新派发回自己正在阻塞的线程池。
 */
internal fun CoroutineContext.withoutScriptDispatcher(): CoroutineContext =
    minusKey(ContinuationInterceptor)
