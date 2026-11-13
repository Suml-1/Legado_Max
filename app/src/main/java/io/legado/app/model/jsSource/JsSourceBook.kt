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
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.addType
import io.legado.app.help.book.isWebFile
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.source.getBookType
import io.legado.app.model.Debug
import kotlinx.coroutines.ensureActive
import splitties.init.appCtx
import kotlin.coroutines.coroutineContext

/**
 * 单文件 JS 书源的五个业务入口：搜索 / 发现 / 详情 / 目录 / 正文。
 *
 * 与声明式书源的区别只在「数据怎么来」：这里调脚本函数并解析 JSON，之后的入库、缓存、
 * 正文保存、目录字段回填都尽量复用主流程已有的辅助（[ContentProcessor]、[BookHelp] 等）。
 *
 * 约定：脚本返回值不合法时抛异常，让上层按普通书源失败处理；单条数据不合法时只丢弃该条
 * 并记调试日志（见 [JsSourceMarshaller]）。
 */
object JsSourceBook {

    /**
     * 搜索。`filter` 与声明式书源一致，用于「精准搜索」等场景的二次过滤。
     */
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

    /**
     * 发现。`url` 即书源 `exploreUrl` 里配置的地址，脚本自己决定怎么拼参数。
     */
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

    /**
     * 详情。`getBookInfo` 是可选函数：脚本没写就沿用搜索阶段字段，
     * 但仍会补齐 `tocUrl` 兜底、文件源下载链接校验。
     */
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
            throw NoStackTraceException(appCtx.getString(R.string.js_source_download_url_empty))
        }
        return book
    }

    /**
     * 目录。除了返回章节列表，还必须回填 Book 的目录字段：
     * [Book.durChapterTitle]（详情页「目录」行直接显示它）、[Book.totalChapterNum]、
     * [Book.latestChapterTitle]。声明式书源由 `BookChapterList` 统一回填，JS 源这里要自己补，
     * 否则详情页会显示「目录：null」。
     */
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
            updateChapterSummary(book, chapters)
            Debug.log(source.bookSourceUrl, "◇JS源目录完成，共${chapters.size}章")
            chapters
        }.onFailure {
            context.ensureActive()
        }
    }

    /**
     * 正文。`nextChapterUrl` 供需要「下一章地址」参与解密/分页的站点使用；
     * `needSave` 为 false 时只返回内容不落缓存（预览、调试场景）。
     */
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
            throw ContentEmptyException(appCtx.getString(R.string.js_source_content_empty))
        }
        Debug.log(source.bookSourceUrl, content, state = 40)
        if (needSave) BookHelp.saveContent(source, book, chapter, content)
        return content
    }

    /**
     * 按当前阅读进度回填 Book 的目录摘要字段。
     *
     * 标题统一走 [BookChapter.getDisplayTitle]，与声明式书源保持同样的替换规则/简繁处理，
     * 避免 JS 源与规则源在详情页、书架显示的标题不一致。
     */
    private fun updateChapterSummary(book: Book, chapters: List<BookChapter>) {
        val replaceRules = ContentProcessor.get(book).getTitleReplaceRules()
        val replaceBook = book.toReplaceBook()
        val useReplace = book.getUseReplaceRule()
        book.durChapterTitle = chapters.getOrElse(book.durChapterIndex) { chapters.last() }
            .getDisplayTitle(replaceRules, useReplace, replaceBook = replaceBook)
        book.totalChapterNum = chapters.size
        book.latestChapterTitle = chapters
            .getOrElse(book.simulatedTotalChapterNum() - 1) { chapters.last() }
            .getDisplayTitle(replaceRules, useReplace, replaceBook = replaceBook)
    }
}
