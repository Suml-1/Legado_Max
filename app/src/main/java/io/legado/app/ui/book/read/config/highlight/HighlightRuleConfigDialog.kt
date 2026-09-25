package io.legado.app.ui.book.read.config.highlight

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogHighlightRuleConfigBinding
import io.legado.app.databinding.ItemHighlightPresetRuleBinding
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.model.ReadBook
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getClipText
import io.legado.app.utils.observeEvent
import io.legado.app.utils.readText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.ui.widget.recycler.ItemTouchCallback

/**
 * 阅读高亮规则的配置弹窗。
 *
 * 负责展示规则列表、分组筛选、菜单入口、导入导出和编辑弹窗调度；
 * 规则状态和保存动作由 `HighlightRuleConfigViewModel` 承担。
 */
class HighlightRuleConfigDialog : BaseDialogFragment(R.layout.dialog_highlight_rule_config),
    PopupMenu.OnMenuItemClickListener {

    private val binding by viewBinding(DialogHighlightRuleConfigBinding::bind)
    private val viewModel: HighlightRuleConfigViewModel by viewModels()
    private val adapter by lazy { HighlightRuleAdapter(requireContext()) }
    private var itemTouchHelper: ItemTouchHelper? = null

    /** 当前展示的「更多」菜单。裸 Dialog 不随 Fragment 生命周期销毁，需在视图销毁时手动关闭 */
    private var itemMenuDialog: android.app.Dialog? = null
    private var primaryTextColor = 0
    private var secondaryTextColor = 0
    private var accentColor = 0
    private var cardBgColor = 0
    private var cardStrokeColor = 0
    private var previewBgColor = 0
    private var previewStrokeColor = 0

    /** 待导出的规则列表暂存，导出文件回调返回时使用。 */
    private var pendingExportRules: List<HighlightRule>? = null

    /** 文件导入回调：用户选择文件后读取内容并解析为高亮规则列表。 */
    private val importDoc = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            runCatching {
                val text = uri.readText(requireContext())
                val imported = GSON.fromJsonArray<HighlightRule>(text).getOrNull()
                if (imported.isNullOrEmpty()) {
                    context?.toastOnUi(R.string.highlight_rule_import_invalid)
                } else {
                    viewModel.importRules(imported)
                    applyGroupFilter()
                    context?.toastOnUi(R.string.highlight_rule_import_success)
                }
            }.onFailure {
                context?.toastOnUi("读取文件出错\n${it.localizedMessage}")
            }
        }
    }

    /** 文件导出回调：用户选择保存位置后将规则 JSON 写入文件。 */
    private val exportResult = registerForActivityResult(HandleFileContract()) { result ->
        val rules = pendingExportRules ?: return@registerForActivityResult
        pendingExportRules = null
        result.uri?.let {
            context?.toastOnUi("已导出 ${rules.size} 条规则")
        }
        result.clipboardJson?.let {
            requireContext().sendToClip(it)
            context?.toastOnUi("已复制 ${rules.size} 条规则")
        }
    }

    /** 设置弹窗尺寸、位置和背景，在底部以 85% 高度展示。 */
    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.85f)
        dialog?.window?.setGravity(Gravity.BOTTOM)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        if (!AppConfig.isEInkMode) {
            view?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    /** Fragment 视图创建完毕：初始化主题、RecyclerView、按钮监听并加载规则。 */
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initTheme()
        attachBottomSheetDismiss(
            binding.dragHandle,
            binding.sheetContainer
        ) { dismissAllowingStateLoss() }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        // 系统自带的长按判定容易被卡片里的按钮和滚动抢走，这里关掉，改成手动 startDrag
        val touchCallback = ItemTouchCallback(object : ItemTouchCallback.Callback {
            override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
                adapter.swapItem(srcPosition, targetPosition)
                return true
            }

            override fun onClearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ) {
                clearDragAppearance(viewHolder.itemView)
                persistRuleOrder()
            }
        }).apply { isCanDrag = false }
        itemTouchHelper = ItemTouchHelper(touchCallback)
        itemTouchHelper?.attachToRecyclerView(binding.recyclerView)
        binding.recyclerView.clipToPadding = false
        binding.recyclerView.setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 28.dpToPx())

        binding.ivClose.setOnClickListener { dismissAllowingStateLoss() }
        binding.ivMenu.setOnClickListener { showMenu(it) }
        binding.tvEmptyAdd.setOnClickListener { showPresetRules() }

        loadRules()
    }

    /** 监听全局配置变更事件，背景色或文字色变化时刷新主题和列表。 */
    override fun observeLiveBus() {
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) {
            if (it.contains(1) || it.contains(2)) {
                initTheme()
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroyView() {
        // 「更多」菜单是裸 Dialog，不随 Fragment 销毁，宿主视图销毁时必须手动关闭
        itemMenuDialog?.dismiss()
        itemMenuDialog = null
        super.onDestroyView()
    }

    /** 根据当前主题计算各颜色值（主/次文字色、卡片背景、描边、预览区颜色）并应用到所有 UI 元素。 */
    private fun initTheme() {
        val bg = requireContext().bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        primaryTextColor = requireContext().getPrimaryTextColor(isLight)
        secondaryTextColor = requireContext().getSecondaryTextColor(isLight)
        accentColor = requireContext().accentColor

        cardBgColor = if (isLight) {
            ColorUtils.blendColors(bg, 0xFF000000.toInt(), 0.08f)
        } else {
            ColorUtils.blendColors(bg, 0xFFFFFFFF.toInt(), 0.06f)
        }
        cardStrokeColor = if (isLight) {
            ColorUtils.blendColors(0xFF000000.toInt(), bg, 0.88f)
        } else {
            ColorUtils.blendColors(0xFFFFFFFF.toInt(), bg, 0.85f)
        }
        previewBgColor = cardBgColor
        previewStrokeColor = cardStrokeColor
        val accentForegroundColor =
            if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

        binding.sheetContainer.background?.mutate()?.setTint(bg)
        binding.ivClose.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
        binding.ivClose.background?.mutate()?.setTint(cardBgColor)
        binding.tvPageTitle.setTextColor(primaryTextColor)
        binding.tvPageSubtitle.setTextColor(secondaryTextColor)
        binding.ivMenu.setColorFilter(accentForegroundColor, PorterDuff.Mode.SRC_IN)
        binding.ivMenu.background?.mutate()?.setTint(accentColor)
        binding.ivEmpty.setColorFilter(secondaryTextColor, PorterDuff.Mode.SRC_IN)
        binding.tvEmptyMsg.setTextColor(secondaryTextColor)
        binding.tvEmptyHint.setTextColor(secondaryTextColor)

        binding.tvEmptyAdd.background?.mutate()?.setTint(accentColor)
        binding.tvEmptyAdd.setTextColor(accentForegroundColor)
    }

    /** 在锚点视图上方弹出溢出菜单（选择、新增、预设、分组、导入导出、分享、恢复默认）。 */
    private fun showMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.highlight_rule_config, menu)
            setOnMenuItemClickListener(this@HighlightRuleConfigDialog)
        }.show()
    }

    /** 弹出单条规则的「更多」操作菜单（编辑、导出、分享、删除），屏幕居中显示。 */
    private fun showItemMenu(rule: HighlightRule) {
        itemMenuDialog?.dismiss()
        itemMenuDialog = HighlightRuleItemMenuDialog(
            context = requireContext(),
            title = rule.name.ifBlank { getString(R.string.highlight_rule_unnamed) },
            onEdit = { editRule(rule) },
            onExport = { showExportChooser(listOf(rule)) },
            onShare = { shareRules(listOf(rule)) },
            onDelete = { deleteRule(rule) },
        ).show()
    }

    /**
     * 长按拾起：给个触感和浮起效果，明确告诉用户已经抓住卡片了。
     */
    private fun startDrag(holder: ItemViewHolder) {
        val itemView = holder.itemView
        itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        itemView.animate().cancel()
        itemView.scaleX = DRAG_SCALE
        itemView.scaleY = DRAG_SCALE
        itemView.alpha = DRAG_ALPHA
        itemView.elevation = DRAG_ELEVATION * itemView.resources.displayMetrics.density
        itemTouchHelper?.startDrag(holder)
    }

    /** 拖动结束：恢复卡片常态（缩放、透明度、阴影） */
    private fun clearDragAppearance(itemView: View) {
        itemView.animate().cancel()
        itemView.scaleX = 1f
        itemView.scaleY = 1f
        itemView.alpha = 1f
        itemView.elevation = 2f * itemView.resources.displayMetrics.density
    }

    /** 拖动结束后把列表顺序写回规则表与存储，并刷新副标题。 */
    private fun persistRuleOrder() {
        val ordered = adapter.getItems()
        if (ordered.isEmpty()) return
        if (!viewModel.reorderRules(ordered)) return
        updateSubtitle()
    }

    /** 溢出菜单项点击分发：将各菜单 ID 路由到对应操作方法。 */
    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_select -> showRuleSelector()
            R.id.menu_add -> editRule(null)
            R.id.menu_preset -> showPresetRules()
            R.id.menu_import -> showImportChooser()
            R.id.menu_group -> showGroupManager()
            R.id.menu_share -> shareRules(getFilteredRules())
            R.id.menu_export -> showExportChooser(getFilteredRules())
            R.id.menu_reset -> resetRules()
            else -> return false
        }
        return true
    }

    /** 从 ViewModel 加载全部规则并应用当前分组筛选。 */
    private fun loadRules() {
        viewModel.loadRules()
        applyGroupFilter()
    }

    /** 按当前分组筛选规则后刷新列表、空状态和副标题。 */
    private fun applyGroupFilter() {
        val filtered = getFilteredRules()
        adapter.setItems(filtered)
        updateEmptyState()
        updateSubtitle()
    }

    /** 获取经过当前分组筛选后的规则列表。 */
    private fun getFilteredRules(): List<HighlightRule> {
        return viewModel.filteredRules()
    }

    /** 更新页面副标题，显示当前分组名和筛选后的规则数量。 */
    private fun updateSubtitle() {
        val groupText = viewModel.currentGroup ?: "全部分组"
        val count = getFilteredRules().size
        binding.tvPageSubtitle.text = "$groupText · $count 条规则"
    }

    /** 切换到指定分组并刷新列表，供外部（如分组管理弹窗）调用。 */
    fun switchToGroup(group: String?) {
        viewModel.switchToGroup(group)
        applyGroupFilter()
    }

    /** 根据筛选后的规则数量切换列表和空状态面板的可见性。 */
    private fun updateEmptyState() {
        val filteredCount = getFilteredRules().size
        val empty = filteredCount == 0
        binding.recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
        binding.emptyPanel.visibility = if (empty) View.VISIBLE else View.GONE
    }

    /** 弹出确认对话框，确认后恢复全部预置规则并覆盖自定义规则。 */
    private fun resetRules() {
        alert("恢复默认") {
            setMessage("恢复后会重新生成预置规则，自定义规则会被覆盖。")
            okButton {
                viewModel.resetRules()
                applyGroupFilter()
            }
            cancelButton()
        }
    }

    /** 打开规则编辑弹窗；新增规则时预填当前书籍书名和书源 URL 作为作用范围。 */
    private fun editRule(rule: HighlightRule?) {
        val defaultGroup = rule?.group ?: viewModel.currentGroup
        // 新增规则时，预填当前书籍的书名和书源URL作为作用范围
        val defaultScope = if (rule == null) {
            ReadBook.book?.let { book ->
                val parts = mutableListOf<String>()
                parts.add(book.name)
                if (book.origin.isNotBlank() && !book.isLocal) {
                    parts.add(book.origin)
                }
                parts.joinToString(";")
            }
        } else null
        HighlightRuleEditDialog(rule, defaultGroup, defaultScope) { newRule ->
            viewModel.upsertRule(newRule)
            applyGroupFilter()
        }.show(childFragmentManager, "highlightRuleEdit")
    }

    /** 弹出确认对话框，确认后从 ViewModel 删除指定规则。 */
    private fun deleteRule(rule: HighlightRule) {
        alert("删除") {
            setMessage("确定删除“${rule.name}”吗？")
            okButton {
                viewModel.deleteRule(rule)
                applyGroupFilter()
            }
            cancelButton()
        }
    }

    /** 打开预设规则弹窗，选择后将规则添加到当前列表。 */
    private fun showPresetRules() {
        HighlightPresetRuleDialog(viewModel.currentGroup) { rule ->
            viewModel.addRule(rule)
            applyGroupFilter()
        }.show(childFragmentManager, "highlightPresetRule")
    }

    /** 打开分组管理弹窗，处理分组重命名和分组切换回调。 */
    private fun showGroupManager() {
        HighlightRuleGroupManageDialog(
            onChanged = { oldGroup, newGroup ->
                if (oldGroup != null && viewModel.currentGroup == oldGroup) {
                    viewModel.switchToGroup(newGroup)
                }
                loadRules()
            },
            onSelectGroup = { group ->
                viewModel.switchToGroup(group)
                applyGroupFilter()
            }
        ).show(childFragmentManager, "highlightRuleGroupManage")
    }

    /** 弹出多选对话框，可批量选择规则进行分享或删除。 */
    private fun showRuleSelector() {
        if (viewModel.rules.isEmpty()) {
            context?.toastOnUi("暂无可选择规则")
            return
        }
        val selected = BooleanArray(viewModel.rules.size)
        val names = viewModel.rules.map {
            "${it.name.ifBlank { "未命名规则" }} / ${it.group}"
        }.toTypedArray()
        alert("选择规则") {
            multiChoiceItems(names, selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            positiveButton("分享选中") {
                val picked = viewModel.rules.filterIndexed { index, _ -> selected[index] }
                if (picked.isEmpty()) {
                    requireContext().toastOnUi("请先选择规则")
                } else {
                    shareRules(picked)
                }
            }
            neutralButton("删除选中") {
                val ids = viewModel.rules.filterIndexed { index, _ -> selected[index] }.map { it.id }.toSet()
                if (ids.isEmpty()) {
                    requireContext().toastOnUi("请先选择规则")
                } else {
                    viewModel.deleteRules(ids)
                    applyGroupFilter()
                }
            }
            negativeButton("取消")
        }
    }

    /** 弹出导入方式选择对话框：从剪贴板导入或从文件导入。 */
    private fun showImportChooser() {
        alert(getString(R.string.import_rules)) {
            items(
                listOf(
                    getString(R.string.import_from_clipboard),
                    getString(R.string.import_from_file)
                )
            ) { _, index ->
                when (index) {
                    0 -> importRulesFromClipboard()
                    1 -> importDoc.launch {
                        mode = HandleFileContract.FILE
                        title = getString(R.string.import_rules)
                        allowExtensions = arrayOf("json", "txt")
                    }
                }
            }
        }
    }

    /** 弹出导出方式选择对话框：导出到剪贴板或导出到文件。 */
    private fun showExportChooser(targetRules: List<HighlightRule>) {
        if (targetRules.isEmpty()) {
            context?.toastOnUi("暂无规则可导出")
            return
        }
        alert(getString(R.string.export_rules)) {
            items(
                listOf(
                    getString(R.string.export_to_clipboard),
                    getString(R.string.export_to_file)
                )
            ) { _, index ->
                when (index) {
                    0 -> exportRulesToClipboard(targetRules)
                    1 -> exportRulesToFile(targetRules)
                }
            }
        }
    }

    /** 读取剪贴板文本并解析为高亮规则列表后导入。 */
    private fun importRulesFromClipboard() {
        val clip = requireContext().getClipText()
        if (clip.isNullOrBlank()) {
            context?.toastOnUi(R.string.highlight_rule_clipboard_empty)
            return
        }
        val imported = GSON.fromJsonArray<HighlightRule>(clip).getOrNull()
        if (imported.isNullOrEmpty()) {
            context?.toastOnUi(R.string.highlight_rule_import_invalid)
            return
        }
        viewModel.importRules(imported)
        applyGroupFilter()
        context?.toastOnUi(R.string.highlight_rule_import_success)
    }

    /** 将规则列表序列化为 JSON 并复制到系统剪贴板。 */
    private fun exportRulesToClipboard(targetRules: List<HighlightRule>) {
        requireContext().sendToClip(GSON.toJson(targetRules))
        context?.toastOnUi("已复制 ${targetRules.size} 条规则")
    }

    /** 启动系统文件选择器将规则 JSON 写入用户指定的文件。 */
    private fun exportRulesToFile(targetRules: List<HighlightRule>) {
        pendingExportRules = targetRules
        exportResult.launch {
            mode = HandleFileContract.EXPORT
            fileData = HandleFileContract.FileData(
                "highlightRule.json",
                GSON.toJson(targetRules).toByteArray(),
                "application/json"
            )
        }
    }

    /** 通过系统分享面板将规则 JSON 以纯文本方式分享给其他应用。 */
    private fun shareRules(targetRules: List<HighlightRule>) {
        if (targetRules.isEmpty()) {
            context?.toastOnUi("没有可分享的规则")
            return
        }
        val json = GSON.toJson(targetRules)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, json)
        }
        startActivity(Intent.createChooser(intent, "分享规则"))
    }

    /**
     * 高亮规则列表适配器。
     */
    private inner class HighlightRuleAdapter(context: Context) :
        RecyclerAdapter<HighlightRule, ItemHighlightPresetRuleBinding>(context) {

        /** 创建列表项的 ViewBinding。 */
        override fun getViewBinding(parent: ViewGroup): ItemHighlightPresetRuleBinding {
            return ItemHighlightPresetRuleBinding.inflate(inflater, parent, false)
        }

        /** 注册列表项事件：点击编辑、长按拖动排序、「更多」弹出操作菜单。 */
        override fun registerListener(holder: ItemViewHolder, binding: ItemHighlightPresetRuleBinding) {
            binding.root.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::editRule)
            }
            // 长按直接拾起卡片排序，编辑/导出/分享/删除全部收进「更多」
            val longPress = View.OnLongClickListener {
                if (getItem(holder.layoutPosition) == null) return@OnLongClickListener false
                startDrag(holder)
                true
            }
            binding.root.setOnLongClickListener(longPress)
            binding.tvPreview.setOnLongClickListener(longPress)
            binding.tvPreview.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::editRule)
            }
            binding.tvEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::editRule)
            }
            binding.tvMore.setOnClickListener {
                getItem(holder.layoutPosition)?.let { showItemMenu(it) }
            }
        }

        /** 绑定规则数据到列表项视图：设置名称、样式摘要、正则、预览文本和主题颜色。 */
        override fun convert(
            holder: ItemViewHolder,
            binding: ItemHighlightPresetRuleBinding,
            item: HighlightRule,
            payloads: MutableList<Any>
        ) {
            binding.tvTitle.text = item.name.ifBlank { getString(R.string.highlight_rule_unnamed) }
            binding.tvDesc.text = item.styleSummary()
            binding.tvPattern.text = buildString {
                append(item.group)
                append(" / ")
                append(item.targetScopeLabel())
                append(" / ")
                append(item.displayPattern())
                // 显示书籍作用域信息
                if (!item.scope.isNullOrBlank()) {
                    append(" / 仅: ")
                    append(item.scope!!.replace(";", "; ").trim())
                }
                if (!item.excludeScope.isNullOrBlank()) {
                    append(" / 排除: ")
                    append(item.excludeScope!!.replace(";", "; ").trim())
                }
                if (!item.layoutScope.isNullOrBlank()) {
                    append(" / 排版: ")
                    append(item.layoutScope!!.replace(";", "; ").trim())
                }
                if (item.themeScope != HighlightRule.THEME_ALL) {
                    append(" / ")
                    append(item.themeScopeLabel())
                }
            }
            binding.tvPreview.setPreview(item, primaryTextColor)

            val density = binding.root.context.resources.displayMetrics.density
            binding.root.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * density
                setColor(cardBgColor)
                setStroke((1f * density).toInt().coerceAtLeast(1), cardStrokeColor)
            }
            binding.tvPattern.background?.mutate()?.setTint(accentColor)
            binding.tvPreview.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f * density
                setColor(previewBgColor)
                setStroke((1f * density).toInt().coerceAtLeast(1), previewStrokeColor)
            }
            binding.tvEdit.background?.mutate()?.setTint(accentColor)

            binding.switchEnable.setOnCheckedChangeListener(null)
            binding.switchEnable.isChecked = item.enabled
            binding.switchEnable.trackTintList = android.content.res.ColorStateList.valueOf(
                if (item.enabled) accentColor else 0xFF666666.toInt()
            )
            binding.switchEnable.thumbTintList = android.content.res.ColorStateList.valueOf(
                if (item.enabled) accentColor else 0xFF999999.toInt()
            )
            binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
                if (item.enabled != isChecked) {
                    viewModel.setRuleEnabled(item, isChecked)
                    applyGroupFilter()
                }
            }

            binding.tvTitle.setTextColor(primaryTextColor)
            binding.tvDesc.setTextColor(secondaryTextColor)
            binding.tvPattern.setTextColor(
                if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            )
            binding.tvPreviewLabel.setTextColor(secondaryTextColor)
            binding.tvPreview.setTextColor(primaryTextColor)

            (binding.tvEdit.getChildAt(0) as? android.widget.ImageView)
                ?.setColorFilter(
                    if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(),
                    PorterDuff.Mode.SRC_IN
                )
            (binding.tvEdit.getChildAt(1) as? android.widget.TextView)
                ?.setTextColor(
                    if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                )

            (binding.tvMore.getChildAt(0) as? android.widget.ImageView)
                ?.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
            (binding.tvMore.getChildAt(1) as? android.widget.TextView)
                ?.setTextColor(primaryTextColor)
            binding.tvMore.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6f * density
                setColor(cardBgColor)
                setStroke((1f * density).toInt().coerceAtLeast(1), cardStrokeColor)
            }
        }
    }

    private companion object {
        /** 拖动中的卡片放大比例、透明度与阴影高度（dp） */
        const val DRAG_SCALE = 1.03f
        const val DRAG_ALPHA = 0.92f
        const val DRAG_ELEVATION = 10f
    }
}
