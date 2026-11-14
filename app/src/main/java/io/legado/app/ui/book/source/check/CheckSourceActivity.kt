package io.legado.app.ui.book.source.check

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.base.BaseComposeActivity
import io.legado.app.constant.EventBus
import io.legado.app.model.CheckSourceResultEvent
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import androidx.lifecycle.lifecycleScope
import io.legado.app.ui.book.source.edit.BookSourceEditLauncher
import kotlinx.coroutines.launch
import io.legado.app.ui.config.CheckSourceConfig
import io.legado.app.utils.observeEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity

class CheckSourceActivity : BaseComposeActivity() {

    private var currentViewModel: CheckSourceViewModel? = null

    override fun observeLiveBus() {
        observeEvent<String>(EventBus.CHECK_SOURCE) { message ->
            currentViewModel?.updateCheckMessage(message)
        }

        observeEvent<CheckSourceResultEvent>(EventBus.CHECK_SOURCE_RESULT) { result ->
            currentViewModel?.onCheckResult(result)
        }

        observeEvent<Int>(EventBus.CHECK_SOURCE_DONE) {
            currentViewModel?.onCheckComplete()
        }
    }

    @Composable
    override fun ComposeContent() {
        val viewModel: CheckSourceViewModel = viewModel()
        currentViewModel = viewModel

        CheckSourceScreen(
            viewModel = viewModel,
            onBackClick = { finish() },
            onOpenConfig = { showDialogFragment<CheckSourceConfig>() },
            onEditSource = { sourceUrl ->
                lifecycleScope.launch {
                    BookSourceEditLauncher.open(this@CheckSourceActivity, sourceUrl)
                }
            },
            onDebugSource = { sourceUrl ->
                startActivity<BookSourceDebugActivity> {
                    putExtra("key", sourceUrl)
                }
            }
        )
    }
}

@Composable
fun CheckSourceContent(
    onBackClick: () -> Unit,
    onOpenConfig: () -> Unit,
    onEditSource: (String) -> Unit,
    onDebugSource: (String) -> Unit,
    onViewModelCreated: (CheckSourceViewModel) -> Unit
) {
    val viewModel: CheckSourceViewModel = viewModel()
    onViewModelCreated(viewModel)

    CheckSourceScreen(
        viewModel = viewModel,
        onBackClick = onBackClick,
        onOpenConfig = onOpenConfig,
        onEditSource = onEditSource,
        onDebugSource = onDebugSource
    )
}
