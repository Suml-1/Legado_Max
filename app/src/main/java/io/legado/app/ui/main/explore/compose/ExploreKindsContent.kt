package io.legado.app.ui.main.explore.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.theme.AppDimens

/**
 * 展开书源的分类区。
 *
 * 布局对齐原 `flexbox`（`flexWrap=wrap` + `flexDirection=row`），由 [ExploreFlexLayout] 复刻：
 * 基准宽度、断行与 `flexGrow` 行内分配都按书源声明的 `FlexChildStyle` 计算；
 * html / select 两类在 View 版里本身是 match_parent，这里声明为整行。
 */
@Composable
internal fun ExploreKindsContent(
    modifier: Modifier = Modifier,
    sourceItem: ExploreSourceItem,
    kindsState: ExploreKindsState,
    controller: ExploreKindsController,
    actions: ExploreSourceActions,
) {
    val kinds = kindsState.kinds
    if (kinds.isEmpty()) return
    ExploreFlexLayout(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = AppDimens.exploreKindsHorizontalPadding,
                end = AppDimens.exploreKindsHorizontalPadding,
                top = AppDimens.exploreKindsTopSpacing,
            ),
        items = kinds.map { kind ->
            ExploreFlexItemSpec(
                style = kind.style(),
                // 只有 html 在 View 版里本身是 match_parent；select 跟随书源声明的基准宽度，
                // 强制独占整行会破坏 createFilter 这类"一行放 2-3 个筛选"的布局
                fillLine = kind.type == ExploreKind.Type.html,
            )
        },
        horizontalSpacing = AppDimens.exploreKindSpacing,
        verticalSpacing = AppDimens.exploreKindSpacing,
    ) { index ->
        ExploreKindItem(
            kind = kinds[index],
            sourceUrl = sourceItem.sourceUrl,
            controller = controller,
            actions = actions,
        )
    }
}

/** 按分类类型分发到对应的交互组件 */
@Composable
private fun ExploreKindItem(
    kind: ExploreKind,
    sourceUrl: String,
    controller: ExploreKindsController,
    actions: ExploreSourceActions,
) {
    when (kind.type) {
        ExploreKind.Type.url -> ExploreKindActionChip(
            kind = kind,
            sourceUrl = sourceUrl,
            controller = controller,
            onClick = {
                val url = kind.url.orEmpty()
                if (kind.title.startsWith(ERROR_KIND_PREFIX)) {
                    actions.onShowError(url)
                } else {
                    actions.onOpenExplore(sourceUrl, kind.title, url)
                }
            },
        )

        ExploreKind.Type.button -> ExploreKindActionChip(
            kind = kind,
            sourceUrl = sourceUrl,
            controller = controller,
            onClick = {
                kind.action?.takeIf { it.isNotBlank() }?.let {
                    controller.evalAction(sourceUrl, it, kind.title)
                }
            },
        )

        ExploreKind.Type.toggle -> ExploreKindToggleChip(
            kind = kind,
            sourceUrl = sourceUrl,
            controller = controller,
        )

        ExploreKind.Type.text -> ExploreKindTextField(
            kind = kind,
            sourceUrl = sourceUrl,
            controller = controller,
        )

        ExploreKind.Type.select -> ExploreKindSelectField(
            kind = kind,
            sourceUrl = sourceUrl,
            controller = controller,
        )

        ExploreKind.Type.html -> ExploreHtmlContent(
            kind = kind,
            sourceUrl = sourceUrl,
            controller = controller,
            onShowPhoto = actions.onShowPhoto,
        )

        else -> Unit
    }
}

/** 书源规则求值失败时，分类标题会带这个前缀，内容放在 `url` 上 */
private const val ERROR_KIND_PREFIX = "ERROR:"
