package io.legado.app.ui.config.theme.application

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import io.legado.app.help.config.ApplicationThemeManager
import io.legado.app.ui.config.theme.application.components.ApplicationThemePreview
import io.legado.app.ui.config.theme.application.components.ThemeComponentOptionsDialog
import io.legado.app.ui.config.theme.application.components.ThemeComponentSelection
import io.legado.app.ui.config.widget.ConfigActionButton
import io.legado.app.ui.config.widget.ConfigAddBottomBar
import io.legado.app.ui.config.widget.ConfigItemMoreButton
import io.legado.app.ui.config.widget.ConfigList
import io.legado.app.ui.config.widget.ConfigManageCard
import io.legado.app.ui.config.widget.ConfigManageScaffold
import io.legado.app.ui.config.widget.ConfigMenuItem
import io.legado.app.ui.config.widget.ConfigMultiSelectBar
import io.legado.app.ui.config.widget.ConfigNameDialog
import io.legado.app.ui.config.widget.ConfigTab
import io.legado.app.ui.config.widget.MultiSelectAction
import io.legado.app.ui.config.widget.SelectAllAction
import io.legado.app.ui.config.widget.rememberConfigManageState
import io.legado.app.ui.widget.components.VerticalScrollbar
import io.legado.app.ui.widget.components.dialog.AppConfirmDialog
import io.legado.app.ui.widget.components.dialog.AppRadioChoiceDialog

/** 应用主题页的对外回调。 */
data class ApplicationThemeActions(
    val onBackClick: () -> Unit = {},
    val onToast: (Int) -> Unit = {},
    val onToastMsg: (String) -> Unit = {},
    val onCopyText: (String) -> Unit = {},
    val onExportFile: (java.io.File) -> Unit = {},
    val onOpenEditor: (String?) -> Unit = {},
    val onPickImportFile: () -> Unit = {},
    val onRestart: () -> Unit = {}
)

/**
 * 应用主题列表页。
 *
 * 与其它管理页统一：卡片同为「预览 + 名称 + 标签 + 摘要 + 应用/编辑/更多」，
 * 「已应用」用胶囊标签而非按钮文案，长按进入多选（置顶/导出/删除），
 * 单条「更多」里也提供置顶。
 */
