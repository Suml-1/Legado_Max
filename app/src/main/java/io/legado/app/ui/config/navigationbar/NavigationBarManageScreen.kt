package io.legado.app.ui.config.navigationbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import io.legado.app.help.config.NavigationBarConfig
import io.legado.app.ui.config.navigationbar.components.NavigationBarEditCallbacks
import io.legado.app.ui.config.navigationbar.components.NavigationBarEditDialog
import io.legado.app.ui.config.widget.ConfigActionButton
import io.legado.app.ui.config.widget.ConfigAddBottomBar
import io.legado.app.ui.config.widget.ConfigItemMoreButton
import io.legado.app.ui.config.widget.ConfigList
import io.legado.app.ui.config.widget.ConfigManageCard
import io.legado.app.ui.config.widget.ConfigManageScaffold
import io.legado.app.ui.config.widget.ConfigManageState
import io.legado.app.ui.config.widget.ConfigMenuItem
import io.legado.app.ui.config.widget.ConfigMultiSelectBar
import io.legado.app.ui.config.widget.DayNightPager
import io.legado.app.ui.config.widget.ImportFromClipboardAction
import io.legado.app.ui.config.widget.MultiSelectAction
import io.legado.app.ui.config.widget.SelectAllAction
import io.legado.app.ui.config.widget.rememberSharedConfigManageState
import io.legado.app.ui.widget.components.VerticalScrollbar
import io.legado.app.ui.widget.components.dialog.AppConfirmDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 底栏管理页的对外回调（避免 Screen 参数超过 5 个）。 */
data class NavigationBarManageActions(
    val onBackClick: () -> Unit = {},
    val onToast: (Int) -> Unit = {},
    val onToastMsg: (String) -> Unit = {},
    val onShareJson: (String) -> Unit = {},
    val onPickColor: (target: Int, current: Int) -> Unit = { _, _ -> },
    val onPickIcon: (itemKey: String, state: String, title: String) -> Unit = { _, _, _ -> },
)

/**
 * 底栏管理页。
 *
 * 日/夜 Tab 与主题列表、顶栏管理共用同一份状态（[rememberSharedConfigManageState]），
 * 条目支持长按多选（置顶/导出/删除），单条「更多」里同样提供置顶。
 */
@Composable
fun NavigationBarManageScreen(
    viewModel: NavigationBarManageViewModel,
    actions: NavigationBarManageActions,
    modifier: Modifier = Modifier,
) {
    val state = rememberSharedConfigManageState()
    val items by viewModel.items.collectAsState()
    val activeId by viewModel.activeId.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingDelete by remember { mutableStateOf<List<NavigationBarConfig>>(emptyList()) }

    val isNight = state.tab.isNight
    LaunchedEffect(isNight) { viewModel.load(isNight) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is NavigationBarEvent.Toast -> actions.onToast(event.resId)
                    is NavigationBarEvent.ToastMsg -> actions.onToastMsg(event.msg)
                    is NavigationBarEvent.ShareJson -> actions.onShareJson(event.json)
                }
            }
        }
    }

    val visibleKeys = remember(items, isNight) {
        items.filter { it.isNight == isNight }.map { it.id }
    }

    ConfigManageScaffold(
        title = stringResource(R.string.navigation_bar_manage),
        isMultiSelectMode = state.isMultiSelectMode,
        onBackClick = actions.onBackClick,
        onExitMultiSelect = state::exitMultiSelect,
        modifier = modifier,
        actions = {
            if (state.isMultiSelectMode) {
                SelectAllAction(
                    isAllSelected = state.isAllSelected(visibleKeys),
                    onSelectAll = { state.selectAllVisible(visibleKeys) },
                )
            } else {
                ImportFromClipboardAction(onImport = viewModel::importFromClipboard)
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
                                viewModel.toTopConfigs(selected)
                                state.exitMultiSelect()
                            },
                        ),
                        MultiSelectAction(
                            icon = Icons.Default.Share,
                            contentDescription = stringResource(R.string.export),
                            onClick = {
                                viewModel.exportConfigs(selected)
                                state.exitMultiSelect()
                            },
                        ),
                        MultiSelectAction(
                            icon = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                            onClick = { pendingDelete = selected },
                        ),
                    ),
                )
            } else {
                ConfigAddBottomBar(
                    text = stringResource(R.string.add_nav_bar_config),
                    onClick = viewModel::startCreate,
                )
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            DayNightPager(
                state = state,
                onTabChange = state::switchTab,
                summaryText = stringResource(R.string.nav_bar_summary),
                scrollEnabled = !state.isMultiSelectMode,
                dayContent = {
                    NavigationBarList(
                        items = items.filter { !it.isNight },
                        state = state,
                        activeId = activeId,
                        emptyText = stringResource(
                            R.string.nav_bar_summary_empty,
                            stringResource(R.string.day_theme),
                        ),
                        onApply = viewModel::applyConfig,
                        onEdit = viewModel::startEdit,
                        onDelete = { pendingDelete = listOf(it) },
                        onToTop = viewModel::toTop,
                        onExport = viewModel::exportConfig,
                        onLongClick = { state.enterMultiSelect(it.id) },
                        onToggleSelect = { state.toggleSelection(it.id) },
                    )
                },
                nightContent = {
                    NavigationBarList(
                        items = items.filter { it.isNight },
                        state = state,
                        activeId = activeId,
                        emptyText = stringResource(
                            R.string.nav_bar_summary_empty,
                            stringResource(R.string.night_theme),
                        ),
                        onApply = viewModel::applyConfig,
                        onEdit = viewModel::startEdit,
                        onDelete = { pendingDelete = listOf(it) },
                        onToTop = viewModel::toTop,
                        onExport = viewModel::exportConfig,
                        onLongClick = { state.enterMultiSelect(it.id) },
                        onToggleSelect = { state.toggleSelection(it.id) },
                    )
                },
            )
        }
    }

    draft?.let { config ->
        NavigationBarEditDialog(
            config = config,
            isEdit = items.any { it.id == config.id },
            defaultBottomColor = viewModel.defaultBottomColor,
            callbacks = NavigationBarEditCallbacks(
                onDraftChange = viewModel::updateDraft,
                onRequestColor = actions.onPickColor,
                onRequestIcon = actions.onPickIcon,
                onSave = viewModel::saveDraft,
                onDismiss = viewModel::clearDraft,
            ),
        )
    }

    if (pendingDelete.isNotEmpty()) {
        AppConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.sure_del),
            confirmText = stringResource(R.string.ok),
            destructive = true,
            onConfirm = {
                viewModel.deleteConfigs(pendingDelete)
                pendingDelete = emptyList()
                state.exitMultiSelect()
            },
            onDismissRequest = { pendingDelete = emptyList() },
        )
    }
}

