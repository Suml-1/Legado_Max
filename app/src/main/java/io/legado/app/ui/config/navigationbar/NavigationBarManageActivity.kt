package io.legado.app.ui.config.navigationbar

import android.net.Uri
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.config.navigationbar.components.NavigationBarColorTarget
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

/**
 * 底栏管理容器 Activity。
 *
 * 只做系统级组件的桥接：颜色选择器（Fragment dialog）与图标文件选择器（ActivityResult）。
 * 数据与状态在 [NavigationBarManageViewModel]，界面在 [NavigationBarManageScreen]。
 */
class NavigationBarManageActivity :
    BaseComposeActivity(),
    ColorPickerDialogListener {

    private val viewModel: NavigationBarManageViewModel by viewModels()

    private data class IconRequest(val itemKey: String, val state: String)

    private var pendingIconRequest: IconRequest? = null

    private val selectIcon = registerForActivityResult(HandleFileContract()) { result ->
        val request = pendingIconRequest
        val uri = result.uri
        pendingIconRequest = null
        if (request == null || uri == null) return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val path = runCatching { copyIconFile(uri, request) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (path != null) {
                    viewModel.updateDraftIcon(request.itemKey, request.state, path)
                } else {
                    toastOnUi(R.string.select_image_failed)
                }
            }
        }
    }

    private fun copyIconFile(uri: Uri, request: IconRequest): String? {
        val configId = viewModel.draft.value?.id ?: return null
        val dir = externalFiles
            .getFile("navigationBarIcons", configId)
            .apply { mkdirs() }
        val extension = uri.lastPathSegment?.substringAfterLast('.', "png") ?: "png"
        val file = dir.getFile("${request.itemKey}_${request.state}.$extension")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        } ?: return null
        return file.absolutePath
    }

    @Composable
    override fun ComposeContent() {
        NavigationBarManageScreen(
            viewModel = viewModel,
            actions = NavigationBarManageActions(
                onBackClick = { finish() },
                onToast = { toastOnUi(it) },
                onToastMsg = { toastOnUi(it) },
                onShareJson = { json -> share(json, getString(R.string.share_nav_bar_config)) },
                onPickColor = ::showColorPicker,
                onPickIcon = { itemKey, state, _ ->
                    pendingIconRequest = IconRequest(itemKey, state)
                    selectIcon.launch {
                        mode = HandleFileContract.FILE
                        title = getString(R.string.select_image)
                    }
                },
            ),
        )
    }

    private fun showColorPicker(target: Int, current: Int) {
        val dialog = ColorPickerDialog.newBuilder()
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setColor(current)
            .setShowAlphaSlider(target == NavigationBarColorTarget.BORDER)
            .setAllowPresets(true)
            .setAllowCustom(true)
            .setDialogId(target)
            .create()
        dialog.setColorPickerDialogListener(this)
        supportFragmentManager
            .beginTransaction()
            .add(dialog, "navigation_bar_color_$target")
            .commitAllowingStateLoss()
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        viewModel.updateDraft { draft ->
            draft.copySelf().apply {
                when (dialogId) {
                    NavigationBarColorTarget.BACKGROUND -> backgroundColor = color
                    NavigationBarColorTarget.BORDER -> {
                        borderColor = color
                        if (borderAlpha == 0) borderAlpha = 100
                    }
                }
            }
        }
    }

    override fun onDialogDismissed(dialogId: Int) {}
}
