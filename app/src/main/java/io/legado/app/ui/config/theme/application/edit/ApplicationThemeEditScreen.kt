package io.legado.app.ui.config.theme.application.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.legado.app.R
import io.legado.app.ui.config.widget.ConfigDialogRow
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.AppPageTopBar
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.VerticalScrollbar
import io.legado.app.ui.widget.components.dialog.AppConfirmDialog
import io.legado.app.ui.widget.components.dialog.AppRadioChoiceDialog

/**
 * 应用主题编辑页。
 *
 * 名称 + 八个组件选择（日/夜 × 主题/顶栏/底栏/封面），每一项都可以选「未设置」，
 * 表示应用该主题时不动这一项。
 *
 * @param viewModel 数据层
 * @param onBackClick 返回
 * @param onToast 提示
 * @param onFinished 保存或删除完成（Activity 据此返回列表页）
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ApplicationThemeEditScreen(
    viewModel: ApplicationThemeEditViewModel,
    onBackClick: () -> Unit,
    onToast: (Int) -> Unit,
    onFinished: () -> Unit,
) {
    val draft by viewModel.draft.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var picker by remember { mutableStateOf(ThemeEditPicker.None) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is ApplicationThemeEditEvent.Toast -> onToast(event.resId)
                    ApplicationThemeEditEvent.Saved, ApplicationThemeEditEvent.Deleted -> onFinished()
                }
            }
        }
    }

    val config = draft ?: return
    val notSet = stringResource(R.string.application_theme_not_set)

    AppScaffold(
        topBar = {
            AppPageTopBar(
                title = stringResource(
                    if (viewModel.isNew) R.string.application_theme_create else R.string.application_theme_edit,
                ),
                onBackClick = onBackClick,
                actions = {
                    TextButton(onClick = viewModel::save) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = AppDimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.manageRowSpacing),
            ) {
                OutlinedTextField(
                    value = config.name,
                    onValueChange = { value -> viewModel.updateDraft { it.copy(name = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.application_theme_name)) },
                )

                ConfigDialogRow(
                    title = componentTitle(false, R.string.application_theme_component_theme),
                    value = config.dayTheme?.themeName ?: notSet,
                    onClick = { picker = ThemeEditPicker.DayTheme },
                )
                ConfigDialogRow(
                    title = componentTitle(true, R.string.application_theme_component_theme),
                    value = config.nightTheme?.themeName ?: notSet,
                    onClick = { picker = ThemeEditPicker.NightTheme },
                )
                ConfigDialogRow(
                    title = componentTitle(false, R.string.application_theme_component_top_bar),
                    value = candidates.dayTopBars
                        .firstOrNull { it.dirName == config.dayTopBarDir }?.config?.name ?: notSet,
                    onClick = { picker = ThemeEditPicker.DayTopBar },
                )
                ConfigDialogRow(
                    title = componentTitle(true, R.string.application_theme_component_top_bar),
                    value = candidates.nightTopBars
                        .firstOrNull { it.dirName == config.nightTopBarDir }?.config?.name ?: notSet,
                    onClick = { picker = ThemeEditPicker.NightTopBar },
                )
                ConfigDialogRow(
                    title = componentTitle(false, R.string.application_theme_component_bottom_bar),
                    value = candidates.dayBottomBars
                        .firstOrNull { it.id == config.dayBottomBarId }?.name ?: notSet,
                    onClick = { picker = ThemeEditPicker.DayBottomBar },
                )
                ConfigDialogRow(
                    title = componentTitle(true, R.string.application_theme_component_bottom_bar),
                    value = candidates.nightBottomBars
                        .firstOrNull { it.id == config.nightBottomBarId }?.name ?: notSet,
                    onClick = { picker = ThemeEditPicker.NightBottomBar },
                )
                ConfigDialogRow(
                    title = componentTitle(false, R.string.application_theme_component_cover),
                    value = candidates.covers
                        .firstOrNull { it.first == config.dayCoverGroupId }?.second ?: notSet,
                    onClick = { picker = ThemeEditPicker.DayCover },
                )
                ConfigDialogRow(
                    title = componentTitle(true, R.string.application_theme_component_cover),
                    value = candidates.covers
                        .firstOrNull { it.first == config.nightCoverGroupId }?.second ?: notSet,
                    onClick = { picker = ThemeEditPicker.NightCover },
                )

                if (!viewModel.isNew) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            VerticalScrollbar(
                state = scrollState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }

    when (picker) {
        ThemeEditPicker.None -> Unit

        ThemeEditPicker.DayTheme -> AppRadioChoiceDialog(
            title = componentTitle(false, R.string.application_theme_component_theme),
            options = listOf(notSet) + candidates.dayThemes.map { it.themeName },
            selectedIndex = candidates.dayThemes.indexOfFirst {
                it.themeName == config.dayTheme?.themeName
            }.let { if (it >= 0) it + 1 else 0 },
            onSelect = { index ->
                val theme = candidates.dayThemes.getOrNull(index - 1)?.copy()
                viewModel.updateDraft { it.copy(dayTheme = theme) }
                picker = ThemeEditPicker.None
            },
            onDismissRequest = { picker = ThemeEditPicker.None },
        )

        ThemeEditPicker.NightTheme -> AppRadioChoiceDialog(
            title = componentTitle(true, R.string.application_theme_component_theme),
            options = listOf(notSet) + candidates.nightThemes.map { it.themeName },
            selectedIndex = candidates.nightThemes.indexOfFirst {
                it.themeName == config.nightTheme?.themeName
            }.let { if (it >= 0) it + 1 else 0 },
            onSelect = { index ->
                val theme = candidates.nightThemes.getOrNull(index - 1)?.copy()
                viewModel.updateDraft { it.copy(nightTheme = theme) }
                picker = ThemeEditPicker.None
            },
            onDismissRequest = { picker = ThemeEditPicker.None },
        )

        ThemeEditPicker.DayTopBar -> AppRadioChoiceDialog(
            title = componentTitle(false, R.string.application_theme_component_top_bar),
            options = listOf(notSet) + candidates.dayTopBars.map { it.config.name },
            selectedIndex = candidates.dayTopBars.indexOfFirst {
                it.dirName == config.dayTopBarDir
            }.let { if (it >= 0) it + 1 else 0 },
            onSelect = { index ->
                val dir = candidates.dayTopBars.getOrNull(index - 1)?.dirName.orEmpty()
                viewModel.updateDraft { it.copy(dayTopBarDir = dir) }
                picker = ThemeEditPicker.None
            },
            onDismissRequest = { picker = ThemeEditPicker.None },
        )

        ThemeEditPicker.NightTopBar -> AppRadioChoiceDialog(
            title = componentTitle(true, R.string.application_theme_component_top_bar),
            options = listOf(notSet) + candidates.nightTopBars.map { it.config.name },
            selectedIndex = candidates.nightTopBars.indexOfFirst {
                it.dirName == config.nightTopBarDir
            }.let { if (it >= 0) it + 1 else 0 },
            onSelect = { index ->
                val dir = candidates.nightTopBars.getOrNull(index - 1)?.dirName.orEmpty()
                viewModel.updateDraft { it.copy(nightTopBarDir = dir) }
                picker = ThemeEditPicker.None
            },
            onDismissRequest = { picker = ThemeEditPicker.None },
        )

        ThemeEditPicker.DayBottomBar -> AppRadioChoiceDialog(
            title = componentTitle(false, R.string.application_theme_component_bottom_bar),
            options = listOf(notSet) + candidates.dayBottomBars.map { it.name },
            selectedIndex = candidates.dayBottomBars.indexOfFirst {
                it.id == config.dayBottomBarId
            }.let { if (it >= 0) it + 1 else 0 },
            onSelect = { index ->
                val id = candidates.dayBottomBars.getOrNull(index - 1)?.id
                viewModel.updateDraft { it.copy(dayBottomBarId = id) }
                picker = ThemeEditPicker.None
            },
            onDismissRequest = { picker = ThemeEditPicker.None },
        )

        ThemeEditPicker.NightBottomBar -> AppRadioChoiceDialog(
            title = componentTitle(true, R.string.application_theme_component_bottom_bar),
            options = listOf(notSet) + candidates.nightBottomBars.map { it.name },
            selectedIndex = candidates.nightBottomBars.indexOfFirst {
                it.id == config.nightBottomBarId
            }.let { if (it >= 0) it + 1 else 0 },
            onSelect = { index ->
                val id = candidates.nightBottomBars.getOrNull(index - 1)?.id
                viewModel.updateDraft { it.copy(nightBottomBarId = id) }
                picker = ThemeEditPicker.None
            },
            onDismissRequest = { picker = ThemeEditPicker.None },
        )

        ThemeEditPicker.DayCover -> AppRadioChoiceDialog(
            title = componentTitle(false, R.string.application_theme_component_cover),
            options = listOf(notSet) + candidates.covers.map { it.second },
            selectedIndex = candidates.covers.indexOfFirst {
                it.first == config.dayCoverGroupId
            }.let { if (it >= 0) it + 1 else 0 },
            onSelect = { index ->
                val id = candidates.covers.getOrNull(index - 1)?.first
                viewModel.updateDraft { it.copy(dayCoverGroupId = id) }
                picker = ThemeEditPicker.None
            },
            onDismissRequest = { picker = ThemeEditPicker.None },
        )

        ThemeEditPicker.NightCover -> AppRadioChoiceDialog(
            title = componentTitle(true, R.string.application_theme_component_cover),
            options = listOf(notSet) + candidates.covers.map { it.second },
            selectedIndex = candidates.covers.indexOfFirst {
                it.first == config.nightCoverGroupId
            }.let { if (it >= 0) it + 1 else 0 },
            onSelect = { index ->
                val id = candidates.covers.getOrNull(index - 1)?.first
                viewModel.updateDraft { it.copy(nightCoverGroupId = id) }
                picker = ThemeEditPicker.None
            },
            onDismissRequest = { picker = ThemeEditPicker.None },
        )
    }

    if (showDeleteConfirm) {
        AppConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.sure_del),
            confirmText = stringResource(R.string.ok),
            destructive = true,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete()
            },
            onDismissRequest = { showDeleteConfirm = false },
        )
    }
}

/**
 * 行标题：复用「日间/夜间」与组件名两个既有资源拼成「日间 · 主题」，
 * 这样新增的编辑页不需要为此再造 8 条只出现一次的字符串。
 */
@Composable
private fun componentTitle(isNight: Boolean, componentRes: Int): String {
    val prefix = stringResource(if (isNight) R.string.night else R.string.day)
    return "$prefix · ${stringResource(componentRes)}"
}

private enum class ThemeEditPicker {
    None,
    DayTheme,
    NightTheme,
    DayTopBar,
    NightTopBar,
    DayBottomBar,
    NightBottomBar,
    DayCover,
    NightCover,
}
