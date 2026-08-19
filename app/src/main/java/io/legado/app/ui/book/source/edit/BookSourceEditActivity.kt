package io.legado.app.ui.book.source.edit

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.ReadConstants
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.databinding.ActivityBookSourceEditBinding
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.widget.dialog.UrlOptionDialog
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.ui.widget.keyboard.KeyboardToolPop
import io.legado.app.ui.widget.recycler.NoChildScrollLinearLayoutManager
import io.legado.app.ui.widget.text.EditEntity
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.imeHeight
import io.legado.app.utils.isContentScheme
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
import io.legado.app.model.debug.ToastContext
import io.legado.app.model.debug.ToastSourceType
import io.legado.app.model.debug.ToastRuleType
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.bottomPadding

/**
 * 书源编辑Activity
 * 提供书源的增删改查功能，支持通过Tab页签切换不同规则模块进行编辑
 * 包含基本设置、搜索规则、发现规则、详情规则、目录规则、正文规则等
 */
class BookSourceEditActivity :
    VMBaseActivity<ActivityBookSourceEditBinding, BookSourceEditViewModel>(),
    KeyboardToolPop.CallBack,
    VariableDialog.Callback {

    override val binding by viewBinding(ActivityBookSourceEditBinding::inflate)
    override val viewModel by viewModels<BookSourceEditViewModel>()

    // RecyclerView适配器
    private val adapter by lazy { BookSourceEditAdapter() }
    // 各Tab页签对应的编辑实体列表
    private val sourceEntities: ArrayList<EditEntity> = ArrayList()    // 基本信息
    private val searchEntities: ArrayList<EditEntity> = ArrayList()    // 搜索规则
    private val exploreEntities: ArrayList<EditEntity> = ArrayList()   // 发现规则
    private val infoEntities: ArrayList<EditEntity> = ArrayList()      // 详情页规则
    private val tocEntities: ArrayList<EditEntity> = ArrayList()       // 目录页规则
    private val contentEntities: ArrayList<EditEntity> = ArrayList()   // 正文页规则

    // 段评规则（已废弃）
    // private val reviewEntities: ArrayList<EditEntity> = ArrayList()
    
    // 保存全屏编辑前的焦点EditText引用，用于返回时恢复光标位置
    private var lastFocusedEditText: EditText? = null
    private var lastFocusedFieldKey: String = ""
    private var lastFocusedTabKey: String = ""

    // 标记保存是否正在进行，防止保存未完成时退出导致数据丢失
    @Volatile
    private var isSaving = false

    // 进入编辑页时加载的书源引用。
    // save() 成功后 viewModel.bookSource 会被替换为新对象（引用变化），
    // 以此区分"本次会话确实保存过书源"与"未修改直接返回"，
    // 避免后者误触发调用方（详情页/换源对话框/阅读页）的刷新逻辑。
    private var initialSource: BookSource? = null

    // 二维码扫描结果回调：用于从二维码导入书源
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        viewModel.importSource(it) { source ->
            upSourceView(source)
        }
    }
    // 文件选择回调：用于选择本地书源文件
    private val selectDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            if (uri.isContentScheme()) {
                sendText(uri.toString())
            } else {
                sendText(uri.path.toString())
            }
        }
    }

    // 软键盘辅助工具栏
    private val softKeyboardTool by lazy {
        KeyboardToolPop(this, lifecycleScope, binding.root, this)
    }

    /**
     * Activity创建时调用
     * 初始化视图、加载数据、绑定书源
     */
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        softKeyboardTool.attachToWindow(window)
        initView()
        viewModel.initData(intent) {
            initialSource = viewModel.bookSource
            upSourceView(viewModel.bookSource)
            // 处理从源内容查询界面跳转来的定位请求
            val tabKey = intent.getStringExtra("tabKey")
            val fieldKey = intent.getStringExtra("fieldKey")
            if (!tabKey.isNullOrBlank() && !fieldKey.isNullOrBlank()) {
                scrollToField(tabKey, fieldKey)
            }
        }
    }

    /**
     * 页面创建完成后调用
     * 如果规则帮助不是最新版本，则显示帮助页面
     */
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (!LocalConfig.ruleHelpVersionIsLast) {
            showHelp("ruleHelp")
        }
    }

    /**
     * 创建选项菜单
     */
    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.source_edit, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    /**
     * 菜单打开前动态调整菜单项可见性
     */
    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_login)?.isVisible = !getSource().loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_auto_complete)?.isChecked = viewModel.autoComplete
        return super.onMenuOpened(featureId, menu)
    }

    /**
     * 全屏文本编辑器回调
     * 用于接收从 CodeEditActivity 返回的编辑后文本和光标位置
     */
    private val textEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            // 编辑后的文本内容
            val text = data?.getStringExtra("text")
            // 字段标识，如 "author" 表示作者字段
            val fieldKey = data?.getStringExtra("fieldKey")
            // 板块标识，如 "info" 表示详情板块
            val tabKey = data?.getStringExtra("tabKey")
            val cursorPosition = data?.getIntExtra("cursorPosition", -1) ?: -1
            
            if (text != null && !fieldKey.isNullOrEmpty() && !tabKey.isNullOrEmpty()) {
                updateEditEntityValue(tabKey, fieldKey, text, cursorPosition)
            } else if (!fieldKey.isNullOrEmpty() && !tabKey.isNullOrEmpty()) {
                scrollToField(tabKey, fieldKey, cursorPosition)
            } else if (text != null) {
                lastFocusedEditText?.let { editText ->
                    editText.setText(text)
                    if (cursorPosition in 0 ..< editText.text.length) {
                        editText.setSelection(cursorPosition)
                    }
                    editText.requestFocus()
                }
            } else if (cursorPosition >= 0) {
                lastFocusedEditText?.let { editText ->
                    if (cursorPosition in 0 ..< editText.text.length) {
                        editText.setSelection(cursorPosition)
                    }
                    editText.requestFocus()
                }
            }
        }
    }

    /**
     * 根据tabKey和fieldKey更新对应的EditEntity值
     * 
     * @param tabKey 板块标识，用于确定在哪个列表中查找
     *               - "base": 基本信息（源地址、源名称等）
     *               - "search": 搜索规则
     *               - "explore": 发现规则
     *               - "info": 详情规则
     *               - "toc": 目录规则
     *               - "content": 正文规则
     * @param fieldKey 字段标识，如 "author" 表示作者，"name" 表示书名
     * @param value 要更新的值
     * @param cursorPosition 光标位置，用于返回后恢复光标
     */
    private fun updateEditEntityValue(tabKey: String, fieldKey: String, value: String, cursorPosition: Int = -1) {
        scrollToField(tabKey, fieldKey, cursorPosition, value)
    }

    private fun scrollToField(
        tabKey: String,
        fieldKey: String,
        cursorPosition: Int = -1,
        value: String? = null
    ) {
        val tabPosition = getTabPosition(tabKey)
        val entities = when (tabKey) {
            "base" -> sourceEntities
            "search" -> searchEntities
            "explore" -> exploreEntities
            "info" -> infoEntities
            "toc" -> tocEntities
            "content" -> contentEntities
            else -> null
        } ?: return

        val entity = entities.find { it.key == fieldKey } ?: return
        value?.let { entity.value = it }

        if (binding.tabLayout.selectedTabPosition != tabPosition) {
            binding.tabLayout.selectTab(binding.tabLayout.getTabAt(tabPosition))
        }
        if (adapter.editEntities !== entities) {
            adapter.editEntities = entities
        }
        binding.recyclerView.post {
            adapter.notifyDataSetChanged()
            scrollToEntity(entity, cursorPosition, value)
        }
    }

    private fun getTabPosition(tabKey: String): Int {
        return when (tabKey) {
            "search" -> 1
            "explore" -> 2
            "info" -> 3
            "toc" -> 4
            "content" -> 5
            else -> 0
        }
    }

    private fun scrollToEntity(entity: EditEntity, cursorPosition: Int, value: String?) {
        val position = adapter.editEntities.indexOf(entity)
        if (position < 0) return
        val layoutManager = binding.recyclerView.layoutManager
        if (layoutManager is LinearLayoutManager) {
            layoutManager.scrollToPositionWithOffset(position, 0)
        } else {
            binding.recyclerView.scrollToPosition(position)
        }
        // 双层 post 确保布局完成后再获焦，避免 onBindViewHolder.clearFocus() 冲突
        binding.recyclerView.post {
            binding.recyclerView.post {
                val viewHolder = binding.recyclerView.findViewHolderForAdapterPosition(position)
                if (viewHolder is BookSourceEditAdapter.MyViewHolder) {
                    val editText = viewHolder.binding.editText
                    value?.let {
                        if (editText.text.toString() != it) {
                            editText.setText(it)
                        }
                    }
                    editText.requestFocus()
                    val pos = if (cursorPosition >= 0) cursorPosition.coerceAtMost(editText.text.length) else 0
                    if (editText.text.isNotEmpty()) {
                        editText.setSelection(pos)
                    }
                }
            }
        }
    }

    /**
     * 处理全屏编辑按钮点击
     * 将当前光标焦点的文本框内容打开到 CodeEditActivity 进行全屏编辑
     */
    private fun onFullEditClicked() {
        val view = window.decorView.findFocus()
        if (view is EditText) {
            lastFocusedEditText = view
            val hint = findParentTextInputLayout(view)?.hint?.toString()
            val currentText = view.text.toString()
            val fieldKey = view.getTag(R.id.tag) as? String ?: ""
            val tabKey = getCurrentTabKey()
            lastFocusedFieldKey = fieldKey
            lastFocusedTabKey = tabKey
            val intent = Intent(this, CodeEditActivity::class.java).apply {
                putExtra("text", currentText)
                putExtra("title", hint)
                putExtra("cursorPosition", view.selectionStart)
                putExtra("sourceType", "bookSource")
                putExtra("sourceKey", getSource().bookSourceUrl)
                putExtra("fieldKey", fieldKey)
                putExtra("tabKey", tabKey)
            }
            textEditLauncher.launch(intent)
        }
        else {
            toastOnUi(R.string.please_focus_cursor_on_textbox)
        }
    }

    /**
     * 获取当前选中的Tab对应的key
     * @return tabKey: "base", "search", "explore", "info", "toc", "content"
     */
    private fun getCurrentTabKey(): String {
        return when (binding.tabLayout.selectedTabPosition) {
            1 -> "search"
            2 -> "explore"
            3 -> "info"
            4 -> "toc"
            5 -> "content"
            else -> "base"
        }
    }

    /**
     * 处理选项菜单项点击事件
     */
    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_fullscreen_edit -> onFullEditClicked()

            R.id.menu_edit_json -> showSourceJsonEdit()

            R.id.menu_save -> {
                saveSource(getSource()) {
                    setResult(RESULT_OK, Intent().putExtra("origin", it.bookSourceUrl))
                    finish()
                }
            }

            R.id.menu_debug_source -> saveSource(getSource()) { source ->
                startActivity<BookSourceDebugActivity> {
                    putExtra("key", source.bookSourceUrl)
                }
            }

            R.id.menu_clear_cookie -> viewModel.clearCookie(getSource().bookSourceUrl)
            R.id.menu_auto_complete -> viewModel.autoComplete = !viewModel.autoComplete
            R.id.menu_copy_source -> sendToClip(GSON.toJson(getSource()))
            R.id.menu_paste_source -> viewModel.pasteSource { upSourceView(it) }
            R.id.menu_qr_code_camera -> qrCodeResult.launch()
            R.id.menu_share_str -> share(GSON.toJson(getSource()))
            R.id.menu_share_qr -> shareWithQr(
                GSON.toJson(getSource()),
                getString(R.string.share_book_source),
                ErrorCorrectionLevel.L
            )

            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_help -> showHelp("ruleHelp")
            R.id.menu_login -> saveSource(getSource()) { source ->
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", source.bookSourceUrl)
                }
            }

            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_search -> saveSource(getSource()) { source ->
                SearchActivity.start(this, source)
            }

        }
        return super.onCompatOptionsItemSelected(item)
    }

    /**
     * 初始化界面视图
     * 创建Tab页签、配置RecyclerView、设置窗口边距适配等
     */
    private fun initView() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            setText(R.string.source_tab_base)
        })
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            setText(R.string.source_tab_search)
        })
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            setText(R.string.source_tab_find)
        })
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            setText(R.string.source_tab_info)
        })
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            setText(R.string.source_tab_toc)
        })
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            setText(R.string.source_tab_content)
        })
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        if (adapter.editEntityMaxLine < 999) {
            binding.recyclerView.layoutManager = NoChildScrollLinearLayoutManager(this)
        }
        binding.recyclerView.adapter = adapter
        binding.recyclerView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus is EditText) {
                newFocus.postDelayed({ sendText("") }, ReadConstants.EDIT_FOCUS_DELAY_MS)
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
        binding.cbNextPageLazyLoad.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                val nextContentUrl = contentEntities.find { it.key == "nextContentUrl" }?.value
                if (nextContentUrl.isNullOrBlank()) {
                    toastOnUi(
                        "未填正文下一页规则",
                        ToastContext(
                            activityName = "BookSourceEditActivity",
                            sourceName = viewModel.bookSource?.bookSourceName,
                            sourceType = ToastSourceType.BOOK,
                            ruleType = ToastRuleType.CONTENT
                        )
                    )
                    buttonView.isChecked = false
                }
            }
        }
    }

    /**
     * 返回键退出逻辑
     * 如果有未保存的修改，弹出确认对话框
     */
    override fun finish() {
        if (isSaving) {
            // 保存正在进行中，阻止退出，避免协程被取消导致数据丢失
            toastOnUi(R.string.save_ing)
            return
        }
        val source = getSource()
        if (!source.equal(viewModel.bookSource ?: BookSource())) {
            alert(R.string.exit) {
                setMessage(R.string.exit_no_save)
                positiveButton(R.string.yes)
                negativeButton(R.string.no) {
                    super.finish()
                }
            }
        } else {
            // 数据与 viewModel.bookSource 一致，说明保存已成功（execute 块已执行 bookSource = source）。
            // 但 Coroutine 的 onSuccess 回调可能因竞态未触发，导致 setResult(RESULT_OK) 未调用。
            // 此处仅当本次会话确实保存过书源（bookSource 引用已更新）时补设 RESULT_OK，
            // 确保调用方（如 ReadBookActivity）能刷新书源缓存。
            // 若用户未修改直接返回（引用未变化），保持默认 RESULT_CANCELED，
            // 避免误触发详情页 refreshBook、换源对话框自动搜索等刷新逻辑。
            if (viewModel.bookSource !== initialSource) {
                viewModel.bookSource?.let {
                    setResult(RESULT_OK, Intent().putExtra("origin", it.bookSourceUrl))
                }
            }
            super.finish()
        }
    }

    /**
     * Activity销毁时释放资源
     */
    override fun onDestroy() {
        super.onDestroy()
        softKeyboardTool.dismiss()
    }

    /**
     * 根据Tab页签位置切换对应的编辑实体列表
     * @param tabPosition Tab索引：0=基本信息, 1=搜索, 2=发现, 3=详情, 4=目录, 5=正文
     */
    private fun setEditEntities(tabPosition: Int?) {
        adapter.editEntities = when (tabPosition) {
            1 -> searchEntities
            2 -> exploreEntities
            3 -> infoEntities
            4 -> tocEntities
            5 -> contentEntities
//            6 -> reviewEntities
            else -> sourceEntities
        }
        binding.recyclerView.scrollToPosition(0)
        window.decorView.rootView.clearFocus()
    }

    /**
     * 将书源数据绑定到界面各Tab页签的编辑列表中
     * @param bookSource 要显示的书源对象
     */
    private fun upSourceView(bookSource: BookSource?) {
        val bs = bookSource ?: BookSource()
        bs.let {
            binding.cbIsEnable.isChecked = it.enabled
            binding.cbIsEnableExplore.isChecked = it.enabledExplore
            binding.cbIsEnableCookie.isChecked = it.enabledCookieJar ?: false
            binding.spType.setSelection(
                when (it.bookSourceType) {
                    BookSourceType.video -> 4
                    BookSourceType.file -> 3
                    BookSourceType.image -> 2
                    BookSourceType.audio -> 1
                    else -> 0
                }
            )
            binding.cbIsEventListener.isChecked = it.eventListener
            binding.cbIsCustomButton.isChecked = it.customButton
        }
        // 基本信息
        sourceEntities.clear()
        sourceEntities.apply {
            add(EditEntity("bookSourceUrl", bs.bookSourceUrl, R.string.source_url))
            add(EditEntity("bookSourceName", bs.bookSourceName, R.string.source_name))
            add(EditEntity("bookSourceGroup", bs.bookSourceGroup, R.string.source_group))
            add(EditEntity("bookSourceComment", bs.bookSourceComment, R.string.comment))
            add(EditEntity("loginUrl", bs.loginUrl, R.string.login_url))
            add(EditEntity("loginUi", bs.loginUi, R.string.login_ui))
            add(EditEntity("loginCheckJs", bs.loginCheckJs, R.string.login_check_js))
            add(EditEntity("coverDecodeJs", bs.coverDecodeJs, R.string.cover_decode_js))
            add(EditEntity("bookUrlPattern", bs.bookUrlPattern, R.string.book_url_pattern))
            add(EditEntity("header", bs.header, R.string.source_http_header))
            add(EditEntity("variableComment", bs.variableComment, R.string.variable_comment))
            add(EditEntity("concurrentRate", bs.concurrentRate, R.string.concurrent_rate))
            add(EditEntity("jsLib", bs.jsLib, "jsLib"))
        }
        // 搜索
        val sr = bs.getSearchRule()
        searchEntities.clear()
        searchEntities.apply {
            add(EditEntity("searchUrl", bs.searchUrl, R.string.r_search_url))
            add(EditEntity("checkKeyWord", sr.checkKeyWord, R.string.check_key_word))
            add(EditEntity("bookList", sr.bookList, R.string.r_book_list))
            add(EditEntity("name", sr.name, R.string.r_book_name))
            add(EditEntity("author", sr.author, R.string.r_author))
            add(EditEntity("kind", sr.kind, R.string.rule_book_kind))
            add(EditEntity("wordCount", sr.wordCount, R.string.rule_word_count))
            add(EditEntity("lastChapter", sr.lastChapter, R.string.rule_last_chapter))
            add(EditEntity("intro", sr.intro, R.string.rule_book_intro))
            add(EditEntity("coverUrl", sr.coverUrl, R.string.rule_cover_url))
            add(EditEntity("bookUrl", sr.bookUrl, R.string.r_book_url))
        }
        // 发现
        val er = bs.getExploreRule()
        exploreEntities.clear()
        exploreEntities.apply {
            add(EditEntity("exploreUrl", bs.exploreUrl, R.string.r_find_url))
            add(EditEntity("bookList", er.bookList, R.string.r_book_list))
            add(EditEntity("name", er.name, R.string.r_book_name))
            add(EditEntity("author", er.author, R.string.r_author))
            add(EditEntity("kind", er.kind, R.string.rule_book_kind))
            add(EditEntity("wordCount", er.wordCount, R.string.rule_word_count))
            add(EditEntity("lastChapter", er.lastChapter, R.string.rule_last_chapter))
            add(EditEntity("intro", er.intro, R.string.rule_book_intro))
            add(EditEntity("coverUrl", er.coverUrl, R.string.rule_cover_url))
            add(EditEntity("bookUrl", er.bookUrl, R.string.r_book_url))
        }
        // 详情页
        val ir = bs.getBookInfoRule()
        infoEntities.clear()
        infoEntities.apply {
            add(EditEntity("init", ir.init, R.string.rule_book_info_init))
            add(EditEntity("name", ir.name, R.string.r_book_name))
            add(EditEntity("author", ir.author, R.string.r_author))
            add(EditEntity("kind", ir.kind, R.string.rule_book_kind))
            add(EditEntity("wordCount", ir.wordCount, R.string.rule_word_count))
            add(EditEntity("lastChapter", ir.lastChapter, R.string.rule_last_chapter))
            add(EditEntity("intro", ir.intro, R.string.rule_book_intro))
            add(EditEntity("coverUrl", ir.coverUrl, R.string.rule_cover_url))
            add(EditEntity("tocUrl", ir.tocUrl, R.string.rule_toc_url))
            add(EditEntity("canReName", ir.canReName, R.string.rule_can_re_name))
            add(EditEntity("downloadUrls", ir.downloadUrls, R.string.download_url_rule))
        }
        // 目录页
        val tr = bs.getTocRule()
        tocEntities.clear()
        tocEntities.apply {
            add(EditEntity("preUpdateJs", tr.preUpdateJs, R.string.pre_update_js))
            add(EditEntity("chapterList", tr.chapterList, R.string.rule_chapter_list))
            add(EditEntity("chapterName", tr.chapterName, R.string.rule_chapter_name))
            add(EditEntity("chapterUrl", tr.chapterUrl, R.string.rule_chapter_url))
            add(EditEntity("formatJs", tr.formatJs, R.string.format_js_rule))
            add(EditEntity("isVolume", tr.isVolume, R.string.rule_is_volume))
            add(EditEntity("updateTime", tr.updateTime, R.string.rule_update_time))
            add(EditEntity("isVip", tr.isVip, R.string.rule_is_vip))
            add(EditEntity("isPay", tr.isPay, R.string.rule_is_pay))
            add(EditEntity("nextTocUrl", tr.nextTocUrl, R.string.rule_next_toc_url))
        }
        // 正文页
        val cr = bs.getContentRule()
        contentEntities.clear()
        contentEntities.apply {
            add(EditEntity("content", cr.content, R.string.rule_book_content))
            add(EditEntity("nextContentUrl", cr.nextContentUrl, R.string.rule_next_content))
            add(EditEntity("subContent", cr.subContent, R.string.rule_sub_content))
            add(EditEntity("replaceRegex", cr.replaceRegex, R.string.rule_replace_regex))
            add(EditEntity("title", cr.title, R.string.rule_chapter_name))
            add(EditEntity("sourceRegex", cr.sourceRegex, R.string.rule_source_regex))
            add(EditEntity("imageStyle", cr.imageStyle, R.string.rule_image_style))
            add(EditEntity("imageDecode", cr.imageDecode, R.string.rule_image_decode))
            add(EditEntity("webJs", cr.webJs, R.string.rule_web_js))
            add(EditEntity("payAction", cr.payAction, R.string.rule_pay_action))
            add(EditEntity("callBackJs", cr.callBackJs, R.string.rule_call_back))
        }
        binding.cbNextPageLazyLoad.isChecked = bs.nextPageLazyLoad
        // 段评
//        val rr = bs.getReviewRule()
//        reviewEntities.clear()
//        reviewEntities.apply {
//            add(EditEntity("reviewUrl", rr.reviewUrl, R.string.rule_review_url))
//            add(EditEntity("avatarRule", rr.avatarRule, R.string.rule_avatar))
//            add(EditEntity("contentRule", rr.contentRule, R.string.rule_review_content))
//            add(EditEntity("postTimeRule", rr.postTimeRule, R.string.rule_post_time))
//            add(EditEntity("reviewQuoteUrl", rr.reviewQuoteUrl, R.string.rule_review_quote))
//            add(EditEntity("voteUpUrl", rr.voteUpUrl, R.string.review_vote_up))
//            add(EditEntity("voteDownUrl", rr.voteDownUrl, R.string.review_vote_down))
//            add(EditEntity("postReviewUrl", rr.postReviewUrl, R.string.post_review_url))
//            add(EditEntity("postQuoteUrl", rr.postQuoteUrl, R.string.post_quote_url))
//            add(EditEntity("deleteUrl", rr.deleteUrl, R.string.delete_review_url))
//        }
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))
        setEditEntities(0)
    }

    /**
     * 保存书源。若书源 URL 发生变更且书架上有该书源的书，
     * 自动将关联书籍迁移到新 URL，并 toast 提醒用户。
     */
    private fun saveSource(
        source: BookSource,
        onSuccess: ((BookSource) -> Unit)? = null
    ) {
        isSaving = true
        val oldUrl = viewModel.bookSource?.bookSourceUrl
        val urlChanged = !oldUrl.isNullOrBlank() && oldUrl != source.bookSourceUrl
        viewModel.save(source, {
            isSaving = false
        }) { savedSource ->
            // 修复竞态：Coroutine 的 onSuccess 先于 onFinally 执行，
            // 若不在此先复位 isSaving，onSuccess 内触发的 finish() 会被
            // isSaving 保护拦截，导致保存成功后 Activity 不退出。
            isSaving = false
            if (urlChanged && oldUrl != null) {
                lifecycleScope.launch {
                    val hasBooks = withContext(IO) { appDb.bookDao.hasBookByOrigin(oldUrl) }
                    if (hasBooks) {
                        withContext(IO) {
                            appDb.bookDao.updateOrigin(oldUrl, savedSource.bookSourceUrl)
                        }
                        toastOnUi(R.string.migrate_book_origin_done)
                    }
                    onSuccess?.invoke(savedSource)
                }
            } else {
                onSuccess?.invoke(savedSource)
            }
        }
    }

    /**
     * 从界面各Tab页签中收集书源数据并构建BookSource对象
     * @return 构建好的书源对象
     */
    private fun getSource(): BookSource {
        val source = viewModel.bookSource?.copy() ?: BookSource()
        source.enabled = binding.cbIsEnable.isChecked
        source.enabledExplore = binding.cbIsEnableExplore.isChecked
        source.enabledCookieJar = binding.cbIsEnableCookie.isChecked
        source.bookSourceType = when (binding.spType.selectedItemPosition) {
            4 -> BookSourceType.video
            3 -> BookSourceType.file
            2 -> BookSourceType.image
            1 -> BookSourceType.audio
            else -> BookSourceType.default
        }
        source.eventListener = binding.cbIsEventListener.isChecked
        source.customButton = binding.cbIsCustomButton.isChecked
        source.nextPageLazyLoad = binding.cbNextPageLazyLoad.isChecked
        val searchRule = SearchRule()
        val exploreRule = ExploreRule()
        val bookInfoRule = BookInfoRule()
        val tocRule = TocRule()
        val contentRule = ContentRule()
//        val reviewRule = ReviewRule()
        sourceEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "bookSourceUrl" -> source.bookSourceUrl = it.value ?: ""
                "bookSourceName" -> source.bookSourceName = it.value ?: ""
                "bookSourceGroup" -> source.bookSourceGroup = it.value
                "loginUrl" -> source.loginUrl = it.value
                "loginUi" -> source.loginUi = it.value
                "loginCheckJs" -> source.loginCheckJs = it.value
                "coverDecodeJs" -> source.coverDecodeJs = it.value
                "bookUrlPattern" -> source.bookUrlPattern = it.value
                "header" -> source.header = it.value
                "bookSourceComment" -> source.bookSourceComment = it.value
                "concurrentRate" -> source.concurrentRate = it.value
                "variableComment" -> source.variableComment = it.value
                "jsLib" -> source.jsLib = it.value
            }
        }
        searchEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "searchUrl" -> source.searchUrl = it.value
                "checkKeyWord" -> searchRule.checkKeyWord = it.value
                "bookList" -> searchRule.bookList = it.value
                "name" -> searchRule.name =
                    viewModel.ruleComplete(it.value, searchRule.bookList)

                "author" -> searchRule.author =
                    viewModel.ruleComplete(it.value, searchRule.bookList)

                "kind" -> searchRule.kind =
                    viewModel.ruleComplete(it.value, searchRule.bookList)

                "intro" -> searchRule.intro =
                    viewModel.ruleComplete(it.value, searchRule.bookList)

