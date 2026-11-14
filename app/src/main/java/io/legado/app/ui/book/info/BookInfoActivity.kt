package io.legado.app.ui.book.info

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewGroup
import io.legado.app.ui.widget.text.ScrollTextView
import android.view.textclassifier.TextClassifier
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.Theme
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.databinding.ActivityBookInfoBinding
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.GlideImageGetter
import io.legado.app.help.TextViewTagHandler
import io.legado.app.help.WebCacheManager
import io.legado.app.help.book.addType
import io.legado.app.help.book.getRemoteUrl
import io.legado.app.lib.theme.EInkRender
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isVideo
import io.legado.app.help.book.isWebFile
import io.legado.app.help.book.removeType
import io.legado.app.help.book.BookTagHelper
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.webView.PooledWebView
import io.legado.app.help.webView.Scope
import io.legado.app.help.webView.WebJsExtensions
import io.legado.app.help.webView.WebJsExtensions.Companion.buildUseWebInjection
import io.legado.app.help.webView.WebJsExtensions.Companion.nameCache
import io.legado.app.help.webView.WebJsExtensions.Companion.nameJava
import io.legado.app.help.webView.WebJsExtensions.Companion.nameSource
import io.legado.app.help.webView.WebJsExtensions.Companion.wrapUseWebHtml
import io.legado.app.help.webView.WebViewPool
import io.legado.app.help.webView.WebViewPool.installInlineContentRefitOnTouch
import io.legado.app.help.webView.WebViewPool.prepareForInlineContent
import io.legado.app.help.webView.WebViewPool.scheduleInlineContentFit
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.model.BookCover
import io.legado.app.model.remote.RemoteBookWebDav
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.changecover.ChangeCoverDialog
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.group.GroupSelectDialog
import io.legado.app.ui.book.info.edit.BookInfoEditActivity
import io.legado.app.ui.book.manga.ReadMangaActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadBookActivity.Companion.RESULT_DELETED
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.search.SearchAdapter
import io.legado.app.domain.model.BookShelfState
import io.legado.app.model.SourceCallBack
import io.legado.app.model.login.isLoginUiV2
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.readRecord.BookReadRecordActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.video.VideoPlayerActivity
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.GSON
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.observeEvent
import io.legado.app.utils.openFileUri
import io.legado.app.utils.openUrl
import io.legado.app.utils.postEvent
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setHtml
import io.legado.app.utils.setMarkdown
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import io.legado.app.help.InnerBrowserLinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.Dispatchers.IO
import java.lang.ref.WeakReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.BookBottomSheet
import io.legado.app.ui.widget.refreshTopBarContentColor

