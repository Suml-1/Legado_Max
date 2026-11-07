package io.legado.app.ui.main.rss

import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.databinding.FragmentRssBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.eInkGrayscale
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.blockrule.BlockRuleConfigDialog
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.rss.compose.ModernRssContent
import io.legado.app.ui.main.rss.compose.ModernRssController
import io.legado.app.ui.main.rss.compose.RssMoreMenuItem
import io.legado.app.ui.main.rss.compose.RssSourceActions
import io.legado.app.ui.main.rss.compose.RssSourceGrid
import io.legado.app.ui.main.rss.compose.RssSourceItem
import io.legado.app.ui.main.rss.compose.RssSourceMenuAction
import io.legado.app.ui.main.rss.compose.toRssSourceItems
import io.legado.app.ui.rss.article.ReadRecordDialog
import io.legado.app.ui.rss.article.RssArticlesViewModel
import io.legado.app.ui.rss.article.RssSortActivity
import io.legado.app.ui.rss.article.RssSortViewModel
import io.legado.app.ui.rss.favorites.RssFavoritesActivity
import io.legado.app.ui.rss.read.ReadRss
import io.legado.app.ui.rss.read.ReadRssActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.rss.source.manage.RssSourceSort
import io.legado.app.ui.rss.subscription.RuleSubActivity
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.applyTint
import io.legado.app.utils.cnCompare
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.openUrl
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import splitties.init.appCtx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 订阅界面。
 *
 * 结构分工与书架 / 发现 / 我的页一致：顶栏（搜索框、分组菜单）仍是 View 体系——
 * 主界面是 ViewPager + TitleBar 的 View 宿主，顶栏颜色必须继续走 TopBarConfig 统一体系；
 * 顶栏以下内容由 Compose 渲染：旧版为订阅源网格（[RssSourceGrid]），
 * 新版为"源选择行 + 分类标签栏 + 文章列表"（[ModernRssContent]，参考 Legado_R 新版订阅），
 * 通过 TitleBar 三点菜单切换。
 */
