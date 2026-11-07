package io.legado.app.ui.config.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.lib.theme.eInkGrayscale
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.composePanelShape

/**
 * 「更多」菜单中的一项。
 *
 * @param text 菜单文案，必须已资源化
 * @param onClick 点击回调（菜单项点击后自动收起菜单）
 */
data class ConfigMenuItem(
    val text: String,
    val onClick: () -> Unit,
)

/**
 * 卡片上的操作文字按钮（应用 / 编辑 / 复制 …）。
 *
 * 统一了四套管理页里各自设置的 13sp 硬编码字号，改走 `labelLarge` 语义档位，
 * 从而跟随用户在「字体缩放」里的设置。
 *
 * 用可点文字而不是 `TextButton`：TextButton 内部有 58dp 最小宽度（盖在外部 Modifier 之上），
 * 一行放三个就把「复制」挤成竖排两行；这里按尺寸令牌给 48dp 最小宽度 + 10dp 内边距，
 * 并禁止换行，卡片进入多选（右侧多出勾选框）时也不会被挤断。
 *
 * @param text 按钮文案，必须已资源化
 * @param onClick 点击回调
 * @param modifier Modifier
 * @param highlighted 是否用主色强调（如「应用」）
 * @param enabled 是否可点
 */
@Composable
fun ConfigActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .heightIn(min = AppDimens.manageActionHeight)
            .widthIn(min = AppDimens.manageActionMinWidth)
            .clip(composePanelShape())
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = AppDimens.manageActionPaddingHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                highlighted -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * 页面顶部栏的图标按钮 + 下拉菜单（如「导入」的多来源选择）。
 *
 * @param icon 图标
 * @param contentDescription 无障碍描述，必须已资源化
 * @param items 菜单项，顺序即展示顺序
 * @param modifier Modifier
 */
@Composable
fun ConfigActionIconMenu(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    items: List<ConfigMenuItem>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, modifier = modifier) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.eInkGrayscale(),
    ) {
        items.forEach { item ->
            DropdownMenuItem(
                text = { Text(item.text) },
                onClick = {
                    expanded = false
                    item.onClick()
                },
            )
        }
    }
}

/**
 * 卡片上的「更多」按钮 + 下拉菜单。
 *
 * 各管理页把本页支持的操作按 [ConfigMenuItem] 传入即可，菜单文案与顺序由调用方决定
 * （如底栏/顶栏/气泡都含「置顶」，应用主题额外含「重命名/更新当前」）。
 *
 * @param items 菜单项，顺序即展示顺序
 * @param modifier Modifier
 */
@Composable
fun RowScope.ConfigItemMoreButton(
    items: List<ConfigMenuItem>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(
        onClick = { expanded = true },
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.more),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        // 下拉菜单是独立窗口，页面那层灰阶罩不到，这里再贴一次
        modifier = Modifier.eInkGrayscale(),
    ) {
        items.forEach { item ->
            DropdownMenuItem(
                text = { Text(item.text) },
                onClick = {
                    expanded = false
                    item.onClick()
                },
            )
        }
    }
}
