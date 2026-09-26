package io.legado.app.ui.main.explore.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.composeActionShape

/**
 * 发现页分类胶囊（url / button / toggle 三类共用的外观）。
 *
 * 观感对齐原 `item_fillet_text.xml`：圆角胶囊 + 次要容器色填充 + 14sp 主文本，
 * 圆角走 [composeActionShape]（与 XML 侧 `ui_action_radius` 同源）。
 */
@Composable
internal fun ExploreKindChip(
    modifier: Modifier = Modifier,
    text: String,
    horizontalAlignment: Alignment.Horizontal,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(composeActionShape())
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(
                horizontal = AppDimens.exploreKindHorizontalPadding,
                vertical = AppDimens.exploreKindVerticalPadding,
            ),
        contentAlignment = horizontalAlignment.toBoxAlignment(),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = horizontalAlignment.toTextAlign(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * url / button 分类项：展示文案可能是静态标题，也可能要跑 `viewName` 脚本求值。
 *
 * @param onClick 点击行为（url 打开发现页、button 执行 `action` 脚本）由调用方决定
 */
@Composable
internal fun ExploreKindActionChip(
    modifier: Modifier = Modifier,
    kind: ExploreKind,
    sourceUrl: String,
    controller: ExploreKindsController,
    onClick: () -> Unit,
) {
    val name by rememberKindName(sourceUrl, kind, controller)
    ExploreKindChip(
        modifier = modifier,
        text = name,
        horizontalAlignment = kind.style().horizontalAlignment(Alignment.CenterHorizontally),
        onClick = onClick,
    )
}

/**
 * toggle 分类项：点击在 `chars` 里循环切换，当前项存在 infoMap 上（`title` 为键），
 * 切换后执行 `action` 脚本。`layout_justifySelf` 为 "right" 时标记符放在文案后面。
 */
@Composable
internal fun ExploreKindToggleChip(
    modifier: Modifier = Modifier,
    kind: ExploreKind,
    sourceUrl: String,
    controller: ExploreKindsController,
) {
    val style = kind.style()
    val name by rememberKindName(sourceUrl, kind, controller)
    val chars = remember(kind) { kind.charsOrDefault() }
    val infoMap = remember(sourceUrl, controller) { controller.infoMap(sourceUrl) }
    // 当前标记符以 infoMap 为准：页面切走再回来要停在用户上次的选择上
    var char by remember(kind, infoMap) {
        mutableStateOf(
            infoMap[kind.title].takeUnless { it.isNullOrEmpty() } ?: (kind.default ?: chars[0])
        )
    }
    LaunchedEffect(kind) {
        infoMap[kind.title] = char
    }
    val prefix = style.layout_justifySelf != "right"
    ExploreKindChip(
        modifier = modifier,
        text = if (prefix) char + name else name + char,
        horizontalAlignment = style.horizontalAlignment(Alignment.CenterHorizontally),
        onClick = {
            val nextIndex = (chars.indexOf(char) + 1).mod(chars.size)
            char = chars.getOrNull(nextIndex).orEmpty()
            infoMap[kind.title] = char
            kind.action?.takeIf { it.isNotBlank() }?.let {
                controller.evalAction(sourceUrl, it, kind.title)
            }
        },
    )
}


