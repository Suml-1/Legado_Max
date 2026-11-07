package io.legado.app.ui.config.widget

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.lib.theme.eInkGrayscale

/**
 * 配置名称输入弹窗（主题/顶栏/底栏/气泡/应用主题共用）。
 *
 * 为空时确认按钮禁用，替代旧版各页面「点确认再弹一个 `input_is_empty`」的写法。
 *
 * @param title 弹窗标题
 * @param initialName 初始名称，重命名时传原名称
 * @param hint 输入框占位提示
 * @param onConfirm 确认回调，参数为去除首尾空白后的非空名称
 * @param onDismissRequest 取消或点击弹窗外部时的回调
 */
@Composable
fun ConfigNameDialog(
    title: String,
    initialName: String,
    hint: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val trimmed = name.trim()
    AlertDialog(
        // 弹层是独立窗口，页面那层灰阶罩不到，这里再贴一次
        modifier = Modifier.eInkGrayscale(),
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(hint) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty(),
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
        textContentColor = MaterialTheme.colorScheme.onSurface,
    )
}
