package io.legado.app.ui.autoTask

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemAppLogBinding
import io.legado.app.model.AutoTask
import io.legado.app.utils.LogUtils
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import androidx.appcompat.widget.Toolbar
import java.util.Date

class AutoTaskLogDialog() : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    constructor(taskId: String, taskName: String) : this() {
        arguments = Bundle().apply {
            putString("taskId", taskId)
            putString("taskName", taskName)
        }
    }

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { LogAdapter(requireContext()) }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val taskId = arguments?.getString("taskId").orEmpty()
        val taskName = arguments?.getString("taskName").orEmpty()
        binding.toolBar.setTitle(taskName.ifBlank { getString(R.string.log) })
        binding.toolBar.inflateMenu(R.menu.app_log)
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        val task = AutoTask.getRules().firstOrNull { it.id == taskId }
        val lastRunAt = task?.lastRunAt ?: 0L
        val message = task?.lastLog ?: task?.lastError ?: task?.lastResult ?: getString(R.string.auto_task_not_run)
        val time = if (lastRunAt > 0L) lastRunAt else System.currentTimeMillis()
        adapter.setItems(listOf(Triple(time, message, null)))
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        if (item?.itemId == R.id.menu_clear) {
            val taskId = arguments?.getString("taskId").orEmpty()
            AutoTask.update(taskId) {
                it.copy(lastLog = null, lastError = null, lastResult = null)
            }
            adapter.setItems(
                listOf(
                    Triple(
                        System.currentTimeMillis(),
                        getString(R.string.auto_task_not_run),
                        null
                    )
                )
            )
        }
        return true
    }

    inner class LogAdapter(context: Context) :
        RecyclerAdapter<Triple<Long, String, Throwable?>, ItemAppLogBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemAppLogBinding {
            return ItemAppLogBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAppLogBinding,
            item: Triple<Long, String, Throwable?>,
            payloads: MutableList<Any>
        ) {
            binding.textTime.text = LogUtils.logTimeFormat.format(Date(item.first))
            binding.textMessage.text = item.second
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemAppLogBinding) {
            // 无交互
        }
    }
}
