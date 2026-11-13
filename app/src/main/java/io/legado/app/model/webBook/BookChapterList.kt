package io.legado.app.model.webBook

import android.text.TextUtils
import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.data.repository.debug.FlowLogRecorder
import io.legado.app.exception.NoStackTraceException
import io.legado.app.exception.TocEmptyException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ChapterCacheMigrator
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.config.AppConfig
import io.legado.app.model.Debug
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setToastRuleType
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.debug.DataFlowStage
import io.legado.app.model.debug.FieldFillRecord
import io.legado.app.model.debug.recordField
import io.legado.app.utils.isTrue
import io.legado.app.utils.mapAsync
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.htmlunit.corejs.javascript.Context
import splitties.init.appCtx
import io.legado.app.constant.AppPattern
import kotlinx.coroutines.currentCoroutineContext

/**
 * 目录渐进加载的中间结果
 *
 * @property chapters 已加载的章节列表（可能不是完整的目录）
 * @property isComplete 目录是否已全部加载完成
 */
data class PartialChapterList(
    val chapters: List<BookChapter>,
    val isComplete: Boolean
)

/**
 * 获取目录
 * 章节目录解析器
 *
 * 负责解析书籍的章节目录列表，支持：
 * - 单页/多页目录解析
 * - 目录反转（倒序排列）
 * - 多页目录串行/并发获取
 * - 章节标题格式化JS
 * - VIP/付费章节标记
 * - 分卷标题识别
 * - 章节信息（字数、更新时间）提取
 *
 * @see WebBook.getChapterList 网络请求入口
 * @see TocRule 目录规则定义
 */
object BookChapterList {

