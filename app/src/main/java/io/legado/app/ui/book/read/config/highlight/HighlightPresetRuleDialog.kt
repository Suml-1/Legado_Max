package io.legado.app.ui.book.read.config.highlight

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogHighlightPresetRuleBinding
import io.legado.app.databinding.ItemHighlightPresetAddBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 预置规则选择弹窗。
 */
class HighlightPresetRuleDialog @JvmOverloads constructor(
    private val defaultGroup: String? = null,
    private val onAddRule: (HighlightRule) -> Unit = {},
) : BaseDialogFragment(R.layout.dialog_highlight_preset_rule) {

    private val binding by viewBinding(DialogHighlightPresetRuleBinding::bind)
    private val adapter by lazy { PresetRuleAdapter(requireContext()) }
    private val presetRules by lazy { HighlightRuleStore.defaultPresetRules(requireContext()) }
    private var primaryTextColor = 0
    private var secondaryTextColor = 0
    private var accentColor = 0
    private var cardBgColor = 0
    private var cardStrokeColor = 0
    private var previewBgColor = 0

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.85f)
        dialog?.window?.setGravity(Gravity.BOTTOM)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        if (!AppConfig.isEInkMode) {
            view?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initTheme()
        attachBottomSheetDismiss(
            binding.dragHandle,
            binding.sheetContainer
        ) { dismissAllowingStateLoss() }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        adapter.setItems(presetRules)

        binding.ivBack.setOnClickListener { dismissAllowingStateLoss() }
    }

    override fun observeLiveBus() {
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) {
            if (it.contains(1) || it.contains(2)) {
                initTheme()
                adapter.notifyDataSetChanged()
            }
        }
    }

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

        binding.sheetContainer.background?.mutate()?.setTint(bg)
        binding.ivBack.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
        binding.ivBack.background?.mutate()?.setTint(cardBgColor)
        binding.tvPageTitle.setTextColor(primaryTextColor)
        binding.tvPageSubtitle.setTextColor(secondaryTextColor)
    }

    private inner class PresetRuleAdapter(context: Context) :
        RecyclerAdapter<HighlightRule, ItemHighlightPresetAddBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemHighlightPresetAddBinding {
            return ItemHighlightPresetAddBinding.inflate(inflater, parent, false)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemHighlightPresetAddBinding) {
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemHighlightPresetAddBinding,
            item: HighlightRule,
            payloads: MutableList<Any>
        ) {
            val density = binding.root.context.resources.displayMetrics.density
            binding.root.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * density
                setColor(cardBgColor)
                setStroke((1f * density).toInt().coerceAtLeast(1), cardStrokeColor)
            }
            binding.tvPreview.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f * density
                setColor(previewBgColor)
                setStroke((1f * density).toInt().coerceAtLeast(1), cardStrokeColor)
            }
            binding.ivAdd.background?.mutate()?.setTint(accentColor)

            binding.tvTitle.text = item.name
            binding.tvTitle.setTextColor(primaryTextColor)
            binding.tvDesc.text = item.displayPattern()
            binding.tvDesc.setTextColor(secondaryTextColor)
            binding.tvPreviewLabel.setTextColor(secondaryTextColor)
            binding.tvPreview.setPreview(item, primaryTextColor)
            binding.tvPreview.setTextColor(primaryTextColor)
            binding.ivAdd.setColorFilter(
                if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(),
                PorterDuff.Mode.SRC_IN
            )
            binding.ivAdd.setOnClickListener {
                val groupToUse = defaultGroup ?: HighlightRuleGroupStore.DEFAULT_GROUP
                onAddRule(item.copy(
                    id = System.currentTimeMillis().toString(),
                    group = groupToUse
                ))
                dismissAllowingStateLoss()
            }
        }
    }
}
