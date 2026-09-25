package io.legado.app.ui.main.my.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.legado.app.ui.main.my.MySettingsAction
import io.legado.app.ui.main.my.MySettingsRowKind
import io.legado.app.ui.main.my.MySettingsSection
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.settings.AppSettingsActionRow
import io.legado.app.ui.widget.components.settings.AppSettingsPanel
import io.legado.app.ui.widget.components.settings.AppSettingsSectionTitle

/**
 * 一个设置分组面板。
 *
 * 三种 [MySettingsRowKind] 共用同一行组件，差异只在「点击做什么、长按做什么、尾部有没有开关」，
 * 因此这里先算出差异再统一渲染，避免为每种类型各写一份几乎相同的行布局。
 *
 * @param section 分组定义
 * @param themeModeLabel 主题模式当前选项名，由 Screen 从字符串数组解析后传入
 * @param webServiceEnabled Web 服务是否运行中
 * @param webServiceSummary Web 服务副标题（运行中为访问地址，否则为说明文案）
 * @param onAction 向上抛的动作回调
 */
@Composable
internal fun MySettingsSectionCard(
    section: MySettingsSection,
    themeModeLabel: String,
    webServiceEnabled: Boolean,
    webServiceSummary: String,
    onAction: (MySettingsAction) -> Unit
) {
    AppSettingsPanel(
        modifier = Modifier.padding(horizontal = AppDimens.panelHorizontalPadding)
    ) {
        section.titleRes?.let { titleRes ->
            AppSettingsSectionTitle(title = stringResource(titleRes))
        }
        section.rows.forEachIndexed { index, row ->
            val isLastRow = index == section.rows.lastIndex
            val isWebService = row.kind == MySettingsRowKind.WebService
            val summary = when (row.kind) {
                MySettingsRowKind.ThemeMode -> themeModeLabel
                MySettingsRowKind.WebService -> webServiceSummary
                MySettingsRowKind.Action -> row.summaryRes?.let { stringResource(it) }
            }
            AppSettingsActionRow(
                title = stringResource(row.titleRes),
                summary = summary,
                showDivider = !isLastRow,
                danger = row.danger,
                leadingIconRes = row.iconRes,
                onClick = {
                    when (row.kind) {
                        MySettingsRowKind.ThemeMode -> onAction(MySettingsAction.OpenThemeModeDialog)
                        // 整行点击与开关等价，保持旧版 SwitchPreference 点整行即切换的行为
                        MySettingsRowKind.WebService -> onAction(MySettingsAction.ToggleWebService)
                        MySettingsRowKind.Action -> onAction(MySettingsAction.OpenRow(row.key))
                    }
                },
                onLongClick = if (isWebService) {
                    { onAction(MySettingsAction.OpenWebServiceActions) }
                } else {
                    null
                },
                trailing = if (isWebService) {
                    {
                        Switch(
                            checked = webServiceEnabled,
                            onCheckedChange = {
                                onAction(MySettingsAction.SetWebServiceEnabled(it))
                            }
                        )
                    }
                } else {
                    null
                }
            )
        }
    }
}
