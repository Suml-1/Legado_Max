package io.legado.app.ui.config.topbar

import android.net.Uri
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.config.topbar.components.TopBarColorTarget
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 顶栏管理容器 Activity。
 *
 * 只负责 zip 导入/导出、壁纸选择与颜色选择器这类系统组件桥接；
 * 数据与状态在 [TopBarManageViewModel]，界面在 [TopBarManageScreen]。
 */
class TopBarManageActivity :
    BaseComposeActivity(),
    ColorPickerDialogListener {

    private val viewModel: TopBarManageViewModel by viewModels()

    private val importZip = registerForActivityResult(HandleFileContract()) { result ->
        val uri = result.uri ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val file = runCatching { copyToCache(uri, "topBarImports", "import") }.getOrNull()
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

    private val selectWallpaper = registerForActivityResult(HandleFileContract()) { result ->
        val uri = result.uri ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val path = runCatching { copyToCache(uri, "topBarWallpapers", "top_bar").absolutePath }.getOrNull()
            withContext(Dispatchers.Main) {
                if (path != null) {
                    viewModel.updateDraft { it.copy(wallpaperPath = path) }
                } else {
                    toastOnUi(R.string.select_image_failed)
                }
            }
        }
    }

    private fun copyToCache(uri: Uri, dirName: String, prefix: String): File {
        val extension = uri.lastPathSegment?.substringAfterLast('.', "img") ?: "img"
        val dir = externalFiles.getFile(dirName).apply { mkdirs() }
        val file = dir.getFile("${prefix}_${System.currentTimeMillis()}.$extension")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        } ?: error("open input stream failed: $uri")
        return file
    }

    @Composable
    override fun ComposeContent() {
        TopBarManageScreen(
            viewModel = viewModel,
            actions = TopBarManageActions(
                onBackClick = { finish() },
                onToast = { toastOnUi(it) },
                onToastMsg = { toastOnUi(it) },
                onShareJson = { json -> share(json, getString(R.string.share_top_bar_config)) },
                onExportZip = { file ->
                    exportZip.launch {
                        mode = HandleFileContract.EXPORT
                        title = getString(R.string.export_str)
                        fileData = HandleFileContract.FileData(file.name, file, "application/zip")
                    }
                },
                onPickColor = ::showColorPicker,
                onPickWallpaper = {
                    selectWallpaper.launch {
                        mode = HandleFileContract.IMAGE
                        title = getString(R.string.wallpaper)
                    }
                },
                onPickImportZip = {
                    importZip.launch {
                        mode = HandleFileContract.FILE
                        title = getString(R.string.top_bar_import_zip)
                        allowExtensions = arrayOf("zip")
                    }
                },
            ),
        )
    }

    private fun showColorPicker(target: Int, current: Int) {
        ColorPickerDialog.newBuilder()
            .setDialogId(target)
            .setColor(current)
            .setShowAlphaSlider(false)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .create()
            .also { it.setColorPickerDialogListener(this) }
            .show(supportFragmentManager, "top_bar_color_$target")
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        viewModel.updateDraft { config ->
            when (dialogId) {
                TopBarColorTarget.BACKGROUND -> config.copy(backgroundColor = color)
                TopBarColorTarget.TAG_BAR -> config.copy(tagBarColor = color)
                TopBarColorTarget.TAG_SELECTED -> config.copy(tagSelectedColor = color)
                else -> config
            }
        }
    }

    override fun onDialogDismissed(dialogId: Int) {}
}
