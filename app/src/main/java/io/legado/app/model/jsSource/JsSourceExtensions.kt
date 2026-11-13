package io.legado.app.model.jsSource

import io.legado.app.data.entities.BookSource

/**
 * 是否单文件 JS 书源。
 *
 * 判据只有 `mainJs` 非空：声明式书源即使写了 jsLib 也仍走 AnalyzeRule 链，
 * 两种书源只在 WebBook 入口分流，避免规则与脚本混跑。
 */
fun BookSource.isJsSource(): Boolean = !mainJs.isNullOrBlank()
