package io.legado.app.ui.main

import android.app.Application
import android.os.Build
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.help.AppWebDav
import io.legado.app.help.DefaultData
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.addType
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isUpError
import io.legado.app.help.book.removeType
import io.legado.app.help.book.sync
import io.legado.app.help.config.AppConfig
import io.legado.app.model.CacheBook
import io.legado.app.model.ReadBook
import io.legado.app.model.debug.SourceSubCategory
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.CacheBookService
import io.legado.app.utils.onEachParallel
import io.legado.app.utils.postEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.collections.forEach
import kotlin.math.min
import io.legado.app.model.RuleUpdate
import io.legado.app.model.SourceCallBack

class MainViewModel(application: Application) : BaseViewModel(application) {
    private var threadCount = AppConfig.threadCount
    private var poolSize = min(threadCount, AppConst.MAX_THREAD)
    private var upTocPool = Executors.newFixedThreadPool(poolSize).asCoroutineDispatcher()
    private val waitUpTocBooks = LinkedList<String>()
    private val onUpTocBooks = ConcurrentHashMap.newKeySet<String>()
    private val eventListenerSource = ConcurrentHashMap<BookSource, Boolean>()
    private val sourceSemaphores = ConcurrentHashMap<String, Semaphore>()
    val onUpBooksLiveData = MutableLiveData<Int>()
    private var upTocJob: Job? = null
    private var cacheBookJob: Job? = null
    var callback: CallBack? = null
    fun setActivityCallback(callback: CallBack) {
        this.callback = callback
    }

    private fun logUpdate(message: String, throwable: Throwable? = null, verbose: Boolean = false) {
        if (!verbose || AppConfig.verboseUpdateLog) {
            if (throwable != null) {
                AppLog.putSource(message, throwable, SourceSubCategory.UPDATE)
            } else {
                AppLog.putSource(message, subCategory = SourceSubCategory.UPDATE)
            }
        }
    }

    init {
        deleteNotShelfBook()
    }

    override fun onCleared() {
        super.onCleared()
        upTocPool.close()
    }

    fun upPool() {
        threadCount = AppConfig.threadCount
        if (upTocJob?.isActive == true || cacheBookJob?.isActive == true) {
            return
        }
        val newPoolSize = min(threadCount, AppConst.MAX_THREAD)
        if (poolSize == newPoolSize) {
            return
        }
        poolSize = newPoolSize
        upTocPool.close()
        upTocPool = Executors.newFixedThreadPool(poolSize).asCoroutineDispatcher()
    }

    fun isUpdate(bookUrl: String): Boolean {
        return onUpTocBooks.contains(bookUrl)
    }

    fun upAllBookToc() {
        execute {
            addToWaitUp(appDb.bookDao.hasUpdateBooks, AppConfig.onlyUpdateRead)
        }
    }

    fun ruleSubsUp() {
        execute {
            val ruleSubs = appDb.ruleSubDao.all
            for (ruleSub in ruleSubs) {
                if (ruleSub.autoUpdate) {
                    val checkResult = RuleUpdate.cacheSource(ruleSub)
                    if(checkResult) {
                        callback?.openImportUi(ruleSub.type, ruleSub.url)
                    }
                }
            }
        }
    }

    fun upToc(books: List<Book>, onlyUpdateRead: Boolean) {
        execute(context = upTocPool) {
            books.filter {
                !it.isLocal && it.canUpdate
            }.let {
                addToWaitUp(it, onlyUpdateRead)
            }
        }
    }

    @Synchronized
    private fun addToWaitUp(books: List<Book>, onlyUpdateRead: Boolean) {
        val addedBooks = mutableListOf<Book>()
        books.forEach { book ->
            if (onlyUpdateRead && book.getUnreadChapterNum() > 0) return@forEach
            if (!waitUpTocBooks.contains(book.bookUrl) && !onUpTocBooks.contains(book.bookUrl)) {
                waitUpTocBooks.add(book.bookUrl)
                addedBooks.add(book)
            }
        }
        if (addedBooks.isNotEmpty()) {
            logUpdate("📚 更新队列：新增 ${addedBooks.size} 本书到更新队列，当前队列总数：${waitUpTocBooks.size}")
        }
        if (upTocJob == null) {
            logUpdate("🚀 更新启动：开始处理更新队列，并发数：$threadCount")
            startUpTocJob()
        }
    }

    private fun startUpTocJob() {
        upPool()
        postUpBooksLiveData()
        upTocJob = viewModelScope.launch(upTocPool) {
            flow {
                while (true) {
                    emit(waitUpTocBooks.poll() ?: break)
                }
            }.onEachParallel(threadCount) {
                onUpTocBooks.add(it)
                postEvent(EventBus.UP_BOOKSHELF, it)
                updateToc(it)
            }.onEach {
                onUpTocBooks.remove(it)
                postEvent(EventBus.UP_BOOKSHELF, it)
                postUpBooksLiveData()
            }.onCompletion {
                upTocJob = null
                if (waitUpTocBooks.isNotEmpty()) {
                    logUpdate("🔄 更新继续：队列中还有 ${waitUpTocBooks.size} 本书待更新", verbose = true)
                    startUpTocJob()
                } else {
                    logUpdate("✨ 更新完成：所有书籍更新任务已完成")
                }
                if (it == null && cacheBookJob == null && !CacheBookService.isRun) {
                    logUpdate("📖 开始缓存：准备缓存章节内容")
                    cacheBook()
                }
            }.catch {
                logUpdate("❌ 更新出错：${it.localizedMessage}", it)
            }.collect()
        }
    }

