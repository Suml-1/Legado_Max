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
import io.legado.app.data.entities.BookChapter

class ChoiceEpisodeDialog(private val mContext: Context) : Dialog(
    mContext, R.style.dialog_style
) {
    private var listView: ListView? = null

    private var adapter: ArrayAdapter<BookChapter>? = null

    private var onItemClickListener: OnListItemClickListener? = null

    private var data: List<BookChapter>? = null

    /** 播放器实测高度：面板高度以它为上限，避免超出可用区域 */
    private var panelHeight = 0

    interface OnListItemClickListener {
        fun onItemClick(position: Int)
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
        //宽度取 45%（原 30% 的 1.5 倍）：选集标题普遍较长（如"1上 某人的梦（上）【会员】"），
        //30% 下会被省略得看不出是哪一集；加宽后一屏内标题更完整。
        //倍速面板仍保持 30% —— 它只有 "1.0X" 这类短文本，加宽反而显得空。
        applyVideoPanelWindow(
            (d.widthPixels * 0.45).toInt(),
            if (panelHeight > 0) panelHeight else d.heightPixels
        )
    }

    @SuppressLint("SetTextI18n")
    fun initList(
        data: List<BookChapter>,
        onItemClickListener: OnListItemClickListener,
        initialSelection: Int = -1,
        /** 播放器可视高度（px）：沉浸式下 displayMetrics.heightPixels 含系统栏区域，
         *  直接拿它当窗口高度会让窗口偏高、被垂直居中裁掉首尾各一截。 */
        usableHeight: Int = 0
    ) {
        this.onItemClickListener = onItemClickListener
        this.data = data
        this.panelHeight = usableHeight
        val inflater = LayoutInflater.from(mContext)
        val view: View = inflater.inflate(R.layout.switch_episode_video_dialog, null)
        listView = view.findViewById(R.id.switch_dialog_list)
        setContentView(view)
        //与倍速面板保持一致：把「当前集」交给适配器，由它用主题色 + 加粗标出。
        //之前这里只调 setSelectionFromTop 滚动到当前项，并没有设置 selectedValue，
        //所以当前集在视觉上和其它集没有区别（只有位置不同）。
        val hasSelection = initialSelection >= 0 && initialSelection < data.size
        val switchAdapter = SwitchVideoAdapter(mContext, data) { item -> item.title }
        if (hasSelection) {
            switchAdapter.selectedValue = data[initialSelection]
        }
        adapter = switchAdapter
        listView!!.setAdapter(adapter)
        if (hasSelection) {
            listView!!.setSelectionFromTop(initialSelection, 0)
        }
        listView!!.onItemClickListener = this@ChoiceEpisodeDialog.OnItemClickListener()
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
            onItemClickListener!!.onItemClick(position)
        }
    }
}