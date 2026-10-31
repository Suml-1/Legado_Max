package io.legado.app.model

import io.legado.app.constant.AppLog
import io.legado.app.constant.ReadConstants
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.constant.AppConst
import io.legado.app.data.entities.BookSource
import io.legado.app.help.AppWebDav
import io.legado.app.help.ConcurrentRateLimiter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isSameNameAuthor
import io.legado.app.help.book.readSimulating
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.book.update
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.globalExecutor
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.book.manga.entities.BaseMangaPage
import io.legado.app.ui.book.manga.entities.MangaChapter
import io.legado.app.ui.book.manga.entities.MangaContent
import io.legado.app.ui.book.manga.entities.MangaPage
import io.legado.app.ui.book.manga.entities.ReaderLoading
import io.legado.app.utils.mapIndexed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlin.math.min
//漫画阅读模型
@Suppress("MemberVisibilityCanBePrivate")
object ReadManga : CoroutineScope by MainScope() {
    var inBookshelf = false
    var book: Book? = null
    val executor = globalExecutor
    var durChapterIndex = 0 //章节位置
    var chapterSize = 0//总章节
    var durChapterPos = 0
    var chapterChanged = false
    var prevMangaChapter: MangaChapter? = null
    var curMangaChapter: MangaChapter? = null
    var nextMangaChapter: MangaChapter? = null
    var bookSource: BookSource? = null
    var readStartTime: Long = System.currentTimeMillis()
    private val loadingChapters = arrayListOf<Int>()
    var simulatedChapterSize = 0
    var mCallback: Callback? = null
    var preDownloadTask: Job? = null

    // —— 卷轴模式「向后预下载」（按页预下载图片）——
    // 漫画页真正的显示路径是 MangaVH.loadImageWithRetry，它只认 BookHelp 的图片文件缓存
    // （BookHelp.getImage(book, url) 是否存在）。Glide 的磁盘缓存不在那条路径上，
    // 所以页级预下载必须直接写进 BookHelp 的图片目录，否则翻页时仍旧重新下载。
    private val pagePreDownloadChannel = Channel<String>(Channel.UNLIMITED)
    // 当前预下载窗口（每次翻页整体替换）
    private val pagePreDownloadWanted = LinkedHashSet<String>()
    // 已入队但还没下完的地址，避免同一张图重复入队
    private val pagePreDownloadScheduled = hashSetOf<String>()
    private val pagePreDownloadFailed = hashSetOf<String>()
    private var pagePreDownloadJob: Job? = null