    /**
     * 解析章节目录
     *
     * @param bookSource 书源
     * @param book 书籍对象（需包含tocUrl）
     * @param baseUrl 基础URL
     * @param redirectUrl 重定向后的URL
     * @param body 页面内容
     * @param isFromBookInfo 是否从详情页跳转
     * @return 章节列表
     * @throws NoStackTraceException 当内容为空时抛出
     * @throws TocEmptyException 当目录为空时抛出
     */
    suspend fun analyzeChapterList(
        bookSource: BookSource,
        book: Book,
        baseUrl: String,
        redirectUrl: String,
        body: String?,
        isFromBookInfo: Boolean = false
    ): List<BookChapter> {
        body ?: throw NoStackTraceException(
            appCtx.getString(R.string.error_get_web_content, baseUrl)
        )
        val chapterList = ArrayList<BookChapter>()
        Debug.log(bookSource.bookSourceUrl, "≡获取成功:${baseUrl}")
        Debug.log(bookSource.bookSourceUrl, body, state = 30)
        val tocRule = bookSource.getTocRule()
        val nextUrlList = arrayListOf(redirectUrl)
        var reverse = false
        var listRule = tocRule.chapterList ?: ""
        if (listRule.startsWith("-")) {
            reverse = true
            listRule = listRule.substring(1)
        }
        if (listRule.startsWith("+")) {
            listRule = listRule.substring(1)
        }
        var chapterData =
            analyzeChapterList(
                book, baseUrl, redirectUrl, body,
                tocRule, listRule, bookSource, log = true,
                isFromBookInfo = isFromBookInfo
            )
        chapterList.addAll(chapterData.first)
        when (chapterData.second.size) {
            0 -> Unit
            1 -> {
                var nextUrl = chapterData.second[0]
                while (nextUrl.isNotEmpty() && !nextUrlList.contains(nextUrl)) {
                    delayBeforeNextTocPage(bookSource)
                    nextUrlList.add(nextUrl)
                    val analyzeUrl = AnalyzeUrl(
                        mUrl = nextUrl,
                        source = bookSource,
                        ruleData = book,
                        coroutineContext = currentCoroutineContext()
                    )
                    val res = analyzeUrl.getStrResponseAwait() //控制并发访问
                    res.body?.let { nextBody ->
                        chapterData = analyzeChapterList(
                            book, nextUrl, nextUrl,
                            nextBody, tocRule, listRule, bookSource,
                            isFromBookInfo = isFromBookInfo
                        )
                        nextUrl = chapterData.second.firstOrNull() ?: ""
                        chapterList.addAll(chapterData.first)
                    }
                }
                Debug.log(bookSource.bookSourceUrl, "◇目录总页数:${nextUrlList.size}")
            }

            else -> {
                Debug.log(
                    bookSource.bookSourceUrl,
                    "◇并发解析目录,总页数:${chapterData.second.size}"
                )
                flow {
                    for (urlStr in chapterData.second) {
                        delayBeforeNextTocPage(bookSource)
                        emit(urlStr)
                    }
                }.mapAsync(if (AppConfig.isTocPartialLoad) 1 else AppConfig.threadCount) { urlStr ->
                    val analyzeUrl = AnalyzeUrl(
                        mUrl = urlStr,
                        source = bookSource,
                        ruleData = book,
                        coroutineContext = currentCoroutineContext()
                    )
                    val res = analyzeUrl.getStrResponseAwait() //控制并发访问
                    analyzeChapterList(
                        book, urlStr, res.url,
                        res.body!!, tocRule, listRule, bookSource, false,
                        isFromBookInfo = isFromBookInfo
                    ).first
                }.collect {
                    chapterList.addAll(it)
                }
            }
        }
        if (chapterList.isEmpty()) {
            throw TocEmptyException(appCtx.getString(R.string.chapter_list_empty))
        }
        if (!reverse) {
            chapterList.reverse()
        }
        currentCoroutineContext().ensureActive()
        //去重
        val lh = LinkedHashSet(chapterList)
        val list = ArrayList(lh)
        if (!book.getReverseToc()) {
            list.reverse()
        }
        Debug.log(book.origin, "◇目录总数:${list.size}")
        currentCoroutineContext().ensureActive()
        list.forEachIndexed { index, bookChapter ->
            bookChapter.index = index
        }
        val formatJs = tocRule.formatJs
        if (!formatJs.isNullOrBlank()) {
            Context.enter().use {
                val bindings = ScriptBindings()
                bindings["gInt"] = 0
                list.forEachIndexed { index, bookChapter ->
                    bindings["index"] = index + 1
                    bindings["chapter"] = bookChapter
                    bindings["title"] = bookChapter.title
                    RhinoScriptEngine.runCatching {
                        eval(formatJs, bindings)?.toString()?.let {
                            bookChapter.title = it
                            //标题已变化, 重置标题MD5缓存, 避免getFileName使用旧标题
                            bookChapter.titleMD5 = null
                        }
                    }.onFailure {
                        Debug.log(book.origin, "格式化标题出错, ${it.localizedMessage}")
                    }
                }
            }
        }
        val replaceRules = ContentProcessor.get(book).getTitleReplaceRules()
        val replaceBook = book.toReplaceBook()
        book.durChapterTitle = list.getOrElse(book.durChapterIndex) { list.last() }
            .getDisplayTitle(
                replaceRules,
                book.getUseReplaceRule(),
                replaceBook = replaceBook
            )
        if (book.totalChapterNum < list.size) {
            book.lastCheckCount = list.size - book.totalChapterNum
            book.latestChapterTime = System.currentTimeMillis()
        }
        book.lastCheckTime = System.currentTimeMillis()
        book.totalChapterNum = list.size
        book.latestChapterTitle =
            list.getOrElse(book.simulatedTotalChapterNum() - 1) { list.last() }
                .getDisplayTitle(
                    replaceRules,
                    book.getUseReplaceRule(),
                    replaceBook = replaceBook
                )
        currentCoroutineContext().ensureActive()
        upChapterInfo(list, book)
        //此时尚未用新目录替换数据库中的旧章节, 按章节url迁移因标题/序号变化而改名的缓存文件
        BookHelp.createChapterCacheMigrator(
            book,
            appDb.bookChapterDao.getChapterList(book.bookUrl)
        ).migrate(list)
        
        val dataFlowFields = mutableListOf<FieldFillRecord>()
        dataFlowFields.recordField("totalChapterNum", result = list.size.toString())
        dataFlowFields.recordField("latestChapterTitle", result = book.latestChapterTitle)
        dataFlowFields.recordField("durChapterTitle", result = book.durChapterTitle)

        FlowLogRecorder.logStageDataFlow(
            source = bookSource,
            stage = DataFlowStage.TOC,
            fields = dataFlowFields,
            message = "目录阶段数据流转",
            bookUrl = book.bookUrl,
            bookName = book.name,
            author = book.author,
            book = book,
            bookSource = bookSource
        )

        return list
    }