//                "updateTime" -> searchRule.updateTime =
//                    viewModel.ruleComplete(it.value, searchRule.bookList)

                "wordCount" -> searchRule.wordCount =
                    viewModel.ruleComplete(it.value, searchRule.bookList)

                "lastChapter" -> searchRule.lastChapter =
                    viewModel.ruleComplete(it.value, searchRule.bookList)

                "coverUrl" -> searchRule.coverUrl =
                    viewModel.ruleComplete(it.value, searchRule.bookList, 3)

                "bookUrl" -> searchRule.bookUrl =
                    viewModel.ruleComplete(it.value, searchRule.bookList, 2)
            }
        }
        exploreEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "exploreUrl" -> source.exploreUrl = it.value
                "bookList" -> exploreRule.bookList = it.value
                "name" -> exploreRule.name =
                    viewModel.ruleComplete(it.value, exploreRule.bookList)

                "author" -> exploreRule.author =
                    viewModel.ruleComplete(it.value, exploreRule.bookList)

                "kind" -> exploreRule.kind =
                    viewModel.ruleComplete(it.value, exploreRule.bookList)

                "intro" -> exploreRule.intro =
                    viewModel.ruleComplete(it.value, exploreRule.bookList)

//                "updateTime" -> exploreRule.updateTime =
//                    viewModel.ruleComplete(it.value, exploreRule.bookList)

                "wordCount" -> exploreRule.wordCount =
                    viewModel.ruleComplete(it.value, exploreRule.bookList)

                "lastChapter" -> exploreRule.lastChapter =
                    viewModel.ruleComplete(it.value, exploreRule.bookList)

                "coverUrl" -> exploreRule.coverUrl =
                    viewModel.ruleComplete(it.value, exploreRule.bookList, 3)

                "bookUrl" -> exploreRule.bookUrl =
                    viewModel.ruleComplete(it.value, exploreRule.bookList, 2)
            }
        }
        infoEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "init" -> bookInfoRule.init = it.value
                "name" -> bookInfoRule.name = viewModel.ruleComplete(it.value, bookInfoRule.init)
                "author" -> bookInfoRule.author =
                    viewModel.ruleComplete(it.value, bookInfoRule.init)

                "kind" -> bookInfoRule.kind =
                    viewModel.ruleComplete(it.value, bookInfoRule.init)

                "intro" -> bookInfoRule.intro =
                    viewModel.ruleComplete(it.value, bookInfoRule.init)

