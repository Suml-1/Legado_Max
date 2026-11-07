package io.legado.app.ui.config.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.legado.app.R
import io.legado.app.lib.theme.eInkGrayscale
import io.legado.app.ui.theme.AppDimens

/**
 * 百分比/整数滑块弹窗（透明度、虚化、圆角缩放等共用）。
 *
 * 两侧的「−」「+」用于逐档微调，对齐旧版 `SeekBar` 弹窗的操作方式。
 *
 * @param title 弹窗标题
 * @param value 初始值，超出 [range] 时自动收敛
 * @param onConfirm 确认回调
 * @param onDismissRequest 取消或点击弹窗外部时的回调
 * @param range 取值范围
 */
@Composable
fun ConfigSliderDialog(
    title: String,
    value: Int,
    onConfirm: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    range: IntRange = 0..100,
    valueText: (Int) -> String = { "$it%" },
) {
    var current by rememberSaveable(value, range) {
        mutableIntStateOf(value.coerceIn(range.first, range.last))
    }
    AlertDialog(
        modifier = Modifier.eInkGrayscale(),
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = valueText(current),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.manageActionSpacing),
                ) {
                    TextButton(
                        onClick = { current = (current - 1).coerceAtLeast(range.first) },
                    ) {
                        Text(stringResource(R.string.reduce))
                    }
                    Slider(
                        value = current.toFloat(),
                        onValueChange = { current = it.toInt() },
                        valueRange = range.first.toFloat()..range.last.toFloat(),
                        steps = (range.last - range.first - 1).coerceAtLeast(0),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { current = (current + 1).coerceAtMost(range.last) },
                    ) {
                        Text(stringResource(R.string.plus))
                    }
                }
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
        },
    )
}
