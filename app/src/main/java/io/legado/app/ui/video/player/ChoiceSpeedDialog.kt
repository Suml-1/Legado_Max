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

class ChoiceSpeedDialog(private val mContext: Context) :
    Dialog(
        mContext,
        R.style.dialog_style,
    ) {
    private var listView: ListView? = null

    private var adapter: ArrayAdapter<Float>? = null

    private var onItemClickListener: OnListItemClickListener? = null

    private var data: List<Float>? = null

    interface OnListItemClickListener {
        fun onItemClick(value: Float)
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
        data: List<Float>,
        onItemClickListener: OnListItemClickListener,
    ) {
        this.onItemClickListener = onItemClickListener
        this.data = data
        val inflater = LayoutInflater.from(mContext)
        val view: View = inflater.inflate(R.layout.switch_speed_video_dialog, null)
        listView = view.findViewById(R.id.switch_dialog_list)
        setContentView(view)
        adapter = SwitchVideoAdapter(mContext, data) { item -> item.toString() + "X" }
        listView!!.setAdapter(adapter)
        listView!!.onItemClickListener = this@ChoiceSpeedDialog.OnItemClickListener()
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
            onItemClickListener?.onItemClick(data?.get(position) ?: 1.0f)
        }
    }

    companion object {
        private const val PANEL_WIDTH_FRACTION = 0.30f
    }
}