@Composable
fun ApplicationThemeScreen(
    viewModel: ApplicationThemeViewModel,
    actions: ApplicationThemeActions,
    modifier: Modifier = Modifier
) {
    val state = rememberConfigManageState(ConfigTab.DAY)
    val items by viewModel.items.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    val currentId by viewModel.currentId.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    var showImportOptions by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ApplicationThemeManager.Config?>(null) }
    var renameTarget by remember { mutableStateOf<ApplicationThemeManager.Config?>(null) }
    var exportTarget by remember { mutableStateOf<ApplicationThemeManager.Config?>(null) }
    var pendingDelete by remember { mutableStateOf<List<ApplicationThemeManager.Config>>(emptyList()) }
    var importSelection by remember { mutableStateOf(ThemeComponentSelection()) }
    var deleteSelection by remember { mutableStateOf(ThemeComponentSelection()) }

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is ApplicationThemeEvent.Toast -> actions.onToast(event.resId)
                    is ApplicationThemeEvent.ToastMsg -> actions.onToastMsg(event.msg)
                    is ApplicationThemeEvent.CopyText -> actions.onCopyText(event.text)
                    is ApplicationThemeEvent.ExportFile -> actions.onExportFile(event.file)
                    ApplicationThemeEvent.Applied -> actions.onRestart()
                }
            }
        }
    }

    val visibleKeys = remember(items) { items.map { it.id } }

    ConfigManageScaffold(
        title = stringResource(R.string.application_theme_manage),
        isMultiSelectMode = state.isMultiSelectMode,
        onBackClick = actions.onBackClick,
        onExitMultiSelect = state::exitMultiSelect,
        modifier = modifier,
        actions = {
            if (state.isMultiSelectMode) {
                SelectAllAction(
                    isAllSelected = state.isAllSelected(visibleKeys),
                    onSelectAll = { state.selectAllVisible(visibleKeys) }
                )
            } else {
                IconButton(onClick = {
                    importSelection = viewModel.importSelection()
                    showImportOptions = true
                }) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = stringResource(R.string.application_theme_import)
                    )
                }
            }
        },
        bottomBar = {
            if (state.isMultiSelectMode) {
                val selected = items.filter { it.id in state.multiSelect.selectedKeys }
                ConfigMultiSelectBar(
                    selectedCount = state.selectedCount,
                    actions = listOf(
                        MultiSelectAction(
                            icon = Icons.Default.VerticalAlignTop,
                            contentDescription = stringResource(R.string.to_top),
                            onClick = {
                                viewModel.toTop(selected)
                                state.exitMultiSelect()
                            }
                        ),
                        MultiSelectAction(
                            icon = Icons.Default.Share,
                            contentDescription = stringResource(R.string.export),
                            onClick = {
                                viewModel.copyJsonList(selected)
                                state.exitMultiSelect()
                            }
                        ),
                        MultiSelectAction(
                            icon = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                            onClick = { pendingDelete = selected }
                        )
                    )
                )
            } else {
                ConfigAddBottomBar(
                    text = stringResource(R.string.application_theme_create),
                    onClick = { actions.onOpenEditor(null) }
                )
            }
        }
    ) { contentPadding ->
        val listState = rememberLazyListState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            ConfigList(
                items = items,
                listState = listState,
                itemKey = { it.id },
                emptyText = stringResource(R.string.empty)
            ) { item ->
                val isBuiltin = ApplicationThemeManager.isBuiltin(item)
                val isCurrent = item.id == currentId
                ConfigManageCard(
                    title = item.name,
                    summary = summaries[item.id].orEmpty(),
                    isCurrent = isCurrent,
                    showBuiltinBadge = isBuiltin,
                    isMultiSelectMode = state.isMultiSelectMode,
                    isSelected = item.id in state.multiSelect.selectedKeys,
                    leading = { ApplicationThemePreview(config = item) },
                    onLongClick = if (isBuiltin) null else {
                        { state.enterMultiSelect(item.id) }
                    },
                    onToggleSelect = { state.toggleSelection(item.id) },
                    actions = {
                        ConfigActionButton(
                            text = stringResource(R.string.apply),
                            onClick = { viewModel.apply(item) },
                            highlighted = isCurrent
                        )
                        if (!isBuiltin) {
                            ConfigActionButton(
                                text = stringResource(R.string.edit),
                                onClick = { actions.onOpenEditor(item.id) }
                            )
                        }
                        ConfigItemMoreButton(
                            items = buildList {
                                if (!isBuiltin) {
                                    add(
                                        ConfigMenuItem(
                                            text = stringResource(R.string.to_top),
                                            onClick = { viewModel.toTop(listOf(item)) }
                                        )
                                    )
                                }
                                add(ConfigMenuItem(stringResource(R.string.apply)) { viewModel.apply(item) })
                                if (!isBuiltin) {
                                    add(
                                        ConfigMenuItem(stringResource(R.string.edit)) {
                                            actions.onOpenEditor(item.id)
                                        }
                                    )
                                    add(
                                        ConfigMenuItem(stringResource(R.string.application_theme_update_current)) {
                                            viewModel.updateFromCurrent(item)
                                        }
                                    )
                                    add(
                                        ConfigMenuItem(stringResource(R.string.application_theme_rename)) {
                                            renameTarget = item
                                        }
                                    )
                                }
                                add(
                                    ConfigMenuItem(stringResource(R.string.copy)) {
                                        viewModel.copyJson(item)
                                    }
                                )
                                add(
                                    ConfigMenuItem(stringResource(R.string.export_str)) {
                                        exportTarget = item
                                    }
                                )
                                if (!isBuiltin && !isCurrent) {
                                    add(
                                        ConfigMenuItem(stringResource(R.string.delete)) {
                                            deleteSelection = viewModel.deleteSelection()
                                            deleteTarget = item
                                        }
                                    )
                                }
                            }
                        )
                    }
                )
            }
            VerticalScrollbar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }

    if (showImportOptions) {
        ThemeComponentOptionsDialog(
            title = stringResource(R.string.application_theme_import_with_options),
            selection = importSelection,
            onConfirm = { selection ->
                importSelection = selection
                viewModel.saveImportOptions(selection)
                showImportOptions = false
                actions.onPickImportFile()
            },
            onDismissRequest = { showImportOptions = false }
        )
    }

    deleteTarget?.let { target ->
        ThemeComponentOptionsDialog(
            title = stringResource(R.string.application_theme_delete_options_hint, target.name),
            selection = deleteSelection,
            onConfirm = { selection ->
                deleteSelection = selection
                viewModel.saveDeleteOptions(selection)
                viewModel.delete(target.id, selection)
                deleteTarget = null
            },
            onDismissRequest = { deleteTarget = null }
        )
    }

    renameTarget?.let { target ->
        ConfigNameDialog(
            title = stringResource(R.string.application_theme_rename),
            initialName = target.name,
            hint = stringResource(R.string.application_theme_name),
            onConfirm = { name ->
                viewModel.rename(target.id, name)
                renameTarget = null
            },
            onDismissRequest = { renameTarget = null }
        )
    }

    exportTarget?.let { target ->
        val formats = ApplicationThemeManager.ExportFormat.entries
        AppRadioChoiceDialog(
            title = stringResource(R.string.export_str),
            options = formats.map { formatLabel(it) },
            selectedIndex = 0,
            onSelect = { index ->
                val format = formats.getOrNull(index)
                exportTarget = null
                if (format != null) viewModel.export(target, format)
            },
            onDismissRequest = { exportTarget = null }
        )
    }

    if (pendingDelete.isNotEmpty()) {
        AppConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.sure_del),
            confirmText = stringResource(R.string.ok),
            destructive = true,
            onConfirm = {
                viewModel.deleteMany(pendingDelete)
                pendingDelete = emptyList()
                state.exitMultiSelect()
            },
            onDismissRequest = { pendingDelete = emptyList() }
        )
    }
}

@Composable
private fun formatLabel(format: ApplicationThemeManager.ExportFormat): String {
    return stringResource(
        when (format) {
            ApplicationThemeManager.ExportFormat.NATIVE -> R.string.export_format_native
            ApplicationThemeManager.ExportFormat.ARCHIVE -> R.string.export_format_archive
            ApplicationThemeManager.ExportFormat.MD3 -> R.string.export_format_md3
            ApplicationThemeManager.ExportFormat.RED -> R.string.export_format_red
        }
    )
}