    val downloadedChapters = hashSetOf<Int>()
    val downloadFailChapters = hashMapOf<Int, Int>()
    val downloadScope = CoroutineScope(SupervisorJob() + IO)
    val preDownloadSemaphore = Semaphore(ReadConstants.PRE_DOWNLOAD_CONCURRENCY)
    var rateLimiter = ConcurrentRateLimiter(null)
    val mangaContents get() = buildMangaContent()
    val hasNextChapter get() = durChapterIndex < simulatedChapterSize - 1
    //重置阅读数据
    fun resetData(book: Book) {
        // 换书前落库上一本书未入库的阅读会话
        ReadSessionRecorder.flush()
        ReadManga.book = book
        readStartTime = System.currentTimeMillis()
        chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        simulatedChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            chapterSize
        }
        durChapterIndex = book.durChapterIndex
        durChapterPos = book.durChapterPos
        clearMangaChapter()
        upWebBook(book)
        synchronized(this) {
            loadingChapters.clear()
            downloadedChapters.clear()
            downloadFailChapters.clear()
        }
    }
    //更新阅读数据
    fun upData(book: Book) {
        ReadManga.book = book
        chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        simulatedChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            chapterSize
        }

        if (durChapterIndex != book.durChapterIndex) {
            durChapterIndex = book.durChapterIndex
            durChapterPos = book.durChapterPos
            clearMangaChapter()
        }
        upWebBook(book)
        synchronized(this) {
            loadingChapters.clear()
            downloadedChapters.clear()
            downloadFailChapters.clear()
        }
    }
    //更新网络书籍
    fun upWebBook(book: Book) {
        appDb.bookSourceDao.getBookSource(book.origin)?.let {
            bookSource = it
            rateLimiter = ConcurrentRateLimiter(it)
        } ?: let {
            bookSource = null
        }
    }
    //清除当前章节内容
    fun clearMangaChapter() {
        prevMangaChapter = null
        curMangaChapter = null
        nextMangaChapter = null
    }

    /**
     * 翻页心跳：只延长内存中的打开会话，不逐页写库。
     * 会话在暂停/退出/换书/跨度过长时由 [ReadSessionRecorder] 统一落库。
     */
    fun upReadTime() {
        val book = book ?: return
        if (!AppConfig.enableReadRecord) {
            return
        }
        val now = System.currentTimeMillis()
        readStartTime = now
        ReadSessionRecorder.onReadTick(
            deviceId = AppConst.androidId,
            bookName = book.name,
            bookAuthor = book.author,
            chapterTitle = book.durChapterTitle.orEmpty(),
            now = now
        )
    }

    /** 阅读暂停/退出：落库当前会话 */
    fun flushReadTime() {
        if (!AppConfig.enableReadRecord) {
            return
        }
        ReadSessionRecorder.flush()
    }

    fun markReadStart() {
        val book = book ?: return
        if (!AppConfig.enableReadRecord) {
            return
        }
        val now = System.currentTimeMillis()
        readStartTime = now
        ReadSessionRecorder.onReadStart(
            deviceId = AppConst.androidId,
            bookName = book.name,
            bookAuthor = book.author,
            chapterTitle = book.durChapterTitle.orEmpty(),
            now = now
        )
    }

    @Synchronized
    private fun addLoading(index: Int): Boolean {
        if (loadingChapters.contains(index)) return false
        loadingChapters.add(index)
        return true
    }

    @Synchronized
    fun removeLoading(index: Int) {
        loadingChapters.remove(index)
    }
    //加载当前章节前后2章
    fun loadContent() {
        clearMangaChapter()
        loadContent(durChapterIndex)
        loadNearChapterContent()
    }
    //加载或更新章节内容
    fun loadOrUpContent() {
        if (curMangaChapter == null) {
            loadContent(durChapterIndex)
        } else {
            mCallback?.upContent()
        }
        loadNearChapterContent()
    }

    private fun loadNearChapterContent() {
        if (nextMangaChapter == null) {
            loadContent(durChapterIndex + 1)
        }
        if (prevMangaChapter == null) {
            loadContent(durChapterIndex - 1)
        }
    }
    //加载章节内容
    private fun loadContent(index: Int) {
        Coroutine.async {
            val book = book!!
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, index) ?: return@async
            if (addLoading(index)) {
                BookHelp.getContent(book, chapter)?.let {
                    contentLoadFinish(chapter, it)
                } ?: run {
                    download(downloadScope, chapter)
                }
            }
        }.onError {
            AppLog.put("加载正文出错\n${it.localizedMessage}")
        }
    }

    /**
     * 内容加载完成
     */
    suspend fun contentLoadFinish(
        chapter: BookChapter,
        content: String?,
        errorMsg: String = "加载内容失败",
        canceled: Boolean = false
    ) {
        removeLoading(chapter.index)
        if (canceled || chapter.index !in durChapterIndex - 1..durChapterIndex + 1) {
            return
        }
        when (val offset = chapter.index - durChapterIndex) {
            0 -> {
                if (content == null) {
                    mCallback?.loadFail(errorMsg)
                    return
                }
                if (content.isEmpty() && !chapter.isVolume) {
                    mCallback?.loadFail("正文内容为空")
                    return
                }
                val mangaChapter = getManageChapter(chapter, content)
                if (mangaChapter.imageCount == 0 && !chapter.isVolume) {
                    mCallback?.loadFail("正文没有图片")
                    return
                }
                curMangaChapter = mangaChapter
                mCallback?.upContent()
            }

            -1, 1 -> {
                if (content == null || (!chapter.isVolume && content.isEmpty())) {
                    return
                }
                val mangaChapter = getManageChapter(chapter, content)
                if (mangaChapter.imageCount == 0 && !chapter.isVolume) {
                    return
                }

                when (offset) {
                    -1 -> prevMangaChapter = mangaChapter
                    1 -> nextMangaChapter = mangaChapter
                }

                mCallback?.upContent()
            }
        }
    }
    //构建漫画内容
    private fun buildMangaContent(): MangaContent {
        val items = arrayListOf<BaseMangaPage>()
        var pos = 0
        var curFinish = false
        var nextFinish = false
        prevMangaChapter?.let {
            pos += it.pages.size
            items.addAll(it.pages)
        }
        curMangaChapter?.let {
            curFinish = true
            items.addAll(it.pages)
            durChapterPos = if (it.imageCount > 0) {
                durChapterPos.coerceIn(0, it.imageCount - 1)
            } else {
                0
            }
            pos += durChapterPos
            if (!AppConfig.hideMangaTitle && it.imageCount > 0) {
                pos++
            }
        }
        nextMangaChapter?.let {
            nextFinish = true
            items.addAll(it.pages)
        }
        return MangaContent(pos, items, curFinish, nextFinish)
    }

    /**
     * 加载下一章
     */
    fun moveToNextChapter(toFirst: Boolean = false): Boolean {
        if (durChapterIndex < simulatedChapterSize - 1) {
            if (toFirst) {
                mCallback?.showLoading()
                durChapterPos = 0
            }
            durChapterIndex++
            prevMangaChapter = curMangaChapter
            curMangaChapter = nextMangaChapter
            nextMangaChapter = null
            if (curMangaChapter == null) {
                mCallback?.startLoad()
                loadContent(durChapterIndex)
            } else {
                mCallback?.upContent()
            }
            loadContent(durChapterIndex + 1)
            saveRead()
            AppLog.putReaderDebug("moveToNextChapter-curPageChanged()")
            curPageChanged()
            return true
        } else {
            AppLog.putReaderDebug("跳转下一章失败,没有下一章")
            return false
        }
    }
    
    fun moveToPrevChapter(toFirst: Boolean = false): Boolean {
        if (durChapterIndex > 0) {
            if (toFirst) {
                mCallback?.showLoading()
                durChapterPos = 0
            }
            durChapterIndex--
            nextMangaChapter = curMangaChapter
            curMangaChapter = prevMangaChapter
            prevMangaChapter = null
            if (curMangaChapter == null) {
                loadContent(durChapterIndex)
            } else {
                mCallback?.upContent()
            }
            loadContent(durChapterIndex - 1)
            saveRead()
            return true
        }
        return false
    }
    
    fun curPageChanged() {
        upReadTime()
        preDownload()
        preDownloadNextPages()
    }

    /**
     * 卷轴模式「向后预下载」：从当前页之后取 [AppConfig.mangaPreDownloadNum] 页图片预先落盘。
     *
     * 为什么不复用 Glide 的 RecyclerViewPreloader：它只把字节写进 Glide 自己的磁盘缓存
     * （key 是图片 URL），而漫画页显示走的是 [BookHelp.getImage] 的图片文件缓存
     * （key 是 URL 的 MD5 文件名）。两条路径的 key 完全不同，预下载再多，
     * 翻页时 [io.legado.app.ui.book.manga.recyclerview.MangaVH] 依然判定为未缓存并重新下载。
     * 所以这里直接用显示路径的写入函数落盘，预下载过的页翻到时才真正命中。
     */
    fun preDownloadNextPages() {
        val book = book ?: return
        val count = AppConfig.mangaPreDownloadNum
        if (count <= 0 || book.isLocal) return
        val wanted = LinkedHashSet<String>()
        // 先取当前章剩余页，再越过章尾取下一章，凑满 count 页（卷轴模式下会自然跨章）
        val curPages = curMangaChapter?.pages
        if (curPages != null) {
            for (page in curPages) {
                if (wanted.size >= count) break
                if (page is MangaPage && page.index > durChapterPos && page.mImageUrl.isNotBlank()) {
                    wanted.add(page.mImageUrl)
                }
            }
        }
        if (wanted.size < count) {
            val nextPages = nextMangaChapter?.pages
            if (nextPages != null) {
                for (page in nextPages) {
                    if (wanted.size >= count) break
                    if (page is MangaPage && page.mImageUrl.isNotBlank()) {
                        wanted.add(page.mImageUrl)
                    }
                }
            }
        }
        if (wanted.isEmpty()) return
        synchronized(pagePreDownloadWanted) {
            // 每次翻页整体替换窗口：滚动越快只追最新的一段，不积压历史请求。
            // 注意不清 scheduled：已经排队的地址仍然有效，再入队会造成重复下载。
            pagePreDownloadWanted.clear()
            pagePreDownloadWanted.addAll(wanted)
            wanted.forEach { url ->
                if (url !in pagePreDownloadScheduled && url !in pagePreDownloadFailed) {
                    pagePreDownloadScheduled.add(url)
                    pagePreDownloadChannel.trySend(url)
                }
            }
        }
        startPagePreDownload(book)
    }

    private fun startPagePreDownload(book: Book) {
        if (pagePreDownloadJob?.isActive == true) return
        pagePreDownloadJob = downloadScope.launch {
            while (true) {
                val url = pagePreDownloadChannel.receive()
                val obsolete = synchronized(pagePreDownloadWanted) {
                    url !in pagePreDownloadWanted
                }
                if (obsolete) {
                    // 窗口已经换成更新的位置，这条排队中的地址不再需要
                    synchronized(pagePreDownloadWanted) {
                        pagePreDownloadScheduled.remove(url)
                    }
                    continue
                }
                var success = false
                try {
                    if (!BookHelp.isImageExist(book, url)) {
                        // 与漫画页显示共用同一把锁和同一个写入函数，避免同一张图被并发写坏
                        BookHelp.saveImage(bookSource, book, url)
                    }
                    success = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    AppLog.putDebug("漫画预下载失败 $url\n${e.localizedMessage}")
                } finally {
                    synchronized(pagePreDownloadWanted) {
                        pagePreDownloadScheduled.remove(url)
                        if (!success) {
                            // 明确失败的地址记下来，翻页时不再反复重试
                            pagePreDownloadFailed.add(url)
                        }
                    }
                }
            }
        }
    }

    //取消页级预下载（离开阅读页时调用，避免退出后继续耗流量）
    fun cancelPagePreDownload() {
        pagePreDownloadJob?.cancel()
        pagePreDownloadJob = null
        synchronized(pagePreDownloadWanted) {
            pagePreDownloadWanted.clear()
            pagePreDownloadScheduled.clear()
            pagePreDownloadFailed.clear()
        }
        // 丢弃 channel 中尚未取走的残留任务，避免下次进入阅读页时先跑旧书的地址
        while (true) {
            pagePreDownloadChannel.tryReceive().getOrNull() ?: break
        }
    }
    //保存阅读进度
    fun saveRead(pageChanged: Boolean = false) {
        executor.execute {
            kotlin.runCatching {
                val book = book ?: return@execute
                book.lastCheckCount = 0
                book.durChapterTime = System.currentTimeMillis()
                val chapterChanged = book.durChapterIndex != durChapterIndex
                book.durChapterIndex = durChapterIndex
                book.durChapterPos = durChapterPos
                if (!pageChanged || chapterChanged) {
                    appDb.bookChapterDao.getChapter(book.bookUrl, durChapterIndex)?.let {
                        book.durChapterTitle = it.getDisplayTitle(
                            ContentProcessor.get(book.name, book.origin).getTitleReplaceRules(),
                            book.getUseReplaceRule(),
                            replaceBook = book.toReplaceBook()
                        )
                    }
                }
                appDb.bookDao.update(book)
            }.onFailure {
                AppLog.put("保存漫画阅读进度信息出错\n$it", it)
            }
        }
    }
    //下载网络内容
    private fun downloadNetworkContent(
        bookSource: BookSource,
        scope: CoroutineScope,
        chapter: BookChapter,
        book: Book,
        semaphore: Semaphore?,
        success: suspend (String) -> Unit = {},
        error: suspend () -> Unit = {},
        cancel: suspend () -> Unit = {},
    ) {
        WebBook.getContent(
            scope,
            bookSource,
            book,
            chapter,
            start = CoroutineStart.LAZY,
            executeContext = IO,
            semaphore = semaphore
        ).onSuccess { content ->
            success.invoke(content)
        }.onError {
            error.invoke()
        }.onCancel {
            cancel.invoke()
        }.start()
    }
    //预下载当前章节前后2章，避免用户切换章节时，需要等待下载完成
    private fun preDownload() {
        if (book?.isLocal == true) return
        executor.execute {
            if (AppConfig.preDownloadNum < 2) {
                upToc()
                return@execute
            }
            preDownloadTask?.cancel()
            preDownloadTask = launch(IO) {
                //预下载当前章节前后2章，避免用户切换章节时，需要等待下载完成
                launch {
                    val maxChapterIndex =
                        min(durChapterIndex + AppConfig.preDownloadNum, chapterSize)
                    for (i in durChapterIndex.plus(2)..maxChapterIndex) {
                        if (downloadedChapters.contains(i)) continue
                        if ((downloadFailChapters[i] ?: 0) >= ReadConstants.MAX_DOWNLOAD_FAIL_COUNT) continue
                        downloadIndex(i)
                    }
                }
                launch {
                    val minChapterIndex = durChapterIndex - min(AppConfig.backwardPreDownloadNum, AppConfig.preDownloadNum)
                    for (i in durChapterIndex.minus(2) downTo minChapterIndex) {
                        if (downloadedChapters.contains(i)) continue
                        if ((downloadFailChapters[i] ?: 0) >= ReadConstants.MAX_DOWNLOAD_FAIL_COUNT) continue
                        downloadIndex(i)
                    }
                }
            }
        }
    }

    fun cancelPreDownloadTask() {
        if (curMangaChapter != null && nextMangaChapter != null) {
            preDownloadTask?.cancel()
            downloadScope.coroutineContext.cancelChildren()
        }
        cancelPagePreDownload()
    }

    private suspend fun downloadIndex(index: Int) {
        if (index < 0) return
        if (index > chapterSize - 1) {
            upToc()
            return
        }
        val book = book ?: return
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, index) ?: return
        if (BookHelp.hasContent(book, chapter)) {
            downloadedChapters.add(chapter.index)
        } else {
            delay(ReadConstants.PRE_DOWNLOAD_DELAY_MS)
            if (addLoading(index)) {
                download(downloadScope, chapter, preDownloadSemaphore)
            }
        }
    }

    /**
     * 获取正文
     */
    private suspend fun download(
        scope: CoroutineScope,
        chapter: BookChapter,
        semaphore: Semaphore? = null,
    ) {
        val book = book ?: return removeLoading(chapter.index)
        val bookSource = bookSource
        if (bookSource != null) {
            downloadNetworkContent(bookSource, scope, chapter, book, semaphore, success = {
                downloadedChapters.add(chapter.index)
                downloadFailChapters.remove(chapter.index)
                contentLoadFinish(chapter, it)
            }, error = {
                downloadFailChapters[chapter.index] =
                    (downloadFailChapters[chapter.index] ?: 0) + 1
                contentLoadFinish(chapter, null)
            }, cancel = {
                contentLoadFinish(chapter, null, canceled = true)
            })
        } else {
            contentLoadFinish(chapter, null, "加载内容失败 没有书源")
        }
    }

    @Synchronized
    fun upToc() {
        val bookSource = bookSource ?: return
        val book = book ?: return
        if (!book.canUpdate) return
        if (chapterSize - durChapterIndex - 1 >= ReadConstants.TOC_UPDATE_REMAINING_THRESHOLD) return
        if (System.currentTimeMillis() - book.lastCheckTime < ReadConstants.TOC_UPDATE_MIN_INTERVAL_MS) return
        book.lastCheckTime = System.currentTimeMillis()
        val oldBook = book.copy()
        WebBook.getChapterList(this, bookSource, book).onSuccess(IO) { cList ->
            ensureActive()
            if (cList.size > chapterSize) {
                if (oldBook.bookUrl == book.bookUrl) {
                    appDb.bookDao.update(book)
                } else {
                    appDb.bookDao.replace(oldBook, book)
                    BookHelp.updateCacheFolder(oldBook, book)
                }
                appDb.bookChapterDao.delByBook(oldBook.bookUrl)
                appDb.bookChapterDao.insert(*cList.toTypedArray())
                onChapterListUpdated(book, false)
                nextMangaChapter ?: loadContent(durChapterIndex + 1)
            }
        }
    }

    fun uploadProgress(successAction: (() -> Unit)? = null) {
        book?.let {
            launch(IO) {
                AppWebDav.uploadBookProgress(it) {
                    successAction?.invoke()
                }
                ensureActive()
                it.update()
            }
        }
    }

    /**
     * 同步阅读进度
     * 如果当前进度快于服务器进度或者没有进度进行上传，如果慢与服务器进度则执行传入动作
     */
    fun syncProgress(
        newProgressAction: ((progress: BookProgress) -> Unit)? = null,
        uploadSuccessAction: (() -> Unit)? = null,
        syncSuccessAction: (() -> Unit)? = null,
    ) {
        if (!AppConfig.syncBookProgress) return
        val book = book ?: return
        Coroutine.async {
            AppWebDav.getBookProgress(book)
        }.onError {
            AppLog.put("拉取阅读进度失败", it)
        }.onSuccess { progress ->
            if (progress == null || progress.durChapterIndex < book.durChapterIndex ||
                (progress.durChapterIndex == book.durChapterIndex
                        && progress.durChapterPos < book.durChapterPos)
            ) {
                // 服务器没有进度或者进度比服务器快，上传现有进度
                Coroutine.async {
                    AppWebDav.uploadBookProgress(BookProgress(book), uploadSuccessAction)
                    book.update()
                }
            } else if (progress.durChapterIndex > book.durChapterIndex ||
                progress.durChapterPos > book.durChapterPos
            ) {
                // 进度比服务器慢，执行传入动作
                newProgressAction?.invoke(progress)
            } else {
                syncSuccessAction?.invoke()
            }
        }
    }

    fun setProgress(progress: BookProgress) {
        if (progress.durChapterIndex < chapterSize &&
            (durChapterIndex != progress.durChapterIndex
                    || durChapterPos != progress.durChapterPos)
        ) {
            mCallback?.showLoading()
            if (progress.durChapterIndex == durChapterIndex) {
                durChapterPos = progress.durChapterPos
                mCallback?.upContent()
            } else {
                durChapterIndex = progress.durChapterIndex
                durChapterPos = progress.durChapterPos
                loadContent()
            }
            saveRead()
        }
    }

    fun showLoading() {
        mCallback?.showLoading()
    }

    fun loadFail(msg: String, retry: Boolean = true) {
        mCallback?.loadFail(msg, retry)
    }

    fun onChapterListUpdated(newBook: Book, loadContent: Boolean = true) {
        if (newBook.isSameNameAuthor(book)) {
            book = newBook
            chapterSize = newBook.totalChapterNum
            simulatedChapterSize = newBook.simulatedTotalChapterNum()
            if (simulatedChapterSize > 0 && durChapterIndex > simulatedChapterSize - 1) {
                durChapterIndex = simulatedChapterSize - 1
            }
            if (mCallback == null) {
                clearMangaChapter()
            } else if (loadContent) {
                loadContent()
            }
        }
    }

    /**
     * 注册回调
     */
    fun register(cb: Callback) {
        mCallback = cb
    }

    /**
     * 取消注册回调
     */
    fun unregister(cb: Callback) {
        if (mCallback === cb) {
            mCallback = null
        }
        preDownloadTask?.cancel()
        preDownloadTask = null
        downloadScope.coroutineContext.cancelChildren()
        coroutineContext.cancelChildren()
    }

    private suspend fun getManageChapter(chapter: BookChapter, content: String): MangaChapter {
        val list = BookHelp.flowImages(chapter, content)
            .distinctUntilChanged().mapIndexed { index, src ->
                MangaPage(
                    chapterIndex = chapter.index,
                    chapterSize = chapterSize,
                    mImageUrl = src,
                    index = index,
                    mChapterName = chapter.title
                )
            }.toList()

        val imageCount = list.size

        list.forEach {
            it.imageCount = imageCount
        }

        if (AppConfig.hideMangaTitle && imageCount > 0) {
            return MangaChapter(chapter, list, imageCount)
        }

        val pages = mutableListOf<BaseMangaPage>()

        if (imageCount == 0 && chapter.isVolume) {
            pages.add(ReaderLoading(chapter.index, -1, chapter.title, true))
        } else {
            pages.add(ReaderLoading(chapter.index, -1, "阅读 ${chapter.title}"))
            pages.addAll(list)
            pages.add(ReaderLoading(chapter.index, imageCount, "已读完 ${chapter.title}"))
        }

        return MangaChapter(chapter, pages, imageCount)
    }

    interface Callback {
        fun upContent()
        fun loadFail(msg: String, retry: Boolean = true)
        fun sureNewProgress(progress: BookProgress)
        fun showLoading()
        fun startLoad()
    }
}