//                "updateTime" -> bookInfoRule.updateTime =
//                    viewModel.ruleComplete(it.value, bookInfoRule.init)

                "wordCount" -> bookInfoRule.wordCount =
                    viewModel.ruleComplete(it.value, bookInfoRule.init)

                "lastChapter" -> bookInfoRule.lastChapter =
                    viewModel.ruleComplete(it.value, bookInfoRule.init)

                "coverUrl" -> bookInfoRule.coverUrl =
                    viewModel.ruleComplete(it.value, bookInfoRule.init, 3)

                "tocUrl" -> bookInfoRule.tocUrl =
                    viewModel.ruleComplete(it.value, bookInfoRule.init, 2)

                "canReName" -> bookInfoRule.canReName = it.value
                "downloadUrls" -> bookInfoRule.downloadUrls =
                    viewModel.ruleComplete(it.value, bookInfoRule.init)
            }
        }
        tocEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "preUpdateJs" -> tocRule.preUpdateJs = it.value
                "chapterList" -> tocRule.chapterList = it.value
                "chapterName" -> tocRule.chapterName =
                    viewModel.ruleComplete(it.value, tocRule.chapterList)

                "chapterUrl" -> tocRule.chapterUrl =
                    viewModel.ruleComplete(it.value, tocRule.chapterList, 2)

                "formatJs" -> tocRule.formatJs = it.value
                "isVolume" -> tocRule.isVolume = it.value
                "updateTime" -> tocRule.updateTime = it.value
                "isVip" -> tocRule.isVip = it.value
                "isPay" -> tocRule.isPay = it.value
                "nextTocUrl" -> tocRule.nextTocUrl =
                    viewModel.ruleComplete(it.value, tocRule.chapterList, 2)
            }
        }
        contentEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "content" -> contentRule.content = viewModel.ruleComplete(it.value)
                "nextContentUrl" -> contentRule.nextContentUrl =
                    viewModel.ruleComplete(it.value, type = 2)
                "subContent" -> contentRule.subContent = viewModel.ruleComplete(it.value)
                "title" -> contentRule.title = viewModel.ruleComplete(it.value)

                "webJs" -> contentRule.webJs = it.value
                "sourceRegex" -> contentRule.sourceRegex = it.value
                "replaceRegex" -> contentRule.replaceRegex = it.value
                "imageStyle" -> contentRule.imageStyle = it.value
                "imageDecode" -> contentRule.imageDecode = it.value
                "payAction" -> contentRule.payAction = it.value
                "callBackJs" -> contentRule.callBackJs = it.value
            }
        }
