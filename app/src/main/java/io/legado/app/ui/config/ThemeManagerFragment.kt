package io.legado.app.ui.config

import android.net.Uri
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.help.config.NgManagedTheme
import io.legado.app.help.config.NgPreparedThemeImport
import io.legado.app.help.config.NgThemeImportAction
import io.legado.app.help.config.NgEffectThemeStatus
import io.legado.app.help.config.EffectThemeRuntime
import io.legado.app.help.config.NgThemeModeStore
import io.legado.app.help.config.NgThemeModeGroup
import io.legado.app.help.config.NgThemePresentationMode
import io.legado.app.help.config.NgEffectPackageKind
import io.legado.app.help.config.NgEffectPackageLibrary
import io.legado.app.help.config.NgThemeLibraryStore
import io.legado.app.help.config.NgThemeDrawerAssets
import io.legado.app.help.config.NgThemeDrawerProfile
import io.legado.app.help.config.NgThemePackageManager
import io.legado.app.help.config.isBuiltIn
import io.legado.app.model.BookCover
import io.legado.app.help.config.md3.Md3ThemeImportDraft
import io.legado.app.help.config.md3.Md3ThemeImportManager
import io.legado.app.help.config.md3.Md3ThemePackageNotRecognizedException
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.resource.ResourceCenterActivity
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.postEvent
import io.legado.app.utils.CreateDocumentContract
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ThemeManagerFragment : BaseFragment(R.layout.fragment_theme_manager) {

    private var pendingExportPackagePath: String? = null
    private var preparingExport by mutableStateOf(false)
    private var originalEditTheme by mutableStateOf<NgManagedTheme?>(null)
    private var draftEditTheme by mutableStateOf<NgManagedTheme?>(null)
    private var pendingDarkBackground: Boolean? = null
    private var editingSession: String? = null
    private var pendingDrawerImage: Pair<String, Boolean>? = null
    private var savingTheme by mutableStateOf(false)
    private var applyingTheme by mutableStateOf(false)
    private var copyingDrawerImage by mutableStateOf(false)
    private var pendingMd3ImportUri: Uri? = null
    private var md3ImportDraft by mutableStateOf<Md3ThemeImportDraft?>(null)
    private var md3ImportInstalling by mutableStateOf(false)
    private var themePackageImport by mutableStateOf<NgPreparedThemeImport?>(null)
    private var themePackageInstalling by mutableStateOf(false)
    private var inspectingImport by mutableStateOf(false)
    private var effectWorking by mutableStateOf(false)
    private var effectStatuses by mutableStateOf<Map<String, NgEffectThemeStatus>>(emptyMap())
    private var statusRefresh by mutableStateOf(0L)
    private val effectBusy: Boolean get() = themePackageInstalling || inspectingImport || effectWorking ||
        applyingTheme || savingTheme || copyingDrawerImage || preparingExport || md3ImportInstalling


    private val exportTheme = registerForActivityResult(
        CreateDocumentContract("application/zip")
    ) { uri ->
        val path = pendingExportPackagePath
        pendingExportPackagePath = null
        val context = requireContext().applicationContext
        if (uri == null || path == null) {
            discardExportPackage(context, path)
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            try {
                withContext(NonCancellable + Dispatchers.IO) {
                    val source = frozenExportPackage(context, path) ?: error("主题包已失效")
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: error("无法写入主题包")
                    output.use { target -> source.inputStream().use { it.copyTo(target) } }
                }
                toastOnUi(R.string.ng_theme_export_success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                toastOnUi(context.getString(R.string.ng_theme_export_failed, error.message.orEmpty()))
            } finally {
                discardExportPackage(context, path)
            }
        }
    }

    private val importTheme = registerForActivityResult(SelectFileContract()) { uri ->
        if (uri == null) return@registerForActivityResult
        val context = requireContext().applicationContext
        inspectingImport = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Md3ThemeImportManager.preview(context, uri)
                    .onSuccess { draft ->
                        pendingMd3ImportUri = uri
                        md3ImportDraft = draft
                    }.onFailure { error ->
                        when (error) {
                            is CancellationException -> throw error
                            is Md3ThemePackageNotRecognizedException -> prepareThemePackageImport(uri)
                            else -> {
                                Log.w(TAG, "Could not inspect theme package", error)
                                toastOnUi(R.string.ng_theme_import_read_failed)
                            }
                        }
                    }
            } finally { inspectingImport = false }
        }
    }

    private val selectBackground = registerForActivityResult(
        SelectFileContract()
    ) { uri ->
        val dark = pendingDarkBackground
        pendingDarkBackground = null
        if (uri == null || dark == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { copyBackground(uri) }
                .onSuccess { path -> updateBackground(dark) { it.copy(path = path) } }
                .onFailure {
                    toastOnUi(getString(R.string.ng_theme_background_copy_failed, it.message.orEmpty()))
                }
        }
    }

    private val selectDrawerImage = registerForActivityResult(SelectFileContract()) { uri ->
        val pending = pendingDrawerImage
        pendingDrawerImage = null
        if (uri == null || pending == null || pending.first != editingSession) return@registerForActivityResult
        val context = requireContext().applicationContext
        copyingDrawerImage = true
        viewLifecycleOwner.lifecycleScope.launch {
            var copiedPath: String? = null
            try {
                val path = NgThemeDrawerAssets.copyDraft(context, uri)
                copiedPath = path
                val current = draftEditTheme
                if (editingSession == pending.first && current != null) {
                    val profile = current.drawerProfile ?: NgThemeDrawerProfile()
                    val oldPath = profile.forNight(pending.second).imagePath
                    draftEditTheme = current.copy(drawerProfile = profile.updated(
                        pending.second, profile.forNight(pending.second).copy(imagePath = path),
                    ).copy(source = "custom_image"))
                    copiedPath = null
                    if (oldPath != path && draftEditTheme?.drawerProfile?.light?.imagePath != oldPath &&
                        draftEditTheme?.drawerProfile?.dark?.imagePath != oldPath) {
                        NgThemeDrawerAssets.discardDraft(context, oldPath)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (editingSession == pending.first) {
                    toastOnUi(getString(R.string.ng_theme_background_copy_failed, error.message.orEmpty()))
                }
            } finally {
                NgThemeDrawerAssets.discardDraft(context, copiedPath)
                if (editingSession == pending.first) copyingDrawerImage = false
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.ng_theme_management)
        setSharedTitleBarVisible(false)
        if (pendingExportPackagePath == null) {
            pendingExportPackagePath = savedInstanceState?.getString(PENDING_EXPORT_PACKAGE)
                ?.takeIf { frozenExportPackage(requireContext(), it) != null }
        }
        (view as ComposeView).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by NgThemeLibraryStore.observe(requireContext()).collectAsState()
                val effectRevision by EffectThemeRuntime.revision.collectAsState()
                val context = requireContext().applicationContext
                LaunchedEffect(state.savedThemes, effectRevision, statusRefresh) {
                    effectStatuses = withContext(Dispatchers.IO) {
                        state.savedThemes.filter { it.effect != null }.associate { theme ->
                            theme.id to EffectThemeRuntime.status(context, theme, NgEffectPackageKind.THEME.target)
                        }
                    }
                }
                NgAppTheme {
                    ThemeManagerScreen(
                        savedThemes = state.savedThemes,
                        activeThemeId = state.activeThemeId,
                        currentThemeName = NgThemeLibraryStore.currentThemeName(requireContext()),
                        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                        onSaveCurrent = ::saveCurrentTheme,
                        onImportPackage = ::importThemePackage,
                        onThemeSelected = ::selectTheme,
                        onThemeEdit = { editTheme(it) },
                        editingTheme = originalEditTheme,
                        draftTheme = draftEditTheme,
                        onDismissThemeEditor = ::dismissThemeEditor,
                        onDraftThemeChanged = ::updateThemeDraft,
                        onSelectBackground = ::selectThemeBackground,
                        onBackgroundBlurChanged = { dark, blur ->
                            updateBackground(dark) { it.copy(blur = blur) }
                        },
                        onClearBackground = { dark ->
                            updateBackground(dark) { it.copy(path = null) }
                        },
                        onSelectDrawerImage = ::selectThemeDrawerImage,
                        savingTheme = savingTheme || copyingDrawerImage || preparingExport,
                        onSaveTheme = ::saveEditedTheme,
                        onThemeExport = ::requestExport,
                        onThemeDelete = ::deleteTheme,
                        md3ImportDraft = md3ImportDraft,
                        md3ImportInstalling = md3ImportInstalling,
                        onDismissMd3Import = ::dismissMd3Import,
                        onConfirmMd3Import = ::installMd3Theme,
                        effectStatuses = effectStatuses,
                        effectBusy = effectBusy,
                        appEffectEnabled = NgThemeModeStore.current(context) == NgThemePresentationMode.EFFECT_THEME,
                        appThemeEnabled = NgThemeModeStore.currentGroup(context) == NgThemeModeGroup.STANDARD,
                        themePackageImport = themePackageImport,
                        themePackageInstalling = themePackageInstalling,
                        onDismissThemePackageImport = ::dismissThemePackageImport,
                        onConfirmThemePackageImport = ::installThemePackage,
                        onEffectApply = ::applyEffectTheme,
                        onEffectApplyStatic = ::applyStaticEffectTheme,
                        onGetMore = {
                            startActivity(Intent(requireContext(), ResourceCenterActivity::class.java)
                                .putExtra("resourcePurpose", "APPEARANCE")
                                .putExtra("resourceType", "theme"))
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        statusRefresh += 1
        activity?.setTitle(R.string.ng_theme_management)
        setSharedTitleBarVisible(false)
    }

    private fun setSharedTitleBarVisible(visible: Boolean) {
        activity?.findViewById<View>(R.id.title_bar)?.visibility = if (visible) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun saveCurrentTheme(name: String) {
        if (effectBusy) return
        val context = requireContext().applicationContext
        lifecycleScope.launch {
            runCatching {
                withContext(NonCancellable + Dispatchers.IO) {
                    NgThemeLibraryStore.saveCurrent(context, name)
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                toastOnUi(context.getString(R.string.ng_drawer_theme_save_failed, error.message.orEmpty()))
            }
        }
    }

    private fun selectTheme(theme: NgManagedTheme) {
        if (effectBusy) return
        val context = requireContext().applicationContext
        applyingTheme = true
        lifecycleScope.launch {
            try {
                if (!NgThemeLibraryStore.applyAsync(context, theme)) {
                    toastOnUi(R.string.ng_drawer_theme_apply_failed)
                }
            } finally {
                applyingTheme = false
            }
        }
    }

    private fun importThemePackage() {
        if (effectBusy) return
        importTheme.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
    }

    private suspend fun prepareThemePackageImport(uri: Uri) {
        val context = requireContext().applicationContext
        var created: NgPreparedThemeImport? = null
        var handedToUi = false
        try {
            val candidate = withContext(Dispatchers.IO) {
                NgThemePackageManager.inspectTheme(context, uri).getOrThrow().also { created = it }
            }
            if (candidate.action == NgThemeImportAction.NO_OP) {
                toastOnUi(R.string.effect_theme_installed)
            } else {
                themePackageImport = candidate
                handedToUi = true
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            toastOnUi(getString(R.string.ng_theme_import_failed, error.message.orEmpty()))
        } finally {
            if (!handedToUi) created?.let { withContext(NonCancellable + Dispatchers.IO) { it.discard() } }
        }
    }

    private fun dismissThemePackageImport() {
        if (themePackageInstalling) return
        val candidate = themePackageImport ?: return
        themePackageImport = null
        lifecycleScope.launch(NonCancellable + Dispatchers.IO) { candidate.discard() }
    }

    private fun installThemePackage(keepCustomizations: Boolean) {
        val candidate = themePackageImport ?: return
        if (effectBusy) return
        val context = requireContext().applicationContext
        val updateCurrent = candidate.previous?.id?.let { NgThemeLibraryStore.current(context).activeThemeId == it } == true
        val applicationMode = NgThemeModeStore.current(context)
        themePackageInstalling = true
        themePackageImport = null
        lifecycleScope.launch {
            try {
                val installed = withContext(NonCancellable + Dispatchers.IO) {
                    candidate.commit(context,
                        allowReplace = candidate.action == NgThemeImportAction.REPLACE,
                        allowDowngrade = candidate.action == NgThemeImportAction.DOWNGRADE,
                        keepCustomizations = keepCustomizations)
                }
                if (updateCurrent &&
                    NgThemeLibraryStore.current(context).activeThemeId == installed.id &&
                    NgThemeModeStore.current(context) == applicationMode &&
                    (applicationMode == NgThemePresentationMode.STANDARD ||
                        applicationMode == NgThemePresentationMode.EFFECT_THEME && installed.effect != null)
                ) {
                    val applied = if (applicationMode == NgThemePresentationMode.STANDARD) {
                        NgThemeLibraryStore.applyStaticAsync(context, installed)
                    } else NgThemeLibraryStore.applyAsync(context, installed)
                    if (!applied) {
                        toastOnUi(R.string.ng_drawer_theme_apply_failed)
                        return@launch
                    }
                    withContext(NonCancellable + Dispatchers.IO) { candidate.discardPreviousPackage(context) }
                }
                toastOnUi(getString(R.string.ng_theme_import_success, installed.name))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) { toastOnUi(getString(R.string.ng_theme_import_failed, error.message.orEmpty()))
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { candidate.discard() }
                themePackageInstalling = false
                statusRefresh += 1
            }
        }
    }

    private fun runEffectOperation(block: suspend (Context) -> Unit) {
        if (effectBusy) return
        val context = requireContext().applicationContext
        effectWorking = true
        lifecycleScope.launch {
            try { block(context)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) { if (isAdded) toastOnUi(error.message ?: getString(R.string.ng_drawer_theme_apply_failed))
            } finally {
                effectWorking = false
                statusRefresh += 1
            }
        }
    }

    private fun applyEffectTheme(theme: NgManagedTheme) {
        val target = NgEffectPackageKind.THEME.target
        if (theme.effect?.targets != listOf(target)) return
        runEffectOperation { context ->
            val status = withContext(Dispatchers.IO) { EffectThemeRuntime.status(context, theme, target) }
            check(status.canRun) { status.label }
            check(NgThemeLibraryStore.applyAsync(context, theme)) { getString(R.string.ng_drawer_theme_apply_failed) }
        }
    }

    private fun applyStaticEffectTheme(theme: NgManagedTheme) {
        runEffectOperation { context ->
            check(NgThemeLibraryStore.applyStaticAsync(context, theme)) { getString(R.string.ng_drawer_theme_apply_failed) }
        }
    }

    private fun dismissMd3Import() {
        if (md3ImportInstalling) return
        pendingMd3ImportUri = null
        md3ImportDraft = null
    }

    private fun installMd3Theme(applyAfterInstall: Boolean) {
        val uri = pendingMd3ImportUri ?: return
        if (md3ImportInstalling) return
        md3ImportInstalling = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Md3ThemeImportManager.install(requireContext(), uri, applyAfterInstall)
                    .onSuccess { result ->
                        pendingMd3ImportUri = null
                        md3ImportDraft = null
                        toastOnUi(getString(R.string.ng_theme_import_success, result.theme.name))
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        Log.w(TAG, "Could not install theme package", error)
                        toastOnUi(R.string.ng_theme_import_save_failed)
                    }
            } finally {
                md3ImportInstalling = false
            }
        }
    }

    private fun editTheme(theme: NgManagedTheme) {
        if (savingTheme || copyingDrawerImage || preparingExport) return
        NgThemeDrawerAssets.discardDrafts(requireContext(), draftEditTheme?.drawerProfile)
        editingSession = UUID.randomUUID().toString()
        val editableBarProfile = NgThemeLibraryStore.editableBarProfile(
            requireContext(),
            theme.barProfile,
        )
        originalEditTheme = theme
        draftEditTheme = theme.copy(
            barProfile = editableBarProfile,
            drawerProfile = NgThemeLibraryStore.editableDrawerProfile(requireContext(), theme.drawerProfile),
        )
    }

    private fun updateThemeDraft(theme: NgManagedTheme) {
        if (savingTheme || copyingDrawerImage || preparingExport) return
        val oldSource = draftEditTheme?.drawerProfile?.source ?: "theme_color"
        val newSource = theme.drawerProfile?.source ?: "theme_color"
        if (newSource != oldSource && newSource != "custom_image") pendingDrawerImage = null
        draftEditTheme = theme
    }

    private fun dismissThemeEditor() {
        if (savingTheme || preparingExport) return
        NgThemeDrawerAssets.discardDrafts(requireContext(), draftEditTheme?.drawerProfile)
        originalEditTheme = null
        draftEditTheme = null
        pendingDarkBackground = null
        pendingDrawerImage = null
        editingSession = null
        copyingDrawerImage = false
    }

    private fun saveEditedTheme() {
        if (savingTheme || copyingDrawerImage || preparingExport) return
        val context = requireContext()
        val original = originalEditTheme ?: return
        val draft = draftEditTheme?.normalized() ?: return
        if (draft.name.isBlank()) {
            toastOnUi(R.string.ng_theme_name_required)
            return
        }
        val builtIn = original.isBuiltIn
        val targetName = if (builtIn && draft.name.equals(original.name, true)) {
            NgThemeLibraryStore.uniqueName(
                context,
                getString(R.string.ng_theme_copy_name, draft.name)
            )
        } else {
            draft.name
        }
        val conflict = NgThemeLibraryStore.current(context).savedThemes.any { existing ->
            existing.id != original.id && existing.name.equals(targetName, true)
        }
        if (conflict) {
            toastOnUi(R.string.ng_theme_name_conflict)
            return
        }
        val applyAfterSave = NgThemeLibraryStore.current(context).activeThemeId == original.id && !builtIn
        val applicationMode = NgThemeModeStore.current(context)
        val session = editingSession
        val applicationContext = context.applicationContext
        savingTheme = true
        lifecycleScope.launch {
            var storedTheme: NgManagedTheme? = null
            try {
                val (saved, applied) = withContext(NonCancellable) {
                    val stored = withContext(Dispatchers.IO) {
                        NgEffectPackageLibrary.addOrReplace(
                            applicationContext, NgEffectPackageKind.THEME,
                            draft.copy(
                                id = if (builtIn) "local.${UUID.randomUUID()}" else original.id,
                                name = targetName,
                            ),
                        )
                    }
                    storedTheme = stored
                    val stillSelected = NgThemeLibraryStore.current(applicationContext).activeThemeId == original.id
                        && NgThemeModeStore.current(applicationContext) == applicationMode
                    val effectIsInactive = stored.effect != null && applicationMode !in
                        setOf(NgThemePresentationMode.STANDARD, NgThemePresentationMode.EFFECT_THEME)
                    val applied = if (!applyAfterSave || !stillSelected || effectIsInactive) true
                    else if (stored.effect != null && applicationMode == NgThemePresentationMode.STANDARD) {
                        NgThemeLibraryStore.applyStaticAsync(applicationContext, stored)
                    } else NgThemeLibraryStore.applyAsync(applicationContext, stored)
                    stored to applied
                }
                if (editingSession != session) return@launch
                if (!applied) {
                    // The theme is saved, so retry against its materialized images and identity.
                    originalEditTheme = saved
                    draftEditTheme = saved
                    toastOnUi(R.string.ng_drawer_theme_apply_failed)
                    return@launch
                }
                savingTheme = false
                dismissThemeEditor()
                if (!applyAfterSave) toastOnUi(R.string.ng_theme_saved_success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (editingSession == session) {
                    storedTheme?.let {
                        originalEditTheme = it
                        draftEditTheme = it
                    }
                    toastOnUi(getString(R.string.ng_drawer_theme_save_failed, error.message.orEmpty()))
                }
            } finally {
                if (editingSession != session || storedTheme != null) {
                    NgThemeDrawerAssets.discardDrafts(applicationContext, draft.drawerProfile)
                }
                savingTheme = false
            }
        }
    }

    private fun selectThemeDrawerImage(dark: Boolean) {
        if (savingTheme || copyingDrawerImage || preparingExport) return
        val session = editingSession ?: return
        pendingDrawerImage = session to dark
        selectDrawerImage.launch(arrayOf("image/*"))
    }

    private fun selectThemeBackground(dark: Boolean) {
        if (savingTheme || copyingDrawerImage || preparingExport) return
        pendingDarkBackground = dark
        selectBackground.launch(arrayOf("image/*"))
    }

    private fun updateBackground(
        dark: Boolean,
        update: (io.legado.app.help.config.NgThemeBackground) -> io.legado.app.help.config.NgThemeBackground
    ) {
        val current = draftEditTheme ?: return
        draftEditTheme = if (dark) {
            current.copy(darkBackground = update(current.darkBackground))
        } else {
            current.copy(lightBackground = update(current.lightBackground))
        }
    }

    private suspend fun copyBackground(uri: Uri): String = withContext(Dispatchers.IO) {
        val context = requireContext().applicationContext
        val extension = when (context.contentResolver.getType(uri)?.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/jpeg" -> "jpg"
            else -> "img"
        }
        val root = File(context.filesDir, BACKGROUND_DIR).apply { mkdirs() }
        val target = File(root, "${UUID.randomUUID()}.$extension")
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取图片")
        input.use { source ->
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_BACKGROUND_BYTES) { "图片超过 32 MB" }
                    output.write(buffer, 0, read)
                }
            }
        }
        target.absolutePath
    }

    private fun requestExport(theme: NgManagedTheme) {
        if (savingTheme || copyingDrawerImage || preparingExport || pendingExportPackagePath != null) return
        val context = requireContext().applicationContext
        val session = editingSession
        preparingExport = true
        lifecycleScope.launch {
            var frozenPath: String? = null
            try {
                // Pin all draft resources into a complete package before opening the system picker.
                val frozen = withContext(NonCancellable + Dispatchers.IO) {
                    val root = File(context.cacheDir, EXPORT_DRAFT_DIR).apply { mkdirs() }
                    val target = File(root, "${UUID.randomUUID()}.zip")
                    frozenPath = target.absolutePath
                    try {
                        NgThemePackageManager.exportTheme(context, theme, Uri.fromFile(target)).getOrThrow()
                        if (!theme.ownedCoverAlbumIds.isNullOrEmpty() || theme.coverProfile?.albumId != null) {
                            NgThemePackageManager.inspectArchive(target)
                        }
                        target
                    } catch (error: Throwable) {
                        target.delete()
                        throw error
                    }
                }
                frozenPath = frozen.absolutePath
                if (!isAdded || view == null) return@launch
                pendingExportPackagePath = frozenPath
                exportTheme.launch("${theme.name.normalizeFileName()}.zip")
                frozenPath = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                pendingExportPackagePath = null
                toastOnUi(context.getString(R.string.ng_theme_export_failed, error.message.orEmpty()))
            } finally {
                discardExportPackage(context, frozenPath)
                if (editingSession != session) {
                    NgThemeDrawerAssets.discardDrafts(context, theme.drawerProfile)
                }
                preparingExport = false
            }
        }
    }

    private fun frozenExportPackage(context: android.content.Context, path: String): File? = runCatching {
        val root = File(context.cacheDir, EXPORT_DRAFT_DIR).canonicalFile
        File(path).canonicalFile.takeIf { it.parentFile == root && it.isFile }
    }.getOrNull()

    private fun discardExportPackage(context: android.content.Context, path: String?) {
        path?.let { frozenExportPackage(context, it)?.delete() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(PENDING_EXPORT_PACKAGE, pendingExportPackagePath)
        super.onSaveInstanceState(outState)
    }

    private fun deleteTheme(theme: NgManagedTheme) {
        if (effectBusy) return
        if (theme.effect != null) {
            runEffectOperation { context ->
                val removed = withContext(NonCancellable) {
                    NgThemeLibraryStore.removeAsync(context, theme.id)
                }
                if (removed != null) {
                    BookCover.upDefaultCover()
                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                }
            }
            return
        }
        if (savingTheme || preparingExport) return
        runCatching { NgThemeLibraryStore.remove(requireContext(), theme.id) }
            .onSuccess { removed ->
                if (removed != null) {
                    BookCover.upDefaultCover()
                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                }
            }
            .onFailure { toastOnUi(it.localizedMessage.orEmpty()) }
    }

    override fun onDestroyView() {
        if (!themePackageInstalling) {
            val pending = themePackageImport
            themePackageImport = null
            if (pending != null) lifecycleScope.launch(NonCancellable + Dispatchers.IO) { pending.discard() }
        }
        if (!savingTheme && !preparingExport) {
            context?.let { NgThemeDrawerAssets.discardDrafts(it, draftEditTheme?.drawerProfile) }
        }
        originalEditTheme = null
        draftEditTheme = null
        editingSession = null
        pendingDrawerImage = null
        pendingDarkBackground = null
        copyingDrawerImage = false
        setSharedTitleBarVisible(true)
        super.onDestroyView()
    }

    override fun onDestroy() {
        if (activity?.isChangingConfigurations != true) {
            context?.let { discardExportPackage(it, pendingExportPackagePath) }
            pendingExportPackagePath = null
        }
        super.onDestroy()
    }

    private companion object {
        private const val TAG = "ThemeManager"
        private const val PENDING_EXPORT_PACKAGE = "pendingExportPackage"
        private const val EXPORT_DRAFT_DIR = "ng_theme_export_drafts"

        private const val BACKGROUND_DIR = "ng_theme_backgrounds"
        private const val MAX_BACKGROUND_BYTES = 32L * 1024 * 1024
    }
}
