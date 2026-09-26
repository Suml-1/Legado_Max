package io.legado.app.model.webBook

import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.repository.debug.FlowLogRecorder
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.addType
import io.legado.app.help.book.removeAllBookType
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.StrResponse
import io.legado.app.help.source.exploreInfoMapList
import io.legado.app.help.source.getBookType
import io.legado.app.model.Debug
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setToastRuleType
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.RuleData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlin.coroutines.CoroutineContext

/**
 * 书源网络请求核心入口
 *
 * 提供书源相关的所有网络请求操作，包括：
 * - 搜索书籍 [searchBook]
 * - 发现书籍 [exploreBook]
 * - 获取书籍详情 [getBookInfo]
 * - 获取章节目录 [getChapterList]
 * - 获取章节正文 [getContent]
 * - 精准搜索 [preciseSearch]
 *
 * 所有请求方法都支持：
 * - 登录检测 JS 脚本执行
 * - 重定向检测与日志记录
 * - 协程上下文切换
 *
 * @see BookList 书籍列表解析
 * @see BookInfo 书籍详情解析
 * @see BookChapterList 章节目录解析
 * @see BookContent 章节正文解析
 */
@Suppress("MemberVisibilityCanBePrivate")
object WebBook {

    /**
     * 搜索书籍（异步回调方式）
     *
     * @param scope 协程作用域
     * @param bookSource 书源
     * @param key 搜索关键词
     * @param page 页码，默认为1
     * @param context 协程上下文
     * @param start 协程启动模式
     * @param executeContext 执行上下文
     * @return Coroutine 包装的搜索结果列表
     */
    fun searchBook(
        scope: CoroutineScope,
        bookSource: BookSource,
        key: String,
        page: Int? = 1,
        context: CoroutineContext = Dispatchers.IO,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        executeContext: CoroutineContext = Dispatchers.Main,
    ): Coroutine<ArrayList<SearchBook>> {
        return Coroutine.async(scope, context, start = start, executeContext = executeContext) {
            searchBookAwait(bookSource, key, page)
        }
    }

    /**
     * 搜索书籍（挂起函数方式）
     *
     * 根据书源的搜索URL规则发起网络请求，解析返回的书籍列表。
     * 支持登录检测JS脚本，用于验证书源登录状态。
     *
     * @param bookSource 书源
     * @param key 搜索关键词
     * @param page 页码，默认为1
     * @param filter 过滤函数，用于筛选搜索结果（书名、作者、分类）
     * @param shouldBreak 提前终止条件，当返回结果满足条件时停止解析
     * @return 搜索结果列表
     * @throws NoStackTraceException 当搜索URL为空时抛出
     */
    suspend fun searchBookAwait(
        bookSource: BookSource,
        key: String,
        page: Int? = 1,
        filter: ((name: String, author: String, kind: String?) -> Boolean)? = null,
        shouldBreak: ((size: Int) -> Boolean)? = null
    ): ArrayList<SearchBook> {
        FlowLogRecorder.setOperation(bookSource.bookSourceUrl, "搜索")
        val searchUrl = bookSource.searchUrl
        if (searchUrl.isNullOrBlank()) {
            throw NoStackTraceException("搜索url不能为空")
        }
        val ruleData = RuleData()
        val analyzeUrl = AnalyzeUrl(
            mUrl = searchUrl,
            key = key,
            page = page,
            baseUrl = bookSource.bookSourceUrl,
            source = bookSource,
            ruleData = ruleData,
            coroutineContext = currentCoroutineContext()
        )
        val checkJs = bookSource.loginCheckJs
        val res = kotlin.runCatching {
            analyzeUrl.getStrResponseAwait().let {
                if (!checkJs.isNullOrBlank()) { //检测书源是否已登录
                    analyzeUrl.evalJS(checkJs, it) as StrResponse
                } else {
                    it
                }
            }
        }.getOrElse { throwable ->
            if (!checkJs.isNullOrBlank()) {
                val errResponse = analyzeUrl.getErrStrResponse(throwable)
                try {
                    (analyzeUrl.evalJS(checkJs, errResponse) as StrResponse).also {
                        if (it.code() == 500) {
                            throw throwable
                        }
                    }
                } catch (_: Throwable) {
                    throw throwable
                }
            } else {
                throw throwable
            }
        }
        checkRedirect(bookSource, res)
        return BookList.analyzeBookList(
            bookSource = bookSource,
            ruleData = ruleData,
            analyzeUrl = analyzeUrl,
            baseUrl = res.url,
            body = res.body,
            isSearch = true,
            isRedirect = res.raw.priorResponse?.isRedirect == true,
            filter = filter,
            shouldBreak = shouldBreak
        )
    }

