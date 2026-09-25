package io.legado.app.ui.main.bookshelf.style2

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.core.view.isGone
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.dao.BookShelfDisplay
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBookshelf2Binding
import io.legado.app.help.book.BookTagHelper
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
import io.legado.app.ui.main.bookshelf.compose.BookshelfBookEntry
import io.legado.app.ui.main.bookshelf.compose.BookshelfDisplayConfig
import io.legado.app.ui.main.bookshelf.compose.BookshelfEntry
import io.legado.app.ui.main.bookshelf.compose.BookshelfFolderEntry
import io.legado.app.ui.main.bookshelf.compose.BookshelfFolderItem
import io.legado.app.ui.main.bookshelf.compose.BookshelfFolderItemView
import io.legado.app.ui.main.bookshelf.compose.BookshelfGridItem
import io.legado.app.ui.main.bookshelf.compose.BookshelfListItem
import io.legado.app.ui.main.bookshelf.compose.buildBookshelfBookItems
import io.legado.app.ui.main.bookshelf.compose.updateBookshelfEntryUpdating
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.RoundedTagBarView
import io.legado.app.ui.widget.components.VerticalScrollbar
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
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * 书架界面（style2：文件夹继承树）。
 *
 * 顶栏、二级标签栏仍是 View，列表内容改为 Compose：根分组显示「文件夹 + 全部书籍」，
 * 进入分组后只显示该分组的书籍。文件夹与书籍各自选择列表/网格列数，用最小公倍数换算
 * 网格总列数与每个条目占用的列数（与原 spanSizeLookup 口径一致）。
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

    /** 适配器最近一次提交列表时所属的分组 */
    private var lastCommittedGroupId = BookGroup.IdRoot

    /** 当前展示的书籍（已按标签筛选），用于重建条目与目录更新 */
    private var shelfDisplays: List<BookShelfDisplay> = emptyList()
    private var displayConfig by mutableStateOf(BookshelfDisplayConfig.fromAppConfig())
    private var shelfEntries by mutableStateOf<List<BookshelfEntry>>(emptyList())
    private var bottomPaddingPx by mutableIntStateOf(0)
    private var canScrollBackward by mutableStateOf(false)
    private var scrollToTopTick by mutableIntStateOf(0)
    private var immediateScrollToTopTick by mutableIntStateOf(0)

    /**
     * 每个分组各自的列表状态。
     *
     * 切换分组时复用上次的 [LazyListState] / [LazyGridState]，退出再进入分组能回到原来的
     * 滚动位置（与原实现按分组保存/恢复 LayoutManager 状态一致）。
     */
    private val groupListStates = hashMapOf<Long, LazyListState>()
    private val groupGridStates = hashMapOf<Long, LazyGridState>()

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
            BookshelfContent()
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
            config = displayConfig,
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

    @Composable
    private fun BookshelfContent() {
        if (displayConfig.useSpanGrid) {
            BookshelfGridContent()
        } else {
            BookshelfListContent()
        }
    }

    /**
     * 列表 / 网格共用的间距：条目四周各留一个 margin（相邻条目之间即两个 margin），
     * 首个条目额外留出顶部空间，底部再叠加主导航栏高度。
     */
    @Composable
    private fun rememberShelfSpacing(): ShelfSpacing {
        val density = LocalDensity.current
        return remember(displayConfig.marginPx, bottomPaddingPx, density) {
            val marginPx = displayConfig.marginPx
            with(density) {
                ShelfSpacing(
                    itemMargin = marginPx.toDp(),
                    itemSpacing = (marginPx * 2).toDp(),
                    topPadding = (marginPx + AppDimens.shelfFirstItemExtraTop.toPx()).toDp(),
                    bottomPadding = (marginPx + bottomPaddingPx).toDp(),
                )
            }
        }
    }

    @Composable
    private fun rememberGroupListState(): LazyListState {
        val currentGroupId = groupId
        return remember(currentGroupId) {
            groupListStates.getOrPut(currentGroupId) { LazyListState() }
        }
    }

    @Composable
    private fun rememberGroupGridState(): LazyGridState {
        val currentGroupId = groupId
        return remember(currentGroupId) {
            groupGridStates.getOrPut(currentGroupId) { LazyGridState() }
        }
    }

    @Composable
    private fun BookshelfListContent() {
        val listState = rememberGroupListState()
        val spacing = rememberShelfSpacing()
        val canScrollBack by remember {
            derivedStateOf {
                listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
            }
        }
        LaunchedEffect(canScrollBack) {
            canScrollBackward = canScrollBack
        }
        LaunchedEffect(immediateScrollToTopTick) {
            if (immediateScrollToTopTick > 0) listState.scrollToItem(0)
        }
        LaunchedEffect(scrollToTopTick) {
            if (scrollToTopTick > 0) {
                if (AppConfig.isEInkMode) listState.scrollToItem(0)
                else listState.animateScrollToItem(0)
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AppDimens.shelfContentHorizontalPadding,
                    top = spacing.topPadding,
                    end = AppDimens.shelfContentHorizontalPadding,
                    bottom = spacing.bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.itemSpacing),
            ) {
                items(items = shelfEntries, key = { it.key }) { entry ->
                    BookshelfEntryItem(
                        entry = entry,
                        config = displayConfig,
                        onClick = ::onEntryClick,
                        onLongClick = ::onEntryLongClick,
                    )
                }
            }
            if (displayConfig.fastScrollerEnabled) {
                VerticalScrollbar(
                    state = listState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }

    @Composable
    private fun BookshelfGridContent() {
        val gridState = rememberGroupGridState()
        val spacing = rememberShelfSpacing()
        val canScrollBack by remember {
            derivedStateOf {
                gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
            }
        }
        LaunchedEffect(canScrollBack) {
            canScrollBackward = canScrollBack
        }
        LaunchedEffect(immediateScrollToTopTick) {
            if (immediateScrollToTopTick > 0) gridState.scrollToItem(0)
        }
        LaunchedEffect(scrollToTopTick) {
            if (scrollToTopTick > 0) {
                if (AppConfig.isEInkMode) gridState.scrollToItem(0)
                else gridState.animateScrollToItem(0)
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(displayConfig.spanCount.coerceAtLeast(1)),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AppDimens.shelfContentHorizontalPadding + spacing.itemMargin,
                    top = spacing.topPadding,
                    end = AppDimens.shelfContentHorizontalPadding + spacing.itemMargin,
                    bottom = spacing.bottomPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(spacing.itemSpacing),
                verticalArrangement = Arrangement.spacedBy(spacing.itemSpacing),
            ) {
                items(
                    items = shelfEntries,
                    key = { it.key },
                    span = { entry ->
                        GridItemSpan(
                            if (entry is BookshelfFolderEntry) displayConfig.folderGridSpan()
                            else displayConfig.bookGridSpan()
                        )
                    },
                ) { entry ->
                    BookshelfEntryItem(
                        entry = entry,
                        config = displayConfig,
                        onClick = ::onEntryClick,
                        onLongClick = ::onEntryLongClick,
                    )
                }
            }
            if (displayConfig.fastScrollerEnabled) {
                VerticalScrollbar(
                    state = gridState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }

    @Composable
    private fun BookshelfEntryItem(
        entry: BookshelfEntry,
        config: BookshelfDisplayConfig,
        onClick: (BookshelfEntry) -> Unit,
        onLongClick: (BookshelfEntry) -> Unit,
    ) {
        when (entry) {
            is BookshelfBookEntry -> if (config.isGrid) {
                BookshelfGridItem(
                    item = entry.book,
                    config = config,
                    onClick = { onClick(entry) },
                    onLongClick = { onLongClick(entry) },
                )
            } else {
                BookshelfListItem(
                    item = entry.book,
                    config = config,
                    onClick = { onClick(entry) },
                    onLongClick = { onLongClick(entry) },
                )
            }

            is BookshelfFolderEntry -> BookshelfFolderItemView(
                folder = entry.folder,
                folderLayout = config.folderLayout,
                onClick = { onClick(entry) },
                onLongClick = { onLongClick(entry) },
            )
        }
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
        viewLifecycleOwner.lifecycleScope.launch {
            val allText = getString(R.string.bookshelf_tag_all)
            val (tags, tagCounts) = withContext(Dispatchers.IO) {
                val configured = AppConfig.bookshelfGroupTags[currentGroupId].orEmpty()
                val hidden = AppConfig.bookshelfHiddenTags[currentGroupId].orEmpty()
                val allBooks = appDb.bookDao.allTagInfos
                val groupBooks = filterBooksByGroup(allBooks, currentGroupId)
                // 每本书的标签只解析一次，后续合并标签与统计数量复用
                val parsedTags = groupBooks.map { BookTagHelper.parseSet(it.customTag) }
                val existing = parsedTags.flatten()
                val merged = BookTagManagement.mergeTags(configured, existing)
                    .filter { tag -> hidden.none { it.equals(tag, ignoreCase = true) } }
                val smartRules = BookTagMatcher.enabledRules(requireContext())
                val snapshots = groupBooks.map { it.toSmartTagSnapshot() }
                // 追加智能标签：仅保留本分组内有书籍命中的规则（总开关关闭时为空）
                val smartNames = BookTagMatcher.matchingNames(snapshots, smartRules)
                val mergedTags = BookTagManagement.mergeTags(merged, smartNames)
                // 每个标签的命中数量，用于 "标签名·数量" 展示（自定义标签与智能标签同一口径）；
                // 空 key 代表"全部"标签，数量即分组内书籍总数
                val counts = BookTagMatcher.countMatches(
                    mergedTags,
                    parsedTags,
                    snapshots,
                    smartRules,
                ) + ("" to groupBooks.size)
                mergedTags to counts
            }
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
     * 根据 groupId 过滤书籍，逻辑与 [io.legado.app.ui.main.bookshelf.BookshelfTagManageViewModel.booksInGroup] 一致。
     * 默认分组（负数 ID）基于 [BookType] 筛选，用户分组（正数 ID）基于 group 位掩码筛选。
     */
    private fun filterBooksByGroup(
        books: List<io.legado.app.data.dao.BookTagInfo>,
        currentGroupId: Long,
    ): List<io.legado.app.data.dao.BookTagInfo> = when (currentGroupId) {
        BookGroup.IdAll -> books
        BookGroup.IdLocal -> books.filter { it.type and BookType.local > 0 }
        BookGroup.IdAudio -> books.filter { it.type and BookType.audio > 0 }
        BookGroup.IdVideo -> books.filter { it.type and BookType.video > 0 }
        BookGroup.IdError -> books.filter { it.type and BookType.updateError > 0 }
        else -> {
            val userGroupMask = appDb.bookGroupDao.all
                .filter { it.groupId > 0 }
                .fold(0L) { acc, group -> acc or group.groupId }
            when (currentGroupId) {
                BookGroup.IdNetNone -> books.filter {
                    it.type and BookType.audio == 0 &&
                        it.type and BookType.video == 0 &&
                        it.type and BookType.local == 0 &&
                        (it.group and userGroupMask) == 0L
                }

                BookGroup.IdLocalNone -> books.filter {
                    it.type and BookType.audio == 0 &&
                        it.type and BookType.video == 0 &&
                        it.type and BookType.local > 0 &&
                        (it.group and userGroupMask) == 0L
                }

                else -> if (currentGroupId > 0) {
                    books.filter { it.group and currentGroupId > 0 }
                } else {
                    emptyList()
                }
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

    /** 由显示配置换算出的间距 */
    private data class ShelfSpacing(
        val itemMargin: Dp,
        val itemSpacing: Dp,
        val topPadding: Dp,
        val bottomPadding: Dp,
    )
}
