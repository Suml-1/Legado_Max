package io.legado.app.ui.login

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.setPadding
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.rule.RowUi
import io.legado.app.databinding.DialogLoginBinding
import io.legado.app.databinding.ItemFilletTextBinding
import io.legado.app.databinding.ItemSourceEditBinding
import io.legado.app.databinding.ItemSelectorSingleBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.openUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import kotlin.text.lastIndexOf
import kotlin.text.startsWith
import kotlin.text.substring
import android.view.MotionEvent
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSpinner
import io.legado.app.data.entities.rule.RowUi.Type
import io.legado.app.model.login.LoginUiV2
import io.legado.app.model.login.evalLoginActionV2
import io.legado.app.model.login.evalLoginUiV2
import io.legado.app.model.login.isLoginUiV2
import io.legado.app.ui.widget.text.TextInputLayout
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.indexOf
import io.legado.app.utils.setSelectionSafely
import kotlinx.coroutines.delay

class SourceLoginDialog :
    BaseDialogFragment(R.layout.dialog_login, true),
    SourceLoginJsExtensions.Callback {

    private val binding by viewBinding(DialogLoginBinding::bind)
    private val viewModel by activityViewModels<SourceLoginViewModel>()
    private var lastClickTime: Long = 0
    private var oKToClose = false
    private var rowUis: List<RowUi>? = null
    private var rowUiName = arrayListOf<String>()
    private var hasChange = false
    private var loginUrl: String? = null

    // 登录界面 v2（loginUi(state)/loginAction(...)）状态
    private var isV2 = false
    private var stateJson = "{}"
    private var v2ActionRunning = false
    private val v2Countdowns = hashMapOf<String, Int>()
    private val sourceLoginJsExtensions by lazy {
        SourceLoginJsExtensions(
            activity as AppCompatActivity,
            viewModel.source,
            viewModel.bookType,
            this,
        )
    }

    private var initHandler = false
    private val handler by lazy {
        initHandler = true
        buildMainHandler()
    }

    override fun upUiData(data: Map<String, Any?>?) {
        try {
            activity?.runOnUiThread {
                // 在主线程中更新 UI
                handleUpUiData(data)
            }
        } catch (e: Exception) {
            AppLog.put("upLoginData Error: " + e.localizedMessage, e)
        }
    }

    override fun reUiView(deltaUp: Boolean) {
        activity?.runOnUiThread {
            handleReUiView(deltaUp)
        }
    }

    private fun handleReUiView(deltaUp: Boolean) {
        val source = viewModel.source ?: return
        if (isV2) {
            renderV2()
            return
        }
        val loginUiStr = source.loginUi ?: return
        val codeStr = loginUiStr.let {
            when {
                it.startsWith("@js:") -> it.substring(4)
                it.startsWith("<js>") -> it.substring(4, it.lastIndexOf("<"))
                else -> null
            }
        }
        if (codeStr != null) {
            hasChange = true
            lifecycleScope.launch(Main) {
                withContext(IO) {
                    val loginUiJson = evalUiJs(codeStr)
                    rowUis = loginUi(loginUiJson)
                }
                rowUiBuilder(source, rowUis, deltaUp)
            }
        } else {
            rowUis = loginUi(loginUiStr)
            rowUiBuilder(source, rowUis, deltaUp)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun handleUpUiData(data: Map<String, Any?>?) {
        hasChange = true
        if (data == null) {
            val newLoginInfo: MutableMap<String, String> = mutableMapOf()
            rowUis?.forEachIndexed { index, rowUi ->
                val default = rowUi.default
                when (val rowView = binding.root.findViewById<View>(index + 1000)) {
                    is TextInputLayout -> {
                        val value = default ?: ""
                        newLoginInfo[rowUi.name] = value
                        rowView.editText?.setText(value)
                    }

                    is TextView -> {
                        when (rowUi.type) {
                            Type.button -> {
                                rowView.text = rowUi.viewName ?: rowUi.name
                            }
                            Type.toggle -> {
                                val char = default ?: run {
                                    val chars = rowUi.chars?.filterNotNull() ?: listOf("chars is null")
                                    chars.getOrNull(0) ?: ""
                                }
                                newLoginInfo[rowUi.name] = char
                                val name = rowUi.viewName ?: rowUi.name
                                val left = rowUi.style?.layout_justifySelf != "right"
                                rowView.text = if (left) char + name else name + char
                            }
                        }
                    }

                    is LinearLayout -> {
                        val chars = rowUi.chars?.filterNotNull() ?: listOf("chars", "is null")
                        val index = chars.indexOf(default)
                        newLoginInfo[rowUi.name] = default ?: run {
                            chars.getOrNull(0) ?: ""
                        }
                        rowView.findViewById<AppCompatSpinner>(R.id.sp_type)?.setSelectionSafely(index)
                    }
                }
            }
            viewModel.loginInfo = newLoginInfo
            return
        }
        val loginInfo = viewModel.loginInfo
        data.forEach { (key, value) ->
            val value = value?.toString()
            val index = rowUiName.indexOf(key)
            if (index != -1) {
                val rowUi = rowUis?.getOrNull(index) ?: return@forEach
                val value = value ?: rowUi.default
                when (val rowView = binding.root.findViewById<View>(index + 1000)) {
                    is TextInputLayout -> {
                        val value = value ?: ""
                        loginInfo[rowUi.name] = value
                        rowView.editText?.setText(value)
                    }

                    is TextView -> {
                        when (rowUi.type) {
                            Type.button -> {
                                rowView.text = value ?: rowUi.viewName ?: key
                            }

                            Type.toggle -> {
                                val char = value ?: run {
                                    val chars = rowUi.chars?.filterNotNull() ?: listOf("chars is null")
                                    chars.getOrNull(0) ?: ""
                                }
                                loginInfo[rowUi.name] = char
                                val name = rowUi.viewName ?: rowUi.name
                                val left = rowUi.style?.layout_justifySelf != "right"
                                rowView.text = if (left) char + name else name + char
                            }
                        }
                    }

                    is LinearLayout -> {
                        val items = rowUi.chars?.filterNotNull() ?: listOf("chars", "is null")
                        val index = items.indexOf(value)
                        rowView.findViewById<AppCompatSpinner>(R.id.sp_type)?.setSelectionSafely(index)
                    }
                }
            } else {
                loginInfo[key] = value ?: ""
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    suspend fun evalUiJs(jsStr: String): String? {
        val source = viewModel.source ?: return null
        val loginJS = loginUrl ?: ""
        val result = rowUis?.let {
            getLoginData(it)
        } ?: viewModel.loginInfo.toMutableMap()
        return try {
            runScriptWithContext {
                source.evalJS("$loginJS\n$jsStr") {
                    put("result", result)
                    put("book", viewModel.book)
                    put("chapter", viewModel.chapter)
                }.toString()
            }
        } catch (e: Exception) {
            AppLog.put(source.getTag() + " loginUi err:" + (e.localizedMessage ?: e.toString()), e)
            null
        }
    }

    fun loginUi(json: String?): List<RowUi>? = GSON.fromJsonArray<RowUi>(json).onFailure {
        AppLog.put("loginUi json parse err:" + it.localizedMessage, it)
    }.getOrNull()

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    private fun rowUiBuilder(source: BaseSource, rowUis: List<RowUi>?, deltaUp: Boolean) {
        val loginInfo = viewModel.loginInfo
        if (!deltaUp) {
            binding.flexbox.removeAllViews()
            rowUiName.clear()
        }
        rowUis?.forEachIndexed { index, rowUi ->
            val type = rowUi.type
            val name = rowUi.name
            val viewName = rowUi.viewName
            val action = rowUi.action
            val default = rowUi.default
            var insertIndex = -1
            if (deltaUp) {
                val oldIndex = rowUiName.indexOf(name, index)
                if (oldIndex == index) {
                    binding.flexbox.getChildAt(oldIndex)?.let {
                        it.id = index + 1000
                        return@forEachIndexed
                    }
                } else if (oldIndex >= 0) {
                    binding.flexbox.getChildAt(oldIndex)?.let {
                        binding.flexbox.removeView(it)
                        rowUiName.removeAt(oldIndex)
                        binding.flexbox.addView(it, index)
                        rowUiName.add(index, name)
                        it.id = index + 1000
                        return@forEachIndexed
                    }
                }
                if (oldIndex == -2) {
                    rowUiName.add(name)
                } else {
                    rowUiName.add(index, name)
                    insertIndex = index
                }
            } else {
                rowUiName.add(name)
            }
            when (type) {
                Type.text -> ItemSourceEditBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false,
                ).let {
                    val editText = it.editText
                    binding.flexbox.addView(it.root, insertIndex)
                    rowUi.style().apply {
                        when (this.layout_justifySelf) {
                            "center" -> editText.gravity = Gravity.CENTER
                            "flex_end" -> editText.gravity = Gravity.END
                        }
                        apply(it.root)
                    }
                    it.root.id = index + 1000
                    if (viewName == null) {
                        it.textInputLayout.hint = name
                    } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                        it.textInputLayout.hint = viewName.substring(1, viewName.length - 1)
                    } else {
                        it.textInputLayout.hint = name
                        execute {
                            evalUiJs(viewName)
                        }.onSuccess { n ->
                            if (n.isNullOrEmpty()) {
                                it.textInputLayout.hint = "null"
                            } else {
                                it.textInputLayout.hint = n
                            }
                        }.onError { _ ->
                            it.textInputLayout.hint = "err"
                        }
                    }
                    editText.setText(loginInfo[name] ?: default)
                    action?.let { jsStr ->
                        val watcher = object : TextWatcher {
                            private var content: String? = null
                            private val runnable = Runnable {
                                handleButtonClick(source, jsStr, name, false)
                            }
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                                content = s.toString()
                            }

                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                            override fun afterTextChanged(s: Editable?) {
                                val reContent = s.toString()
                                if (reContent != content) {
                                    handler.removeCallbacks(runnable)
                                    handler.postDelayed(runnable, 600)
                                }
                            }
                        }
                        editText.addTextChangedListener(watcher)
                    }
                }

                Type.password -> ItemSourceEditBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false,
                ).let {
                    val editText = it.editText
                    binding.flexbox.addView(it.root, insertIndex)
                    rowUi.style().apply {
                        when (this.layout_justifySelf) {
                            "center" -> editText.gravity = Gravity.CENTER
                            "flex_end" -> editText.gravity = Gravity.END
                        }
                        apply(it.root)
                    }
                    it.root.id = index + 1000
                    if (viewName == null) {
                        it.textInputLayout.hint = name
                    } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                        it.textInputLayout.hint = viewName.substring(1, viewName.length - 1)
                    } else {
                        it.textInputLayout.hint = name
                        execute {
                            evalUiJs(viewName)
                        }.onSuccess { n ->
                            if (n.isNullOrEmpty()) {
                                it.textInputLayout.hint = "null"
                            } else {
                                it.textInputLayout.hint = n
                            }
                        }.onError { _ ->
                            it.textInputLayout.hint = "err"
                        }
                    }
                    editText.inputType =
                        InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT
                    editText.setText(loginInfo[name] ?: default)
                    action?.let { jsStr ->
                        val watcher = object : TextWatcher {
                            private var content: String? = null
                            private val runnable = Runnable {
                                handleButtonClick(source, jsStr, name, false)
                            }
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                                content = s.toString()
                            }

                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                            override fun afterTextChanged(s: Editable?) {
                                val reContent = s.toString()
                                if (reContent != content) {
                                    handler.removeCallbacks(runnable)
                                    handler.postDelayed(runnable, 600)
                                }
                            }
                        }
                        editText.addTextChangedListener(watcher)
                    }
                }

                Type.select -> ItemSelectorSingleBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false,
                ).let {
                    if (viewName == null) {
                        it.spName.text = name
                    } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                        it.spName.text = viewName.substring(1, viewName.length - 1)
                    } else {
                        it.spName.text = name
                        execute {
                            evalUiJs(viewName)
                        }.onSuccess { n ->
                            if (n.isNullOrEmpty()) {
                                it.spName.text = "null"
                            } else {
                                it.spName.text = n
                            }
                        }.onError { _ ->
                            it.spName.text = "err"
                        }
                    }
                    val chars = rowUi.chars?.filterNotNull() ?: listOf("chars", "is null")
                    val adapter = ArrayAdapter(
                        requireContext(),
                        R.layout.item_text_common,
                        chars,
                    )
                    adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
                    val selector = it.spType
                    selector.adapter = adapter
                    val infoV = loginInfo[name]
                    val char = if (infoV.isNullOrEmpty()) {
                        hasChange = true
                        default ?: chars[0]
                    } else {
                        infoV
                    }
                    loginInfo[name] = char
                    val i = chars.indexOf(char)
                    selector.setSelectionSafely(i)
                    selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        var isInitializing = true
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            if (isInitializing) { // 忽略初始化选择
                                isInitializing = false
                                return
                            }
                            hasChange = true
                            loginInfo[name] = chars[position]
                            if (action != null) {
                                handleButtonClick(source, action, name, false)
                            }
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {
                        }
                    }
                    binding.flexbox.addView(it.root, insertIndex)
                    rowUi.style().apply {
                        when (this.layout_justifySelf) {
                            "flex_start" -> selector.gravity = Gravity.START
                            "flex_end" -> selector.gravity = Gravity.END
                        }
                        apply(it.root)
                    }
                    it.root.id = index + 1000
                }

                Type.button -> ItemFilletTextBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false,
                ).let {
                    binding.flexbox.addView(it.root, insertIndex)
                    rowUi.style().apply {
                        when (this.layout_justifySelf) {
                            "flex_start" -> it.textView.gravity = Gravity.START
                            "flex_end" -> it.textView.gravity = Gravity.END
                        }
                        apply(it.root)
                    }
                    it.root.id = index + 1000
                    if (viewName == null) {
                        it.textView.text = name
                    } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                        val n = viewName.substring(1, viewName.length - 1)
                        rowUi.viewName = n
                        it.textView.text = n
                    } else {
                        it.textView.text = name
                        execute {
                            evalUiJs(viewName)
                        }.onSuccess { n ->
                            if (n.isNullOrEmpty()) {
                                it.textView.text = "null"
                            } else {
                                rowUi.viewName = n // 在回调handleUIDataUpdate用
                                it.textView.text = n
                            }
                        }.onError { _ ->
                            it.textView.text = "err"
                        }
                    }
                    it.textView.setPadding(16.dpToPx())
                    var downTime = 0L
                    it.root.setOnClickListener {
                        // 无障碍点击
                        handleButtonClick(source, action, name, false)
                    }
                    it.root.setOnTouchListener { view, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                view.isSelected = true
                                downTime = System.currentTimeMillis()
                            }
                            MotionEvent.ACTION_UP -> {
                                view.isSelected = false
                                val upTime = System.currentTimeMillis()
                                if (upTime - lastClickTime < 200) {
                                    return@setOnTouchListener true
                                }
                                lastClickTime = upTime
                                handleButtonClick(source, action, name, upTime > downTime + 666)
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                view.isSelected = false
                            }
                        }
                        return@setOnTouchListener true
                    }
                }

                Type.label -> ItemFilletTextBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false,
                ).let {
                    binding.flexbox.addView(it.root, insertIndex)
                    rowUi.style().apply { apply(it.root) }
                    it.root.id = index + 1000
                    it.textView.text = viewName ?: name
                    it.textView.setPadding(16.dpToPx())
                }

                Type.toggle -> ItemFilletTextBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false,
                ).let {
                    var newName = name
                    var left = true
                    binding.flexbox.addView(it.root, insertIndex)
                    rowUi.style().apply {
                        when (this.layout_justifySelf) {
                            "flex_start" -> it.textView.gravity = Gravity.START
                            "flex_end" -> it.textView.gravity = Gravity.END
                            "right" -> left = false
                        }
                        apply(it.root)
                    }
                    it.root.id = index + 1000
                    val chars = rowUi.chars?.filterNotNull() ?: listOf("chars is null")
                    val infoV = loginInfo[name]
                    var char = if (infoV.isNullOrEmpty()) {
                        hasChange = true
                        default ?: chars[0]
                    } else {
                        infoV
                    }
                    loginInfo[name] = char
                    if (viewName == null) {
                        it.textView.text = if (left) char + name else name + char
                    } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                        val n = viewName.substring(1, viewName.length - 1)
                        rowUi.viewName = n
                        newName = n
                        it.textView.text = if (left) char + n else n + char
                    } else {
                        it.textView.text = if (left) char + name else name + char
                        execute {
                            evalUiJs(viewName)
                        }.onSuccess { n ->
                            if (n.isNullOrEmpty()) {
                                it.textView.text = char + "null"
                            } else {
                                rowUi.viewName = n // 存放新名字，在回调handleUIDataUpdate时用
                                newName = n // 下面切换时用
                                it.textView.text = if (left) char + n else n + char
                            }
                        }.onError { _ ->
                            it.textView.text = char + "err"
                        }
                    }
                    it.textView.setPadding(16.dpToPx())
                    var downTime = 0L
                    it.root.setOnClickListener { _ ->
                        val currentIndex = chars.indexOf(char)
                        val nextIndex = (currentIndex + 1) % chars.size
                        char = chars.getOrNull(nextIndex) ?: ""
                        hasChange = true
                        loginInfo[name] = char
                        it.textView.text = if (left) char + newName else newName + char
                        handleButtonClick(source, action, name, false)
                    }
                    it.root.setOnTouchListener { view, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                view.isSelected = true
                                downTime = System.currentTimeMillis()
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
                                hasChange = true
                                loginInfo[name] = char
                                it.textView.text = if (left) char + newName else newName + char
                                handleButtonClick(source, action, name, upTime > downTime + 666)
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                view.isSelected = false
                            }
                        }
                        return@setOnTouchListener true
                    }
                }
            }
        }
        if (deltaUp && rowUis != null) {
            val newRowUiSize = rowUis.size
            for (i in binding.flexbox.childCount - 1 downTo newRowUiSize) {
                binding.flexbox.removeViewAt(i)
            }
            if (rowUiName.size > newRowUiSize) {
                rowUiName.subList(newRowUiSize, rowUiName.size).clear()
            }
        }
    }

    private fun buttonUi(source: BaseSource, rowUis: List<RowUi>?) {
        rowUiBuilder(source, rowUis, false)
        binding.toolBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_ok -> {
                    oKToClose = true
                    login(source)
                }

                R.id.menu_show_login_header -> alert {
                    setTitle(R.string.login_header)
                    source.getLoginHeader()?.let { loginHeader ->
                        setMessage(loginHeader)
                        positiveButton(R.string.copy_text) {
                            appCtx.sendToClip(loginHeader)
                        }
                    }
                }

                R.id.menu_del_login_header -> source.removeLoginHeader()
                R.id.menu_log -> showDialogFragment<AppLogDialog>()
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val source = viewModel.source ?: return
        isV2 = source.isLoginUiV2()
        loginUrl = source.getLoginJs()
        setupToolBar(source)
        if (isV2) {
            renderV2()
            return
        }
        val loginUiStr = source.loginUi ?: return
        val codeStr = loginUiStr.let {
            when {
                it.startsWith("@js:") -> it.substring(4)
                it.startsWith("<js>") -> it.substring(4, it.lastIndexOf("<"))
                else -> null
            }
        }
        if (codeStr != null) {
            lifecycleScope.launch(Main) {
                withContext(IO) {
                    val loginUiJson = evalUiJs(codeStr)
                    rowUis = loginUi(loginUiJson)
                }
                buttonUi(source, rowUis)
            }
        } else {
            rowUis = loginUi(loginUiStr)
            buttonUi(source, rowUis)
        }
    }

    private fun setupToolBar(source: BaseSource) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.title = getString(R.string.login_source, source.getTag())
        binding.toolBar.inflateMenu(R.menu.source_login)
        binding.toolBar.menu.applyTint(requireContext())
    }

    /**
     * 渲染一帧 v2 登录界面。
     *
     * 脚本返回 `{rows:[...]}`；行里的 `key` 是表单键、`name` 是显示名，这里统一归一化成
     * `name = key` + `viewName = 显示名`，让 v1 的渲染/取值逻辑原样复用。
     */
    private fun renderV2(candidateState: String = stateJson) {
        val source = viewModel.source ?: return
        lifecycleScope.launch {
            val result = withContext(IO) {
                runCatching {
                    LoginUiV2.parseRender(source.evalLoginUiV2(candidateState))
                        ?: error("loginUi(state) 返回值不是 {rows:[...]}")
                }
            }
            val newRows = result.getOrNull()
            if (newRows == null) {
                AppLog.put("登录 UI v2 渲染失败", result.exceptionOrNull())
                if (rowUis.isNullOrEmpty()) {
                    viewModel.loginInfo.clear()
                    rowUis = listOf(
                        RowUi(
                            name = getString(R.string.login_ui_v2_render_error),
                            type = Type.label,
                        ),
                    )
                    buttonUi(source, rowUis)
                } else {
                    context?.toastOnUi(R.string.login_ui_v2_render_error)
                }
                return@launch
            }
            stateJson = candidateState
            val stored = storedLoginInfo()
            viewModel.loginInfo.clear()
            newRows.forEach { row ->
                val key = row.key ?: return@forEach
                if (row.type == Type.text ||
                    row.type == Type.password ||
                    row.type == Type.select ||
                    row.type == Type.toggle
                ) {
                    val choices = row.choices()
                    val resolved = LoginUiV2.resolveFieldValue(row.value, null, stored[key])
                        ?: row.default
                        ?: choices.firstOrNull()
                        ?: ""
                    viewModel.loginInfo[key] = if (row.type == Type.toggle && choices.isNotEmpty()) {
                        resolved.takeIf { it in choices } ?: choices.first()
                    } else {
                        resolved
                    }
                }
            }
            rowUis = newRows.map(::normalizeV2Row)
            buttonUi(source, rowUis)
        }
    }

    private fun normalizeV2Row(row: RowUi): RowUi {
        val key = row.key
        if (key.isNullOrBlank()) return row
        return row.copy(
            name = key,
            viewName = row.viewName ?: row.name,
            chars = row.chars ?: row.options,
        )
    }

    private fun storedLoginInfo(): Map<String, String> = viewModel.source?.getLoginInfo()
        ?.let { GSON.fromJsonObject<Map<String, String>>(it).getOrNull() }
        .orEmpty()

    private fun dispatchV2(action: String, countdownSeconds: Int? = null) {
        if (v2ActionRunning) return
        if ((v2Countdowns[action] ?: 0) > 0) return
        val source = viewModel.source ?: return
        val formJson = GSON.toJson(getLoginData(rowUis))
        v2ActionRunning = true
        lifecycleScope.launch {
            val result = withContext(IO) {
                runCatching { source.evalLoginActionV2(action, stateJson, formJson) }
            }
            v2ActionRunning = false
            result.exceptionOrNull()?.let { error ->
                AppLog.put("登录 UI v2 动作 $action 执行出错", error)
                context?.toastOnUi(
                    getString(
                        R.string.login_ui_v2_action_error,
                        error.localizedMessage ?: error.toString(),
                    ),
                )
                return@launch
            }
            val command = LoginUiV2.parseActionResult(result.getOrNull())
            if (command.malformed) {
                AppLog.put("登录 UI v2 动作 $action 返回无效命令")
                context?.toastOnUi(R.string.login_ui_v2_invalid_action)
                return@launch
            }
            command.unknownKeys.forEach {
                AppLog.put("登录 UI v2 动作 $action 返回未知命令 $it，已忽略")
            }
            command.loginJson?.let { loginJson ->
                if (!withContext(IO) { source.putLoginInfo(loginJson) }) {
                    context?.toastOnUi(R.string.login_ui_v2_save_error)
                    return@launch
                }
            }
            if (command.close) {
                oKToClose = true
                dismissAllowingStateLoss()
                return@launch
            }
            command.error?.values?.firstOrNull()?.let { message ->
                context?.toastOnUi(getString(R.string.login_ui_v2_action_error, message))
            }
            if (command.error.isNullOrEmpty()) {
                countdownSeconds?.let { startCountdown(action, it) }
            }
            command.stateJson?.let { renderV2(it) }
        }
    }

    private fun startCountdown(action: String, seconds: Int) {
        if (seconds <= 0) return
        v2Countdowns[action] = seconds
        lifecycleScope.launch {
            for (left in seconds downTo 1) {
                v2Countdowns[action] = left
                delay(1000L)
            }
            v2Countdowns.remove(action)
        }
    }

    private fun handleButtonClick(source: BaseSource, action: String?, name: String, isLongClick: Boolean) {
        if (isV2) {
            if (action != null) {
                val countdown = rowUis?.firstOrNull { it.name == name }?.countdown
                dispatchV2(action, countdown)
            }
            return
        }
        lifecycleScope.launch(IO) {
            if (action.isAbsUrl()) {
                context?.openUrl(action!!)
            } else if (action != null) {
                // JavaScript
                val buttonFunctionJS = action
                val loginJS = loginUrl ?: return@launch
                kotlin.runCatching {
                    runScriptWithContext {
                        source.evalJS("$loginJS\n$buttonFunctionJS") {
                            put("java", sourceLoginJsExtensions)
                            put("result", getLoginData(rowUis))
                            put("book", viewModel.book)
                            put("chapter", viewModel.chapter)
                            put("isLongClick", isLongClick)
                        }
                    }
                }.onFailure { e ->
                    ensureActive()
                    AppLog.put("LoginUI Button $name JavaScript error", e)
                }
            }
        }
    }

    private fun getLoginData(rowUis: List<RowUi>?): MutableMap<String, String> {
        val loginData = hashMapOf<String, String>()
        rowUis?.forEachIndexed { index, rowUi ->
            when (rowUi.type) {
                Type.text, Type.password -> {
                    val rowView = binding.root.findViewById<View>(index + 1000)
                    ItemSourceEditBinding.bind(rowView).editText.text.let {
                        loginData[rowUi.name] = it?.toString() ?: rowUi.default ?: "" // 没文本的时候存空字符串,而不是删除loginInfo
                    }
                }
            }
        }
        return viewModel.loginInfo.toMutableMap().apply { putAll(loginData) }
    }

    private fun login(source: BaseSource) {
        lifecycleScope.launch(IO) {
            val loginData = getLoginData(rowUis)
            if (loginData.isEmpty()) {
                source.removeLoginInfo()
                withContext(Main) {
                    dismiss()
                }
            } else if (source.putLoginInfo(GSON.toJson(loginData))) {
                try {
                    val buttonFunctionJS = "if (typeof login=='function'){ login.apply(this); } else { throw('Function login not implements!!!') }"
                    val loginJS = loginUrl ?: return@launch
                    runScriptWithContext {
                        source.evalJS("$loginJS\n$buttonFunctionJS") {
                            put("java", sourceLoginJsExtensions)
                            put("result", loginData)
                            put("book", viewModel.book)
                            put("chapter", viewModel.chapter)
                            put("isLongClick", false)
                        }
                    }
                    context?.toastOnUi(R.string.success)
                    withContext(Main) {
                        dismiss()
                    }
                } catch (e: Exception) {
                    AppLog.put("登录出错\n${e.localizedMessage}", e)
                    context?.toastOnUi("登录出错\n${e.localizedMessage}")
                    e.printOnDebug()
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (!oKToClose && hasChange && !isV2) {
            val loginInfo = viewModel.loginInfo
            if (loginInfo.isEmpty()) {
                viewModel.source?.removeLoginInfo()
            } else {
                viewModel.source?.putLoginInfo(GSON.toJson(loginInfo))
            }
        }
        if (initHandler) {
            handler.removeCallbacksAndMessages(null)
        }
        super.onDismiss(dialog)
        activity?.finish()
    }
}
