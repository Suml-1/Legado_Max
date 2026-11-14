package io.legado.app.model

import android.annotation.SuppressLint
import android.util.Log
import io.legado.app.BuildConfig
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.*
import io.legado.app.data.repository.debug.DebugEventCenter
import io.legado.app.help.book.isWebFile
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.CompositeCoroutine
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.source.sortUrls
import io.legado.app.model.debug.DebugCategory
import io.legado.app.model.debug.DebugEvent
import io.legado.app.model.debug.DebugLevel
import io.legado.app.model.debug.SourceSubCategory
import io.legado.app.model.rss.Rss
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

object Debug {
    var callback: Callback? = null
    private var debugSource: String? = null
    private var localDebugSession: String? = null
    private val tasks: CompositeCoroutine = CompositeCoroutine()
    val debugMessageMap = HashMap<String, String>()
    private val debugTimeMap = HashMap<String, Long>()
    var isChecking: Boolean = false

    @SuppressLint("ConstantLocale")
    private val debugTimeFormat = SimpleDateFormat("[mm:ss.SSS]", Locale.getDefault())
    private var startTime: Long = System.currentTimeMillis()
    private val dataUrlBase64Regex =
        Regex("""data:([^;,\s"'<>)]*)?;base64,[A-Za-z0-9+/=_-]+""", RegexOption.IGNORE_CASE)

    private fun sanitizeDebugMessage(msg: String): String {
        if (!msg.contains("data:", ignoreCase = true) ||
            !msg.contains("base64,", ignoreCase = true)
        ) {
            return msg
        }
        return dataUrlBase64Regex.replace(msg) { matchResult ->
            val fullMatch = matchResult.value
            // 数据较短时不折叠，直接原样返回
            if (fullMatch.length <= 50) return@replace fullMatch
            // 保留前50个字符（含 data:mime;base64, 前缀及部分 base64 数据），后续折叠
            "${fullMatch.take(50)}…[base64字符过长省略输出, length=${fullMatch.length}]"
        }
    }

    /**
     * 记录日志
     * @param sourceUrl 书源URL
     * @param msg 日志消息
     * @param print 是否打印
     * @param isHtml 是否为HTML格式
     * @param showTime 是否显示时间
     * @param state 状态码
     */
    @Synchronized
    fun log(
        sourceUrl: String?,
        msg: String = "",
        print: Boolean = true,
        isHtml: Boolean = false,
        showTime: Boolean = true,
        state: Int = 1,
        category: DebugCategory? = null
    ) {
        val safeMsg = sanitizeDebugMessage(msg)
        if (BuildConfig.DEBUG) {
            Log.d("sourceDebug", safeMsg)
        }
        //调试信息始终要执行
        callback?.let {
            if ((debugSource != sourceUrl || !print)) return
            var printMsg = safeMsg
            if (isHtml) {
                printMsg = HtmlFormatter.format(safeMsg)
            }
            if (showTime) {
                val time = debugTimeFormat.format(Date(System.currentTimeMillis() - startTime))
                printMsg = "$time $printMsg"
            }
            it.printLog(state, printMsg)
        }

        // 在锁外异步上报到调试事件中心，避免持锁期间启动协程
        if (DebugEventCenter.isEnabled) {
            val capturedMsg = safeMsg
            val capturedSourceUrl = sourceUrl
            val capturedState = state
            val capturedIsHtml = isHtml
            val capturedShowTime = showTime
            val capturedStartTime = startTime
            val capturedCategory = category

            GlobalScope.launch(Dispatchers.Default) {
                var printMsg = capturedMsg
                if (capturedIsHtml) {
                    printMsg = HtmlFormatter.format(capturedMsg)
                }
                if (capturedShowTime) {
                    val time = debugTimeFormat.format(Date(System.currentTimeMillis() - capturedStartTime))
                    printMsg = "$time $printMsg"
                }

            // 根据category参数或sourceUrl判断分类
            val eventCategory = capturedCategory ?: when {
                capturedSourceUrl?.contains("rss", ignoreCase = true) == true -> DebugCategory.RSS
                else -> DebugCategory.RULE  // 默认归为规则执行
            }

            DebugEventCenter.emit(
                DebugEvent(
                    level = when {
                        capturedState < 0 -> DebugLevel.ERROR
                        capturedState == 0 -> DebugLevel.WARN
                        else -> DebugLevel.DEBUG
                    },
                    category = eventCategory,
                    subCategory = when (eventCategory) {
                        DebugCategory.RULE -> SourceSubCategory.RULE
                        DebugCategory.SOURCE -> SourceSubCategory.RULE
                        DebugCategory.RSS -> SourceSubCategory.RULE
                        else -> null
                    },
                    message = printMsg,
                    detail = capturedMsg,
                    sourceUrl = capturedSourceUrl,
                    tags = mapOf("state" to capturedState.toString())
                )
            )
        }
        }

        if (isChecking && sourceUrl != null && safeMsg.length < 30) {
            var printMsg = safeMsg
            if (isHtml) {
                printMsg = HtmlFormatter.format(safeMsg)
            }
            if (showTime && debugTimeMap[sourceUrl] != null) {
                val time =
                    debugTimeFormat.format(Date(System.currentTimeMillis() - debugTimeMap[sourceUrl]!!))
                printMsg = printMsg.replace(AppPattern.debugMessageSymbolRegex, "")

                debugMessageMap[sourceUrl] = "$time $printMsg"
            }
        }
    }

