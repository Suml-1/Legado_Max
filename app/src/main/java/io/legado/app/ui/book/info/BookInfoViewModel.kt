package io.legado.app.ui.book.info

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.NoBooksDirException
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.config.AppConfig
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.BookTagManagement
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.addType
import io.legado.app.help.book.getExportFileName
import io.legado.app.help.book.getRemoteUrl
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.BookshelfMatcher
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.isSameNameAuthor
import io.legado.app.help.book.isWebFile
import io.legado.app.help.book.removeType
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.book.sync
import io.legado.app.help.book.updateTo
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.webdav.ObjectNotFoundException
import io.legado.app.model.AudioPlay
import io.legado.app.model.BookCover
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadManga
import io.legado.app.model.SharedJsScope
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.UrlUtil
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.ExperimentalCoroutinesApi
import io.legado.app.domain.model.BookShelfState

@OptIn(ExperimentalCoroutinesApi::class)
class BookInfoViewModel(application: Application) : BaseViewModel(application) {
    val bookData = MutableLiveData<Book>()
    val chapterListData = MutableLiveData<List<BookChapter>>()
    val authorOtherWorksData = MutableLiveData<AuthorOtherWorksState>(AuthorOtherWorksState.Idle)
    val webFiles = mutableListOf<WebFile>()
    var inBookshelf = false
    var hasCustomBtn = false
    var bookSource: BookSource? = null
    private var changeSourceCoroutine: Coroutine<*>? = null
    private var authorOtherWorksBookKey: String? = null
    private var fromAuthorOtherWorks = false
    val waitDialogData = MutableLiveData<Boolean>()
    val actionLive = MutableLiveData<String>()
    val tocLoading = MutableLiveData<Boolean>()

    fun initData(intent: Intent) {
        execute {
            inBookshelf = false
            hasCustomBtn = false
            bookSource = null
            fromAuthorOtherWorks = intent.getBooleanExtra("fromAuthorOtherWorks", false)
            val name = intent.getStringExtra("name") ?: ""
            val author = intent.getStringExtra("author") ?: ""
            val bookUrl = intent.getStringExtra("bookUrl") ?: ""
            appDb.bookDao.getBook(name, author)?.let { storedBook ->
                inBookshelf = !storedBook.isNotShelf
                // 修复：书不在书架中时，如果从不同书源打开，使用新书源信息
                if (!inBookshelf && bookUrl.isNotBlank() && bookUrl != storedBook.bookUrl) {
                    appDb.searchBookDao.getSearchBook(bookUrl)?.toBook()?.let { searchBook ->
                        upBook(searchBook)
                        return@execute
                    }
                }
                upBook(storedBook)
                return@execute
            }
            if (bookUrl.isNotBlank()) {
                appDb.bookDao.getBook(bookUrl)?.let {
                    inBookshelf = !it.isNotShelf
                    upBook(it)
                    return@execute
                }
                appDb.searchBookDao.getSearchBook(bookUrl)?.toBook()?.let { book ->
                    appDb.bookDao.getBook(book.name, book.author)?.let {
                        inBookshelf = !it.isNotShelf
                    }
                    upBook(book)
                    return@execute
                }
            }
            appDb.searchBookDao.getFirstByNameAuthor(name, author)?.toBook()?.let { book ->
                appDb.bookDao.getBook(book.name, book.author)?.let {
                    inBookshelf = !it.isNotShelf
                }
                upBook(book)
                return@execute
            }

            // 如果前面的查找都失败，但是有bookUrl，创建临时书籍对象
            if (bookUrl.isNotBlank() && name.isNotBlank()) {
                val tempBook = Book(
                    bookUrl = bookUrl,
                    name = name,
                    author = author,
                    origin = intent.getStringExtra("origin") ?: "",
                ).apply {
                    // 从搜索记录中获取更多信息
                    appDb.searchBookDao.getSearchBook(bookUrl)?.let { searchBook ->
                        coverUrl = searchBook.coverUrl
                        intro = searchBook.intro
                        latestChapterTitle = searchBook.latestChapterTitle
                        tocUrl = searchBook.tocUrl
                        originName = searchBook.originName
                        kind = searchBook.kind
                    }
                }
                upBook(tempBook)
                return@execute
            }

            throw NoStackTraceException("未找到书籍")
        }.onError {
            AppLog.put(it.localizedMessage, it)
            context.toastOnUi(it.localizedMessage)
        }
    }

