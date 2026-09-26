package io.legado.app.ui.main.bookshelf.style2

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.isGone
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.dao.BookShelfDisplay
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBookshelf2Binding
import io.legado.app.help.book.BookTagManagement
import io.legado.app.help.book.BookTagMatcher
import io.legado.app.help.book.toSmartTagSnapshot
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.book.group.GroupEditDialog
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.loadBookshelfTagBarData
import io.legado.app.ui.main.bookshelf.compose.BookshelfBookEntry
import io.legado.app.ui.main.bookshelf.compose.BookshelfDisplayConfig
import io.legado.app.ui.main.bookshelf.compose.BookshelfEntry
import io.legado.app.ui.main.bookshelf.compose.BookshelfFolderEntry
import io.legado.app.ui.main.bookshelf.compose.BookshelfFolderItem
import io.legado.app.ui.main.bookshelf.compose.buildBookshelfBookItems
import io.legado.app.ui.main.bookshelf.compose.updateBookshelfEntryUpdating
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.RoundedTagBarView
import io.legado.app.utils.cnCompare
import io.legado.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legado.app.utils.observeEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 书架界面（style2：文件夹继承树）。
 *
 * 顶栏、二级标签栏仍是 View，列表内容改为 Compose（内容见 [BookshelfShelfTreeContent]）：
 * 根分组显示「文件夹 + 全部书籍」，进入分组后只显示该分组的书籍。文件夹与书籍各自选择
 * 列表/网格列数，用最小公倍数换算网格总列数与每个条目占用的列数（与原 spanSizeLookup 口径一致）。
 */
