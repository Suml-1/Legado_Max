package io.legado.app.ui.video.player

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import io.legado.app.R
import io.legado.app.lib.theme.accentColor

/**
 * 选集 / 倍速面板的列表适配器（纯文字样式）。
 *
 * 视觉口径：**没有卡片背景、没有描边**，只有一行行文字叠在画面上；
 * 文字统一使用**阅读主题色**（与 App 其它地方的强调色一致），当前项更亮更粗。
 *
 * 为什么用"亮度/字重"而不是"背景色块"来表达选中：
 * 面板容器是透明的，画面上任何底色块都会显出生硬的矩形边界；
 * 而参考样式本身就是靠文字层级区分，这样在亮/暗画面上都更稳。
 */
class SwitchVideoAdapter<T>(
    context: Context,
    private val dataList: List<T>,
    private val titleProvider: (T) -> String = { it.toString() }
) : ArrayAdapter<T>(context, 0, dataList) {

    /** 当前选中项：倍速面板用它高亮正在生效的档位，null 表示不标注 */
    var selectedValue: T? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private val accentColor: Int by lazy { context.accentColor }

    /**
     * 未选中项：主题色压暗到 55% 亮度，保证在明亮画面上依然可读、又不抢画面；
     * 选中项：主题色原色 + 加粗，一眼能看出当前档位/当前集。
     */
    private val normalColor: Int by lazy {
        val r = (accentColor shr 16 and 0xFF) * 0.75f
        val g = (accentColor shr 8 and 0xFF) * 0.75f
        val b = (accentColor and 0xFF) * 0.75f
        (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.switch_video_dialog_item, parent, false)
        val textView = view.findViewById<TextView>(R.id.text1)
        val item = dataList[position]
        textView.text = titleProvider(item)
        val selected = selectedValue != null && selectedValue == item
        if (selected) {
            textView.setTextColor(accentColor)
            textView.setTypeface(null, Typeface.BOLD)
        } else {
            textView.setTextColor(normalColor)
            textView.setTypeface(null, Typeface.NORMAL)
        }
        return view
    }
}
