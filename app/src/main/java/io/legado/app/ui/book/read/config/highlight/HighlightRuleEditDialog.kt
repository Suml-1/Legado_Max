package io.legado.app.ui.book.read.config.highlight

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogHighlightRuleEditBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.font.FontSelectDialog
import io.legado.app.utils.showDialogFragment
import kotlin.math.roundToInt

/**
 * 高亮规则单条编辑弹窗。
 *
 * 负责绑定输入控件、颜色选择器、背景图选择器和预览刷新；
 * 当前编辑规则和分组列表由 `HighlightRuleEditViewModel` 保存。
 */
class HighlightRuleEditDialog @JvmOverloads constructor(
    private val sourceRule: HighlightRule? = null,
    private val defaultGroup: String? = null,
    private val defaultScope: String? = null,
    private val onSave: (HighlightRule) -> Unit = {},
) : BaseDialogFragment(R.layout.dialog_highlight_rule_edit, true),
    ColorPickerDialogListener,
    FontSelectDialog.CallBack {

    private val binding by viewBinding(DialogHighlightRuleEditBinding::bind)
    private val viewModel: HighlightRuleEditViewModel by viewModels()
    private var editingRule: HighlightRule
        get() = viewModel.editingRule
        set(value) {
            viewModel.editingRule = value
        }
    private val groupItems: List<String>
        get() = viewModel.groupItems
    private var primaryTextColor = 0
    private var secondaryTextColor = 0
    private var accentColor = 0
    private var isRegexMode: Boolean
        get() = viewModel.isRegexMode
        set(value) {
            viewModel.isRegexMode = value
        }

    private val selectImageResult = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            // 选择图片时，清除背景颜色
            editingRule.bgColor = null
            val rawPath = RealPathUtil.getPath(requireContext(), uri) ?: uri.toString()
            val savedPath = HighlightRuleBackgroundManager.copyToInternal(requireContext(), rawPath)
            editingRule.bgImage = savedPath ?: rawPath
            binding.etBgImage.setText(savedPath ?: rawPath)
            updateBgPreview()
            updatePreview()
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.85f)
        dialog?.window?.setGravity(Gravity.BOTTOM)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initTheme()
        viewModel.initialize(sourceRule, defaultGroup, defaultScope)
        attachBottomSheetDismiss(
            binding.dragHandle,
            binding.sheetContainer,
        ) { dismissAllowingStateLoss() }

        binding.tvPageTitle.text =
            getString(if (sourceRule == null) R.string.highlight_rule_add else R.string.highlight_rule_edit)

        binding.spGroup.adapter = object : ArrayAdapter<String>(
            requireContext(),
            R.layout.item_text_common,
            groupItems,
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                if (view is android.widget.TextView) view.setTextColor(primaryTextColor)
                return view
            }
            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getDropDownView(position, convertView, parent)
                if (view is android.widget.TextView) view.setTextColor(primaryTextColor)
                return view
            }
        }.apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        binding.spTarget.adapter = object : ArrayAdapter<String>(
            requireContext(),
            R.layout.item_text_common,
            listOf("作用于全部", "作用于标题", "作用于正文"),
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                if (view is android.widget.TextView) view.setTextColor(primaryTextColor)
                return view
            }
            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getDropDownView(position, convertView, parent)
                if (view is android.widget.TextView) view.setTextColor(primaryTextColor)
                return view
            }
        }.apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        binding.spUnderlineMode.adapter = object : ArrayAdapter<String>(
            requireContext(),
            R.layout.item_text_common,
            listOf("无", "实线下划线", "虚线下划线", "波浪下划线", "双下划线", "自定义SVG", "删除线", "斜体", "方框"),
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                if (view is android.widget.TextView) view.setTextColor(primaryTextColor)
                return view
            }
            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getDropDownView(position, convertView, parent)
                if (view is android.widget.TextView) view.setTextColor(primaryTextColor)
                return view
            }
        }.apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        binding.spBgImageFit.adapter = object : ArrayAdapter<String>(
            requireContext(),
            R.layout.item_text_common,
            listOf("平铺", "拉伸填充", "居中裁剪", "九宫格"),
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                if (view is android.widget.TextView) view.setTextColor(primaryTextColor)
                return view
            }
            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getDropDownView(position, convertView, parent)
                if (view is android.widget.TextView) view.setTextColor(primaryTextColor)
                return view
            }
        }.apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        binding.spThemeScope.adapter = object : ArrayAdapter<String>(
            requireContext(),
            R.layout.item_text_common,
            listOf(getString(R.string.highlight_rule_theme_scope_all), getString(R.string.highlight_rule_theme_scope_light), getString(R.string.highlight_rule_theme_scope_dark)),
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                if (view is android.widget.TextView) view.setTextColor(primaryTextColor)
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                if (view is android.widget.TextView) view.setTextColor(primaryTextColor)
                return view
            }
        }.apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }

        bindData()
        bindEvents()
        updatePreview()
    }

    override fun observeLiveBus() {
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) {
            if (it.contains(1) || it.contains(2)) {
                initTheme()
                updatePreview()
            }
        }
    }

    private fun initTheme() {
        val bg = requireContext().bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        primaryTextColor = requireContext().getPrimaryTextColor(isLight)
        secondaryTextColor = requireContext().getSecondaryTextColor(isLight)
        accentColor = requireContext().accentColor

        val cardBg = if (isLight) {
            ColorUtils.blendColors(bg, 0xFFFFFFFF.toInt(), 0.7f)
        } else {
            ColorUtils.blendColors(bg, 0xFFFFFFFF.toInt(), 0.08f)
        }

        val cardStrokeColor = if (isLight) {
            ColorUtils.blendColors(0xFF000000.toInt(), bg, 0.88f)
        } else {
            ColorUtils.blendColors(0xFFFFFFFF.toInt(), bg, 0.85f)
        }

        val inputStrokeColor = if (isLight) {
            ColorUtils.blendColors(0xFF000000.toInt(), bg, 0.82f)
        } else {
            ColorUtils.blendColors(0xFFFFFFFF.toInt(), bg, 0.80f)
        }

        val inputBgColor = if (isLight) {
            ColorUtils.blendColors(bg, 0xFFFFFFFF.toInt(), 0.5f)
        } else {
            ColorUtils.blendColors(bg, 0xFFFFFFFF.toInt(), 0.06f)
        }

        val density = resources.displayMetrics.density

        binding.sheetContainer.background?.mutate()?.setTint(bg)
        binding.tvPageTitle.setTextColor(primaryTextColor)

        binding.ivBack.background?.mutate()?.setTint(cardBg)
        binding.ivBack.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)

        binding.tvSaveAction.background?.mutate()?.setTint(accentColor)
        binding.tvSaveAction.setTextColor(
            if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(),
        )

        val disabledColor = ColorUtils.blendColors(accentColor, secondaryTextColor, 0.6f)
        binding.switchEnable.trackTintList = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accentColor, disabledColor),
        )
        binding.switchEnable.thumbTintList = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accentColor, secondaryTextColor),
        )

        val cardDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f * density
            setColor(cardBg)
            setStroke((1f * density).toInt().coerceAtLeast(1), cardStrokeColor)
        }
        binding.cardInfo.background = cardDrawable
        binding.cardStyle.background = makeCardDrawable(cardBg, cardStrokeColor, 24f, density)
        binding.cardPreview.background = makeCardDrawable(cardBg, cardStrokeColor, 24f, density)

        binding.etPattern.setTextColor(primaryTextColor)
        binding.etPattern.setHintTextColor(secondaryTextColor)
        binding.etName.setTextColor(primaryTextColor)
        binding.etName.setHintTextColor(secondaryTextColor)
        binding.etTextColor.setTextColor(primaryTextColor)
        binding.etTextColor.setHintTextColor(secondaryTextColor)
        binding.etUnderlineColor.setTextColor(primaryTextColor)
        binding.etUnderlineColor.setHintTextColor(secondaryTextColor)
        binding.etUnderlineWidth.setTextColor(primaryTextColor)
        binding.etUnderlineWidth.setHintTextColor(secondaryTextColor)
        binding.etUnderlineOffset.setHintTextColor(secondaryTextColor)
        binding.etUnderlineOffset.setTextColor(primaryTextColor)
        binding.etSvgPath.setTextColor(primaryTextColor)
        binding.etSvgPath.setHintTextColor(secondaryTextColor)
        binding.etBgImage.setTextColor(primaryTextColor)
        binding.etBgImage.setHintTextColor(secondaryTextColor)
        binding.tvBgImagePick.setTextColor(primaryTextColor)
        binding.etFont.setTextColor(primaryTextColor)
        binding.etFont.setHintTextColor(secondaryTextColor)
        binding.tvFontPick.setTextColor(primaryTextColor)
        binding.etSampleText.setTextColor(primaryTextColor)
        binding.etSampleText.setHintTextColor(secondaryTextColor)
        binding.etScope.setTextColor(primaryTextColor)
        binding.etScope.setHintTextColor(secondaryTextColor)
        binding.etExcludeScope.setTextColor(primaryTextColor)
        binding.etExcludeScope.setHintTextColor(secondaryTextColor)

        // 将预览文本颜色设置为主题颜色
        binding.tvPreview.setTextColor(primaryTextColor)
        binding.tvPatternError.setTextColor(requireContext().getColor(R.color.error))
        binding.tvBgImageScale.setTextColor(secondaryTextColor)
        binding.tvNpAdjustAction.setTextColor(accentColor)

        binding.tvRegexToggle.setTextColor(primaryTextColor)
        binding.tvRegexToggle.background?.mutate()?.setTint(bg)
        binding.tvWidthMinus.setTextColor(primaryTextColor)
        binding.tvWidthPlus.setTextColor(primaryTextColor)
        binding.tvOffsetMinus.setTextColor(primaryTextColor)
        binding.tvOffsetPlus.setTextColor(primaryTextColor)

        val inputBg = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        val previewBg = makeInputDrawable(inputBgColor, inputStrokeColor, 16f, density)
        binding.etPattern.background = inputBg
        binding.etName.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.spGroup.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.spTarget.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.etTextColor.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.spUnderlineMode.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.etUnderlineColor.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.etSvgPath.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.tvPreview.background = previewBg
        binding.etBgImage.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.tvBgImagePick.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.etFont.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.tvFontPick.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.etSampleText.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.etScope.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.etExcludeScope.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.etLayoutScope.setTextColor(primaryTextColor)
        binding.etLayoutScope.setHintTextColor(secondaryTextColor)
        binding.etLayoutScope.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.spBgImageFit.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.spThemeScope.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.tvWidthMinus.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.tvWidthPlus.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.etUnderlineWidth.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.tvOffsetMinus.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.tvOffsetPlus.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)
        binding.etUnderlineOffset.background = makeInputDrawable(inputBgColor, inputStrokeColor, 14f, density)

        // 设置 Spinner 下拉弹窗背景色，避免深色主题下白底白字
        val popupBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 14f * density
            setColor(bg)
            setStroke((1f * density).toInt().coerceAtLeast(1), cardStrokeColor)
        }
        binding.spGroup.setPopupBackgroundDrawable(popupBg)
        binding.spTarget.setPopupBackgroundDrawable(popupBg)
        binding.spUnderlineMode.setPopupBackgroundDrawable(popupBg)
        binding.spBgImageFit.setPopupBackgroundDrawable(popupBg)
        binding.spThemeScope.setPopupBackgroundDrawable(popupBg)

        // 递归遍历三个卡片容器，将静态标签的文字颜色替换为动态主题色
        applyThemeToStaticLabels()
        updateRegexToggle()
    }

    /**
     * 递归遍历卡片容器中的所有 TextView，将 XML 中使用 @color/primaryText 和 @color/secondaryText
     * 的静态标签替换为动态主题颜色。
     * 已在 initTheme() 中显式设置过颜色的控件不会受影响（因为它们的 currentTextColor
     * 已经不是静态颜色值了）。
     */
    private fun applyThemeToStaticLabels() {
        val staticPrimary = requireContext().getColor(R.color.primaryText)
        val staticSecondary = requireContext().getColor(R.color.secondaryText)
        listOf(binding.cardInfo, binding.cardStyle, binding.cardPreview).forEach { card ->
            applyThemeColorRecursive(card, staticPrimary, staticSecondary)
        }
    }

    private fun applyThemeColorRecursive(
        view: View,
        staticPrimary: Int,
        staticSecondary: Int,
    ) {
        if (view is android.widget.TextView) {
            val currentColor = view.currentTextColor
            if (currentColor == staticPrimary) {
                view.setTextColor(primaryTextColor)
            } else if (currentColor == staticSecondary) {
                view.setTextColor(secondaryTextColor)
            }
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyThemeColorRecursive(view.getChildAt(i), staticPrimary, staticSecondary)
            }
        }
    }

    private fun makeCardDrawable(
        fillColor: Int,
        strokeColor: Int,
        cornerDp: Float,
        density: Float,
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = cornerDp * density
        setColor(fillColor)
        setStroke((1f * density).toInt().coerceAtLeast(1), strokeColor)
    }

    private fun makeInputDrawable(
        fillColor: Int,
        strokeColor: Int,
        cornerDp: Float,
        density: Float,
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = cornerDp * density
        setColor(fillColor)
        setStroke((1f * density).toInt().coerceAtLeast(1), strokeColor)
    }

    private fun bindData() {
        binding.switchEnable.isChecked = editingRule.enabled
        binding.etName.setText(editingRule.name)
        binding.etPattern.setText(editingRule.pattern)
        binding.etTextColor.setText(editingRule.textColor?.toHexColor().orEmpty())
        binding.etUnderlineColor.setText(editingRule.underlineColor?.toHexColor().orEmpty())
        binding.etUnderlineWidth.setText(editingRule.underlineWidth.toString())
        binding.etUnderlineOffset.setText(editingRule.underlineOffset.formatDistance())
        binding.etSvgPath.setText(editingRule.underlineSvgPath.orEmpty())
        binding.etBgImage.setText(editingRule.bgImage.orEmpty())
        // 如果有背景颜色，显示颜色值；如果有背景图片，显示图片路径
        if (!editingRule.bgImage.isNullOrBlank()) {
            binding.etBgImage.setText(editingRule.bgImage)
        } else if (editingRule.bgColor != null) {
            binding.etBgImage.setText(editingRule.bgColor!!.toHexColor())
        } else {
            binding.etBgImage.setText("")
        }
        updateFontText()
        binding.etSampleText.setText(editingRule.sampleText.ifBlank { editingRule.normalizedSampleText() })
        binding.spBgImageFit.setSelection(editingRule.bgImageFit.coerceIn(0, 3))
        updateNpAdjustRow()
        binding.sbBgImageScale.progress = (editingRule.bgImageScale.coerceIn(0.1f, 5f) * 10).toInt()
        binding.tvBgImageScale.text = "${editingRule.bgImageScale.coerceIn(0.1f, 5f).formatScale()}x"
        binding.spUnderlineMode.setSelection(editingRule.underlineMode.coerceIn(0, 8))
        val groupIndex = groupItems.indexOf(editingRule.group).takeIf { it >= 0 } ?: 0
        binding.spGroup.setSelection(groupIndex)
        binding.spTarget.setSelection(editingRule.targetScope.coerceIn(0, 2))
        // 书籍作用域字段绑定
        binding.etScope.setText(editingRule.scope.orEmpty())
        binding.etExcludeScope.setText(editingRule.excludeScope.orEmpty())
        // 排版作用范围显示
        updateLayoutScopeText()
        // 主题作用范围绑定
        val themePos = when (editingRule.themeScope) {
            HighlightRule.THEME_LIGHT -> 1
            HighlightRule.THEME_DARK -> 2
            else -> 0
        }
        binding.spThemeScope.setSelection(themePos)

        updateColorPreview(binding.viewTextColorPreview, editingRule.textColor)
        updateColorPreview(binding.viewUnderlineColorPreview, editingRule.underlineColor)
        // 更新背景预览：如果有背景图片显示图片，否则显示颜色
        updateBgPreview()

        updateSvgPathVisibility(editingRule.underlineMode)
        updateRegexToggle()
    }

    private fun bindEvents() {
        binding.sheetContainer.setOnClickListener { }
        binding.ivBack.setOnClickListener {
            dismissAllowingStateLoss()
        }
        binding.tvSaveAction.setOnClickListener {
            saveRule()
        }
        binding.llTextColor.setOnClickListener {
            showColorPicker(1, editingRule.textColor ?: Color.BLACK)
        }
        binding.llUnderlineColor.setOnClickListener {
            showColorPicker(2, editingRule.underlineColor ?: Color.BLACK)
        }
        // 点击背景预览块可以选择颜色
        binding.viewBgImagePreview.setOnClickListener {
            showColorPicker(3, editingRule.bgColor ?: Color.BLACK)
        }
        binding.tvRegexToggle.setOnClickListener {
            isRegexMode = !isRegexMode
            editingRule.isRegex = isRegexMode
            updateRegexToggle()
            updatePreview()
        }
        binding.tvWidthMinus.setOnClickListener {
            adjustWidth(-0.5f)
        }
        binding.tvWidthPlus.setOnClickListener {
            adjustWidth(0.5f)
        }
        binding.tvOffsetMinus.setOnClickListener {
            adjustOffset(-1f)
        }
        binding.tvOffsetPlus.setOnClickListener {
            adjustOffset(1f)
        }
        binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
            editingRule.enabled = isChecked
        }
        binding.etName.doAfterTextChanged {
            editingRule.name = it?.toString().orEmpty()
        }
        binding.etPattern.doAfterTextChanged {
            editingRule.pattern = it?.toString().orEmpty()
            updatePreview()
        }
        binding.etTextColor.doAfterTextChanged {
            editingRule.textColor = parseColorOrNull(it?.toString().orEmpty())
            updateColorPreview(binding.viewTextColorPreview, editingRule.textColor)
            updatePreview()
        }
        binding.etUnderlineColor.doAfterTextChanged {
            editingRule.underlineColor = parseColorOrNull(it?.toString().orEmpty())
            updateColorPreview(binding.viewUnderlineColorPreview, editingRule.underlineColor)
            updatePreview()
        }
        binding.etUnderlineWidth.doAfterTextChanged {
            editingRule.underlineWidth = it?.toString()?.toFloatOrNull()?.coerceIn(0.1f, 10f) ?: 1f
            updatePreview()
        }
        binding.etUnderlineOffset.doAfterTextChanged {
            editingRule.underlineOffset = it?.toString()?.toFloatOrNull()?.coerceIn(0f, 20f) ?: 2f
            updatePreview()
        }
        binding.etSvgPath.doAfterTextChanged {
            editingRule.underlineSvgPath = it?.toString().orEmpty()
            updatePreview()
        }
        binding.etBgImage.doAfterTextChanged {
            val text = it?.toString().orEmpty()
            // 判断输入的是颜色值还是图片路径
            val color = parseColorOrNull(text)
            if (color != null) {
                // 输入的是颜色值
                editingRule.bgColor = color
                editingRule.bgImage = null
            } else if (text.isNotBlank()) {
                // 输入的是图片路径
                editingRule.bgImage = text
                editingRule.bgColor = null
            } else {
                // 清空
                editingRule.bgImage = null
                editingRule.bgColor = null
            }
            updateBgPreview()
            updatePreview()
        }
        binding.etSampleText.doAfterTextChanged {
            editingRule.sampleText = it?.toString().orEmpty()
            updatePreview()
        }
        binding.tvBgImagePick.setOnClickListener {
            showBgImagePicker()
        }
        binding.llNpAdjust.setOnClickListener {
            showNineSliceAdjustDialog()
        }
        binding.etFont.setOnClickListener {
            showDialogFragment<FontSelectDialog> {
                putBoolean(FontSelectDialog.ARG_FOR_RULE, true)
            }
        }
        binding.tvFontPick.setOnClickListener {
            showDialogFragment<FontSelectDialog> {
                putBoolean(FontSelectDialog.ARG_FOR_RULE, true)
            }
        }
        binding.spBgImageFit.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    editingRule.bgImageFit = position
                    updateNpAdjustRow()
                    updatePreview()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        binding.sbBgImageScale.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val scale = (progress.coerceAtLeast(1) / 10f).coerceIn(0.1f, 5f)
                    editingRule.bgImageScale = scale
                    binding.tvBgImageScale.text = "${scale.formatScale()}x"
                    if (fromUser) updatePreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            },
        )
        binding.spUnderlineMode.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    editingRule.underlineMode = position
                    updateSvgPathVisibility(position)
                    updatePreview()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        binding.spGroup.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    editingRule.group = groupItems.getOrElse(position) { HighlightRuleGroupStore.DEFAULT_GROUP }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        binding.spTarget.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    editingRule.targetScope = position.coerceIn(0, 2)
                    updatePreview()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        // 书籍作用域输入监听，空白时存为null
        binding.etScope.doAfterTextChanged {
            editingRule.scope = it?.toString().orEmpty().takeIf { it.isNotBlank() }
        }
        binding.etExcludeScope.doAfterTextChanged {
            editingRule.excludeScope = it?.toString().orEmpty().takeIf { it.isNotBlank() }
        }
        // 排版作用范围多选弹窗
        val openLayoutScopePicker = {
            val layoutNames = ReadBookConfig.configList.map { it.name }
            val savedSet = editingRule.layoutScope?.split(";")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            val checkedItems = BooleanArray(layoutNames.size) { index -> layoutNames[index] in savedSet }
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.highlight_rule_layout_scope))
                .setMultiChoiceItems(layoutNames.toTypedArray(), checkedItems) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val selected = layoutNames.filterIndexed { index, _ -> checkedItems[index] }
                    editingRule.layoutScope = if (selected.isEmpty() || selected.size == layoutNames.size) {
                        null
                    } else {
                        selected.joinToString(";")
                    }
                    updateLayoutScopeText()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        binding.etLayoutScope.setOnClickListener { openLayoutScopePicker() }
        binding.spThemeScope.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    editingRule.themeScope = when (position) {
                        1 -> HighlightRule.THEME_LIGHT
                        2 -> HighlightRule.THEME_DARK
                        else -> HighlightRule.THEME_ALL
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
    }

    private fun updateLayoutScopeText() {
        val layoutScopeVal = editingRule.layoutScope
        if (layoutScopeVal.isNullOrBlank()) {
            binding.etLayoutScope.setText("")
            binding.etLayoutScope.hint = getString(R.string.highlight_rule_layout_scope_all)
        } else {
            val names = layoutScopeVal.split(";").map { it.trim() }.filter { it.isNotBlank() }
            binding.etLayoutScope.setText(
                if (names.size == 1) {
                    names[0]
                } else {
                    getString(R.string.highlight_rule_layout_scope_count, names.size)
                },
            )
        }
    }

    /**
     * 更新字体输入框显示，只展示字体文件名，路径本体保存在规则里
     */
    private fun updateFontText() {
        val fontPath = editingRule.font
        binding.etFont.setText(
            if (fontPath.isNullOrBlank()) {
                ""
            } else {
                editingRule.fontDisplayName()
            },
        )
    }

    override val curFontPath: String
        get() = editingRule.font.orEmpty()

    override fun selectFont(path: String) {
        editingRule.font = path.takeIf { it.isNotBlank() }
        updateFontText()
        updatePreview()
    }

    private fun updateRegexToggle() {
        binding.tvRegexToggle.text = getString(
            if (isRegexMode) {
                R.string.explore_block_rule_regex_mode
            } else {
                R.string.explore_block_rule_keyword_mode
            },
        )
        if (isRegexMode) {
            binding.tvRegexToggle.setTextColor(accentColor)
        } else {
            binding.tvRegexToggle.setTextColor(primaryTextColor)
        }
    }

    private fun adjustWidth(delta: Float) {
        val current = binding.etUnderlineWidth.text?.toString()?.toFloatOrNull() ?: 1f
        val newValue = (current + delta).coerceIn(0.1f, 10f)
        binding.etUnderlineWidth.setText(String.format("%.1f", newValue))
    }

    private fun adjustOffset(delta: Float) {
        val current = binding.etUnderlineOffset.text?.toString()?.toFloatOrNull() ?: 2f
        val newValue = (current + delta).coerceIn(0f, 20f)
        binding.etUnderlineOffset.setText(newValue.formatDistance())
    }

    private fun updateColorPreview(view: View, color: Int?) {
        val drawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 8f * resources.displayMetrics.density
            setColor(color ?: 0xFFDDDDDD.toInt())
            setStroke(2 * resources.displayMetrics.density.toInt(), 0xFFBBBBBB.toInt())
        }
        view.background = drawable
    }

    private fun updateSvgPathVisibility(mode: Int) {
        binding.llSvgPath.visibility = if (mode == 5) View.VISIBLE else View.GONE
    }

    private fun updateBgPreview() {
        // 如果有背景图片，显示图片预览
        val bgImage = editingRule.bgImage.orEmpty()
        if (bgImage.isNotBlank()) {
            val bitmap = HighlightRuleBackgroundManager.getBitmap(bgImage)
            if (bitmap != null) {
                val drawable = android.graphics.drawable.BitmapDrawable(resources, bitmap)
                binding.viewBgImagePreview.background = drawable
            } else {
                // 图片加载失败时显示默认颜色
                updateColorPreview(binding.viewBgImagePreview, null)
            }
            return
        }
        // 如果有背景颜色，显示颜色预览
        val bgColor = editingRule.bgColor
        if (bgColor != null) {
            updateColorPreview(binding.viewBgImagePreview, bgColor)
            return
        }
        // 都没有，显示默认颜色（和文本颜色预览块一样）
        updateColorPreview(binding.viewBgImagePreview, null)
    }

    private fun showBgImagePicker() {
        val bgImages = try {
            requireContext().assets.list("bg")?.toList().orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        val options = mutableListOf<String>()
        val assetItems = mutableListOf<String>()
        options.add("从手机选择图片")
        bgImages.forEach { img ->
            options.add(img)
            assetItems.add(img)
        }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("选择背景图片")
            .setItems(options.toTypedArray()) { _, which ->
                // 选择图片时，清除背景颜色
                editingRule.bgColor = null
                if (which == 0) {
                    selectImageResult.launch {
                        mode = HandleFileContract.IMAGE
                        title = "选择背景图片"
                    }
                } else {
                    val selected = "assets://bg/${assetItems[which - 1]}"
                    editingRule.bgImage = selected
                    binding.etBgImage.setText(selected)
                    updateBgPreview()
                    updatePreview()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** 九宫格调整入口行：仅在适配方式为九宫格且已设置背景图时显示 */
    private fun updateNpAdjustRow() {
        val isNineSlice = binding.spBgImageFit.selectedItemPosition == 3
        val hasImage = editingRule.bgImage?.isNotBlank() == true
        binding.llNpAdjust.visibility = if (isNineSlice && hasImage) View.VISIBLE else View.GONE
        if (isNineSlice && hasImage) {
            binding.tvNpSummary.text = formatNpSummary()
        }
    }

    private fun formatNpSummary(): String {
        fun Float.percent() = "${(this * 100).roundToInt()}%"
        val strategy = when (HighlightRule.resolvedBleedMode(editingRule.bgBleedMode)) {
            HighlightRule.BLEED_STRICT -> "严格"
            HighlightRule.BLEED_FORCE -> "强制"
            else -> "智能"
        }
        return "左${editingRule.npLeft.percent()} 上${editingRule.npTop.percent()} " +
            "右${editingRule.npRight.percent()} 下${editingRule.npBottom.percent()}\n" +
            "$strategy 间距 左${formatEm(editingRule.bgSpacingLeft)} 右${formatEm(editingRule.bgSpacingRight)} " +
            "上${formatEm(editingRule.bgSpacingTop)} 下${formatEm(editingRule.bgSpacingBottom)}"
    }

    /** 间距的展示格式：固定两位小数并带 em 单位 */
    private fun formatEm(value: Float): String = String.format(java.util.Locale.US, "%.2fem", value)

    /**
     * 九宫格调整弹窗：上方预览图叠加四条红色分割线，下方为调整区。
     *
     * 四条分割比例滑条（左/上/右/下）含义与 .9.png 的拉伸标记一致：分割线一侧为固定不拉伸的
     * 边框区；左右两侧相加、上下两侧相加均不超过 100%，拖动超限时压回当前滑条自身。
     *
     * 另有「外扩策略」开关（严格/智能/强制）与左右/上下四边的间距滑条：策略决定自动外扩量
     * （严格不外扩、智能只占用邻接空白、强制按四角厚度外扩并推开邻字），
     * 间距按四个方向独立可调，为正表示把背景向外撑大、为负向内收。
     *
     * 取消按钮左侧另有一个左对齐的「重置」：把本弹窗内的全部取值回退到默认（分割比例 0.1、
     * 智能外扩、四边间距 0），只改弹窗内的临时值，仍需「确定」才写入规则。
     */
    private fun showNineSliceAdjustDialog() {
        val bgImage = editingRule.bgImage?.takeIf { it.isNotBlank() } ?: return
        val bitmap = HighlightRuleBackgroundManager.getBitmap(bgImage)
        val density = resources.displayMetrics.density
        var npLeft = editingRule.npLeft.coerceIn(0f, 1f)
        var npTop = editingRule.npTop.coerceIn(0f, 1f)
        var npRight = editingRule.npRight.coerceIn(0f, 1f)
        var npBottom = editingRule.npBottom.coerceIn(0f, 1f)
        var bleedMode = HighlightRule.resolvedBleedMode(editingRule.bgBleedMode)
        var spacingLeft = editingRule.bgSpacingLeft
        var spacingRight = editingRule.bgSpacingRight
        var spacingTop = editingRule.bgSpacingTop
        var spacingBottom = editingRule.bgSpacingBottom

        val preview = NineSlicePreviewView(requireContext(), bitmap, npLeft, npTop, npRight, npBottom).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (200 * density).toInt(),
            ).apply { bottomMargin = (8 * density).toInt() }
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), 0)
            addView(preview)
        }

        // 已创建的滑条按 key 记录，供对侧滑条查询上限（左右/上下两两联动）
        val bars = HashMap<String, SeekBar>(4)
        // 重置时四个比例要一起归位，中途会被"相加不超过 100%"压回，故重置期间跳过该限制
        var resetting = false
        // 各间距滑条的回退动作，供重置按钮统一调用
        val resetActions = ArrayList<() -> Unit>(4)
        fun limitOf(oppositeKey: String): Int {
            val opposite = bars[oppositeKey] ?: return 100
            return 100 - opposite.progress
        }

        fun sliderRow(
            label: String,
            key: String,
            oppositeKey: String,
            initial: Float,
            onValue: (Float) -> Unit,
        ) {
            val percentText = TextView(requireContext()).apply {
                text = "${(initial * 100).roundToInt()}%"
                textSize = 13f
                setTextColor(primaryTextColor)
            }
            val seekBar = SeekBar(requireContext()).apply {
                max = 100
                progress = (initial * 100).roundToInt()
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = (6 * density).toInt() }
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        val limit = limitOf(oppositeKey)
                        if (!resetting && progress > limit) {
                            // 压回后再次触发本回调（fromUser=false），在该分支完成刷新
                            sb?.progress = limit
                            return
                        }
                        onValue(progress / 100f)
                        percentText.text = "$progress%"
                        preview.invalidate()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                    override fun onStopTrackingTouch(sb: SeekBar?) = Unit
                })
            }
            bars[key] = seekBar
            fun adjustButton(text: String, delta: Int) = TextView(requireContext()).apply {
                this.text = text
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(primaryTextColor)
                setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
                setOnClickListener { seekBar.progress = (seekBar.progress + delta).coerceIn(0, limitOf(oppositeKey)) }
            }
            container.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
                addView(TextView(requireContext()).apply {
                    text = label
                    textSize = 13f
                    setTextColor(primaryTextColor)
                })
                addView(adjustButton("−", -1))
                addView(seekBar)
                addView(adjustButton("+", 1))
                addView(percentText.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { width = (40 * density).toInt(); gravity = Gravity.CENTER }
                })
            })
        }

        sliderRow("左", "left", "right", npLeft) { npLeft = it; preview.npLeft = it }
        sliderRow("右", "right", "left", npRight) { npRight = it; preview.npRight = it }
        sliderRow("上", "top", "bottom", npTop) { npTop = it; preview.npTop = it }
        sliderRow("下", "bottom", "top", npBottom) { npBottom = it; preview.npBottom = it }

        // em 滑条：以 0.01em 为一格，[rangeMin, rangeMax] 为可调区间，[default] 为重置回退值
        fun emSliderRow(
            label: String,
            initial: Float,
            rangeMin: Float,
            rangeMax: Float,
            default: Float,
            onValue: (Float) -> Unit,
        ) {
            fun toProgress(value: Float) = ((value - rangeMin) * 100).roundToInt()
            fun toValue(progress: Int) = progress / 100f + rangeMin
            val valueText = TextView(requireContext()).apply {
                text = formatEm(initial)
                textSize = 13f
                setTextColor(primaryTextColor)
            }
            val seekBar = SeekBar(requireContext()).apply {
                max = ((rangeMax - rangeMin) * 100).roundToInt()
                progress = toProgress(initial).coerceIn(0, max)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = (6 * density).toInt() }
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        val value = toValue(progress)
                        onValue(value)
                        valueText.text = formatEm(value)
                    }

                    override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                    override fun onStopTrackingTouch(sb: SeekBar?) = Unit
                })
            }
            resetActions.add { seekBar.progress = toProgress(default).coerceIn(0, seekBar.max) }
            fun adjustButton(text: String, delta: Int) = TextView(requireContext()).apply {
                this.text = text
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(primaryTextColor)
                setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
                setOnClickListener {
                    seekBar.progress = (seekBar.progress + delta).coerceIn(0, seekBar.max)
                }
            }
            container.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
                addView(
                    TextView(requireContext()).apply {
                        text = label
                        textSize = 13f
                        setTextColor(primaryTextColor)
                    },
                    LinearLayout.LayoutParams(
                        (60 * density).toInt(),
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(adjustButton("−", -1))
                addView(seekBar)
                addView(adjustButton("+", 1))
                addView(valueText.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { width = (52 * density).toInt(); gravity = Gravity.CENTER }
                })
            })
        }

        // 外扩策略：严格 / 智能 / 强制，用 −/+ 循环切换，切换时同步刷新下面的一行说明
        val strategyNames = arrayOf("严格", "智能", "强制")
        val strategyHints = arrayOf(
            "严格：背景只覆盖匹配到的文字，绝不向外扩。",
            "智能（默认）：只占用邻接的空白——左右借用空格，上下吃掉一半行距，不会压到相邻文字。",
            "强制：按四边分割比例向外扩展，并把左右邻字自动推开一个正文字距，文字会整体仍保持两端对齐。",
        )
        val strategyText = TextView(requireContext()).apply {
            text = strategyNames[bleedMode]
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(primaryTextColor)
        }
        val strategyHint = TextView(requireContext()).apply {
            text = strategyHints[bleedMode]
            textSize = 11f
            setTextColor(primaryTextColor)
        }
        fun strategyButton(text: String, delta: Int) = TextView(requireContext()).apply {
            this.text = text
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(primaryTextColor)
            setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
            setOnClickListener {
                bleedMode = (bleedMode + delta + strategyNames.size) % strategyNames.size
                strategyText.text = strategyNames[bleedMode]
                strategyHint.text = strategyHints[bleedMode]
            }
        }
        container.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).toInt(), 0, 0)
            addView(
                TextView(requireContext()).apply {
                    text = "外扩策略"
                    textSize = 13f
                    setTextColor(primaryTextColor)
                },
                LinearLayout.LayoutParams(
                    (60 * density).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(strategyButton("−", -1))
            addView(
                strategyText,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(strategyButton("+", 1))
        })
        container.addView(strategyHint.apply {
            setPadding(0, (4 * density).toInt(), 0, (8 * density).toInt())
        })

        container.addView(TextView(requireContext()).apply {
            text = "间距：四个方向分别可调，正数把背景向外撑大、离文字更远，负数让背景向内收，" +
                "0 表示紧贴文字边界。间距与策略的外扩量叠加，可用它补偿图片自带的透明留白。"
            textSize = 11f
            setTextColor(primaryTextColor)
            setPadding(0, (8 * density).toInt(), 0, 0)
        })
        emSliderRow(
            "左间距", spacingLeft,
            HighlightRuleStore.MIN_BG_SPACING_H, HighlightRuleStore.MAX_BG_SPACING_H, 0f,
        ) { spacingLeft = it }
        emSliderRow(
            "右间距", spacingRight,
            HighlightRuleStore.MIN_BG_SPACING_H, HighlightRuleStore.MAX_BG_SPACING_H, 0f,
        ) { spacingRight = it }
        emSliderRow(
            "上间距", spacingTop,
            HighlightRuleStore.MIN_BG_SPACING_V, HighlightRuleStore.MAX_BG_SPACING_V, 0f,
        ) { spacingTop = it }
        emSliderRow(
            "下间距", spacingBottom,
            HighlightRuleStore.MIN_BG_SPACING_V, HighlightRuleStore.MAX_BG_SPACING_V, 0f,
        ) { spacingBottom = it }

        // 重置：弹窗内所有值回到默认（四边分割比例 0.1、智能外扩、四边间距 0）
        fun applyDefaults() {
            resetting = true
            val defaultNp = (HighlightRuleStore.DEFAULT_NP_RATIO * 100).roundToInt()
            // 顺序与限制无关（重置期间跳过"相加不超过 100%"的压回），只求四个都落到默认值
            bars["right"]?.progress = defaultNp
            bars["left"]?.progress = defaultNp
            bars["bottom"]?.progress = defaultNp
            bars["top"]?.progress = defaultNp
            resetting = false
            resetActions.forEach { it() }
            bleedMode = HighlightRule.BLEED_SMART
            strategyText.text = strategyNames[bleedMode]
            strategyHint.text = strategyHints[bleedMode]
            preview.invalidate()
        }

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("九宫格调整")
            // 行数较多，套一层滚动容器，小屏上不会挤掉确定/取消按钮
            .setView(android.widget.ScrollView(requireContext()).apply { addView(container) })
            .setPositiveButton(android.R.string.ok) { _, _ ->
                editingRule.npLeft = npLeft
                editingRule.npTop = npTop
                editingRule.npRight = npRight
                editingRule.npBottom = npBottom
                editingRule.bgBleedMode = bleedMode
                editingRule.bgSpacingLeft = spacingLeft
                editingRule.bgSpacingRight = spacingRight
                editingRule.bgSpacingTop = spacingTop
                editingRule.bgSpacingBottom = spacingBottom
                // 四边间距已成为唯一来源，旧字段清零，避免下次加载又被当成"未设置"再迁移一次
                editingRule.bgSpacingH = 0f
                editingRule.bgSpacingV = 0f
                updateNpAdjustRow()
                updatePreview()
            }
            .setNegativeButton(android.R.string.cancel, null)
            // 重置留在弹窗内：用 neutral 占位，点击事件在 onShow 里手动接管（否则默认会关闭弹窗）
            .setNeutralButton(R.string.reset, null)
            .create()
        dialog.setOnShowListener {
            val resetButton = dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)
            resetButton?.setOnClickListener { applyDefaults() }
            // 让重置靠左对齐：在它之后插入一个撑满剩余宽度的空白视图，取消/确定仍贴着右边
            val buttonBar = resetButton?.parent as? LinearLayout
            if (buttonBar != null) {
                buttonBar.addView(
                    View(requireContext()),
                    buttonBar.indexOfChild(resetButton) + 1,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
                )
            }
        }
        dialog.show()
    }

    /** 九宫格调整预览：居中显示背景图并叠加四条红色分割线，分割线随滑条实时移动 */
    private class NineSlicePreviewView(
        context: Context,
        private val bitmap: Bitmap?,
        npLeft: Float,
        npTop: Float,
        npRight: Float,
        npBottom: Float,
    ) : View(context) {

        var npLeft = npLeft
        var npTop = npTop
        var npRight = npRight
        var npBottom = npBottom

        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE53935.toInt()
            strokeWidth = 2f * resources.displayMetrics.density
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(0x1F888888)
            val bm = bitmap ?: return
            val cw = width.toFloat()
            val ch = height.toFloat()
            val aspect = bm.width.toFloat() / bm.height
            val (iw, ih, ox, oy) = if (aspect > cw / ch) {
                val h = cw / aspect
                listOf(cw, h, 0f, (ch - h) / 2f)
            } else {
                val w = ch * aspect
                listOf(w, ch, (cw - w) / 2f, 0f)
            }
            canvas.drawBitmap(bm, null, RectF(ox, oy, ox + iw, oy + ih), bitmapPaint)
            val leftX = ox + iw * npLeft
            canvas.drawLine(leftX, oy, leftX, oy + ih, linePaint)
            val rightX = ox + iw * (1f - npRight)
            canvas.drawLine(rightX, oy, rightX, oy + ih, linePaint)
            val topY = oy + ih * npTop
            canvas.drawLine(ox, topY, ox + iw, topY, linePaint)
            val bottomY = oy + ih * (1f - npBottom)
            canvas.drawLine(ox, bottomY, ox + iw, bottomY, linePaint)
        }
    }

    private fun saveRule() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val pattern = binding.etPattern.text?.toString()?.trim().orEmpty()
        if (pattern.isBlank()) {
            toastOnUi(R.string.highlight_rule_pattern_required)
            return
        }
        val regexError = validatePattern(pattern)
        if (regexError != null) {
            binding.tvPatternError.visibility = View.VISIBLE
            binding.tvPatternError.text = regexError
            return
        }
        editingRule = editingRule.copy(
            id = editingRule.id.ifBlank { System.currentTimeMillis().toString() },
            name = name.ifBlank { pattern },
            pattern = pattern,
            isRegex = isRegexMode,
            sampleText = binding.etSampleText.text?.toString().orEmpty(),
            group = groupItems.getOrElse(binding.spGroup.selectedItemPosition) {
                HighlightRuleGroupStore.DEFAULT_GROUP
            },
            targetScope = binding.spTarget.selectedItemPosition.coerceIn(0, 2),
            enabled = binding.switchEnable.isChecked,
            textColor = parseColorOrNull(binding.etTextColor.text?.toString().orEmpty()),
            underlineMode = binding.spUnderlineMode.selectedItemPosition,
            underlineColor = parseColorOrNull(binding.etUnderlineColor.text?.toString().orEmpty()),
            underlineWidth = binding.etUnderlineWidth.text?.toString()?.toFloatOrNull()?.coerceIn(0.1f, 10f) ?: 1f,
            underlineOffset = binding.etUnderlineOffset.text?.toString()?.toFloatOrNull()?.coerceIn(0f, 20f) ?: 2f,
            underlineSvgPath = binding.etSvgPath.text?.toString().orEmpty().takeIf { binding.spUnderlineMode.selectedItemPosition == 5 }.orEmpty(),
            font = editingRule.font?.takeIf { it.isNotBlank() },
            // 背景：判断输入的是颜色值还是图片路径
            bgColor = parseColorOrNull(binding.etBgImage.text?.toString().orEmpty()),
            bgImage = binding.etBgImage.text?.toString().orEmpty().takeIf { it.isNotBlank() && parseColorOrNull(it) == null },
            bgImageFit = binding.spBgImageFit.selectedItemPosition,
            bgImageScale = (binding.sbBgImageScale.progress.coerceAtLeast(1) / 10f).coerceIn(0.1f, 5f),
            // 书籍作用域，空白时存为null表示对所有书籍生效
            scope = binding.etScope.text?.toString().orEmpty().takeIf { it.isNotBlank() },
            excludeScope = binding.etExcludeScope.text?.toString().orEmpty().takeIf { it.isNotBlank() },
            // 排版作用范围，"所有排版"时存为null
            layoutScope = editingRule.layoutScope?.takeIf { it.isNotBlank() },
            // 主题作用范围
            themeScope = when (binding.spThemeScope.selectedItemPosition) {
                1 -> HighlightRule.THEME_LIGHT
                2 -> HighlightRule.THEME_DARK
                else -> HighlightRule.THEME_ALL
            },
        )
        onSave(editingRule)
        dismissAllowingStateLoss()
    }

    private fun updatePreview() {
        val pattern = binding.etPattern.text?.toString().orEmpty()
        binding.tvPatternError.visibility = View.GONE
        val patternError = validatePattern(pattern)
        if (patternError != null && pattern.isNotBlank()) {
            binding.tvPatternError.visibility = View.VISIBLE
            binding.tvPatternError.text = patternError
        }
        binding.tvPreview.text = HighlightRulePreview.build(
            editingRule.copy(
                name = binding.etName.text?.toString().orEmpty(),
                pattern = pattern,
                isRegex = isRegexMode,
                sampleText = binding.etSampleText.text?.toString().orEmpty(),
                group = groupItems.getOrElse(binding.spGroup.selectedItemPosition) {
                    HighlightRuleGroupStore.DEFAULT_GROUP
                },
                targetScope = binding.spTarget.selectedItemPosition.coerceIn(0, 2),
                textColor = parseColorOrNull(binding.etTextColor.text?.toString().orEmpty()),
                underlineMode = binding.spUnderlineMode.selectedItemPosition,
                underlineColor = parseColorOrNull(binding.etUnderlineColor.text?.toString().orEmpty()),
                underlineWidth = binding.etUnderlineWidth.text?.toString()?.toFloatOrNull()?.coerceIn(0.1f, 10f) ?: 1f,
                underlineOffset = binding.etUnderlineOffset.text?.toString()?.toFloatOrNull()?.coerceIn(0f, 20f) ?: 2f,
                underlineSvgPath = binding.etSvgPath.text?.toString().orEmpty(),
                // 背景：判断输入的是颜色值还是图片路径
                bgColor = parseColorOrNull(binding.etBgImage.text?.toString().orEmpty()),
                bgImage = binding.etBgImage.text?.toString().orEmpty().takeIf { it.isNotBlank() && parseColorOrNull(it) == null },
                bgImageFit = binding.spBgImageFit.selectedItemPosition,
                bgImageScale = (binding.sbBgImageScale.progress.coerceAtLeast(1) / 10f).coerceIn(0.1f, 5f),
                font = editingRule.font?.takeIf { it.isNotBlank() },
            ),
        )
    }

    private fun validatePattern(pattern: String): String? {
        if (pattern.isBlank()) return null
        if (!isRegexMode) return null
        return kotlin.runCatching { Regex(pattern) }.exceptionOrNull()?.localizedMessage
    }

    private fun Float.formatScale(): String = if (this == this.toInt().toFloat()) {
        this.toInt().toString()
    } else {
        String.format("%.1f", this)
    }

    private fun Float.formatDistance(): String = if (this == this.toInt().toFloat()) {
        this.toInt().toString()
    } else {
        String.format("%.1f", this)
    }

    private fun parseColorOrNull(value: String): Int? {
        val text = value.trim()
        if (text.isEmpty()) return null
        return kotlin.runCatching {
            val normalized = if (text.startsWith("#")) text else "#$text"
            Color.parseColor(normalized)
        }.getOrNull()
    }

    private fun Int.toHexColor(): String = String.format("#%08X", this)

    private fun showColorPicker(dialogId: Int, currentColor: Int) {
        val dialog = ColorPickerDialog.newBuilder()
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setColor(currentColor)
            .setShowAlphaSlider(false)
            .setAllowPresets(true)
            .setAllowCustom(true)
            .setDialogId(dialogId)
            .create()
        dialog.setColorPickerDialogListener(this)
        requireActivity().supportFragmentManager
            .beginTransaction()
            .add(dialog, "color_picker_$dialogId")
            .commitAllowingStateLoss()
    }

    override fun onColorSelected(dialogId: Int, @ColorInt color: Int) {
        when (dialogId) {
            1 -> {
                editingRule.textColor = color
                binding.etTextColor.setText(color.toHexColor())
                updateColorPreview(binding.viewTextColorPreview, color)
                updatePreview()
            }
            2 -> {
                editingRule.underlineColor = color
                binding.etUnderlineColor.setText(color.toHexColor())
                updateColorPreview(binding.viewUnderlineColorPreview, color)
                updatePreview()
            }
            3 -> {
                // 选择背景颜色时，清除背景图片
                editingRule.bgColor = color
                editingRule.bgImage = null
                binding.etBgImage.setText(color.toHexColor())
                updateBgPreview()
                updatePreview()
            }
        }
    }

    override fun onDialogDismissed(dialogId: Int) {
        // no-op
    }
}
