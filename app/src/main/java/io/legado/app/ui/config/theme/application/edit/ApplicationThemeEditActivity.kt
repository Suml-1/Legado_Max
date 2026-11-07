package io.legado.app.ui.config.theme.application.edit

import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.utils.toastOnUi

/**
 * 应用主题编辑容器 Activity。
 *
 * 通过 [EXTRA_ID] 区分新建与编辑：为空表示新建（以当前生效配置为底稿）。
 */
class ApplicationThemeEditActivity : BaseComposeActivity() {

    private val viewModel: ApplicationThemeEditViewModel by viewModels()

    override fun onActivityCreated(savedInstanceState: android.os.Bundle?) {
        viewModel.load(intent.getStringExtra(EXTRA_ID))
    }

    @Composable
    override fun ComposeContent() {
        ApplicationThemeEditScreen(
            viewModel = viewModel,
            onBackClick = { finish() },
            onToast = { toastOnUi(it) },
            onFinished = { finish() }
        )
    }

    companion object {
        const val EXTRA_ID = "id"
    }
}
