package io.legado.app.ui.config.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R

/**
 * 顶栏多选操作项（文字）。
 *
 * 沿用旧版管理页的做法：进入多选后，批量操作以**文字**形式排在顶栏右侧
 * （旧版是 AppCompat 菜单项 `showAsAction = always` 且不设图标，最终渲染成文字），
 * 而不是底栏的图标按钮。
 *
 * 文字颜色取 [LocalContentColor]，与同顶栏的返回/标题/图标共用统一前景色，
 * 不单独取主题色，避免在自定义顶栏（壁纸/浅底）上出现压色。
 *
 * @param text 操作文案，必须已资源化
 * @param modifier Modifier
 * @param onClick 点击回调
 */
@Composable
fun ConfigMultiSelectTextAction(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // 顶栏要并排放下 4 个文字操作。TextButton 内部有 58dp 最小宽度（且盖在外部 Modifier 之上，
    // 外部 widthIn 改不动），4 个加起来会把「已选择 N 项」挤成「已…」；旧版 AppCompat 菜单项
    // 没有这个最小宽度，所以这里直接用可点 Text 还原它的占位。
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = LocalContentColor.current,
        maxLines = 1,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
    )
}

/**
 * 从剪贴板导入操作图标按钮。
 *
 * @param onImport 点击回调
 */
@Composable
fun ImportFromClipboardAction(
    onImport: () -> Unit,
) {
    IconButton(onClick = onImport) {
        Icon(
            imageVector = Icons.Default.ContentPaste,
            contentDescription = stringResource(R.string.import_from_clipboard),
            tint = LocalContentColor.current,
        )
    }
}
