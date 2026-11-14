package io.legado.app.ui.main.explore

import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import io.legado.app.lib.theme.eInkGrayscale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.FragmentExploreBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.explore.compose.ExploreShowActions
import io.legado.app.ui.book.explore.compose.ExploreShowController
import io.legado.app.ui.book.explore.compose.ExploreShowInitArgs
import io.legado.app.ui.book.explore.compose.EXPLORE_LAYOUT_GRID
import io.legado.app.ui.book.explore.compose.EXPLORE_LAYOUT_WATERFALL
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.group.GroupSelectDialog
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.source.edit.BookSourceEditLauncher
import io.legado.app.ui.book.source.manage.BookSourceSort
import io.legado.app.ui.blockrule.BlockRuleConfigDialog
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.explore.compose.ExploreKindsController
import io.legado.app.ui.main.explore.compose.ExploreSourceActions
import io.legado.app.ui.main.explore.compose.ExploreSourceItem
import io.legado.app.ui.main.explore.compose.ExploreSourceList
import io.legado.app.ui.main.explore.compose.ExploreSourceMenuAction
import io.legado.app.ui.main.explore.compose.ModernExploreContent
import io.legado.app.ui.main.explore.compose.toExploreSourceItems
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.applyTint
import io.legado.app.utils.cnCompare
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 发现界面。
 *
 * 结构分工与书架 / 我的页一致：顶栏（搜索框、排序与分组菜单）仍是 View 体系——
 * 主界面是 ViewPager + TitleBar 的 View 宿主，顶栏颜色必须继续走 TopBarConfig 统一体系；
 * 顶栏以下的书源列表整体 Compose 化（内容见 [ExploreSourceList]）。
 *
 * 本类是宿主：持有搜索词、排序、展开状态与底栏内边距，执行平台操作（开 Activity、弹对话框），
 * 把排序 / 过滤后的书源列表交给 Compose 渲染。
 */
