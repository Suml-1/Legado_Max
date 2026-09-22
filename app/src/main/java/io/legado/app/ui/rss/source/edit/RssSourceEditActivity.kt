package io.legado.app.ui.rss.source.edit

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.entities.RssSource
import io.legado.app.databinding.ActivityRssSourceEditBinding
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.rss.source.debug.RssSourceDebugActivity
import io.legado.app.ui.widget.dialog.UrlOptionDialog
import io.legado.app.ui.widget.dialog.CookieViewerDialog
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.ui.widget.keyboard.KeyboardToolPop
import io.legado.app.ui.widget.text.EditEntity
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.imeHeight
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isTrue
import io.legado.app.utils.launch
import io.legado.app.utils.navigationBarHeight
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.share
import io.legado.app.utils.shareWithQr
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.bottomPadding
import kotlin.text.isNotEmpty

class RssSourceEditActivity :
    VMBaseActivity<ActivityRssSourceEditBinding, RssSourceEditViewModel>(),
    KeyboardToolPop.CallBack,
    VariableDialog.Callback {

    override val binding by viewBinding(ActivityRssSourceEditBinding::inflate)
    override val viewModel by viewModels<RssSourceEditViewModel>()
    private val softKeyboardTool by lazy {
        KeyboardToolPop(this, lifecycleScope, binding.root, this)
    }
    private val adapter by lazy { RssSourceEditAdapter() }
    private val sourceEntities: ArrayList<EditEntity> = ArrayList()
    private val listEntities: ArrayList<EditEntity> = ArrayList()
    private val webViewEntities: ArrayList<EditEntity> = ArrayList()
    private val startEntities: ArrayList<EditEntity> = ArrayList()

    // 进行中的保存数量。保存入口有「保存/调试/登录/源变量」多个，用计数而不是布尔量：
    // 并发发起时先完成的一次不能把守卫提前解除，否则另一次保存还在写库就允许退出，
    // 协程随 Activity 销毁被取消 → 数据丢失。所有读写都在主线程。
    private var pendingSaveCount = 0

    // 进入编辑页时加载的订阅源引用。save() 成功后 viewModel.rssSource 会被替换为新对象，
    // 以此区分"本次会话确实保存过"与"未修改直接返回"，避免后者误触发调用方刷新。
    private var initialSource: RssSource? = null
    private val selectDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            if (uri.isContentScheme()) {
                sendText(uri.toString())
            } else {
                sendText(uri.path.toString())
            }
        }
    }
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it?.let {
            viewModel.importSource(it) { source: RssSource ->
                upSourceView(source)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        softKeyboardTool.attachToWindow(window)
        initView()
        viewModel.initData(intent) {
            initialSource = viewModel.rssSource
            upSourceView(viewModel.rssSource)
            // 处理从源内容查询界面跳转来的定位请求
            val tabKey = intent.getStringExtra("tabKey")
            val fieldKey = intent.getStringExtra("fieldKey")
            if (!tabKey.isNullOrBlank() && !fieldKey.isNullOrBlank()) {
                scrollToField(tabKey, fieldKey)
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (!LocalConfig.ruleHelpVersionIsLast) {
            showHelp("rssRuleHelp")
        }
    }

    override fun finish() {
        if (pendingSaveCount > 0) {
            // 保存正在进行中，阻止退出，避免协程被取消导致数据丢失
            toastOnUi(R.string.save_ing)
            return
        }
        val source = getRssSource()
        if (!source.equal(viewModel.rssSource ?: RssSource())) {
            alert(R.string.exit) {
                setMessage(R.string.exit_no_save)
                positiveButton(R.string.yes)
                negativeButton(R.string.no) {
                    super.finish()
                }
            }
        } else {
            // 兜底：已保存过但结果没能送达调用方时补设 RESULT_OK（如保存完成后界面被系统回收），
            // 未修改直接返回时保持默认 RESULT_CANCELED，避免误触发调用方刷新
            if (viewModel.rssSource !== initialSource) {
                setResult(RESULT_OK)
            }
            super.finish()
        }
    }

    /**
     * 保存订阅源。所有保存入口统一走这里：
     * 保存期间保持 pendingSaveCount > 0（阻止退出导致协程被取消），
     * 成功回调与 finally 只结算一次，成功回调先扣减再触发，保证内部 finish() 不被守卫拦下。
     */
    private fun saveSource(source: RssSource, onSuccess: ((RssSource) -> Unit)? = null) {
        pendingSaveCount++
        var settled = false
        fun settle(savedSource: RssSource?) {
            if (settled) return
            settled = true
            pendingSaveCount--
            if (savedSource != null) {
                onSuccess?.invoke(savedSource)
            }
        }
        viewModel.save(source, { settle(null) }) { savedSource ->
            settle(savedSource)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        softKeyboardTool.dismiss()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.source_edit, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_login)?.isVisible = !viewModel.rssSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_auto_complete)?.isChecked = viewModel.autoComplete
        return super.onMenuOpened(featureId, menu)
    }

    private val textEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val text = data?.getStringExtra("text")
            val fieldKey = data?.getStringExtra("fieldKey")
            val cursorPosition = data?.getIntExtra("cursorPosition", -1) ?: -1

            // text 为 null 表示"内容没改"（只回传定位参数）；空串是"用户把字段清空了"，
            // 必须当一次修改回写，否则清空操作会被静默丢弃
            if (text != null && !fieldKey.isNullOrEmpty()) {
                updateEditEntityValue(fieldKey, text, cursorPosition)
            } else if (!fieldKey.isNullOrEmpty()) {
                // 内容未变，只定位到该字段
                updateEditEntityValue(fieldKey, null, cursorPosition)
            } else if (text != null) {
                val view = window.decorView.findFocus()
                if (view is EditText) {
                    view.setText(text)
                    if (cursorPosition in 0 ..< view.text.length) {
                        view.setSelection(cursorPosition)
                    }
                } else {
                    toastOnUi(R.string.focus_lost_on_textbox)
                }
            }
        }
    }
    private fun onFullEditClicked() {
        val view = window.decorView.findFocus()
        if (view is EditText) {
            val hint = findParentTextInputLayout(view)?.hint?.toString()
            val currentText = view.text.toString()
            val fieldKey = view.getTag(R.id.tag) as? String ?: ""
            val intent = Intent(this, CodeEditActivity::class.java).apply {
                putExtra("text", currentText)
                putExtra("title", hint)
                putExtra("cursorPosition", view.selectionStart)
                putExtra("sourceType", "rssSource")
                putExtra("sourceKey", getRssSource().sourceUrl)
                putExtra("fieldKey", fieldKey)
                putExtra("tabKey", getCurrentTabKey())
            }
            textEditLauncher.launch(intent)
        }
        else {
            toastOnUi(R.string.please_focus_cursor_on_textbox)
        }
    }

    /**
     * 获取当前选中的Tab对应的key
     * @return tabKey: "base", "start", "list", "webView"
     */
    private fun getCurrentTabKey(): String {
        return when (binding.tabLayout.selectedTabPosition) {
            1 -> "start"
            2 -> "list"
            3 -> "webView"
            else -> "base"
        }
    }

    /**
     * 打开指定字段的全屏编辑器（供列表内截断预览字段点击时调用）。
     * 传入 entity.value 全文，保存后通过 textEditLauncher 回传更新。
     */
    private fun openFullEdit(editEntity: EditEntity) {
        val intent = Intent(this, CodeEditActivity::class.java).apply {
            putExtra("text", editEntity.value ?: "")
            putExtra("title", editEntity.hint)
            putExtra("cursorPosition", 0)
            putExtra("sourceType", "rssSource")
            putExtra("sourceKey", getRssSource().sourceUrl)
            putExtra("fieldKey", editEntity.key)
            putExtra("tabKey", getCurrentTabKey())
        }
        textEditLauncher.launch(intent)
    }

    /**
     * 更新指定字段的值并定位到该字段。
     * 按字段标识在所有板块里查找（全屏编辑内部可切换板块，不能只按当前 Tab 找）。
     *
     * @param fieldKey 字段标识，如 "sourceName", "ruleContent"
     * @param value 新的值；为 null 表示内容未变，只定位不回写
     * @param cursorPosition 光标位置（可选）
     */
    private fun updateEditEntityValue(fieldKey: String, value: String?, cursorPosition: Int = -1) {
        val allEntities = listOf(
            sourceEntities to 0,
            startEntities to 1,
            listEntities to 2,
            webViewEntities to 3
        )

        for ((entities, tabPosition) in allEntities) {
            val entity = entities.find { it.key == fieldKey } ?: continue
            value?.let { entity.value = it }

            if (binding.tabLayout.selectedTabPosition != tabPosition) {
                binding.tabLayout.selectTab(binding.tabLayout.getTabAt(tabPosition))
            }
            if (adapter.editEntities !== entities) {
                swapEditEntities(entities)
            }
            binding.recyclerView.post {
                // P4: 用增量更新替代 notifyDataSetChanged
                adapter.notifyItemRangeChanged(0, adapter.editEntities.size)
                scrollToFieldAndUpdate(entity, value, cursorPosition)
            }
            return
        }
    }

    private fun scrollToFieldAndUpdate(entity: EditEntity, value: String?, cursorPosition: Int) {
        val position = adapter.editEntities.indexOf(entity)
        if (position < 0) return
        val layoutManager = binding.recyclerView.layoutManager
        if (layoutManager is LinearLayoutManager) {
            layoutManager.scrollToPositionWithOffset(position, 0)
        } else {
            binding.recyclerView.scrollToPosition(position)
        }
        // 超长字段处于截断预览（只读）态，只滚动定位：
        // 不能把全文灌回预览控件，也不能获焦（预览控件已禁用）
        if (RssSourceEditAdapter.isPreview(entity)) {
            return
        }
        // 双层 post 确保布局完成后再获焦，避免 onBindViewHolder.clearFocus() 冲突
        binding.recyclerView.post {
            binding.recyclerView.post {
                // position 是 post 之前算好的；期间若用户切了 Tab（列表已整体替换），
                // 这个位置对应的已是别的字段，回写会污染别的字段，必须重新校验
                if (adapter.editEntities.getOrNull(position) !== entity) return@post
                val viewHolder = binding.recyclerView.findViewHolderForAdapterPosition(position)
                if (viewHolder is RssSourceEditAdapter.EditTextViewHolder) {
                    val editText = viewHolder.binding.editText
                    value?.let {
                        if (editText.text.toString() != it) {
                            editText.setText(it)
                        }
                    }
                    editText.requestFocus()
                    val pos = if (cursorPosition >= 0) {
                        cursorPosition.coerceAtMost(editText.text.length)
                    } else 0
                    if (editText.text.isNotEmpty()) {
                        editText.setSelection(pos)
                    }
                }
            }
        }
    }

    /**
     * 切换 Adapter 当前持有的编辑列表，并按新旧长度差异发出对应通知。
     *
     * 只发 notifyItemRangeChanged 而不处理长度增减，会让 RecyclerView 记录的条目数与 Adapter
     * 不一致（换到更短的列表时尤其明显），触发 Inconsistency detected 一类崩溃，
     * 因此列表替换必须走这里统一收口。
     */
    private fun swapEditEntities(entities: ArrayList<EditEntity>) {
        val oldSize = adapter.editEntities.size
        adapter.editEntities = entities
        val newSize = entities.size
        if (newSize > oldSize) {
            adapter.notifyItemRangeChanged(0, oldSize)
            adapter.notifyItemRangeInserted(oldSize, newSize - oldSize)
        } else if (newSize < oldSize) {
            adapter.notifyItemRangeChanged(0, newSize)
            adapter.notifyItemRangeRemoved(newSize, oldSize - newSize)
        } else {
            adapter.notifyItemRangeChanged(0, newSize)
        }
    }

    /**
     * 滚动到指定的字段位置（从源内容查询界面跳转时使用）
     * @param tabKey Tab 标识，如 "base", "start", "list", "webView"
     * @param fieldKey 字段标识，如 "sourceName", "ruleContent"
     */
    private fun scrollToField(tabKey: String, fieldKey: String) {
        val entities = when (tabKey) {
            "base" -> sourceEntities
            "start" -> startEntities
            "list" -> listEntities
            "webView" -> webViewEntities
            else -> return
        }

        val tabPosition = when (tabKey) {
            "base" -> 0
            "start" -> 1
            "list" -> 2
            "webView" -> 3
            else -> 0
        }

        val entity = entities.find { it.key == fieldKey } ?: return
        if (binding.tabLayout.selectedTabPosition != tabPosition) {
            binding.tabLayout.selectTab(binding.tabLayout.getTabAt(tabPosition))
        }
        if (adapter.editEntities !== entities) {
            swapEditEntities(entities)
        }
        binding.recyclerView.post {
            // P4: 用增量更新替代 notifyDataSetChanged
            adapter.notifyItemRangeChanged(0, adapter.editEntities.size)
            scrollToFieldAndUpdate(entity, entity.value ?: "", -1)
        }
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_fullscreen_edit -> onFullEditClicked()

            R.id.menu_edit_json -> showSourceJsonEdit()

            R.id.menu_save -> saveSource(getRssSource()) {
                setResult(RESULT_OK)
                finish()
            }

            R.id.menu_debug_source -> saveSource(getRssSource()) { source ->
                startActivity<RssSourceDebugActivity> {
                    putExtra("key", source.sourceUrl)
                }
            }

            R.id.menu_login -> saveSource(getRssSource()) {
                startActivity<SourceLoginActivity> {
                    putExtra("type", "rssSource")
                    putExtra("key", it.sourceUrl)
                }
            }

            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_clear_cookie -> viewModel.clearCookie(getRssSource().sourceUrl)
            R.id.menu_auto_complete -> viewModel.autoComplete = !viewModel.autoComplete
            R.id.menu_copy_source -> sendToClip(GSON.toJson(getRssSource()))
            R.id.menu_qr_code_camera -> qrCodeResult.launch()
            R.id.menu_paste_source -> viewModel.pasteSource { upSourceView(it) }
            R.id.menu_share_str -> share(GSON.toJson(getRssSource()))
            R.id.menu_share_qr -> shareWithQr(
                GSON.toJson(getRssSource()),
                getString(R.string.share_rss_source),
                ErrorCorrectionLevel.L
            )

            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_help -> showHelp("rssRuleHelp")
            R.id.menu_view_cookie -> showDialogFragment(CookieViewerDialog(getRssSource().sourceUrl))
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initView() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            setText(R.string.source_tab_base)
        })
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            setText(R.string.source_tab_start)
        })
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            setText(R.string.source_tab_list)
        })
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            text = "WEB_VIEW"
        })
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        // P3: RecyclerView 高度为 match_parent，不受内容数量影响
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.setItemViewCacheSize(15)
        binding.recyclerView.recycledViewPool.setMaxRecycledViews(0, 15)
        binding.recyclerView.recycledViewPool.setMaxRecycledViews(1, 10)
        // 关闭条目动画：列表内容按 Tab 整体替换，条目身份（字段）完全变掉，动画没有信息量。
        // 保留默认 DefaultItemAnimator 会有两个代价：
        // 1) notifyItemRangeChanged 触发 change 动画，RecyclerView 会为被更新的位置另建 ViewHolder
        //    并把旧 ViewHolder 挂进动画队列；下一次 notify/scroll 与动画回调交错时，
        //    DefaultItemAnimator 会对仍处于 attached 状态的 View 调 recycle，
        //    抛 IllegalArgumentException: Scrapped or attached views may not be recycled（Tab 切换/保存后闪退）。
        // 2) 另建 ViewHolder 会让 bind() 里的"key/value 未变则跳过 setText"优化失效（新 holder 无历史记录），
        //    长文本又会全文重新排版，Tab 切换反而更慢。
        binding.recyclerView.itemAnimator = null
        // 预览模式（超长文本截断显示）的字段被点击时打开全屏编辑
        adapter.onRequestFullEdit = { entity -> openFullEdit(entity) }
        val createSpanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = when (adapter.getItemViewType(position)) {
                EditEntity.ViewType.checkBox -> 1 //CheckBox 占1个span
                else -> 2 //占2个span（整行）
            }
        }
        val gridLayoutManager = if (AppConfig.sourceEditMaxLine < 999) {
            object : GridLayoutManager(this, 2) {
                init {
                    spanSizeLookup = createSpanSizeLookup
                }
                override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean) = false
                override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean) = false
            }
        } else {
            GridLayoutManager(this, 2).apply {
                spanSizeLookup = createSpanSizeLookup
            }
        }
        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus is EditText) {
                newFocus.postDelayed({ sendText("") }, 120)
            }
        }
        binding.tabLayout.setBackgroundColor(backgroundColor)
        binding.tabLayout.setSelectedTabIndicatorColor(accentColor)
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {

            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {

            }

            override fun onTabSelected(tab: TabLayout.Tab?) {
                setEditEntities(tab?.position)
            }
        })
        binding.recyclerView.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            val navigationBarHeight = windowInsets.navigationBarHeight
            val imeHeight = windowInsets.imeHeight
            view.bottomPadding = if (imeHeight == 0) navigationBarHeight else 0
            softKeyboardTool.initialPadding = imeHeight
            windowInsets
        }
    }

    private fun setEditEntities(tabPosition: Int?) {
        // P4: 用增量更新替代 notifyDataSetChanged
        swapEditEntities(
            when (tabPosition) {
                1 -> startEntities
                2 -> listEntities
                3 -> webViewEntities
                else -> sourceEntities
            }
        )
        binding.recyclerView.scrollToPosition(0)
        window.decorView.rootView.clearFocus()
    }

    private fun upSourceView(rssSource: RssSource?) {
        val rs = rssSource ?: RssSource()
        rs.let {
            binding.cbIsEnable.isChecked = rs.enabled
            binding.cbSingleUrl.isChecked = rs.singleUrl
            binding.cbIsEnableCookie.isChecked = rs.enabledCookieJar == true
            binding.cbIsEnablePreload.isChecked = rs.preload
            if (rs.type !in 0..<binding.spType.count) {
                rs.type = 0
            }
            binding.spType.setSelection(rs.type)
            if (rs.articleStyle !in 0..<binding.lyType.count) {
                rs.articleStyle = 0
            }
            binding.lyType.setSelection(rs.articleStyle)
        }
        sourceEntities.clear()
        sourceEntities.apply {
            add(EditEntity("sourceName", rs.sourceName, R.string.source_name))
            add(EditEntity("sourceUrl", rs.sourceUrl, R.string.source_url))
            add(EditEntity("sourceIcon", rs.sourceIcon, R.string.source_icon))
            add(EditEntity("sourceGroup", rs.sourceGroup, R.string.source_group))
            add(EditEntity("sourceComment", rs.sourceComment, R.string.comment))
            add(EditEntity("searchUrl", rs.searchUrl, R.string.r_search_url))
            add(EditEntity("sortUrl", rs.sortUrl, R.string.sort_url))
            add(EditEntity("loginUrl", rs.loginUrl, R.string.login_url))
            add(EditEntity("loginUi", rs.loginUi, R.string.login_ui))
            add(EditEntity("loginCheckJs", rs.loginCheckJs, R.string.login_check_js))
            add(EditEntity("coverDecodeJs", rs.coverDecodeJs, R.string.cover_decode_js))
            add(EditEntity("header", rs.header, R.string.source_http_header))
            add(EditEntity("variableComment", rs.variableComment, R.string.variable_comment))
            add(EditEntity("concurrentRate", rs.concurrentRate, R.string.concurrent_rate))
            add(EditEntity("jsLib", rs.jsLib, "jsLib"))
        }
        startEntities.clear()
        startEntities.apply {
            add(EditEntity("startHtml", rs.startHtml, R.string.r_startHtml))
            add(EditEntity("startStyle", rs.startStyle, R.string.r_startStyle))
            add(EditEntity("startJs", rs.startJs, R.string.r_startJs))
            add(EditEntity("preloadJs", rs.preloadJs, R.string.r_preloadJs))
        }
        listEntities.clear()
        listEntities.apply {
            add(EditEntity("ruleArticles", rs.ruleArticles, R.string.r_articles))
            add(EditEntity("ruleNextPage", rs.ruleNextPage, R.string.r_next))
            add(EditEntity("ruleTitle", rs.ruleTitle, R.string.r_title))
            add(EditEntity("rulePubDate", rs.rulePubDate, R.string.r_date))
            add(EditEntity("ruleDescription", rs.ruleDescription, R.string.r_description))
            add(EditEntity("ruleImage", rs.ruleImage, R.string.r_image))
            add(EditEntity("ruleLink", rs.ruleLink, R.string.r_link))
        }
        webViewEntities.clear()
        webViewEntities.apply {
            add(
                EditEntity(
                    "enableJs",
                    rs.enableJs.toString(),
                    R.string.enable_js,
                    EditEntity.ViewType.checkBox
                )
            )
            add(
                EditEntity(
                    "loadWithBaseUrl",
                    rs.loadWithBaseUrl.toString(),
                    R.string.load_with_base_url,
                    EditEntity.ViewType.checkBox
                )
            )
            add(
                 EditEntity(
                     "showWebLog",
                     rs.showWebLog.toString(),
                     R.string.load_with_web_log,
                     EditEntity.ViewType.checkBox
                 )
             )
            add(
                EditEntity(
                    "cacheFirst",
                    rs.cacheFirst.toString(),
                    R.string.cache_first,
                    EditEntity.ViewType.checkBox
                )
            )
            add(EditEntity("ruleContent", rs.ruleContent, R.string.r_content))
            add(EditEntity("style", rs.style, R.string.r_style))
            add(EditEntity("injectJs", rs.injectJs, R.string.r_inject_js))
            add(EditEntity("contentWhitelist", rs.contentWhitelist, R.string.c_whitelist))
            add(EditEntity("contentBlacklist", rs.contentBlacklist, R.string.c_blacklist))
            add(
                EditEntity(
                    "shouldOverrideUrlLoading",
                    rs.shouldOverrideUrlLoading,
                    "url跳转拦截(js, 返回true拦截,js变量url,可以通过js打开url,比如调用阅读搜索,添加书架等,简化规则写法,不用webView js注入)"
                )
            )
        }
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))
        setEditEntities(0)
    }

    private fun getRssSource(): RssSource {
        val source = viewModel.rssSource?.copy() ?: RssSource()
        source.enabled = binding.cbIsEnable.isChecked
        source.singleUrl = binding.cbSingleUrl.isChecked
        source.enabledCookieJar = binding.cbIsEnableCookie.isChecked
        source.preload = binding.cbIsEnablePreload.isChecked
        source.type = binding.spType.selectedItemPosition
        source.articleStyle = binding.lyType.selectedItemPosition
        sourceEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "sourceName" -> source.sourceName = it.value ?: ""
                "sourceUrl" -> source.sourceUrl = it.value ?: ""
                "sourceIcon" -> source.sourceIcon = it.value ?: ""
                "sourceGroup" -> source.sourceGroup = it.value
                "sourceComment" -> source.sourceComment = it.value
                "loginUrl" -> source.loginUrl = it.value
                "loginUi" -> source.loginUi = it.value
                "loginCheckJs" -> source.loginCheckJs = it.value
                "coverDecodeJs" -> source.coverDecodeJs = it.value
                "header" -> source.header = it.value
                "variableComment" -> source.variableComment = it.value
                "concurrentRate" -> source.concurrentRate = it.value
                "searchUrl" -> source.searchUrl = it.value
                "sortUrl" -> source.sortUrl = it.value
                "jsLib" -> source.jsLib = it.value
            }
        }
        startEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "startHtml" -> source.startHtml = it.value
                "startStyle" -> source.startStyle = it.value
                "startJs" -> source.startJs = it.value
                "preloadJs" -> source.preloadJs = it.value
            }
        }
        listEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "ruleArticles" -> source.ruleArticles = it.value
                "ruleNextPage" -> source.ruleNextPage =
                    viewModel.ruleComplete(it.value, source.ruleArticles, 2)

                "ruleTitle" -> source.ruleTitle =
                    viewModel.ruleComplete(it.value, source.ruleArticles)

                "rulePubDate" -> source.rulePubDate =
                    viewModel.ruleComplete(it.value, source.ruleArticles)

                "ruleDescription" -> source.ruleDescription =
                    viewModel.ruleComplete(it.value, source.ruleArticles)

                "ruleImage" -> source.ruleImage =
                    viewModel.ruleComplete(it.value, source.ruleArticles, 3)

                "ruleLink" -> source.ruleLink =
                    viewModel.ruleComplete(it.value, source.ruleArticles)
            }
        }
        webViewEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "enableJs" -> source.enableJs = it.value.isTrue()
                "loadWithBaseUrl" -> source.loadWithBaseUrl = it.value.isTrue()
                "showWebLog" -> source.showWebLog = it.value.isTrue()
                "cacheFirst" -> source.cacheFirst = it.value.isTrue()
                "ruleContent" -> source.ruleContent =
                    viewModel.ruleComplete(it.value, source.ruleArticles)

                "style" -> source.style = it.value
                "injectJs" -> source.injectJs = it.value
                "contentWhitelist" -> source.contentWhitelist = it.value
                "contentBlacklist" -> source.contentBlacklist = it.value
                "shouldOverrideUrlLoading" -> source.shouldOverrideUrlLoading = it.value
            }
        }
        return source
    }

    private fun setSourceVariable() {
        saveSource(getRssSource()) { source ->
            lifecycleScope.launch {
                val comment =
                    source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
                val variable = withContext(Dispatchers.IO) { source.getVariable() }
                showDialogFragment(
                    VariableDialog(
                        getString(R.string.set_source_variable),
                        source.getKey(),
                        variable,
                        comment
                    )
                )
            }
        }
    }

    override fun setVariable(key: String, variable: String?) {
        viewModel.rssSource?.setVariable(variable)
    }

    override fun helpActions(): List<SelectItem<String>> {
        return arrayListOf(
            SelectItem("插入URL参数", "urlOption"),
            SelectItem("订阅源教程", "ruleHelp"),
            SelectItem("js教程", "jsHelp"),
            SelectItem("正则教程", "regexHelp"),
            SelectItem("选择文件", "selectFile"),
        )
    }

    override fun onHelpActionSelect(action: String) {
        when (action) {
            "urlOption" -> UrlOptionDialog(this) {
                sendText(it)
            }.show()

            "ruleHelp" -> showHelp("rssRuleHelp")
            "jsHelp" -> showHelp("jsHelp")
            "regexHelp" -> showHelp("regexHelp")
            "selectFile" -> selectDoc.launch {
                mode = HandleFileContract.FILE
            }
        }
    }

    override fun sendText(text: String) {
        val view = window.decorView.findFocus()
        if (view is EditText) {
            var start = view.selectionStart
            var end = view.selectionEnd
            if (start > end) {
                val temp = start
                start = end
                end = temp
            }
            if (text.isNotEmpty()) {
                val edit = view.editableText//获取EditText的文字
                if (start < 0 || start >= edit.length) {
                    edit.append(text)
                } else {
                    edit.replace(start, end, text)//光标所在位置插入文字
                }
            }
            if (AppConfig.sourceEditMaxLine >= 999) {
                view.post {
                    val editTextLocation = IntArray(2)
                    view.getLocationOnScreen(editTextLocation)
                    val recyclerViewLocation = IntArray(2)
                    binding.recyclerView.getLocationOnScreen(recyclerViewLocation)
                    val layout = view.layout
                    if (layout != null) {
                        val line = layout.getLineForOffset(end)
                        val cursorYInEditText = layout.getLineTop(line)
                        // 光标相对于屏幕的位置
                        val cursorYOnScreen = editTextLocation[1] + cursorYInEditText
                        // 光标相对于RecyclerView的位置
                        val cursorYInRecyclerView = cursorYOnScreen - recyclerViewLocation[1]
                        val recyclerViewBottom = binding.recyclerView.height - 120 //考虑键盘的经验值
                        // 如果光标不在可见范围内，则滚动到光标位置
                        if (cursorYInRecyclerView !in 0..recyclerViewBottom) {
                            val scrollDistance = cursorYInRecyclerView - recyclerViewBottom / 3
                            if (scrollDistance > 0 && binding.recyclerView.canScrollVertically(1) || scrollDistance < 0 && binding.recyclerView.canScrollVertically(-1)) {
                                binding.recyclerView.smoothScrollBy(0, scrollDistance)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showSourceJsonEdit() {
        val source = getRssSource()
        val json = GSON.toJson(source)
        showDialogFragment(RssSourceJsonEditDialog(json) { newJson ->
            try {
                val newSource = GSON.fromJsonObject<RssSource>(newJson).getOrThrow()
                upSourceView(newSource)
            } catch (e: Exception) {
                toastOnUi(R.string.json_format)
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onUndoClicked() {
        val editText = window.decorView.findFocus()
        if (editText is EditText) {
            editText.onTextContextMenuItem(android.R.id.undo)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onRedoClicked() {
        val editText = window.decorView.findFocus()
        if (editText is EditText) {
            editText.onTextContextMenuItem(android.R.id.redo)
        }
    }

}