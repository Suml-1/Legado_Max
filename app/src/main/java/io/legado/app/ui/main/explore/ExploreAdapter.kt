package io.legado.app.ui.main.explore

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSpinner
import androidx.collection.LruCache
import androidx.core.view.children
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.google.android.flexbox.FlexboxLayout
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.ExploreKind.Type
import io.legado.app.databinding.ItemFilletTextBinding
import io.legado.app.databinding.ItemFindBookBinding
import io.legado.app.databinding.ItemFilletSelectorSingleBinding
import io.legado.app.databinding.ItemFilletCompleteTextBinding
import io.legado.app.databinding.ItemExploreHtmlBinding
import io.legado.app.help.GlideImageGetter
import io.legado.app.help.TextViewTagHandler
import io.legado.app.help.WebCacheManager
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.webView.PooledWebView
import io.legado.app.help.webView.Scope
import io.legado.app.help.webView.WebJsExtensions
import io.legado.app.help.webView.WebJsExtensions.Companion.buildUseWebInjection
import io.legado.app.help.webView.WebJsExtensions.Companion.nameCache
import io.legado.app.help.webView.WebJsExtensions.Companion.nameJava
import io.legado.app.help.webView.WebJsExtensions.Companion.nameSource
import io.legado.app.help.webView.WebJsExtensions.Companion.wrapUseWebHtml
import io.legado.app.help.webView.WebViewPool
import io.legado.app.help.webView.WebViewPool.fitInlineContentSmooth
import io.legado.app.help.webView.WebViewPool.installInlineContentRefitOnTouch
import io.legado.app.help.webView.WebViewPool.prepareForInlineContent
import io.legado.app.help.webView.WebViewPool.currentInlineContentGeneration
import io.legado.app.help.webView.WebViewPool.scheduleInlineContentFit
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.obtainExploreInfoMap
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.text.AccentTextView
import io.legado.app.ui.widget.text.ScrollTextView
import io.legado.app.utils.InfoMap
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.activity
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.openUrl
import io.legado.app.utils.removeLastElement
import io.legado.app.utils.setHtml
import io.legado.app.utils.setSelectionSafely
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.visible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.onLongClick
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.text.isNullOrEmpty

class ExploreAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<BookSourcePart, ItemFindBookBinding>(context) {
    companion object {
        private const val PAYLOAD_TOGGLE_EXPAND = "toggle_expand"
        private const val PAYLOAD_RESUME = "resume"
        private const val PAYLOAD_FORCE_REFRESH = "force_refresh"
        private val exploreWebViewHeightCache = LruCache<String, Int>(99)
    }
    private val recycler = arrayListOf<TextView>()
    private val textRecycler = arrayListOf<AutoCompleteTextView>()
    private val selectRecycler = arrayListOf<LinearLayout>()
    private val htmlRecycler = arrayListOf<FrameLayout>()

    private var expandedSourceUrl: String? = null
    private var scrollToSourceUrl: String? = null
    private var lastClickTime: Long = 0
    private val sourceKinds = ConcurrentHashMap<String, List<ExploreKind>>()
    private val activeWebViews = linkedMapOf<FrameLayout, PooledWebView>()
    private var saveInfoMapJob: Job? = null

    /**
     * 完成待处理的滚动到指定书源位置
     * 如果当前滚动目标与指定的 sourceUrl 匹配，则执行滚动并清除待处理状态
     *
     * @param sourceUrl 目标书源 URL
     * @param anchor 锚点视图，用于在视图树中执行滚动操作
     */
    private fun completePendingScrollToSource(sourceUrl: String?, anchor: View? = null) {
        if (sourceUrl == null || scrollToSourceUrl != sourceUrl) {
            return
        }
        val scrollAction = {
            findSourcePosition(sourceUrl)?.let(callBack::scrollTo)
            if (scrollToSourceUrl == sourceUrl) {
                scrollToSourceUrl = null
            }
        }
        anchor?.post(scrollAction) ?: scrollAction()
    }

    fun clearPendingScrollToSource() {
        scrollToSourceUrl = null
    }

    /**
     * 创建视图绑定对象
     * 用于 RecyclerView 的 ViewHolder 创建
     */
    override fun getViewBinding(parent: ViewGroup): ItemFindBookBinding {
        return ItemFindBookBinding.inflate(inflater, parent, false)
    }