    /**
     * 渐进式解析章节目录
     *
     * 与 [analyzeChapterList] 不同，此方法通过 Flow 逐页发射目录结果，
     * 允许调用方在目录未完全加载时就获取已解析的章节，实现"边加载边展示"。
     *
     * 加载策略：
     * - 单页目录：先发射中间结果，再发射最终结果
     * - 串行多页目录（1个nextUrl）：每加载完一页就发射一次，最后一页发射 isComplete=true
     * - 并发多页目录（多个nextUrl）：全部加载完后只发射一次 isComplete=true
     *
     * @param bookSource 书源
     * @param book 书籍对象（需包含tocUrl）
     * @param baseUrl 基础URL
     * @param redirectUrl 重定向后的URL
     * @param body 页面内容
     * @param isFromBookInfo 是否从详情页跳转
     * @return Flow<PartialChapterList> 逐页发射的目录加载结果
     */
    fun analyzeChapterListFlow(
        bookSource: BookSource,
        book: Book,
        baseUrl: String,
        redirectUrl: String,
        body: String?,
        isFromBookInfo: Boolean = false
    ): Flow<PartialChapterList> = flow {
        body ?: throw NoStackTraceException(
            appCtx.getString(R.string.error_get_web_content, baseUrl)
        )
        AppLog.putReaderDebug(
            "[TOC] analyzeChapterListFlow进入 flow{}: baseUrl='$baseUrl', " +
                    "redirectUrl='$redirectUrl', body.len=${body.length}"
        )
        val chapterList = ArrayList<BookChapter>()
        Debug.log(bookSource.bookSourceUrl, "≡获取成功:${baseUrl}")
        Debug.log(bookSource.bookSourceUrl, body, state = 30)
        val tocRule = bookSource.getTocRule()
        val nextUrlList = arrayListOf(redirectUrl)
        var reverse = false
        var listRule = tocRule.chapterList ?: ""
        if (listRule.startsWith("-")) {
            reverse = true
            listRule = listRule.substring(1)
        }
        if (listRule.startsWith("+")) {
            listRule = listRule.substring(1)
        }
        val tParse = System.currentTimeMillis()
        AppLog.putReaderDebug("[TOC] analyzeChapterListFlow 首页解析开始")
        var chapterData = analyzeChapterList(
            book, baseUrl, redirectUrl, body,
            tocRule, listRule, bookSource, log = true,
            isFromBookInfo = isFromBookInfo
        )
        AppLog.putReaderDebug(
            "[TOC] analyzeChapterListFlow 首页解析完成: 耗时${System.currentTimeMillis() - tParse}ms, " +
                    "first.size=${chapterData.first.size}, second.size=${chapterData.second.size}"
        )
        chapterList.addAll(chapterData.first)
        //渐进加载中旧章节会被中间结果逐批替换, 先取旧章节创建迁移器, 每次发射时接力迁移缓存文件
        val cacheMigrator = BookHelp.createChapterCacheMigrator(
            book,
            appDb.bookChapterDao.getChapterList(book.bookUrl)
        )
        // 第一页解析完成后先排序编号；只有多页目录才发射中间结果
        val sortedFirstPage = sortAndIndex(chapterList, reverse, book, cacheMigrator)

        when (chapterData.second.size) {
            0 -> {
                // 单页目录，直接发射最终结果
                AppLog.putReaderDebug("[TOC] analyzeChapterListFlow 分支[单页]: 准备 emit complete")
                val finalList = finalizeChapterList(sortedFirstPage, tocRule, book, bookSource, cacheMigrator)
                emit(PartialChapterList(finalList, isComplete = true))
                AppLog.putReaderDebug("[TOC] analyzeChapterListFlow 分支[单页]: emit 完成 count=${finalList.size}")
            }
            1 -> {
                // 串行多页目录，每加载完一页就发射一次
                AppLog.putReaderDebug("[TOC] analyzeChapterListFlow 分支[串行多页]: 首次 emit partial count=${sortedFirstPage.size}")
                emit(PartialChapterList(sortedFirstPage, isComplete = false))
                var nextUrl = chapterData.second[0]
                var emittedComplete = false
                while (nextUrl.isNotEmpty() && !nextUrlList.contains(nextUrl)) {
                    delayBeforeNextTocPage(bookSource)
                    nextUrlList.add(nextUrl)
                    val analyzeUrl = AnalyzeUrl(
                        mUrl = nextUrl,
                        source = bookSource,
                        ruleData = book,
                        coroutineContext = currentCoroutineContext()
                    )
                    val res = analyzeUrl.getStrResponseAwait()
                    res.body?.let { nextBody ->
                        chapterData = analyzeChapterList(
                            book, nextUrl, nextUrl,
                            nextBody, tocRule, listRule, bookSource,
                            isFromBookInfo = isFromBookInfo
                        )
                        nextUrl = chapterData.second.firstOrNull() ?: ""
                        chapterList.addAll(chapterData.first)
                        val sorted = sortAndIndex(chapterList, reverse, book, cacheMigrator)
                        val isLast = nextUrl.isEmpty() || nextUrlList.contains(nextUrl)
                        if (isLast) {
                            // 最后一页，执行格式化JS等最终处理并发射完成结果
                            val finalList = finalizeChapterList(sorted, tocRule, book, bookSource, cacheMigrator)
                            emit(PartialChapterList(finalList, isComplete = true))
                            emittedComplete = true
                        } else {
                            // 中间页，发射中间结果供调用方增量展示
                            emit(PartialChapterList(sorted, isComplete = false))
                        }
                    } ?: run {
                        throw NoStackTraceException(
                            appCtx.getString(R.string.error_get_web_content, nextUrl)
                        )
                    }
                }
                Debug.log(bookSource.bookSourceUrl, "◇目录总页数:${nextUrlList.size}")
                if (!emittedComplete) {
                    val sorted = sortAndIndex(chapterList, reverse, book, cacheMigrator)
                    val finalList = finalizeChapterList(sorted, tocRule, book, bookSource, cacheMigrator)
                    emit(PartialChapterList(finalList, isComplete = true))
                }
            }
            else -> {
                AppLog.putReaderDebug("[TOC] analyzeChapterListFlow 分支[并发多页]: 首次 emit partial count=${sortedFirstPage.size}, 总页数=${chapterData.second.size}")
                emit(PartialChapterList(sortedFirstPage, isComplete = false))
                // 并发多页目录，无法逐页发射，全部加载完后发射最终结果
                Debug.log(
                    bookSource.bookSourceUrl,
                    "◇并发解析目录,总页数:${chapterData.second.size}"
                )
                flow {
                    for (urlStr in chapterData.second) {
                        delayBeforeNextTocPage(bookSource)
                        emit(urlStr)
                    }
                }.mapAsync(if (AppConfig.isTocPartialLoad) 1 else AppConfig.threadCount) { urlStr ->
                    val analyzeUrl = AnalyzeUrl(
                        mUrl = urlStr,
                        source = bookSource,
                        ruleData = book,
                        coroutineContext = currentCoroutineContext()
                    )
                    val res = analyzeUrl.getStrResponseAwait()
                    analyzeChapterList(
                        book, urlStr, res.url,
                        res.body!!, tocRule, listRule, bookSource, false,
                        isFromBookInfo = isFromBookInfo
                    ).first
                }.collect {
                    chapterList.addAll(it)
                    val sorted = sortAndIndex(chapterList, reverse, book, cacheMigrator)
                    emit(PartialChapterList(sorted, isComplete = false))
                }
                val sorted = sortAndIndex(chapterList, reverse, book, cacheMigrator)
                val finalList = finalizeChapterList(sorted, tocRule, book, bookSource, cacheMigrator)
                emit(PartialChapterList(finalList, isComplete = true))
            }
        }
    }

