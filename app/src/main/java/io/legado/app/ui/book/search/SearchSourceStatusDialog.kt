package io.legado.app.ui.book.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.lib.theme.EInkRender
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.model.webBook.SourceSearchRecord
import io.legado.app.ui.book.source.edit.BookSourceEditLauncher
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchSourceStatusDialog : BottomSheetDialogFragment() {

    private val viewModel: SearchViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.dialog_search_source_status, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 墨水屏渲染：弹窗是独立窗口，Activity 内容根那层盖不到。本类不是 BaseDialogFragment
        // （走的是 BottomSheet），没有那套统一处理，这里自己补一层灰阶
        EInkRender.applyRootLayer(dialog?.window?.findViewById(android.R.id.content))
        // 设置 BottomSheet 背景为应用主题背景色
        (view.parent as? View)?.setBackgroundColor(backgroundColor)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val adapter = SourceStatusAdapter { record ->
            openBookSourceEdit(record.sourceUrl)
        }
        recyclerView.adapter = adapter

        viewModel.sourceRecordsLiveData.observe(viewLifecycleOwner) { records ->
            adapter.submitList(records.sortedByDescending { it.duration })
        }
    }

    private fun openBookSourceEdit(sourceUrl: String) {
        val context = context ?: return
        lifecycleScope.launch {
            val exists = withContext(Dispatchers.IO) {
                appDb.bookSourceDao.getBookSource(sourceUrl) != null
            }
            if (exists) {
                BookSourceEditLauncher.open(context, sourceUrl)
            } else {
                context.toastOnUi("书源不存在或已被删除")
            }
        }
    }

    class SourceStatusAdapter(
        private val onSourceClick: (SourceSearchRecord) -> Unit,
    ) : RecyclerView.Adapter<SourceStatusAdapter.VH>() {
        private var items = emptyList<SourceSearchRecord>()

        fun submitList(list: List<SourceSearchRecord>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_source_status, parent, false)
            return VH(view, onSourceClick)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        class VH(itemView: View, private val onSourceClick: (SourceSearchRecord) -> Unit) : RecyclerView.ViewHolder(itemView) {
            private val tvName = itemView.findViewById<TextView>(R.id.tv_source_name)
            private val tvStatus = itemView.findViewById<TextView>(R.id.tv_status)
            private val tvDuration = itemView.findViewById<TextView>(R.id.tv_duration)
            private val tvErrorDetail = itemView.findViewById<TextView>(R.id.tv_error_detail)
            private var currentRecord: SourceSearchRecord? = null

            init {
                tvName.setOnClickListener {
                    currentRecord?.let { onSourceClick(it) }
                }
            }

            fun bind(record: SourceSearchRecord) {
                currentRecord = record
                tvName.text = record.sourceName

                // 状态文本：简短形式
                val statusText = when (record.status) {
                    SourceSearchRecord.Status.PENDING -> "等待中"
                    SourceSearchRecord.Status.RUNNING -> if (record.duration > 10000) "运行中(慢)" else "运行中"
                    SourceSearchRecord.Status.SUCCESS -> "成功"
                    SourceSearchRecord.Status.EMPTY -> "空结果"
                    SourceSearchRecord.Status.FAILED -> "失败"
                }
                tvStatus.text = statusText
                tvDuration.text = "${record.duration / 1000}s"

                // 状态颜色
                val statusColorRes = when (record.status) {
                    SourceSearchRecord.Status.FAILED -> R.color.md_red_500
                    SourceSearchRecord.Status.RUNNING -> if (record.duration > 10000) R.color.md_orange_500 else R.color.md_green_500
                    SourceSearchRecord.Status.EMPTY -> R.color.md_grey_500
                    else -> R.color.md_green_500
                }
                tvStatus.setTextColor(itemView.context.getColor(statusColorRes))

                // 详细报错信息：仅失败时显示，另起一行
                if (record.status == SourceSearchRecord.Status.FAILED && !record.errorMsg.isNullOrBlank()) {
                    tvErrorDetail.text = record.errorMsg
                    tvErrorDetail.visibility = View.VISIBLE
                } else {
                    tvErrorDetail.text = null
                    tvErrorDetail.visibility = View.GONE
                }
            }
        }
    }
}
