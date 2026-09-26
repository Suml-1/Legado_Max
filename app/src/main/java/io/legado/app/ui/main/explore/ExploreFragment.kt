package io.legado.app.ui.main.explore

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.FragmentExploreBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.manage.BookSourceSort
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.explore.compose.ExploreKindsController
import io.legado.app.ui.main.explore.compose.ExploreSourceActions
import io.legado.app.ui.main.explore.compose.ExploreSourceItem
import io.legado.app.ui.main.explore.compose.ExploreSourceList
import io.legado.app.ui.main.explore.compose.ExploreSourceMenuAction
import io.legado.app.ui.main.explore.compose.toExploreSourceItems
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.applyTint
import io.legado.app.utils.cnCompare
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
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
class ExploreFragment() : VMBaseFragment<ExploreViewModel>(R.layout.fragment_explore),
    MainFragmentInterface,
    ExploreKindQueryDialog.OnKindSelected {

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
        setSupportToolbar(binding.titleBar.toolbar)
        // 首次进入时主动取一次底栏高度，之后由 MainActivity 通过接口推送变化
        bottomPaddingPx = (activity as? MainActivity)?.mainContentBottomPadding() ?: 0
        initSearchView()
        initComposeContent()
        initGroupData()
        upExploreData(searchView.query?.toString())
    }

    private fun initComposeContent() {
        binding.composeSourceList.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.composeSourceList.setContent {
            LegadoTheme {
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
        val sortSubMenu = menu.findItem(R.id.action_sort).subMenu!!
        sortSubMenu.findItem(R.id.menu_sort_desc).isChecked = !sortAscending
        sortSubMenu.setGroupCheckable(R.id.menu_group_sort, true, true)
        super.onPrepareOptionsMenu(menu)
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.screen_find)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

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
                    AppDatabase.BOOK_SOURCE_TABLE_NAME
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
                AppDatabase.BOOK_SOURCE_TABLE_NAME
            ).catch {
                AppLog.put("发现界面更新数据出错", it)
            }.conflate().flowOn(IO).collect { data ->
                sourceItems = data
                displayItems = data.toExploreSourceItems()
                // 搜索中不显示空态：搜索框里的字还没清掉，列表空着是正常的
                binding.tvEmptyMsg.isGone = data.isNotEmpty() || searchView.query.isNotEmpty()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        kindsController.resumeWebViews()
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
            subMenu.add(R.id.menu_group_text, Menu.NONE, Menu.NONE, it)
        }
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
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
        startActivity<BookSourceEditActivity> {
            putExtra("sourceUrl", sourceUrl)
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

}