class ExploreFragment() :
    VMBaseFragment<ExploreViewModel>(R.layout.fragment_explore),
    MainFragmentInterface,
    ExploreKindQueryDialog.OnKindSelected,
    GroupSelectDialog.CallBack {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    override val viewModel by viewModels<ExploreViewModel>()
    private val binding by viewBinding(FragmentExploreBinding::bind)
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }

    /** Compose 侧直接读这些快照状态，写入即触发重组 */
    private var sourceItems by mutableStateOf<List<BookSourcePart>>(emptyList())

    /** 条目 UI 模型：排序过滤后的结果转一次，列表重组时不再逐项加工 */
    private var displayItems by mutableStateOf<List<ExploreSourceItem>>(emptyList())
    private var expandedSourceUrl by mutableStateOf<String?>(null)
    private var bottomPaddingPx by mutableIntStateOf(0)
    private var scrollToTopTick by mutableIntStateOf(0)

    // 书源分组集合
    private val groups = linkedSetOf<String>()

    // 发现数据流任务
    private var exploreFlowJob: Job? = null

    // 分组菜单
    private var groupsMenu: SubMenu? = null

    // 排序方式
    private var sort = BookSourceSort.Default

    // 是否升序排序
    private var sortAscending = true

    // ── 新版发现 ──

    // 三点菜单里的自定义 item id（PopupMenu 动态构建用）
    private companion object {
        private const val MENU_ID_PAGE = 1
        private const val MENU_ID_ADD_ALL_TO_SHELF = 2
        private const val MENU_ID_SWITCH_LAYOUT = 3
        private const val MENU_ID_SELECT_COLUMN = 4
        private const val MENU_ID_BLOCK_RULE = 5
        private const val MENU_ID_SWITCH_LEGACY = 6
        private const val REQUEST_CODE_ADD_ALL_TO_SHELF = 2001
    }

    /** 是否显示新版发现，随三点菜单切换并持久化 */
    private var modernExplore by mutableStateOf(AppConfig.exploreModernPage)
    private var modernExploreInited = false
    private var modernExploreSourceUrl by mutableStateOf(AppConfig.modernExploreSourceUrl)

    /** 构造期不能读 Fragment 扩展 prefs（未 attach），onFragmentCreated 里回填 */
    private var modernShowBlockProgress by mutableStateOf(false)

    /** 新版发现复用发现列表页的 ViewModel：单源 + 分类 + 三布局 + 双向翻页 */
    private val exploreShowViewModel by viewModels<ExploreShowViewModel>()

    private val modernController by lazy {
        ExploreShowController(
            viewModel = exploreShowViewModel,
            actions = modernExploreActions,
            initialArgs = ExploreShowInitArgs(
                exploreName = getString(R.string.discovery),
                exploreUrl = "",
                // 布局与列数按书源记忆，与旧版发现列表页共用同一组 key
                layoutMode = getPrefInt("${PreferKey.exploreGridMode}_$modernExploreSourceUrl", 0),
                columnGrid = getPrefInt("${PreferKey.exploreShowColumn}_$modernExploreSourceUrl", 2),
                columnWaterfall = getPrefInt("${PreferKey.exploreShowColumnWaterfall}_$modernExploreSourceUrl", 2),
                showCategoryTab = true,
                preloadMode = 0,
                showBlockProgress = modernShowBlockProgress,
            ),
            scope = exploreShowViewModel.viewModelScope,
        )
    }

    private val modernExploreActions by lazy {
        ExploreShowActions(
            onBackClick = {},
            onPagePick = { current, onPicked ->
                NumberPickerDialog(requireActivity())
                    .setTitle(getString(R.string.change_page))
                    .setMaxValue(999)
                    .setMinValue(1)
                    .setValue(current)
                    .show(onPicked)
            },
            onColumnPick = { current, onPicked ->
                NumberPickerDialog(requireActivity())
                    .setTitle(getString(R.string.select_column_count))
                    .setMaxValue(10)
                    .setMinValue(1)
                    .setValue(current)
                    .show(onPicked)
            },
            onAddAllToShelfClick = {
                showDialogFragment(GroupSelectDialog(0, REQUEST_CODE_ADD_ALL_TO_SHELF))
            },
            onShowBlockRuleClick = { showModernBlockRuleConfig() },
            onShowBookInfo = { book ->
                startActivity<BookInfoActivity> {
                    putExtra("name", book.name)
                    putExtra("author", book.author)
                    putExtra("bookUrl", book.bookUrl)
                    putExtra("origin", book.origin)
                }
            },
            addToShelf = { exploreShowViewModel.addToShelf(it) },
            onShowError = { message -> showDialogFragment(TextDialog("ERROR", message)) },
            onShowPhoto = { url, sourceUrl -> showDialogFragment(PhotoDialog(url, sourceUrl)) },
            persistLayoutMode = { putPrefInt("${PreferKey.exploreGridMode}_$modernExploreSourceUrl", it) },
            persistColumnGrid = { putPrefInt("${PreferKey.exploreShowColumn}_$modernExploreSourceUrl", it) },
            persistColumnWaterfall = {
                putPrefInt("${PreferKey.exploreShowColumnWaterfall}_$modernExploreSourceUrl", it)
            },
            persistShowCategoryTab = {},
            persistPreloadMode = {},
            persistShowBlockProgress = {
                putPrefBoolean(PreferKey.blockRuleShowProgress, it)
                modernShowBlockProgress = it
            },
        )
    }

    /**
     * 书源分类区的控制器：JS 求值、infoMap 与内联 WebView 的生命周期都挂在它的作用域上。
     * 这里用 viewLifecycleOwner 的作用域，视图销毁后任务与 WebView 一并结束。
     */
    private val kindsController by lazy {
        ExploreKindsController(
            activity = requireActivity() as? AppCompatActivity,
            scope = viewLifecycleOwner.lifecycleScope,
        )
    }

    private val actions by lazy {
        ExploreSourceActions(
            onToggleExpand = { item ->
                expandedSourceUrl = if (expandedSourceUrl == item.sourceUrl) null else item.sourceUrl
            },
            onMenuAction = ::onSourceMenuAction,
            onOpenExplore = { sourceUrl, title, exploreUrl ->
                openExplore(sourceUrl, title, exploreUrl)
            },
            onShowError = { message -> showDialogFragment(TextDialog("ERROR", message)) },
            onShowPhoto = { url, sourceUrl -> showDialogFragment(PhotoDialog(url, sourceUrl)) },
        )
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        modernShowBlockProgress = getPrefBoolean(PreferKey.blockRuleShowProgress, false)
        setSupportToolbar(binding.titleBar.toolbar)
        // 首次进入时主动取一次底栏高度，之后由 MainActivity 通过接口推送变化
        bottomPaddingPx = (activity as? MainActivity)?.mainContentBottomPadding() ?: 0
        initSearchView()
        initComposeContent()
        initGroupData()
        initBookSourceInvalidation()
        initExploreShowBridge()
        upExploreData(searchView.query?.toString())
        upModernVisibility()
        upMenuVisibility()
    }

    /** 新版发现的数据桥接：ViewModel LiveData → Compose 控制器（与发现列表页一致） */
    private fun initExploreShowBridge() {
        exploreShowViewModel.booksData.observe(this) { modernController.upData(it) }
        exploreShowViewModel.addBooksData.observe(this) { modernController.upDataTop(it) }
        exploreShowViewModel.blockRulesRefreshData.observe(this) {
            modernController.refreshAfterBlock(it)
        }
        exploreShowViewModel.blockedCountData.observe(this) {
            modernController.onBlockedCountChanged(it)
        }
        exploreShowViewModel.exploreKindsData.observe(this) { modernController.onKindsLoaded(it) }
        exploreShowViewModel.errorLiveData.observe(this) { modernController.onFooterError(it) }
        exploreShowViewModel.errorTopLiveData.observe(this) { modernController.onTopFooterError(it) }
        exploreShowViewModel.upAdapterLiveData.observe(this) { modernController.onShelfStateChanged() }
        exploreShowViewModel.pageLiveData.observe(this) { modernController.onPageChanged(it) }
        exploreShowViewModel.addAllToShelfResult.observe(this) { count ->
            if (count == 0) {
                toastOnUi(R.string.all_books_in_shelf)
            } else {
                toastOnUi(getString(R.string.add_books_success, count))
            }
        }
    }

    private fun initComposeContent() {
        binding.composeSourceList.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeSourceList.setContent {
            LegadoTheme {
                if (modernExplore) {
                    ModernExploreContent(
                        controller = modernController,
                        actions = modernExploreActions,
                        kindsController = kindsController,
                        sources = sourceItems,
                        selectedSourceUrl = modernExploreSourceUrl,
                        onSelectSource = ::selectModernExploreSource,
                        onSwitchLegacy = { applyModernExplore(false) },
                        showBlockProgress = modernShowBlockProgress,
                        bottomPaddingPx = bottomPaddingPx,
                        onRefreshSource = ::refreshModernExploreSource,
                        onLogin = ::loginModernExploreSource,
                        onEditSource = { currentModernSourceUrl()?.let(::editSource) },
                        onSearchSource = { currentModernSource()?.let(::searchBook) },
                    )
                } else {
                    ExploreSourceList(
                        sourceItems = displayItems,
                        expandedSourceUrl = expandedSourceUrl,
                        bottomPaddingPx = bottomPaddingPx,
                        scrollToTopTick = scrollToTopTick,
                        controller = kindsController,
                        actions = actions,
                    )
                }
            }
        }
    }

    /**
     * 创建选项菜单
     * 初始化菜单布局，设置排序菜单项状态，并更新分组菜单
     */
    override fun onCompatCreateOptionsMenu(menu: Menu) {
        super.onCompatCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main_explore, menu)
        groupsMenu = menu.findItem(R.id.menu_group)?.subMenu
        val sortSubMenu = menu.findItem(R.id.action_sort).subMenu
        sortSubMenu?.findItem(R.id.menu_sort_desc)?.isChecked = !sortAscending
        sortSubMenu?.setGroupCheckable(R.id.menu_group_sort, true, true)
        upGroupsMenu()
    }

    /**
     * 准备选项菜单
     * 更新排序菜单项的选中状态
     */
    override fun onPrepareOptionsMenu(menu: Menu) {
        // Fragment 菜单不走 Activity 菜单体系，此方法不会被执行（排序勾选在
        // onCompatCreateOptionsMenu 里初始化）；保留覆盖仅为兼容签名
        super.onPrepareOptionsMenu(menu)
    }

    /**
     * 按新旧版模式调整顶栏菜单可见性（新版模式下排序/分组/搜索均无意义）。
     * Fragment 菜单不走 Activity 的 onPrepareOptionsMenu 体系，需直接改 Toolbar 菜单。
     */
    private fun upMenuVisibility() {
        supportToolbar?.menu?.let { m ->
            m.findItem(R.id.action_sort)?.isVisible = !modernExplore
            m.findItem(R.id.menu_group)?.isVisible = !modernExplore
            m.findItem(R.id.menu_select_column)?.isVisible = false
            // 新版模式下 TitleBar 整体隐藏（源切换/菜单移到内容区头部行），
            // 新旧版切换入口由头部三点菜单承担
            m.findItem(R.id.menu_more)?.isVisible = !modernExplore
        }
    }

    /**
     * 模式切换后同步 TitleBar 与菜单：新版模式下 TitleBar 隐藏，
     * 源切换行 / 三点菜单由内容区头部行承担——头部行自占
     * 状态栏 + 顶栏高度（statusBarsPadding + topBarHeight），
     * 位置与高度与旧版 TitleBar 完全一致。
     */
    private fun upModernVisibility() {
        binding.titleBar.isGone = modernExplore
        if (!modernExplore) {
            searchView.isVisible = true
        }
        upMenuVisibility()
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.screen_find)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                upExploreData(newText)
                return false
            }
        })
    }

    override fun updateMainBottomPadding(bottomPadding: Int) {
        bottomPaddingPx = bottomPadding
    }

    private fun initGroupData() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookSourceDao.flowExploreGroups()
                .flowWithLifecycleAndDatabaseChange(
                    viewLifecycleOwner.lifecycle,
                    Lifecycle.State.RESUMED,
                    AppDatabase.BOOK_SOURCE_TABLE_NAME,
                )
                .conflate()
                .distinctUntilChanged()
                .collect {
                    groups.clear()
                    groups.addAll(it)
                    upGroupsMenu()
                    delay(500)
                }
        }
    }

    /**
     * 订阅书源表的失效事件，真写库时才作废控制器缓存的书源对象（否则改了发现配置只会读到旧对象）。
     *
     * 不能挂在 `upExploreData` 的数据流上：那条流每次回到本页都会重发一次数据
     * （[flowWithLifecycleAndDatabaseChange] 的初始信号会随生命周期重启再发），
     * 在那里作废会递增重建信号、让已展开书源重新求值——`@js:` 书源（如"起点中文网(按钮筛选)"）
     * 于是在"进一次分类再返回"这种与数据无关的往返里重跑分类脚本，重复弹提示 / 重复发请求。
     *
     * 这里用 `emitInitialState = false`：订阅瞬间不补发，只收真实写入；并且**不挂**
     * `repeatOnLifecycle`——编辑书源期间本页处于 STOPPED，若按可见性收起订阅，
     * 保存产生的那次写入会落在订阅之外（Room 的失效流只对比订阅期间的版本号，不补发历史变更），
     * 缓存就再也追不上新数据了。订阅本身只跟踪版本号，不查库，常驻没有额外开销。
     */
    private fun initBookSourceInvalidation() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.invalidationTracker
                .createFlow(AppDatabase.BOOK_SOURCE_TABLE_NAME, emitInitialState = false)
                .collect { kindsController.invalidateBookSources() }
        }
    }

    private fun upExploreData(searchKey: String? = null) {
        exploreFlowJob?.cancel()
        exploreFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            when {
                searchKey.isNullOrBlank() -> {
                    appDb.bookSourceDao.flowExplore()
                }

                searchKey.startsWith("group:") -> {
                    val key = searchKey.substringAfter("group:")
                    appDb.bookSourceDao.flowGroupExplore(key)
                }

                else -> {
                    appDb.bookSourceDao.flowExplore(searchKey)
                }
            }.map { data ->
                // 根据排序方式和排序方向对数据进行排序
                if (sortAscending) {
                    when (sort) {
                        BookSourceSort.Name -> data.sortedWith { o1, o2 ->
                            o1.bookSourceName.cnCompare(o2.bookSourceName)
                        }

                        BookSourceSort.Url -> data.sortedBy { it.bookSourceUrl }
                        BookSourceSort.Update -> data.sortedByDescending { it.lastUpdateTime }
                        BookSourceSort.Respond -> data.sortedBy { it.respondTime }
                        else -> data
                    }
                } else {
                    when (sort) {
                        BookSourceSort.Name -> data.sortedWith { o1, o2 ->
                            o2.bookSourceName.cnCompare(o1.bookSourceName)
                        }

                        BookSourceSort.Url -> data.sortedByDescending { it.bookSourceUrl }
                        BookSourceSort.Update -> data.sortedBy { it.lastUpdateTime }
                        BookSourceSort.Respond -> data.sortedByDescending { it.respondTime }
                        else -> data.reversed()
                    }
                }
            }.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_SOURCE_TABLE_NAME,
            ).catch {
                AppLog.put("发现界面更新数据出错", it)
            }.conflate().flowOn(IO).collect { data ->
                sourceItems = data
                displayItems = data.toExploreSourceItems()
                // 搜索中不显示空态：搜索框里的字还没清掉，列表空着是正常的
                binding.tvEmptyMsg.isGone =
                    data.isNotEmpty() ||
                    searchView.query.isNotEmpty() ||
                    modernExplore
                if (modernExplore && !modernExploreInited) {
                    initModernExploreData()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        kindsController.resumeWebViews()
        // 旧版列表页可能改过当前源的布局/列数（同一组按源 key），回来时同步进控制器
        if (modernExplore) {
            modernController.applyPerSourceLayout(
                layoutMode = getPrefInt("${PreferKey.exploreGridMode}_$modernExploreSourceUrl", 0),
                columnGrid = getPrefInt("${PreferKey.exploreShowColumn}_$modernExploreSourceUrl", 2),
                columnWaterfall = getPrefInt("${PreferKey.exploreShowColumnWaterfall}_$modernExploreSourceUrl", 2),
            )
        }
    }

    override fun onPause() {
        searchView.clearFocus()
        // WebView 只暂停不释放，回来时内容还在；infoMap 里标记为待保存的先落盘
        kindsController.pauseWebViews()
        super.onPause()
    }

    override fun onDestroyView() {
        kindsController.releaseAllWebViews()
        kindsController.saveInfoMaps()
        super.onDestroyView()
    }

    private fun upGroupsMenu() = groupsMenu?.transaction { subMenu ->
        subMenu.removeGroup(R.id.menu_group_text)
        groups.forEach {
            // 分组名是用户自定义的，可能带表情符号；这个下拉是 AppCompat 内部创建的窗口，
            // 挂不了整层灰阶，只能把标题本身按灰阶绘制（见 CharSequence.eInkGrayscale）
            subMenu.add(R.id.menu_group_text, Menu.NONE, Menu.NONE, it.eInkGrayscale())
        }
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_more -> showExploreMoreMenu(binding.titleBar)
            R.id.menu_select_column -> modernExploreActions.onColumnPick(
                modernController.effectiveColumnCount(),
            ) { modernController.selectColumnCount(it) }

            R.id.menu_sort_desc -> {
                sortAscending = !sortAscending
                item.isChecked = !sortAscending
                upExploreData(searchView.query?.toString())
            }

            R.id.menu_sort_manual -> {
                item.isChecked = true
                sort = BookSourceSort.Default
                upExploreData(searchView.query?.toString())
            }

            R.id.menu_sort_name -> {
                item.isChecked = true
                sort = BookSourceSort.Name
                upExploreData(searchView.query?.toString())
            }

            R.id.menu_sort_url -> {
                item.isChecked = true
                sort = BookSourceSort.Url
                upExploreData(searchView.query?.toString())
            }

            R.id.menu_sort_time -> {
                item.isChecked = true
                sort = BookSourceSort.Update
                upExploreData(searchView.query?.toString())
            }

            R.id.menu_sort_respondTime -> {
                item.isChecked = true
                sort = BookSourceSort.Respond
                upExploreData(searchView.query?.toString())
            }
        }
        if (item.groupId == R.id.menu_group_text) {
            searchView.setQuery("group:${item.title}", true)
        }
    }

    /** 再次点击"发现"标签：先收起展开项，没有展开项时回到顶部 */
    fun compressExplore() {
        if (expandedSourceUrl != null) {
            expandedSourceUrl = null
        } else {
            scrollToTopTick += 1
        }
    }

    // ── 书源条目动作（由 Compose 侧回调） ──

    private fun onSourceMenuAction(item: ExploreSourceItem, action: ExploreSourceMenuAction) {
        // 条目模型只带 url，置顶 / 删除 / 搜索要拿完整的 BookSourcePart，从当前列表里反查
        val source = sourceItems.firstOrNull { it.bookSourceUrl == item.sourceUrl }
        when (action) {
            ExploreSourceMenuAction.Edit -> editSource(item.sourceUrl)
            ExploreSourceMenuAction.ToTop -> source?.let(::toTop)
            ExploreSourceMenuAction.Query -> source?.let(::showKindQueryDialog)
            ExploreSourceMenuAction.Login -> startActivity<SourceLoginActivity> {
                putExtra("type", "bookSource")
                putExtra("key", item.sourceUrl)
            }

            ExploreSourceMenuAction.Search -> source?.let(::searchBook)
            // 已展开行的"刷新"由 Compose 侧就地转成重新求值，不会走到这里
            ExploreSourceMenuAction.Refresh -> Unit
            ExploreSourceMenuAction.Delete -> source?.let(::deleteSource)
        }
    }

    override fun openExplore(sourceUrl: String, title: String, exploreUrl: String?) {
        if (exploreUrl.isNullOrBlank()) return
        startActivity<ExploreShowActivity> {
            putExtra("exploreName", title)
            putExtra("sourceUrl", sourceUrl)
            putExtra("exploreUrl", exploreUrl)
        }
    }

    private fun editSource(sourceUrl: String) {
        lifecycleScope.launch {
            BookSourceEditLauncher.open(requireContext(), sourceUrl)
        }
    }

    private fun toTop(source: BookSourcePart) {
        viewModel.topSource(source)
    }

    private fun deleteSource(source: BookSourcePart) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + source.bookSourceName)
            noButton()
            yesButton {
                viewModel.deleteSource(source)
            }
        }
    }

    private fun searchBook(bookSource: BookSourcePart) {
        SearchActivity.start(requireContext(), bookSource)
    }

    /**
     * 显示查询对话框
     */
    private fun showKindQueryDialog(source: BookSourcePart) {
        showDialogFragment(ExploreKindQueryDialog(source.bookSourceUrl, source.bookSourceName))
    }

    // ── 新版发现 ──

    /** 首次进入新版模式时选中上次的书源并加载数据；源列表未就绪时不置位，等数据流再触发 */
    private fun initModernExploreData() {
        val source = sourceItems.firstOrNull { it.bookSourceUrl == modernExploreSourceUrl }
            ?: sourceItems.firstOrNull()
            ?: return
        modernExploreInited = true
        selectModernExploreSource(source)
    }

    /** 切换新版发现的书源：复位列表状态后经 ViewModel 重新加载分类与首屏 */
    private fun selectModernExploreSource(source: BookSourcePart) {
        modernExploreSourceUrl = source.bookSourceUrl
        AppConfig.modernExploreSourceUrl = source.bookSourceUrl
        modernController.resetForNewSource()
        // 布局与列数按源记忆：切源后同步为新源的值
        modernController.applyPerSourceLayout(
            layoutMode = getPrefInt("${PreferKey.exploreGridMode}_${source.bookSourceUrl}", 0),
            columnGrid = getPrefInt("${PreferKey.exploreShowColumn}_${source.bookSourceUrl}", 2),
            columnWaterfall = getPrefInt("${PreferKey.exploreShowColumnWaterfall}_${source.bookSourceUrl}", 2),
        )
        exploreShowViewModel.initData(source.bookSourceUrl, null)
    }

    /**
     * 新版发现三点菜单的「刷新」：清掉书源的发现分类缓存 → 分类区按新分类重建 → 复位并重拉当前分类。
     *
     * 顺序不能调换：`exploreKinds()` 有进程内 + ACache 两级缓存，且 ViewModel 与分类区
     * 共用同一份；先发重建信号（分类区会立刻求值）再清缓存，分类区拿到的仍是旧分类。
     * 内容区的复位重拉交给 [ExploreShowController.refreshCurrent]：
     * 只调 ViewModel 重拉而不复位，回填的数据会被 upData 的增量合并当成"无新增"丢掉。
     */
    private fun refreshModernExploreSource() {
        val sourceUrl = modernExploreSourceUrl
        viewLifecycleOwner.lifecycleScope.launch {
            if (!sourceUrl.isNullOrBlank()) {
                kindsController.clearKindsCache(sourceUrl)
                kindsController.requestRefresh(sourceUrl)
            }
            modernController.refreshCurrent()
        }
    }

    /** 新版发现三点菜单的「登录」：打开当前书源的登录页（与书源条目的长按菜单同一入口） */
    private fun loginModernExploreSource() {
        val sourceUrl = modernExploreSourceUrl
        if (sourceUrl.isNullOrBlank()) return
        startActivity<SourceLoginActivity> {
            putExtra("type", "bookSource")
            putExtra("key", sourceUrl)
        }
    }

    /** 新版发现当前选中源的书源地址；未选中或为空时返回 null */
    private fun currentModernSourceUrl(): String? = modernExploreSourceUrl?.takeIf { it.isNotBlank() }

    /**
     * 新版发现当前选中源的完整书源。
     *
     * 列表条目模型只带 url，而编辑书源 / 搜索书籍需要完整的 [BookSourcePart]，
     * 因此从当前源列表里按 url 反查（与旧版长按菜单 `onSourceMenuAction` 的做法一致）。
     */
    private fun currentModernSource(): BookSourcePart? = sourceItems.firstOrNull { it.bookSourceUrl == modernExploreSourceUrl }

    /** 三点菜单：旧版只有切换入口；新版带发现列表页的全部菜单项 */
    private fun showExploreMoreMenu(anchor: View) {
        // 锚可能是全屏的 ComposeView：TOP|END 让菜单贴屏幕右上，而不是弹到锚底（屏幕外）
        val popup = PopupMenu(requireContext(), anchor, Gravity.END or Gravity.TOP)
        val menu = popup.menu
        if (!modernExplore) {
            menu.add(Menu.NONE, MENU_ID_SWITCH_LEGACY, 0, R.string.switch_to_new_explore)
            popup.setOnMenuItemClickListener {
                applyModernExplore(true)
                true
            }
        } else {
            menu.add(Menu.NONE, MENU_ID_PAGE, 0, getString(R.string.menu_page, modernController.currentPage))
            menu.add(Menu.NONE, MENU_ID_ADD_ALL_TO_SHELF, 1, R.string.add_all_to_shelf)
            menu.add(Menu.NONE, MENU_ID_SWITCH_LAYOUT, 2, switchLayoutTitle())
            menu.add(Menu.NONE, MENU_ID_SELECT_COLUMN, 3, R.string.select_column_count)
            menu.add(Menu.NONE, MENU_ID_BLOCK_RULE, 4, R.string.explore_block_rule)
            menu.add(Menu.NONE, MENU_ID_SWITCH_LEGACY, 5, R.string.switch_to_old_explore)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_ID_PAGE -> modernExploreActions.onPagePick(modernController.currentPage) {
                        modernController.skipPageTo(it)
                    }

                    MENU_ID_ADD_ALL_TO_SHELF -> modernExploreActions.onAddAllToShelfClick()
                    MENU_ID_SWITCH_LAYOUT -> {
                        modernController.switchLayout()
                        upMenuVisibility()
                    }

                    MENU_ID_SELECT_COLUMN -> modernExploreActions.onColumnPick(
                        modernController.effectiveColumnCount(),
                    ) { modernController.selectColumnCount(it) }

                    MENU_ID_BLOCK_RULE -> modernExploreActions.onShowBlockRuleClick()
                    else -> applyModernExplore(false)
                }
                true
            }
        }
        popup.show()
    }

    private fun switchLayoutTitle(): String {
        val modeName = when (modernController.layoutMode) {
            EXPLORE_LAYOUT_GRID -> getString(R.string.switch_layout_grid)
            EXPLORE_LAYOUT_WATERFALL -> getString(R.string.switch_layout_waterfall)
            else -> getString(R.string.switch_layout_list)
        }
        return getString(R.string.switch_layout_current, modeName)
    }

    private fun applyModernExplore(value: Boolean) {
        modernExplore = value
        AppConfig.exploreModernPage = value
        upModernVisibility()
        if (value) {
            // 切回新版时按当前源重读布局记忆：旧版列表页期间的修改要同步进来
            modernController.applyPerSourceLayout(
                layoutMode = getPrefInt("${PreferKey.exploreGridMode}_$modernExploreSourceUrl", 0),
                columnGrid = getPrefInt("${PreferKey.exploreShowColumn}_$modernExploreSourceUrl", 2),
                columnWaterfall = getPrefInt("${PreferKey.exploreShowColumnWaterfall}_$modernExploreSourceUrl", 2),
            )
            if (!modernExploreInited) {
                // 数据流处于 RESUMED 不会重发，就地初始化
                initModernExploreData()
            }
        }
    }

    /** 新版发现的屏蔽规则配置（复用发现列表页的做法） */
    private fun showModernBlockRuleConfig() {
        val dialog = BlockRuleConfigDialog()
        dialog.sourceUrl = exploreShowViewModel.currentSourceUrl
        dialog.allBooks = exploreShowViewModel.allBooksList
        dialog.onRulesChanged = {
            exploreShowViewModel.applyBlockRules(exploreShowViewModel.currentSourceUrl)
        }
        dialog.onShowProgressChanged = {
            modernExploreActions.persistShowBlockProgress(it)
            modernController.updateShowBlockProgress(it)
        }
        dialog.show(parentFragmentManager, "exploreModernBlockRuleConfig")
    }

    override fun upGroup(requestCode: Int, groupId: Long) {
        if (requestCode == REQUEST_CODE_ADD_ALL_TO_SHELF) {
            toastOnUi(getString(R.string.adding_books, exploreShowViewModel.booksCount))
            exploreShowViewModel.addAllToShelf(groupId)
        }
    }
}
