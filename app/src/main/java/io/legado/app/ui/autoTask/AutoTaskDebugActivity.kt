package io.legado.app.ui.autoTask

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityAutoTaskDebugBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.gone
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.launch

class AutoTaskDebugActivity :
    VMBaseActivity<ActivityAutoTaskDebugBinding, AutoTaskDebugViewModel>() {

    companion object {
        private const val EXTRA_TASK_ID = "taskId"

        fun startIntent(context: Context, id: String): Intent {
            return Intent(context, AutoTaskDebugActivity::class.java).apply {
                putExtra(EXTRA_TASK_ID, id)
            }
        }
    }

    override val binding by viewBinding(ActivityAutoTaskDebugBinding::inflate)
    override val viewModel by viewModels<AutoTaskDebugViewModel>()
    private val adapter by lazy { AutoTaskDebugAdapter(this) }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        viewModel.observe { state, msg ->
            lifecycleScope.launch {
                adapter.addItem(msg)
                if (state == -1 || state == 1000) {
                    binding.rotateLoading.gone()
                }
            }
        }
        viewModel.init(intent.getStringExtra(EXTRA_TASK_ID)) {
            startDebug()
        }
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.applyNavigationBarPadding()
        binding.rotateLoading.loadingColor = accentColor
    }

    private fun startDebug() {
        adapter.clearItems()
        viewModel.startDebug({
            binding.rotateLoading.visible()
        }, {
            toastOnUi(getString(R.string.auto_task_no_task))
            binding.rotateLoading.gone()
        })
    }
}