    /**
     * 绑定数据到视图
     * 使用 payload 机制区分不同的刷新场景，避免不必要的 WebView 重建
     * 
     * @param holder 视图持有者
     * @param binding 视图绑定对象
     * @param item 书源数据
     * @param payloads 刷新载荷，用于区分刷新类型：
     *   - 空：完整刷新
     *   - "toggle_expand"：仅切换展开/折叠状态
     *   - "resume"：页面恢复，保持现有内容
     *   - "force_refresh"：强制刷新，重新创建内容
     */
    override fun convert(
        holder: ItemViewHolder,
        binding: ItemFindBookBinding,
        item: BookSourcePart,
        payloads: MutableList<Any>
    ) {
        binding.run {
            // 设置最后一项的底部内边距
            if (holder.layoutPosition == itemCount - 1) {
                root.setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            } else {
                root.setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 0)
            }
            // 只在完整刷新时更新书源名称
            if (payloads.isEmpty()) {
                tvName.text = item.bookSourceName
            }
            
            // 解析 payload 类型，区分不同的刷新场景
            val isResume = payloads.contains(PAYLOAD_RESUME)           // 页面恢复
            val isForceRefresh = payloads.contains(PAYLOAD_FORCE_REFRESH)  // 强制刷新
            
            if (isForceRefresh) {
                // 强制刷新时清除 sourceUrl 标记，触发重新创建内容
                flexbox.setTag(R.id.explore_source_url, null)
                flexbox.setTag(R.id.explore_content_signature, null)
            }
            
            if (expandedSourceUrl == item.bookSourceUrl) {
                // 当前项已展开
                ivStatus.setImageResource(R.drawable.ic_arrow_down)
                rotateLoading.loadingColor = context.accentColor
                
                // 如果是页面恢复且内容已存在，直接返回避免重建
                if (isResume && flexbox.childCount > 0) {
                    rotateLoading.gone()
                    return
                }
                
                // 显示加载动画
                rotateLoading.visible()
                var waitInlineContentScroll = false
                Coroutine.async(callBack.scope) {
                    // 优先使用缓存的 kinds 数据
                    sourceKinds[item.bookSourceUrl]?.also {
                        return@async it
                    }
                    // 缓存不存在时重新获取
                    item.exploreKinds().also {
                        sourceKinds[item.bookSourceUrl] = it
                    }
                }.onSuccess { kindList ->
                    waitInlineContentScroll = kindList.any { it.type == Type.html }
                    upKindList(this@run, item, kindList, item)
                }.onFinally {
                    rotateLoading.gone()
                    // 处理滚动到指定位置
                    scrollToSourceUrl?.let { sourceUrl ->
                        if (sourceUrl == item.bookSourceUrl) {
                            findSourcePosition(sourceUrl)?.let(callBack::scrollTo)
                            if (!waitInlineContentScroll) {
                                scrollToSourceUrl = null
                            }
                        }
                    }
                }
            } else {
                // 当前项已折叠，回收 flexbox 中的视图
                kotlin.runCatching {
                    ivStatus.setImageResource(R.drawable.ic_arrow_right)
                    rotateLoading.gone()
                    recyclerFlexbox(flexbox)
                    flexbox.gone()
                }
            }
        }
    }

    /**
     * 更新发现分类列表
     * 根据分类类型创建对应的视图，并绑定到 flexbox 布局中
     * 
     * 关键优化：通过 sourceUrl 标记避免重复创建 WebView
     * 
     * @param binding 视图绑定对象
     * @param item 书源数据
     * @param kinds 分类列表
     * @param expandedItem 当前展开的书源项
     */
    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    private fun upKindList(binding: ItemFindBookBinding, item: BookSourcePart, kinds: List<ExploreKind>, expandedItem: BookSourcePart) {
        if (kinds.isEmpty()) {
            return
        }
        val flexbox = binding.flexbox
        val sourceUrl = item.bookSourceUrl
        
        // 检查是否已经有内容且 sourceUrl 匹配，避免重复创建 WebView
        // 这是防止页面切换时 WebView 闪烁的关键检查
        val existingSourceUrl = flexbox.getTag(R.id.explore_source_url) as? String
        val currentContentSignature = buildExploreContentSignature(sourceUrl, kinds)
        val existingContentSignature = flexbox.getTag(R.id.explore_content_signature) as? String
        if (existingSourceUrl == sourceUrl
            && existingContentSignature == currentContentSignature
            && flexbox.childCount > 0
        ) {
            // 已经有相同 sourceUrl 的内容，跳过重新创建
            return
        }
        
        kotlin.runCatching {
            // 回收现有的 flexbox 子视图
            recyclerFlexbox(flexbox)
            // 标记当前 flexbox 对应的 sourceUrl，用于后续判断是否需要重建
            flexbox.setTag(R.id.explore_source_url, sourceUrl)
            flexbox.setTag(R.id.explore_content_signature, currentContentSignature)
            flexbox.visible()
            val source by lazy { appDb.bookSourceDao.getBookSource(sourceUrl) }
            val infoMap by lazy { obtainExploreInfoMap(sourceUrl) }
            val sourceJsExtensions by lazy {
                SourceLoginJsExtensions(context as? AppCompatActivity, source,
                    callback = object : SourceLoginJsExtensions.Callback {
                        override fun upUiData(data: Map<String, Any?>?) {
                        }

                        override fun reUiView(deltaUp: Boolean) {
                            refreshExplore(expandedItem, binding)
                        }
                    })
            }
            kinds.forEach { kind ->
                val type = kind.type
                val title = kind.title
                val viewName = kind.viewName
                when (type) {
                    Type.url -> {
                        val tv = getFlexboxChild(flexbox)
                        flexbox.addView(tv)
                        kind.style().apply {
                            when (this.layout_justifySelf) {
                                "flex_start" -> tv.gravity = Gravity.START
                                "flex_end" -> tv.gravity = Gravity.END
                                else -> tv.gravity = Gravity.CENTER
                            }
                            apply(tv)
                        }
                        if (viewName == null) {
                            tv.text = title
                        } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                            val n = viewName.substring(1, viewName.length - 1)
                            tv.text = n
                        } else {
                            tv.text = title
                            Coroutine.async(callBack.scope, IO) {
                                evalUiJs(viewName, source, infoMap)
                            }.onSuccess { n ->
                                if (n.isNullOrEmpty()) {
                                    tv.text = "null"
                                } else {
                                    tv.text = n
                                }
                            }.onError { _ ->
                                tv.text = "err"
                            }
                        }
                        tv.setOnClickListener {// 辅助触发无障碍功能正常
                            val url = kind.url ?: return@setOnClickListener
                            if (kind.title.startsWith("ERROR:")) {
                                it.activity?.showDialogFragment(TextDialog("ERROR", url))
                            } else {
                                callBack.openExplore(sourceUrl, kind.title, url)
                            }
                        }
                        tv.setOnTouchListener { view, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    view.isSelected = true
                                }
                                MotionEvent.ACTION_UP -> {
                                    view.isSelected = false
                                    val upTime = System.currentTimeMillis()
                                    if (upTime - lastClickTime < 200) {
                                        return@setOnTouchListener true
                                    }
                                    lastClickTime = upTime
                                    val url = kind.url?.takeIf { it.isNotBlank() } ?: return@setOnTouchListener true
                                    if (kind.title.startsWith("ERROR:")) {
                                        view.activity?.showDialogFragment(TextDialog("ERROR", url))
                                    } else {
                                        callBack.openExplore(sourceUrl, kind.title, url)
                                    }
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    view.isSelected = false
                                }
                            }
                            return@setOnTouchListener true
                        }
                    }

                    Type.html -> {
                        val container = getFlexboxChildHtml(flexbox)
                        flexbox.addView(container)
                        kind.style().apply(container)
                        bindHtmlView(container, kind, source, infoMap, title, sourceJsExtensions)
                    }

                    Type.button -> {
                        val tv = getFlexboxChild(flexbox)
                        flexbox.addView(tv)
                        kind.style().apply {
                            when (this.layout_justifySelf) {
                                "flex_start" -> tv.gravity = Gravity.START
                                "flex_end" -> tv.gravity = Gravity.END
                                else -> tv.gravity = Gravity.CENTER
                            }
                            apply(tv)
                        }
                        if (viewName == null) {
                            tv.text = title
                        } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                            val n = viewName.substring(1, viewName.length - 1)
                            tv.text = n
                        } else {
                            tv.text = title
                            Coroutine.async(callBack.scope, IO) {
                                evalUiJs(viewName, source, infoMap)
                            }.onSuccess { n ->
                                if (n.isNullOrEmpty()) {
                                    tv.text = "null"
                                } else {
                                    tv.text = n
                                }
                            }.onError{ _ ->
                                tv.text = "err"
                            }
                        }
                        tv.setOnClickListener {
                            val action = kind.action?.takeIf { it.isNotBlank() } ?: return@setOnClickListener
                            callBack.scope.launch(IO) {
                                evalButtonClick(action, source, infoMap, title, sourceJsExtensions)
                            }
                        }
                        tv.setOnTouchListener { view, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    view.isSelected = true
                                }
                                MotionEvent.ACTION_UP -> {
                                    view.isSelected = false
                                    val upTime = System.currentTimeMillis()
                                    if (upTime - lastClickTime < 200) {
                                        return@setOnTouchListener true
                                    }
                                    lastClickTime = upTime
                                    val action = kind.action?.takeIf { it.isNotBlank() } ?: return@setOnTouchListener true
                                    callBack.scope.launch(IO) {
                                        evalButtonClick(action, source, infoMap, title, sourceJsExtensions)
                                    }
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    view.isSelected = false
                                }
                            }
                            return@setOnTouchListener true
                        }
                    }

                    Type.text -> {
                        val ti = getFlexboxChildText(flexbox)
                        flexbox.addView(ti)
                        kind.style().apply {
                            when (this.layout_justifySelf) {
                                "center" -> ti.gravity = Gravity.CENTER
                                "flex_end" -> ti.gravity = Gravity.END
                                else -> ti.gravity = Gravity.START
                            }
                            apply(ti)
                        }
                        if (viewName == null) {
                            ti.hint = title
                        } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                            val n = viewName.substring(1, viewName.length - 1)
                            ti.hint = n
                        } else {
                            ti.hint = title
                            Coroutine.async(callBack.scope, IO) {
                                evalUiJs(viewName, source, infoMap)
                            }.onSuccess { n ->
                                if (n.isNullOrEmpty()) {
                                    ti.hint = "null"
                                } else {
                                    ti.hint = n
                                }
                            }.onError{ _ ->
                                ti.hint = "err"
                            }
                        }
                        ti.setText(infoMap[title])
                        var actionJob: Job? = null
                        val watcher = object : TextWatcher {
                            var content: String? = null
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                                content = s.toString()
                            }

                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                            override fun afterTextChanged(s: Editable?) {
                                val reContent = s.toString()
                                infoMap[title] = reContent
                                if (kind.action != null && reContent != content) {
                                    actionJob?.cancel()
                                    actionJob = callBack.scope.launch(IO) {
                                        delay(600) //防抖
                                        evalButtonClick(kind.action, source, infoMap, title, sourceJsExtensions)
                                        content = reContent
                                    }
                                }
                            }
                        }
                        ti.setTag(R.id.text_watcher, watcher)
                        ti.addTextChangedListener(watcher)
                    }

                    Type.toggle -> {
                        var newName = title
                        var left = true
                        val tv = getFlexboxChild(flexbox)
                        flexbox.addView(tv)
                        kind.style().apply {
                            when (this.layout_justifySelf) {
                                "flex_start" -> tv.gravity = Gravity.START
                                "flex_end" -> tv.gravity = Gravity.END
                                "right" -> left = false
                                else -> tv.gravity = Gravity.CENTER
                            }
                            apply(tv)
                        }
                        val chars = kind.chars?.filterNotNull() ?: listOf("chars","is null")
                        val infoV = infoMap[title]
                        var char = if (infoV.isNullOrEmpty()) {
                            (kind.default ?: chars[0]).also {
                                infoMap[title] = it
                            }
                        } else {
                            infoV
                        }
                        if (viewName == null) {
                            tv.text = if (left) char + title else title + char
                        } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                            val n = viewName.substring(1, viewName.length - 1)
                            newName = n
                            tv.text = if (left) char + n else n + char
                        } else {
                            tv.text = if (left) char + title else title + char
                            Coroutine.async(callBack.scope, IO) {
                                evalUiJs(viewName, source, infoMap)
                            }.onSuccess { n ->
                                if (n.isNullOrEmpty()) {
                                    tv.text = char + "null"
                                } else {
                                    newName = n
                                    tv.text = if (left) char + n else n + char
                                }
                            }.onError{ _ ->
                                tv.text = char + "err"
                            }
                        }
                        tv.setOnClickListener {
                            val currentIndex = chars.indexOf(char)
                            val nextIndex = (currentIndex + 1) % chars.size
                            char = chars.getOrNull(nextIndex) ?: ""
                            infoMap[title] = char
                            tv.text = if (left) char + newName else newName + char
                            val action = kind.action?.takeIf { it.isNotBlank() } ?: return@setOnClickListener
                            callBack.scope.launch(IO) {
                                evalButtonClick(action, source, infoMap, title, sourceJsExtensions)
                            }
                        }
                        tv.setOnTouchListener { view, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    view.isSelected = true
                                }
                                MotionEvent.ACTION_UP -> {
                                    view.isSelected = false
                                    val upTime = System.currentTimeMillis()
                                    if (upTime - lastClickTime < 200) {
                                        return@setOnTouchListener true
                                    }
                                    lastClickTime = upTime
                                    val currentIndex = chars.indexOf(char)
                                    val nextIndex = (currentIndex + 1) % chars.size
                                    char = chars.getOrNull(nextIndex) ?: ""
                                    infoMap[title] = char
                                    tv.text = if (left) char + newName else newName + char
                                    val action = kind.action?.takeIf { it.isNotBlank() } ?: return@setOnTouchListener true
                                    callBack.scope.launch(IO) {
                                        evalButtonClick(action, source, infoMap, title, sourceJsExtensions)
                                    }
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    view.isSelected = false
                                }
                            }
                            return@setOnTouchListener true
                        }
                    }

                    Type.select -> {
                        val sl = getFlexboxChildSelect(flexbox)
                        flexbox.addView(sl)
                        kind.style().apply {
                            when (this.layout_justifySelf) {
                                "flex_start" -> sl.gravity = Gravity.START
                                "flex_end" -> sl.gravity = Gravity.END
                                else -> sl.gravity = Gravity.CENTER
                            }
                            apply(sl)
                        }
                        val spName = sl.findViewById<AccentTextView>(R.id.sp_name)
                        if (viewName == null) {
                            spName.text = title
                        } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                            val n = viewName.substring(1, viewName.length - 1)
                            spName.text = n
                        } else {
                            spName.text = title
                            Coroutine.async(callBack.scope, IO) {
                                evalUiJs(viewName, source, infoMap)
                            }.onSuccess { n ->
                                if (n.isNullOrEmpty()) {
                                    spName.text = "null"
                                } else {
                                    spName.text = n
                                }
                            }.onError{ _ ->
                                spName.text = "err"
                            }
                        }
                        val chars = kind.chars?.filterNotNull() ?: listOf("chars","is null")
                        val adapter = ArrayAdapter(
                            context,
                            R.layout.item_text_common,
                            chars
                        )
                        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
                        val selector = sl.findViewById<AppCompatSpinner>(R.id.sp_type)
                        selector.adapter = adapter
                        val infoV = infoMap[title]
                        val char = if (infoV.isNullOrEmpty()) {
                            (kind.default ?: chars[0]).also {
                                infoMap[title] = it
                            }
                        } else {
                            infoV
                        }
                        val i = chars.indexOf(char)
                        selector.setSelectionSafely(i)
                        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            var isInitializing = true
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                if (isInitializing) { //忽略初始化选择
                                    isInitializing = false
                                    return
                                }
                                infoMap[title] = chars[position]
                                if (kind.action != null) {
                                    callBack.scope.launch(IO) {
                                        evalButtonClick(kind.action, source, infoMap, title, sourceJsExtensions)
                                    }
                                }
                            }
                            override fun onNothingSelected(parent: AdapterView<*>?) {
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 执行 UI JavaScript 表达式
     * 用于动态计算视图名称等 UI 相关的值
     *
     * @param jsStr JavaScript 表达式字符串
     * @param source 书源对象
     * @param infoMap 信息映射表
     * @return 执行结果字符串，执行失败返回 null
     */
    private suspend fun evalUiJs(jsStr: String, source: BookSource?, infoMap: InfoMap): String? {
        val source = source ?: return null
        return try {
            runScriptWithContext {
                source.evalJS(jsStr) {
                    put("infoMap", infoMap)
                }.toString()
            }
        } catch (e: Exception) {
            AppLog.put(source.getTag() + " exploreUi err:" + (e.localizedMessage ?: e.toString()), e)
            null
        }
    }

    /**
     * 执行按钮点击的 JavaScript 动作
     * 用于处理 button、toggle、select 等交互元素的点击事件
     *
     * @param jsStr JavaScript 表达式字符串
     * @param source 书源对象
     * @param infoMap 信息映射表
     * @param name 按钮名称，用于日志记录
     * @param java JavaScript 扩展接口对象
     */
    private suspend fun evalButtonClick(jsStr: String, source: BaseSource?, infoMap: InfoMap, name: String, java: SourceLoginJsExtensions) {
        val source = source ?: return
        try {
            runScriptWithContext {
                source.evalJS(jsStr) {
                    put("java", java)
                    put("infoMap", infoMap)
                }
            }
        } catch (e: Exception) {
            AppLog.put("ExploreUI Button $name JavaScript error", e)
        }
    }

    /**
     * 从回收池获取或创建 TextView
     * 用于 url、button、toggle 类型的分类项
     */
    @Synchronized
    private fun getFlexboxChild(flexbox: FlexboxLayout): TextView {
        return if (recycler.isEmpty()) {
            ItemFilletTextBinding.inflate(inflater, flexbox, false).root
        } else {
            recycler.removeLastElement()
        }
    }

    /**
     * 从回收池获取或创建 AutoCompleteTextView
     * 用于 text 类型的分类项（文本输入框）
     */
    @Synchronized
    private fun getFlexboxChildText(flexbox: FlexboxLayout): AutoCompleteTextView {
        return if (textRecycler.isEmpty()) {
            ItemFilletCompleteTextBinding.inflate(inflater, flexbox, false).root
        } else {
            textRecycler.removeLastElement()
        }
    }

    /**
     * 从回收池获取或创建 LinearLayout（下拉选择器容器）
     * 用于 select 类型的分类项
     */
    @Synchronized
    private fun getFlexboxChildSelect(flexbox: FlexboxLayout): LinearLayout {
        return if (selectRecycler.isEmpty()) {
            ItemFilletSelectorSingleBinding.inflate(inflater, flexbox, false).root
        } else {
            selectRecycler.removeLastElement()
        }
    }

    /**
     * 从回收池获取或创建 FrameLayout（HTML 内容容器）
     * 用于 html 类型的分类项
     */
    @Synchronized
    private fun getFlexboxChildHtml(flexbox: FlexboxLayout): FrameLayout {
        return if (htmlRecycler.isEmpty()) {
            ItemExploreHtmlBinding.inflate(inflater, flexbox, false).root
        } else {
            htmlRecycler.removeLastElement()
        }
    }

    /**
     * 绑定 HTML 视图
     * 根据内容前缀判断使用 WebView（useweb）还是 TextView（usehtml）展示
     *
     * @param container 容器布局
     * @param kind 发现分类对象
     * @param source 书源对象
     * @param infoMap 信息映射表
     * @param title 分类标题
     * @param sourceJsExtensions 书源 JavaScript 扩展接口
     */
    private fun bindHtmlView(
        container: FrameLayout,
        kind: ExploreKind,
        source: BookSource?,
        infoMap: InfoMap,
        title: String,
        sourceJsExtensions: SourceLoginJsExtensions
    ) {
        releaseWebView(container)
        container.removeAllViews()
        resolveHtmlContent(kind, source, infoMap, title).onSuccess { rawContent ->
            val content = rawContent?.trim().orEmpty()
            when {
                content.startsWith("<useweb>") -> bindExploreWebView(container, content, source, infoMap)
                content.startsWith("<usehtml>") -> bindExploreTextView(
                    container,
                    content,
                    source,
                    infoMap,
                    title,
                    sourceJsExtensions
                )
                else -> {
                    container.gone()
                }
            }
        }.onError {
            container.gone()
        }
    }

    /**
     * 解析 HTML 内容
     * 从 kind 的 url、title 或 viewName 中提取内容
     *
     * @param kind 发现分类对象
     * @param source 书源对象
     * @param infoMap 信息映射表
     * @param title 分类标题
     * @return 异步返回解析后的内容字符串
     */
    private fun resolveHtmlContent(
        kind: ExploreKind,
        source: BookSource?,
        infoMap: InfoMap,
        title: String
    ) = Coroutine.async(callBack.scope, IO) {
        kind.url?.takeIf { it.startsWith("<usehtml>") || it.startsWith("<useweb>") }
            ?: title.takeIf { it.startsWith("<usehtml>") || it.startsWith("<useweb>") }
            ?: kind.viewName?.let { viewName ->
                when {
                    viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'' -> {
                        viewName.substring(1, viewName.length - 1)
                    }
                    else -> evalUiJs(viewName, source, infoMap)
                }
            }
    }

    /**
     * 绑定 TextView 显示 usehtml 内容
     * 使用 Html.fromHtml 解析内容，支持图片加载和自定义标签处理
     *
     * @param container 容器布局
     * @param content 包含 usehtml 标签的内容字符串
     * @param source 书源对象
     * @param infoMap 信息映射表
     * @param title 分类标题
     * @param sourceJsExtensions 书源 JavaScript 扩展接口
     */
    private fun bindExploreTextView(
        container: FrameLayout,
        content: String,
        source: BookSource?,
        infoMap: InfoMap,
        title: String,
        sourceJsExtensions: SourceLoginJsExtensions
    ) {
        val endIndex = content.lastIndexOf("<")
        if (endIndex < 9) {
            container.gone()
            return
        }
        val html = content.substring(9, endIndex)
        val textView = ScrollTextView(context, null).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            minHeight = 48.dpToPx()
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            textSize = 14F
        }
        val lifecycle = container.findViewTreeLifecycleOwner()?.lifecycle
        val imageGetter = lifecycle?.let {
            GlideImageGetter(
                context,
                textView,
                it,
                context.resources.displayMetrics.widthPixels - 48.dpToPx(),
                source?.bookSourceUrl
            )
        }
        val tagHandler = TextViewTagHandler(object : TextViewTagHandler.OnButtonClickListener {
            override fun onButtonClick(name: String, click: String) {
                callBack.scope.launch(IO) {
                    evalButtonClick(click, source, infoMap, "$title $name", sourceJsExtensions)
                }
            }
        })
        textView.setHtml(
            html,
            imageGetter,
            tagHandler,
            imgOnLongClickListener = {
                container.activity?.showDialogFragment(PhotoDialog(it, source?.bookSourceUrl))
            },
            imgOnClickListener = { click ->
                callBack.scope.launch(IO) {
                    evalButtonClick(click, source, infoMap, "$title image", sourceJsExtensions)
                }
            }
        )
        container.addView(textView)
        container.visible()
        completePendingScrollToSource(source?.bookSourceUrl, container)
    }

    /**
     * 绑定 WebView 显示 useweb 内容
     * 处理流程：解析HTML -> 构建注入JS -> 获取WebView -> 显示加载圈 -> 加载内容 -> 加载完成后显示
     */
    private fun bindExploreWebView(
        container: FrameLayout,
        content: String,
        source: BookSource?,
        infoMap: InfoMap
    ) {
        val endIndex = content.lastIndexOf("<")
        if (endIndex < 8) {
            container.gone()
            return
        }
        val useWebHtml = content.substring(8, endIndex)
       // 相同的来源与 useWeb 模板应恢复至同一记忆页面，且
// 在回收 / 重新绑定后复用上次测算的高度。
        val pageStateKey = buildExploreUseWebStateKey(source, useWebHtml, infoMap)
        val initialPage = infoMap["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val pageLayoutKey = buildExploreUseWebLayoutKey(source, useWebHtml, infoMap, initialPage)
        val pageJs = buildExploreUseWebPageInjection(
            pageKey = pageStateKey,
            initialPage = initialPage
        )
        val html = wrapExploreUseWebHtml(useWebHtml, source, pageJs)
        val pooledWebView = WebViewPool.acquire(context, Scope.INLINE)
        val webView = pooledWebView.realWebView
        webView.onResume()
        val cachedHeight = exploreWebViewHeightCache[pageLayoutKey]?.takeIf { it > 1 }
        val loadingHeight = 120.dpToPx()
        prepareForInlineContent(webView, cachedHeight ?: loadingHeight)
        val loadingIndicator = createLoadingIndicator(container)
        container.addView(loadingIndicator)
        webView.invisible()
        webView.webViewClient = ExploreHtmlWebViewClient(
            container,
            source,
            source?.bookSourceUrl,
            pageJs,
            pageLayoutKey,
            loadingIndicator
        )
        installInlineContentRefitOnTouch(webView) {
            val height = webView.layoutParams?.height ?: 0
            if (height > 1) {
                exploreWebViewHeightCache.put(pageLayoutKey, height)
            }
            container.requestLayout()
        }
        webView.addJavascriptInterface(WebCacheManager, nameCache)
        source?.let {
            webView.addJavascriptInterface(it as BaseSource, nameSource)
            val webJsExtensions = WebJsExtensions(it, context as? AppCompatActivity, webView)
            webView.addJavascriptInterface(webJsExtensions, nameJava)
        }
        container.addView(webView)
        activeWebViews[container] = pooledWebView
        val baseUrl = source?.bookSourceUrl?.takeIf { it.startsWith("http", true) }
        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", baseUrl)
        container.visible()
    }

    /**
     * 创建加载指示器
     * 用于 WebView 加载过程中显示的进度条
     *
     * @param container 父容器
     * @return ProgressBar 实例
     */
    private fun createLoadingIndicator(container: FrameLayout): ProgressBar {
        return ProgressBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                120.dpToPx(),
                android.view.Gravity.CENTER
            )
            indeterminateTintList = android.content.res.ColorStateList.valueOf(context.accentColor)
        }
    }

    /**
     * 释放 WebView 资源
     * 移除 JavaScript 接口并将 WebView 归还到池中
     */
    private fun releaseWebView(container: FrameLayout) {
        activeWebViews.remove(container)?.let { pooledWebView ->
            // A pooled WebView must not keep source-specific JS bridges when it is attached
            // to the next row, otherwise callbacks may hit the wrong source/context.
            pooledWebView.realWebView.apply {
                removeJavascriptInterface(nameCache)
                removeJavascriptInterface(nameSource)
                removeJavascriptInterface(nameJava)
            }
            WebViewPool.release(pooledWebView)
        }
    }

    /**
     * 构建高度缓存的唯一标识 Key
     * 使用书源URL哈希、HTML哈希和HTML长度组合，降低哈希冲突概率
     */
    /**
     * 构建 useweb 页面状态缓存 Key
     * 用于标识 WebView 页面状态的唯一性
     *
     * @param source 书源对象
     * @param html HTML 内容字符串
     * @return 页面状态 Key
     */
    private fun buildExploreUseWebPageKey(source: BookSource?, html: String): String {
        return buildString {
            append("useweb_page_")
            append(source?.bookSourceUrl?.hashCode() ?: "default")
            append('_')
            append(html.hashCode())
            append('_')
            append(html.length) // 添加长度进一步降低冲突概率
        }
    }

    /**
     * 构建 useweb 状态 Key
     * 用于标识特定上下文下的 WebView 状态，支持页面恢复
     *
     * @param source 书源对象
     * @param html HTML 内容字符串
     * @param infoMap 信息映射表
     * @return 状态 Key
     */
    private fun buildExploreUseWebStateKey(
        source: BookSource?,
        html: String,
        infoMap: InfoMap? = null
    ): String {
        val contextSignature = buildExploreUseWebContextSignature(infoMap)
        return buildString {
            append("useweb_state_")
            append(source?.bookSourceUrl?.let(MD5Utils::md5Encode16) ?: "default")
            append('_')
            append(MD5Utils.md5Encode16(html))
            append('_')
            append(contextSignature)
        }
    }

    /**
     * 构建 useweb 布局 Key
     * 用于缓存特定页面的 WebView 高度
     *
     * @param source 书源对象
     * @param html HTML 内容字符串
     * @param infoMap 信息映射表
     * @param page 页码
     * @return 布局 Key
     */
    private fun buildExploreUseWebLayoutKey(
        source: BookSource?,
        html: String,
        infoMap: InfoMap?,
        page: Int
    ): String {
        return buildString {
            append(buildExploreUseWebStateKey(source, html, infoMap))
            append("_layout_")
            append(page.coerceAtLeast(1))
        }
    }

    /**
     * 构建 useweb 上下文签名
     * 排除 page 字段后对 infoMap 进行签名，用于判断上下文是否变化
     *
     * @param infoMap 信息映射表
     * @return 上下文签名字符串
     */
    private fun buildExploreUseWebContextSignature(infoMap: InfoMap?): String {
        if (infoMap == null || infoMap.isEmpty()) return "default"
        val normalizedContext = infoMap.entries
            .asSequence()
            .filter { (key, _) -> !key.equals("page", ignoreCase = true) }
            .sortedBy { it.key }
            .joinToString("&") { (key, value) -> "$key=$value" }
        return if (normalizedContext.isBlank()) {
            "default"
        } else {
            MD5Utils.md5Encode16(normalizedContext)
        }
    }

    /**
     * 构建发现内容签名
     * 用于判断分类列表是否发生变化，避免重复创建视图
     *
     * @param sourceUrl 书源 URL
     * @param kinds 分类列表
     * @return 内容签名字符串
     */
    private fun buildExploreContentSignature(sourceUrl: String, kinds: List<ExploreKind>): String {
        return buildString {
            append(MD5Utils.md5Encode16(sourceUrl))
            append('_')
            append(MD5Utils.md5Encode16(GSON.toJson(kinds)))
        }
    }

    /**
     * 构建 useweb 页面注入脚本
     * 提供 page 属性的读写能力，支持分页状态管理
     *
     * @param pageKey 页面状态缓存 Key
     * @param initialPage 初始页码
     * @return JavaScript 注入代码
     */
    private fun buildExploreUseWebPageInjection(pageKey: String, initialPage: Int): String {
        val safePage = initialPage.coerceAtLeast(1)
        val keyJson = GSON.toJson(pageKey)
        return """
            try{
                const __useWebPageKey = $keyJson;
                const __useWebDefaultPage = $safePage;
                const __readUseWebPage = () => {
                    const cachedPage = parseInt(cache.getFromMemory(__useWebPageKey), 10);
                    return Number.isFinite(cachedPage) && cachedPage > 0 ? cachedPage : __useWebDefaultPage;
                };
                const __writeUseWebPage = value => {
                    const nextPage = parseInt(value, 10);
                    const safeNextPage = Number.isFinite(nextPage) && nextPage > 0 ? nextPage : __useWebDefaultPage;
                    cache.putMemory(__useWebPageKey, String(safeNextPage));
                    return safeNextPage;
                };
                Object.defineProperty(window, 'page', {
                    configurable: true,
                    get() {
                        return __readUseWebPage();
                    },
                    set(value) {
                        __writeUseWebPage(value);
                    }
                });
                if (typeof Element !== 'undefined' && !Object.getOwnPropertyDescriptor(Element.prototype, 'page')) {
                    Object.defineProperty(Element.prototype, 'page', {
                        configurable: true,
                        get() {
                            return window.page;
                        },
                        set(value) {
                            window.page = value;
                        }
                    });
                }
                if (java && typeof java.open === 'function' && !java.__exploreUseWebPageWrapped) {
                    const __rawOpen = java.open.bind(java);
                    java.open = function(name, url, title, origin) {
                        if (name === 'explore' && typeof url === 'string') {
                            const match = url.match(/[?&]page=(\d+)/i);
                            if (match) {
                                __writeUseWebPage(parseInt(match[1], 10) + 1);
                            }
                        }
                        return __rawOpen(name, url, title, origin);
                    };
                    java.__exploreUseWebPageWrapped = true;
                }
            }catch(e){}
        """.trimIndent()
    }

    /**
     * 包装 useweb HTML 内容
     * 添加透明背景样式和 JavaScript 注入脚本
     *
     * @param html 原始 HTML 内容
     * @param source 书源对象
     * @param pageJs 页面 JavaScript 注入代码
     * @return 包装后的完整 HTML
     */
    private fun wrapExploreUseWebHtml(html: String, source: BookSource?, pageJs: String): String {
        val inlineStyle = """
            <style>
            html,body{background:transparent;}
            </style>
        """.trimIndent()
        val injection = buildString {
            val baseJs = buildUseWebInjection(source).trim()
            if (baseJs.isNotEmpty()) {
                append(baseJs)
            }
            if (pageJs.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(pageJs.trim())
            }
        }
        if (injection.isBlank()) {
            return "$inlineStyle\n$html"
        }
        val safeInjection = Regex("(?i)</script>").replace(injection, "<\\\\/script>")
        return "$inlineStyle\n<script>\n$safeInjection\n</script>\n$html"
    }

    /**
     * 回收 flexbox 中的子视图
     * 将子视图分类回收到对应的回收池中，以便复用
     * 
     * 注意：此方法会释放 WebView 并清除 sourceUrl 标记
     */
    @Synchronized
    private fun recyclerFlexbox(flexbox: FlexboxLayout) {
        // 清除 sourceUrl 标记，表示内容已被回收
        flexbox.setTag(R.id.explore_source_url, null)
        flexbox.setTag(R.id.explore_content_signature, null)
        val children = flexbox.children.toList()
        if (children.isEmpty()) return
        flexbox.removeAllViews()
        callBack.scope.launch {
            for (child in children) {
                when (child) {
                    is AutoCompleteTextView -> {
                        val watcher = child.getTag(R.id.text_watcher) as? TextWatcher
                        if (watcher != null) {
                            child.removeTextChangedListener(watcher)
                        }
                        textRecycler.add(child)
                    }
                    is TextView -> {
                        child.setOnTouchListener(null)
                        child.setOnClickListener(null)
                        recycler.add(child)
                    }
                    is LinearLayout -> {
                        child.findViewById<AppCompatSpinner>(R.id.sp_type)?.onItemSelectedListener = null
                        selectRecycler.add(child)
                    }
                    is FrameLayout -> {
                        releaseWebView(child)
                        child.removeAllViews()
                        htmlRecycler.add(child)
                    }
                }
            }
        }
    }

    /**
     * 注册列表项点击监听器
     * 点击标题展开/折叠发现分类，长按显示菜单
     */
    override fun registerListener(holder: ItemViewHolder, binding: ItemFindBookBinding) {
        binding.apply {
            // 点击标题展开/折叠
            llTitle.setOnClickListener {
                val position = holder.bindingAdapterPosition.takeIf { it >= 0 } ?: return@setOnClickListener
                val item = getItem(position) ?: return@setOnClickListener
                val oldExpandedSourceUrl = expandedSourceUrl
                // 切换展开状态：如果当前项已展开则折叠，否则展开
                expandedSourceUrl = if (oldExpandedSourceUrl == item.bookSourceUrl) null else item.bookSourceUrl
                // 折叠旧的展开项
                oldExpandedSourceUrl?.let { sourceUrl ->
                    findSourcePosition(sourceUrl)?.let {
                        notifyItemChanged(it, PAYLOAD_TOGGLE_EXPAND)
                    }
                }
                // 展开新的项
                expandedSourceUrl?.let { sourceUrl ->
                    scrollToSourceUrl = sourceUrl
                    findSourcePosition(sourceUrl)?.let {
                        notifyItemChanged(it, PAYLOAD_TOGGLE_EXPAND)
                    }
                } ?: run {
                    scrollToSourceUrl = null
                }
            }
            // 长按显示菜单
            llTitle.onLongClick {
                val position = holder.bindingAdapterPosition
                if (position >= 0) {
                    showMenu(binding, position)
                } else {
                    true
                }
            }
        }
    }

    /**
     * 压缩（折叠）所有展开的发现项
     * @return 是否有项被折叠
     */
    fun compressExplore(): Boolean {
        val sourceUrl = expandedSourceUrl
        return if (sourceUrl == null) {
            false
        } else {
            expandedSourceUrl = null
            findSourcePosition(sourceUrl)?.let {
                notifyItemChanged(it)
            }
            true
        }
    }

    /**
     * 刷新展开的项目
     * 
     * @param force 是否强制刷新（重新创建内容），默认 false
     *   - true: 强制刷新，清除缓存并重新创建 WebView
     *   - false: 普通恢复，保持现有内容不变
     */
    fun refreshExpandedItem(force: Boolean = false) {
        expandedSourceUrl?.let { sourceUrl ->
            findSourcePosition(sourceUrl)?.let { position ->
                if (force) {
                    // 强制刷新：清除标记以触发重新创建
                    // 注意：这里不直接操作，而是通过 payload 传递
                    notifyItemChanged(position, PAYLOAD_FORCE_REFRESH)
                } else {
                    // 普通恢复：通过 payload 传递标记，保持现有内容
                    notifyItemChanged(position, PAYLOAD_RESUME)
                }
            }
        }
    }

    /**
     * 页面暂停时调用
     * 只暂停 WebView 而不释放，保持数据状态以便快速恢复
     */
    fun onPause() {
        // 暂停所有活跃的 WebView，但不释放它们
        activeWebViews.values.forEach { pooledWebView ->
            pooledWebView.realWebView.onPause()
        }
        saveInfoMapJob?.cancel()
        exploreInfoMapList.snapshot().filter { (_, infoMap) -> infoMap.needSave }.forEach { (_, infoMap) ->
            infoMap.saveNow()
        }
    }

    /**
     * 页面恢复时调用
     * 恢复所有暂停的 WebView
     */
    fun onResume() {
        activeWebViews.forEach { (container, pooledWebView) ->
            val webView = pooledWebView.realWebView
            webView.onResume()
            WebViewPool.fitInlineContentSmooth(
                webView,
                WebViewPool.currentInlineContentGeneration(webView),
                afterLayout = {
                    container.requestLayout()
                }
            )
            webView.postDelayed({
                WebViewPool.scheduleInlineContentFit(webView, {
                    container.requestLayout()
                }, longArrayOf(120L, 360L))
            }, 100)
        }
    }

    /**
     * 页面销毁时调用
     * 释放所有 WebView 和清除缓存数据
     */
    fun onDestroy() {
        sourceKinds.clear()
        activeWebViews.keys.toList().forEach(::releaseWebView)
        saveInfoMapJob?.cancel()
    }

    /**
     * 刷新发现分类内容
     * 清除缓存并重新加载分类列表
     *
     * @param source 书源分部对象
     * @param binding 视图绑定对象
     */
    private fun refreshExplore(source: BookSourcePart, binding: ItemFindBookBinding) {
        binding.rotateLoading.visible()
        Coroutine.async(callBack.scope) {
            source.clearExploreKindsCache()
            sourceKinds[source.bookSourceUrl] = source.exploreKinds()
        }.onSuccess {
            findSourcePosition(source.bookSourceUrl)?.let {
                // Rebind the expanded row after clearing exploreKinds cache so inline useWeb/useHtml
                // content is rebuilt from the latest rule output instead of reusing attached views.
                notifyItemChanged(it, PAYLOAD_FORCE_REFRESH)
            }
        }.onFinally {
            binding.rotateLoading.gone()
        }
    }

    /**
     * 显示书源操作菜单
     * 提供编辑、置顶、查询、搜索、登录、刷新、删除等操作
     *
     * @param binding 视图绑定对象
     * @param position 列表位置
     * @return 是否成功显示菜单
     */
    private fun showMenu(binding: ItemFindBookBinding, position: Int): Boolean {
        val source = getItem(position) ?: return true
        val popupMenu = PopupMenu(context, binding.llTitle)
        popupMenu.inflate(R.menu.explore_item)
        popupMenu.menu.findItem(R.id.menu_login).isVisible = source.hasLoginUrl
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_edit -> callBack.editSource(source.bookSourceUrl)
                R.id.menu_top -> callBack.toTop(source)
                R.id.menu_query -> callBack.showKindQueryDialog(source)
                R.id.menu_search -> callBack.searchBook(source)
                R.id.menu_login -> context.startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", source.bookSourceUrl)
                }

                R.id.menu_refresh -> refreshExplore(source, binding)

                R.id.menu_del -> callBack.deleteSource(source)
            }
            true
        }
        popupMenu.show()
        return true
    }

    interface CallBack {
        val scope: CoroutineScope
        fun scrollTo(pos: Int)
        fun openExplore(sourceUrl: String, title: String, exploreUrl: String?)
        fun editSource(sourceUrl: String)
        fun toTop(source: BookSourcePart)
        fun deleteSource(source: BookSourcePart)
        fun searchBook(bookSource: BookSourcePart)
        fun showKindQueryDialog(source: BookSourcePart)
    }

    /**
     * 根据书源 URL 查找其在列表中的位置
     *
     * @param sourceUrl 书源 URL
     * @return 列表位置，未找到返回 null
     */
    private fun findSourcePosition(sourceUrl: String): Int? {
        for (index in 0 until itemCount) {
            val item = getItem(index) ?: continue
            if (item.bookSourceUrl == sourceUrl) {
                return index
            }
        }
        return null
    }

    private inner class ExploreHtmlWebViewClient(
        private val container: FrameLayout,
        private val source: BaseSource?,
        private val sourceUrl: String?,
        private val pageJs: String,
        private val pageLayoutKey: String,
        private val loadingIndicator: ProgressBar
    ) : WebViewClient() {
        private val jsStr = buildString {
            append(buildUseWebInjection(source))
            if (pageJs.isNotBlank()) {
                append('\n')
                append(pageJs)
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            request?.let {
                val uri = it.url
                return when (uri.scheme) {
                    "http", "https" -> false
                    "legado", "yuedu" -> {
                        context.startActivity<OnLineImportActivity> {
                            data = uri
                        }
                        true
                    }

                    else -> {
                        container.activity?.findViewById<View>(android.R.id.content)
                            ?.longSnackbar(R.string.jump_to_another_app, R.string.confirm) {
                                container.activity?.openUrl(uri)
                            }
                        true
                    }
                }
            }
            return true
        }

        private fun injectPageState(webView: WebView, delayedRetries: LongArray = longArrayOf()) {
            if (jsStr.isBlank()) return
            webView.evaluateJavascript(jsStr, null)
            delayedRetries.forEach { delayMillis ->
                webView.postDelayed({
                    if (!webView.isAttachedToWindow) return@postDelayed
                    webView.evaluateJavascript(jsStr, null)
                }, delayMillis)
            }
        }

        private fun cacheMeasuredHeight(webView: WebView) {
            val height = webView.layoutParams?.height ?: 0
            if (height > 1) {
                exploreWebViewHeightCache.put(pageLayoutKey, height)
            }
            loadingIndicator.gone()
            webView.visible()
            container.requestLayout()
            completePendingScrollToSource(sourceUrl, container)
        }

        private fun fitAndCacheHeight(webView: WebView, delayed: Boolean) {
            if (delayed) {
                scheduleInlineContentFit(webView, { cacheMeasuredHeight(webView) }, longArrayOf(120L, 360L, 720L))
            } else {
                WebViewPool.fitInlineContentSmooth(
                    webView,
                    WebViewPool.currentInlineContentGeneration(webView),
                    afterLayout = { cacheMeasuredHeight(webView) }
                )
            }
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            view?.let { webView ->
                injectPageState(webView)
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            view?.let { webView ->
                injectPageState(webView, longArrayOf(120L, 360L))
                fitAndCacheHeight(webView, delayed = false)
                fitAndCacheHeight(webView, delayed = true)
            }
        }
    }
}
