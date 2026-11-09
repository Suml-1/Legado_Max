package io.legado.app.ui.video.player

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.widget.TextView
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import io.legado.app.R
import io.legado.app.data.entities.BookChapter

class ChoiceEpisodeDialog(private val mContext: Context) :
    Dialog(
        mContext,
        R.style.dialog_style,
    ) {
    private var listView: ListView? = null

    private var adapter: ArrayAdapter<BookChapter>? = null

    private var onItemClickListener: OnListItemClickListener? = null

    private var data: List<BookChapter>? = null

    interface OnListItemClickListener {
        fun onItemClick(position: Int)
        fun finishDialog()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        applyVideoPanelWindow(PANEL_WIDTH_FRACTION)
    }

    override fun onStop() {
        onItemClickListener?.finishDialog()
        super.onStop()
    }

    @SuppressLint("SetTextI18n")
    fun initList(
        data: List<BookChapter>,
        onItemClickListener: OnListItemClickListener,
        initialSelection: Int = -1,
    ) {
        this.onItemClickListener = onItemClickListener
        this.data = data
        val inflater = LayoutInflater.from(mContext)
        val view: View = inflater.inflate(R.layout.switch_episode_video_dialog, null)
        view.findViewById<TextView>(R.id.listCount).text = "选集（${data.size}）"
        listView = view.findViewById(R.id.switch_dialog_list)
        setContentView(view)
        adapter = SwitchVideoAdapter(mContext, data) { item -> item.title }
        listView!!.setAdapter(adapter)
        if (initialSelection >= 0 && initialSelection < data.size) {
            listView!!.setSelectionFromTop(initialSelection, 0)
        }
        listView!!.onItemClickListener = this@ChoiceEpisodeDialog.OnItemClickListener()
        applyVideoPanelWindow(PANEL_WIDTH_FRACTION)
    }

    private inner class OnItemClickListener : AdapterView.OnItemClickListener {
        override fun onItemClick(
            adapterView: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long,
        ) {
            dismiss()
            onItemClickListener?.onItemClick(position)
        }
    }

    companion object {
        private const val PANEL_WIDTH_FRACTION = 0.40f
    }
}