    /**
     * 发现书籍（异步回调方式）
     *
     * @param scope 协程作用域
     * @param bookSource 书源
     * @param url 发现URL
     * @param page 页码，默认为1
     * @param context 协程上下文
     * @return Coroutine 包装的发现结果列表
     */
    fun exploreBook(
        scope: CoroutineScope,
        bookSource: BookSource,
        url: String,
        page: Int? = 1,
        context: CoroutineContext = Dispatchers.IO,
    ): Coroutine<List<SearchBook>> {
        return Coroutine.async(scope, context) {
            exploreBookAwait(bookSource, url, page)
        }
    }

    /**
     * 发现书籍（挂起函数方式）
     *
     * 根据书源的发现URL规则发起网络请求，解析返回的书籍列表。
     * 与搜索不同，发现是根据书源预设的分类URL获取书籍列表。
     *
     * @param bookSource 书源
     * @param url 发现URL（从exploreKinds中获取）
     * @param page 页码，默认为1
     * @return 发现结果列表
     */
    suspend fun exploreBookAwait(
        bookSource: BookSource,
        url: String,
        page: Int? = 1,
    ): ArrayList<SearchBook> {
        FlowLogRecorder.setOperation(bookSource.bookSourceUrl, "发现")
        val ruleData = RuleData()
        val sourceUrl = bookSource.bookSourceUrl
        val exploreInfoMap = exploreInfoMapList[sourceUrl]
        val analyzeUrl = AnalyzeUrl(
            mUrl = url,
            page = page,
            baseUrl = sourceUrl,
            source = bookSource,
            ruleData = ruleData,
            coroutineContext = currentCoroutineContext(),
            infoMap = exploreInfoMap
        )
        val checkJs = bookSource.loginCheckJs
        val res = kotlin.runCatching {
            analyzeUrl.getStrResponseAwait().let {
                if (!checkJs.isNullOrBlank()) { //检测书源是否已登录
                    analyzeUrl.evalJS(checkJs, it) as StrResponse
                } else {
                    it
                }
            }
        }.getOrElse { throwable ->
            if (!checkJs.isNullOrBlank()) {
                val errResponse = analyzeUrl.getErrStrResponse(throwable)
                try {
                    (analyzeUrl.evalJS(checkJs, errResponse) as StrResponse).also {
                        if (it.code() == 500) {
                            throw throwable
                        }
                    }
                } catch (_: Throwable) {
                    throw throwable
                }
            } else {
                throw throwable
            }
        }
        checkRedirect(bookSource, res)
        return BookList.analyzeBookList(
            bookSource = bookSource,
            ruleData = ruleData,
            analyzeUrl = analyzeUrl,
            baseUrl = res.url,
            body = res.body,
            isSearch = false
        )
    }

    /**
     * 获取书籍详情（异步回调方式）
     *
     * @param scope 协程作用域
     * @param bookSource 书源
     * @param book 书籍对象（需包含bookUrl）
     * @param context 协程上下文
     * @param canReName 是否允许重命名书名和作者
     * @return Coroutine 包装的书籍对象（已填充详情信息）
     */
    fun getBookInfo(
        scope: CoroutineScope,
        bookSource: BookSource,
        book: Book,
        context: CoroutineContext = Dispatchers.IO,
        canReName: Boolean = true,
    ): Coroutine<Book> {
        return Coroutine.async(scope, context) {
            getBookInfoAwait(bookSource, book, canReName)
        }
    }

