package io.legado.app.ui.config.theme.manage

import io.legado.app.lib.theme.eInkGrayscale

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.legado.app.R
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.config.theme.manage.components.ThemeCard
import io.legado.app.ui.config.theme.manage.components.ThemeEditDialog
import io.legado.app.ui.config.widget.ConfigAddBottomBar
import io.legado.app.ui.config.widget.ConfigList
import io.legado.app.ui.config.widget.ConfigManageScaffold
import io.legado.app.ui.config.widget.ConfigMultiSelectBar
import io.legado.app.ui.config.widget.ConfigTab
import io.legado.app.ui.config.widget.DayNightPager
import io.legado.app.ui.config.widget.ImportFromClipboardAction
import io.legado.app.ui.config.widget.MultiSelectAction
import io.legado.app.ui.config.widget.SelectAllAction
import io.legado.app.ui.config.widget.rememberSharedConfigManageState
import io.legado.app.ui.widget.components.AppSearchBar
import io.legado.app.ui.widget.components.VerticalScrollbar

@Composable
fun ThemeManageScreen(
    viewModel: ThemeManageViewModel,
    onBackClick: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onImportEmpty: () -> Unit,
    onImportFailed: () -> Unit,
    onSelectImage: () -> Unit,
    onShareJson: (String) -> Unit,
    onDeleteConfirm: () -> Unit,
    onToast: (Int) -> Unit = {},
    onToastMsg: (String) -> Unit = {},
    onColorClick: (colorKey: String, currentColor: String) -> Unit = { _, _ -> },
    onBlurClick: (currentBlur: Int) -> Unit = {},
) {
    val state = rememberSharedConfigManageState()
    var searchQuery by remember { mutableStateOf("") }
    val allItems by viewModel.items.collectAsState()
    val editDraft by viewModel.editDraft.collectAsState()
    val appliedThemeTemplate = stringResource(R.string.applied_theme_config)
    val themeSummary = stringResource(R.string.theme_summary)
    val dayItems = remember(allItems, searchQuery) {
        allItems.filter { !it.config.isNightTheme && it.config.themeName.contains(searchQuery.trim(), ignoreCase = true) }
    }
    val nightItems = remember(allItems, searchQuery) {
        allItems.filter { it.config.isNightTheme && it.config.themeName.contains(searchQuery.trim(), ignoreCase = true) }
    }
    val visibleItems = if (state.tab == ConfigTab.DAY) dayItems else nightItems
    val visibleKeys = remember(visibleItems) { visibleItems.map { it.key } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentConfig = remember(allItems, context) {
        ThemeConfig.getDurConfig(context)
    }

    var pendingDeleteItem by remember { mutableStateOf<ThemeItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ThemeEvent.Toast -> onToast(event.resId)
                is ThemeEvent.ToastMsg -> onToastMsg(event.msg)
                is ThemeEvent.ImportSuccess -> onImportFromClipboard()
                is ThemeEvent.ImportEmpty -> onImportEmpty()
                is ThemeEvent.ImportFailed -> onImportFailed()
                is ThemeEvent.ShareJson -> onShareJson(event.json)
                is ThemeEvent.DeleteConfirm -> onDeleteConfirm()
                is ThemeEvent.Applied -> onToastMsg(appliedThemeTemplate.format(event.themeName))
            }
        }
    }

    ConfigManageScaffold(
        title = stringResource(R.string.theme_list),
        isMultiSelectMode = state.isMultiSelectMode,
        onBackClick = onBackClick,
        onExitMultiSelect = state::exitMultiSelect,
        actions = {
            if (state.isMultiSelectMode) {
                SelectAllAction(
                    isAllSelected = state.isAllSelected(visibleKeys),
                    onSelectAll = { state.selectAllVisible(visibleKeys) },
                )
            } else {
                ImportFromClipboardAction(viewModel::importFromClipboard)
            }
        },
        bottomBar = {
            if (state.isMultiSelectMode) {
                ConfigMultiSelectBar(
                    selectedCount = state.selectedCount,
                    actions = listOf(
                        MultiSelectAction(
                            icon = Icons.Default.VerticalAlignTop,
                            contentDescription = stringResource(R.string.to_top),
                            onClick = {
                                viewModel.toTopSelected(state.multiSelect.selectedKeys.toSet())
                                state.exitMultiSelect()
                            },
                        ),
                        MultiSelectAction(
                            icon = Icons.Default.Share,
                            contentDescription = stringResource(R.string.export),
                            onClick = {
                                viewModel.exportSelected(state.multiSelect.selectedKeys.toSet())
                                state.exitMultiSelect()
                            },
                        ),
                        MultiSelectAction(
                            icon = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                            onClick = {
                                viewModel.requestDeleteSelected(state.multiSelect.selectedKeys.toSet())
                            },
                        ),
                    ),
                )
            } else {
                ConfigAddBottomBar(
                    text = stringResource(R.string.add_theme),
                    onClick = {
                        val draft = viewModel.startNew(state.tab.isNight)
                        state.openEditDialog(isNew = true, editingKey = draft.editingKey)
                    },
                )
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            AppSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                hint = stringResource(R.string.search),
            )
            DayNightPager(
                state = state,
                onTabChange = state::switchTab,
                summaryText = themeSummary,
                scrollEnabled = !state.isMultiSelectMode,
                dayContent = {
                    ThemeList(
                        items = dayItems,
                        state = state,
                        currentConfig = currentConfig,
                        onApply = viewModel::applyConfig,
                        onEdit = { item ->
                            val draft = viewModel.startEdit(item)
                            state.openEditDialog(draft.isNew, draft.editingKey)
                        },
                        onShare = viewModel::shareItem,
                        onDelete = { item -> pendingDeleteItem = item },
                        onCopy = viewModel::copyItem,
                        onLongClick = { item -> state.enterMultiSelect(item.key) },
                        onToggleSelect = { item -> state.toggleSelection(item.key) },
                    )
                },
                nightContent = {
                    ThemeList(
                        items = nightItems,
                        state = state,
                        currentConfig = currentConfig,
                        onApply = viewModel::applyConfig,
                        onEdit = { item ->
                            val draft = viewModel.startEdit(item)
                            state.openEditDialog(draft.isNew, draft.editingKey)
                        },
                        onShare = viewModel::shareItem,
                        onDelete = { item -> pendingDeleteItem = item },
                        onCopy = viewModel::copyItem,
                        onLongClick = { item -> state.enterMultiSelect(item.key) },
                        onToggleSelect = { item -> state.toggleSelection(item.key) },
                    )
                },
            )
        }
    }

    if (state.editDialog.visible && editDraft != null) {
        ThemeEditDialog(
            draft = editDraft!!,
            isNew = state.editDialog.isNew,
            onDismiss = {
                state.closeEditDialog()
                viewModel.clearEditDraft()
            },
            onSave = {
                viewModel.saveEditedTheme(state.editDialog.editingKey)
                state.closeEditDialog()
            },
            onSelectImage = onSelectImage,
            onUpdateDraft = viewModel::updateDraftConfig,
            onColorClick = onColorClick,
            onBlurClick = onBlurClick,
        )
    }

    pendingDeleteItem?.let { item ->
        AlertDialog(
            modifier = Modifier.eInkGrayscale(),
            onDismissRequest = { pendingDeleteItem = null },
            title = { Text(stringResource(R.string.delete)) },
            text = {
                Text(stringResource(R.string.sure_del_any, item.config.themeName))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteItem(item)
                    pendingDeleteItem = null
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteItem = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ThemeList(
    items: List<ThemeItem>,
    state: io.legado.app.ui.config.widget.ConfigManageState,
    currentConfig: ThemeConfig.Config,
    onApply: (ThemeItem) -> Unit,
    onEdit: (ThemeItem) -> Unit,
    onShare: (ThemeItem) -> Unit,
    onDelete: (ThemeItem) -> Unit,
    onCopy: (ThemeItem) -> Unit,
    onLongClick: (ThemeItem) -> Unit,
    onToggleSelect: (ThemeItem) -> Unit,
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        ConfigList(
            items = items,
            listState = listState,
            itemKey = { it.key },
            itemContent = { item ->
                ThemeCard(
                    item = item,
                    isMultiSelectMode = state.isMultiSelectMode,
                    isSelected = item.key in state.multiSelect.selectedKeys,
                    isCurrent = item.config.themeName == currentConfig.themeName &&
                        item.config.isNightTheme == currentConfig.isNightTheme,
                    onApply = { onApply(item) },
                    onEdit = { onEdit(item) },
                    onShare = { onShare(item) },
                    onDelete = { onDelete(item) },
                    onCopy = { onCopy(item) },
                    onLongClick = { onLongClick(item) },
                    onToggleSelect = { onToggleSelect(item) },
                )
            },
        )
        VerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}
