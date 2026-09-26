package io.legado.app.ui.main.explore.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.theme.AppDimens

/**
 * 展开书源的分类区。
 *
 * 布局对齐原 `flexbox`（`flexWrap=wrap` + `flexDirection=row`）：
 * 每一项按自身 `FlexChildStyle` 决定宽度（内容宽度 / 基准百分比 / 整行），`layout_wrapBefore`
 * 通过 [groupByWrapBefore] 拆成多个 [FlowRow] 表达。与原实现的差异见 `ExploreKindStyle` 的说明。
 */
@OptIn(ExperimentalLayoutApi::class)
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = AppDimens.exploreKindsHorizontalPadding,
                end = AppDimens.exploreKindsHorizontalPadding,
                top = AppDimens.exploreKindsTopSpacing,
            ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.exploreKindSpacing),
    ) {
        kinds.groupByWrapBefore().forEach { group ->
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.exploreKindSpacing),
                verticalArrangement = Arrangement.spacedBy(AppDimens.exploreKindSpacing),
            ) {
                group.forEach { kind ->
                    ExploreKindItem(
                        kind = kind,
                        sourceUrl = sourceItem.sourceUrl,
                        controller = controller,
                        actions = actions,
                    )
                }
            }
        }
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