    /**
     * 获取书籍详情（挂起函数方式）
     *
     * 请求书籍详情页，解析并填充书籍的详细信息，包括：
     * - 书名、作者、分类、字数
     * - 最新章节、简介、封面
     * - 目录链接或下载链接
     *
     * @param bookSource 书源
     * @param book 书籍对象（需包含bookUrl）
     * @param canReName 是否允许重命名书名和作者（受书源canReName规则控制）
     * @return 已填充详情信息的书籍对象
     */
    suspend fun getBookInfoAwait(
        bookSource: BookSource,
        book: Book,
        canReName: Boolean = true,
    ): Book {
        FlowLogRecorder.setOperation(bookSource.bookSourceUrl, "详情")
        book.removeAllBookType()
        book.addType(bookSource.getBookType())
        if (!book.infoHtml.isNullOrEmpty()) {
            BookInfo.analyzeBookInfo(
                bookSource = bookSource,
                book = book,
                baseUrl = book.bookUrl,
                redirectUrl = book.bookUrl,
                body = book.infoHtml,
                canReName = canReName
            )
        } else {
            val analyzeUrl = AnalyzeUrl(
                mUrl = book.bookUrl,
                baseUrl = bookSource.bookSourceUrl,
                source = bookSource,
                ruleData = book,
                coroutineContext = currentCoroutineContext()
            )
            val checkJs = bookSource.loginCheckJs
            val res = kotlin.runCatching {
                analyzeUrl.getStrResponseAwait().let {
                    if (!checkJs.isNullOrBlank()) { //检测书源是否已登录
                        analyzeUrl.evalJS(checkJs, it) as StrResponse
                    } else {
                        it
                    }
                }
            }.getOrElse { throwable ->
                if (!checkJs.isNullOrBlank()) {
                    val errResponse = analyzeUrl.getErrStrResponse(throwable)
                    try {
                        (analyzeUrl.evalJS(checkJs, errResponse) as StrResponse).also {
                            if (it.code() == 500) {
                                throw throwable
                            }
                        }
                    } catch (_: Throwable) {
                        throw throwable
                    }
                } else {
                    throw throwable
                }
            }
            checkRedirect(bookSource, res)
            BookInfo.analyzeBookInfo(
                bookSource = bookSource,
                book = book,
                baseUrl = book.bookUrl,
                redirectUrl = res.url,
                body = res.body,
                canReName = canReName
            )
        }
        return book
    }

    /**
     * 获取章节目录（异步回调方式）
     *
     * @param scope 协程作用域
     * @param bookSource 书源
     * @param book 书籍对象（需包含tocUrl）
     * @param runPerJs 是否执行目录前置JS
     * @param context 协程上下文
     * @param isFromBookInfo 是否从详情页跳转
     * @return Coroutine 包装的章节列表
     */
    fun getChapterList(
        scope: CoroutineScope,
        bookSource: BookSource,
        book: Book,
        runPerJs: Boolean = false,
        context: CoroutineContext = Dispatchers.IO,
        isFromBookInfo : Boolean = false
    ): Coroutine<List<BookChapter>> {
        return Coroutine.async(scope, context) {
            getChapterListAwait(bookSource, book, runPerJs,isFromBookInfo).getOrThrow()
        }
    }

    /**
     * 执行目录前置JS脚本
     *
     * 在获取目录前执行的JS脚本，用于预处理书籍数据或环境。
     *
     * @param bookSource 书源
     * @param book 书籍对象
     * @param isFromBookInfo 是否从详情页跳转
     * @return 执行结果
     */
    suspend fun runPreUpdateJs(bookSource: BookSource, book: Book, isFromBookInfo : Boolean = false): Result<Unit> {
        return kotlin.runCatching {
            val preUpdateJs = bookSource.ruleToc?.preUpdateJs
            if (!preUpdateJs.isNullOrBlank()) {
                AnalyzeRule(book, bookSource, true, isFromBookInfo)
                    .setCoroutineContext(currentCoroutineContext())
                    .setToastRuleType("TOC")
                    .evalJS(preUpdateJs)
            }
        }.onFailure {
            currentCoroutineContext().ensureActive()
            AppLog.put("执行preUpdateJs规则失败 书源:${bookSource.bookSourceName}", it)
        }
    }