    /**
     * 记录日志
     * @param msg 日志消息
     */
    @Synchronized
    fun log(msg: String?) {
        log(debugSource, msg ?: "", true)
    }

    /**
     * 记录RSS源日志
     * @param sourceUrl RSS源URL
     * @param msg 日志消息
     * @param print 是否打印
     * @param isHtml 是否为HTML格式
     * @param showTime 是否显示时间
     * @param state 状态码
     */
    fun logRss(
        sourceUrl: String?,
        msg: String = "",
        print: Boolean = true,
        isHtml: Boolean = false,
        showTime: Boolean = true,
        state: Int = 1
    ) {
        log(sourceUrl, msg, print, isHtml, showTime, state, DebugCategory.RSS)
    }

    /**
     * 取消调试
     * @param destroy 是否销毁
     */
    fun cancelDebug(destroy: Boolean = false) {
        tasks.clear()

        if (destroy) {
            debugSource = null
            localDebugSession = null
            callback = null
        }
    }

    /**
     * 只注册调试目标、不走任何规则的调试会话（定时任务脚本试运行用）
     */
    fun startSimpleDebug(sourceUrl: String) {
        cancelDebug()
        debugSource = sourceUrl
        startTime = System.currentTimeMillis()
    }

    /**
     * 当前源是否处于本地调试会话：本地调试下流水线不发网络请求，规则直跑
     */
    fun isLocalDebug(sourceUrl: String?): Boolean {
        return localDebugSession != null && localDebugSession == sourceUrl
    }

    /**
     * 开始校验
     * @param source 书源
     */
    fun startChecking(source: BookSource) {
        isChecking = true
        debugTimeMap[source.bookSourceUrl] = System.currentTimeMillis()
        debugMessageMap[source.bookSourceUrl] = "${debugTimeFormat.format(Date(0))} 开始校验"
    }

    /**
     * 完成校验
     */
    fun finishChecking() {
        isChecking = false
    }

    /**
     * 获取响应时间
     * @param sourceUrl 书源URL
     * @return 响应时间
     */
    fun getRespondTime(sourceUrl: String): Long {
        return debugTimeMap[sourceUrl] ?: CheckSource.timeout
    }

    /**
     * 更新最终消息
     * @param sourceUrl 书源URL
     * @param state 状态字符串
     */
    fun updateFinalMessage(sourceUrl: String, state: String) {
        if (debugTimeMap[sourceUrl] != null && debugMessageMap[sourceUrl] != null) {
            val spendingTime = System.currentTimeMillis() - debugTimeMap[sourceUrl]!!
            debugTimeMap[sourceUrl] =
                if (state == "校验成功") spendingTime else CheckSource.timeout + spendingTime
            val printTime = debugTimeFormat.format(Date(spendingTime))
            debugMessageMap[sourceUrl] = "$printTime $state"
        }
    }