@Composable
private fun NavigationBarList(
    items: List<NavigationBarConfig>,
    state: ConfigManageState,
    activeId: String?,
    emptyText: String,
    onApply: (NavigationBarConfig) -> Unit,
    onEdit: (NavigationBarConfig) -> Unit,
    onDelete: (NavigationBarConfig) -> Unit,
    onToTop: (NavigationBarConfig) -> Unit,
    onExport: (NavigationBarConfig) -> Unit,
    onLongClick: (NavigationBarConfig) -> Unit,
    onToggleSelect: (NavigationBarConfig) -> Unit,
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        ConfigList(
            items = items,
            listState = listState,
            itemKey = { it.id },
            emptyText = emptyText,
        ) { item ->
            NavigationBarCard(
                item = item,
                isCurrent = item.id == activeId,
                state = state,
                onApply = { onApply(item) },
                onEdit = { onEdit(item) },
                onDelete = { onDelete(item) },
                onToTop = { onToTop(item) },
                onExport = { onExport(item) },
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
private fun NavigationBarCard(
    item: NavigationBarConfig,
    isCurrent: Boolean,
    state: ConfigManageState,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToTop: () -> Unit,
    onExport: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val summary = buildString {
        append(item.getLayoutModeText(context))
        if (item.layoutMode != NavigationBarConfig.LAYOUT_STANDARD) {
            append(" · ")
            append(item.getEffectModeText(context))
        }
        append(" · ")
        append(stringResource(R.string.opacity))
        append(" ")
        append(item.opacity)
        append("%")
        if (item.updatedAt > 0) {
            append(" · ")
            append(dateFormat.format(Date(item.updatedAt)))
        }
    }
    ConfigManageCard(
        title = item.name,
        summary = summary,
        isCurrent = isCurrent,
        showBuiltinBadge = item.isBuiltin,
        isMultiSelectMode = state.isMultiSelectMode,
        isSelected = item.id in state.multiSelect.selectedKeys,
        onLongClick = onLongClick,
        onToggleSelect = onToggleSelect,
        actions = {
            ConfigActionButton(
                text = stringResource(R.string.apply),
                onClick = onApply,
                highlighted = isCurrent,
            )
            if (!item.isBuiltin) {
                ConfigActionButton(
                    text = stringResource(R.string.edit),
                    onClick = onEdit,
                )
            }
            ConfigItemMoreButton(
                items = buildList {
                    if (!item.isBuiltin) {
                        add(ConfigMenuItem(stringResource(R.string.to_top), onToTop))
                    }
                    add(ConfigMenuItem(stringResource(R.string.apply), onApply))
                    if (!item.isBuiltin) {
                        add(ConfigMenuItem(stringResource(R.string.edit), onEdit))
                    }
                    add(ConfigMenuItem(stringResource(R.string.export_str), onExport))
                    if (!item.isBuiltin && !isCurrent) {
                        add(ConfigMenuItem(stringResource(R.string.delete), onDelete))
                    }
                },
            )
        },
    )
}
