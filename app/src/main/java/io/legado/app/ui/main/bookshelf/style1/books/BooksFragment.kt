package io.legado.app.ui.main.bookshelf.style1.books

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.dao.BookShelfDisplay
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBooksBinding
import io.legado.app.help.book.BookTagMatcher
import io.legado.app.help.book.toSmartTagSnapshot
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.main.bookshelf.compose.BookshelfBookItem
import io.legado.app.ui.main.bookshelf.compose.BookshelfDisplayConfig
import io.legado.app.ui.main.bookshelf.compose.BookshelfGridItem
import io.legado.app.ui.main.bookshelf.compose.BookshelfListItem
import io.legado.app.ui.main.bookshelf.compose.buildBookshelfBookItems
import io.legado.app.ui.main.bookshelf.compose.updateBookshelfBookUpdating
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.VerticalScrollbar
import io.legado.app.utils.cnCompare
import io.legado.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legado.app.utils.observeEvent
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 书架界面（style1 内层分组页）。
 *
 * 分组骨架（TabLayout/下拉 + 内层 ViewPager + 二级标签栏）仍在 View 侧，这里只把每个
 * 分组的书籍列表换成 Compose 渲染；排序、标签筛选、下拉刷新、空态提示、底部内边距、
 * 快速滚动条与"回到顶部"等行为与原实现保持一致。
 */
class BooksFragment() : BaseFragment(R.layout.fragment_books) {

