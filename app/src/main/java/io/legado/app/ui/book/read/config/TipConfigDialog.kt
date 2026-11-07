package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.indices
import androidx.core.view.isVisible
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogReaderInfoTemplateBinding
import io.legado.app.databinding.DialogTipConfigBinding
import io.legado.app.databinding.DialogTitleSplitValueBinding
import io.legado.app.help.book.ChapterTitleSplitter
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.help.config.ReaderInfoTemplate
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.utils.checkByIndex
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getIndexById
import io.legado.app.utils.hexString
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlin.math.roundToInt


class TipConfigDialog : BaseDialogFragment(R.layout.dialog_tip_config) {

    companion object {
        const val TIP_COLOR = 7897
        const val TIP_DIVIDER_COLOR = 7898

        /** 次行字号倍数的 SeekBar 步进：40 档覆盖 0~2 倍 */
        private const val SUB_SCALE_STEP = 0.05f
    }

    private val binding by viewBinding(DialogTipConfigBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initEvent()
        observeEvent<String>(EventBus.TIP_COLOR) {
            upTvTipColor()
            upTvTipDividerColor()
        }
    }

    private fun initView() {
        if (ReadBookConfig.titleMode !in binding.rgTitleMode.indices) {
            ReadBookConfig.titleMode = 0
        }
        binding.rgTitleMode.checkByIndex(ReadBookConfig.titleMode)
        binding.dsbTitleSize.progress = ReadBookConfig.titleSize
        binding.dsbTitleTop.progress = ReadBookConfig.titleTopSpacing
        binding.dsbTitleBottom.progress = ReadBookConfig.titleBottomSpacing
        // 存的是倍数，进度条上直接显示成「1.00 倍」，否则用户看到的是 0~40 的档位号
        binding.dsbTitleSubScale.valueFormat = { "${it * SUB_SCALE_STEP} 倍" }
        upTitleSplitView()

        binding.tvHeaderShow.text =
            ReadTipConfig.getHeaderModes(requireContext())[ReadTipConfig.headerMode]
        binding.tvFooterShow.text =
            ReadTipConfig.getFooterModes(requireContext())[ReadTipConfig.footerMode]

        // 初始化页眉页脚字体大小 SeekBar
        binding.dsbHeaderFontSize.progress = ReadTipConfig.headerFontSize
        binding.dsbFooterFontSize.progress = ReadTipConfig.footerFontSize
        initTipValues()
        upTvTipColor()
        upTvTipDividerColor()
    }

    /**
     * 各位置摘要展示实际生效的模板（未自定义时展示旧规则转换后的等价模板）
     */
    private fun initTipValues() = binding.run {
        ReadTipConfig.run {
            tvHeaderLeft.text = effectiveTemplate(tipHeaderLeftTemplate, tipHeaderLeft)
            tvHeaderMiddle.text = effectiveTemplate(tipHeaderMiddleTemplate, tipHeaderMiddle)
            tvHeaderRight.text = effectiveTemplate(tipHeaderRightTemplate, tipHeaderRight)
            tvFooterLeft.text = effectiveTemplate(tipFooterLeftTemplate, tipFooterLeft)
            tvFooterMiddle.text = effectiveTemplate(tipFooterMiddleTemplate, tipFooterMiddle)
            tvFooterRight.text = effectiveTemplate(tipFooterRightTemplate, tipFooterRight)
        }
    }

    private fun upTvTipColor() {
        val tipColorNames = ReadTipConfig.tipColorNames
        val tipColor = ReadTipConfig.tipColor
        binding.tvTipColor.text = if (tipColor == 0) {
            tipColorNames.first()
        } else {
            "#${tipColor.hexString}"
        }
    }

    private fun upTvTipDividerColor() {
        val tipDividerColorNames = ReadTipConfig.tipDividerColorNames
        val tipDividerColor = ReadTipConfig.tipDividerColor
        binding.tvTipDividerColor.text = when (tipDividerColor) {
            -1, 0 -> tipDividerColorNames[tipDividerColor + 1]
            else -> "#${tipDividerColor.hexString}"
        }
    }