class BookshelfFragment2() :
    BaseBookshelfFragment(R.layout.fragment_bookshelf2),
    SearchView.OnQueryTextListener {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBookshelf2Binding::bind)
    private var bookGroups: List<BookGroup> = emptyList()
    private var booksFlowJob: Job? = null
    override var groupId = BookGroup.IdRoot
    override var books: List<Book> = emptyList()
    override var onlyUpdateRead = false
    private var enableRefresh = true
    private var tagFilter: String? = null
    private var tagBar: RoundedTagBarView? = null
    private var tagSelectedIndex = -1
    private var currentTagList: List<String> = emptyList()

    /** 二级标签栏数据是否已就绪；显隐变化统一推迟到列表提交同帧生效，消除转场残留帧 */
    private var tagBarLoaded = false

    /** 列表最近一次提交时所属的分组 */
    private var lastCommittedGroupId = BookGroup.IdRoot

    /** 当前展示的书籍（已按标签筛选），用于重建条目与目录更新 */
    private var shelfDisplays: List<BookShelfDisplay> = emptyList()
    private var displayConfig by mutableStateOf(BookshelfDisplayConfig.fromAppConfig())
    private var shelfEntries by mutableStateOf<List<BookshelfEntry>>(emptyList())
    private var bottomPaddingPx by mutableIntStateOf(0)
    private var canScrollBackward by mutableStateOf(false)
    private var scrollToTopTick by mutableIntStateOf(0)
    private var immediateScrollToTopTick by mutableIntStateOf(0)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        tagBar = binding.tagBar
        tagBar?.setOnTagClickListener { index ->
            tagSelectedIndex = index
            tagBar?.setSelectedIndex(index)
            applyTagFilter()
        }
        initComposeShelf()
        initBookGroupData()
        initBooksData()
    }

    private fun initComposeShelf() {
        updateMainBottomPadding((activity as? MainActivity)?.mainContentBottomPadding() ?: 0)
        binding.refreshLayout.setColorSchemeColors(accentColor)
        // ComposeView 不参与 View 体系的滚动测量，下拉刷新能否触发由列表状态反向同步
        binding.refreshLayout.setOnChildScrollUpCallback { _, _ -> canScrollBackward }
        binding.refreshLayout.setOnRefreshListener {
            binding.refreshLayout.isRefreshing = false
            activityViewModel.upToc(books, onlyUpdateRead)
        }
        binding.composeBookshelf.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.composeBookshelf.setContent {
            // 与 style1 一致：只注入主题色板，不带背景（壁纸由外壳承载）
            LegadoTheme {
                BookshelfShelfTreeContent(
                    shelfEntries = shelfEntries,
                    displayConfig = displayConfig,
                    groupId = groupId,
                    bottomPaddingPx = bottomPaddingPx,
                    scrollToTopTick = scrollToTopTick,
                    immediateScrollToTopTick = immediateScrollToTopTick,
                    onScrollBackwardChange = { canScrollBackward = it },
                    onEntryClick = ::onEntryClick,
                    onEntryLongClick = ::onEntryLongClick,
                )
            }
        }
    }

    override fun updateMainBottomPadding(bottomPadding: Int) {
        bottomPaddingPx = bottomPadding
    }

    override fun upGroup(data: List<BookGroup>) {
        if (data != bookGroups) {
            bookGroups = data
            rebuildEntries()
        }
    }

    override fun upSort() {
        initBooksData()
    }

    private fun initBooksData() {
        if (groupId == BookGroup.IdRoot) {
            // 退出到主书架：标签栏数据态先复位，显隐推迟到列表提交同帧收起，
            // 避免旧分组内容以"无标签栏"状态残留数帧
            tagBarLoaded = false
            tagFilter = null
            if (isAdded) {
                binding.titleBar.title = getString(R.string.bookshelf)
                binding.refreshLayout.isEnabled = true
                enableRefresh = true
            }
        } else {
            bookGroups.firstOrNull { groupId == it.groupId }?.let {
                binding.titleBar.title = "${getString(R.string.bookshelf)}(${it.groupName})"
                binding.refreshLayout.isEnabled = it.enableRefresh
                enableRefresh = it.enableRefresh
                onlyUpdateRead = it.onlyUpdateRead
            }
            // 加载标签栏（标签栏完成后会自行刷新数据流，不会回调 initBooksData）
            loadTagBar()
        }
        restartBooksFlow()
    }

    /**
     * 启动/重启书籍数据流。
     * 根据当前 [groupId] 和 [tagFilter] 从数据库加载书籍并筛选。
     */
    private fun restartBooksFlow() {
        booksFlowJob?.cancel()
        booksFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowShelfByGroup(groupId).map { list ->
                when (AppConfig.getBookSortByGroupId(groupId)) {
                    1 -> list.sortedByDescending { it.latestChapterTime }
                    2 -> list.sortedWith { o1, o2 -> o1.name.cnCompare(o2.name) }
                    3 -> list.sortedBy { it.order }
                    4 -> list.sortedByDescending { max(it.latestChapterTime, it.durChapterTime) }
                    // SQL 已按 durChapterTime DESC 排序，无需再排
                    else -> list
                }
            }.flowWithLifecycleAndDatabaseChangeFirst(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.STARTED,
                AppDatabase.BOOK_TABLE_NAME,
            ).catch {
                AppLog.put("书架更新出错", it)
            }.conflate().flowOn(Dispatchers.Default).collect { list ->
                // 注意 flowOn 只影响上游，collect 仍运行在主线程，可安全取 context
                val filtered = filterShelfByTag(list)
                shelfDisplays = filtered
                books = filtered.map { it.toMinimalBook() }
                rebuildEntries()
            }
        }
    }

    /**
     * 重建条目列表。
     *
     * 根分组 = 文件夹 + 全部书籍；分组内只有书籍。标签栏显隐与条目提交绑在同一处，
     * 保证与列表内容同帧切换（原实现挂在 AsyncListDiffer 的提交回调上）。
     */
    private fun rebuildEntries() {
        val bookItems = buildBookshelfBookItems(
            context = requireContext(),
            displays = shelfDisplays,
            displayConfig = displayConfig,
            isUpdating = ::isUpdate,
        )
        val entries = ArrayList<BookshelfEntry>(bookItems.size + bookGroups.size)
        if (groupId == BookGroup.IdRoot) {
            bookGroups.forEach { entries.add(BookshelfFolderEntry(BookshelfFolderItem.from(it))) }
        }
        bookItems.forEach { entries.add(BookshelfBookEntry(it)) }
        shelfEntries = entries
        lastCommittedGroupId = groupId
        updateTagBarVisibility()
        val count = entries.size
        binding.tvEmptyMsg.isGone = count > 0
        binding.refreshLayout.isEnabled = enableRefresh && count > 0
    }

    private fun updateTagBarVisibility() {
        tagBar?.visibility =
            if (lastCommittedGroupId != BookGroup.IdRoot && tagBarLoaded) View.VISIBLE else View.GONE
    }

    fun back(): Boolean {
        if (groupId != BookGroup.IdRoot) {
            groupId = BookGroup.IdRoot
            // 不在此处收起标签栏：过早 GONE 会让旧分组内容以无标签栏状态残留数帧，
            // 收起时机由 rebuildEntries 与新列表提交绑定在同一帧
            tagFilter = null
            // 检查 View 是否存在，避免崩溃
            if (view != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                initBooksData()
            }
            return true
        }
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        SearchActivity.start(requireContext(), query)
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean = false

    override fun gotoTop() {
        if (AppConfig.isEInkMode) {
            immediateScrollToTopTick++
        } else {
            scrollToTopTick++
        }
    }

    private fun onEntryClick(entry: BookshelfEntry) {
        when (entry) {
            is BookshelfBookEntry -> startActivityForBook(entry.book.display.toMinimalBook())

            is BookshelfFolderEntry -> {
                groupId = entry.folder.groupId
                initBooksData()
            }
        }
    }

    private fun onEntryLongClick(entry: BookshelfEntry) {
        when (entry) {
            is BookshelfBookEntry -> {
                val book = entry.book.display.toMinimalBook()
                startActivity<BookInfoActivity> {
                    putExtra("name", book.name)
                    putExtra("author", book.author)
                }
            }

            is BookshelfFolderEntry -> bookGroups.firstOrNull { it.groupId == entry.folder.groupId }
                ?.let { showDialogFragment(GroupEditDialog(it)) }
        }
    }

    private fun isUpdate(bookUrl: String): Boolean = activityViewModel.isUpdate(bookUrl)

    /**
     * 加载当前分组的二级标签栏数据。
     *
     * 标签栏关闭时清除筛选状态并刷新数据流；
     * 标签栏开启时异步加载标签，完成后设置默认筛选（全部）并刷新数据流。
     * 标签来源：当前分组中实际有书籍使用的标签 + 用户手动配置的标签，
     * 减去被隐藏的标签。只显示当前分组中有对应书籍的标签。
     * 本方法不会回调 [initBooksData]，避免循环调用。
     */
    private fun loadTagBar() {
        if (!AppConfig.showBookshelfTagBar) {
            // 仅复位数据态，显隐由 rebuildEntries 在列表提交时处理，避免与内容切换脱节
            tagBarLoaded = false
            tagSelectedIndex = -1
            currentTagList = emptyList()
            tagFilter = null
            // 不在此处 restartBooksFlow，由调用方 initBooksData 负责
            return
        }
        val currentGroupId = groupId
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val allText = getString(R.string.bookshelf_tag_all)
            val (tags, tagCounts) = loadBookshelfTagBarData(context, currentGroupId)
            // 查询期间已切换分组（如快速进出分组），丢弃过期结果
            if (currentGroupId != groupId) return@launch
            // 在标签列表前插入空字符串作为"全部"标签
            currentTagList = listOf("") + tags
            tagSelectedIndex = 0
            tagBar?.applyTopBarStyle(force = true)
            tagBar?.submitItems(
                currentTagList.map { tag ->
                    RoundedTagBarView.Item(
                        BookTagManagement.tagBarLabel(tag, allText, tagCounts[tag] ?: 0),
                    )
                },
                0,
            )
            tagBar?.setSelectedIndex(0, false)
            tagBarLoaded = true
            // 仅当列表内容已切换到当前分组时立即显示；
            // 否则等待 rebuildEntries 在内容提交同帧显示，避免标签栏先于内容出现
            updateTagBarVisibility()
            // 标签栏加载完成后，默认选"全部"（tagFilter=null）。
            // 仅在 tagFilter 有非空旧值时才需重启数据流，避免不必要的取消/重启导致列表闪烁。
            if (tagFilter != null) {
                tagFilter = null
                restartBooksFlow()
            }
        }
    }


    /**
     * 应用当前选中的标签筛选，重新加载数据流。
     * "全部"标签（索引0）传 null 表示不筛选。
     */
    private fun applyTagFilter() {
        val selectedIndex = tagSelectedIndex
        tagFilter = if (selectedIndex <= 0 || selectedIndex >= currentTagList.size) {
            null
        } else {
            currentTagList[selectedIndex]
        }
        // 只重启数据流，不调用 initBooksData，避免 loadTagBar → initBooksData → loadTagBar 循环
        restartBooksFlow()
    }

    private fun filterShelfByTag(list: List<BookShelfDisplay>): List<BookShelfDisplay> {
        val filterTag = tagFilter ?: return list
        val smartRules = BookTagMatcher.enabledRules(requireContext())
        return list.filter {
            BookTagMatcher.matches(
                filterTag,
                it.customTag,
                it.toSmartTagSnapshot(),
                smartRules,
            )
        }
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.UP_BOOKSHELF) {
            shelfEntries = updateBookshelfEntryUpdating(shelfEntries, it, ::isUpdate)
        }
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
            displayConfig = BookshelfDisplayConfig.fromAppConfig()
            // 布局、边距、条目内容开关变化后需要重建条目
            rebuildEntries()
            // 刷新标签栏（开关状态可能变化）
            if (groupId != BookGroup.IdRoot) {
                loadTagBar()
                // 标签栏从开变关时 loadTagBar 清除了 tagFilter，
                // 需要重启数据流以应用无筛选状态
                restartBooksFlow()
            }
        }
        // 顶栏配置变更时，同步刷新二级标签栏样式
        observeEvent<Boolean>(EventBus.TOP_BAR_CHANGED) { isNightMode ->
            if (isNightMode == AppConfig.isNightTheme) {
                tagBar?.applyTopBarStyle(force = true)
            }
        }
    }
}