    /**
     * 开始调试RSS源
     * @param scope 协程作用域
     * @param rssSource RSS源
     * @param localDebug 是否本地调试（规则直跑，流水线不发网络请求）
     */
    suspend fun startDebug(scope: CoroutineScope, rssSource: RssSource, localDebug: Boolean = false) {
        cancelDebug()
        debugSource = rssSource.sourceUrl
        localDebugSession = if (localDebug) rssSource.sourceUrl else null
        logRss(debugSource, "︾开始解析")
        val sort = rssSource.sortUrls().first()
        Rss.getArticles(scope, sort.first, sort.second, rssSource, 1)
            .onSuccess {
                if (it.first.isEmpty()) {
                    logRss(debugSource, "⇒列表页解析成功，为空")
                    logRss(debugSource, "︽解析完成", state = 1000)
                } else {
                    val ruleContent = rssSource.ruleContent
                    if (!rssSource.ruleArticles.isNullOrBlank() && rssSource.ruleDescription.isNullOrBlank()) {
                        logRss(debugSource, "︽列表页解析完成")
                        logRss(debugSource, showTime = false)
                        if (ruleContent.isNullOrEmpty()) {
                            logRss(debugSource, "⇒内容规则为空，默认获取整个网页", state = 1000)
                        } else {
                            rssContentDebug(scope, it.first[0], ruleContent, rssSource)
                        }
                    } else {
                        logRss(debugSource, "⇒存在描述规则，不解析内容页")
                        logRss(debugSource, "︽解析完成", state = 1000)
                    }
                }
            }
            .onError {
                logRss(debugSource, it.stackTraceStr, state = -1)
            }
    }

    /**
     * 开始调试RSS源
     * @param scope 协程作用域
     * @param rssSource RSS源
     * @param key 关键字
     * @param localDebug 是否本地调试（规则直跑，流水线不发网络请求）
     */
    fun startDebug(scope: CoroutineScope, rssSource: RssSource, key: String, localDebug: Boolean = false) {
        cancelDebug()
        debugSource = rssSource.sourceUrl
        localDebugSession = if (localDebug) rssSource.sourceUrl else null
        startTime = System.currentTimeMillis()
        when {
            key.contains("::") -> {
                val name = key.substringBefore("::")
                val url = key.substringAfter("::")
                logRss(debugSource, "⇒开始访问分类页:$url")
                logRss(debugSource, "︾开始解析分类页")
                sortDebug(scope, rssSource, name, url)
            }

            key.isAbsUrl() -> {
                val ruleContent = rssSource.ruleContent
                if (!rssSource.ruleArticles.isNullOrBlank() && rssSource.ruleDescription.isNullOrBlank()) {
                    if (ruleContent.isNullOrEmpty()) {
                        logRss(debugSource, "⇒内容规则为空，默认获取整个网页", state = 1000)
                    } else {
                        val rssArticle = RssArticle()
                        rssArticle.origin = rssSource.sourceUrl
                        rssArticle.link = key
                        logRss(debugSource, "⇒开始访问内容页:$key")
                        rssContentDebug(scope, rssArticle, ruleContent, rssSource)
                    }
                } else {
                    logRss(debugSource, "⇒存在描述规则，不解析内容页")
                    logRss(debugSource, "︽解析完成", state = 1000)
                }
            }

            else -> {
                val searchUrl = rssSource.searchUrl
                if (searchUrl.isNullOrEmpty()) {
                    logRss(debugSource, "⇒搜索URL为空", state = -1)
                    return
                }
                logRss(debugSource, "⇒开始搜索关键字:$key")
                logRss(debugSource, "︾开始解析搜索页")
                sortDebug(scope, rssSource, "搜索", searchUrl, key)
            }
        }
    }

