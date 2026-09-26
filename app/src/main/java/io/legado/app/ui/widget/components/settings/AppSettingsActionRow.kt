package io.legado.app.ui.widget.components.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.composePanelShape
import io.legado.app.ui.theme.pageMutedIconTint
import io.legado.app.ui.theme.pageSecondaryTextColor

/**
 * 设置面板中的一行：可选图标 + 标题 + 可选副标题 + 可选尾部控件。
 *
 * 整行是一个点击热区（最小高度 [AppDimens.panelRowMinHeight]，不低于无障碍要求的 48dp），
 * 尾部控件自带点击时（如开关）由尾部控件消费自己的点击，不会误触整行回调。
 *
 * @param title 主文案
 * @param summary 副标题，传 `null` 或空串时该行收缩为单行高度
 * @param showDivider 是否绘制行底分隔线；分组最后一行传 `false`
 * @param danger 危险操作行（如退出、删除），标题与图标转为错误色
 * @param leadingIconRes 行首图标资源，传 `null` 时不占位
 * @param onClick 整行点击回调
 * @param onLongClick 整行长按回调，传 `null` 表示不支持长按
 * @param trailing 尾部插槽，如开关、状态文本
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppSettingsActionRow(
    modifier: Modifier = Modifier,
    title: String,
    summary: String? = null,
    showDivider: Boolean = true,
    danger: Boolean = false,
    @DrawableRes leadingIconRes: Int? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val shape = composePanelShape()
    val interactionSource = remember { MutableInteractionSource() }
    val titleColor = if (danger) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.panelRowMinHeight)
            .semantics(mergeDescendants = true) {}
            .appSettingsRowDecoration(
                shape = shape,
                dividerColor = MaterialTheme.colorScheme.outlineVariant,
                showDivider = showDivider
            )
            .combinedClickable(
                interactionSource = interactionSource,
                // 必须走 indication 层而不是自己读按下态再画底色：后者要经一次重组才出现，
                // 快速点击时按下/抬起在同一帧内结束，肉眼看不到任何反馈
                indication = ripple(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                ),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(
                horizontal = AppDimens.panelRowHorizontalPadding,
                vertical = AppDimens.panelRowVerticalPadding
            )
    ) {
        if (leadingIconRes != null) {
            Image(
                painter = painterResource(leadingIconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    if (danger) MaterialTheme.colorScheme.error else pageMutedIconTint()
                ),
                modifier = Modifier
                    .size(AppDimens.panelRowIconSize)
                    .align(Alignment.CenterVertically)
            )
            Spacer(modifier = Modifier.width(AppDimens.panelRowIconSpacing))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!summary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(AppDimens.panelRowTitleSpacing))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = pageSecondaryTextColor(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailing != null) {
            Spacer(modifier = Modifier.width(AppDimens.panelRowTrailingSpacing))
            trailing()
        }
    }
}
