package io.legado.app.ui.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityTranslucenceBinding
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.utils.FileUtils
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.io.File

class BuiltInFilePickerActivity : BaseActivity<ActivityTranslucenceBinding>(),
    FilePickerDialog.CallBack {

    companion object {
        const val DIRECTORY = 0
        const val FILE = 1

        private const val EXTRA_MODE = "mode"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_INITIAL_PATH = "initialPath"
        private const val EXTRA_ALLOW_EXTENSIONS = "allowExtensions"
        private const val EXTRA_OUTPUT_FILE_NAME = "outputFileName"

        fun createIntent(
            context: Context,
            mode: Int,
            title: String? = null,
            initialPath: String? = null,
            allowExtensions: Array<String>? = null,
            outputFileName: String? = null,
        ): Intent = Intent(context, BuiltInFilePickerActivity::class.java).apply {
            putExtra(EXTRA_MODE, mode)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_INITIAL_PATH, initialPath)
            putExtra(EXTRA_ALLOW_EXTENSIONS, allowExtensions)
            putExtra(EXTRA_OUTPUT_FILE_NAME, outputFileName)
        }
    }

    override val binding by viewBinding(ActivityTranslucenceBinding::inflate)
    private var pickerShown = false

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        pickerShown = supportFragmentManager.findFragmentByTag(FilePickerDialog.tag) != null
        if (!pickerShown) requestStoragePermission()
    }

    private fun requestStoragePermission() {
        PermissionsCompat.Builder()
            .addPermissions(*Permissions.Group.STORAGE)
            .rationale(R.string.tip_perm_request_storage)
            .onGranted {
                binding.root.post(::showPicker)
            }
            .onDenied { finish() }
            .onError { finish() }
            .request()
    }

    private fun showPicker() {
        if (pickerShown || isFinishing || supportFragmentManager.isStateSaved) return
        pickerShown = true
        FilePickerDialog.show(
            supportFragmentManager,
            mode = intent.getIntExtra(EXTRA_MODE, FILE),
            title = intent.getStringExtra(EXTRA_TITLE),
            initPath = intent.getStringExtra(EXTRA_INITIAL_PATH),
            allowExtensions = intent.getStringArrayExtra(EXTRA_ALLOW_EXTENSIONS),
        )
    }

    override fun onResult(data: Intent) {
        val selectedUri = data.data ?: return finish()
        val outputFileName = intent.getStringExtra(EXTRA_OUTPUT_FILE_NAME)
        val resultUri = if (outputFileName.isNullOrBlank()) {
            selectedUri
        } else {
            val directory = selectedUri.path?.let(::File) ?: return finish()
            val safeName = File(outputFileName).name
            Uri.fromFile(FileUtils.createFileIfNotExist(directory, safeName))
        }
        setResult(RESULT_OK, Intent().setData(resultUri))
        finish()
    }
}