    private suspend fun updateToc(bookUrl: String) {
        val book = appDb.bookDao.getBook(bookUrl) ?: return
        val source = appDb.bookSourceDao.getBookSource(book.origin)
        if (source == null) {
            if (!book.isUpError) {
                book.addType(BookType.updateError)
                appDb.bookDao.update(book)
            }
            logUpdate("⚠️ 书籍《${book.name}》未找到对应书源")
            return
        }
        
        val semaphore = sourceSemaphores.getOrPut(source.bookSourceUrl) { Semaphore(1) }
        
        if (semaphore.availablePermits == 0) {
            logUpdate("⏸️ 任务等待（书源${source.bookSourceName}，书籍《${book.name}》）：等待书源信号量...", verbose = true)
        }
        
        semaphore.acquire()
        
        try {
            logUpdate("✅ 任务开始（书源${source.bookSourceName}，书籍《${book.name}》）：获取信号量成功，开始更新", verbose = true)
            
            if (source.eventListener) {
                if (eventListenerSource.putIfAbsent(source, true) == null) {
                    SourceCallBack.callBackSource(viewModelScope, SourceCallBack.START_SHELF_REFRESH, source)
                }
            }
            kotlin.runCatching {
                val oldBook = book.copy()
                if (book.tocUrl.isBlank()) {
                    WebBook.getBookInfoAwait(source, book)
                } else {
                    WebBook.runPreUpdateJs(source, book)
                }
                val toc = WebBook.getChapterListAwait(source, book).getOrThrow()
                book.sync(oldBook)
                book.removeType(BookType.updateError)
                if (book.bookUrl == bookUrl) {
                    appDb.bookDao.update(book)
                } else {
                    appDb.bookDao.replace(oldBook, book)
                    BookHelp.updateCacheFolder(oldBook, book)
                }
                appDb.bookChapterDao.delByBook(bookUrl)
                appDb.bookChapterDao.insert(*toc.toTypedArray())
                ReadBook.onChapterListUpdated(book)
                addDownload(source, book)
                logUpdate("✅ 任务完成（书源${source.bookSourceName}，书籍《${book.name}》）：更新成功")
            }.onFailure {
                currentCoroutineContext().ensureActive()
                logUpdate("❌ 任务失败（书源${source.bookSourceName}，书籍《${book.name}》）：${it.localizedMessage}", it)
                appDb.bookDao.getBook(book.bookUrl)?.let { book ->
                    book.addType(BookType.updateError)
                    appDb.bookDao.update(book)
                }
            }
        } finally {
            semaphore.release()
            logUpdate("🔓 信号量释放（书源${source.bookSourceName}，书籍《${book.name}》）：允许下一个任务执行", verbose = true)
        }
    }

    fun postUpBooksLiveData(reset: Boolean = false) {
        if (AppConfig.showWaitUpCount) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                onUpBooksLiveData.postValue(waitUpTocBooks.size + onUpTocBooks.size)
            } else {
                var count = 0
                onUpTocBooks.forEach { _ -> count++ }
                onUpBooksLiveData.postValue(waitUpTocBooks.size + count)
            }
        } else if (reset) {
            onUpBooksLiveData.postValue(0)
        }
    }

    @Synchronized
    private fun addDownload(source: BookSource, book: Book) {
        if (AppConfig.preDownloadNum == 0) return
        val endIndex = min(
            book.totalChapterNum - 1,
            book.durChapterIndex.plus(AppConfig.preDownloadNum)
        )
        val cacheBook = CacheBook.getOrCreate(source, book)
        cacheBook.addDownload(book.durChapterIndex, endIndex)
    }

    /**
     * 缓存书籍
     */
    private fun cacheBook() {
        //开始缓存前，通知监听事件的书源，书架刷新已完成
        eventListenerSource.toList().forEach {
            SourceCallBack.callBackSource(viewModelScope, SourceCallBack.END_SHELF_REFRESH, it.first)
        }
        eventListenerSource.clear()
        if (AppConfig.preDownloadNum == 0) return
        cacheBookJob?.cancel()
        cacheBookJob = viewModelScope.launch(upTocPool) {
            launch {
                while (isActive && CacheBook.isRun) {
                    val isOnUpTocBooksEmpty = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        onUpTocBooks.isEmpty()
                    } else {
                        var isEmpty = true
                        onUpTocBooks.forEach { _ ->
                            isEmpty = false
                            return@forEach
                        }
                        isEmpty
                    }
                    //有目录更新是不缓存,优先更新目录,现在更多网站限制并发
                    CacheBook.setWorkingState(waitUpTocBooks.isEmpty() && isOnUpTocBooksEmpty)
                    delay(1000)
                }
            }
            CacheBook.startProcessJob(upTocPool)
        }
    }

    fun postLoad() {
        execute {
            if (appDb.httpTTSDao.count == 0) {
                DefaultData.httpTTS.let {
                    appDb.httpTTSDao.insert(*it.toTypedArray())
                }
            }
        }
    }

    fun restoreWebDav(name: String) {
        execute {
            AppWebDav.restoreWebDav(name)
        }
    }

    private fun deleteNotShelfBook() {
        execute {
            appDb.bookDao.deleteNotShelfBook()
        }
    }

    interface CallBack {
        fun openImportUi(type: Int, source: String)
    }

}