class BookInfoActivity :
    VMBaseActivity<ActivityBookInfoBinding, BookInfoViewModel>(toolBarTheme = Theme.Dark, showOpenMenuIcon = false),
    GroupSelectDialog.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeCoverDialog.CallBack,
    VariableDialog.Callback,
    BookTagSelectDialog.Callback,
    SearchAdapter.CallBack {

    companion object {
        /**
         * 详情页堆叠深度上限：内部跳转（作者其他作品、底部弹窗查看详情）超过该层数时，
         * 淘汰最底层（最旧）实例再入栈，形成滑动窗口，避免深度浏览时任务栈无限增长
         */
        private const val MAX_STACK_DEPTH = 3

        /** 存活实例注册表（弱引用，按创建顺序排列），用于统计当前堆叠深度 */
        private val liveInstances = ArrayList<WeakReference<BookInfoActivity>>()

        private fun register(instance: BookInfoActivity) {
            liveInstances.removeAll { it.get() == null || it.get() === instance }
            liveInstances.add(WeakReference(instance))
        }

        private fun unregister(instance: BookInfoActivity) {
            liveInstances.removeAll { it.get() == null || it.get() === instance }
        }

        /** 当前有效实例（排除已销毁、正在退出、正在重建的），同时顺带清理失效引用 */
        private fun aliveInstances(): List<BookInfoActivity> {
            liveInstances.removeAll { it.get() == null || it.get()!!.isDestroyed }
            return liveInstances.mapNotNull { ref ->
                ref.get()?.takeUnless { it.isFinishing || it.isChangingConfigurations }
            }
        }

        /**
         * 详情页内部跳转到另一本书的详情页。
         * 堆叠深度达到 [MAX_STACK_DEPTH] 时先 finish 最底层（最旧）实例再入栈新实例，
         * 栈内最多保留最近浏览的 [MAX_STACK_DEPTH] 本书。
         */
        fun jumpToBookInfo(context: Context, configIntent: Intent.() -> Unit) {
            val instances = aliveInstances()
            if (instances.size >= MAX_STACK_DEPTH) {
                instances.firstOrNull()?.finish()
            }
            context.startActivity<BookInfoActivity>(configIntent)
        }
    }

    private val tocActivityResult = registerForActivityResult(TocActivityResult()) {
        it?.let {
            viewModel.getBook(false)?.let { book ->
                lifecycleScope.launch {
                    withContext(IO) {
                        val durChapterIndex = it[0] as Int
                        val durChapterPos = it[1] as Int
                        val durVolumeIndex = it[3] as Int
                        val chapterInVolumeIndex = it[4] as Int
                        book.durChapterIndex = durChapterIndex
                        book.durChapterPos = durChapterPos
                        chapterChanged = it[2] as Boolean
                        book.durVolumeIndex = durVolumeIndex
                        book.chapterInVolumeIndex = chapterInVolumeIndex
                        appDb.bookDao.update(book)
                    }
                    startReadActivity(book)
                }
            }
        } ?: let {
            if (!viewModel.inBookshelf) {
                viewModel.delBook(clearSearchBooks = false) // 进目录会保存book，此时退出目录触发的book删除，保留搜索记录以便再次进入
            }
        }
    }
    private val localBookTreeSelect = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { treeUri ->
            AppConfig.defaultBookTreeUri = treeUri.toString()
        }
    }
    private val readBookResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.upBook(intent)
        when (it.resultCode) {
            RESULT_OK -> {
                viewModel.inBookshelf = true
                upTvBookshelf()
            }

            RESULT_DELETED -> {
                setResult(RESULT_OK)
                finish()
            }
        }
    }
    private val infoEditResult = registerForActivityResult(
        StartActivityContract(BookInfoEditActivity::class.java),
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.upEditBook()
        }
    }
    private val editSourceResult = registerForActivityResult(
        StartActivityContract(BookSourceEditActivity::class.java),
    ) {
        if (it.resultCode == RESULT_CANCELED) {
            return@registerForActivityResult
        }
        book?.let { book ->
            // 书源 URL 可能已变更（含迁移关联书籍），重新从数据库加载书籍以获取最新 origin，
            // 否则用内存缓存的旧 origin 查源会查不到（旧 URL 的源已被删除），导致详情页显示“没有源”
            val freshBook = appDb.bookDao.getBook(book.bookUrl) ?: book
            viewModel.bookSource =
                appDb.bookSourceDao.getBookSource(freshBook.origin)?.also { source ->
                    viewModel.hasCustomBtn = source.customButton
                }
            viewModel.refreshBook(freshBook)
        }
    }
    private var chapterChanged = false
    private val waitDialog by lazy { WaitDialog(this) }
    private var editMenuItem: MenuItem? = null
    private var menuCustomBtn: MenuItem? = null
    private var authorOtherWorksRawBooks = emptyList<SearchBook>()

    /** 书籍底部弹窗状态 */
    private var showBookSheet by mutableStateOf(false)
    private var selectedBook by mutableStateOf<SearchBook?>(null)
    private var selectedBookShelfState by mutableStateOf(BookShelfState.NOT_IN_SHELF)
    private var bookSheetComposeView: ComposeView? = null
    private val book get() = viewModel.getBook(false)
    private var readRecordJob: Job? = null

    override val binding by viewBinding(ActivityBookInfoBinding::inflate)
    override val viewModel by viewModels<BookInfoViewModel>()
    private var initIntroView = false
    private val introTextView by lazy {
        initIntroView = true
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.view_book_intro, binding.tvIntroContainer, false) as ScrollTextView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            view.revealOnFocusHint = false
        }
        view
    }

    private var pooledWebView: PooledWebView? = null

    private val imgAvailableWidth by lazy {
        val textView = introTextView
        textView.width - textView.paddingLeft - textView.paddingRight - 8.dpToPx() // 8是为了文字对齐额外的右边距
    }
    private var initGetter = false
    private val glideImageGetter by lazy {
        initGetter = true
        GlideImageGetter(
            this,
            introTextView,
            lifecycle,
            imgAvailableWidth,
            viewModel.bookSource?.bookSourceUrl,
        )
    }

    private val textViewTagHandler by lazy {
        TextViewTagHandler(object : TextViewTagHandler.OnButtonClickListener {
            override fun onButtonClick(name: String, click: String) {
                viewModel.onButtonClick(this@BookInfoActivity, "info button $name", click)
            }
        })
    }

    private val authorOtherWorksAdapter by lazy {
        SearchAdapter(this, this)
    }

    @SuppressLint("PrivateResource")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        register(this)
        binding.titleBar.setBackgroundResource(R.color.transparent)
        // 顶栏被改成透明（露出封面底图）后必须重算前景色：applyTopBarConfig 是按"不透明底"
        // 算的内容色，不重算会让标题停在旧配色上，与随后重算的图标不同色、也可能与底图撞色
        binding.titleBar.refreshTopBarContentColor()
        binding.refreshLayout?.setColorSchemeColors(accentColor)
        binding.arcView?.setBgColor(backgroundColor)
        binding.llContentBg?.setBackgroundColor(backgroundColor)
        binding.llInfo.setBackgroundColor(backgroundColor)
        binding.llAuthorOtherWorks?.setBackgroundColor(backgroundColor)
        binding.vSpacerBottom?.setBackgroundColor(backgroundColor)
        binding.ivCoverC.setCardBackgroundColor(backgroundColor)
        if (!AppConfig.bookCoverShadow) {
            binding.ivCoverC.cardElevation = 0f
            binding.ivCoverC.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        binding.flAction.setBackgroundColor(bottomBackground)
        binding.flAction.applyNavigationBarPadding(withInitialPadding = true)
        binding.tvShelf.setTextColor(getPrimaryTextColor(ColorUtils.isColorLight(bottomBackground)))
        binding.tvToc.text = getString(R.string.toc_s, getString(R.string.loading))
        initAuthorOtherWorksView()
        viewModel.bookData.observe(this) { showBook(it) }
        viewModel.chapterListData.observe(this) { upLoading(false, it) }
        viewModel.authorOtherWorksData.observe(this) {
            showAuthorOtherWorks(it)
        }
        viewModel.waitDialogData.observe(this) { upWaitDialogStatus(it) }
        viewModel.initData(intent)
        initViewEvent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.initData(intent)
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_info, menu)
        editMenuItem = menu.findItem(R.id.menu_edit)
        menuCustomBtn = menu.findItem(R.id.menu_custom_btn).also {
            it.isVisible = viewModel.hasCustomBtn
        }
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_can_update)?.isChecked =
            viewModel.bookData.value?.canUpdate ?: true
        menu.findItem(R.id.menu_split_long_chapter)?.isChecked =
            viewModel.bookData.value?.getSplitLongChapter() ?: true
        menu.findItem(R.id.menu_login)?.isVisible =
            viewModel.bookSource?.let { !it.loginUrl.isNullOrBlank() || it.isLoginUiV2() } ?: false
        menu.findItem(R.id.menu_set_source_variable)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_set_book_variable)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_can_update)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_split_long_chapter)?.isVisible =
            viewModel.bookData.value?.isLocalTxt ?: false
        menu.findItem(R.id.menu_upload)?.isVisible =
            viewModel.bookData.value?.isLocal ?: false
        menu.findItem(R.id.menu_delete_alert)?.isChecked =
            LocalConfig.bookInfoDeleteAlert
        menu.findItem(R.id.menu_show_read_record)?.isChecked =
            AppConfig.bookInfoShowReadRecord
        menu.findItem(R.id.menu_show_author_other_works)?.isChecked =
            AppConfig.bookInfoShowAuthorOtherWorks
        menu.findItem(R.id.menu_show_book_tag)?.isChecked =
            AppConfig.bookInfoShowBookTag
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_custom_btn -> {
                viewModel.bookSource?.customButton?.let {
                    viewModel.getBook()?.let { book ->
                        SourceCallBack.callBackBtn(
                            this,
                            SourceCallBack.CLICK_CUSTOM_BUTTON,
                            viewModel.bookSource,
                            book,
                            null,
                        )
                    }
                }
            }

            R.id.menu_edit -> {
                viewModel.getBook()?.let {
                    infoEditResult.launch {
                        putExtra("bookUrl", it.bookUrl)
                    }
                }
            }

            R.id.menu_share_it -> {
                viewModel.getBook()?.let {
                    val bookJson = GSON.toJson(it)
                    val shareStr = "${it.bookUrl}#$bookJson"
                    SourceCallBack.callBackBtn(
                        this,
                        SourceCallBack.CLICK_SHARE_BOOK,
                        viewModel.bookSource,
                        it,
                        null,
                        result = shareStr,
                    ) {
                        val intent = Intent(Intent.ACTION_SEND)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.putExtra(Intent.EXTRA_TEXT, shareStr)
                        intent.type = "text/plain"
                        startActivity(Intent.createChooser(intent, it.name))
                    }
                }
            }

            R.id.menu_refresh -> {
                refreshBook()
            }

            R.id.menu_login -> viewModel.bookSource?.let {
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", it.bookSourceUrl)
                    putExtra("bookUrl", book?.bookUrl)
                }
            }

            R.id.menu_top -> viewModel.topBook()
            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_set_book_variable -> setBookVariable()
            R.id.menu_copy_book_url -> viewModel.getBook()?.let {
                SourceCallBack.callBackBtn(
                    this,
                    SourceCallBack.CLICK_COPY_BOOK_URL,
                    viewModel.bookSource,
                    it,
                    null,
                    result = it.bookUrl,
                ) {
                    sendToClip(it.bookUrl)
                }
            }

            R.id.menu_copy_toc_url -> viewModel.getBook()?.let {
                SourceCallBack.callBackBtn(
                    this,
                    SourceCallBack.CLICK_COPY_TOC_URL,
                    viewModel.bookSource,
                    it,
                    null,
                    result = it.tocUrl,
                ) {
                    sendToClip(it.tocUrl)
                }
            }

            // 是否禁止更新
            R.id.menu_can_update -> {
                viewModel.getBook()?.let {
                    it.canUpdate = !it.canUpdate
                    if (viewModel.inBookshelf) {
                        if (!it.canUpdate) {
                            it.removeType(BookType.updateError)
                            toastOnUi("已禁止更新，书架刷新时将不再检查此书的新章节")
                        }
                        viewModel.saveBook(it)
                    }
                }
            }

            R.id.menu_clear_cache -> viewModel.getBook()?.let { book ->
                alert(R.string.clear_cache) {
                    setMessage(getString(R.string.sure_clear_cache, book.name))
                    noButton()
                    yesButton {
                        SourceCallBack.callBackBtn(this@BookInfoActivity, SourceCallBack.CLICK_CLEAR_CACHE, viewModel.bookSource, book, null) {
                            viewModel.clearCache(book)
                        }
                    }
                }
            }
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_split_long_chapter -> {
                upLoading(true)
                viewModel.getBook()?.let {
                    it.setSplitLongChapter(!item.isChecked)
                    viewModel.loadBookInfo(it, false)
                }
                item.isChecked = !item.isChecked
                if (!item.isChecked) longToastOnUi(R.string.need_more_time_load_content)
            }

            R.id.menu_delete_alert -> LocalConfig.bookInfoDeleteAlert = !item.isChecked
            R.id.menu_show_read_record -> {
                AppConfig.bookInfoShowReadRecord = !item.isChecked
                viewModel.getBook()?.let { upReadRecord(it) }
            }
            R.id.menu_show_author_other_works -> {
                AppConfig.bookInfoShowAuthorOtherWorks = !item.isChecked
                viewModel.getBook()?.let { upAuthorOtherWorksVisibility(it) }
            }
            R.id.menu_show_book_tag -> {
                AppConfig.bookInfoShowBookTag = !item.isChecked
                viewModel.getBook()?.let { upTag(it) }
            }
            R.id.menu_upload -> {
                viewModel.getBook()?.let { book ->
                    book.getRemoteUrl()?.let {
                        alert(R.string.draw, R.string.sure_upload) {
                            okButton {
                                upLoadBook(book)
                            }
                            cancelButton()
                        }
                    } ?: upLoadBook(book)
                }
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun observeLiveBus() {
        viewModel.actionLive.observe(this) {
            when (it) {
                "selectBooksDir" -> localBookTreeSelect.launch {
                    title = getString(R.string.select_book_folder)
                }
            }
        }

        observeEvent<Boolean>(EventBus.REFRESH_BOOK_INFO) {
            // 书源js函数触发刷新
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                refreshBook()
            }
        }

        observeEvent<Boolean>(EventBus.REFRESH_BOOK_TOC) {
            // 书源js函数触发刷新
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                refreshToc()
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (initIntroView && ev.action == MotionEvent.ACTION_DOWN) {
            currentFocus?.let {
                if (it === introTextView && introTextView.hasSelection()) {
                    it.clearFocus()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun refreshBook() {
        upLoading(true)
        viewModel.getBook()?.let {
            viewModel.refreshBook(it)
        }
    }

    private fun refreshToc() {
        upLoading(true)
        viewModel.getBook()?.let {
            viewModel.loadChapter(it, true, isFromBookInfo = true)
        }
    }

    private fun upLoadBook(
        book: Book,
        bookWebDav: RemoteBookWebDav? = AppWebDav.defaultBookWebDav,
    ) {
        lifecycleScope.launch {
            waitDialog.setText("上传中.....")
            waitDialog.show()
            try {
                bookWebDav
                    ?.upload(book)
                    ?: throw NoStackTraceException("未配置webDav")
                // 更新书籍最后更新时间,使之比远程书籍的时间新
                book.lastCheckTime = System.currentTimeMillis()
                viewModel.saveBook(book)
            } catch (e: Exception) {
                toastOnUi(e.localizedMessage)
            } finally {
                waitDialog.dismiss()
            }
        }
    }

    private fun showBook(book: Book) = binding.run {
        AppLog.putReaderDebug(
            "[TOC] showBook回调: name=${book.name}, " +
                "intro.len=${book.intro?.length ?: -1}, intro.prefix='${book.intro?.take(16) ?: "null"}', " +
                "coverUrl='${book.coverUrl?.take(40) ?: "null"}', " +
                "latestChapterTitle='${book.latestChapterTitle}', " +
                "totalChapterNum=${book.totalChapterNum}",
        )
        showCover(book)
        tvName.text = book.name
        tvAuthor.text = getString(R.string.author_show, book.getRealAuthor())
        tvOrigin.text = getString(R.string.origin_show, book.originName)
        tvLasted.text = getString(R.string.lasted_show, book.latestChapterTitle)
        showBookIntro(book)
        upReadRecord(book)
        if (book.isWebFile) {
            llToc.gone()
            tvLasted.text = getString(R.string.lasted_show, "下载中...")
        } else {
            llToc.visible()
        }
        menuCustomBtn?.isVisible = viewModel.hasCustomBtn
        upTvBookshelf()
        upKinds(book)
        upGroup(book.group)
        viewModel.prepareAuthorOtherWorks(book)
        upAuthorOtherWorksVisibility(book)
    }

    private fun upAuthorOtherWorksVisibility(book: Book) {
        if (!AppConfig.bookInfoShowAuthorOtherWorks || book.isLocal || book.getRealAuthor().isBlank()) {
            binding.llAuthorOtherWorks?.gone()
        } else {
            binding.llAuthorOtherWorks?.visible()
        }
    }

    inner class CustomWebViewClient(
        private val source: BaseSource?,
    ) : WebViewClient() {
        private val jsStr = buildUseWebInjection(source)
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?,
        ): Boolean {
            request?.let {
                val uri = it.url
                return when (uri.scheme) {
                    "http", "https" -> false
                    "legado", "yuedu" -> {
                        startActivity<OnLineImportActivity> {
                            data = uri
                        }
                        true
                    }

                    else -> {
                        binding.root.longSnackbar(R.string.jump_to_another_app, R.string.confirm) {
                            openUrl(uri)
                        }
                        true
                    }
                }
            }
            return true
        }
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            view?.evaluateJavascript(jsStr, null)
        }
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            view?.let {
                scheduleInlineContentFit(it, afterLayout = {
                    binding.tvIntroContainer.requestLayout()
                })
            }
        }
    }

    private fun showBookIntro(book: Book) {
        val intro = book.getDisplayIntro()
        if (intro?.startsWith("<useweb>") == true) {
            val lastIndex = intro.lastIndexOf("<")
            if (lastIndex < 8) {
                introTextView.text = intro
                return
            }
            val rawHtml = intro.substring(8, lastIndex)
            val source = viewModel.bookSource
            // wrapUseWebHtml 会同步拼装 jsLib，URL 未命中缓存时内部 runBlocking 会阻塞主线程
            // 因此先切 IO 拼装，完成后回主线程做 WebView 装配 + loadDataWithBaseURL
            lifecycleScope.launch {
                val html = withContext(IO) { wrapUseWebHtml(rawHtml, source) }
                bindUseWebIntro(html, source)
            }
            return
        }
        if (!initIntroView || pooledWebView != null) {
            destroyWeb()
            binding.tvIntroContainer.removeAllViews()
            binding.tvIntroContainer.addView(introTextView)
        }
        if (intro.isNullOrBlank()) {
            return
        }
        val tvIntro = introTextView
        if (intro.startsWith("<usehtml>")) {
            val lastIndex = intro.lastIndexOf("<")
            if (lastIndex < 9) {
                tvIntro.text = intro
                return
            }
            val html = intro.substring(9, lastIndex)
            tvIntro.setHtml(
                html,
                glideImageGetter,
                textViewTagHandler,
                imgOnLongClickListener = {
                    showDialogFragment(PhotoDialog(it, viewModel.bookSource?.bookSourceUrl))
                },
                imgOnClickListener = {
                    viewModel.onButtonClick(this@BookInfoActivity, "info image", it)
                },
            )
        } else if (intro.startsWith("<md>")) {
            val lastIndex = intro.lastIndexOf("<")
            if (lastIndex < 4) {
                tvIntro.text = intro
                return
            }
            val mark = intro.substring(4, lastIndex)
            lifecycleScope.launch {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    tvIntro.setTextClassifier(TextClassifier.NO_OP)
                }
                val context = this@BookInfoActivity
                val markwon: Markwon
                val markdown = withContext(IO) {
                    markwon = Markwon.builder(context)
                        .usePlugin(object : io.noties.markwon.AbstractMarkwonPlugin() {
                            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                                builder.linkResolver(InnerBrowserLinkResolver)
                            }
                        })
                        .usePlugin(
                            GlideImagesPlugin.create(
                                Glide.with(context)
                                    .applyDefaultRequestOptions(
                                        RequestOptions()
                                            .override(imgAvailableWidth)
                                            .encodeQuality(88),
                                    ),
                            ),
                        )
                        .usePlugin(HtmlPlugin.create())
                        .usePlugin(TablePlugin.create(context))
                        .build()
                    markwon.toMarkdown(mark)
                }
                tvIntro.setMarkdown(
                    markwon,
                    markdown,
                    imgOnLongClickListener = { source ->
                        showDialogFragment(PhotoDialog(source, viewModel.bookSource?.bookSourceUrl))
                    },
                )
            }
        } else {
            tvIntro.text = intro
        }
    }

    /**
     * 主线程执行 useweb 简介的 WebView 装配与内容加载。
     *
     * 前置假设：html 已经在 IO 线程拼装完成（参考 [showBookIntro] 的 <useweb> 分支），
     * 本方法内不再触碰 SharedJsScope 相关的同步接口，避免主线程重新陷入 runBlocking。
     */
    private fun bindUseWebIntro(html: String, source: BookSource?) {
        val pooledWebView = this.pooledWebView ?: let {
            val pooledWebView = WebViewPool.acquire(this, Scope.INLINE)
            val webView = pooledWebView.realWebView
            webView.onResume()
            prepareForInlineContent(webView)
            installInlineContentRefitOnTouch(webView) {
                binding.tvIntroContainer.requestLayout()
            }
            webView.webViewClient = CustomWebViewClient(source)
            webView.addJavascriptInterface(WebCacheManager, nameCache)
            source?.let {
                webView.addJavascriptInterface(it as BaseSource, nameSource)
                val webJsExtensions = WebJsExtensions(it, this, webView)
                webView.addJavascriptInterface(webJsExtensions, nameJava)
            }
            pooledWebView
        }
        val webView = pooledWebView.realWebView
        prepareForInlineContent(webView)
        installInlineContentRefitOnTouch(webView) {
            binding.tvIntroContainer.requestLayout()
        }
        if (initIntroView || this.pooledWebView == null) {
            initIntroView = false
            this.pooledWebView = pooledWebView
            binding.tvIntroContainer.removeAllViews()
        }
        (webView.parent as? ViewGroup)?.removeView(webView)
        binding.tvIntroContainer.addView(webView)
        val bookUrl = viewModel.getBook()?.bookUrl
            ?.takeIf { it.startsWith("http", true) }
            ?.substringBefore(",")
        webView.loadDataWithBaseURL(bookUrl, html, "text/html", "utf-8", bookUrl)
    }

    private fun upKinds(book: Book) = binding.run {
        lifecycleScope.launch {
            var kinds = book.getKindList()
            if (book.isLocal) {
                withContext(IO) {
                    val size = FileDoc.fromFile(book.bookUrl).size
                    if (size > 0) {
                        kinds = kinds.toMutableList()
                        kinds.add(ConvertUtils.formatFileSize(size))
                    }
                }
            }
            if (kinds.isEmpty()) {
                lbKind.gone()
            } else {
                lbKind.visible()
                val source = viewModel.bookSource
                if (source == null) {
                    lbKind.setLabels(kinds)
                    return@launch
                }
                lbKind.setLabels(
                    kinds,
                    { kind ->
                        SourceCallBack.callBackBtn(
                            this@BookInfoActivity,
                            SourceCallBack.CLICK_BOOK_LABEL,
                            source,
                            book,
                            null,
                            result = kind,
                        ) {
                            SearchActivity.start(this@BookInfoActivity, source, kind)
                        }
                    },
                    { kind ->
                        SourceCallBack.callBackBtn(
                            this@BookInfoActivity,
                            SourceCallBack.LONG_CLICK_BOOK_LABEL,
                            source,
                            book,
                            null,
                            result = kind,
                        )
                        true
                    },
                )
            }
        }
    }

    private fun showCover(book: Book) {
        // 墨水屏渲染：整屏封面底图照常加载（此前在墨水屏下直接不加载，详情页看起来是空的），
        // 由 ImageView 的 colorFilter 转灰阶
        binding.bgBook.colorFilter = EInkRender.androidFilterOrNull()
        binding.ivCover.load(book, false) {
            BookCover.loadBlur(this, BookCover.getDisplayCover(book), false, book.origin)
                .into(binding.bgBook)
        }
    }

    private fun upLoading(isLoading: Boolean, chapterList: List<BookChapter>? = null) {
        when {
            isLoading -> {
                binding.tvToc.text = getString(R.string.toc_s, getString(R.string.loading))
            }

            chapterList.isNullOrEmpty() -> {
                binding.tvToc.text = getString(
                    R.string.toc_s,
                    getString(R.string.error_load_toc),
                )
                binding.tvLasted.text = getString(
                    R.string.lasted_show,
                    book?.latestChapterTitle.orEmpty(),
                )
            }

            else -> {
                val chapters = chapterList.orEmpty()
                book?.let {
                    // durChapterTitle / latestChapterTitle 都可能为空（例如尚未打开过的 JS 源书籍），
                    // 这里用目录首末章兜底，避免 String.format 把 null 打成字面量 "null"
                    val currentTitle = it.durChapterTitle?.takeIf(String::isNotBlank)
                        ?: chapters.firstOrNull()?.title.orEmpty()
                    val latestTitle = it.latestChapterTitle?.takeIf(String::isNotBlank)
                        ?: chapters.lastOrNull()?.title.orEmpty()
                    binding.tvToc.text = getString(R.string.toc_s, currentTitle)
                    binding.tvLasted.text = getString(R.string.lasted_show, latestTitle)
                }
            }
        }
    }

    private fun upTvBookshelf() {
        if (viewModel.inBookshelf) {
            binding.tvShelf.text = getString(R.string.remove_from_bookshelf)
        } else {
            binding.tvShelf.text = getString(R.string.add_to_bookshelf)
        }
        editMenuItem?.isVisible = viewModel.inBookshelf
        // 书架状态变化（如加入书架）时同步刷新标签行的可见性
        viewModel.getBook(false)?.let { upTag(it) }
    }

    private fun upGroup(groupId: Long) {
        viewModel.loadGroup(groupId) {
            if (it.isNullOrEmpty()) {
                binding.tvGroup.text = if (book?.isLocal == true) {
                    getString(R.string.group_s, getString(R.string.local_no_group))
                } else {
                    getString(R.string.group_s, getString(R.string.no_group))
                }
            } else {
                binding.tvGroup.text = getString(R.string.group_s, it)
            }
        }
    }

    private fun upTag(book: Book) {
        // 标签属于书架管理功能，未加入书架的书籍不显示标签行与设置入口
        if (!AppConfig.bookInfoShowBookTag || !viewModel.inBookshelf) {
            binding.llBookTag?.gone()
            return
        }
        binding.llBookTag?.visible()
        val tags = BookTagHelper.parse(book.customTag)
        val text = if (tags.isEmpty()) {
            getString(R.string.bookshelf_tag_none)
        } else {
            tags.joinToString(", ")
        }
        binding.tvTag?.text = getString(R.string.book_tag_s, text)
    }

    private fun initViewEvent() = binding.run {
        ivCover.setOnClickListener {
            viewModel.getBook()?.let {
                showDialogFragment(
                    ChangeCoverDialog(it.name, it.author),
                )
            }
        }
        ivCover.setOnLongClickListener {
            viewModel.getBook()?.let { book ->
                BookCover.getDisplayCover(book)?.let { path ->
                    showDialogFragment(PhotoDialog(path, isBook = true))
                }
            }
            true
        }
        tvRead.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (book.isWebFile) {
                    showWebFileDownloadAlert {
                        readBook(it)
                    }
                } else {
                    readBook(book)
                }
            }
        }
        tvShelf.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (viewModel.inBookshelf) {
                    deleteBook()
                } else {
                    if (book.isWebFile) {
                        showWebFileDownloadAlert()
                    } else {
                        viewModel.addToBookshelf(success = {
                            upTvBookshelf()
                        })
                    }
                }
            }
        }
        tvOrigin.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (book.isLocal) return@let
                if (!appDb.bookSourceDao.has(book.origin)) {
                    toastOnUi(R.string.error_no_source)
                    return@let
                }
                editSourceResult.launch {
                    putExtra("sourceUrl", book.origin)
                }
            }
        }
        tvChangeSource.setOnClickListener {
            viewModel.getBook()?.let { book ->
                showDialogFragment(ChangeBookSourceDialog(book.name, book.author))
            }
        }
        tvTocView.setOnClickListener {
            viewModel.getBook()?.let { book ->
                AppLog.putReaderDebug("[TOC] 点击查看目录: bookUrl=${book.bookUrl}, inBookshelf=${viewModel.inBookshelf}, chapterListData.size=${viewModel.chapterListData.value?.size}")
                if (!viewModel.inBookshelf) {
                    viewModel.saveBook(book) {
                        // 点击目录会保存book
                        viewModel.saveChapterList {
                            openChapterList()
                        }
                    }
                } else {
                    openChapterList()
                }
            }
        }
        llReadRecord.setOnClickListener {
            openBookReadRecord()
        }
        tvReadRecordView.setOnClickListener {
            openBookReadRecord()
        }
        tvChangeGroup.setOnClickListener {
            viewModel.getBook()?.let {
                showDialogFragment(
                    GroupSelectDialog(it.group),
                )
            }
        }
        tvSetTag?.setOnClickListener {
            viewModel.getBook()?.let {
                showDialogFragment(
                    BookTagSelectDialog(it.customTag),
                )
            }
        }
        tvAuthor.setOnClickListener {
            viewModel.getBook(false)?.let { book ->
                SourceCallBack.callBackBtn(
                    this@BookInfoActivity,
                    SourceCallBack.CLICK_AUTHOR,
                    viewModel.bookSource,
                    book,
                    null,
                    result = book.author,
                ) {
                    SearchActivity.start(this@BookInfoActivity, book.author)
                }
            }
        }
        tvAuthor.setOnLongClickListener {
            viewModel.getBook(false)?.let { book ->
                SourceCallBack.callBackBtn(
                    this@BookInfoActivity,
                    SourceCallBack.LONG_CLICK_AUTHOR,
                    viewModel.bookSource,
                    book,
                    null,
                    result = book.author,
                ) {
                    SearchActivity.start(this@BookInfoActivity, book.author)
                }
            }
            true
        }
        tvName.setOnClickListener {
            viewModel.getBook(false)?.let { book ->
                SourceCallBack.callBackBtn(
                    this@BookInfoActivity,
                    SourceCallBack.CLICK_BOOK_NAME,
                    viewModel.bookSource,
                    book,
                    null,
                    result = book.name,
                ) {
                    SearchActivity.start(this@BookInfoActivity, book.name)
                }
            }
        }
        tvName.setOnLongClickListener {
            viewModel.getBook(false)?.let { book ->
                SourceCallBack.callBackBtn(
                    this@BookInfoActivity,
                    SourceCallBack.LONG_CLICK_BOOK_NAME,
                    viewModel.bookSource,
                    book,
                    null,
                    result = book.name,
                ) {
                    SearchActivity.start(this@BookInfoActivity, book.name)
                }
            }
            true
        }
        refreshLayout?.setOnRefreshListener {
            refreshLayout?.isRefreshing = false
            refreshBook()
        }
    }

    private fun openBookReadRecord() {
        viewModel.getBook(false)?.let { book ->
            startActivity<BookReadRecordActivity> {
                putExtra(BookReadRecordActivity.EXTRA_BOOK_NAME, book.name)
                putExtra(BookReadRecordActivity.EXTRA_BOOK_AUTHOR, book.author)
            }
        }
    }

    private val readRecordRepository by lazy { ReadRecordRepository(appDb.readRecordDao) }

    private fun upReadRecord(book: Book) {
        if (!AppConfig.enableReadRecord || !AppConfig.bookInfoShowReadRecord) {
            binding.llReadRecord.gone()
            binding.llReadRecordList.gone()
            return
        }
        binding.llReadRecord.visible()
        binding.llReadRecordList.gone()
        readRecordJob?.cancel()
        readRecordJob = lifecycleScope.launch {
            readRecordRepository.getBookReadTime(book.name, book.author)
                .collect { totalReadTime ->
                    val summary = if (totalReadTime <= 0L) {
                        getString(R.string.no_read_record)
                    } else {
                        val totalStr = io.legado.app.utils.formatReadDuration(this@BookInfoActivity, totalReadTime)
                        getString(R.string.book_read_time_label, totalStr)
                    }
                    binding.tvReadRecord.text = summary
                }
        }
    }

    private fun setSourceVariable() {
        lifecycleScope.launch {
            val source = viewModel.bookSource
            if (source == null) {
                toastOnUi("书源不存在")
                return@launch
            }
            val comment =
                source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
            val variable = withContext(IO) { source.getVariable() }
            showDialogFragment(
                VariableDialog(
                    getString(R.string.set_source_variable),
                    source.getKey(),
                    variable,
                    comment,
                ),
            )
        }
    }

    private fun setBookVariable() {
        lifecycleScope.launch {
            val source = viewModel.bookSource
            if (source == null) {
                toastOnUi("书源不存在")
                return@launch
            }
            val book = viewModel.getBook() ?: return@launch
            val variable = withContext(IO) { book.getCustomVariable() }
            val comment = source.getDisplayVariableComment(
                """书籍变量可在js中通过book.getVariable("custom")获取""",
            )
            showDialogFragment(
                VariableDialog(
                    getString(R.string.set_book_variable),
                    book.bookUrl,
                    variable,
                    comment,
                ),
            )
        }
    }

    override fun setVariable(key: String, variable: String?) {
        when (key) {
            viewModel.bookSource?.getKey() -> viewModel.bookSource?.setVariable(variable)
            viewModel.bookData.value?.bookUrl -> viewModel.bookData.value?.let {
                it.putCustomVariable(variable)
                if (viewModel.inBookshelf) {
                    viewModel.saveBook(it)
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun deleteBook() {
        viewModel.getBook()?.let { book ->
            if (LocalConfig.bookInfoDeleteAlert) {
                alert(
                    titleResource = R.string.draw,
                    messageResource = R.string.sure_del,
                ) {
                    var checkBox: CheckBox? = null
                    if (book.isLocal) {
                        checkBox = CheckBox(this@BookInfoActivity).apply {
                            setText(R.string.delete_book_file)
                            isChecked = LocalConfig.deleteBookOriginal
                        }
                        val view = LinearLayout(this@BookInfoActivity).apply {
                            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
                            addView(checkBox)
                        }
                        customView { view }
                    }
                    yesButton {
                        if (checkBox != null) {
                            LocalConfig.deleteBookOriginal = checkBox.isChecked
                        }
                        SourceCallBack.callBackBook(SourceCallBack.DEL_BOOK_SHELF, viewModel.bookSource, book) // 确认后删除书架
                        viewModel.delBook(LocalConfig.deleteBookOriginal) {
                            setResult(RESULT_OK)
                            finish()
                        }
                    }
                    noButton()
                }
            } else {
                SourceCallBack.callBackBook(SourceCallBack.DEL_BOOK_SHELF, viewModel.bookSource, book) // 点按钮直接删除书架
                viewModel.delBook(LocalConfig.deleteBookOriginal) {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    private fun openChapterList() {
        viewModel.getBook()?.let {
            tocActivityResult.launch(it.bookUrl)
        }
    }

    private fun showWebFileDownloadAlert(
        onClick: ((Book) -> Unit)? = null,
    ) {
        val webFiles = viewModel.webFiles
        if (webFiles.isEmpty()) {
            toastOnUi("Unexpected webFileData")
            return
        }
        selector(
            R.string.download_and_import_file,
            webFiles,
        ) { _, webFile, _ ->
            if (webFile.isSupported) {
                /* import */
                viewModel.importOrDownloadWebFile<Book>(webFile) {
                    onClick?.invoke(it)
                }
            } else if (webFile.isSupportDecompress) {
                /* 解压筛选后再选择导入项 */
                viewModel.importOrDownloadWebFile<Uri>(webFile) { uri ->
                    viewModel.getArchiveFilesName(uri) { fileNames ->
                        if (fileNames.size == 1) {
                            viewModel.importArchiveBook(uri, fileNames[0]) {
                                onClick?.invoke(it)
                            }
                        } else {
                            showDecompressFileImportAlert(uri, fileNames, onClick)
                        }
                    }
                }
            } else {
                alert(
                    title = getString(R.string.draw),
                    message = getString(R.string.file_not_supported, webFile.name),
                ) {
                    neutralButton(R.string.open_fun) {
                        /* download only */
                        viewModel.importOrDownloadWebFile<Uri>(webFile) {
                            openFileUri(it, "*/*")
                        }
                    }
                    noButton()
                }
            }
        }
    }

    private fun showDecompressFileImportAlert(
        archiveFileUri: Uri,
        fileNames: List<String>,
        success: ((Book) -> Unit)? = null,
    ) {
        if (fileNames.isEmpty()) {
            toastOnUi(R.string.unsupport_archivefile_entry)
            return
        }
        selector(
            R.string.import_select_book,
            fileNames,
        ) { _, name, _ ->
            viewModel.importArchiveBook(archiveFileUri, name) {
                success?.invoke(it)
            }
        }
    }

    private fun readBook(book: Book) {
        if (!viewModel.inBookshelf) {
            book.addType(BookType.notShelf)
            viewModel.saveBook(book) {
                viewModel.saveChapterList {
                    startReadActivity(book)
                }
            }
        } else {
            viewModel.saveBook(book) {
                startReadActivity(book)
            }
        }
    }

    private fun startReadActivity(book: Book) {
        when {
            book.isAudio -> readBookResult.launch(
                Intent(this, AudioPlayActivity::class.java)
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf),
            )
            book.isVideo -> readBookResult.launch(
                Intent(this, VideoPlayerActivity::class.java)
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf),
            )

            else -> readBookResult.launch(
                Intent(
                    this,
                    if (!book.isLocal && book.isImage && AppConfig.showMangaUi) {
                        ReadMangaActivity::class.java
                    } else {
                        ReadBookActivity::class.java
                    },
                )
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf)
                    .putExtra("chapterChanged", chapterChanged),
            )
        }
    }

    override val oldBook: Book?
        get() = viewModel.bookData.value

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        viewModel.changeTo(source, book, toc)
    }

    override fun coverChangeTo(coverUrl: String) {
        viewModel.bookData.value?.let { book ->
            book.customCoverUrl = coverUrl
            showCover(book)
            if (viewModel.inBookshelf) {
                viewModel.saveBook(book)
            }
        }
    }

    override fun upGroup(requestCode: Int, groupId: Long) {
        upGroup(groupId)
        viewModel.getBook()?.let { book ->
            book.group = groupId
            if (viewModel.inBookshelf) {
                // 改分组会同时改变原分组与新分组的标签命中数量，写库完成后必须刷新书架标签栏
                viewModel.saveBook(book) {
                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                }
            } else if (groupId > 0) {
                viewModel.addToBookshelf({
                    upTvBookshelf()
                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                }, groupId)
            }
        }
    }

    override fun setTags(tags: List<String>) {
        viewModel.getBook()?.let { book ->
            book.customTag = BookTagHelper.join(tags)
            upTag(book)
            if (viewModel.inBookshelf) {
                // 新增标签同步注册进所属分组的标签配置，与管理标签"添加标签"行为一致
                viewModel.registerBookTags(book, tags)
                // 刷新事件必须在写库完成后发出，否则书架可能读到旧的 customTag
                viewModel.saveBook(book) {
                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                }
            }
        }
    }

    private fun upWaitDialogStatus(isShow: Boolean) {
        val showText = "Loading....."
        if (isShow) {
            waitDialog.run {
                setText(showText)
                show()
            }
        } else {
            waitDialog.dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        if (initGetter) {
            glideImageGetter.start()
        }
    }

    override fun onStop() {
        super.onStop()
        if (initGetter) {
            glideImageGetter.stop()
        }
    }

    override fun onDestroy() {
        unregister(this)
        readRecordJob?.cancel()
        destroyWeb()
        // 清理动态添加的 ComposeView，避免 Activity 销毁后 ComposeView 仍被
        // AccessibilityInteractionController.mTempArrayList 持有导致内存泄漏
        bookSheetComposeView?.let { view ->
            try {
                (view.parent as? ViewGroup)?.removeView(view)
            } catch (_: Exception) {
            }
        }
        bookSheetComposeView = null
        super.onDestroy()
        if (initGetter) {
            glideImageGetter.clear()
        }
    }

    private fun destroyWeb() {
        pooledWebView?.let { WebViewPool.release(it) }
        pooledWebView = null
    }

    private fun initAuthorOtherWorksView() = binding.run {
        rvAuthorOtherWorks?.layoutManager = LinearLayoutManager(this@BookInfoActivity)
        rvAuthorOtherWorks?.adapter = authorOtherWorksAdapter
        rvAuthorOtherWorks?.itemAnimator = null
        rvAuthorOtherWorks?.isNestedScrollingEnabled = false
        rvAuthorOtherWorks?.setEdgeEffectColor(accentColor)
        authorOtherWorksAdapter.showOriginCount = false
        ivAuthorOtherWorksRefresh?.setOnClickListener {
            viewModel.searchAuthorOtherWorks()
        }
    }

    private fun showAuthorOtherWorks(state: BookInfoViewModel.AuthorOtherWorksState) = binding.run {
        ivAuthorOtherWorksRefresh?.isEnabled = state !is BookInfoViewModel.AuthorOtherWorksState.Loading
        when (state) {
            BookInfoViewModel.AuthorOtherWorksState.Idle -> {
                rvAuthorOtherWorks?.gone()
                tvAuthorOtherWorksState?.visible()
                tvAuthorOtherWorksState?.text = getString(R.string.book_author_other_works_idle)
                resetAuthorOtherWorksDisplay()
            }

            BookInfoViewModel.AuthorOtherWorksState.Loading -> {
                rvAuthorOtherWorks?.gone()
                tvAuthorOtherWorksState?.visible()
                tvAuthorOtherWorksState?.text = getString(R.string.book_author_other_works_loading)
                resetAuthorOtherWorksDisplay()
            }

            BookInfoViewModel.AuthorOtherWorksState.Empty -> {
                rvAuthorOtherWorks?.gone()
                tvAuthorOtherWorksState?.visible()
                tvAuthorOtherWorksState?.text = getString(R.string.book_author_other_works_empty)
                resetAuthorOtherWorksDisplay()
            }

            is BookInfoViewModel.AuthorOtherWorksState.Success -> {
                tvAuthorOtherWorksState?.gone()
                rvAuthorOtherWorks?.visible()
                updateAuthorOtherWorksDisplay(state.books)
            }

            is BookInfoViewModel.AuthorOtherWorksState.Error -> {
                rvAuthorOtherWorks?.gone()
                tvAuthorOtherWorksState?.visible()
                tvAuthorOtherWorksState?.text =
                    getString(R.string.book_author_other_works_error, state.message)
                resetAuthorOtherWorksDisplay()
            }
        }
    }

    private fun resetAuthorOtherWorksDisplay() {
        authorOtherWorksRawBooks = emptyList()
        authorOtherWorksAdapter.setItems(emptyList())
    }

    private fun updateAuthorOtherWorksDisplay(books: List<SearchBook>) {
        authorOtherWorksRawBooks = books
        authorOtherWorksAdapter.setItems(books)
    }

    // SearchAdapter.CallBack 实现
    override fun getBookShelfState(book: SearchBook): BookShelfState = viewModel.getBookShelfState(book)

    override fun showBookInfo(name: String, author: String, bookUrl: String, origin: String) {
        jumpToBookInfo(this) {
            putExtra("name", name)
            putExtra("author", author)
            putExtra("bookUrl", bookUrl)
            putExtra("origin", origin)
            putExtra("fromAuthorOtherWorks", true)
        }
    }

    override fun onBookLongClick(book: SearchBook) {
        selectedBook = book
        selectedBookShelfState = viewModel.getBookShelfState(book)
        showBookSheet = true
        updateBookSheetView()
    }

    /**
     * 更新书籍底部弹窗的显示状态
     * 与首页、发现页、搜索页的长按弹窗行为完全一致
     */
    private fun updateBookSheetView() {
        val contentView = binding.root
        if (showBookSheet) {
            if (bookSheetComposeView == null) {
                bookSheetComposeView = ComposeView(this).also { composeView ->
                    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    composeView.setContent {
                        LegadoTheme {
                            BookBottomSheet(
                                show = showBookSheet,
                                book = selectedBook,
                                shelfState = selectedBookShelfState,
                                onDismiss = {
                                    showBookSheet = false
                                    updateBookSheetView()
                                },
                                onAddToShelf = { book ->
                                    viewModel.addAuthorOtherWorkToBookshelf(book)
                                },
                                onShowInfo = { book ->
                                    jumpToBookInfo(this@BookInfoActivity) {
                                        putExtra("name", book.name)
                                        putExtra("author", book.author)
                                        putExtra("bookUrl", book.bookUrl)
                                        putExtra("fromAuthorOtherWorks", true)
                                    }
                                },
                            )
                        }
                    }
                    val params = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                    contentView.addView(composeView, params)
                }
            }
        } else {
            bookSheetComposeView?.let {
                contentView.removeView(it)
                bookSheetComposeView = null
            }
        }
    }
}
