package io.legado.app.ui.config.topbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignTop
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.legado.app.R
import io.legado.app.help.config.TopBarConfig
import io.legado.app.ui.config.topbar.components.TopBarEditCallbacks
import io.legado.app.ui.config.topbar.components.TopBarEditDialog
import io.legado.app.ui.config.widget.ConfigActionButton
import io.legado.app.ui.config.widget.ConfigActionIconMenu
import io.legado.app.ui.config.widget.ConfigAddBottomBar
import io.legado.app.ui.config.widget.ConfigItemMoreButton
import io.legado.app.ui.config.widget.ConfigList
import io.legado.app.ui.config.widget.ConfigManageCard
import io.legado.app.ui.config.widget.ConfigManageScaffold
import io.legado.app.ui.config.widget.ConfigManageState
import io.legado.app.ui.config.widget.ConfigMenuItem
import io.legado.app.ui.config.widget.ConfigMultiSelectBar
import io.legado.app.ui.config.widget.DayNightPager
import io.legado.app.ui.config.widget.MultiSelectAction
import io.legado.app.ui.config.widget.SelectAllAction
import io.legado.app.ui.config.widget.rememberSharedConfigManageState
import io.legado.app.ui.widget.components.VerticalScrollbar
import io.legado.app.ui.widget.components.dialog.AppConfirmDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 顶栏管理页的对外回调。 */
data class TopBarManageActions(
    val onBackClick: () -> Unit = {},
    val onToast: (Int) -> Unit = {},
    val onToastMsg: (String) -> Unit = {},
    val onShareJson: (String) -> Unit = {},
    val onExportZip: (java.io.File) -> Unit = {},
    val onPickColor: (target: Int, current: Int) -> Unit = { _, _ -> },
    val onPickWallpaper: () -> Unit = {},
    val onPickImportZip: () -> Unit = {}
)

/**
 * 顶栏管理页。
 *
 * 与底栏管理同构：日/夜 Tab 共用状态、长按多选、单条「更多」含置顶。
 * 顶栏配置以目录包形式存储，新增/编辑的保存路径由 ViewModel 处理。
 */
