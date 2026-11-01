package io.legado.app.ui.rss.source.manage

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.databinding.ActivityRssSourceBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.utils.ACache
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.applyTint
import io.legado.app.utils.cnCompare
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.eInkGrayscale
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import splitties.init.appCtx
import io.legado.app.ui.association.ImportRssSourceDialog
import io.legado.app.ui.association.ImportUrlDialogHelper
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.launch
import io.legado.app.help.ExportResultHandler
import io.legado.app.model.rss.Rss
import io.legado.app.utils.StringUtils
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 订阅源管理
 */
class RssSourceActivity :
    VMBaseActivity<ActivityRssSourceBinding, RssSourceViewModel>(),
    PopupMenu.OnMenuItemClickListener,
    SelectActionBar.CallBack,
    RssSourceAdapter.CallBack {

    override val binding by viewBinding(ActivityRssSourceBinding::inflate)
    override val viewModel by viewModels<RssSourceViewModel>()
    private val importRecordKey = "rssSourceRecordKey"
    private val adapter by lazy { RssSourceAdapter(this, this) }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private var sourceFlowJob: Job? = null
    private var groups = arrayListOf<String>()
    private var groupMenu: SubMenu? = null

    /**
     * 订阅源排序方式，使用 SharedPreferences 持久化保存
     * 读取时从配置中获取，默认值为手动排序
     * 写入时同步保存到配置中
     */
    var sort = RssSourceSort.entries[appCtx.getPrefInt(PreferKey.rssSourceSort, 0)]
        private set(value) {
            field = value
            appCtx.putPrefInt(PreferKey.rssSourceSort, value.ordinal)
        }

    /**
     * 排序方向，使用 SharedPreferences 持久化保存
     * true=升序，false=降序
     */
    var sortAscending = appCtx.getPrefBoolean(PreferKey.rssSourceSortAscending, true)
        private set(value) {
            field = value
            appCtx.putPrefBoolean(PreferKey.rssSourceSortAscending, value)
        }

    /**
     * 是否按域名分组，使用 SharedPreferences 持久化保存
     */
    private var groupSourcesByDomain = appCtx.getPrefBoolean(PreferKey.rssSourceGroupByDomain, false)

    /**
     * 域名缓存，避免重复提取
     */
    private val hostMap = hashMapOf<String, String>()
    private var locateSourceUrl: String? = null
    private var locateSourceName: String? = null
    private val itemTouchCallback by lazy { ItemTouchCallback(adapter) }
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportRssSourceDialog(it))
    }
    private val importDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            showDialogFragment(ImportRssSourceDialog(uri.toString()))
        }
    }
    private val exportResult = registerForActivityResult(HandleFileContract()) {
        ExportResultHandler.handleExportResult(this, it, { text ->
            sendToClip(text)
        }, StringUtils.RSS_SOURCE)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        locateSourceUrl = intent.getStringExtra("locateSourceUrl")
        locateSourceName = intent.getStringExtra("locateSourceName")
        AppLog.put("RssSourceActivity启动: locateSourceUrl=$locateSourceUrl, locateSourceName=$locateSourceName")
        initRecyclerView()
        initSearchView()
        if (locateSourceUrl != null) {
            searchView.setQuery("", false)
        }
        initGroupFlow()
        upSourceFlow()
        initSelectActionBar()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        locateSourceUrl = intent.getStringExtra("locateSourceUrl")
        locateSourceName = intent.getStringExtra("locateSourceName")
        AppLog.put("RssSourceActivity onNewIntent: locateSourceUrl=$locateSourceUrl, locateSourceName=$locateSourceName")
        if (locateSourceUrl != null) {
            searchView.setQuery("", false)
            upSourceFlow()
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.rss_source, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    /**
     * 准备选项菜单，恢复菜单选中状态
     * 从持久化配置中读取排序设置，同步到菜单项
     * 注意：menu_group_sources_by_domain 在主菜单中，不在 action_sort 子菜单中
     */
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        groupMenu = menu.findItem(R.id.menu_group)?.subMenu
        val sortSubMenu = menu.findItem(R.id.action_sort).subMenu!!
        // 恢复排序方向（升序/降序）菜单状态
        sortSubMenu.findItem(R.id.menu_sort_desc).isChecked = !sortAscending
        sortSubMenu.setGroupCheckable(R.id.menu_group_sort, true, true)
        // 恢复排序方式菜单选中状态
        when (sort) {
            RssSourceSort.Default -> sortSubMenu.findItem(R.id.menu_sort_manual)?.isChecked = true
            RssSourceSort.Name -> sortSubMenu.findItem(R.id.menu_sort_name)?.isChecked = true
            RssSourceSort.Url -> sortSubMenu.findItem(R.id.menu_sort_url)?.isChecked = true
            RssSourceSort.Update -> sortSubMenu.findItem(R.id.menu_sort_time)?.isChecked = true
            RssSourceSort.Enable -> sortSubMenu.findItem(R.id.menu_sort_enable)?.isChecked = true
        }
        // 按域名分组菜单在主菜单中，需要直接从 menu 查找
        menu.findItem(R.id.menu_group_sources_by_domain)?.isChecked = groupSourcesByDomain
        upGroupMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add -> startActivity<RssSourceEditActivity>()
            R.id.menu_import_local -> importDoc.launch {
                mode = HandleFileContract.FILE
                allowExtensions = arrayOf("txt", "json")
            }

            R.id.menu_import_onLine -> showImportDialog()
            R.id.menu_import_qr -> qrCodeResult.launch()
            R.id.menu_group_manage -> showDialogFragment<GroupManageDialog>()
            R.id.menu_import_default -> viewModel.importDefault()

            R.id.menu_sort_desc -> {
                sortAscending = !sortAscending
                item.isChecked = !sortAscending
                upSourceFlow(searchView.query?.toString())
            }

            R.id.menu_sort_manual -> {
                item.isChecked = true
                sort = RssSourceSort.Default
                upSourceFlow(searchView.query?.toString())
            }

            R.id.menu_sort_name -> {
                item.isChecked = true
                sort = RssSourceSort.Name
                upSourceFlow(searchView.query?.toString())
            }

            R.id.menu_sort_url -> {
                item.isChecked = true
                sort = RssSourceSort.Url
                upSourceFlow(searchView.query?.toString())
            }

            R.id.menu_sort_time -> {
                item.isChecked = true
                sort = RssSourceSort.Update
                upSourceFlow(searchView.query?.toString())
            }

            R.id.menu_sort_enable -> {
                item.isChecked = true
                sort = RssSourceSort.Enable
                upSourceFlow(searchView.query?.toString())
            }

            R.id.menu_group_sources_by_domain -> {
                item.isChecked = !item.isChecked
                groupSourcesByDomain = item.isChecked
                appCtx.putPrefBoolean(PreferKey.rssSourceGroupByDomain, item.isChecked)
                adapter.showSourceHost = item.isChecked
                itemTouchCallback.isCanDrag = !item.isChecked && sort == RssSourceSort.Default
                upSourceFlow(searchView.query?.toString())
            }

            R.id.menu_enabled_group -> {
                searchView.setQuery(getString(R.string.enabled), true)
            }

            R.id.menu_disabled_group -> {
                searchView.setQuery(getString(R.string.disabled), true)
            }

            R.id.menu_group_login -> {
                searchView.setQuery(getString(R.string.need_login), true)
            }

            R.id.menu_group_null -> {
                searchView.setQuery(getString(R.string.no_group), true)
            }

            R.id.menu_content_query -> {
                showDialogFragment(RssSourceContentSearchDialog())
            }

            R.id.menu_check_rss_sources -> checkRssSources()

            R.id.menu_help -> showHelp("SourceMRssHelp")
            else -> if (item.groupId == R.id.source_group) {
                searchView.setQuery("group:${item.title}", true)
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_enable_selection -> viewModel.enableSelection(adapter.selection)
            R.id.menu_disable_selection -> viewModel.disableSelection(adapter.selection)
            R.id.menu_add_group -> selectionAddToGroups()
            R.id.menu_remove_group -> selectionRemoveFromGroups()
            R.id.menu_top_sel -> viewModel.topSource(*adapter.selection.toTypedArray())
            R.id.menu_bottom_sel -> viewModel.bottomSource(*adapter.selection.toTypedArray())
            R.id.menu_export_selection -> viewModel.saveToFile(adapter.selection) { file, name ->
                exportResult.launch {
                    mode = HandleFileContract.EXPORT
                    fileData = HandleFileContract.FileData(
                        name,
                        file,
                        "application/json",
                    )
                }
            }

            R.id.menu_share_source -> viewModel.saveToFile(adapter.selection) { file, name ->
                share(file)
            }

            R.id.menu_check_selected_interval -> adapter.checkSelectedInterval()
        }
        return true
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setFastScrollEnabled(true)
        binding.recyclerView.isVerticalScrollBarEnabled = false
        val dragSelectTouchHelper: DragSelectTouchHelper =
            DragSelectTouchHelper(adapter.dragSelectCallback).setSlideArea(16, 50)
        dragSelectTouchHelper.attachToRecyclerView(binding.recyclerView)
        dragSelectTouchHelper.activeSlideSelect()
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
    }

    private fun initSearchView() {
        binding.titleBar.findViewById<SearchView>(R.id.search_view).let {
            it.applyTint(primaryTextColor)
            it.onActionViewExpanded()
            it.queryHint = getString(R.string.search_rss_source)
            it.clearFocus()
            it.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    upSourceFlow(newText)
                    return false
                }
            })
        }
    }

    private fun initSelectActionBar() {
        binding.selectActionBar.setMainActionText(R.string.delete)
        binding.selectActionBar.inflateMenu(R.menu.rss_source_sel)
        binding.selectActionBar.setOnMenuItemClickListener(this)
        binding.selectActionBar.setCallBack(this)
    }

    private fun initGroupFlow() {
        lifecycleScope.launch {
            appDb.rssSourceDao.flowGroups().conflate().collect {
                groups.clear()
                groups.addAll(it)
                upGroupMenu()
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun selectionAddToGroups() {
        alert(titleResource = R.string.add_group) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint(R.string.group_name)
                editView.setFilterValues(groups.toList())
                editView.dropDownHeight = 180.dpToPx()
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    if (it.isNotEmpty()) {
                        viewModel.selectionAddToGroups(adapter.selection, it)
                    }
                }
            }
            cancelButton()
        }
    }

    @SuppressLint("InflateParams")
    private fun selectionRemoveFromGroups() {
        alert(titleResource = R.string.remove_group) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint(R.string.group_name)
                editView.setFilterValues(groups.toList())
                editView.dropDownHeight = 180.dpToPx()
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    if (it.isNotEmpty()) {
                        viewModel.selectionRemoveFromGroups(adapter.selection, it)
                    }
                }
            }
            cancelButton()
        }
    }

    override fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            adapter.selectAll()
        } else {
            adapter.revertSelection()
        }
    }

    override fun revertSelection() {
        adapter.revertSelection()
    }

    override fun onClickSelectBarMainAction() {
        delSourceDialog()
    }

    private fun delSourceDialog() {
        alert(titleResource = R.string.draw, messageResource = R.string.sure_del) {
            yesButton { viewModel.del(*adapter.selection.toTypedArray()) }
            noButton()
        }
    }

    /**
     * 校验订阅源是否还能正常使用，校验失败的源可一键删除
     */
    private fun checkRssSources() {
        if (adapter.itemCount == 0) {
            toastOnUi(R.string.check_rss_source_success)
            return
        }
        val sources = appDb.rssSourceDao.all
        val broken = arrayListOf<RssSource>()
        lifecycleScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                sources.forEach { source ->
                    runCatching {
                        withTimeout(60_000L) {
                            val sortUrl = source.sortUrl ?: source.sourceUrl
                            Rss.getArticlesAwait("", sortUrl, source, 1)
                        }
                    }.onFailure {
                        broken.add(source)
                    }
                }
            }
            if (broken.isEmpty()) {
                toastOnUi(R.string.check_rss_source_success)
            } else {
                alert(titleResource = R.string.check_rss_sources) {
                    setMessage(getString(R.string.check_rss_source_fail, broken.size))
                    positiveButton(R.string.delete_broken_sources) {
                        viewModel.del(*broken.toTypedArray())
                    }
                    noButton()
                }
            }
        }
    }

    private fun upGroupMenu() = groupMenu?.transaction { menu ->
        menu.removeGroup(R.id.source_group)
        groups.forEach {
            // 分组名是用户自定义的（可能带表情符号），且这个下拉是 AppCompat 自己开的窗口，
            // 挂不了整层灰阶，只能把标题本身按灰阶绘制（见 CharSequence.eInkGrayscale）
            menu.add(R.id.source_group, Menu.NONE, Menu.NONE, it.eInkGrayscale())
        }
    }

    private fun upSourceFlow(searchKey: String? = null) {
        sourceFlowJob?.cancel()
        sourceFlowJob = lifecycleScope.launch {
            when {
                searchKey.isNullOrBlank() -> {
                    appDb.rssSourceDao.flowAll()
                }

                searchKey == getString(R.string.enabled) -> {
                    appDb.rssSourceDao.flowEnabled()
                }

                searchKey == getString(R.string.disabled) -> {
                    appDb.rssSourceDao.flowDisabled()
                }

                searchKey == getString(R.string.need_login) -> {
                    appDb.rssSourceDao.flowLogin()
                }

                searchKey == getString(R.string.no_group) -> {
                    appDb.rssSourceDao.flowNoGroup()
                }

                searchKey.startsWith("group:") -> {
                    val key = searchKey.substringAfter("group:")
                    appDb.rssSourceDao.flowGroupSearch(key)
                }

                else -> {
                    appDb.rssSourceDao.flowSearch(searchKey)
                }
            }.map { data ->
                hostMap.clear()
                if (groupSourcesByDomain) {
                    data.sortedWith(
                        compareBy<RssSource> { getSourceHost(it.sourceUrl) == "#" }
                            .thenBy { getSourceHost(it.sourceUrl) }
                            .thenByDescending { it.lastUpdateTime },
                    )
                } else if (sortAscending) {
                    when (sort) {
                        RssSourceSort.Name -> data.sortedWith { o1, o2 ->
                            o1.sourceName.cnCompare(o2.sourceName)
                        }

                        RssSourceSort.Url -> data.sortedBy { it.sourceUrl }
                        RssSourceSort.Update -> data.sortedByDescending { it.lastUpdateTime }
                        RssSourceSort.Enable -> data.sortedWith { o1, o2 ->
                            var sortNum = -o1.enabled.compareTo(o2.enabled)
                            if (sortNum == 0) {
                                sortNum = o1.sourceName.cnCompare(o2.sourceName)
                            }
                            sortNum
                        }

                        else -> data
                    }
                } else {
                    when (sort) {
                        RssSourceSort.Name -> data.sortedWith { o1, o2 ->
                            o2.sourceName.cnCompare(o1.sourceName)
                        }

                        RssSourceSort.Url -> data.sortedByDescending { it.sourceUrl }
                        RssSourceSort.Update -> data.sortedBy { it.lastUpdateTime }
                        RssSourceSort.Enable -> data.sortedWith { o1, o2 ->
                            var sortNum = o1.enabled.compareTo(o2.enabled)
                            if (sortNum == 0) {
                                sortNum = o1.sourceName.cnCompare(o2.sourceName)
                            }
                            sortNum
                        }

                        else -> data.reversed()
                    }
                }
            }.catch {
                AppLog.put("订阅源管理界面更新数据出错", it)
            }.flowOn(IO).conflate().collect {
                if (locateSourceUrl != null) {
                    adapter.setItems(it)
                } else {
                    adapter.setItems(it, adapter.diffItemCallback)
                }
                itemTouchCallback.isCanDrag =
                    sort == RssSourceSort.Default &&
                    !groupSourcesByDomain
                tryLocateSource(it)
                delay(100)
            }
        }
    }

    private fun tryLocateSource(data: List<RssSource>) {
        val urlToLocate = locateSourceUrl ?: return
        val nameToLocate = locateSourceName
        val index = data.indexOfFirst { it.sourceUrl == urlToLocate }.takeIf { it >= 0 }
            ?: data.indexOfFirst { nameToLocate != null && it.sourceName == nameToLocate }
        AppLog.put("locate rss source: url=$urlToLocate, name=$nameToLocate, index=$index, dataSize=${data.size}")
        if (index < 0) {
            return
        }
        locateSourceUrl = null
        locateSourceName = null
        binding.recyclerView.post {
            (binding.recyclerView.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(index, 72.dpToPx())
                ?: binding.recyclerView.scrollToPosition(index)
            adapter.setSelection(index)
            AppLog.put("locate rss source done: index=$index")
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.upResumed(true)
    }

    override fun onPause() {
        adapter.upResumed(false)
        super.onPause()
    }

    override fun upCountView() {
        binding.selectActionBar.upCountView(
            adapter.selection.size,
            adapter.itemCount,
        )
    }

    override fun getSourceHost(origin: String): String = hostMap.getOrPut(origin) {
        NetworkUtils.getSubDomainOrNull(origin) ?: "#"
    }

    @SuppressLint("InflateParams")
    private fun showImportDialog() {
        val aCache = ACache.get(cacheDir = false)
        val cacheUrls: MutableList<String> = aCache
            .getAsString(importRecordKey)
            ?.splitNotBlank(",")
            ?.toMutableList() ?: mutableListOf()
        alert(titleResource = R.string.import_on_line) {
            val alertBinding = ImportUrlDialogHelper.createBinding(
                layoutInflater = layoutInflater,
                context = this@RssSourceActivity,
                lifecycleOwner = this@RssSourceActivity,
                cacheUrls = cacheUrls,
                onUrlsChanged = {
                    aCache.put(importRecordKey, it.joinToString(","))
                },
                openBrowser = { url ->
                    startActivity<WebViewActivity> {
                        putExtra("url", url)
                    }
                },
            )
            customView { alertBinding.root }
            okButton {
                val text = alertBinding.editView.text?.toString()?.trim()
                text?.let {
                    if (it.isAbsUrl() && !cacheUrls.contains(it)) {
                        cacheUrls.add(0, it)
                        aCache.put(importRecordKey, cacheUrls.joinToString(","))
                    }
                    showDialogFragment(
                        ImportRssSourceDialog(it),
                    )
                }
            }
            cancelButton()
        }
    }

    override fun del(source: RssSource) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + source.sourceName)
            noButton()
            yesButton {
                viewModel.del(source)
            }
        }
    }

    override fun edit(source: RssSource) {
        startActivity<RssSourceEditActivity> {
            putExtra("sourceUrl", source.sourceUrl)
        }
    }

    override fun update(vararg source: RssSource) {
        viewModel.update(*source)
    }

    override fun toTop(source: RssSource) {
        if (sortAscending) {
            viewModel.topSource(source)
        } else {
            viewModel.bottomSource(source)
        }
    }

    override fun toBottom(source: RssSource) {
        if (sortAscending) {
            viewModel.bottomSource(source)
        } else {
            viewModel.topSource(source)
        }
    }

    override fun upOrder() {
        viewModel.upOrder()
    }
}