//        reviewEntities.forEach {
//            when (it.key) {
//                "reviewUrl" -> reviewRule.reviewUrl = it.value
//                "avatarRule" -> reviewRule.avatarRule =
//                    viewModel.ruleComplete(it.value, reviewRule.reviewUrl, 3)
//
//                "contentRule" -> reviewRule.contentRule =
//                    viewModel.ruleComplete(it.value, reviewRule.reviewUrl)
//
//                "postTimeRule" -> reviewRule.postTimeRule =
//                    viewModel.ruleComplete(it.value, reviewRule.reviewUrl)
//
//                "reviewQuoteUrl" -> reviewRule.reviewQuoteUrl =
//                    viewModel.ruleComplete(it.value, reviewRule.reviewUrl, 2)
//
//                "voteUpUrl" -> reviewRule.voteUpUrl = it.value
//                "voteDownUrl" -> reviewRule.voteDownUrl = it.value
//                "postReviewUrl" -> reviewRule.postReviewUrl = it.value
//                "postQuoteUrl" -> reviewRule.postQuoteUrl = it.value
//                "deleteUrl" -> reviewRule.deleteUrl = it.value
//            }
//        }
        source.ruleSearch = searchRule
        source.ruleExplore = exploreRule
        source.ruleBookInfo = bookInfoRule
        source.ruleToc = tocRule
        source.ruleContent = contentRule
