package io.legado.app.ui.config.widget

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.composePanelShape

/**
 * 配置管理页统一卡片（主题列表/应用主题/底栏/顶栏/气泡共用）。
 *
 * 统一了此前四套各写一遍的条目结构：前置预览位 + 名称 + 标签 + 摘要 + 快捷操作行，
 * 以及「已应用」标记的样式（胶囊标签，替代旧版摘要前缀「当前应用 · 」与预览角标两种写法）。
 * 多选模式下整卡点击切换选中，非多选模式下长按进入多选。
 *
 * 根容器不做 `mergeDescendants`：卡片内含多个可点按钮，合并会把按钮的无障碍节点吞掉，
 * 因此只在标题+摘要这段纯展示内容上合并。
 *
 * @param title 配置名称
 * @param summary 摘要文本，最多两行
 * @param isMultiSelectMode 是否处于多选模式
 * @param isSelected 多选模式下是否已选中
 * @param modifier Modifier
 * @param isCurrent 是否为当前已应用的配置
 * @param showBuiltinBadge 是否显示「内置」标签
 * @param leading 前置插槽，通常为预览图或图标
 * @param trailing 尾部插槽，通常为分享/删除图标按钮
 * @param onLongClick 长按回调；传 `null` 表示不支持进入多选
 * @param onToggleSelect 多选模式下点击切换选中
 * @param actions 快捷操作按钮行
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConfigManageCard(
    title: String,
    summary: String,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    showBuiltinBadge: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onToggleSelect: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val isLightBg = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isLightBg) 0.55f else 0.42f)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (isLightBg) 0.06f else 0.10f)
    val shape = composePanelShape()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(AppDimens.manageCardBorderWidth, borderColor, shape),
        shape = shape,
        color = cardColor,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (isMultiSelectMode) onToggleSelect() },
                    onLongClick = { if (!isMultiSelectMode) onLongClick?.invoke() }
                )
                .padding(AppDimens.manageCardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(AppDimens.panelSpacing))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {}
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (showBuiltinBadge) {
                        Spacer(Modifier.width(AppDimens.panelRowTitleSpacing))
                        ConfigBadge(text = stringResource(R.string.builtin))
                    }
                    if (isCurrent) {
                        Spacer(Modifier.width(AppDimens.panelRowTitleSpacing))
                        ConfigBadge(
                            text = stringResource(R.string.applied),
                            highlighted = true
                        )
                    }
                }
                if (summary.isNotBlank()) {
                    Spacer(Modifier.height(AppDimens.manageCardTitleSpacing))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(AppDimens.panelRowTitleSpacing))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.manageActionSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
            if (trailing != null) {
                trailing()
            }
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() }
                )
            }
        }
    }
}

/**
 * 卡片上的胶囊标签（内置 / 已应用）。
 *
 * @param text 标签文案，必须已资源化
 * @param highlighted 是否用主色强调（「已应用」用）
 */
@Composable
fun ConfigBadge(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    val color = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val background = if (highlighted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    }
    Box(
        modifier = modifier
            .clip(composePanelShape())
            .background(background)
            .padding(
                horizontal = AppDimens.manageBadgePaddingHorizontal,
                vertical = AppDimens.manageBadgePaddingVertical
            )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1
        )
    }
}
