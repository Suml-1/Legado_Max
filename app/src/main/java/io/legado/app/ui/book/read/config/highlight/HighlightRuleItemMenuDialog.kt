package io.legado.app.ui.book.read.config.highlight

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.databinding.DialogHighlightRuleItemMenuBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx

/**
 * 规则条目的「更多」菜单，屏幕居中显示，跟随当前主题上色。
 *
 * 列表项上的长按已改用于拖动排序，编辑/导出/分享/删除这些操作收到这里，
 * 保证每张卡片只有一个明确的"更多"入口。
 *
 * 这里用的是裸 [Dialog]（居中的自绘菜单，不做 Fragment 事务），所以调用方要持有
 * [show] 返回的实例，在宿主视图销毁时主动 [Dialog.dismiss]，避免窗口泄漏。
 */
class HighlightRuleItemMenuDialog(
    private val context: Context,
    private val title: CharSequence,
    private val onEdit: () -> Unit,
    private val onExport: () -> Unit,
    private val onShare: () -> Unit,
    private val onDelete: () -> Unit,
) {

    /** @return 已展示的对话框，调用方负责在宿主销毁时关闭它 */
    fun show(): Dialog {
        val binding = DialogHighlightRuleItemMenuBinding
            .inflate(LayoutInflater.from(context))
        val dialog = Dialog(context, R.style.dialog_style).apply {
            setContentView(binding.root)
            window?.setDimAmount(0.4f)
        }
        applyTheme(binding)
        binding.tvMenuTitle.text = title

        // 点任一操作都先关闭菜单，再执行动作，避免动作里再开弹窗时叠着两层
        fun dismissThen(action: () -> Unit) = View.OnClickListener {
            dialog.dismiss()
            action()
        }
        binding.itemEdit.setOnClickListener(dismissThen(onEdit))
        binding.itemExport.setOnClickListener(dismissThen(onExport))
        binding.itemShare.setOnClickListener(dismissThen(onShare))
        binding.itemDelete.setOnClickListener(dismissThen(onDelete))

        dialog.show()
        // 居中弹窗：宽屏下按屏幕宽度 80% 展示，最宽不超过 360dp
        val screenWidth = context.resources.displayMetrics.widthPixels
        dialog.window?.setLayout(
            (screenWidth * 0.8f).toInt().coerceAtMost(360.dpToPx()),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        return dialog
    }

    private fun applyTheme(binding: DialogHighlightRuleItemMenuBinding) {
        val bg = context.bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        val primaryTextColor = context.getPrimaryTextColor(isLight)
        val accentColor = context.accentColor
        val itemBgColor = if (isLight) {
            ColorUtils.blendColors(bg, Color.BLACK, 0.06f)
        } else {
            ColorUtils.blendColors(bg, Color.WHITE, 0.08f)
        }
        val errorColor = context.getColor(R.color.error)

        binding.menuContainer.background?.mutate()?.setTint(bg)
        binding.tvMenuTitle.setTextColor(primaryTextColor)

        listOf(binding.itemEdit, binding.itemExport, binding.itemShare, binding.itemDelete)
            .forEach { it.background?.mutate()?.setTint(itemBgColor) }

        listOf(
            binding.ivEdit to binding.tvEdit,
            binding.ivExport to binding.tvExport,
            binding.ivShare to binding.tvShare,
        ).forEach { (icon, label) ->
            icon.setColorFilter(accentColor, PorterDuff.Mode.SRC_IN)
            label.setTextColor(primaryTextColor)
        }
        binding.ivDelete.setColorFilter(errorColor, PorterDuff.Mode.SRC_IN)
        binding.tvDelete.setTextColor(errorColor)
    }
}
