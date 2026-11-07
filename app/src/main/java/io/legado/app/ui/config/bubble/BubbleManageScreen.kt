package io.legado.app.ui.config.bubble

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.legado.app.R
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.ui.config.bubble.components.BubbleEditCallbacks
import io.legado.app.ui.config.bubble.components.BubbleEditDialog
import io.legado.app.ui.config.bubble.components.BubblePreview
import io.legado.app.ui.config.widget.ConfigActionButton
import io.legado.app.ui.config.widget.ConfigActionIconMenu
import io.legado.app.ui.config.widget.ConfigAddBottomBar
import io.legado.app.ui.config.widget.ConfigItemMoreButton
import io.legado.app.ui.config.widget.ConfigList
import io.legado.app.ui.config.widget.ConfigManageCard
import io.legado.app.ui.config.widget.ConfigManageScaffold
import io.legado.app.ui.config.widget.ConfigManageState
import io.legado.app.ui.config.widget.ConfigMenuItem
import io.legado.app.ui.config.widget.ConfigMultiSelectTextAction
import io.legado.app.ui.config.widget.ConfigUrlImportDialog
import io.legado.app.ui.config.widget.rememberConfigManageState
import io.legado.app.ui.widget.components.VerticalScrollbar
import io.legado.app.ui.widget.components.dialog.AppConfirmDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 气泡管理页的对外回调。 */
data class BubbleManageActions(
    val onBackClick: () -> Unit = {},
    val onToast: (Int) -> Unit = {},
    val onToastMsg: (String) -> Unit = {},
    val onShareJson: (String) -> Unit = {},
    val onExportZip: (java.io.File) -> Unit = {},
    val onPickZip: () -> Unit = {},
    val onPickColor: (target: Int, current: String) -> Unit = { _, _ -> },
    val onEditSvg: (String) -> Unit = {},
    val onHelp: () -> Unit = {},
)

/**
 * 气泡管理页。
 *
 * 气泡包本身不分日夜（一套模板同时提供日/夜配色），因此没有日/夜 Tab，
 * 使用不带 Tab 的管理外壳；长按多选与「更多」置顶与其他管理页保持一致。
 */
@Composable
fun BubbleManageScreen(
    viewModel: BubbleManageViewModel,
    actions: BubbleManageActions,
    modifier: Modifier = Modifier,
) {
    val state = rememberConfigManageState()
    val items by viewModel.items.collectAsState()
    val activeDirName by viewModel.activeDirName.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val urlHistory by viewModel.urlHistory.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingDelete by remember { mutableStateOf<List<BubblePackageManager.Entry>>(emptyList()) }
    var showUrlDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is BubbleEvent.Toast -> actions.onToast(event.resId)
                    is BubbleEvent.ToastMsg -> actions.onToastMsg(event.msg)
                    is BubbleEvent.ShareJson -> actions.onShareJson(event.json)
                    is BubbleEvent.ExportZip -> actions.onExportZip(event.file)
                }
            }
        }
    }

    val visibleKeys = remember(items) { items.map { it.dirName } }

    ConfigManageScaffold(
        title = stringResource(R.string.bubble_manage),
        isMultiSelectMode = state.isMultiSelectMode,
        selectedCount = state.selectedCount,
        onBackClick = actions.onBackClick,
        onExitMultiSelect = state::exitMultiSelect,
        modifier = modifier,
        actions = {
            ConfigActionIconMenu(
                icon = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more),
                items = listOf(
                    ConfigMenuItem(
                        text = stringResource(R.string.bubble_import_zip),
                        onClick = actions.onPickZip,
                    ),
                    ConfigMenuItem(
                        text = stringResource(R.string.import_on_line),
                        onClick = { showUrlDialog = true },
                    ),
                    ConfigMenuItem(
                        text = stringResource(R.string.help),
                        onClick = actions.onHelp,
                    ),
                ),
            )
        },
        multiSelectActions = {
            val selected = items.filter { it.dirName in state.multiSelect.selectedKeys }
            ConfigMultiSelectTextAction(stringResource(R.string.select_all)) {
                state.toggleSelectAllVisible(visibleKeys)
            }
            ConfigMultiSelectTextAction(stringResource(R.string.to_top)) {
                viewModel.toTopEntries(selected)
                state.exitMultiSelect()
            }
            ConfigMultiSelectTextAction(stringResource(R.string.export)) {
                viewModel.exportZip(selected)
                state.exitMultiSelect()
            }
            ConfigMultiSelectTextAction(stringResource(R.string.delete)) {
                pendingDelete = selected
            }
        },
        bottomBar = {
            ConfigAddBottomBar(
                text = stringResource(R.string.bubble_create_manual),
                onClick = viewModel::startCreate,
            )
        },
    ) { contentPadding ->
        BubbleList(
            items = items,
            state = state,
            activeDirName = activeDirName,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            onApply = viewModel::applyEntry,
            onEdit = viewModel::startEdit,
            onDelete = { pendingDelete = listOf(it) },
            onToTop = { viewModel.toTopEntries(listOf(it)) },
            onExport = { viewModel.exportZip(listOf(it)) },
            onShare = viewModel::shareJson,
            onLongClick = { state.enterMultiSelect(it.dirName) },
            onToggleSelect = { state.toggleSelection(it.dirName) },
        )
    }

    draft?.let { config ->
        BubbleEditDialog(
            config = config,
            isEdit = viewModel.isEditingExisting,
            callbacks = BubbleEditCallbacks(
                onDraftChange = viewModel::updateDraft,
                onRequestColor = actions.onPickColor,
                onEditSvg = { actions.onEditSvg(config.svgTemplate) },
                onSave = viewModel::saveDraft,
                onDismiss = viewModel::clearDraft,
            ),
        )
    }

    if (showUrlDialog) {
        ConfigUrlImportDialog(
            title = stringResource(R.string.import_on_line),
            history = urlHistory,
            onConfirm = { url ->
                showUrlDialog = false
                viewModel.importFromUrl(url)
            },
            onDismissRequest = { showUrlDialog = false },
        )
    }

    if (pendingDelete.isNotEmpty()) {
        AppConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.sure_del),
            confirmText = stringResource(R.string.ok),
            destructive = true,
            onConfirm = {
                viewModel.deleteEntries(pendingDelete)
                pendingDelete = emptyList()
                state.exitMultiSelect()
            },
            onDismissRequest = { pendingDelete = emptyList() },
        )
    }
}

