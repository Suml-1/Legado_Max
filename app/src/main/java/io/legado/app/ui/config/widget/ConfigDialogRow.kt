package io.legado.app.ui.config.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.composeActionShape
import io.legado.app.ui.theme.composePanelShape

/**
 * 配置编辑弹窗内的一行「标题 + 当前值（+ 可选颜色块）」。
 *
 * 统一了此前底栏/顶栏/气泡三个编辑弹窗里用代码逐行拼 `LinearLayout` 的写法：
 * 行内边距、字号、文字色都走主题令牌，随「字体缩放」与日夜自动变化。
 *
 * @param title 行标题
 * @param value 当前值的展示文本
 * @param modifier Modifier
 * @param colorPreview 需要展示颜色时传入，`null` 表示不占位
 * @param onClick 点击回调
 */
@Composable
fun ConfigDialogRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    colorPreview: Color? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(composePanelShape())
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(
                horizontal = AppDimens.panelRowHorizontalPadding,
                vertical = AppDimens.panelRowVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (colorPreview != null) {
            Box(
                modifier = Modifier
                    .size(AppDimens.panelRowIconSize)
                    .clip(composeActionShape())
                    .background(colorPreview)
            )
            Spacer(Modifier.width(AppDimens.panelRowTitleSpacing))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
