package io.legado.app.ui.main.explore.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.composeActionShape
import io.legado.app.ui.theme.pageSecondaryTextColor
import kotlinx.coroutines.delay

/**
 * 文本输入类分类项（`type: text`）。
 *
 * 输入值写在 infoMap 的 `title` 键上（书源规则读同一个 infoMap），并带防抖地执行 `action`：
 * 原实现是 600ms 防抖，这里用 `LaunchedEffect(value)` 的"重启即取消"语义表达同一件事。
 */
@Composable
internal fun ExploreKindTextField(
    modifier: Modifier = Modifier,
    kind: ExploreKind,
    sourceUrl: String,
    controller: ExploreKindsController,
) {
    val style = kind.style()
    val alignment = style.horizontalAlignment(Alignment.Start)
    val hint by rememberKindName(sourceUrl, kind, controller)
    val infoMap = remember(sourceUrl, controller) { controller.infoMap(sourceUrl) }
    var text by remember(kind, infoMap) { mutableStateOf(infoMap[kind.title].orEmpty()) }
    // 首次进入组合只是把已存的值写回 infoMap，不该触发一次动作
    var ready by remember(kind) { mutableStateOf(false) }

    LaunchedEffect(text) {
        infoMap[kind.title] = text
        if (!ready) {
            ready = true
            return@LaunchedEffect
        }
        val action = kind.action?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        delay(EXPLORE_KIND_TEXT_DEBOUNCE_MS)
        controller.evalAction(sourceUrl, action, kind.title)
    }

    BasicTextField(
        value = text,
        onValueChange = { text = it },
        modifier = modifier.then(style.sizeModifier()),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = alignment.toTextAlign(),
        ),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .clip(composeActionShape())
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(
                        horizontal = AppDimens.exploreKindHorizontalPadding,
                        vertical = AppDimens.exploreKindVerticalPadding,
                    ),
                contentAlignment = alignment.toBoxAlignment(),
            ) {
                if (text.isEmpty()) {
                    // 提示文案用次要文本色，与真实输入区分开
                    Text(
                        text = hint,
                        color = pageSecondaryTextColor(),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = alignment.toTextAlign(),
                        maxLines = 1,
                    )
                }
                innerTextField()
            }
        },
    )
}

/** 文本输入的防抖时长，对齐原实现（原 ExploreAdapter 的 600ms 防抖） */
private const val EXPLORE_KIND_TEXT_DEBOUNCE_MS = 600L