@Composable
private fun BubbleList(
    items: List<BubblePackageManager.Entry>,
    state: ConfigManageState,
    activeDirName: String,
    modifier: Modifier = Modifier,
    onApply: (BubblePackageManager.Entry) -> Unit,
    onEdit: (BubblePackageManager.Entry) -> Unit,
    onDelete: (BubblePackageManager.Entry) -> Unit,
    onToTop: (BubblePackageManager.Entry) -> Unit,
    onExport: (BubblePackageManager.Entry) -> Unit,
    onShare: (BubblePackageManager.Entry) -> Unit,
    onLongClick: (BubblePackageManager.Entry) -> Unit,
    onToggleSelect: (BubblePackageManager.Entry) -> Unit,
) {
    val listState = rememberLazyListState()
    Box(modifier = modifier) {
        ConfigList(
            items = items,
            listState = listState,
            itemKey = { it.dirName },
            emptyText = stringResource(R.string.empty),
        ) { item ->
            BubbleCard(
                entry = item,
                isCurrent = item.dirName == activeDirName,
                state = state,
                onApply = { onApply(item) },
                onEdit = { onEdit(item) },
                onDelete = { onDelete(item) },
                onToTop = { onToTop(item) },
                onExport = { onExport(item) },
                onShare = { onShare(item) },
                onLongClick = { onLongClick(item) },
                onToggleSelect = { onToggleSelect(item) },
            )
        }
        VerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun BubbleCard(
    entry: BubblePackageManager.Entry,
    isCurrent: Boolean,
    state: ConfigManageState,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToTop: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val isBuiltin = entry.source == BubblePackageManager.Source.BUILTIN
    // 内置包已由卡片上的「内置」标签表达来源，摘要只保留日期，避免出现「内置 · 内置」
    val summary = if (isBuiltin || entry.config.updatedAt <= 0L) {
        ""
    } else {
        stringResource(R.string.bubble_source_local) + " · " + dateFormat.format(Date(entry.config.updatedAt))
    }
    ConfigManageCard(
        title = entry.config.name,
        summary = summary,
        isCurrent = isCurrent,
        showBuiltinBadge = isBuiltin,
        isMultiSelectMode = state.isMultiSelectMode,
        isSelected = entry.dirName in state.multiSelect.selectedKeys,
        leading = { BubblePreview(config = entry.config) },
        onLongClick = if (isBuiltin) null else onLongClick,
        onToggleSelect = onToggleSelect,
        actions = {
            ConfigActionButton(
                text = stringResource(R.string.apply),
                onClick = onApply,
                highlighted = isCurrent,
            )
            if (!isBuiltin) {
                ConfigActionButton(
                    text = stringResource(R.string.edit),
                    onClick = onEdit,
                )
            }
            ConfigItemMoreButton(
                items = buildList {
                    if (!isBuiltin) {
                        add(ConfigMenuItem(stringResource(R.string.to_top), onToTop))
                    }
                    add(ConfigMenuItem(stringResource(R.string.apply), onApply))
                    if (!isBuiltin) {
                        add(ConfigMenuItem(stringResource(R.string.edit), onEdit))
                        add(ConfigMenuItem(stringResource(R.string.export_str), onExport))
                        add(ConfigMenuItem(stringResource(R.string.share), onShare))
                    }
                    if (!isBuiltin && !isCurrent) {
                        add(ConfigMenuItem(stringResource(R.string.delete), onDelete))
                    }
                },
            )
        },
    )
}