    private fun initEvent() = binding.run {
        rgTitleMode.setOnCheckedChangeListener { _, checkedId ->
            ReadBookConfig.titleMode = rgTitleMode.getIndexById(checkedId)
            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
        }
        dsbTitleSize.onChanged = {
            ReadBookConfig.titleSize = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbTitleTop.onChanged = {
            ReadBookConfig.titleTopSpacing = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbTitleBottom.onChanged = {
            ReadBookConfig.titleBottomSpacing = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        llTitleSplitMode.setOnClickListener {
            val modes = resources.getStringArray(R.array.title_split_modes)
            context?.selector(items = modes.toList()) { _, i ->
                ReadBookConfig.titleSplitMode = i
                upTitleSplitView()
                postEvent(EventBus.UP_CONFIG, arrayListOf(5))
            }
        }
        llTitleSplitValue.setOnClickListener {
            editSplitValue()
        }
        dsbTitleSubScale.onChanged = {
            // 0.05 的倍数在 Float 里除不尽，取两位小数落库，避免进度来回拖动时精度漂移
            ReadBookConfig.titleSubScale = (it * SUB_SCALE_STEP * 100f).roundToInt() / 100f
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbTitleLineSpacing.onChanged = {
            // 分段后的标题行距：与正文行距同单位同量程，但只作用于分段后的标题各行
            ReadBookConfig.titleLineSpacingExtra = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        llHeaderShow.setOnClickListener {
            val headerModes = ReadTipConfig.getHeaderModes(requireContext())
            context?.selector(items = headerModes.values.toList()) { _, i ->
                ReadTipConfig.headerMode = headerModes.keys.toList()[i]
                tvHeaderShow.text = headerModes[ReadTipConfig.headerMode]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }
        llFooterShow.setOnClickListener {
            val footerModes = ReadTipConfig.getFooterModes(requireContext())
            context?.selector(items = footerModes.values.toList()) { _, i ->
                ReadTipConfig.footerMode = footerModes.keys.toList()[i]
                tvFooterShow.text = footerModes[ReadTipConfig.footerMode]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }
        llHeaderLeft.setOnClickListener {
            ReadTipConfig.run {
                editTemplate(
                    title = getString(R.string.reader_info_template),
                    current = effectiveTemplate(tipHeaderLeftTemplate, tipHeaderLeft),
                ) { tipHeaderLeftTemplate = it }
            }
        }
        llHeaderMiddle.setOnClickListener {
            ReadTipConfig.run {
                editTemplate(
                    title = getString(R.string.reader_info_template),
                    current = effectiveTemplate(tipHeaderMiddleTemplate, tipHeaderMiddle),
                ) { tipHeaderMiddleTemplate = it }
            }
        }
        llHeaderRight.setOnClickListener {
            ReadTipConfig.run {
                editTemplate(
                    title = getString(R.string.reader_info_template),
                    current = effectiveTemplate(tipHeaderRightTemplate, tipHeaderRight),
                ) { tipHeaderRightTemplate = it }
            }
        }
        llFooterLeft.setOnClickListener {
            ReadTipConfig.run {
                editTemplate(
                    title = getString(R.string.reader_info_template),
                    current = effectiveTemplate(tipFooterLeftTemplate, tipFooterLeft),
                ) { tipFooterLeftTemplate = it }
            }
        }
        llFooterMiddle.setOnClickListener {
            ReadTipConfig.run {
                editTemplate(
                    title = getString(R.string.reader_info_template),
                    current = effectiveTemplate(tipFooterMiddleTemplate, tipFooterMiddle),
                ) { tipFooterMiddleTemplate = it }
            }
        }
        llFooterRight.setOnClickListener {
            ReadTipConfig.run {
                editTemplate(
                    title = getString(R.string.reader_info_template),
                    current = effectiveTemplate(tipFooterRightTemplate, tipFooterRight),
                ) { tipFooterRightTemplate = it }
            }
        }
        // 页眉字体大小调节
        dsbHeaderFontSize.onChanged = {
            ReadTipConfig.headerFontSize = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        // 页脚字体大小调节
        dsbFooterFontSize.onChanged = {
            ReadTipConfig.footerFontSize = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        llTipColor.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipColorNames) { _, i ->
                when (i) {
                    0 -> {
                        ReadTipConfig.tipColor = 0
                        upTvTipColor()
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    }

                    1 -> ColorPickerDialog.newBuilder()
                        .setShowAlphaSlider(false)
                        .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                        .setDialogId(TIP_COLOR)
                        .show(requireActivity())
                }
            }
        }
        llTipDividerColor.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipDividerColorNames) { _, i ->
                when (i) {
                    0, 1 -> {
                        ReadTipConfig.tipDividerColor = i - 1
                        upTvTipDividerColor()
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    }

                    2 -> ColorPickerDialog.newBuilder()
                        .setShowAlphaSlider(false)
                        .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                        .setDialogId(TIP_DIVIDER_COLOR)
                        .show(requireActivity())
                }
            }
        }
    }

    /**
     * 刷新正文标题分段的两行：模式名 + 依据内容。
     *
     * 关闭模式下整行收起，避免用户填了内容却因为没选模式而不生效。
     */
    private fun upTitleSplitView() = binding.run {
        val modes = resources.getStringArray(R.array.title_split_modes)
        // 旧配置或导入的配置可能越界，回落到关闭，避免取模式名时崩掉
        val mode = ReadBookConfig.titleSplitMode.coerceIn(modes.indices)
        ReadBookConfig.titleSplitMode = mode
        tvTitleSplitMode.text = modes[mode]
        val splitEnabled = mode != ChapterTitleSplitter.MODE_NONE
        llTitleSplitValue.isVisible = splitEnabled
        // 没有分段就没有次行，次行字号一起收起；标题行距同样只在分段后有意义
        dsbTitleSubScale.isVisible = splitEnabled
        dsbTitleLineSpacing.isVisible = splitEnabled
        if (splitEnabled) {
            val value = ReadBookConfig.titleSplitValue
            tvTitleSplitValue.text = value.ifEmpty { getString(splitValueHint(mode)) }
            // 导入的配置可能越界（次行倍数超过 2 倍、行距超过量程），回落到量程内，
            // 否则 SeekBar 的 progress 大于 max 会显示异常
            val scaleSteps = (ReadBookConfig.titleSubScale / SUB_SCALE_STEP).roundToInt()
            dsbTitleSubScale.progress = scaleSteps.coerceIn(0, dsbTitleSubScale.max)
            dsbTitleLineSpacing.progress =
                ReadBookConfig.titleLineSpacingExtra.coerceIn(0, dsbTitleLineSpacing.max)
        }
    }

    /** 未填内容时把该模式该填什么直接显示在右侧，省一次点击 */
    private fun splitValueHint(mode: Int) = when (mode) {
        ChapterTitleSplitter.MODE_POSITION -> R.string.title_split_hint_position
        ChapterTitleSplitter.MODE_MARK -> R.string.title_split_hint_mark
        ChapterTitleSplitter.MODE_REGEX -> R.string.title_split_hint_regex
        else -> R.string.title_split_hint_position
    }

    /**
     * 编辑分段依据，确定后立即重排正文，标题显示同步变化
     */
    private fun editSplitValue() {
        val mode = ReadBookConfig.titleSplitMode
        val dialogBinding = DialogTitleSplitValueBinding.inflate(layoutInflater)
        dialogBinding.editSplitValue.setHint(splitValueHint(mode))
        val current = ReadBookConfig.titleSplitValue
        dialogBinding.editSplitValue.setText(current)
        dialogBinding.editSplitValue.setSelection(current.length)
        alert(getString(R.string.title_split_value)) {
            customView { dialogBinding.root }
            okButton {
                ReadBookConfig.titleSplitValue =
                    dialogBinding.editSplitValue.editableText.toString()
                upTitleSplitView()
                postEvent(EventBus.UP_CONFIG, arrayListOf(5))
            }
            cancelButton()
        }
    }

    /**
     * 打开信息模板编辑框，点占位符即插入到光标处
     */
    private fun editTemplate(
        title: String,
        current: String,
        save: (String) -> Unit,
    ) {
        val dialogBinding = DialogReaderInfoTemplateBinding.inflate(layoutInflater)
        dialogBinding.editTemplate.setText(current)
        dialogBinding.editTemplate.setSelection(current.length)
        ReaderInfoTemplate.placeholders.forEach { placeholder ->
            // 不能用 Material Chip：它强制要求宿主主题是 Theme.MaterialComponents，会直接抛异常
            val placeholderView = TextView(requireContext()).apply {
                text = placeholder
                textSize = 13f
                setTextColor(requireContext().getCompatColor(R.color.primaryText))
                setBackgroundResource(R.drawable.reader_info_placeholder_bg)
                val paddingHorizontal = 10.dpToPx()
                val paddingVertical = 5.dpToPx()
                setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
                setOnClickListener {
                    val edit = dialogBinding.editTemplate
                    val editable = edit.editableText
                    val start = minOf(edit.selectionStart, edit.selectionEnd)
                        .coerceIn(0, editable.length)
                    val end = maxOf(edit.selectionStart, edit.selectionEnd)
                        .coerceIn(0, editable.length)
                    editable.replace(start, end, placeholder)
                }
            }
            dialogBinding.chipPlaceholders.addView(placeholderView)
        }
        alert(title) {
            customView { dialogBinding.root }
            okButton {
                save(dialogBinding.editTemplate.editableText.toString())
                initTipValues()
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
            cancelButton()
        }
    }

}
