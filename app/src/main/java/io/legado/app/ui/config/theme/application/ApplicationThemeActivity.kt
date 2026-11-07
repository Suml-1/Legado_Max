package io.legado.app.ui.config.theme.application

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.config.theme.application.edit.ApplicationThemeEditActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 应用主题列表容器 Activity。
 *
 * 系统组件桥接（文件导入/导出、剪贴板、编辑页跳转）在这一层；
 * 数据与状态在 [ApplicationThemeViewModel]，界面在 [ApplicationThemeScreen]。
 */
class ApplicationThemeActivity : BaseComposeActivity() {

    private val viewModel: ApplicationThemeViewModel by viewModels()

    private val importTheme = registerForActivityResult(HandleFileContract()) { result ->
        val uri = result.uri ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val file = runCatching { copyToCache(uri) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (file != null) {
                    viewModel.importFile(file)
                } else {
                    toastOnUi(R.string.import_failed)
                }
            }
        }
    }

    private val exportTheme = registerForActivityResult(HandleFileContract()) {
        if (it.uri != null) toastOnUi(R.string.success)
    }

    private val openEditor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.load()
    }

    private fun copyToCache(uri: Uri): File {
        val extension = uri.lastPathSegment?.substringAfterLast('.', "json") ?: "json"
        val dir = externalFiles.getFile("themeImports").apply { mkdirs() }
        val file = dir.getFile("import_${System.currentTimeMillis()}.$extension")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        } ?: error("open input stream failed: $uri")
        return file
    }

    @Composable
    override fun ComposeContent() {
        ApplicationThemeScreen(
            viewModel = viewModel,
            actions = ApplicationThemeActions(
                onBackClick = { finish() },
                onToast = { toastOnUi(it) },
                onToastMsg = { toastOnUi(it) },
                onCopyText = ::copyText,
                onExportFile = { file ->
                    exportTheme.launch {
                        mode = HandleFileContract.EXPORT
                        title = getString(R.string.export_str)
                        fileData = HandleFileContract.FileData(file.name, file, "application/json")
                    }
                },
                onOpenEditor = { id ->
                    openEditor.launch(
                        Intent(this, ApplicationThemeEditActivity::class.java)
                            .putExtra(ApplicationThemeEditActivity.EXTRA_ID, id)
                    )
                },
                onPickImportFile = {
                    importTheme.launch {
                        mode = HandleFileContract.FILE
                        title = getString(R.string.application_theme_import)
                        allowExtensions = arrayOf("zip", "json", "red")
                    }
                },
                onRestart = { recreate() }
            )
        )
    }

    private fun copyText(text: String) {
        runCatching {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("application_theme", text))
        }.onSuccess {
            toastOnUi(R.string.copy_complete)
        }.onFailure {
            toastOnUi(it.localizedMessage ?: getString(R.string.error))
        }
    }
}
