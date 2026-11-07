package io.legado.app.ui.config.bubble

import android.app.Activity
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.help.HelpDocManager
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.code.CodeEditLauncher
import io.legado.app.ui.config.bubble.components.BubbleColorTarget
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * 气泡管理容器 Activity。
 *
 * 负责 zip 导入/导出、SVG 代码编辑页、颜色选择器与帮助文档这类系统组件桥接；
 * 数据与状态在 [BubbleManageViewModel]，界面在 [BubbleManageScreen]。
 */
class BubbleManageActivity :
    BaseComposeActivity(),
    ColorPickerDialogListener {

    private val viewModel: BubbleManageViewModel by viewModels()

    private val importZip = registerForActivityResult(HandleFileContract()) { result ->
        val uri = result.uri ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val file = runCatching { copyToCache(uri, "bubbleImports", "import") }.getOrNull()
            withContext(Dispatchers.Main) {
                if (file != null) {
                    viewModel.importZip(file)
                } else {
                    toastOnUi(R.string.import_failed)
                }
            }
        }
    }

    private val exportZip = registerForActivityResult(HandleFileContract()) {
        if (it.uri != null) toastOnUi(R.string.success)
    }

    private val svgEditor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        CodeEditLauncher.readText(result.data)?.let { text ->
            viewModel.updateDraft { it.copy(svgTemplate = text) }
        }
    }

    private fun copyToCache(uri: Uri, dirName: String, prefix: String): File {
        val extension = uri.lastPathSegment?.substringAfterLast('.', "zip") ?: "zip"
        val dir = externalFiles.getFile(dirName).apply { mkdirs() }
        val file = dir.getFile("${prefix}_${System.currentTimeMillis()}.$extension")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        } ?: error("open input stream failed: $uri")
        return file
    }

    @Composable
    override fun ComposeContent() {
        BubbleManageScreen(
            viewModel = viewModel,
            actions = BubbleManageActions(
                onBackClick = { finish() },
                onToast = { toastOnUi(it) },
                onToastMsg = { toastOnUi(it) },
                onShareJson = { json -> share(json, getString(R.string.share)) },
                onExportZip = { file ->
                    exportZip.launch {
                        mode = HandleFileContract.EXPORT
                        title = getString(R.string.export_str)
                        fileData = HandleFileContract.FileData(file.name, file, "application/zip")
                    }
                },
                onPickZip = {
                    importZip.launch {
                        mode = HandleFileContract.FILE
                        title = getString(R.string.bubble_import_zip)
                        allowExtensions = arrayOf("zip")
                    }
                },
                onPickColor = ::showColorPicker,
                onEditSvg = { svg ->
                    val intent = android.content.Intent(this, CodeEditActivity::class.java).apply {
                        CodeEditLauncher.putText(this, svg)
                        putExtra("title", getString(R.string.bubble_svg_template_title))
                        putExtra("cursorPosition", svg.length)
                    }
                    svgEditor.launch(intent)
                },
                onHelp = ::showHelp
            )
        )
    }

    private fun showColorPicker(target: Int, current: String) {
        val color = runCatching { android.graphics.Color.parseColor(current) }
            .getOrDefault(android.graphics.Color.GRAY)
        ColorPickerDialog.newBuilder()
            .setColor(color)
            .setShowAlphaSlider(false)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setDialogId(target)
            .create()
            .also { it.setColorPickerDialogListener(this) }
            .show(supportFragmentManager, "bubble_color_$target")
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        val hex = String.format(Locale.ROOT, "#%06X", color and 0x00FFFFFF)
        viewModel.updateDraft { config ->
            when (dialogId) {
                BubbleColorTarget.DAY_NORMAL -> config.copy(dayNormalColor = hex)
                BubbleColorTarget.DAY_EMPHASIS -> config.copy(dayEmphasisColor = hex)
                BubbleColorTarget.NIGHT_NORMAL -> config.copy(nightNormalColor = hex)
                BubbleColorTarget.NIGHT_EMPHASIS -> config.copy(nightEmphasisColor = hex)
                else -> config
            }
        }
    }

    override fun onDialogDismissed(dialogId: Int) {}

    private fun showHelp() {
        val content = HelpDocManager.loadDoc(assets, "bubbleHelp")
        showDialogFragment(
            TextDialog(
                title = getString(R.string.bubble_manage),
                content = content,
                mode = TextDialog.Mode.MD,
                helpDocName = "bubbleHelp"
            )
        )
    }
}
