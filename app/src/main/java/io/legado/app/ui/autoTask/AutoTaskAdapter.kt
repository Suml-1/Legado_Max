package io.legado.app.ui.autoTask

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemAutoTaskBinding
import io.legado.app.model.AutoTaskRule
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.startActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoTaskAdapter(context: Context, private val callBack: CallBack) :
    RecyclerAdapter<AutoTaskRule, ItemAutoTaskBinding>(context),
    ItemTouchCallback.Callback {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val selectedIds = linkedSetOf<String>()

    val selection: List<AutoTaskRule>
        get() = getItems().filter { it.id in selectedIds }

    val diffItemCallBack = object : DiffUtil.ItemCallback<AutoTaskRule>() {
        override fun areItemsTheSame(oldItem: AutoTaskRule, newItem: AutoTaskRule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AutoTaskRule, newItem: AutoTaskRule): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: AutoTaskRule, newItem: AutoTaskRule): Any? {
            val payload = Bundle()
            if (oldItem.name != newItem.name) {
                payload.putBoolean("name", true)
            }
            if (oldItem.enable != newItem.enable) {
                payload.putBoolean("enabled", true)
            }
            if (oldItem.lastRunAt != newItem.lastRunAt ||
                oldItem.lastError != newItem.lastError ||
                oldItem.cron != newItem.cron
            ) {
                payload.putBoolean("summary", true)
            }
            return if (payload.isEmpty) null else payload
        }
    }

    override fun getViewBinding(parent: ViewGroup): ItemAutoTaskBinding {
        return ItemAutoTaskBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemAutoTaskBinding,
        item: AutoTaskRule,
        payloads: MutableList<Any>
    ) {
        binding.apply {
            if (payloads.isEmpty()) {
                cbName.text = item.name.ifBlank { item.id }
                swtEnabled.isChecked = item.enable
                tvSubtitle.text = buildSummary(item)
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as? Bundle ?: continue
                    bundle.keySet().forEach {
                        when (it) {
                            "name" -> cbName.text = item.name.ifBlank { item.id }
                            "enabled" -> swtEnabled.isChecked = item.enable
                            "summary" -> tvSubtitle.text = buildSummary(item)
                        }
                    }
                }
            }
            cbName.isChecked = item.id in selectedIds
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemAutoTaskBinding) {
        binding.apply {
            cbName.setOnUserCheckedChangeListener { isChecked ->
                getItem(holder.layoutPosition)?.let { task ->
                    if (isChecked) {
                        selectedIds.add(task.id)
                    } else {
                        selectedIds.remove(task.id)
                    }
                    callBack.upCountView()
                }
            }
            swtEnabled.setOnUserCheckedChangeListener { isChecked ->
                getItem(holder.layoutPosition)?.let { task ->
                    callBack.toggle(task, isChecked)
                }
            }
            ivEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.let { callBack.edit(it) }
            }
            ivMenuMore.setOnClickListener { view ->
                getItem(holder.layoutPosition)?.let { showMenu(view, it) }
            }
            root.setOnClickListener {
                getItem(holder.layoutPosition)?.let { task ->
                    cbName.isChecked = !cbName.isChecked
                    if (cbName.isChecked) {
                        selectedIds.add(task.id)
                    } else {
                        selectedIds.remove(task.id)
                    }
                    callBack.upCountView()
                }
            }
        }
    }

    override fun onCurrentListChanged() {
        val currentIds = getItems().map { it.id }.toHashSet()
        selectedIds.retainAll(currentIds)
        callBack.upCountView()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            getItems().forEach { selectedIds.add(it.id) }
        } else {
            selectedIds.clear()
        }
        notifyDataSetChanged()
        callBack.upCountView()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun revertSelection() {
        getItems().forEach {
            if (it.id in selectedIds) {
                selectedIds.remove(it.id)
            } else {
                selectedIds.add(it.id)
            }
        }
        notifyDataSetChanged()
        callBack.upCountView()
    }

    // ItemTouchCallback.Callback
    override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
        swapItem(srcPosition, targetPosition)
        return true
    }

    override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        callBack.upOrder(getItems())
    }

    val dragSelectCallback: DragSelectTouchHelper.Callback =
        object : DragSelectTouchHelper.AdvanceCallback<String>(
            DragSelectTouchHelper.AdvanceCallback.Mode.ToggleAndReverse
        ) {
            override fun currentSelectedId(): Set<String> {
                return selectedIds
            }

            override fun getItemId(position: Int): String {
                return getItem(position)!!.id
            }

            override fun updateSelectState(position: Int, isSelected: Boolean): Boolean {
                getItem(position)?.let { task ->
                    if (isSelected) {
                        selectedIds.add(task.id)
                    } else {
                        selectedIds.remove(task.id)
                    }
                    notifyItemChanged(position, bundleOf(Pair("selected", null)))
                    callBack.upCountView()
                    return true
                }
                return false
            }
        }

    private fun buildSummary(task: AutoTaskRule): String {
        val cron = task.cron?.trim().orEmpty().ifBlank { "-" }
        val status = when {
            !task.lastError.isNullOrBlank() ->
                context.getString(R.string.auto_task_last_error, task.lastError)
            task.lastRunAt > 0L ->
                context.getString(
                    R.string.auto_task_last_run,
                    timeFormat.format(Date(task.lastRunAt))
                )
            else -> context.getString(R.string.auto_task_not_run)
        }
        return context.getString(R.string.auto_task_item_summary, cron, status)
    }

    private fun showMenu(view: View, task: AutoTaskRule) {
        val popupMenu = PopupMenu(context, view)
        popupMenu.inflate(R.menu.auto_task_item)
        popupMenu.menu.findItem(R.id.menu_login).isVisible = !task.loginUrl.isNullOrBlank()
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_login -> context.startActivity<SourceLoginActivity> {
                    putExtra("type", "autoTask")
                    putExtra("key", task.id)
                }

                R.id.menu_log -> callBack.showLog(task)
                R.id.menu_delete -> callBack.delete(task)
            }
            true
        }
        popupMenu.show()
    }

    interface CallBack {
        fun edit(task: AutoTaskRule)
        fun delete(task: AutoTaskRule)
        fun toggle(task: AutoTaskRule, enabled: Boolean)
        fun upCountView()
        fun showLog(task: AutoTaskRule)
        fun upOrder(items: List<AutoTaskRule>)
    }
}