    /**
     * 对已加载的章节进行去重、排序和编号
     *
     * 在渐进加载的中间过程中调用，确保每次发射的章节列表顺序正确且 index 已设置。
     * 注意：此方法不做格式化JS和book信息更新，这些仅在最终完成时处理。
     *
     * @param chapterList 已加载的所有章节（可能来自多页）
     * @param reverse 是否反转（由 listRule 的 "-" 前缀决定）
     * @param book 书籍对象
     * @return 排序编号后的章节列表
     */
    private suspend fun delayBeforeNextTocPage(bookSource: BookSource) {
        if (!AppConfig.isTocPartialLoad) {
            return
        }
        val interval = AppConfig.tocPartialLoadInterval.coerceIn(0, 60) * 1000L +
                parseConcurrentRateInterval(bookSource.concurrentRate)
        if (interval > 0) {
            delay(interval)
        }
    }

    private fun parseConcurrentRateInterval(concurrentRate: String?): Long {
        if (concurrentRate.isNullOrBlank() || concurrentRate == "0") {
            return 0L
        }
        val rateIndex = concurrentRate.indexOf("/")
        return if (rateIndex > 0) {
            concurrentRate.substring(rateIndex + 1).toLongOrNull() ?: 0L
        } else {
            concurrentRate.toLongOrNull() ?: 0L
        }.coerceAtLeast(0L)
    }