@Composable
fun TopBarManageScreen(
    viewModel: TopBarManageViewModel,
    actions: TopBarManageActions,
    modifier: Modifier = Modifier
) {
    val state = rememberSharedConfigManageState()
    val items by viewModel.items.collectAsState()
    val activeDirName by viewModel.activeDirName.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingDelete by remember { mutableStateOf<List<TopBarConfig.Entry>>(emptyList()) }

    val isNight = state.tab.isNight
    LaunchedEffect(isNight) { viewModel.load(isNight) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is TopBarEvent.Toast -> actions.onToast(event.resId)
                    is TopBarEvent.ToastMsg -> actions.onToastMsg(event.msg)
                    is TopBarEvent.ShareJson -> actions.onShareJson(event.json)
                    is TopBarEvent.ExportZip -> actions.onExportZip(event.file)
                }
            }
        }
    }

    val visibleKeys = remember(items, isNight) {
        items.filter { it.config.isNightMode == isNight }.map { it.dirName }
    }

    ConfigManageScaffold(
        title = stringResource(R.string.top_bar_manage),
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
                ConfigActionIconMenu(
                    icon = Icons.Default.FileDownload,
                    contentDescription = stringResource(R.string.import_str),
                    items = listOf(
                        ConfigMenuItem(
                            text = stringResource(R.string.top_bar_import_zip),
                            onClick = actions.onPickImportZip
                        ),
                        ConfigMenuItem(
                            text = stringResource(R.string.top_bar_import_clipboard),
                            onClick = viewModel::importFromClipboard
                        )
                    )
                )
            }
        },
        bottomBar = {
            if (state.isMultiSelectMode) {
                val selected = items.filter { it.dirName in state.multiSelect.selectedKeys }
                ConfigMultiSelectBar(
                    selectedCount = state.selectedCount,
                    actions = listOf(
                        MultiSelectAction(
                            icon = Icons.Default.VerticalAlignTop,
                            contentDescription = stringResource(R.string.to_top),
                            onClick = {
                                viewModel.toTopEntries(selected)
                                state.exitMultiSelect()
                            }
                        ),
                        MultiSelectAction(
                            icon = Icons.Default.Share,
                            contentDescription = stringResource(R.string.export),
                            onClick = {
                                viewModel.exportJson(selected, single = false)
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
                    text = stringResource(R.string.add_top_bar_config),
                    onClick = viewModel::startCreate
                )
            }
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            DayNightPager(
                state = state,
                onTabChange = state::switchTab,
                summaryText = stringResource(
                    R.string.top_bar_summary,
                    stringResource(if (isNight) R.string.night else R.string.day)
                ),
                scrollEnabled = !state.isMultiSelectMode,
                dayContent = {
                    TopBarList(
                        items = items.filter { !it.config.isNightMode },
                        state = state,
                        activeDirName = activeDirName,
                        onApply = viewModel::applyPackage,
                        onEdit = viewModel::startEdit,
                        onDelete = { pendingDelete = listOf(it) },
                        onToTop = viewModel::toTop,
                        onExportZip = viewModel::exportZip,
                        onShareJson = { viewModel.exportJson(listOf(it), single = true) },
                        onImportClipboard = viewModel::importFromClipboard,
                        onLongClick = { state.enterMultiSelect(it.dirName) },
                        onToggleSelect = { state.toggleSelection(it.dirName) }
                    )
                },
                nightContent = {
                    TopBarList(
                        items = items.filter { it.config.isNightMode },
                        state = state,
                        activeDirName = activeDirName,
                        onApply = viewModel::applyPackage,
                        onEdit = viewModel::startEdit,
                        onDelete = { pendingDelete = listOf(it) },
                        onToTop = viewModel::toTop,
                        onExportZip = viewModel::exportZip,
                        onShareJson = { viewModel.exportJson(listOf(it), single = true) },
                        onImportClipboard = viewModel::importFromClipboard,
                        onLongClick = { state.enterMultiSelect(it.dirName) },
                        onToggleSelect = { state.toggleSelection(it.dirName) }
                    )
                }
            )
        }
    }

    draft?.let { config ->
        TopBarEditDialog(
            config = config,
            isEdit = viewModel.isEditingExisting,
            callbacks = TopBarEditCallbacks(
                onDraftChange = viewModel::updateDraft,
                onRequestColor = actions.onPickColor,
                onRequestWallpaper = actions.onPickWallpaper,
                onSave = viewModel::saveDraft,
                onDismiss = viewModel::clearDraft
            )
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
            onDismissRequest = { pendingDelete = emptyList() }
        )
    }
}

@Composable
private fun TopBarList(
    items: List<TopBarConfig.Entry>,
    state: ConfigManageState,
    activeDirName: String?,
    onApply: (TopBarConfig.Entry) -> Unit,
    onEdit: (TopBarConfig.Entry) -> Unit,
    onDelete: (TopBarConfig.Entry) -> Unit,
    onToTop: (TopBarConfig.Entry) -> Unit,
    onExportZip: (TopBarConfig.Entry) -> Unit,
    onShareJson: (TopBarConfig.Entry) -> Unit,
    onImportClipboard: () -> Unit,
    onLongClick: (TopBarConfig.Entry) -> Unit,
    onToggleSelect: (TopBarConfig.Entry) -> Unit
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        ConfigList(
            items = items,
            listState = listState,
            itemKey = { it.dirName },
            emptyText = stringResource(R.string.empty)
        ) { item ->
            TopBarCard(
                entry = item,
                isCurrent = item.dirName == activeDirName,
                state = state,
                onApply = { onApply(item) },
                onEdit = { onEdit(item) },
                onDelete = { onDelete(item) },
                onToTop = { onToTop(item) },
                onExportZip = { onExportZip(item) },
                onShareJson = { onShareJson(item) },
                onImportClipboard = onImportClipboard,
                onLongClick = { onLongClick(item) },
                onToggleSelect = { onToggleSelect(item) }
            )
        }
        VerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun TopBarCard(
    entry: TopBarConfig.Entry,
    isCurrent: Boolean,
    state: ConfigManageState,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToTop: () -> Unit,
    onExportZip: () -> Unit,
    onShareJson: () -> Unit,
    onImportClipboard: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val isBuiltin = entry.dirName == TopBarConfig.DEFAULT_DIR_NAME
    val summary = buildString {
        append(
            stringResource(
                if (entry.config.style == TopBarConfig.STYLE_REGULAR) {
                    R.string.regular_top_bar
                } else {
                    R.string.default_top_bar
                }
            )
        )
        if (entry.config.style == TopBarConfig.STYLE_REGULAR) {
            append(" · ")
            append(stringResource(R.string.corner_scale))
            append(" ")
            append(String.format(Locale.ROOT, "%.1f", (entry.config.cornerScale ?: 1f).coerceIn(0f, 3f)))
        }
        if (entry.config.updatedAt > 0) {
            append(" · ")
            append(dateFormat.format(Date(entry.config.updatedAt)))
        }
    }
    ConfigManageCard(
        title = entry.config.name,
        summary = summary,
        isCurrent = isCurrent,
        showBuiltinBadge = isBuiltin,
        isMultiSelectMode = state.isMultiSelectMode,
        isSelected = entry.dirName in state.multiSelect.selectedKeys,
        onLongClick = onLongClick,
        onToggleSelect = onToggleSelect,
        actions = {
            ConfigActionButton(
                text = stringResource(R.string.apply),
                onClick = onApply,
                highlighted = isCurrent
            )
            if (!isBuiltin) {
                ConfigActionButton(
                    text = stringResource(R.string.edit),
                    onClick = onEdit
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
                        add(ConfigMenuItem(stringResource(R.string.export_str), onExportZip))
                        add(ConfigMenuItem(stringResource(R.string.share_top_bar_config), onShareJson))
                    } else {
                        add(ConfigMenuItem(stringResource(R.string.top_bar_import_clipboard), onImportClipboard))
                    }
                    if (!isBuiltin && !isCurrent) {
                        add(ConfigMenuItem(stringResource(R.string.delete), onDelete))
                    }
                }
            )
        }
    )
}