    /**
     * 获取章节目录（挂起函数方式）
     *
     * 请求目录页，解析并返回章节列表。支持：
     * - 多页目录（串行/并发）
     * - 目录反转
     * - 章节标题格式化JS
     * - 去重处理
     *
     * @param bookSource 书源
     * @param book 书籍对象（需包含tocUrl）
     * @param runPerJs 是否执行目录前置JS
     * @param isFromBookInfo 是否从详情页跳转
     * @return 章节列表的Result包装
     */
    suspend fun getChapterListAwait(
        bookSource: BookSource,
        book: Book,
        runPerJs: Boolean = false,
        isFromBookInfo : Boolean = false
    ): Result<List<BookChapter>> {
        FlowLogRecorder.setOperation(bookSource.bookSourceUrl, "目录")
        book.removeAllBookType()
        book.addType(bookSource.getBookType())
        return kotlin.runCatching {
            if (runPerJs) {
                runPreUpdateJs(bookSource, book, isFromBookInfo).getOrThrow()
            }
            if (book.bookUrl == book.tocUrl && !book.tocHtml.isNullOrEmpty()) {
                BookChapterList.analyzeChapterList(
                    bookSource = bookSource,
                    book = book,
                    baseUrl = book.tocUrl,
                    redirectUrl = book.tocUrl,
                    body = book.tocHtml,
                    isFromBookInfo = isFromBookInfo
                )
            } else {
                val analyzeUrl = AnalyzeUrl(
                    mUrl = book.tocUrl,
                    baseUrl = book.bookUrl,
                    source = bookSource,
                    ruleData = book,
                    coroutineContext = currentCoroutineContext()
                )
                val checkJs = bookSource.loginCheckJs
                val res = kotlin.runCatching {
                    analyzeUrl.getStrResponseAwait().let {
                        if (!checkJs.isNullOrBlank()) { //检测书源是否已登录
                            analyzeUrl.evalJS(checkJs, it) as StrResponse
                        } else {
                            it
                        }
                    }
                }.getOrElse { throwable ->
                    if (!checkJs.isNullOrBlank()) {
                        val errResponse = analyzeUrl.getErrStrResponse(throwable)
                        try {
                            (analyzeUrl.evalJS(checkJs, errResponse) as StrResponse).also {
                                if (it.code() == 500) {
                                    throw throwable
                                }
                            }
                        } catch (_: Throwable) {
                            throw throwable
                        }
                    } else {
                        throw throwable
                    }
                }
                checkRedirect(bookSource, res)
                BookChapterList.analyzeChapterList(
                    bookSource = bookSource,
                    book = book,
                    baseUrl = book.tocUrl,
                    redirectUrl = res.url,
                    body = res.body,
                    isFromBookInfo = isFromBookInfo
                )
            }
        }.onFailure {
            currentCoroutineContext().ensureActive()
        }
    }

