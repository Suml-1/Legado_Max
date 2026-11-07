package io.legado.app.ui.config.theme.application.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import io.legado.app.R
import io.legado.app.lib.theme.eInkGrayscale
import io.legado.app.ui.theme.AppDimens

/**
 * 应用主题的「组件勾选」集合。
 *
 * 导入与删除共用这一份结构：导入默认全选，删除默认全不选，
 * 由调用方转换成 [io.legado.app.help.config.ApplicationThemeManager] 的对应选项。
 */
data class ThemeComponentSelection(
    val dayTheme: Boolean = true,
    val nightTheme: Boolean = true,
    val dayTopBar: Boolean = true,
    val nightTopBar: Boolean = true,
    val dayBottomBar: Boolean = true,
    val nightBottomBar: Boolean = true,
    val dayCover: Boolean = true,
    val nightCover: Boolean = true
)

/**
 * 应用主题导入/删除的组件勾选弹窗。
 *
 * 四行（主题/顶栏/底栏/封面），每行独立勾选「日间」「夜间」，
 * 替代旧版用代码拼 `LinearLayout + CheckBox` 的导入、删除两个重复对话框。
 *
 * @param title 弹窗标题
 * @param selection 初始勾选状态
 * @param onConfirm 确认回调
 * @param onDismissRequest 取消回调
 */
@Composable
fun ThemeComponentOptionsDialog(
    title: String,
    selection: ThemeComponentSelection,
    onConfirm: (ThemeComponentSelection) -> Unit,
    onDismissRequest: () -> Unit
) {
    var current by remember(selection) { mutableStateOf(selection) }
    AlertDialog(
        modifier = Modifier.eInkGrayscale(),
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.manageRowSpacing)) {
                ComponentRow(
                    label = stringResource(R.string.application_theme_component_theme),
                    dayChecked = current.dayTheme,
                    nightChecked = current.nightTheme,
                    onDayChange = { current = current.copy(dayTheme = it) },
                    onNightChange = { current = current.copy(nightTheme = it) }
                )
                ComponentRow(
                    label = stringResource(R.string.application_theme_component_top_bar),
                    dayChecked = current.dayTopBar,
                    nightChecked = current.nightTopBar,
                    onDayChange = { current = current.copy(dayTopBar = it) },
                    onNightChange = { current = current.copy(nightTopBar = it) }
                )
                ComponentRow(
                    label = stringResource(R.string.application_theme_component_bottom_bar),
                    dayChecked = current.dayBottomBar,
                    nightChecked = current.nightBottomBar,
                    onDayChange = { current = current.copy(dayBottomBar = it) },
                    onNightChange = { current = current.copy(nightBottomBar = it) }
                )
                ComponentRow(
                    label = stringResource(R.string.application_theme_component_cover),
                    dayChecked = current.dayCover,
                    nightChecked = current.nightCover,
                    onDayChange = { current = current.copy(dayCover = it) },
                    onNightChange = { current = current.copy(nightCover = it) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ComponentRow(
    label: String,
    dayChecked: Boolean,
    nightChecked: Boolean,
    onDayChange: (Boolean) -> Unit,
    onNightChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = AppDimens.panelRowVerticalPadding)
        ) {
            Checkbox(checked = dayChecked, onCheckedChange = onDayChange)
            Text(
                text = stringResource(R.string.day),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(end = AppDimens.panelRowVerticalPadding)
            )
            Checkbox(checked = nightChecked, onCheckedChange = onNightChange)
            Text(
                text = stringResource(R.string.night),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
