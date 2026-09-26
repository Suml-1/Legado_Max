package io.legado.app.ui.main.bookshelf.style1.books

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.main.bookshelf.compose.BookshelfBookItem
import io.legado.app.ui.main.bookshelf.compose.BookshelfDisplayConfig
import io.legado.app.ui.main.bookshelf.compose.buildBookshelfBookItems
import io.legado.app.ui.main.bookshelf.compose.updateBookshelfBookUpdating
import io.legado.app.ui.theme.LegadoTheme
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
 * 分组的书籍列表换成 Compose 渲染（内容见 [BookshelfShelfContent]）；排序、标签筛选、
 * 下拉刷新、空态提示、底部内边距、快速滚动条与"回到顶部"等行为与原实现保持一致。
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
        updateMainBottomPadding((activity as? MainActivity)?.mainContentBottomPadding() ?: 0)
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
            // 内嵌 ComposeView 不带背景，这里只注入主题色板：
            // 不包 LegadoTheme 的话 MaterialTheme 走 M3 默认色，角标/进度条/标题全部偏离应用主题
            LegadoTheme {
                BookshelfShelfContent(
                    shelfItems = shelfItems,
                    displayConfig = displayConfig,
                    bottomPaddingPx = bottomPaddingPx,
                    scrollToTopTick = scrollToTopTick,
                    immediateScrollToTopTick = immediateScrollToTopTick,
                    onScrollBackwardChange = { canScrollBackward = it },
                    onBookClick = ::onBookClick,
                    onBookLongClick = ::onBookLongClick,
                )
            }
        }
        startLastUpdateTimeJob()
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
            displayConfig = displayConfig,
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

    private fun onBookClick(bookItem: BookshelfBookItem) {
        startActivityForBook(bookItem.display.toMinimalBook())
    }

    private fun onBookLongClick(bookItem: BookshelfBookItem) {
        val book = bookItem.display.toMinimalBook()
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
}