class RssFragment() :
    VMBaseFragment<RssViewModel>(R.layout.fragment_rss),
    MainFragmentInterface,
    VariableDialog.Callback {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    private val binding by viewBinding(FragmentRssBinding::bind)
    override val viewModel by viewModels<RssViewModel>()
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }

    /** Compose 侧直接读这些快照状态，写入即触发重组 */
    private var sourceItems by mutableStateOf<List<RssSource>>(emptyList())

    /** 条目 UI 模型：过滤排序后的结果转一次，网格重组时不再逐项加工 */
    private var displayItems by mutableStateOf<List<RssSourceItem>>(emptyList())
    private var bottomPaddingPx by mutableIntStateOf(0)

    private var groupsFlowJob: Job? = null
    private var rssFlowJob: Job? = null
    private val groups = linkedSetOf<String>()
    private var groupsMenu: SubMenu? = null

    private val actions by lazy {
        RssSourceActions(
            onOpen = ::openRss,
            onMenuAction = ::onSourceMenuAction,
            onOpenRuleSub = { startActivity<RuleSubActivity>() },
        )
    }

    /**
     * 订阅源排序方式，从 SharedPreferences 读取，与订阅源管理页面同步
     */
    private val sort: RssSourceSort
        get() = RssSourceSort.entries[appCtx.getPrefInt(PreferKey.rssSourceSort, 0)]

    /**
     * 排序方向，从 SharedPreferences 读取
     */
    private val sortAscending: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.rssSourceSortAscending, true)

    // ── 新版订阅 ──

    private companion object {
        private const val MENU_ID_PAGE = 1
        private const val MENU_ID_LOGIN = 2
        private const val MENU_ID_REFRESH_SORT = 3
        private const val MENU_ID_SET_VARIABLE = 4
        private const val MENU_ID_EDIT_SOURCE = 5
        private const val MENU_ID_SWITCH_LAYOUT = 6
        private const val MENU_ID_BLOCK_RULE = 7
        private const val MENU_ID_READ_RECORD = 8
        private const val MENU_ID_CLEAR = 9
        private const val MENU_ID_SWITCH_LEGACY = 10
        private const val MENU_ID_SWITCH_MODERN = 11
    }

    /** 是否显示新版订阅，随三点菜单切换并持久化 */
    private var modernRss by mutableStateOf(AppConfig.rssModernPage)
    private var modernRssInited = false
    private var modernRssSourceUrl by mutableStateOf(AppConfig.modernRssSourceUrl)

    /** 构造期不能读 Fragment 扩展 prefs（未 attach），onFragmentCreated 里回填 */
    private var modernShowBlockProgress by mutableStateOf(false)

    /** 新版订阅的源级操作（清除文章缓存 / 清除分类缓存） */
    private val rssSortViewModel by viewModels<RssSortViewModel>()

    /** 新版订阅的文章加载（复用分类页 ViewModel） */
    private val rssArticlesViewModel by viewModels<RssArticlesViewModel>()

    private val modernController by lazy {
        ModernRssController(
            articlesViewModel = rssArticlesViewModel,
            sortViewModel = rssSortViewModel,
            // 用 VM 作用域：视图在 offscreen 分步放开期间可能销毁重建，
            // viewLifecycleOwner 的 scope 会随视图死亡导致控制器协程静默失效
            scope = rssSortViewModel.viewModelScope,
        )
    }

    private val editSourceResult = registerForActivityResult(
        StartActivityContract(RssSourceEditActivity::class.java),
    ) {
        if (it.resultCode == androidx.appcompat.app.AppCompatActivity.RESULT_OK) {
            // 编辑源后重载当前源（分类缓存可能已变）
            modernController.source?.let { source -> selectModernRssSource(source) }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        modernShowBlockProgress = getPrefBoolean(PreferKey.blockRuleShowProgress, false)
        setSupportToolbar(binding.titleBar.toolbar)
        // 首次进入时主动取一次底栏高度，之后由 MainActivity 通过接口推送变化
        bottomPaddingPx = (activity as? MainActivity)?.mainContentBottomPadding() ?: 0
        initSearchView()
        initComposeContent()
        initGroupData()
        initModernRssBridge()
        upRssFlowJob()
        upModernVisibility()
    }

    /** 新版订阅的数据桥接：文章加载 ViewModel → Compose 控制器 */
    private fun initModernRssBridge() {
        rssArticlesViewModel.pageLiveData.observe(this) { modernController.onPageChanged(it) }
        rssArticlesViewModel.loadFinallyLiveData.observe(this) { modernController.onFinally(it) }
        rssArticlesViewModel.loadErrorLiveData.observe(this) { modernController.onError(it) }
    }

    private fun initComposeContent() {
        binding.composeSourceGrid.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeSourceGrid.setContent {
            LegadoTheme {
                if (modernRss) {
                    ModernRssContent(
                        controller = modernController,
                        sources = sourceItems,
                        showBlockProgress = modernShowBlockProgress,
                        onReadArticle = ::readModernArticle,
                        onSelectSource = ::selectModernRssSource,
                        onOpenInWeb = ::openModernRssInWeb,
                        onShowBlockRule = { showModernBlockRuleConfig() },
                        moreMenuItems = ::buildModernRssMenuItems,
                        onMenuItem = ::handleModernRssMenu,
                        bottomPaddingPx = bottomPaddingPx,
                    )
                } else {
                    RssSourceGrid(
                        sourceItems = displayItems,
                        bottomPaddingPx = bottomPaddingPx,
                        actions = actions,
                    )
                }
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_rss, menu)
        groupsMenu = menu.findItem(R.id.menu_group)?.subMenu
        upGroupsMenu()
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_more -> showRssMoreMenu(binding.titleBar)
            R.id.menu_read_record -> showDialogFragment<ReadRecordDialog>()
            R.id.menu_rss_star -> startActivity<RssFavoritesActivity>()
            else -> if (item.groupId == R.id.menu_group_text) {
                searchView.setQuery("group:${item.title}", true)
            }
        }
    }

    /**
     * 按新旧版模式调整顶栏菜单可见性（新版模式下分组无意义）。
     * Fragment 菜单不走 Activity 的 onPrepareOptionsMenu 体系，需直接改 Toolbar 菜单。
     */
    /** 按新旧版模式调整顶栏菜单可见性（新版下分组/三点均无意义：入口移到内容区头部行） */
    private fun upMenuVisibility() {
        supportToolbar?.menu?.let { m ->
            m.findItem(R.id.menu_group)?.isVisible = !modernRss
            m.findItem(R.id.menu_more)?.isVisible = !modernRss
        }
    }

    /**
     * 模式切换后同步 TitleBar 与菜单：新版模式下 TitleBar 隐藏，
     * 源切换行 / 三点菜单由内容区头部行承担——头部行自占
     * 状态栏 + 顶栏高度（statusBarsPadding + topBarHeight），
     * 位置与高度与旧版 TitleBar 完全一致。
     */
    private fun upModernVisibility() {
        binding.titleBar.isGone = modernRss
        if (!modernRss) {
            binding.titleBar.title = getString(R.string.rss)
            searchView.isVisible = true
        }
        upMenuVisibility()
    }

    override fun onPause() {
        super.onPause()
        searchView.clearFocus()
    }

    private fun upGroupsMenu() = groupsMenu?.transaction { subMenu ->
        subMenu.removeGroup(R.id.menu_group_text)
        groups.forEach {
            // 分组名是用户自定义的（可能带表情符号），且这个下拉是 AppCompat 自己开的窗口，
            // 挂不了整层灰阶，只能把标题本身按灰阶绘制（见 CharSequence.eInkGrayscale）
            subMenu.add(R.id.menu_group_text, Menu.NONE, Menu.NONE, it.eInkGrayscale())
        }
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.rss)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                upRssFlowJob(newText)
                return false
            }
        })
    }

    override fun updateMainBottomPadding(bottomPadding: Int) {
        bottomPaddingPx = bottomPadding
    }

    private fun initGroupData() {
        groupsFlowJob?.cancel()
        groupsFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.rssSourceDao.flowEnabledGroups().catch {
                AppLog.put("订阅界面获取分组数据失败\n${it.localizedMessage}", it)
            }.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.RSS_SOURCE_TABLE_NAME,
            ).conflate().collect {
                groups.clear()
                groups.addAll(it)
                upGroupsMenu()
            }
        }
    }

    private fun upRssFlowJob(searchKey: String? = null) {
        rssFlowJob?.cancel()
        rssFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            when {
                searchKey.isNullOrEmpty() -> appDb.rssSourceDao.flowEnabled()
                searchKey.startsWith("group:") -> {
                    val key = searchKey.substringAfter("group:")
                    appDb.rssSourceDao.flowEnabledByGroup(key)
                }

                else -> appDb.rssSourceDao.flowEnabled(searchKey)
            }.map { data ->
                // 应用排序逻辑，与订阅源管理页面保持一致
                when (sort) {
                    RssSourceSort.Name -> {
                        if (sortAscending) {
                            data.sortedWith { o1, o2 ->
                                o1.sourceName.cnCompare(o2.sourceName)
                            }
                        } else {
                            data.sortedWith { o1, o2 ->
                                o2.sourceName.cnCompare(o1.sourceName)
                            }
                        }
                    }
                    RssSourceSort.Url -> {
                        if (sortAscending) {
                            data.sortedBy { it.sourceUrl }
                        } else {
                            data.sortedByDescending { it.sourceUrl }
                        }
                    }
                    RssSourceSort.Update -> {
                        if (sortAscending) {
                            data.sortedBy { it.lastUpdateTime }
                        } else {
                            data.sortedByDescending { it.lastUpdateTime }
                        }
                    }
                    RssSourceSort.Enable -> {
                        if (sortAscending) {
                            data.sortedWith { o1, o2 ->
                                var sortNum = -o1.enabled.compareTo(o2.enabled)
                                if (sortNum == 0) sortNum = o1.sourceName.cnCompare(o2.sourceName)
                                sortNum
                            }
                        } else {
                            data.sortedWith { o1, o2 ->
                                var sortNum = o1.enabled.compareTo(o2.enabled)
                                if (sortNum == 0) sortNum = o1.sourceName.cnCompare(o2.sourceName)
                                sortNum
                            }
                        }
                    }
                    else -> data // 手动排序时，数据库已按 customOrder 排序
                }
            }.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.RSS_SOURCE_TABLE_NAME,
            ).catch {
                AppLog.put("订阅界面更新数据出错", it)
            }.conflate().flowOn(IO).collect {
                sourceItems = it
                displayItems = it.toRssSourceItems()
                if (modernRss && !modernRssInited) {
                    initModernRssData()
                }
            }
        }
    }

    // ── 订阅源条目动作（由 Compose 侧回调） ──

    private fun onSourceMenuAction(item: RssSourceItem, action: RssSourceMenuAction) {
        // 条目模型只带 url，置顶 / 删除 / 禁用要拿完整的 RssSource，从当前列表里反查
        val source = sourceItems.firstOrNull { it.sourceUrl == item.sourceUrl } ?: return
        when (action) {
            RssSourceMenuAction.Edit -> edit(source)
            RssSourceMenuAction.ToTop -> viewModel.topSource(source)
            RssSourceMenuAction.Login -> login(source)
            RssSourceMenuAction.Disable -> viewModel.disable(source)
            RssSourceMenuAction.Delete -> del(source)
        }
    }

    private fun openRss(item: RssSourceItem) {
        val rssSource = sourceItems.firstOrNull { it.sourceUrl == item.sourceUrl } ?: return
        if (rssSource.singleUrl) {
            viewModel.getSingleUrl(rssSource) { url ->
                if (url.startsWith("http", true)) {
                    ReadRssActivity.start(
                        requireContext(),
                        true,
                        rssSource.sourceUrl,
                        rssSource.sourceName,
                        url,
                    )
                } else {
                    context?.openUrl(url)
                }
            }
        } else {
            viewModel.launchRssWithHtml(rssSource, {
                startActivity<RssSortActivity> {
                    putExtra("sourceUrl", rssSource.sourceUrl)
                }
            }) { html ->
                ReadRssActivity.start(
                    requireContext(),
                    true,
                    rssSource.sourceUrl,
                    rssSource.sourceName,
                    startHtml = html,
                )
            }
        }
    }

    private fun login(rssSource: RssSource) {
        startActivity<SourceLoginActivity> {
            putExtra("type", "rssSource")
            putExtra("key", rssSource.sourceUrl)
        }
    }

    private fun edit(rssSource: RssSource) {
        startActivity<RssSourceEditActivity> {
            putExtra("sourceUrl", rssSource.sourceUrl)
        }
    }

    private fun del(rssSource: RssSource) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + rssSource.sourceName)
            noButton()
            yesButton {
                viewModel.del(rssSource)
            }
        }
    }

    // ── 新版订阅 ──

    /** 三点菜单：旧版只有切换入口；新版保留分类页的全部菜单项 */
    private fun showRssMoreMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor, Gravity.END or Gravity.TOP)
        val menu = popup.menu
        if (!modernRss) {
            menu.add(Menu.NONE, MENU_ID_SWITCH_MODERN, 0, R.string.switch_to_new_rss)
            popup.setOnMenuItemClickListener {
                applyModernRss(true)
                true
            }
        } else {
            val source = modernController.source
            val pageItem = menu.add(
                Menu.NONE,
                MENU_ID_PAGE,
                0,
                getString(R.string.menu_page, modernController.currentPage),
            )
            pageItem.isVisible = !source?.ruleNextPage.isNullOrEmpty()
            val loginItem = menu.add(Menu.NONE, MENU_ID_LOGIN, 1, R.string.login)
            loginItem.isVisible = !source?.loginUrl.isNullOrBlank()
            menu.add(Menu.NONE, MENU_ID_REFRESH_SORT, 2, R.string.refresh_sort)
            menu.add(Menu.NONE, MENU_ID_SET_VARIABLE, 3, R.string.set_source_variable)
            menu.add(Menu.NONE, MENU_ID_EDIT_SOURCE, 4, R.string.edit_source)
            menu.add(Menu.NONE, MENU_ID_SWITCH_LAYOUT, 5, R.string.switchLayout)
            menu.add(Menu.NONE, MENU_ID_BLOCK_RULE, 6, R.string.explore_block_rule)
            menu.add(Menu.NONE, MENU_ID_READ_RECORD, 7, R.string.read_record)
            menu.add(Menu.NONE, MENU_ID_CLEAR, 8, R.string.clear)
            menu.add(Menu.NONE, MENU_ID_SWITCH_LEGACY, 9, R.string.switch_to_old_rss)
            popup.setOnMenuItemClickListener { item ->
                handleModernRssMenu(item.itemId)
                true
            }
        }
        popup.show()
    }

    /** 新版订阅头部三点菜单项（弹出时求值，携带动态可见性与页码标题） */
    private fun buildModernRssMenuItems(): List<RssMoreMenuItem> {
        val source = modernController.source
        return listOf(
            RssMoreMenuItem(
                MENU_ID_PAGE,
                getString(R.string.menu_page, modernController.currentPage),
                visible = !source?.ruleNextPage.isNullOrEmpty(),
            ),
            RssMoreMenuItem(
                MENU_ID_LOGIN,
                getString(R.string.login),
                visible = !source?.loginUrl.isNullOrBlank(),
            ),
            RssMoreMenuItem(MENU_ID_REFRESH_SORT, getString(R.string.refresh_sort)),
            RssMoreMenuItem(MENU_ID_SET_VARIABLE, getString(R.string.set_source_variable)),
            RssMoreMenuItem(MENU_ID_EDIT_SOURCE, getString(R.string.edit_source)),
            RssMoreMenuItem(MENU_ID_SWITCH_LAYOUT, getString(R.string.switchLayout)),
            RssMoreMenuItem(MENU_ID_BLOCK_RULE, getString(R.string.explore_block_rule)),
            RssMoreMenuItem(MENU_ID_READ_RECORD, getString(R.string.read_record)),
            RssMoreMenuItem(MENU_ID_CLEAR, getString(R.string.clear)),
            RssMoreMenuItem(MENU_ID_SWITCH_LEGACY, getString(R.string.switch_to_old_rss)),
        )
    }

    private fun handleModernRssMenu(itemId: Int) {
        when (itemId) {
            MENU_ID_PAGE -> {
                if (!modernController.source?.ruleNextPage.isNullOrEmpty()) {
                    modernExplorePagePick()
                }
            }

            MENU_ID_LOGIN -> modernController.source?.let {
                startActivity<SourceLoginActivity> {
                    putExtra("type", "rssSource")
                    putExtra("key", it.sourceUrl)
                }
            }

            MENU_ID_REFRESH_SORT -> modernController.refreshSorts()
            MENU_ID_SET_VARIABLE -> setModernSourceVariable()
            MENU_ID_EDIT_SOURCE -> modernController.source?.let {
                editSourceResult.launch {
                    putExtra("sourceUrl", it.sourceUrl)
                }
            }

            MENU_ID_SWITCH_LAYOUT -> modernController.toggleLayout()
            MENU_ID_BLOCK_RULE -> showModernBlockRuleConfig()
            MENU_ID_READ_RECORD -> showDialogFragment(
                ReadRecordDialog(modernController.source?.sourceUrl),
            )

            MENU_ID_CLEAR -> modernController.clearArticles()
            MENU_ID_SWITCH_LEGACY -> applyModernRss(false)
        }
    }

    private fun modernExplorePagePick() {
        NumberPickerDialog(requireActivity())
            .setTitle(getString(R.string.change_page))
            .setMinValue(1)
            .setMaxValue(999)
            .setValue(modernController.currentPage)
            .show { targetPage -> modernController.skipPageTo(targetPage) }
    }

    /** 首次进入新版模式时选中上次的订阅源并加载数据；源列表未就绪时不置位，等数据流再触发 */
    private fun initModernRssData() {
        val source = sourceItems.firstOrNull { it.sourceUrl == modernRssSourceUrl }
            ?: sourceItems.firstOrNull()
            ?: return
        modernRssInited = true
        selectModernRssSource(source)
    }

    /**
     * 新版订阅的"网页打开态"：单 URL 源 / 无文章列表规则的源解析不出分类，
     * 点按钮时按旧版流程打开（singleUrl 走网页，其余走 startHtml 或分类页）。
     */
    private fun openModernRssInWeb(source: RssSource) {
        openRss(
            RssSourceItem(
                sourceUrl = source.sourceUrl,
                sourceName = source.sourceName,
                sourceIcon = source.sourceIcon,
                hasLoginUrl = !source.loginUrl.isNullOrBlank(),
            ),
        )
    }

    /** 切换新版订阅的订阅源：持久化后由控制器重建分类与文章列表 */
    private fun selectModernRssSource(source: RssSource) {
        modernRssSourceUrl = source.sourceUrl
        AppConfig.modernRssSourceUrl = source.sourceUrl
        binding.titleBar.title = source.sourceName
        modernController.selectSource(source)
    }

    private fun readModernArticle(article: RssArticle) {
        modernController.source?.let { ReadRss.readRss(this, article, it) }
    }

    /** 新版订阅的屏蔽规则配置（对齐 RssSortActivity） */
    private fun showModernBlockRuleConfig() {
        val dialog = BlockRuleConfigDialog()
        dialog.sourceUrl = modernController.source?.sourceUrl.orEmpty()
        dialog.allBooks = emptyList()
        dialog.allRssArticles = modernController.rawArticlesForRules()
        dialog.onRulesChanged = {
            modernController.applyBlockRules()
        }
        dialog.show(parentFragmentManager, "rssModernBlockRuleConfig")
    }

    private fun setModernSourceVariable() {
        viewLifecycleOwner.lifecycleScope.launch {
            val source = modernController.source
            if (source == null) {
                toastOnUi(R.string.source_not_exist)
                return@launch
            }
            val comment =
                source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
            val variable = withContext(Dispatchers.IO) { source.getVariable() }
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

    private fun applyModernRss(value: Boolean) {
        modernRss = value
        AppConfig.rssModernPage = value
        upModernVisibility()
        if (value && !modernRssInited) {
            // 数据流处于 RESUMED 不会重发，就地初始化
            initModernRssData()
        }
    }

    override fun setVariable(key: String, variable: String?) {
        modernController.source?.setVariable(variable)
    }
}