    private fun sortAndIndex(
        chapterList: ArrayList<BookChapter>,
        reverse: Boolean,
        book: Book,
        cacheMigrator: ChapterCacheMigrator
    ): ArrayList<BookChapter> {
        if (chapterList.isEmpty()) return chapterList
        val list = ArrayList(chapterList)
        if (!reverse) {
            list.reverse()
        }
        val lh = LinkedHashSet(list)
        val result = ArrayList(lh)
        if (!book.getReverseToc()) {
            result.reverse()
        }
        result.forEachIndexed { index, bookChapter ->
            bookChapter.index = index
        }
        //中间结果的序号已确定, 先迁移一次, 保证渐进加载期间读取的章节能命中缓存
        cacheMigrator.migrate(result)
        return result
    }

    /**
     * 完成目录加载的最终处理
     *
     * 在目录全部加载完成后调用，执行：
     * - 格式化JS处理章节标题
     * - 更新书籍的当前阅读标题、最新章节标题
     * - 更新章节检查计数和时间
     * - 合并历史章节信息（字数、变量、图片URL）
     *
     * @param list 已排序编号的完整章节列表
     * @param tocRule 目录规则
     * @param book 书籍对象
     * @param bookSource 书源对象
     * @return 处理后的章节列表
     */
    private fun finalizeChapterList(
        list: ArrayList<BookChapter>,
        tocRule: TocRule,
        book: Book,
        bookSource: BookSource? = null,
        cacheMigrator: ChapterCacheMigrator
    ): ArrayList<BookChapter> {
        if (list.isEmpty()) return list
        val formatJs = tocRule.formatJs
        if (!formatJs.isNullOrBlank()) {
            Context.enter().use {
                val bindings = ScriptBindings()
                bindings["gInt"] = 0
                list.forEachIndexed { index, bookChapter ->
                    bindings["index"] = index + 1
                    bindings["chapter"] = bookChapter
                    bindings["title"] = bookChapter.title
                    RhinoScriptEngine.runCatching {
                        eval(formatJs, bindings)?.toString()?.let {
                            bookChapter.title = it
                            //标题已变化, 重置标题MD5缓存, 避免getFileName使用旧标题
                            bookChapter.titleMD5 = null
                        }
                    }.onFailure {
                        Debug.log(book.origin, "格式化标题出错, ${it.localizedMessage}")
                    }
                }
            }
        }
        val replaceRules = ContentProcessor.get(book).getTitleReplaceRules()
        val replaceBook = book.toReplaceBook()
        book.durChapterTitle = list.getOrElse(book.durChapterIndex) { list.last() }
            .getDisplayTitle(
                replaceRules,
                book.getUseReplaceRule(),
                replaceBook = replaceBook
            )
        if (book.totalChapterNum < list.size) {
            book.lastCheckCount = list.size - book.totalChapterNum
            book.latestChapterTime = System.currentTimeMillis()
        }
        book.lastCheckTime = System.currentTimeMillis()
        book.totalChapterNum = list.size
        book.latestChapterTitle =
            list.getOrElse(book.simulatedTotalChapterNum() - 1) { list.last() }
                .getDisplayTitle(
                    replaceRules,
                    book.getUseReplaceRule(),
                    replaceBook = replaceBook
                )
        upChapterInfo(list, book)
        //目录最终确定, 将缓存文件迁移到最终文件名
        cacheMigrator.migrate(list)

        val dataFlowFields = mutableListOf<FieldFillRecord>()
        dataFlowFields.recordField("totalChapterNum", result = list.size.toString())
        dataFlowFields.recordField("latestChapterTitle", result = book.latestChapterTitle)
        dataFlowFields.recordField("durChapterTitle", result = book.durChapterTitle)

        FlowLogRecorder.logStageDataFlow(
            source = bookSource,
            stage = DataFlowStage.TOC,
            fields = dataFlowFields,
            message = "目录阶段数据流转",
            bookUrl = book.bookUrl,
            bookName = book.name,
            author = book.author,
            book = book,
            bookSource = bookSource
        )

        return list
    }