    /**
     * 渐进式获取章节目录（Flow方式）
     *
     * 与 [getChapterListAwait] 不同，此方法返回 Flow，逐页发射目录结果，
     * 允许在目录未完全加载时就获取已解析的章节，实现"边加载边进入正文"。
     *
     * 处理流程与 [getChapterListAwait] 一致（登录检测、重定向、前置JS等），
     * 区别仅在于最终调用 [BookChapterList.analyzeChapterListFlow] 而非 [BookChapterList.analyzeChapterList]。
     *
     * @param bookSource 书源
     * @param book 书籍对象（需包含tocUrl）
     * @param runPerJs 是否执行目录前置JS
     * @param isFromBookInfo 是否从详情页跳转
     * @return Flow<PartialChapterList> 逐页发射的目录加载结果
     */
    suspend fun getChapterListFlow(
        bookSource: BookSource,
        book: Book,
        runPerJs: Boolean = false,
        isFromBookInfo: Boolean = false
    ): Flow<PartialChapterList> {
        book.removeAllBookType()
        book.addType(bookSource.getBookType())
        if (runPerJs) {
            runPreUpdateJs(bookSource, book, isFromBookInfo).getOrThrow()
        }
        return if (book.bookUrl == book.tocUrl && !book.tocHtml.isNullOrEmpty()) {
            BookChapterList.analyzeChapterListFlow(
                bookSource = bookSource,
                book = book,
                baseUrl = book.tocUrl,
                redirectUrl = book.tocUrl,
                body = book.tocHtml,
                isFromBookInfo = isFromBookInfo
            )
        } else {
            val analyzeUrl = AnalyzeUrl(
                mUrl = book.tocUrl,
                baseUrl = book.bookUrl,
                source = bookSource,
                ruleData = book,
                coroutineContext = currentCoroutineContext()
            )
            val checkJs = bookSource.loginCheckJs
            val res = kotlin.runCatching {
                analyzeUrl.getStrResponseAwait().let {
                    if (!checkJs.isNullOrBlank()) {
                        analyzeUrl.evalJS(checkJs, it) as StrResponse
                    } else {
                        it
                    }
                }
            }.getOrElse { throwable ->
                if (!checkJs.isNullOrBlank()) {
                    val errResponse = analyzeUrl.getErrStrResponse(throwable)
                    try {
                        (analyzeUrl.evalJS(checkJs, errResponse) as StrResponse).also {
                            if (it.code() == 500) {
                                throw throwable
                            }
                        }
                    } catch (_: Throwable) {
                        throw throwable
                    }
                } else {
                    throw throwable
                }
            }
            checkRedirect(bookSource, res)
            BookChapterList.analyzeChapterListFlow(
                bookSource = bookSource,
                book = book,
                baseUrl = book.tocUrl,
                redirectUrl = res.url,
                body = res.body,
                isFromBookInfo = isFromBookInfo
            )
        }
    }

    /**
     * 获取章节正文（异步回调方式）
     *
     * @param scope 协程作用域
     * @param bookSource 书源
     * @param book 书籍对象
     * @param bookChapter 章节对象
     * @param nextChapterUrl 下一章URL（用于多页正文判断终止）
     * @param needSave 是否保存到本地缓存
     * @param context 协程上下文
     * @param start 协程启动模式
     * @param executeContext 执行上下文
     * @param semaphore 并发信号量（用于控制并发数）
     * @return Coroutine 包装的正文内容
     */
    fun getContent(
        scope: CoroutineScope,
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        nextChapterUrl: String? = null,
        needSave: Boolean = true,
        context: CoroutineContext = Dispatchers.IO,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        executeContext: CoroutineContext = Dispatchers.Main,
        semaphore: Semaphore? = null,
    ): Coroutine<String> {
        return Coroutine.async(
            scope,
            context,
            start = start,
            executeContext = executeContext,
            semaphore = semaphore
        ) {
            getContentAwait(bookSource, book, bookChapter, nextChapterUrl, needSave)
        }
    }