    fun upBook(intent: Intent) {
        execute {
            val name = intent.getStringExtra("name") ?: ""
            val author = intent.getStringExtra("author") ?: ""
            appDb.bookDao.getBook(name, author)?.let { book ->
                inBookshelf = !book.isNotShelf
                upBook(book)
            }
        }
    }

    private fun upBook(book: Book) {
        execute {
            bookSource = if (book.isLocal) {
                null
            } else {
                appDb.bookSourceDao.getBookSource(book.origin)?.also {
                    hasCustomBtn = it.customButton
                    // 后台异步预下载 jsLib URL，避免首次 useweb 渲染时主线程 runBlocking 卡住
                    SharedJsScope.prefetch(it.jsLib)
                }
            }
            AppLog.putReaderDebug(
                "[TOC] upBook入口快照: name=${book.name}, author=${book.author}, " +
                    "bookUrl=${book.bookUrl}, tocUrl='${book.tocUrl}', " +
                    "intro.len=${book.intro?.length ?: -1}, intro.prefix='${book.intro?.take(16) ?: "null"}', " +
                    "coverUrl='${book.coverUrl?.take(40) ?: "null"}', " +
                    "latestChapterTitle='${book.latestChapterTitle}', " +
                    "inBookshelf=$inBookshelf, isLocal=${book.isLocal}",
            )
            bookData.postValue(book)
            upCoverByRule(book)
            if (book.tocUrl.isEmpty() && !book.isLocal) {
                AppLog.putReaderDebug("[TOC] upBook: tocUrl为空, 走loadBookInfo分支")
                loadBookInfo(book, runPreUpdateJs = inBookshelf)
            } else {
                val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl)
                AppLog.putReaderDebug("[TOC] upBook: DB已有${chapterList.size}章, isTocPartialLoad=${AppConfig.isTocPartialLoad}")
                if (chapterList.isNotEmpty()) {
                    AppLog.putReaderDebug("[TOC] upBook: 仅刷新目录、不走loadBookInfo（intro停留在DB快照）")
                    chapterListData.postValue(chapterList)
                } else {
                    AppLog.putReaderDebug("[TOC] upBook: tocUrl非空但DB无目录，仅 loadChapter、不走loadBookInfo")
                    loadChapter(book, isFromBookInfo = true)
                }
            }
        }
    }

    private fun upCoverByRule(book: Book) {
        execute {
            if (book.coverUrl.isNullOrBlank() && book.customCoverUrl.isNullOrBlank()) {
                val coverUrl = BookCover.searchCover(book)
                if (coverUrl.isNullOrBlank()) {
                    return@execute
                }
                book.customCoverUrl = coverUrl
                bookData.postValue(book)
                if (inBookshelf) {
                    saveBook(book)
                }
            }
        }
    }

    fun refreshBook(book: Book) {
        executeLazy(executeContext = IO) {
            if (book.isLocal) {
                book.tocUrl = ""
                book.getRemoteUrl()?.let {
                    val bookWebDav = AppWebDav.defaultBookWebDav
                        ?: throw NoStackTraceException("webDav没有配置")
                    val remoteBook = bookWebDav.getRemoteBook(it)
                    if (remoteBook == null) {
                        book.origin = BookType.localTag
                    } else if (remoteBook.lastModify > book.lastCheckTime) {
                        val uri = bookWebDav.downloadRemoteBook(remoteBook)
                        book.bookUrl = if (uri.isContentScheme()) uri.toString() else uri.path!!
                        book.lastCheckTime = remoteBook.lastModify
                    }
                }
            } else {
                val bs = bookSource ?: return@executeLazy
                if (book.originName != bs.bookSourceName) {
                    book.originName = bs.bookSourceName
                }
            }
        }.onError {
            when (it) {
                is ObjectNotFoundException -> {
                    book.origin = BookType.localTag
                }

                else -> {
                    AppLog.put("下载远程书籍<${book.name}>失败", it)
                }
            }
        }.onFinally {
            loadBookInfo(book, false)
        }.start()
    }

    fun loadBookInfo(
        book: Book,
        canReName: Boolean = true,
        runPreUpdateJs: Boolean = true,
        scope: CoroutineScope = viewModelScope,
    ) {
        if (book.isLocal) {
            LocalBook.upBookInfo(book)
            bookData.postValue(book)
            loadChapter(book)
        } else {
            val bookSource = bookSource ?: let {
                chapterListData.postValue(emptyList())
                context.toastOnUi(R.string.error_no_source)
                return
            }
            AppLog.putReaderDebug("[TOC] loadBookInfo开始: bookUrl=${book.bookUrl}")
            WebBook.getBookInfo(scope, bookSource, book, canReName = canReName)
                .onSuccess(IO) {
                    try {
                        AppLog.putReaderDebug(
                            "[TOC] loadBookInfo成功: bookUrl=${book.bookUrl}, isWebFile=${it.isWebFile}, " +
                                "tocUrl='${it.tocUrl}', intro.len=${it.intro?.length ?: -1}, " +
                                "intro.prefix='${it.intro?.take(16) ?: "null"}', " +
                                "coverUrl='${it.coverUrl?.take(40) ?: "null"}', " +
                                "latestChapterTitle='${it.latestChapterTitle}'",
                        )
                        val dbBook = appDb.bookDao.getBook(book.name, book.author)
                        if (!inBookshelf && dbBook != null && !dbBook.isNotShelf && dbBook.origin == book.origin) {
                            dbBook.updateTo(it)
                            inBookshelf = true
                        }
                        bookData.postValue(it)
                        if (inBookshelf) {
                            it.save()
                        }
                        if (it.isWebFile) {
                            AppLog.putReaderDebug("[TOC] loadBookInfo: isWebFile=true, 走loadWebFile分支")
                            loadWebFile(it)
                        } else {
                            AppLog.putReaderDebug("[TOC] loadBookInfo: 即将调用loadChapter, isLocal=${it.isLocal}, bookSource=${bookSource != null}")
                            try {
                                loadChapter(it, runPreUpdateJs, isFromBookInfo = true)
                                AppLog.putReaderDebug("[TOC] loadChapter调用完成")
                            } catch (e: Throwable) {
                                AppLog.putReaderDebug("[TOC] loadChapter调用异常: ${e.localizedMessage}", e)
                            }
                        }
                    } catch (e: Throwable) {
                        AppLog.putReaderDebug("[TOC] loadBookInfo onSuccess异常: ${e.localizedMessage}", e)
                    }
                }.onError {
                    AppLog.putReaderDebug("[TOC] loadBookInfo失败: bookUrl=${book.bookUrl}, error=${it.localizedMessage}", it)
                    context.toastOnUi(R.string.error_get_book_info)
                }
        }
    }

    fun loadChapter(
        book: Book,
        runPreUpdateJs: Boolean = true,
        scope: CoroutineScope = viewModelScope,
        isFromBookInfo: Boolean = false,
    ) {
        AppLog.putReaderDebug("[TOC] loadChapter入口A")
        AppLog.putReaderDebug("[TOC] loadChapter入口B: bookUrl=" + book.bookUrl)
        AppLog.putReaderDebug("[TOC] loadChapter入口C: isLocal=" + book.isLocal)
        if (book.isLocal) {
            execute(scope) {
                LocalBook.getChapterList(book).let {
                    appDb.bookDao.update(book)
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*it.toTypedArray())
                    ReadBook.onChapterListUpdated(book)
                    chapterListData.postValue(it)
                }
            }.onError {
                context.toastOnUi("LoadTocError:${it.localizedMessage}")
            }
        } else {
            val bookSource = bookSource ?: let {
                chapterListData.postValue(emptyList())
                context.toastOnUi(R.string.error_no_source)
                return
            }
            val oldBook = book.copy()
            if (AppConfig.isTocPartialLoad) {
                tocLoading.postValue(true)
                AppLog.putReaderDebug("[TOC] 部分加载开始, bookUrl=${book.bookUrl}")
                var oldChapters: List<BookChapter> = emptyList()
                var latestChapters: List<BookChapter> = emptyList()
                execute(scope) {
                    oldChapters = appDb.bookChapterDao.getChapterList(oldBook.bookUrl)
                    var firstEmitChapterSize: Int? = null
                    var hasShownNextTocLazyLoadToast = false
                    WebBook.getChapterListFlow(bookSource, book, runPreUpdateJs, isFromBookInfo = isFromBookInfo)
                        .collect { partial ->
                            val chapters = partial.chapters
                            latestChapters = chapters.toList()
                            AppLog.putReaderDebug("[TOC] Flow emit: count=${chapters.size}, isComplete=${partial.isComplete}, bookUrl=${book.bookUrl}")
                            if (chapters.isEmpty()) return@collect
                            val firstSize = firstEmitChapterSize
                            if (firstSize == null) {
                                firstEmitChapterSize = chapters.size
                            } else if (!hasShownNextTocLazyLoadToast && chapters.size > firstSize) {
                                hasShownNextTocLazyLoadToast = true
                                context.toastOnUi("下一页目录懒加载已成功执行")
                            }
                            if (partial.isComplete) {
                                updatePartialBookChapterSummary(book, chapters, oldBook.totalChapterNum)
                                // 目录全部加载完成
                                if (inBookshelf) {
                                    saveShelfBook(oldBook, book, removeUpdateError = true)
                                } else {
                                    book.addType(BookType.notShelf)
                                    book.save()
                                }
                                replaceBookChapters(oldBook, chapters)
                                if (inBookshelf) {
                                    ReadBook.onChapterListUpdated(book)
                                }
                                chapterListData.postValue(chapters)
                            } else {
                                // 中间过程：增量保存到数据库，通知目录视图刷新
                                updatePartialBookChapterSummary(book, chapters, oldBook.totalChapterNum)
                                if (inBookshelf) {
                                    saveShelfBook(oldBook, book)
                                } else {
                                    book.addType(BookType.notShelf)
                                    book.save()
                                }
                                savePartialBookChapters(chapters)
                                chapterListData.postValue(chapters)
                                ReadBook.onChapterListUpdated(book, loadContent = false, isIncremental = true)
                            }
                            postEvent(EventBus.TOC_PARTIAL_LOADED, book.bookUrl)
                        }
                }.onError(IO) {
                    if (oldChapters.isNotEmpty()) {
                        restoreCachedChapterList(oldBook, book, oldChapters, latestChapters)
                        chapterListData.postValue(oldChapters)
                        AppLog.put("${context.getString(R.string.toc_load_failed_using_cache)}\n${it.localizedMessage}", it)
                        context.toastOnUi(R.string.toc_load_failed_using_cache)
                    } else {
                        chapterListData.postValue(emptyList())
                        AppLog.put("${context.getString(R.string.error_get_chapter_list)}\n${it.localizedMessage}", it)
                        context.toastOnUi(R.string.error_get_chapter_list)
                    }
                }.onFinally {
                    tocLoading.postValue(false)
                    postEvent(EventBus.TOC_LOAD_COMPLETE, book.bookUrl)
                }
            } else {
                WebBook.getChapterList(scope, bookSource, book, runPreUpdateJs, isFromBookInfo = isFromBookInfo)
                    .onSuccess(IO) {
                        if (inBookshelf) {
                            saveShelfBook(oldBook, book, removeUpdateError = true)
                            replaceBookChapters(oldBook, it)
                            ReadBook.onChapterListUpdated(book)
                        }
                        chapterListData.postValue(it)
                    }.onError {
                        val oldChapters = appDb.bookChapterDao.getChapterList(oldBook.bookUrl)
                        if (oldChapters.isNotEmpty()) {
                            bookData.postValue(oldBook)
                            chapterListData.postValue(oldChapters)
                            AppLog.put("${context.getString(R.string.toc_load_failed_using_cache)}\n${it.localizedMessage}", it)
                            context.toastOnUi(R.string.toc_load_failed_using_cache)
                        } else {
                            chapterListData.postValue(emptyList())
                            AppLog.put("${context.getString(R.string.error_get_chapter_list)}\n${it.localizedMessage}", it)
                            context.toastOnUi(R.string.error_get_chapter_list)
                        }
                    }
            }
        }
    }

    private fun updatePartialBookChapterSummary(
        book: Book,
        chapters: List<BookChapter>,
        baseTotalChapterNum: Int,
    ) {
        val replaceRules = ContentProcessor.get(book).getTitleReplaceRules()
        val replaceBook = book.toReplaceBook()
        val currentChapter = chapters.getOrNull(book.durChapterIndex) ?: chapters.firstOrNull()
        if (book.durChapterTitle.isNullOrBlank()) {
            book.durChapterTitle = currentChapter?.getDisplayTitle(
                replaceRules,
                book.getUseReplaceRule(),
                replaceBook = replaceBook,
            )
        }
        if (baseTotalChapterNum < chapters.size) {
            book.lastCheckCount = chapters.size - baseTotalChapterNum
            book.latestChapterTime = System.currentTimeMillis()
        }
        book.lastCheckTime = System.currentTimeMillis()
        book.totalChapterNum = chapters.size
        book.latestChapterTitle = chapters.getOrElse(book.simulatedTotalChapterNum() - 1) { chapters.last() }
            .getDisplayTitle(
                replaceRules,
                book.getUseReplaceRule(),
                replaceBook = replaceBook,
            )
    }

    private fun saveShelfBook(
        oldBook: Book,
        book: Book,
        removeUpdateError: Boolean = false,
    ) {
        book.sync(oldBook)
        if (removeUpdateError) {
            book.removeType(BookType.updateError)
        }
        if (oldBook.bookUrl == book.bookUrl) {
            appDb.bookDao.update(book)
        } else {
            appDb.bookDao.replace(oldBook, book)
            BookHelp.updateCacheFolder(oldBook, book)
        }
    }

    private fun replaceBookChapters(oldBook: Book, chapters: List<BookChapter>) {
        appDb.bookChapterDao.delByBook(oldBook.bookUrl)
        appDb.bookChapterDao.insert(*chapters.toTypedArray())
    }

    private fun savePartialBookChapters(chapters: List<BookChapter>) {
        appDb.bookChapterDao.insert(*chapters.toTypedArray())
    }

    /** 目录刷新失败时恢复旧目录、书籍信息和因标题/排序变化而改名的缓存文件。 */
    private fun restoreCachedChapterList(
        oldBook: Book,
        currentBook: Book,
        oldChapters: List<BookChapter>,
        latestChapters: List<BookChapter>,
    ) {
        if (latestChapters.isNotEmpty()) {
            BookHelp.createChapterCacheMigrator(currentBook, latestChapters).migrate(oldChapters)
        }
        if (currentBook.bookUrl != oldBook.bookUrl) {
            appDb.bookChapterDao.delByBook(currentBook.bookUrl)
        }
        appDb.bookChapterDao.delByBook(oldBook.bookUrl)
        appDb.bookChapterDao.insert(*oldChapters.toTypedArray())
        if (appDb.bookDao.has(oldBook.bookUrl)) {
            appDb.bookDao.update(oldBook)
        }
        if (inBookshelf) {
            ReadBook.onChapterListUpdated(oldBook, loadContent = false)
        }
        bookData.postValue(oldBook)
    }

    fun loadGroup(groupId: Long, success: ((groupNames: String?) -> Unit)) {
        execute {
            appDb.bookGroupDao.getGroupNames(groupId).joinToString(",")
        }.onSuccess {
            success.invoke(it)
        }
    }

    private fun loadWebFile(book: Book) {
        execute {
            webFiles.clear()
            val fileNameNoExtension = if (book.author.isBlank()) {
                book.name
            } else {
                "${book.name} 作者：${book.author}"
            }
            book.downloadUrls!!.map {
                val analyzeUrl = AnalyzeUrl(
                    it,
                    source = bookSource,
                    coroutineContext = coroutineContext,
                )
                var mFileName = UrlUtil.getFileName(analyzeUrl)
                    ?: fileNameNoExtension
                analyzeUrl.type?.let { suffix ->
                    mFileName += ".$suffix"
                }
                WebFile(it, mFileName)
            }
        }.onError {
            context.toastOnUi("LoadWebFileError\n${it.localizedMessage}")
        }.onSuccess {
            webFiles.addAll(it)
            book.latestChapterTitle = "已下载"
            bookData.postValue(book)
            chapterListData.postValue(emptyList())
        }
    }

    /* 导入或者下载在线文件 */
    fun <T> importOrDownloadWebFile(webFile: WebFile, success: ((T) -> Unit)?) {
        bookSource ?: return
        execute {
            waitDialogData.postValue(true)
            if (webFile.isSupported) {
                val book = LocalBook.importFileOnLine(
                    webFile.url,
                    bookData.value!!.getExportFileName(webFile.suffix),
                    bookSource,
                )
                changeToLocalBook(book)
            } else {
                LocalBook.saveBookFile(
                    webFile.url,
                    bookData.value!!.getExportFileName(webFile.suffix),
                    bookSource,
                )
            }
        }.onSuccess {
            @Suppress("unchecked_cast")
            success?.invoke(it as T)
        }.onError {
            when (it) {
                is NoBooksDirException -> actionLive.postValue("selectBooksDir")
                else -> {
                    AppLog.put("ImportWebFileError\n${it.localizedMessage}", it)
                    context.toastOnUi("ImportWebFileError\n${it.localizedMessage}")
                    webFiles.remove(webFile)
                }
            }
        }.onFinally {
            waitDialogData.postValue(false)
        }
    }

    fun getArchiveFilesName(archiveFileUri: Uri, onSuccess: (List<String>) -> Unit) {
        execute {
            ArchiveUtils.getArchiveFilesName(archiveFileUri) {
                AppPattern.bookFileRegex.matches(it)
            }
        }.onError {
            AppLog.put("getArchiveEntriesName Error:\n${it.localizedMessage}", it)
            context.toastOnUi("getArchiveEntriesName Error:\n${it.localizedMessage}")
        }.onSuccess {
            onSuccess.invoke(it)
        }
    }

    fun importArchiveBook(
        archiveFileUri: Uri,
        archiveEntryName: String,
        success: ((Book) -> Unit)? = null,
    ) {
        execute {
            val suffix = archiveEntryName.substringAfterLast(".")
            LocalBook.importArchiveFile(
                archiveFileUri,
                bookData.value!!.getExportFileName(suffix),
            ) {
                it.contains(archiveEntryName)
            }.first()
        }.onSuccess {
            val book = changeToLocalBook(it)
            success?.invoke(book)
        }.onError {
            AppLog.put("importArchiveBook Error:\n${it.localizedMessage}", it)
            context.toastOnUi("importArchiveBook Error:\n${it.localizedMessage}")
        }
    }

    fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        changeSourceCoroutine?.cancel()
        changeSourceCoroutine = execute {
            bookSource = source.also {
                hasCustomBtn = it.customButton
            }
            bookData.value?.migrateTo(book, toc)
            if (book.isWebFile) {
                loadWebFile(book)
            }
            if (inBookshelf) {
                book.removeType(BookType.updateError)
                bookData.value?.delete()
                appDb.bookDao.insert(book)
                appDb.bookChapterDao.insert(*toc.toTypedArray())
            }
            bookData.postValue(book)
            chapterListData.postValue(toc)
        }.onFinally {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }
    }

    fun topBook() {
        execute {
            bookData.value?.let { book ->
                val minOrder = appDb.bookDao.minOrder
                book.order = minOrder - 1
                book.durChapterTime = System.currentTimeMillis()
                appDb.bookDao.update(book)
            }
        }
    }

    fun saveBook(book: Book?, success: (() -> Unit)? = null) {
        book ?: return
        execute {
            if (book.order == 0) {
                book.order = appDb.bookDao.minOrder - 1
            }
            appDb.bookDao.getBook(book.name, book.author)?.let {
                book.durChapterIndex = it.durChapterIndex
                book.durChapterPos = it.durChapterPos
                book.durChapterTitle = it.durChapterTitle
            }
            book.save()
            if (ReadBook.book?.isSameNameAuthor(book) == true) {
                ReadBook.book = book
            } else if (AudioPlay.book?.isSameNameAuthor(book) == true) {
                AudioPlay.book = book
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    /**
     * 将书籍标签注册进所属用户分组的标签配置，与管理标签"添加标签"语义一致：
     * 标签在 bookshelfGroupTags 中持久化，即使书籍移除标签后仍保留在分组标签列表中。
     */
    fun registerBookTags(book: Book, tags: List<String>) {
        if (tags.isEmpty()) return
        execute {
            val bookGroups = appDb.bookGroupDao.all
                .map { it.groupId }
                .filter { it > 0 && book.group and it > 0L }
            if (bookGroups.isEmpty()) return@execute
            val configMap = AppConfig.bookshelfGroupTags.toMutableMap()
            var changed = false
            bookGroups.forEach { groupId ->
                val merged = BookTagManagement.mergeTags(configMap[groupId].orEmpty(), tags)
                if (merged != configMap[groupId]) {
                    configMap[groupId] = merged
                    changed = true
                }
            }
            if (changed) {
                AppConfig.bookshelfGroupTags = configMap
            }
        }
    }

    fun saveChapterList(success: (() -> Unit)?) {
        execute {
            chapterListData.value?.let {
                appDb.bookChapterDao.insert(*it.toTypedArray())
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun addToBookshelf(success: (() -> Unit)?, groupId: Long? = null) { // 点击书架按钮或在加分组时触发
        execute {
            inBookshelf = true // 尽早设置，防止loadChapter并发重新添加notShelf标记
            bookData.value?.let { book ->
                book.removeType(BookType.notShelf)
                if (groupId != null) {
                    AppLog.put("addToBookshelf: 设置groupId=$groupId, bookUrl=${book.bookUrl}")
                    book.group = groupId
                }
                if (book.order == 0) {
                    book.order = appDb.bookDao.minOrder - 1
                }
                appDb.bookDao.getBook(book.name, book.author)?.let {
                    book.durChapterIndex = it.durChapterIndex
                    book.durChapterPos = it.durChapterPos
                    book.durChapterTitle = it.durChapterTitle
                }
                if (ReadBook.book?.isSameNameAuthor(book) == true) {
                    ReadBook.book = book
                    ReadBook.inBookshelf = true
                } else if (AudioPlay.book?.isSameNameAuthor(book) == true) {
                    AudioPlay.book = book
                    AudioPlay.inBookshelf = true
                } else if (ReadManga.book?.isSameNameAuthor(book) == true) {
                    ReadManga.inBookshelf = true
                }
                book.save()
                SourceCallBack.callBackBook(SourceCallBack.ADD_BOOK_SHELF, bookSource, book)
            }
            chapterListData.value?.let {
                appDb.bookChapterDao.insert(*it.toTypedArray())
            }
        }.onError {
            inBookshelf = false // 保存失败时回滚状态，避免UI与数据库不一致
            AppLog.put("加入书架失败: ${it.localizedMessage}", it)
        }.onSuccess {
            success?.invoke()
        }
    }

    fun getBook(toastNull: Boolean = true): Book? {
        val book = bookData.value
        if (toastNull && book == null) {
            context.toastOnUi("book is null")
        }
        return book
    }

    fun delBook(
        deleteOriginal: Boolean = false,
        clearSearchBooks: Boolean = true,
        success: (() -> Unit)? = null,
    ) {
        execute {
            bookData.value?.let { book ->
                book.delete()
                inBookshelf = false
                if (clearSearchBooks) {
                    // 清除该书对应书源+作者的作者其他作品缓存
                    appDb.searchBookDao.clearByOriginAuthor(book.origin, book.author)
                }
                if (book.isLocal) {
                    // 本地书由 LocalBook 一并清缓存与封面
                    LocalBook.deleteBook(book, deleteOriginal)
                } else {
                    // 移出书架要连缓存一起清：缓存清单会让 clearInvalidCache 跳过该目录，
                    // 不清的话这份缓存会一直占着空间
                    BookHelp.clearCache(book)
                }
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun clearCache(book: Book) {
        execute {
            BookHelp.clearCache(book)
            if (ReadBook.book?.bookUrl == book.bookUrl) {
                ReadBook.clearTextChapter()
            }
            if (ReadManga.book?.bookUrl == book.bookUrl) {
                ReadManga.clearMangaChapter()
            }
        }.onSuccess {
            context.toastOnUi(R.string.clear_cache_success)
        }.onError {
            context.toastOnUi("清理缓存出错\n${it.localizedMessage}")
        }
    }

    fun upEditBook() {
        bookData.value?.let {
            appDb.bookDao.getBook(it.bookUrl)?.let { book ->
                bookData.postValue(book)
            }
        }
    }

    private fun changeToLocalBook(localBook: Book): Book = LocalBook.mergeBook(localBook, bookData.value).let {
        bookData.postValue(it)
        loadChapter(it)
        inBookshelf = true
        it
    }

    fun onButtonClick(activity: AppCompatActivity, name: String, click: String) {
        val source = bookSource ?: return
        val book = bookData.value ?: return
        execute {
            val java = SourceLoginJsExtensions(activity, source)
            runScriptWithContext {
                source.evalJS(click) {
                    put("result", null)
                    put("java", java)
                    put("book", book)
                }
            }
        }.onError {
            AppLog.put("${source.bookSourceName}: ${it.localizedMessage}", it)
            context.toastOnUi("$name click error\n${it.localizedMessage}")
        }
    }

    data class WebFile(
        val url: String,
        val name: String,
    ) {

        override fun toString(): String = name

        // 后缀
        val suffix: String = UrlUtil.getSuffix(name)

        // txt epub umd pdf等文件
        val isSupported: Boolean = AppPattern.bookFileRegex.matches(name)

        // 压缩包形式的txt epub umd pdf文件
        val isSupportDecompress: Boolean = AppPattern.archiveFileRegex.matches(name)
    }

    // 作者其他作品相关方法
    fun prepareAuthorOtherWorks(book: Book) {
        val key = "${book.name}\n${book.author}\n${book.origin}"
        if (authorOtherWorksBookKey != key) {
            authorOtherWorksBookKey = key
            authorOtherWorksData.value = AuthorOtherWorksState.Idle
            loadCachedAuthorOtherWorks(book)
        }
    }

    private fun loadCachedAuthorOtherWorks(book: Book) {
        val source = bookSource ?: return
        val author = normalizeAuthor(book.author)
        if (author.isBlank()) return
        if (!inBookshelf && !fromAuthorOtherWorks) return
        execute {
            val cachedBooks = appDb.searchBookDao.getByOriginAuthor(source.bookSourceUrl, author)
            if (cachedBooks.isEmpty()) {
                return@execute emptyList<SearchBook>()
            }
            filterAuthorOtherWorks(book, cachedBooks)
        }.onSuccess { items ->
            if (items.isNotEmpty()) {
                authorOtherWorksData.postValue(AuthorOtherWorksState.Success(items))
            }
        }.onError {
            AppLog.put("加载缓存的作者其他作品失败\n${it.localizedMessage}", it)
        }
    }

    fun searchAuthorOtherWorks() {
        val book = bookData.value ?: return
        val source = bookSource ?: let {
            authorOtherWorksData.value = AuthorOtherWorksState.Error(context.getString(R.string.error_no_source))
            return
        }
        val author = normalizeAuthor(book.author)
        if (author.isBlank()) {
            authorOtherWorksData.value = AuthorOtherWorksState.Empty
            return
        }
        authorOtherWorksData.value = AuthorOtherWorksState.Loading
        execute {
            val items = WebBook.searchBookAwait(
                bookSource = source,
                key = author,
                page = 1,
                filter = { _, itemAuthor, _ ->
                    normalizeAuthor(itemAuthor) == author
                },
            ).onEach {
                it.releaseHtmlData()
            }.let { filterAuthorOtherWorks(book, it) }
            if (items.isNotEmpty()) {
                appDb.searchBookDao.insert(*items.toTypedArray())
            }
            items
        }.onSuccess {
            authorOtherWorksData.postValue(
                if (it.isEmpty()) AuthorOtherWorksState.Empty else AuthorOtherWorksState.Success(it),
            )
        }.onError {
            AppLog.put("搜索作者其他作品失败\n${it.localizedMessage}", it)
            authorOtherWorksData.postValue(
                AuthorOtherWorksState.Error(it.localizedMessage ?: it.javaClass.simpleName),
            )
        }
    }

    private fun filterAuthorOtherWorks(book: Book, books: List<SearchBook>): List<SearchBook> {
        val author = normalizeAuthor(book.author)
        val currentName = book.name.trim()
        return books.filter {
            normalizeAuthor(it.author) == author && it.name.trim() != currentName
        }.distinctBy {
            "${it.name.trim()}-${it.author.trim()}"
        }
    }

    private fun normalizeAuthor(author: String): String = author.trim()

    fun getBookShelfState(book: SearchBook): BookShelfState = BookshelfMatcher.getState(book.name, book.author, book.bookUrl)

    /**
     * 将作者其他作品中的书籍加入书架
     */
    fun addAuthorOtherWorkToBookshelf(book: SearchBook) {
        execute {
            val bookEntity = book.toBook()
            appDb.bookDao.insert(bookEntity)
            // BookshelfMatcher 会通过 flowShelfKeys() 自动感知 DB 变化并刷新
        }.onError {
            AppLog.put("加入书架失败", it)
        }
    }

    sealed class AuthorOtherWorksState {
        object Idle : AuthorOtherWorksState()
        object Loading : AuthorOtherWorksState()
        object Empty : AuthorOtherWorksState()
        data class Success(val books: List<SearchBook>) : AuthorOtherWorksState()
        data class Error(val message: String) : AuthorOtherWorksState()
    }
}
