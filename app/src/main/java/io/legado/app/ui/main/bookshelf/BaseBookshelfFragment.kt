package io.legado.app.ui.main.bookshelf

import android.annotation.SuppressLint
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.indices
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.DialogBookshelfConfigBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.cache.CacheActivity
import io.legado.app.ui.book.group.GroupManageDialog
import io.legado.app.ui.book.import.local.ImportBookActivity
import io.legado.app.ui.book.import.remote.RemoteBookActivity
import io.legado.app.ui.book.manage.BookshelfManageActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.checkByIndex
import io.legado.app.utils.getCheckedIndex
import io.legado.app.utils.postEvent
import io.legado.app.utils.readText
import io.legado.app.help.ExportResultHandler
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi

/**
 * 书架Fragment基类
 * 处理书架界面相关的逻辑，包括菜单操作、书籍导入导出等功能
 */
abstract class BaseBookshelfFragment(layoutId: Int) :
    VMBaseFragment<BookshelfViewModel>(layoutId),
    MainFragmentInterface {

    override val position: Int? get() = arguments?.getInt("position")

    val activityViewModel by activityViewModels<MainViewModel>()
    override val viewModel by viewModels<BookshelfViewModel>()

    /** 导入书单的ActivityResultLauncher，支持从文件选择器选择文件 */
    private val importBookshelf = registerForActivityResult(HandleFileContract()) {
        kotlin.runCatching {
            it.uri?.readText(requireContext())?.let { text ->
                viewModel.importBookshelf(text, groupId)
            }
        }.onFailure {
            toastOnUi(it.localizedMessage ?: "ERROR")
        }
    }

    /** 导出书单结果的ActivityResultLauncher，用于选择保存位置 */
    private val exportResult = registerForActivityResult(HandleFileContract()) {
        ExportResultHandler.handleExportResult(requireActivity() as androidx.appcompat.app.AppCompatActivity, it, { text ->
            requireContext().sendToClip(text)
        }, null)
    }

    /** 当前分组ID，用于确定导入书籍的分组 */
    abstract val groupId: Long

    /** 当前书架的书籍列表 */
    abstract val books: List<Book>

    /** 是否只更新已读书籍的目录 */
    abstract var onlyUpdateRead: Boolean

    /** 分组LiveData观察者 */
    private var groupsLiveData: LiveData<List<BookGroup>>? = null

    /** 添加书籍时的等待对话框 */
    private val waitDialog by lazy {
        WaitDialog(requireContext()).apply {
            setOnCancelListener {
                viewModel.addBookJob?.cancel()
            }
        }
    }

    abstract fun gotoTop()

    /** 创建选项菜单，加载main_bookshelf菜单资源 */
    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_bookshelf, menu)
    }

    /** 处理选项菜单的点击事件 */
    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_remote -> startActivity<RemoteBookActivity>()
            R.id.menu_search -> startActivity<SearchActivity>()
            R.id.menu_update_toc -> activityViewModel.upToc(books, onlyUpdateRead)
            R.id.menu_bookshelf_layout -> configBookshelf()
            R.id.menu_group_manage -> showDialogFragment<GroupManageDialog>()
            R.id.menu_tag_manage -> startActivity<BookshelfTagManageActivity> {
                putExtra("groupId", groupId)
            }
            R.id.menu_add_local -> startActivity<ImportBookActivity>()
            R.id.menu_add_url -> showAddBookByUrlAlert()
            R.id.menu_bookshelf_manage -> startActivity<BookshelfManageActivity> {
                putExtra("groupId", groupId)
            }

            R.id.menu_download -> startActivity<CacheActivity> {
                putExtra("groupId", groupId)
            }

            R.id.menu_export_bookshelf -> viewModel.exportBookshelf(books) { file ->
                exportResult.launch {
                    mode = HandleFileContract.EXPORT
                    fileData =
                        HandleFileContract.FileData("bookshelf.json", file, "application/json")
                }
            }

            R.id.menu_import_bookshelf -> importBookshelfAlert(groupId)
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
        }
    }

    /** 初始化书籍分组数据观察 */
    protected fun initBookGroupData() {
        groupsLiveData?.removeObservers(viewLifecycleOwner)
        groupsLiveData = appDb.bookGroupDao.show.apply {
            observe(viewLifecycleOwner) {
                upGroup(it)
            }
        }
    }

    abstract fun upGroup(data: List<BookGroup>)

    abstract fun upSort()

    /** 观察LiveBus事件，处理添加书籍进度更新 */
    override fun observeLiveBus() {
        viewModel.addBookProgressLiveData.observe(this) { count ->
            if (count < 0) {
                waitDialog.dismiss()
            } else {
                waitDialog.setText("添加中... ($count)")
            }
        }
    }

    /** 显示通过URL添加书籍的对话框 */
    @SuppressLint("InflateParams")
    fun showAddBookByUrlAlert() {
        alert(titleResource = R.string.add_book_url) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url"
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    waitDialog.setText("添加中...")
                    waitDialog.show()
                    viewModel.addBookByUrl(it)
                }
            }
            cancelButton()
        }
    }

    /** 显示书架布局配置对话框 */
    @SuppressLint("InflateParams")
    fun configBookshelf() {
        alert(titleResource = R.string.bookshelf_layout) {
            var bookshelfSort = AppConfig.bookshelfSort
            var showBookname = AppConfig.showBookname
            var bookLayout = AppConfig.bookLayout
            var folderLayout = AppConfig.folderLayout
            val alertBinding =
                DialogBookshelfConfigBinding.inflate(layoutInflater)
                    .apply {
                        if (AppConfig.bookGroupStyle !in 0..<spGroupStyle.count) {
                            AppConfig.bookGroupStyle = 0
                        }
                        if (bookshelfSort !in rgSort.indices) {
                            bookshelfSort = 0
                            AppConfig.bookshelfSort = 0
                        }
                        if (showBookname !in rgbLayout.indices) {
                            showBookname = 0
                            AppConfig.showBookname = 0
                        }
                        spGroupStyle.setSelection(AppConfig.bookGroupStyle)
                        spBookView.setSelection(bookLayout)
                        spFolderView.setSelection(folderLayout)
                        // 根据分组样式控制文件夹视图和下拉选择分组的可见性
                        llFolderView.visibility = if (AppConfig.bookGroupStyle == 1) View.VISIBLE else View.GONE
                        // 下拉选择分组开关仅在分组样式为标签（position == 0）时显示
                        swDropdownSelectGroup.visibility = if (AppConfig.bookGroupStyle == 0) View.VISIBLE else View.GONE
                        swDropdownSelectGroup.isChecked = AppConfig.dropdownSelectGroup
                        // 监听分组样式变化，动态更新文件夹视图和下拉选择分组的可见性
                        spGroupStyle.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                                llFolderView.visibility = if (position == 1) View.VISIBLE else View.GONE
                                // 下拉选择分组开关仅在分组样式为标签（position == 0）时显示
                                swDropdownSelectGroup.visibility = if (position == 0) View.VISIBLE else View.GONE
                            }
                            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                        }
                        swShowUnread.isChecked = AppConfig.showUnread
                        swShowLastUpdateTime.isChecked = AppConfig.showLastUpdateTime
                        swShowWaitUpBooks.isChecked = AppConfig.showWaitUpCount
                        swShowBookshelfFastScroller.isChecked = AppConfig.showBookshelfFastScroller
                        swShowBookshelfTagBar.isChecked = AppConfig.showBookshelfTagBar
                        // 初始化"显示更多信息"相关开关状态
                        llShowMoreInfo.visibility = if (bookLayout == 0) View.VISIBLE else View.GONE
                        swShowMoreInfo.isChecked = AppConfig.showMoreInfoInList
                        swShowIntro.isChecked = AppConfig.showIntroInList
                        swShowCategoryInfo.isChecked = AppConfig.showCategoryInfoInList
                        swShowReadProgress.isChecked = AppConfig.showBookshelfReadProgress
                        // 书籍外边框开关（仅在列表/紧凑列表视图时显示，默认关闭）
                        swShowBookBorder.visibility = if (bookLayout <= 1) View.VISIBLE else View.GONE
                        swShowBookBorder.isChecked = AppConfig.showBookBorder
                        // 子菜单可见性
                        swShowIntro.visibility = if (AppConfig.showMoreInfoInList) View.VISIBLE else View.GONE
                        swShowCategoryInfo.visibility = if (AppConfig.showMoreInfoInList) View.VISIBLE else View.GONE
                        swShowReadProgress.visibility = if (AppConfig.showMoreInfoInList) View.VISIBLE else View.GONE
                        // 简介行数选择器可见性（仅在显示简介勾选时显示）
                        tvIntroLines.visibility = if (AppConfig.showMoreInfoInList && AppConfig.showIntroInList) View.VISIBLE else View.GONE
                        // 更新简介行数显示文本
                        tvIntroLines.text = "${getString(R.string.intro_lines)}: ${AppConfig.introLinesInList}"
                        // 监听"显示更多信息"开关变化
                        swShowMoreInfo.setOnCheckedChangeListener { _, isChecked ->
                            swShowIntro.visibility = if (isChecked) View.VISIBLE else View.GONE
                            swShowCategoryInfo.visibility = if (isChecked) View.VISIBLE else View.GONE
                            swShowReadProgress.visibility = if (isChecked) View.VISIBLE else View.GONE
                            // 更新简介行数选择器可见性
                            tvIntroLines.visibility = if (isChecked && swShowIntro.isChecked) View.VISIBLE else View.GONE
                        }
                        // 监听"显示简介"开关变化
                        swShowIntro.setOnCheckedChangeListener { _, isChecked ->
                            tvIntroLines.visibility = if (isChecked && swShowMoreInfo.isChecked) View.VISIBLE else View.GONE
                        }
                        // 点击简介行数选择器，弹出 NumberPickerDialog
                        tvIntroLines.setOnClickListener {
                            NumberPickerDialog(requireContext())
                                .setTitle(getString(R.string.intro_lines))
                                .setMinValue(1)
                                .setMaxValue(10)
                                .setValue(AppConfig.introLinesInList)
                                .show { newValue ->
                                    AppConfig.introLinesInList = newValue
                                    tvIntroLines.text = "${getString(R.string.intro_lines)}: ${AppConfig.introLinesInList}"
                                    // 立即刷新书架以应用简介行数变化
                                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                                }
                        }
                        rgbLayout.checkByIndex(showBookname)
                        // 根据书籍视图控制书名显示选项的可见性
                        bookNameChoice.visibility = if (bookLayout > 1) View.VISIBLE else View.GONE
                        spBookView.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                                bookNameChoice.visibility = if (position > 1) View.VISIBLE else View.GONE
                                // 根据书籍视图控制"显示更多信息"的可见性（仅在标准列表视图时显示）
                                llShowMoreInfo.visibility = if (position == 0) View.VISIBLE else View.GONE
                                // 书籍外边框开关（仅在列表/紧凑列表视图时显示）
                                swShowBookBorder.visibility = if (position <= 1) View.VISIBLE else View.GONE
                            }
                            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                        }
                        rgSort.checkByIndex(bookshelfSort)
                        margin.progress = AppConfig.bookshelfMargin
                    }
            customView { alertBinding.root }
            okButton {
                alertBinding.apply {
                    var recreate = false
                    var refreshBookshelf = false
                    if (AppConfig.bookGroupStyle != spGroupStyle.selectedItemPosition) {
                        AppConfig.bookGroupStyle = spGroupStyle.selectedItemPosition
                        recreate = true // 分组样式改变需要重新创建Activity
                    }
                    if (bookLayout != spBookView.selectedItemPosition) {
                        AppConfig.bookLayout = spBookView.selectedItemPosition
                        refreshBookshelf = true
                    }
                    if (folderLayout != spFolderView.selectedItemPosition) {
                        AppConfig.folderLayout = spFolderView.selectedItemPosition
                        refreshBookshelf = true
                    }
                    if (showBookname != rgbLayout.getCheckedIndex()) {
                        AppConfig.showBookname = rgbLayout.getCheckedIndex()
                        // 书架列表已 Compose 化，条目读取走 BOOKSHELF_REFRESH 重新加载的显示配置，
                        // 不再需要整页重建
                        refreshBookshelf = true
                    }
                    if (AppConfig.bookshelfMargin != margin.progress) {
                        AppConfig.bookshelfMargin = margin.progress
                        refreshBookshelf = true
                    }
                    if (AppConfig.showUnread != swShowUnread.isChecked) {
                        AppConfig.showUnread = swShowUnread.isChecked
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    if (AppConfig.showLastUpdateTime != swShowLastUpdateTime.isChecked) {
                        AppConfig.showLastUpdateTime = swShowLastUpdateTime.isChecked
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    if (AppConfig.showWaitUpCount != swShowWaitUpBooks.isChecked) {
                        AppConfig.showWaitUpCount = swShowWaitUpBooks.isChecked
                        activityViewModel.postUpBooksLiveData(true)
                    }
                    if (AppConfig.showBookshelfFastScroller != swShowBookshelfFastScroller.isChecked) {
                        AppConfig.showBookshelfFastScroller = swShowBookshelfFastScroller.isChecked
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    if (AppConfig.showBookshelfTagBar != swShowBookshelfTagBar.isChecked) {
                        AppConfig.showBookshelfTagBar = swShowBookshelfTagBar.isChecked
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    // 保存"显示更多信息"相关配置
                    if (AppConfig.showMoreInfoInList != swShowMoreInfo.isChecked) {
                        AppConfig.showMoreInfoInList = swShowMoreInfo.isChecked
                        refreshBookshelf = true
                    }
                    if (AppConfig.showIntroInList != swShowIntro.isChecked) {
                        AppConfig.showIntroInList = swShowIntro.isChecked
                        refreshBookshelf = true
                    }
                    if (AppConfig.showCategoryInfoInList != swShowCategoryInfo.isChecked) {
                        AppConfig.showCategoryInfoInList = swShowCategoryInfo.isChecked
                        refreshBookshelf = true
                    }
                    if (AppConfig.showBookshelfReadProgress != swShowReadProgress.isChecked) {
                        AppConfig.showBookshelfReadProgress = swShowReadProgress.isChecked
                        refreshBookshelf = true
                    }
                    // 简介行数已在 NumberPickerDialog 回调中保存，无需在此处保存
                    // 保存"书籍外边框"开关配置
                    if (AppConfig.showBookBorder != swShowBookBorder.isChecked) {
                        AppConfig.showBookBorder = swShowBookBorder.isChecked
                        refreshBookshelf = true
                    }
                    // 保存"下拉选择分组"开关配置
                    if (AppConfig.dropdownSelectGroup != swDropdownSelectGroup.isChecked) {
                        AppConfig.dropdownSelectGroup = swDropdownSelectGroup.isChecked
                        recreate = true // 下拉选择分组改变需要重新创建Activity以更新标题栏行为
                    }
                    if (bookshelfSort != rgSort.getCheckedIndex()) {
                        AppConfig.bookshelfSort = rgSort.getCheckedIndex()
                        upSort()
                    }
                    if (recreate) {
                        postEvent(EventBus.RECREATE, "")
                    } else if (refreshBookshelf) {
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                }
            }
            cancelButton()
        }
    }

    /** 显示导入书单对话框，支持输入URL或选择本地文件 */
    private fun importBookshelfAlert(groupId: Long) {
        alert(titleResource = R.string.import_bookshelf) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url/json"
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    viewModel.importBookshelf(it, groupId)
                }
            }
            cancelButton()
            neutralButton(R.string.select_file) {
                importBookshelf.launch {
                    mode = HandleFileContract.FILE
                    allowExtensions = arrayOf("txt", "json")
                }
            }
        }
    }
}