//        source.ruleReview = reviewRule
        return source
    }

    /**
     * 弹出分组选择对话框
     */
    private fun alertGroups() {
        lifecycleScope.launch {
            val groups = withContext(IO) {
                appDb.bookSourceDao.allGroups()
            }
            selector(groups) { _, s, _ ->
                sendText(s)
            }
        }
    }

    /**
     * 根据当前焦点的文本框类型动态生成帮助操作列表
     * 例如：分组字段显示"插入分组"，其他字段显示"选择文件"
     */
    override fun helpActions(): List<SelectItem<String>> {
        val helpActions = arrayListOf(
            SelectItem("插入URL参数", "urlOption"),
            SelectItem("书源教程", "ruleHelp"),
            SelectItem("js教程", "jsHelp"),
            SelectItem("正则教程", "regexHelp"),
        )
        val view = window.decorView.findFocus()
        if (view is EditText) {
            when (view.getTag(R.id.tag)) {
                "bookSourceGroup" -> {
                    helpActions.add(
                        SelectItem("插入分组", "addGroup")
                    )
                }

                else -> {
                    helpActions.add(
                        SelectItem("选择文件", "selectFile")
                    )
                }
            }
        }
        return helpActions
    }

    /**
     * 处理帮助操作的选择事件
     * @param action 操作标识符
     */
    override fun onHelpActionSelect(action: String) {
        when (action) {
            "addGroup" -> alertGroups()
            "urlOption" -> UrlOptionDialog(this) { sendText(it) }.show()
            "ruleHelp" -> showHelp("ruleHelp")
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
            if (adapter.editEntityMaxLine >= 999) {
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

    private fun setSourceVariable() {
        saveSource(getSource()) { source ->
            lifecycleScope.launch {
                val comment =
                    source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
                val variable = withContext(IO) { source.getVariable() }
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

    private fun showSourceJsonEdit() {
        val source = getSource()
        val json = GSON.toJson(source)
        showDialogFragment(SourceJsonEditDialog(json) { newJson ->
            try {
                val newSource = GSON.fromJsonObject<BookSource>(newJson).getOrThrow()
                upSourceView(newSource)
            } catch (e: Exception) {
                toastOnUi(R.string.json_format)
            }
        })
    }

    override fun setVariable(key: String, variable: String?) {
        viewModel.bookSource?.setVariable(variable)
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