    /**
     * 获取章节正文（挂起函数方式）
     *
     * 请求正文页，解析并返回正文内容。支持：
     * - 多页正文（串行/并发）
     * - 副内容（歌词/弹幕）
     * - 全文替换规则
     * - 章节标题规则
     * - HTML格式化
     *
     * @param bookSource 书源
     * @param book 书籍对象
     * @param bookChapter 章节对象
     * @param nextChapterUrl 下一章URL（用于多页正文判断终止）
     * @param needSave 是否保存到本地缓存
     * @return 正文内容字符串
     * @throws ContentEmptyException 当正文为空时抛出
     */
    suspend fun getContentAwait(
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        nextChapterUrl: String? = null,
        needSave: Boolean = true
    ): String {
        FlowLogRecorder.setOperation(bookSource.bookSourceUrl, "正文")
        val contentRule = bookSource.getContentRule()
        if (contentRule.content.isNullOrEmpty()) {
            Debug.log(bookSource.bookSourceUrl, "⇒正文规则为空,使用章节链接:${bookChapter.url}")
            return bookChapter.url
        }
        if (bookChapter.isVolume && bookChapter.url.startsWith(bookChapter.title)) {
            Debug.log(bookSource.bookSourceUrl, "⇒一级目录正文不解析规则")
            return bookChapter.tag ?: ""
        }
        return if (bookChapter.url == book.bookUrl && !book.tocHtml.isNullOrEmpty()) {
            BookContent.analyzeContent(
                bookSource = bookSource,
                book = book,
                bookChapter = bookChapter,
                baseUrl = bookChapter.getAbsoluteURL(),
                redirectUrl = bookChapter.getAbsoluteURL(),
                body = book.tocHtml,
                nextChapterUrl = nextChapterUrl,
                needSave = needSave
            )
        } else {
            val analyzeUrl = AnalyzeUrl(
                mUrl = bookChapter.getAbsoluteURL(),
                baseUrl = book.tocUrl,
                source = bookSource,
                ruleData = book,
                chapter = bookChapter,
                coroutineContext = currentCoroutineContext()
            )
            val checkJs = bookSource.loginCheckJs
            val res = kotlin.runCatching {
                analyzeUrl.getStrResponseAwait(
                    jsStr = contentRule.webJs,
                    sourceRegex = contentRule.sourceRegex
                ).let {
                    if (!checkJs.isNullOrBlank()) { //检测书源是否已登录
                        analyzeUrl.evalJS(checkJs, it) as StrResponse
                    } else {
                        it
                    }
                }
            }.getOrElse { throwable ->
                if (!checkJs.isNullOrBlank()) {
                    val errResponse = analyzeUrl.getErrStrResponse(throwable)
                    try {
                        (analyzeUrl.evalJS(checkJs, errResponse) as StrResponse).also {
                            if (it.code() == 500) {
                                throw throwable
                            }
                        }
                    } catch (_: Throwable) {
                        throw throwable
                    }
                } else {
                    throw throwable
                }
            }
            checkRedirect(bookSource, res)
            BookContent.analyzeContent(
                bookSource = bookSource,
                book = book,
                bookChapter = bookChapter,
                baseUrl = bookChapter.getAbsoluteURL(),
                redirectUrl = res.url,
                body = res.body,
                nextChapterUrl = nextChapterUrl,
                needSave = needSave
            )
        }
    }

    suspend fun getContentLazyAwait(
        scope: CoroutineScope,
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        nextChapterUrl: String? = null,
        callback: LazyContentCallback? = null
    ): Pair<String, LazyContentManager?> {
        FlowLogRecorder.setOperation(bookSource.bookSourceUrl, "正文(懒加载)")
        val contentRule = bookSource.getContentRule()
        if (contentRule.content.isNullOrEmpty()) {
            Debug.log(bookSource.bookSourceUrl, "⇒正文规则为空,使用章节链接:${bookChapter.url}")
            return Pair(bookChapter.url, null)
        }
        if (bookChapter.isVolume && bookChapter.url.startsWith(bookChapter.title)) {
            Debug.log(bookSource.bookSourceUrl, "⇒一级目录正文不解析规则")
            return Pair(bookChapter.tag ?: "", null)
        }
        return if (bookChapter.url == book.bookUrl && !book.tocHtml.isNullOrEmpty()) {
            BookContent.analyzeContentLazy(
                scope = scope,
                bookSource = bookSource,
                book = book,
                bookChapter = bookChapter,
                baseUrl = bookChapter.getAbsoluteURL(),
                redirectUrl = bookChapter.getAbsoluteURL(),
                body = book.tocHtml,
                nextChapterUrl = nextChapterUrl,
                callback = callback
            )
        } else {
            val analyzeUrl = AnalyzeUrl(
                mUrl = bookChapter.getAbsoluteURL(),
                baseUrl = book.tocUrl,
                source = bookSource,
                ruleData = book,
                chapter = bookChapter,
                coroutineContext = currentCoroutineContext()
            )
            val checkJs = bookSource.loginCheckJs
            val res = kotlin.runCatching {
                analyzeUrl.getStrResponseAwait(
                    jsStr = contentRule.webJs,
                    sourceRegex = contentRule.sourceRegex
                ).let {
                    if (!checkJs.isNullOrBlank()) {
                        analyzeUrl.evalJS(checkJs, it) as StrResponse
                    } else {
                        it
                    }
                }
            }.getOrElse { throwable ->
                if (!checkJs.isNullOrBlank()) {
                    val errResponse = analyzeUrl.getErrStrResponse(throwable)
                    try {
                        (analyzeUrl.evalJS(checkJs, errResponse) as StrResponse).also {
                            if (it.code() == 500) {
                                throw throwable
                            }
                        }
                    } catch (_: Throwable) {
                        throw throwable
                    }
                } else {
                    throw throwable
                }
            }
            checkRedirect(bookSource, res)
            BookContent.analyzeContentLazy(
                scope = scope,
                bookSource = bookSource,
                book = book,
                bookChapter = bookChapter,
                baseUrl = bookChapter.getAbsoluteURL(),
                redirectUrl = res.url,
                body = res.body,
                nextChapterUrl = nextChapterUrl,
                callback = callback
            )
        }
    }