    /**
     * 分类调试
     * @param scope 协程作用域
     * @param rssSource RSS源
     * @param name 名称
     * @param url URL
     * @param key 关键字
     */
    private fun sortDebug(scope: CoroutineScope, rssSource: RssSource, name: String, url: String, key: String? = null) {
        Rss.getArticles(scope, name, url, rssSource, 1, key)
            .onSuccess {
                if (it.first.isEmpty()) {
                    logRss(debugSource, "⇒列表页解析成功，为空")
                    logRss(debugSource, "︽解析完成", state = 1000)
                } else {
                    val ruleContent = rssSource.ruleContent
                    if (!rssSource.ruleArticles.isNullOrBlank() && rssSource.ruleDescription.isNullOrBlank()) {
                        logRss(debugSource, "︽列表页解析完成")
                        logRss(debugSource, showTime = false)
                        if (ruleContent.isNullOrEmpty()) {
                            logRss(debugSource, "⇒内容规则为空，默认获取整个网页", state = 1000)
                        } else {
                            rssContentDebug(scope, it.first[0], ruleContent, rssSource)
                        }
                    } else {
                        logRss(debugSource, "⇒存在描述规则，不解析内容页")
                        logRss(debugSource, "︽解析完成", state = 1000)
                    }
                }
            }
            .onError {
                logRss(debugSource, it.stackTraceStr, state = -1)
            }
    }

    /**
     * RSS内容调试
     * @param scope 协程作用域
     * @param rssArticle RSS文章
     * @param ruleContent 内容规则
     * @param rssSource RSS源
     */
    private fun rssContentDebug(
        scope: CoroutineScope,
        rssArticle: RssArticle,
        ruleContent: String,
        rssSource: RssSource
    ) {
        logRss(debugSource, "︾开始解析内容页")
        Rss.getContent(scope, rssArticle, ruleContent, rssSource)
            .onSuccess {
                logRss(debugSource, it)
                logRss(debugSource, "︽内容页解析完成", state = 1000)
            }
            .onError {
                logRss(debugSource, it.stackTraceStr, state = -1)
            }
    }

    /**
     * 开始调试书源
     * @param scope 协程作用域
     * @param bookSource 书源
     * @param key 关键字
     * @param localDebug 是否本地调试（规则直跑，流水线不发网络请求）
     */
    fun startDebug(scope: CoroutineScope, bookSource: BookSource, key: String, localDebug: Boolean = false) {
        cancelDebug()
        debugSource = bookSource.bookSourceUrl
        localDebugSession = if (localDebug) bookSource.bookSourceUrl else null
        startTime = System.currentTimeMillis()
        when {
            key.isAbsUrl() -> {
                val book = Book()
                book.origin = bookSource.bookSourceUrl
                book.bookUrl = key
                log(debugSource, "⇒开始访问详情页:$key")
                infoDebug(scope, bookSource, book)
            }

            key.contains("::") -> {
                val url = key.substringAfter("::")
                log(debugSource, "⇒开始访问发现页:$url")
                exploreDebug(scope, bookSource, url)
            }

            key.startsWith("++") -> {
                val url = key.substring(2)
                val book = Book()
                book.origin = bookSource.bookSourceUrl
                book.tocUrl = url
                log(debugSource, "⇒开始访目录页:$url")
                tocDebug(scope, bookSource, book)
            }

            key.startsWith("--") -> {
                val url = key.substring(2)
                val book = Book()
                book.origin = bookSource.bookSourceUrl
                log(debugSource, "⇒开始访正文页:$url")
                val chapter = BookChapter()
                chapter.title = "调试"
                chapter.url = url
                contentDebug(scope, bookSource, book, chapter, null)
            }

            else -> {
                log(debugSource, "⇒开始搜索关键字:$key")
                searchDebug(scope, bookSource, key)
            }
        }
    }

    /**
     * 发现页调试
     * @param scope 协程作用域
     * @param bookSource 书源
     * @param url URL
     */
    private fun exploreDebug(scope: CoroutineScope, bookSource: BookSource, url: String) {
        log(debugSource, "︾开始解析发现页")
        val explore = WebBook.exploreBook(scope, bookSource, url, 1)
            .onSuccess { exploreBooks ->
                if (exploreBooks.isNotEmpty()) {
                    log(debugSource, "︽发现页解析完成")
                    log(debugSource, showTime = false)
                    infoDebug(scope, bookSource, exploreBooks[0].toBook())
                } else {
                    log(debugSource, "︽未获取到书籍", state = -1)
                }
            }
            .onError {
                log(debugSource, it.stackTraceStr, state = -1)
            }
        tasks.add(explore)
    }

