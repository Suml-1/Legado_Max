package io.legado.app.ui.code

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.util.regex.RegexBackrefGrammar
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ActivityCodeEditBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.dialogs.AndroidAlertBuilder
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.jsSource.JsSourceUpsert
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.source.manage.SourceContentSearchDialog
import io.legado.app.ui.code.config.ChangeThemeDialog
import io.legado.app.ui.code.config.SettingsDialog
import io.legado.app.ui.widget.keyboard.KeyboardToolPop
import io.legado.app.utils.imeHeight
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch

/**
 * 代码编辑活动
 * 提供代码编辑功能，支持语法高亮、搜索替换、格式化等
 * 使用 Sora Editor 作为编辑器核心，支持 TextMate 语法高亮
 */
class CodeEditActivity :
    VMBaseActivity<ActivityCodeEditBinding, CodeEditViewModel>(),
    KeyboardToolPop.CallBack, ChangeThemeDialog.CallBack, SettingsDialog.CallBack {
    companion object {
        /**
         * 传入该 extra 表示「直接编辑某个 JS 书源的主脚本」：
         * 初始文本来自数据库，保存时由本页调用 JsSourceUpsert 写库并结束页面。
         */
        const val EXTRA_JS_SOURCE_URL = "jsSourceUrl"
        private var isInitialized = false
        private var findText = ""
        private var replaceText = ""
        private var isRegex = true
    }
    override val binding by viewBinding(ActivityCodeEditBinding::inflate)
    override val viewModel by viewModels<CodeEditViewModel>()
    private val softKeyboardTool by lazy {
        KeyboardToolPop(this, lifecycleScope, binding.root, this)
    }
    private val editor: CodeEditor by lazy { binding.editText }
    private val editorSearcher: EditorSearcher by lazy { editor.searcher }
    private var searchOptions: SearchOptions? = null
    private var menuSaveBtn: MenuItem? = null

    private val isDark
        get() = AppConfig.editTemeAuto && ThemeConfig.isDarkTheme()
    private var themeIndex = -1

    /**
     * 活动创建时初始化
     * 配置编辑器、加载文本内容、设置光标位置
     */
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        softKeyboardTool.attachToWindow(window)
        editor.colorScheme = TextMateColorScheme2.create(ThemeRegistry.getInstance()) //先设置颜色,避免一开始的白屏
        viewModel.initData(intent) {
            editor.apply {
                viewModel.title?.let {
                    binding.titleBar.title = it
                }
                nonPrintablePaintingFlags = AppConfig.editNonPrintable
                setEditorLanguage(viewModel.language)
                upEdit(AppConfig.editFontScale, null, AppConfig.editAutoWrap)
                setText(viewModel.initialText)
                editable = viewModel.writable
                menuSaveBtn?.isVisible = viewModel.writable
                requestFocus()
                postDelayed({
                    val pos = cursor.indexer.getCharPosition(viewModel.cursorPosition)
                    setSelection(pos.line, pos.column, true)
                }, 360) // 进行延时,确保加载渲染完成,从而确保光标能显示跳转到长文本最后
            }
        }
        initView()
    }

    /**
     * 初始化视图
     * 设置软键盘监听器
     */
    private fun initView() {
        binding.root.setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
            softKeyboardTool.initialPadding = windowInsets.imeHeight
            windowInsets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        editorSearcher.stopSearch()
        editor.release()
    }

    /**
     * 使用super.finish(),防止循环回调
     * 保存编辑内容并退出
     * @param check 是否检查未保存更改
     */
    private fun save(check: Boolean) {
        if (!viewModel.writable) return super.finish()
        val text = editor.text.toString()
        // JS 书源编辑模式：保存 = 写库，不把文本回传给调用方
        if (viewModel.jsSourceUrl != null) {
            saveJsSource(text, check)
            return
        }
        val cursorPos = editor.cursor?.left ?: 0
        // 获取当前编辑的字段标识和板块标识，用于返回时告诉调用者更新哪个字段
        val fieldKey = viewModel.fieldKey
        val tabKey = viewModel.tabKey
        when {
            // 内容没有变化，直接退出
            text == viewModel.initialText -> {
                if (cursorPos > 0 || !fieldKey.isNullOrEmpty() && !tabKey.isNullOrEmpty()) {
                    val result = Intent().apply {
                        putExtra("cursorPosition", cursorPos)
                        putExtra("fieldKey", fieldKey)
                        putExtra("tabKey", tabKey)
                    }
                    setResult(RESULT_OK, result)
                }
                super.finish()
            }
            // 需要检查未保存更改，弹出确认对话框
            check -> {
                alert(R.string.exit) {
                    setMessage(R.string.exit_no_save)
                    positiveButton(R.string.yes)
                    negativeButton(R.string.no) {
                        if (cursorPos > 0 || !fieldKey.isNullOrEmpty() && !tabKey.isNullOrEmpty()) {
                            val result = Intent().apply {
                                putExtra("cursorPosition", cursorPos)
                                putExtra("fieldKey", fieldKey)
                                putExtra("tabKey", tabKey)
                            }
                            setResult(RESULT_OK, result)
                        }
                        super.finish()
                    }
                }
            }
            // 保存内容并返回
            else -> {
                val result = Intent().apply {
                    CodeEditLauncher.putText(this, text) // 编辑后的文本(超阈值走内存中转,小文本直传)
                    putExtra("cursorPosition", cursorPos) // 光标位置
                    putExtra("fieldKey", fieldKey)       // 字段标识，用于定位要更新的字段
                    putExtra("tabKey", tabKey)           // 板块标识，用于定位要更新的列表
                }
                setResult(RESULT_OK, result)
                super.finish()
            }
        }
    }

    /**
     * 保存 JS 书源主脚本。
     *
     * 写库前会重新执行脚本提取 `config`（[JsSourceUpsert.save]），校验不过就留在编辑页并提示原因，
     * 避免把跑不起来的脚本存进书源列表；成功时把书源 URL 回传给列表页刷新。
     *
     * @param script 编辑器当前内容
     * @param check 是否为「返回键退出」路径（true 时先确认是否放弃修改）
     */
    private fun saveJsSource(script: String, check: Boolean) {
        if (script == viewModel.initialText) {
            super.finish()
            return
        }
        if (check) {
            alert(R.string.exit) {
                setMessage(R.string.exit_no_save)
                positiveButton(R.string.yes)
                negativeButton(R.string.no) { super.finish() }
            }
            return
        }
        val sourceUrl = viewModel.jsSourceUrl ?: return
        lifecycleScope.launch {
            runCatching { JsSourceUpsert.save(script, sourceUrl) }
                .onSuccess {
                    viewModel.initialText = script
                    setResult(RESULT_OK, Intent().putExtra("origin", it.bookSourceUrl))
                    super.finish()
                }
                .onFailure {
                    toastOnUi(
                        getString(R.string.js_source_save_failed)
                            .format(it.localizedMessage.orEmpty())
                    )
                    AppLog.put("JS书源保存失败:${it.localizedMessage}", it)
                }
        }
    }

    /**
     * 更新编辑器设置
     * @param fontSize 字体大小
     * @param autoComplete 是否启用自动补全
     * @param autoWarp 是否启用自动换行
     * @param editNonPrintable 不可见字符显示标志
     */
    override fun upEdit(fontSize: Int?, autoComplete: Boolean?, autoWarp: Boolean?, editNonPrintable: Int?) {
        if (fontSize != null) {
            editor.setTextSize(fontSize.toFloat())
        }
        if (autoComplete != null) {
            viewModel.language?.isAutoCompleteEnabled = autoComplete
            editor.setEditorLanguage(viewModel.language)
        }
        if (autoWarp != null) {
            editor.isWordwrap = autoWarp
        }
        if (editNonPrintable != null) {
            editor.nonPrintablePaintingFlags = editNonPrintable
        }
    }

    /**
     * 初始化主题
     * 根据系统主题自动切换编辑器主题
     */
    override fun initTheme() {
        super.initTheme()
        if (!isInitialized) {
            viewModel.initSora()
            isInitialized = true
        }
        val index = if (isDark) {
            AppConfig.editThemeDark
        } else {
            AppConfig.editTheme
        }
        upTheme(index)
        themeIndex = index
    }

    /**
     * 更新编辑器主题
     * @param index 主题索引
     */
    override fun upTheme(index: Int) {
        if (themeIndex != index) {
            viewModel.loadTextMateThemes(index)
            editor.setEditorLanguage(viewModel.language) //每次更改颜色后需要再执行一次语言设置,防止切换主题后高亮颜色不正确
            themeIndex = index
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.code_edit_activity, menu)
        menuSaveBtn = menu.findItem(R.id.menu_save).apply {
            isVisible = viewModel.writable
        }
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_auto_wrap)?.isChecked = AppConfig.editAutoWrap
        return super.onPrepareOptionsMenu(menu)
    }

    /**
     * 设置搜索选项
     * 根据是否使用正则表达式配置搜索参数
     */
    private fun setSearchOptions() {
        searchOptions =  SearchOptions(
            if (isRegex) SearchOptions.TYPE_REGULAR_EXPRESSION else SearchOptions.TYPE_NORMAL,
            !isRegex,
            RegexBackrefGrammar.DEFAULT
        )
    }

    /**
     * 显示搜索界面
     * 配置搜索、替换功能的事件监听器
     */
    private fun search() {
        if (binding.searchGroup.isVisible) return
        binding.switchRegex.run {
            isChecked = isRegex
            setSearchOptions()
            setOnCheckedChangeListener { _, isChecked ->
                isRegex = isChecked
                setSearchOptions()
                searchTxt(binding.etFind.text.toString())
            }
        }
        val receiptSearch =
            editor.subscribeEvent(PublishSearchResultEvent::class.java) { event, _ ->
                if (event.editor == editor) {
                    updateSearchResults()
                }
            }
        val receiptChange = editor.subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
            if (event.cause == SelectionChangeEvent.CAUSE_SEARCH) {
                updateSearchResults()
            }
        }
        binding.searchGroup.visibility = View.VISIBLE
        binding.btnCloseFind.setOnClickListener {
            binding.searchGroup.visibility = View.GONE
            editorSearcher.stopSearch()
            receiptSearch.unsubscribe()
            receiptChange.unsubscribe()
            editor.requestFocus()
            editor.invalidate()
        }
        searchTxt(findText)
        binding.etFind.run {
            requestFocus()
            setText(findText)
            addTextChangedListener { text ->
                if (!text.isNullOrEmpty()) {
                    findText = text.toString()
                    searchTxt(findText)
                } else {
                    editorSearcher.stopSearch()
                    editor.invalidate()
                }
            }

        }
        binding.etReplace.run {
            setText(replaceText)
            addTextChangedListener { text ->
                if (!text.isNullOrEmpty()) {
                    replaceText = text.toString()
                }
            }
        }
        binding.btnPrevious.setOnClickListener {
            if (editorSearcher.hasQuery()) {
                editorSearcher.gotoPrevious()
            }
        }
        binding.btnNext.setOnClickListener {
            if (editorSearcher.hasQuery()) {
                editorSearcher.gotoNext()
            }
        }
        binding.btnReplace.setOnClickListener {
            if (binding.replaceGroup.isGone) {
                binding.replaceGroup.visibility = View.VISIBLE
                binding.btnReplaceAll.isEnabled = true
                binding.etReplace.requestFocus()
            } else {
                if (editorSearcher.hasQuery()) {
                    editorSearcher.replaceCurrentMatch(binding.etReplace.text.toString())
                }
            }
        }
        binding.btnCloseReplace.setOnClickListener {
            binding.replaceGroup.visibility = View.GONE
            binding.btnReplaceAll.isEnabled = false
            binding.etFind.requestFocus()
        }
        binding.btnReplaceAll.setOnClickListener {
            if (editorSearcher.hasQuery()) {
                editorSearcher.replaceAll(binding.etReplace.text.toString())
            }
        }
    }

    /**
     * 执行搜索
     * @param txt 搜索文本
     */
    private fun searchTxt(txt: String) {
        if (txt.isNotEmpty()) {
            try {
                searchOptions?.let {
                    editorSearcher.search(txt, it)
                }
            } catch (_: java.util.regex.PatternSyntaxException) {
                // 忽略正则表达式语法错误
                editorSearcher.stopSearch()
                editor.invalidate()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    /**
     * 更新搜索结果显示
     * 显示当前匹配位置和总匹配数
     */
    private fun updateSearchResults() {
        if (editorSearcher.hasQuery()) {
            val totalResults = editorSearcher.matchedPositionCount
            val currentPosition = editorSearcher.currentMatchedPositionIndex + 1
            binding.tvSearchResult.text =
                "${if (currentPosition > 0) "$currentPosition/" else ""}$totalResults"
        }
    }

    /**
     * 处理菜单选项点击事件
     * @param item 被点击的菜单项
     * @return 是否消耗了事件
     */
    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_search -> search()
            R.id.menu_save -> save(false)
            R.id.menu_format_code -> viewModel.formatCode(editor)
            R.id.menu_change_theme -> showDialogFragment(ChangeThemeDialog())
            R.id.menu_grammar -> showGrammarSelectDialog()
            R.id.menu_config_settings -> showDialogFragment(SettingsDialog(this, this))
            R.id.menu_auto_wrap -> {
                item.isChecked = !AppConfig.editAutoWrap
                upEdit(autoWarp = !AppConfig.editAutoWrap)
                putPrefBoolean(PreferKey.editAutoWrap, !AppConfig.editAutoWrap)
            }
            R.id.menu_help -> {
                val actions = helpActions()
                selector(R.string.help, actions.map { it.title }) { _, which ->
                    onHelpActionSelect(actions[which].value)
                }
            }
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_switch_rule -> showSwitchRuleDialog()
            R.id.menu_search_rule -> showRuleSearchDialog()
            R.id.menu_source_content_query -> showDialogFragment<SourceContentSearchDialog>()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    /**
     * 显示语法选择对话框
     * 提供常用语言列表，选择后切换编辑器语法高亮
     * 使用单选对话框，当前选中的语言会显示打钩标记
     */
    private fun showGrammarSelectDialog() {
        val grammars = listOf(
            SelectItem("默认", viewModel.initialLanguageName),
            SelectItem("JavaScript", "source.js"),
            SelectItem("HTML", "text.html.basic"),
            SelectItem("Markdown", "text.html.markdown")
        )
        val titles = grammars.map { it.title }.toTypedArray()
        val currentIndex = grammars.indexOfFirst { it.value == viewModel.languageName }
        with(AndroidAlertBuilder(this)) {
            setTitle(R.string.grammar_select)
            singleChoiceItems(titles, currentIndex) { _, which ->
                val selected = grammars[which]
                if (selected.value != viewModel.languageName) {
                    viewModel.languageName = selected.value
                    viewModel.language = TextMateLanguage.create(selected.value, AppConfig.editAutoComplete)
                    editor.setEditorLanguage(viewModel.language)
                }
                this@CodeEditActivity.toastOnUi(selected.title)
            }
            show()
        }
    }

    /**
     * 显示规则搜索对话框
     * 搜索当前源中所有规则字段的内容
     */
    private fun showRuleSearchDialog() {
        val sourceJson = viewModel.sourceJson
        val sourceType = viewModel.sourceType
        if (sourceJson.isNullOrEmpty() || sourceType.isNullOrEmpty()) {
            return
        }
        showDialogFragment(RuleSearchDialog(sourceJson, sourceType) { tabKey, fieldKey, cursorPosition ->
            switchToField(tabKey, fieldKey, cursorPosition)
        })
    }

    /**
     * 显示切换规则对话框
     * 多级选择：源类型 -> 板块 -> 字段
     */
    private fun showSwitchRuleDialog() {
        val sourceType = viewModel.sourceType
        if (sourceType.isNullOrEmpty() || viewModel.sourceJson.isNullOrEmpty()) {
            return
        }
        when (sourceType) {
            "bookSource" -> showBookSourceRuleSelector()
            "rssSource" -> showRssSourceRuleSelector()
        }
    }

    /**
     * 书源规则选择器
     */
    private fun showBookSourceRuleSelector() {
        val tabs = listOf(
            SelectItem("基本", "base"),
            SelectItem("搜索", "search"),
            SelectItem("发现", "explore"),
            SelectItem("详情", "info"),
            SelectItem("目录", "toc"),
            SelectItem("正文", "content")
        )
        with(AndroidAlertBuilder(this)) {
            setTitle("切换规则")
            items(tabs.map { it.title }) { _, position ->
                showBookSourceFieldSelector(tabs[position].value)
            }
            show()
        }
    }

    /**
     * 书源字段选择器
     */
    private fun showBookSourceFieldSelector(tabKey: String) {
        val fields = when (tabKey) {
            "base" -> listOf(
                SelectItem("源地址", "bookSourceUrl"),
                SelectItem("源名称", "bookSourceName"),
                SelectItem("源分组", "bookSourceGroup"),
                SelectItem("源注释", "bookSourceComment"),
                SelectItem("登录地址", "loginUrl"),
                SelectItem("登录界面", "loginUi"),
                SelectItem("登录检查JS", "loginCheckJs"),
                SelectItem("封面解密JS", "coverDecodeJs"),
                SelectItem("书籍URL正则", "bookUrlPattern"),
                SelectItem("请求头", "header"),
                SelectItem("变量说明", "variableComment"),
                SelectItem("并发率", "concurrentRate"),
                SelectItem("jsLib", "jsLib")
            )
            "search" -> listOf(
                SelectItem("搜索地址", "searchUrl"),
                SelectItem("校验关键字", "checkKeyWord"),
                SelectItem("书籍列表", "bookList"),
                SelectItem("书名", "name"), 
                SelectItem("作者", "author"),
                SelectItem("分类", "kind"),
                SelectItem("字数", "wordCount"),
                SelectItem("最新章节", "lastChapter"),
                SelectItem("简介规则", "intro"),
                SelectItem("封面规则", "coverUrl"),
                SelectItem("书籍URL", "bookUrl")
            )
            "explore" -> listOf(
                SelectItem("发现地址", "exploreUrl"),
                SelectItem("书籍列表", "bookList"),
                SelectItem("书名", "name"),
                SelectItem("作者", "author"),
                SelectItem("分类", "kind"),
                SelectItem("字数", "wordCount"),
                SelectItem("最新章节", "lastChapter"),
                SelectItem("简介", "intro"),
                SelectItem("封面规则", "coverUrl"),
                SelectItem("书籍URL", "bookUrl")
            )
            "info" -> listOf(
                SelectItem("初始化", "init"),
                SelectItem("书名", "name"),
                SelectItem("作者", "author"),
                SelectItem("分类", "kind"),
                SelectItem("字数", "wordCount"),
                SelectItem("最新章节", "lastChapter"),
                SelectItem("简介", "intro"),
                SelectItem("封面规则", "coverUrl"),
                SelectItem("目录URL", "tocUrl"),
                SelectItem("允许修改书名作者", "canReName"),
                SelectItem("下载地址", "downloadUrls")
            )
            "toc" -> listOf(
                SelectItem("更新之前JS", "preUpdateJs"),
                SelectItem("目录列表规则", "chapterList"),
                SelectItem("章节名称", "chapterName"),
                SelectItem("章节URL", "chapterUrl"),
                SelectItem("格式化规则", "formatJs"),
                SelectItem("Volume标识", "isVolume"),
                SelectItem("更新时间", "updateTime"),
                SelectItem("是否VIP", "isVip"),
                SelectItem("购买标识", "isPay"),
                SelectItem("目录下一页规则", "nextTocUrl")
            )
            "content" -> listOf(
                SelectItem("正文规则", "content"),
                SelectItem("正文下一页URL规则", "nextContentUrl"),
                SelectItem("副文规则", "subContent"),
                SelectItem("替换正则", "replaceRegex"),
                SelectItem("章节名称规则", "title"),
                SelectItem("资源正则", "sourceRegex"),
                SelectItem("图片样式", "imageStyle"),
                SelectItem("图片解密", "imageDecode"),
                SelectItem("WebView JS", "webJs"),
                SelectItem("购买操作", "payAction"),
                SelectItem("回调操作", "callBackJs")
            )
            else -> emptyList()
        }
        if (fields.isEmpty()) return
        with(AndroidAlertBuilder(this)) {
            setTitle("选择字段")
            items(fields.map { it.title }) { _, position ->
                val fieldKey = fields[position].value
                switchToField(tabKey, fieldKey)
            }
            onCancelled {
                showBookSourceRuleSelector()
            }
            show()
        }
    }

    /**
     * 订阅源规则选择器
     */
    private fun showRssSourceRuleSelector() {
        val tabs = listOf(
            SelectItem("基本", "base"),
            SelectItem("启动", "start"),
            SelectItem("列表", "list"),
            SelectItem("WEB_VIEW", "webView")
        )
        with(AndroidAlertBuilder(this)) {
            setTitle("切换规则")
            items(tabs.map { it.title }) { _, position ->
                showRssSourceFieldSelector(tabs[position].value)
            }
            show()
        }
    }

    /**
     * 订阅源字段选择器
     */
    private fun showRssSourceFieldSelector(tabKey: String) {
        val fields = when (tabKey) {
            "base" -> listOf(
                SelectItem("源名称", "sourceName"),
                SelectItem("源URL", "sourceUrl"),
                SelectItem("图标", "sourceIcon"),
                SelectItem("源分组", "sourceGroup"),
                SelectItem("源注释", "sourceComment"),
                SelectItem("搜索地址", "searchUrl"),
                SelectItem("分类URL", "sortUrl"),
                SelectItem("登录URL", "loginUrl"),
                SelectItem("登录UI", "loginUi"),
                SelectItem("登录检查JS", "loginCheckJs"),
                SelectItem("封面解密", "coverDecodeJs"),
                SelectItem("请求头", "header"),
                SelectItem("变量说明", "variableComment"),
                SelectItem("并发率", "concurrentRate"),
                SelectItem("js库", "jsLib")
            )
            "start" -> listOf(
                SelectItem("起始页HTML", "startHtml"),
                SelectItem("起始页样式", "startStyle"),
                SelectItem("起始页JS", "startJs"),
                SelectItem("预加载JS", "preloadJs")
            )
            "list" -> listOf(
                SelectItem("列表规则", "ruleArticles"),
                SelectItem("列表下一页规则", "ruleNextArticles"),
                SelectItem("标题规则", "ruleTitle"),
                SelectItem("时间规则", "rulePubDate"),
                SelectItem("描述规则", "ruleDescription"),
                SelectItem("图片URL规则", "ruleImage"),
                SelectItem("链接规则", "ruleLink")
            )
            "webView" -> listOf(
                SelectItem("内容规则", "ruleContent"),
                SelectItem("样式", "style"),
                SelectItem("注入JS", "injectJs"),
                SelectItem("白名单", "contentWhitelist"),
                SelectItem("黑名单", "contentBlacklist"),
                SelectItem("URL跳转拦截", "shouldOverrideUrlLoading")
            )
            else -> emptyList()
        }
        if (fields.isEmpty()) return
        with(AndroidAlertBuilder(this)) {
            setTitle("选择字段")
            items(fields.map { it.title }) { _, position ->
                val fieldKey = fields[position].value
                switchToField(tabKey, fieldKey)
            }
            onCancelled {
                showRssSourceRuleSelector()
            }
            show()
        }
    }

    /**
     * 切换到指定字段
     * 从源JSON中获取指定字段的值，并更新编辑器内容
     * 
     * @param tabKey 板块标识，用于确定从哪个规则对象获取数据
     *               书源：
     *               - "base": 从根对象获取（如 bookSourceUrl、bookSourceName）
     *               - "search": 从 ruleSearch 对象获取（如 bookList、name）
     *               - "explore": 从 ruleExplore 对象获取
     *               - "info": 从 ruleBookInfo 对象获取
     *               - "toc": 从 ruleToc 对象获取
     *               - "content": 从 ruleContent 对象获取
     *               订阅源：
     *               - "base": 从根对象获取（如 sourceUrl、sourceName）
     *               - "start": 从根对象获取（如 startHtml、startJs）
     *               - "list": 从根对象获取（如 ruleArticles、ruleTitle）
     *               - "webView": 从根对象获取（如 ruleContent、injectJs）
     * @param fieldKey 字段标识，如 "author" 表示作者，"name" 表示书名
     * @param cursorPosition 光标位置，用于从搜索结果跳转到匹配位置
     */
    private fun switchToField(tabKey: String, fieldKey: String, cursorPosition: Int = 0) {
        val json = viewModel.sourceJson ?: return
        try {
            // 解析源JSON字符串为JsonObject
            val jsonObj = com.google.gson.JsonParser.parseString(json).asJsonObject
            // 根据板块标识从对应的规则对象中获取字段值
            val value = when (tabKey) {
                // 基本信息直接从根对象获取
                "base" -> {
                    if (jsonObj.has(fieldKey)) jsonObj.get(fieldKey).asString else ""
                }
                // 搜索规则：searchUrl 在根对象，其他在 ruleSearch 对象中
                "search" -> {
                    val rule = jsonObj.getAsJsonObject("ruleSearch")
                    if (fieldKey == "searchUrl") {
                        if (jsonObj.has("searchUrl")) jsonObj.get("searchUrl").asString else ""
                    } else {
                        if (rule != null && rule.has(fieldKey)) rule.get(fieldKey).asString else ""
                    }
                }
                // 发现规则：exploreUrl 在根对象，其他在 ruleExplore 对象中
                "explore" -> {
                    val rule = jsonObj.getAsJsonObject("ruleExplore")
                    if (fieldKey == "exploreUrl") {
                        if (jsonObj.has("exploreUrl")) jsonObj.get("exploreUrl").asString else ""
                    } else {
                        if (rule != null && rule.has(fieldKey)) rule.get(fieldKey).asString else ""
                    }
                }
                // 详情规则：从 ruleBookInfo 对象获取
                "info" -> {
                    val rule = jsonObj.getAsJsonObject("ruleBookInfo")
                    if (rule != null && rule.has(fieldKey)) rule.get(fieldKey).asString else ""
                }
                // 目录规则：从 ruleToc 对象获取
                "toc" -> {
                    val rule = jsonObj.getAsJsonObject("ruleToc")
                    if (rule != null && rule.has(fieldKey)) rule.get(fieldKey).asString else ""
                }
                // 正文规则：从 ruleContent 对象获取
                "content" -> {
                    val rule = jsonObj.getAsJsonObject("ruleContent")
                    if (rule != null && rule.has(fieldKey)) rule.get(fieldKey).asString else ""
                }
                // 文章规则（订阅源）：从 ruleArticle 对象获取
                "article" -> {
                    val rule = jsonObj.getAsJsonObject("ruleArticle")
                    if (rule != null && rule.has(fieldKey)) rule.get(fieldKey).asString else ""
                }
                "start", "list", "webView" -> {
                    if (jsonObj.has(fieldKey)) jsonObj.get(fieldKey).asString else ""
                }
                else -> ""
            }
            // 更新编辑器内容
            editor.setText(value ?: "")
            // 记录当前字段标识，保存时需要返回给调用者
            viewModel.fieldKey = fieldKey
            // 记录当前板块标识，保存时需要返回给调用者
            viewModel.tabKey = tabKey
            // 更新初始文本，用于判断内容是否被修改
            viewModel.initialText = value ?: ""
            // 更新标题栏显示的字段名
            updateTitle(fieldKey)
            if (cursorPosition > 0) {
                editor.post {
                    val pos = editor.cursor.indexer.getCharPosition(cursorPosition)
                    editor.setSelection(pos.line, pos.column)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 更新标题栏显示的字段名
     * @param fieldKey 字段标识
     */
    private fun updateTitle(fieldKey: String) {
        val fieldName = getFieldName(fieldKey)
        if (fieldName.isNotEmpty()) {
            binding.titleBar.title = fieldName
        }
    }
    
    /**
     * 根据字段标识获取字段的中文名称
     * @param fieldKey 字段标识
     * @return 字段的中文名称
     */
    private fun getFieldName(fieldKey: String): String {
        val sourceType = viewModel.sourceType ?: return ""
        
        // 订阅源字段映射
        val rssFieldNames = mapOf(
            "sourceUrl" to "源地址",
            "sourceName" to "源名称",
            "sourceGroup" to "源分组",
            "sourceComment" to "源注释",
            "sourceIcon" to "源图标",
            "loginUrl" to "登录地址",
            "loginUi" to "登录界面",
            "loginCheckJs" to "登录检查JS",
            "coverDecodeJs" to "封面解密JS",
            "header" to "请求头",
            "variableComment" to "变量说明",
            "concurrentRate" to "并发率",
            "jsLib" to "js库",
            "searchUrl" to "搜索地址",
            "sortUrl" to "分类地址",
            "startHtml" to "启动页HTML",
            "startStyle" to "启动页样式",
            "startJs" to "启动页JS",
            "preloadJs" to "预注入JS",
            "ruleArticles" to "列表规则",
            "ruleNextPage" to "列表下一页规则",
            "ruleTitle" to "标题规则",
            "rulePubDate" to "时间规则",
            "ruleDescription" to "描述规则",
            "ruleLink" to "链接规则",
            "ruleImage" to "图片URL规则",
            "ruleContent" to "内容规则",
            "style" to "样式",
            "injectJs" to "注入JS",
            "shouldOverrideUrlLoading" to "URL跳转拦截",
            "contentWhitelist" to "内容白名单",
            "contentBlacklist" to "内容黑名单",
            "enableJs" to "启用JS",
            "loadWithBaseUrl" to "使用BaseURL加载",
            "showWebLog" to "显示Web日志",
            "cacheFirst" to "缓存优先"
        )
        
        // 书源字段映射
        val bookFieldNames = mapOf(
            "bookSourceUrl" to "源地址",
            "bookSourceName" to "源名称",
            "bookSourceGroup" to "源分组",
            "bookSourceComment" to "源注释",
            "loginUrl" to "登录地址",
            "loginUi" to "登录界面",
            "loginCheckJs" to "登录检查JS",
            "coverDecodeJs" to "封面解密JS",
            "bookUrlPattern" to "书籍URL正则",
            "header" to "请求头",
            "variableComment" to "变量说明",
            "concurrentRate" to "并发率",
            "jsLib" to "jsLib",
            "searchUrl" to "搜索地址",
            "checkKeyWord" to "校验关键字",
            "bookList" to "书籍列表",
            "name" to "书名",
            "author" to "作者",
            "kind" to "分类",
            "wordCount" to "字数",
            "lastChapter" to "最新章节",
            "intro" to "简介规则",
            "coverUrl" to "封面规则",
            "bookUrl" to "书籍URL",
            "exploreUrl" to "发现地址",
            "init" to "初始化",
            "tocUrl" to "目录URL",
            "canReName" to "允许修改书名作者",
            "downloadUrls" to "下载地址",
            "preUpdateJs" to "更新之前JS",
            "chapterList" to "目录列表规则",
            "chapterName" to "章节名称",
            "chapterUrl" to "章节URL",
            "formatJs" to "格式化规则",
            "isVolume" to "Volume标识",
            "updateTime" to "更新时间",
            "isVip" to "是否VIP",
            "isPay" to "购买标识",
            "nextTocUrl" to "目录下一页规则",
            "content" to "正文规则",
            "nextContentUrl" to "正文下一页URL规则",
            "subContent" to "副文规则",
            "replaceRegex" to "替换正则",
            "title" to "章节名称规则",
            "sourceRegex" to "资源正则",
            "imageStyle" to "图片样式",
            "imageDecode" to "图片解密",
            "webJs" to "WebView JS",
            "payAction" to "购买操作",
            "callBackJs" to "回调操作"
        )
        
        return when (sourceType) {
            "rssSource" -> rssFieldNames[fieldKey] ?: fieldKey
            "bookSource" -> bookFieldNames[fieldKey] ?: fieldKey
            else -> fieldKey
        }
    }

    /**
     * 退出时保存
     * 检查是否有未保存的更改
     */
    override fun finish() {
        save(true)
    }

    /**
     * 提供帮助操作列表
     * @return 包含帮助操作的列表
     */
    override fun helpActions(): List<SelectItem<String>> {
        return arrayListOf(
            SelectItem("书源教程", "ruleHelp"),
            SelectItem("订阅源教程", "rssRuleHelp"),
            SelectItem("js教程", "jsHelp"),
            SelectItem("正则教程", "regexHelp")
        )
    }

    /**
     * 处理帮助操作的选择事件
     * @param action 操作标识符
     */
    override fun onHelpActionSelect(action: String) {
        when (action) {
            "ruleHelp" -> showHelp("ruleHelp")
            "rssRuleHelp" -> showHelp("rssRuleHelp")
            "jsHelp" -> showHelp("jsHelp")
            "regexHelp" -> showHelp("regexHelp")
        }
    }

    /**
     * 发送文本到当前焦点视图
     * 支持普通输入框和代码编辑器
     * @param text 要插入的文本
     */
    override fun sendText(text: String) {
        val view = window.decorView.findFocus()
        if (view is TextInputEditText) {
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
        }
        else {
            editor.insertText(text, text.length)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    /**
     * 撤销操作
     */
    override fun onUndoClicked() {
        editor.undo()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    /**
     * 重做操作
     */
    override fun onRedoClicked() {
        editor.redo()
    }
}
