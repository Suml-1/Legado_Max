package io.legado.app.help.source

import com.script.rhino.RhinoClassShutter
import io.legado.app.constant.SourceType
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.model.SharedJsScope
import org.htmlunit.corejs.javascript.TopLevel
import kotlin.coroutines.CoroutineContext

/**
 * 取书源共享 jsLib 作用域。
 *
 * 书源（BookSource）额外带上「类访问策略 + 源级命名空间」，
 * 避免同一份 jsLib 在不同书源之间共享同一个可写全局对象。
 */
fun BaseSource.getShareScope(coroutineContext: CoroutineContext? = null): TopLevel? {
    val bookSource = this as? BookSource
    return SharedJsScope.getScope(
        jsLib = jsLib,
        coroutineContext = coroutineContext,
        bookSourceClassPolicy = bookSource != null,
        bookSourceLabel = bookSource?.bookSourceName,
        scopeNamespace = bookSource?.let {
            BookSourceStorageScope.namespace(it.bookSourceUrl)
        }
    )
}

/**
 * 在「书源类访问策略」下执行脚本片段。
 *
 * 声明式规则与单文件 JS 书源都必须经此执行宿主 Java 访问，保证隔离级别一致。
 */
fun <T> BaseSource?.withBookSourceClassPolicy(block: () -> T): T {
    val bookSource = this as? BookSource
    return RhinoClassShutter.withBookSourceClassPolicy(
        enabled = bookSource != null,
        sourceLabel = bookSource?.bookSourceName,
        block = block
    )
}

fun BaseSource.getSourceType(): Int {
    return when (this) {
        is BookSource -> SourceType.book
        is RssSource -> SourceType.rss
        else -> error("unknown source type: ${this::class.simpleName}.")
    }
}