    /**
     * 搜索调试
     * @param scope 协程作用域
     * @param bookSource 书源
     * @param key 关键字
     */
    private fun searchDebug(scope: CoroutineScope, bookSource: BookSource, key: String) {
        log(debugSource, "︾开始解析搜索页")
        val search = WebBook.searchBook(scope, bookSource, key, 1)
            .onSuccess { searchBooks ->
                if (searchBooks.isNotEmpty()) {
                    log(debugSource, "︽搜索页解析完成")
                    log(debugSource, showTime = false)
                    infoDebug(scope, bookSource, searchBooks[0].toBook())
                } else {
                    log(debugSource, "︽未获取到书籍", state = -1)
                }
            }
            .onError {
                log(debugSource, it.stackTraceStr, state = -1)
            }
        tasks.add(search)
    }

    /**
     * 详情页调试
     * @param scope 协程作用域
     * @param bookSource 书源
     * @param book 书籍
     */
    private fun infoDebug(scope: CoroutineScope, bookSource: BookSource, book: Book) {
        if (book.tocUrl.isNotBlank()) {
            log(debugSource, "≡已获取目录链接,跳过详情页")
            log(debugSource, showTime = false)
            tocDebug(scope, bookSource, book)
            return
        }
        log(debugSource, "︾开始解析详情页")
        val info = WebBook.getBookInfo(scope, bookSource, book)
            .onSuccess {
                log(debugSource, "︽详情页解析完成")
                log(debugSource, showTime = false)
                if (!book.isWebFile) {
                    tocDebug(scope, bookSource, book)
                } else {
                    log(debugSource, "≡文件类书源跳过解析目录", state = 1000)
                }
            }
            .onError {
                log(debugSource, it.stackTraceStr, state = -1)
            }
        tasks.add(info)
    }

    /**
     * 目录页调试
     * @param scope 协程作用域
     * @param bookSource 书源
     * @param book 书籍
     */
    private fun tocDebug(scope: CoroutineScope, bookSource: BookSource, book: Book) {
        log(debugSource, "︾开始解析目录页")
        val chapterList = if (AppConfig.isTocPartialLoad) {
            Coroutine.async(scope) {
                WebBook.getChapterListFlow(bookSource, book)
                    .first { it.chapters.isNotEmpty() }
                    .chapters
            }
        } else {
            WebBook.getChapterList(scope, bookSource, book)
        }
            .onSuccess { chapters ->
                if (AppConfig.isTocPartialLoad) {
                    log(debugSource, "︽目录页解析完成,已开启目录不完全加载，只加载一页目录")
                } else {
                    log(debugSource, "︽目录页解析完成")
                }
                log(debugSource, showTime = false)
                val toc = chapters.filter { !(it.isVolume && it.url.startsWith(it.title)) }
                if (toc.isEmpty()) {
                    log(debugSource, "≡没有正文章节")
                    return@onSuccess
                }
                val nextChapterUrl = toc.getOrNull(1)?.url ?: toc.first().url
                contentDebug(scope, bookSource, book, toc.first(), nextChapterUrl)
            }
            .onError {
                log(debugSource, it.stackTraceStr, state = -1)
            }
        tasks.add(chapterList)
    }

    /**
     * 正文页调试
     * @param scope 协程作用域
     * @param bookSource 书源
     * @param book 书籍
     * @param bookChapter 书籍章节
     * @param nextChapterUrl 下一章节URL
     */
    private fun contentDebug(
        scope: CoroutineScope,
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        nextChapterUrl: String?
    ) {
        log(debugSource, "︾开始解析正文页")
        val content = WebBook.getContent(
            scope = scope,
            bookSource = bookSource,
            book = book,
            bookChapter = bookChapter,
            nextChapterUrl = nextChapterUrl,
            needSave = false
        ).onSuccess {
            log(debugSource, "︽正文页解析完成", state = 1000)
        }.onError {
            log(debugSource, it.stackTraceStr, state = -1)
        }
        tasks.add(content)
    }

    interface Callback {
        fun printLog(state: Int, msg: String)
    }
}
