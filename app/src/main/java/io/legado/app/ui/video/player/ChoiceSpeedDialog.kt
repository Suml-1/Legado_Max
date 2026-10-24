package io.legado.app.ui.video.player

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import io.legado.app.R

class ChoiceSpeedDialog(private val mContext: Context) : Dialog(
    mContext, R.style.dialog_style
) {
    private var listView: ListView? = null

    private var adapter: ArrayAdapter<Float>? = null

    private var onItemClickListener: OnListItemClickListener? = null

    private var data: List<Float>? = null

    /** 播放器实测高度：面板高度以它为上限，避免超出可用区域 */
    private var panelHeight = 0

    interface OnListItemClickListener {
        fun onItemClick(value: Float)
        fun finishDialog()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    /**
     * 窗口参数必须在 show() 之后再次应用：Dialog.show() 内部会按 decor 重新下发
     * LayoutParams，把 initList 里设好的贴顶/layoutInScreen 覆盖回去——这就是
     * "贴顶不生效、顶部始终空一条状态栏"的原因。
     */
    override fun onStart() {
        super.onStart()
        applyPanelWindow()
    }

    override fun onStop() {
        onItemClickListener!!.finishDialog()
        super.onStop()
    }

    private fun applyPanelWindow() {
        val d = mContext.resources.displayMetrics
        //与选集面板同宽（30%）：倍速档位只有 "1.0X" 这类短文本，过宽会显得很空
        applyVideoPanelWindow(
            (d.widthPixels * 0.30).toInt(),
            if (panelHeight > 0) panelHeight else d.heightPixels
        )
    }

    @SuppressLint("SetTextI18n")
    fun initList(
        data: List<Float>,
        onItemClickListener: OnListItemClickListener,
        currentSpeed: Float = 1.0f,
        /** 播放器可视高度（px）：沉浸式下 displayMetrics.heightPixels 含系统栏区域，
         *  直接拿它当窗口高度会让窗口比可用区域高、被垂直居中裁掉首尾各一截。 */
        usableHeight: Int = 0
    ) {
        this.onItemClickListener = onItemClickListener
        this.data = data
        this.panelHeight = usableHeight
        val inflater = LayoutInflater.from(mContext)
        val view: View = inflater.inflate(R.layout.switch_speed_video_dialog, null)
        listView = view.findViewById(R.id.switch_dialog_list)
        setContentView(view)
        //当前档位高亮：标明正在生效的倍速（面板关闭前一直显示）
        adapter = SwitchVideoAdapter(mContext, data) { item -> item.toString() + "X" }
            .apply { selectedValue = currentSpeed }
        listView!!.setAdapter(adapter)
        listView!!.onItemClickListener = this@ChoiceSpeedDialog.OnItemClickListener()
        applyPanelWindow()
    }

    private inner class OnItemClickListener : AdapterView.OnItemClickListener {
        override fun onItemClick(
            adapterView: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            dismiss()
            onItemClickListener!!.onItemClick(data?.get(position) ?: 1.0f)
        }
    }
}