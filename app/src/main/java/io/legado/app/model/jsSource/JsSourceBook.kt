package io.legado.app.model.jsSource

import io.legado.app.R
import io.legado.app.constant.BookSourceType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.ContentEmptyException
import io.legado.app.exception.NoStackTraceException
import io.legado.app.exception.TocEmptyException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.addType
import io.legado.app.help.book.isWebFile
import io.legado.app.help.source.getBookType
import io.legado.app.model.Debug
import kotlinx.coroutines.ensureActive
import splitties.init.appCtx
import kotlin.coroutines.coroutineContext

/**
 * 单文件 JS 书源的五个业务入口：搜索 / 发现 / 详情 / 目录 / 正文。
 *
 * 与声明式书源的区别只在「数据怎么来」：这里调脚本函数并解析 JSON，
 * 之后的入库、缓存、正文保存等仍复用主流程。
 */
object JsSourceBook {

    suspend fun searchAwait(
        source: BookSource,
        key: String,
        page: Int? = 1,
        filter: ((name: String, author: String, kind: String?) -> Boolean)? = null,
    ): ArrayList<SearchBook> {
        val json = JsSourceEngine(source, coroutineContext).callFunction(
            "search",
            listOf("key" to key, "page" to (page ?: 1)),
        )
        Debug.log(source.bookSourceUrl, json.orEmpty(), state = 10)
        val books = JsSourceMarshaller.parseSearchBooks(json, source)
        if (filter != null) books.removeAll { !filter(it.name, it.author, it.kind) }
        Debug.log(source.bookSourceUrl, "◇JS源搜索完成，共${books.size}条")
        return books
    }

    suspend fun exploreAwait(
        source: BookSource,
        url: String,
        page: Int? = 1,
    ): ArrayList<SearchBook> {
        val json = JsSourceEngine(source, coroutineContext).callFunction(
            "explore",
            listOf("url" to url, "page" to (page ?: 1)),
        )
        Debug.log(source.bookSourceUrl, json.orEmpty(), state = 10)
        val books = JsSourceMarshaller.parseSearchBooks(json, source)
        Debug.log(source.bookSourceUrl, "◇JS源发现完成，共${books.size}条")
        return books
    }

    suspend fun getBookInfoAwait(
        source: BookSource,
        book: Book,
        canReName: Boolean,
    ): Book {
        book.addType(source.getBookType())
        val json = JsSourceEngine(source, coroutineContext).callFunctionIfExists(
            "getBookInfo",
            listOf("book" to book),
        )
        if (json == null) {
            Debug.log(source.bookSourceUrl, "≡getBookInfo 未定义或无返回，沿用搜索阶段字段")
        }
        Debug.log(source.bookSourceUrl, json.orEmpty(), state = 20)
        JsSourceMarshaller.mergeBookInfo(book, json, source, canReName)
        if (source.bookSourceType == BookSourceType.file) {
            book.addType(source.getBookType())
        }
        if (!book.isWebFile && book.tocUrl.isBlank()) book.tocUrl = book.bookUrl
        if (book.isWebFile && book.downloadUrls.isNullOrEmpty()) {
            throw NoStackTraceException("下载链接为空")
        }
        return book
    }

    suspend fun getChapterListAwait(
        source: BookSource,
        book: Book,
    ): Result<List<BookChapter>> {
        book.addType(source.getBookType())
        val context = coroutineContext
        return runCatching {
            val json = JsSourceEngine(source, context).callFunction(
                "getChapters",
                listOf("book" to book),
            )
            Debug.log(source.bookSourceUrl, json.orEmpty(), state = 30)
            val chapters = JsSourceMarshaller.parseChapters(json, book, source)
            if (chapters.isEmpty()) {
                throw TocEmptyException(appCtx.getString(R.string.chapter_list_empty))
            }
            book.totalChapterNum = chapters.size
            chapters.lastOrNull()?.let { book.latestChapterTitle = it.title }
            Debug.log(source.bookSourceUrl, "◇JS源目录完成，共${chapters.size}章")
            chapters
        }.onFailure {
            context.ensureActive()
        }
    }

    suspend fun getContentAwait(
        source: BookSource,
        book: Book,
        chapter: BookChapter,
        nextChapterUrl: String? = null,
        needSave: Boolean = true,
    ): String {
        if (chapter.isVolume && chapter.url.startsWith(chapter.title)) {
            Debug.log(source.bookSourceUrl, "⇒一级目录正文不解析")
            return ""
        }
        val content = JsSourceEngine(source, coroutineContext).callFunction(
            "getContent",
            listOf(
                "chapter" to chapter,
                "book" to book,
                "nextChapterUrl" to nextChapterUrl,
            ),
        ).orEmpty()
        if (!chapter.isVolume && content.isBlank()) {
            throw ContentEmptyException("内容为空")
        }
        Debug.log(source.bookSourceUrl, content, state = 40)
        if (needSave) BookHelp.saveContent(source, book, chapter, content)
        return content
    }
}
