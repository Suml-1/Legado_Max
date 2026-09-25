package io.legado.app.ui.main.my

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.main.my.components.MySettingsSectionCard
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.dialog.AppRadioChoiceDialog

/**
 * 「我的」主屏幕。
 *
 * 只做三件事：把状态翻译成展示文案、把分组交给 [MySettingsSectionCard] 渲染、按状态条件渲染弹窗。
 * 跳转、启停服务、读写剪贴板等平台操作一律通过 [onAction] 抛给宿主 Fragment，
 * 本文件不引用任何平台 API（state-events.md §4.1）。
 *
 * 背景保持透明：主界面壁纸由 `MainActivity` 的 `content_container` 统一承载，
 * 这里铺不透明底色会把壁纸盖掉，与书架、首页等其它 Tab 的观感不一致。
 *
 * @param state 页面状态
 * @param bottomPaddingPx 底部导航栏占用的高度（px），由宿主 Fragment 下发，避免最后一项被底栏遮挡
 * @param onAction 用户动作回调
 */
@Composable
internal fun MySettingsScreen(
    modifier: Modifier = Modifier,
    state: MySettingsUiState,
    bottomPaddingPx: Int,
    onAction: (MySettingsAction) -> Unit
) {
    val sections = remember(state.debugToolsVisible) {
        MySettingsCatalog.visibleSections(state.debugToolsVisible)
    }
    val themeModeLabels = stringArrayResource(R.array.theme_mode).toList()
    val webServiceSummary = state.webServiceAddress
        ?: stringResource(R.string.web_service_desc)
    val bottomPadding = with(LocalDensity.current) { bottomPaddingPx.toDp() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = AppDimens.panelTopPadding,
            bottom = bottomPadding + AppDimens.panelSpacing
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.panelSpacing)
    ) {
        items(
            items = sections,
            // 分组标题可能为空，用分组首行的 key 作为标识，保证唯一且跨重组稳定
            key = { section -> section.rows.first().key }
        ) { section ->
            MySettingsSectionCard(
                section = section,
                themeModeLabel = themeModeLabels.getOrNull(state.themeModeIndex).orEmpty(),
                webServiceEnabled = state.webServiceEnabled,
                webServiceSummary = webServiceSummary,
                onAction = onAction
            )
        }
    }

    when (state.dialog) {
        MySettingsDialog.ThemeMode -> AppRadioChoiceDialog(
            title = stringResource(R.string.theme_mode),
            options = themeModeLabels,
            selectedIndex = state.themeModeIndex,
            onSelect = { onAction(MySettingsAction.SetThemeMode(it)) },
            onDismissRequest = { onAction(MySettingsAction.DismissDialog) }
        )

        MySettingsDialog.WebServiceActions -> AppRadioChoiceDialog(
            title = stringResource(R.string.web_service),
            options = listOf(
                stringResource(R.string.copy_url),
                stringResource(R.string.open_in_browser)
            ),
            selectedIndex = -1,
            onSelect = { index ->
                when (index) {
                    0 -> onAction(MySettingsAction.CopyWebServiceAddress)
                    else -> onAction(MySettingsAction.OpenWebServiceAddressInBrowser)
                }
            },
            onDismissRequest = { onAction(MySettingsAction.DismissDialog) }
        )

        null -> Unit
    }
}
