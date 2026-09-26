package io.legado.app.ui.main.explore.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import io.legado.app.data.entities.rule.FlexChildStyle
import kotlin.math.roundToInt

/**
 * 参与弹性布局的一个分类项。
 *
 * @param style 书源里声明的子项样式
 * @param fillLine 该项独占整行（html 类：View 版里它本身是 match_parent）
 */
internal class ExploreFlexItemSpec(
    val style: FlexChildStyle,
    val fillLine: Boolean = false,
)

/** 弹性布局里一项的基准宽度与 grow 系数 */
private class FlexEntry(val index: Int, val basisPx: Int, val grow: Float)

/**
 * 发现分类区的弹性布局，复刻 FlexboxLayout 在 `flexDirection=row + flexWrap=wrap` 下的行为：
 * 1. **基准宽度**：`flexBasisPercent > 0` 取整行宽度的百分比，[ExploreFlexItemSpec.fillRectline]
 *    取整行，否则取内容自身宽度；
 * 2. **断行**：当前行放不下、或该项声明 `layout_wrapBefore` 时换到下一行；
 * 3. **分配**：行内剩余宽度按 `flexGrow` 的比例分给各项，`flexGrow = 0` 的项保持基准宽度。
 *
 * 不用 `FlowRow` 的原因：它没有 grow 语义，且 `fillMaxWidth(fraction)` 以"剩余空间"为基准，
 * 与 flexbox 的"整行宽度"基准不一致——同一行越往后越窄，0.25 + 0.15×3 这类声明会被挤成一串省略号。
 */
@Composable
internal fun ExploreFlexLayout(
    modifier: Modifier = Modifier,
    items: List<ExploreFlexItemSpec>,
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    itemContent: @Composable (Int) -> Unit,
) {
    Layout(
        content = {
            items.indices.forEach { index -> itemContent(index) }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        check(constraints.hasBoundedWidth) { "ExploreFlexLayout 需要有界宽度（外层需 fillMaxWidth）" }
        val lineWidth = constraints.maxWidth
        val hSpace = horizontalSpacing.roundToPx()
        val vSpace = verticalSpacing.roundToPx()

        // intrinsic 宽度不消耗测量次数，先拿内容宽度做断行
        val preferredWidths = measurables.map { it.maxIntrinsicWidth(0) }

        val lines = mutableListOf<MutableList<FlexEntry>>()
        var current = mutableListOf<FlexEntry>()
        var used = 0
        items.forEachIndexed { index, spec ->
            val percent = spec.style.layout_flexBasisPercent
            val basis = when {
                spec.fillLine -> lineWidth
                percent > 0 -> (percent * lineWidth).roundToInt().coerceIn(0, lineWidth)
                else -> preferredWidths[index]
            }
            if (spec.style.layout_wrapBefore && current.isNotEmpty()) {
                lines += current
                current = mutableListOf()
                used = 0
            }
            val needed = if (current.isEmpty()) basis else used + hSpace + basis
            if (needed > lineWidth && current.isNotEmpty()) {
                lines += current
                current = mutableListOf()
                used = 0
            }
            current += FlexEntry(index, basis, spec.style.layout_flexGrow)
            used = if (current.size == 1) basis else used + hSpace + basis
        }
        if (current.isNotEmpty()) lines += current

        // 行内剩余宽度按 grow 分配，得到每项的最终宽度
        val finalWidths = IntArray(items.size)
        val lineHeights = IntArray(lines.size)
        lines.forEachIndexed { lineIndex, line ->
            val usedInLine = line.sumOf { it.basisPx } + hSpace * (line.size - 1)
            val leftover = (lineWidth - usedInLine).coerceAtLeast(0)
            val growSum = line.sumOf { it.grow.toDouble() }.toFloat()
            line.forEach { entry ->
                val extra = if (growSum > 0f) leftover * (entry.grow / growSum) else 0f
                finalWidths[entry.index] = (entry.basisPx + extra).roundToInt().coerceIn(0, lineWidth)
            }
        }

        // 按最终宽度测量（每项只测一次）
        val placeables = measurables.mapIndexed { index, measurable ->
            val width = finalWidths[index]
            measurable.measure(
                Constraints(
                    minWidth = width,
                    maxWidth = width,
                    minHeight = 0,
                    maxHeight = constraints.maxHeight,
                )
            )
        }

        lines.forEachIndexed { lineIndex, line ->
            lineHeights[lineIndex] = line.maxOf { placeables[it.index].height }
        }
        val contentHeight = lineHeights.sum() + vSpace * (lines.size - 1).coerceAtLeast(0)

        layout(lineWidth, contentHeight.coerceAtLeast(0)) {
            var y = 0
            lines.forEachIndexed { lineIndex, line ->
                var x = 0
                line.forEach { entry ->
                    placeables[entry.index].place(x = x, y = y)
                    x += placeables[entry.index].width + hSpace
                }
                y += lineHeights[lineIndex] + vSpace
            }
        }
    }
}