    /**
     * 精准搜索（异步回调方式）
     *
     * 遍历书源列表，按书名+作者精确匹配书籍。
     * 找到第一个匹配结果后立即返回。
     *
     * @param scope 协程作用域
     * @param bookSourceParts 待搜索的书源列表
     * @param name 书名
     * @param author 作者
     * @param context 协程上下文
     * @param semaphore 并发信号量
     * @return Coroutine 包装的书籍和书源对
     */
    fun preciseSearch(
        scope: CoroutineScope,
        bookSourceParts: List<BookSourcePart>,
        name: String,
        author: String,
        context: CoroutineContext = Dispatchers.IO,
        semaphore: Semaphore? = null,
    ): Coroutine<Pair<Book, BookSource>> {
        return Coroutine.async(scope, context, semaphore = semaphore) {
            for (s in bookSourceParts) {
                val source = s.getBookSource() ?: continue
                val book = preciseSearchAwait(source, name, author).getOrNull()
                if (book != null) {
                    return@async Pair(book, source)
                }
            }
            throw NoStackTraceException("没有搜索到<$name>$author")
        }
    }

    /**
     * 精准搜索（挂起函数方式）
     *
     * 在单个书源中按书名+作者精确匹配书籍。
     * 使用filter过滤结果，shouldBreak在找到第一个匹配后停止。
     *
     * @param bookSource 书源
     * @param name 书名
     * @param author 作者
     * @return 搜索结果的Result包装
     */
    suspend fun preciseSearchAwait(
        bookSource: BookSource,
        name: String,
        author: String,
    ): Result<Book> {
        return kotlin.runCatching {
            currentCoroutineContext().ensureActive()
            searchBookAwait(
                bookSource, name,
                filter = { fName, fAuthor, _ -> fName == name && fAuthor == author },
                shouldBreak = { it > 0 }
            ).firstOrNull()?.let { searchBook ->
                currentCoroutineContext().ensureActive()
                return@runCatching searchBook.toBook()
            }
            throw NoStackTraceException("未搜索到 $name($author) 书籍")
        }.onFailure {
            currentCoroutineContext().ensureActive()
        }
    }

    /**
     * 检测重定向
     *
     * 检查响应是否发生重定向，并记录调试日志。
     *
     * @param bookSource 书源（用于日志记录）
     * @param response 响应对象
     */
    private fun checkRedirect(bookSource: BookSource, response: StrResponse) {
        response.raw.priorResponse?.let {
            if (it.isRedirect) {
                Debug.log(bookSource.bookSourceUrl, "≡检测到重定向(${it.code})")
                Debug.log(bookSource.bookSourceUrl, "┌重定向后地址")
                Debug.log(bookSource.bookSourceUrl, "└${response.url}")
            }
        }
    }

}