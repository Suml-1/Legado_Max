package io.legado.app.ui.config.theme.manage.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.ui.config.theme.manage.ThemeItem
import io.legado.app.ui.config.widget.ConfigActionButton
import io.legado.app.ui.config.widget.ConfigBadge

/** 颜色 hex 解析容错，避免非法色值导致崩溃 */
private fun parseColorOrDefault(hex: String, default: Int): Int = runCatching { hex.toColorInt() }.getOrDefault(default)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemeCard(
    item: ThemeItem,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    isCurrent: Boolean = false,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val config = item.config
    val primaryColor = remember(config.primaryColor) {
        parseColorOrDefault(config.primaryColor, 0xFF607D8B.toInt())
    }
    val accentColor = remember(config.accentColor) {
        parseColorOrDefault(config.accentColor, 0xFF8BC34A.toInt())
    }
    val backgroundColor = remember(config.backgroundColor) {
        parseColorOrDefault(config.backgroundColor, 0xFFF5F5F5.toInt())
    }

    val isLightBg = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isLightBg) 0.55f else 0.42f)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (isLightBg) 0.06f else 0.10f)
    val onColor = MaterialTheme.colorScheme.onSurfaceVariant
    val iconTint = onColor.copy(alpha = 0.85f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(0.5.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (isMultiSelectMode) onToggleSelect() },
                    onLongClick = { if (!isMultiSelectMode) onLongClick() },
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemePreview(
                primaryColor = Color(primaryColor),
                accentColor = Color(accentColor),
                backgroundColor = Color(backgroundColor),
                backgroundImgPath = config.backgroundImgPath,
                // 「已应用」统一由标题右侧的胶囊标签表达，预览图不再重复打勾
                isCurrent = false,
                isMultiSelectMode = isMultiSelectMode,
                isNightTheme = config.isNightTheme,
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = config.themeName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = onColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        ConfigBadge(
                            text = stringResource(R.string.applied),
                            highlighted = true,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (isLightBg) 0.15f else 0.12f),
                ) {
                    Text(
                        text = if (config.isNightTheme) stringResource(R.string.night) else stringResource(R.string.day),
                        style = MaterialTheme.typography.bodySmall,
                        color = onColor.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 与其它管理页共用操作按钮：TextButton 的 58dp 最小宽度会让「复制」
                    // 在多选模式（右侧多出勾选框）下被挤成竖排两行
                    ConfigActionButton(
                        text = stringResource(R.string.apply_theme),
                        onClick = onApply,
                        highlighted = isCurrent,
                    )
                    ConfigActionButton(
                        text = stringResource(R.string.edit_theme),
                        onClick = onEdit,
                    )
                    ConfigActionButton(
                        text = stringResource(R.string.copy),
                        onClick = onCopy,
                    )
                }
            }

            if (!isMultiSelectMode) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.share),
                            modifier = Modifier.size(18.dp),
                            tint = iconTint,
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            modifier = Modifier.size(18.dp),
                            tint = iconTint,
                        )
                    }
                }
            } else {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                )
            }
        }
    }
}