    /**
     * 解析单页目录内容
     *
     * @param book 书籍对象
     * @param baseUrl 基础URL
     * @param redirectUrl 重定向后的URL
     * @param body 页面内容
     * @param tocRule 目录规则
     * @param listRule 列表规则
     * @param bookSource 书源
     * @param getNextUrl 是否获取下一页URL
     * @param log 是否输出调试日志
     * @param isFromBookInfo 是否从详情页跳转
     * @return 章节列表和下一页URL列表的Pair
     */
    private suspend fun analyzeChapterList(
        book: Book,
        baseUrl: String,
        redirectUrl: String,
        body: String,
        tocRule: TocRule,
        listRule: String,
        bookSource: BookSource,
        getNextUrl: Boolean = true,
        log: Boolean = false,
        isFromBookInfo:Boolean
    ): Pair<List<BookChapter>, List<String>> {
        val analyzeRule = AnalyzeRule(book, bookSource, false, isFromBookInfo)
        analyzeRule.setContent(body).setBaseUrl(baseUrl)
        analyzeRule.setRedirectUrl(redirectUrl)
        analyzeRule.setCoroutineContext(currentCoroutineContext())
        analyzeRule.setToastRuleType("TOC")
        //获取目录列表
        val chapterList = arrayListOf<BookChapter>()
        Debug.log(bookSource.bookSourceUrl, "┌获取目录列表", log)
        val elements = analyzeRule.getElements(listRule)
        Debug.log(bookSource.bookSourceUrl, "└列表大小:${elements.size}", log)
        //获取下一页链接
        val nextUrlList = arrayListOf<String>()
        val nextTocRule = tocRule.nextTocUrl
        if (getNextUrl && !nextTocRule.isNullOrEmpty()) {
            Debug.log(bookSource.bookSourceUrl, "┌获取目录下一页列表", log)
            analyzeRule.getStringList(nextTocRule, isUrl = true)?.let {
                for (item in it) {
                    if (item != redirectUrl) {
                        nextUrlList.add(item)
                    }
                }
            }
            Debug.log(
                bookSource.bookSourceUrl,
                "└" + TextUtils.join("，\n", nextUrlList),
                log
            )
        }
        currentCoroutineContext().ensureActive()
        if (elements.isNotEmpty()) {
            Debug.log(bookSource.bookSourceUrl, "┌解析目录列表", log)
            
            FlowLogRecorder.logExtract(
                source = bookSource,
                message = "开始提取章节列表字段",
                book = book,
                bookSource = bookSource
            )
            
            val nameRule = analyzeRule.splitSourceRule(tocRule.chapterName)
            val urlRule = analyzeRule.splitSourceRule(tocRule.chapterUrl)
            val vipRule = analyzeRule.splitSourceRule(tocRule.isVip)
            val payRule = analyzeRule.splitSourceRule(tocRule.isPay)
            val upTimeRule = analyzeRule.splitSourceRule(tocRule.updateTime)
            val isVolumeRule = analyzeRule.splitSourceRule(tocRule.isVolume)
            val tocCountWords = AppConfig.tocCountWords
            elements.forEachIndexed { index, item ->
                currentCoroutineContext().ensureActive()
                analyzeRule.setContent(item)
                val bookChapter = BookChapter(bookUrl = book.bookUrl, baseUrl = redirectUrl)
                analyzeRule.setChapter(bookChapter)
                bookChapter.title = analyzeRule.getString(nameRule)
                bookChapter.url = analyzeRule.getString(urlRule)
                
                if (index == 0) {
                    FlowLogRecorder.logExtract(
                        source = bookSource,
                        message = "提取章节标题",
                        rule = tocRule.chapterName,
                        result = bookChapter.title,
                        book = book,
                        bookSource = bookSource
                    )

                    FlowLogRecorder.logExtract(
                        source = bookSource,
                        message = "提取章节URL",
                        rule = tocRule.chapterUrl,
                        result = bookChapter.url,
                        book = book,
                        bookSource = bookSource
                    )
                }
                
                val info = analyzeRule.getString(upTimeRule)
                val isVolume = analyzeRule.getString(isVolumeRule)
                bookChapter.isVolume = false
                if (isVolume.isTrue()) {
                    bookChapter.isVolume = true
                    bookChapter.tag = info
                } else {
                    if (tocCountWords) {
                        AppPattern.wordCountRegex.find(info)?.let { match ->
                            bookChapter.apply {
                                wordCount = match.groupValues[1].trim()
                                tag = info.replaceFirst(match.value, "")
                            }
                        } ?: run { bookChapter.tag = info }
                    } else {
                        bookChapter.tag = info
                    }
                }
                if (bookChapter.url.isEmpty()) {
                    if (bookChapter.isVolume) {
                        bookChapter.url = bookChapter.title + index
                        Debug.log(
                            bookSource.bookSourceUrl,
                            "⇒一级目录${index}未获取到url,使用标题替代"
                        )
                    } else {
                        bookChapter.url = baseUrl
                        Debug.log(
                            bookSource.bookSourceUrl,
                            "⇒目录${index}未获取到url,使用baseUrl替代"
                        )
                    }
                }
                if (bookChapter.title.isNotEmpty()) {
                    val isVip = analyzeRule.getString(vipRule)
                    val isPay = analyzeRule.getString(payRule)
                    if (isVip.isTrue()) {
                        bookChapter.isVip = true
                    }
                    if (isPay.isTrue()) {
                        bookChapter.isPay = true
                    }
                    chapterList.add(bookChapter)
                }
            }
            Debug.log(bookSource.bookSourceUrl, "└目录列表解析完成", log)
            if (chapterList.isEmpty()) {
                Debug.log(bookSource.bookSourceUrl, "◇章节列表为空", log)
            } else {
                Debug.log(bookSource.bookSourceUrl, "≡首章信息", log)
                Debug.log(bookSource.bookSourceUrl, "◇章节名称:${chapterList[0].title}", log)
                Debug.log(bookSource.bookSourceUrl, "◇章节链接:${chapterList[0].url}", log)
                chapterList[0].wordCount?.run{
                    Debug.log(bookSource.bookSourceUrl, "◇章节信息:${chapterList[0].tag} $this", log)
                    Debug.log(bookSource.bookSourceUrl, "⇒已识别到章节信息中的字数",log)
                } ?: run {
                    Debug.log(bookSource.bookSourceUrl, "◇章节信息:${chapterList[0].tag}", log)
                }
                Debug.log(bookSource.bookSourceUrl, "◇是否VIP:${chapterList[0].isVip}", log)
                Debug.log(bookSource.bookSourceUrl, "◇是否购买:${chapterList[0].isPay}", log)
            }
        }
        return Pair(chapterList, nextUrlList)
    }

    /**
     * 更新章节信息
     *
     * 从数据库中读取已有的章节信息（字数、变量、图片URL），
     * 合并到新解析的章节列表中，保留历史数据。
     *
     * @param list 新解析的章节列表
     * @param book 书籍对象
     */
    private fun upChapterInfo(list: ArrayList<BookChapter>, book: Book) {
        if (!AppConfig.tocCountWords) {
            return
        }
        val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl)
        if (chapterList.isNotEmpty()) {
            val map = HashMap<String, Triple<String?, String?, String?>>(chapterList.size)
            for (chapter in chapterList) {
                map["${chapter.index}_${chapter.title}"] = Triple(chapter.wordCount, chapter.variable, chapter.imgUrl)
            }
            for (chapter in list) {
                map["${chapter.index}_${chapter.title}"]?.let { (w, v, i) ->
                    chapter.run {
                        w?.let { wordCount = it }
                        v?.let { variable = it }
                        i?.let { imgUrl = it }
                    }
                }
            }
        }
    }

}
