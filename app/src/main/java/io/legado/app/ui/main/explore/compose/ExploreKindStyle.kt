package io.legado.app.ui.main.explore.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.FlexChildStyle

/**
 * 分类项的展示文案。
 *
 * `viewName` 有三种写法（与原实现一致）：不填、单引号包起来的字面量、需要求值的 JS 表达式。
 * 只有第三种需要跑脚本，[needsJs] 为 true。
 */
internal data class ExploreKindTitle(
    val text: String,
    val needsJs: Boolean,
)

internal fun ExploreKind.staticTitle(): ExploreKindTitle {
    val viewName = viewName
    return when {
        viewName == null -> ExploreKindTitle(title, needsJs = false)
        viewName.isQuotedLiteral() -> ExploreKindTitle(
            viewName.substring(1, viewName.length - 1),
            needsJs = false,
        )
        else -> ExploreKindTitle(title, needsJs = true)
    }
}

/** 对齐原实现的"单引号字面量"判定：长度 3..19 且首尾都是单引号 */
internal fun String.isQuotedLiteral(): Boolean {
    return length in 3..19 && first() == '\'' && last() == '\''
}

/**
 * 分类展示文案：静态标题直接返回，需要求值的用 [produceState] 求值。
 *
 * 求值失败显示 `err`、结果为空显示 `null`——保持与原实现相同的兜底口径，
 * 让书源作者能从界面上区分"规则写错"与"规则没匹配到内容"。
 */
@Composable
internal fun rememberKindName(
    sourceUrl: String,
    kind: ExploreKind,
    controller: ExploreKindsController,
): State<String> {
    val title = kind.staticTitle()
    if (!title.needsJs) {
        return remember(title.text) { mutableStateOf(title.text) }
    }
    return produceState(
        initialValue = title.text,
        key1 = sourceUrl,
        key2 = kind.viewName,
    ) {
        value = controller.evalName(sourceUrl, kind.viewName.orEmpty()).fold(
            onSuccess = { result -> result?.takeIf { it.isNotEmpty() } ?: "null" },
            onFailure = { "err" },
        )
    }
}

/**
 * 把 View 侧 flexbox 的子项样式翻译成 Compose 尺寸。
 *
 * `layout_flexBasisPercent` 对应 flexbox 的"基准宽度百分比"，Compose 侧用
 * `fillMaxWidth(percent)` 表达；其余情况交给 `FlowRow` 按内容宽度排布（原实现里
 * url/button/toggle 等胶囊的 `flexGrow` 默认为 0，正是"按内容宽度"）。
 *
 * 有意保留的偏差：`layout_wrapBefore` 不在这里表达——它在 [groupByWrapBefore] 里
 * 拆行处理，因为 FlowRow 没有"强制换行"能力。
 */
internal fun FlexChildStyle.sizeModifier(): Modifier {
    val fraction = layout_flexBasisPercent
    return when {
        fraction >= 1f -> Modifier.fillMaxWidth()
        fraction > 0f -> Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f))
        else -> Modifier
    }
}

/**
 * 子项在自己那一格内的水平对齐（原 `layout_justifySelf`）。
 *
 * @param default 样式未命中任何分支时的兜底对齐：url/button/toggle/select 居中、text 靠左。
 */
internal fun FlexChildStyle.horizontalAlignment(default: Alignment.Horizontal): Alignment.Horizontal {
    return when (layout_justifySelf) {
        "flex_start", "start" -> Alignment.Start
        "flex_end", "end", "right" -> Alignment.End
        "center" -> Alignment.CenterHorizontally
        else -> default
    }
}

/** html 载荷前缀：走 WebView 渲染 */
internal const val HTML_KIND_USE_WEB = "<useweb>"

/** html 载荷前缀：走富文本渲染 */
internal const val HTML_KIND_USE_HTML = "<usehtml>"

/** 是否是需要渲染的 html 载荷（而不是普通链接/文案） */
internal fun String.isHtmlPayload(): Boolean {
    return startsWith(HTML_KIND_USE_HTML) || startsWith(HTML_KIND_USE_WEB)
}

/**
 * toggle / select 的候选项列表。
 *
 * 书源没配 `chars` 时用原实现的占位文案提示规则写错了；配了空数组时也走占位，
 * 避免后面按下标取值时崩在书源作者写错的规则上。
 */
internal fun ExploreKind.charsOrDefault(): List<String> {
    return chars?.filterNotNull()?.takeIf { it.isNotEmpty() } ?: listOf("chars", "is null")
}

/** 分类项在自己那一格里的对齐，翻译成 `Box` 的 contentAlignment */
internal fun Alignment.Horizontal.toBoxAlignment(): Alignment {
    return when (this) {
        Alignment.Start -> Alignment.CenterStart
        Alignment.End -> Alignment.CenterEnd
        else -> Alignment.Center
    }
}

/** 分类项文本的对齐，翻译成 `TextAlign` */
internal fun Alignment.Horizontal.toTextAlign(): TextAlign {
    return when (this) {
        Alignment.Start -> TextAlign.Start
        Alignment.End -> TextAlign.End
        else -> TextAlign.Center
    }
}

/**
 * 按 `layout_wrapBefore` 把分类切成若干组，每组渲染成一个 `FlowRow`。
 *
 * 拆组而不是给单个自增宽度，是为了让"强制换行"既不影响上一行的尾部留白，
 * 也不让下一行的第一项与上一行共享剩余宽度。
 */
internal fun List<ExploreKind>.groupByWrapBefore(): List<List<ExploreKind>> {
    if (isEmpty()) return emptyList()
    val groups = mutableListOf<MutableList<ExploreKind>>()
    var current = mutableListOf<ExploreKind>()
    forEach { kind ->
        if (kind.style().layout_wrapBefore && current.isNotEmpty()) {
            groups.add(current)
            current = mutableListOf()
        }
        current.add(kind)
    }
    groups.add(current)
    return groups
}
