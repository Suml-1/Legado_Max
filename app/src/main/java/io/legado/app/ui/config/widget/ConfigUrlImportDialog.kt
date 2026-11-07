package io.legado.app.ui.config.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.R
import io.legado.app.lib.theme.eInkGrayscale
import io.legado.app.ui.theme.AppDimens
import io.legado.app.utils.isAbsUrl

/**
 * 在线导入地址输入弹窗（气泡包等按 URL 导入的场景）。
 *
 * 下方列出最近使用过的地址，点一下即填入输入框，避免反复手打。
 *
 * @param title 弹窗标题
 * @param history 历史地址，按最近使用在前
 * @param onConfirm 确认回调，参数为已校验过的绝对地址
 * @param onDismissRequest 取消回调
 */
@Composable
fun ConfigUrlImportDialog(
    title: String,
    history: List<String>,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    var url by rememberSaveable { mutableStateOf(history.firstOrNull().orEmpty()) }
    var showError by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        modifier = Modifier.eInkGrayscale(),
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        showError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = showError,
                    placeholder = { Text(stringResource(R.string.please_input_url)) }
                )
                if (showError) {
                    Text(
                        text = stringResource(R.string.url_format_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (history.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = AppDimens.dialogOptionsMaxHeight)
                            .verticalScroll(rememberScrollState())
                    ) {
                        history.forEach { item ->
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { url = item }
                                    .padding(vertical = AppDimens.manageCardTitleSpacing)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = url.trim()
                if (trimmed.isAbsUrl()) {
                    onConfirm(trimmed)
                } else {
                    showError = true
                }
            }) {
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
