package io.legado.app.ui.widget.components.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.R
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.composeActionRadius

/**
 * 单选列表弹窗，用于「主题模式」这类从固定枚举里挑一项的设置。
 *
 * 选中即回调并关闭：调用点负责落库与关闭弹窗，本组件不持有选中态，
 * 避免弹窗内部状态与设置项真实状态不一致。
 *
 * @param title 弹窗标题
 * @param options 选项文案，顺序与业务枚举一致
 * @param selectedIndex 当前选中项下标；越界时视作无选中项
 * @param onSelect 选中项回调，参数为下标
 * @param onDismissRequest 取消或点击弹窗外部时的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRadioChoiceDialog(
    modifier: Modifier = Modifier,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(
                        start = AppDimens.panelRowHorizontalPadding,
                        top = AppDimens.panelRowHorizontalPadding,
                        end = AppDimens.panelRowHorizontalPadding,
                        bottom = AppDimens.panelTitleBottomPadding
                    )
                )
                Column(
                    modifier = Modifier
                        // 选项过多时弹窗会顶穿屏幕，这里限高后滚动，保证取消按钮始终可见
                        .heightIn(max = AppDimens.dialogOptionsMaxHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    options.forEachIndexed { index, label ->
                        DialogOptionRow(
                            label = label,
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) }
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            end = AppDimens.panelRowTitleSpacing,
                            bottom = AppDimens.panelRowTitleSpacing
                        )
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        shape = RoundedCornerShape(composeActionRadius())
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = AppDimens.panelRowTitleSpacing,
                end = AppDimens.panelRowHorizontalPadding
            )
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(AppDimens.panelRowTitleSpacing))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
