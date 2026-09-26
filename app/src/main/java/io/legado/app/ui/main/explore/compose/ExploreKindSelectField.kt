package io.legado.app.ui.main.explore.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.composeActionShape

/**
 * 下拉选择类分类项（`type: select`）。
 *
 * 外观对齐原 `item_fillet_selector_single.xml`：强调色的名称 + 当前选中项（原实现是 Spinner）。
 * 选中值写在 infoMap 的 `title` 键上，切换后执行 `action` 脚本。
 */
@Composable
internal fun ExploreKindSelectField(
    modifier: Modifier = Modifier,
    kind: ExploreKind,
    sourceUrl: String,
    controller: ExploreKindsController,
) {
    val name by rememberKindName(sourceUrl, kind, controller)
    val chars = remember(kind) { kind.charsOrDefault() }
    val infoMap = remember(sourceUrl, controller) { controller.infoMap(sourceUrl) }
    var selected by remember(kind, infoMap) {
        mutableStateOf(
            infoMap[kind.title].takeUnless { it.isNullOrEmpty() } ?: (kind.default ?: chars[0])
        )
    }
    var expanded by remember(kind) { mutableStateOf(false) }
    LaunchedEffect(kind) {
        infoMap[kind.title] = selected
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(composeActionShape())
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { expanded = true }
                .padding(
                    horizontal = AppDimens.exploreKindHorizontalPadding,
                    vertical = AppDimens.exploreKindVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = selected,
                modifier = Modifier.padding(start = AppDimens.exploreKindSpacing),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 装饰图标：点击目标是有 semantics 的整行，图标本身不需要单独描述
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(AppDimens.exploreTitleIconSize),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            chars.forEach { char ->
                DropdownMenuItem(
                    text = { Text(text = char, style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        expanded = false
                        if (char == selected) return@DropdownMenuItem
                        selected = char
                        infoMap[kind.title] = char
                        kind.action?.takeIf { it.isNotBlank() }?.let {
                            controller.evalAction(sourceUrl, it, kind.title)
                        }
                    },
                )
            }
        }
    }
}