    constructor(position: Int, group: BookGroup) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        bundle.putLong("groupId", group.groupId)
        bundle.putInt("bookSort", group.getRealBookSort())
        bundle.putBoolean("enableRefresh", group.enableRefresh)
        bundle.putBoolean("onlyUpdateRead", group.onlyUpdateRead)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBooksBinding::bind)
    private val activityViewModel by activityViewModels<MainViewModel>()
    private var booksFlowJob: Job? = null
    var position = 0
        private set
    var groupId = -1L
        private set
    var bookSort = 0
        private set
    private var upLastUpdateTimeJob: Job? = null
    private var enableRefresh = true
    private var onlyUpdateRead = false
    private var tagFilter: String? = null

    /** 当前展示的书籍（已按标签筛选，供目录更新与重新构建条目复用） */
    private var shelfDisplays: List<BookShelfDisplay> = emptyList()
    private var displayConfig by mutableStateOf(BookshelfDisplayConfig.fromAppConfig())
    private var shelfItems by mutableStateOf<List<BookshelfBookItem>>(emptyList())
    private var bottomPaddingPx by mutableIntStateOf(0)
    private var canScrollBackward by mutableStateOf(false)
    private var scrollToTopTick by mutableIntStateOf(0)
    private var immediateScrollToTopTick by mutableIntStateOf(0)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.let {
            position = it.getInt("position", 0)
            groupId = it.getLong("groupId", -1)
            bookSort = it.getInt("bookSort", 0)
            enableRefresh = it.getBoolean("enableRefresh", true)
            onlyUpdateRead = it.getBoolean("onlyUpdateRead", false)
            binding.refreshLayout.isEnabled = enableRefresh
        }
        initSwipeRefresh()
        upRecyclerData()
    }

    private fun initSwipeRefresh() {
        binding.refreshLayout.setColorSchemeColors(accentColor)
        // ComposeView 不参与 View 体系的滚动测量，下拉刷新能否触发由列表状态反向同步
        binding.refreshLayout.setOnChildScrollUpCallback { _, _ -> canScrollBackward }
        binding.refreshLayout.setOnRefreshListener {
            binding.refreshLayout.isRefreshing = false
            activityViewModel.upToc(getBooks(), onlyUpdateRead)
        }
        binding.composeBookshelf.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.composeBookshelf.setContent {
            BookshelfContent()
        }
        startLastUpdateTimeJob()
    }

    @Composable
    private fun BookshelfContent() {
        if (displayConfig.isGrid) {
            BookshelfGridContent()
        } else {
            BookshelfListContent()
        }
    }

    /**
     * 列表 / 网格共用的间距。
     *
     * 对齐原 ItemDecoration 的口径：条目四周各留一个 margin（相邻条目之间即两个 margin），
     * 首个条目额外留出顶部空间，底部再叠加主导航栏高度以让内容能滚到底栏之上。
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
    private fun BookshelfListContent() {
        val listState = rememberLazyListState()
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
                items(items = shelfItems, key = { it.key }) { item ->
                    BookshelfListItem(
                        item = item,
                        config = displayConfig,
                        onClick = ::onBookClick,
                        onLongClick = ::onBookLongClick,
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
        val gridState = rememberLazyGridState()
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
                columns = GridCells.Fixed(displayConfig.bookLayout.coerceAtLeast(2)),
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
                items(items = shelfItems, key = { it.key }) { item ->
                    BookshelfGridItem(
                        item = item,
                        config = displayConfig,
                        onClick = ::onBookClick,
                        onLongClick = ::onBookLongClick,
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

    fun updateMainBottomPadding(bottomPadding: Int) {
        bottomPaddingPx = bottomPadding
    }

    fun upBookSort(sort: Int) {
        binding.root.post {
            arguments?.putInt("bookSort", sort)
            bookSort = sort
            upRecyclerData()
        }
    }

    fun setEnableRefresh(enable: Boolean) {
        enableRefresh = enable
        binding.refreshLayout.isEnabled = enable
    }

    /**
     * 更新书籍列表数据。
     *
     * 数据源、排序口径与标签筛选逻辑与原实现一致，只是在收集时把 [BookShelfDisplay]
     * 转成条目 UI 模型再交给 Compose 渲染。
     */
    private fun upRecyclerData() {
        booksFlowJob?.cancel()
        booksFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowShelfByGroup(groupId).map { list ->
                when (bookSort) {
                    1 -> list.sortedByDescending { it.latestChapterTime }
                    2 -> list.sortedWith { o1, o2 -> o1.name.cnCompare(o2.name) }
                    3 -> list.sortedBy { it.order }
                    // 综合排序 issue #3192
                    4 -> list.sortedByDescending { max(it.latestChapterTime, it.durChapterTime) }
                    5 -> list.sortedWith { o1, o2 -> o1.author.cnCompare(o2.author) }
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
                // 注意 flowOn 只影响上游，collect 仍运行在主线程
                val filtered = applyTagFilter(list)
                shelfDisplays = filtered
                binding.tvEmptyMsg.isGone = filtered.isNotEmpty()
                binding.refreshLayout.isEnabled = enableRefresh && filtered.isNotEmpty()
                shelfItems = buildItems(filtered)
            }
        }
    }

    private fun buildItems(displays: List<BookShelfDisplay>): List<BookshelfBookItem> =
        buildBookshelfBookItems(
            context = requireContext(),
            displays = displays,
            config = displayConfig,
            isUpdating = ::isUpdate,
        )

    private fun applyTagFilter(list: List<BookShelfDisplay>): List<BookShelfDisplay> {
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

    /** 启动"最后更新时间"定时刷新：列表布局下每 30 秒重算一次相对时间 */
    private fun startLastUpdateTimeJob() {
        upLastUpdateTimeJob?.cancel()
        if (!displayConfig.showLastUpdateTime || displayConfig.isGrid) {
            return
        }
        upLastUpdateTimeJob = viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    if (shelfDisplays.isNotEmpty()) {
                        shelfItems = buildItems(shelfDisplays)
                    }
                    delay(30 * 1000)
                }
            }
        }
    }

    fun getBooks(): List<Book> = shelfDisplays.map { it.toMinimalBook() }

    fun gotoTop() {
        if (AppConfig.isEInkMode) {
            immediateScrollToTopTick++
        } else {
            scrollToTopTick++
        }
    }

    fun getBooksCount(): Int = shelfItems.size

    /** 按标签筛选书籍。传 null 表示清除筛选。 */
    fun filterByTag(tag: String?) {
        tagFilter = tag
        upRecyclerData()
    }

    private fun onBookClick(item: BookshelfBookItem) {
        startActivityForBook(item.display.toMinimalBook())
    }

    private fun onBookLongClick(item: BookshelfBookItem) {
        val book = item.display.toMinimalBook()
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
        }
    }

    private fun isUpdate(bookUrl: String): Boolean = activityViewModel.isUpdate(bookUrl)

    override fun onDestroyView() {
        super.onDestroyView()
        shelfItems = emptyList()
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.UP_BOOKSHELF) {
            shelfItems = updateBookshelfBookUpdating(shelfItems, it, ::isUpdate)
        }
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
            displayConfig = BookshelfDisplayConfig.fromAppConfig()
            // 布局、边距、条目内容开关变化后需要重建条目，并重启相对时间刷新任务
            shelfItems = buildItems(shelfDisplays)
            startLastUpdateTimeJob()
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
