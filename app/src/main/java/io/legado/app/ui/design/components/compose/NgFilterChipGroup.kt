package io.legado.app.ui.design.components.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.NgFilterChipGroupVariant
import io.legado.app.ui.design.theme.NgTheme

/** NG 长列表筛选面板内的可选项。 */
@Immutable
data class NgFilterChipItem(
    val key: String,
    val label: String,
    @param:DrawableRes val iconRes: Int? = null,
)

/** 与筛选 Chip 共用几何的紧凑一次性动作，用于示例、建议和快捷填充。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NgActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = rememberNgSolidButtonColors(Color(NgTheme.colors.primary))
    val contentColor = Color(colors.content)
    Row(
        modifier = modifier
            .height(CHIP_HEIGHT)
            .widthIn(max = 360.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(colors.container))
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                }
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        iconRes?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor,
            )
        }
        Text(
            text = text,
            color = contentColor,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * NG 长列表统一的筛选标签组。
 *
 * 未选中项保持亮白承载面，选中项使用当前主题的强调容器；业务页面只维护 key 集合。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NgFilterChipGroup(
    items: List<NgFilterChipItem>,
    selectedKeys: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    variant: NgFilterChipGroupVariant = NgFilterChipGroupVariant.WRAP,
) {
    when (variant) {
        NgFilterChipGroupVariant.WRAP -> {
            FlowRow(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.forEach { item ->
                    NgFilterChip(
                        item = item,
                        selected = item.key in selectedKeys,
                        onClick = { onToggle(item.key) },
                    )
                }
            }
        }

        NgFilterChipGroupVariant.EVEN_ROW -> {
            NgEvenFilterChipRow(items, selectedKeys, onToggle, modifier)
        }

        NgFilterChipGroupVariant.TWO_ROW_RAIL -> {
            NgTwoRowFilterChipRail(
                items = items,
                selectedKeys = selectedKeys,
                onToggle = onToggle,
                modifier = modifier,
            )
        }
    }
}

/** 分类等宽铺满一行；窄屏或大字号放不下时保留横向滚动。 */
@Composable
private fun NgEvenFilterChipRow(
    items: List<NgFilterChipItem>,
    selectedKeys: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier,
) {
    if (items.isEmpty()) return
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, lineHeight = 16.sp)
    val minimumWidth = remember(items, density, textMeasurer, textStyle) {
        items.maxOf { item ->
            val textWidth = with(density) {
                textMeasurer.measure(item.label, textStyle, maxLines = 1).size.width.toDp()
            }
            textWidth + 22.dp + if (item.iconRes == null) 0.dp else 21.dp
        }
    }
    BoxWithConstraints(modifier) {
        val itemWidth = maxOf(minimumWidth, (maxWidth - CHIP_GAP * (items.size - 1)) / items.size)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        ) {
            items.forEach { item ->
                NgFilterChip(item, item.key in selectedKeys, { onToggle(item.key) }, Modifier.width(itemWidth))
            }
        }
    }
}

/**
 * 固定高度的双排横向筛选轨。相邻两个项目组成一列，每列使用其中较长项的宽度；
 * 分组数量只改变横向滚动范围，不改变抽屉内容高度。
 */
@Composable
private fun NgTwoRowFilterChipRail(
    items: List<NgFilterChipItem>,
    selectedKeys: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val columns = items.chunked(2)
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(TWO_ROW_RAIL_HEIGHT)
            .trailingFadingEdge(listState.canScrollForward),
        state = listState,
        contentPadding = PaddingValues(end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        items(
            items = columns,
            key = { column -> column.joinToString(separator = "\u0000") { it.key } },
        ) { columnItems ->
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(IntrinsicSize.Max),
                verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
            ) {
                columnItems.forEach { item ->
                    NgFilterChip(
                        item = item,
                        selected = item.key in selectedKeys,
                        onClick = { onToggle(item.key) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NgFilterChip(
    item: NgFilterChipItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 11.dp,
) {
    val colors = NgTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val containerColor = if (selected) {
        Color(colors.selectedContainer)
    } else {
        ngDrawerContentCardColor()
    }
    val contentColor = if (selected) {
        Color(colors.primary)
    } else {
        Color(colors.onSurfaceVariant)
    }
    Row(
        modifier = modifier
            .height(CHIP_HEIGHT)
            .widthIn(max = 176.dp)
            .clip(shape)
            .background(containerColor)
            .selectable(
                selected = selected,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            space = 5.dp,
            alignment = Alignment.CenterHorizontally,
        ),
    ) {
        item.iconRes?.let { iconRes ->
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor,
            )
        }
        Text(
            text = item.label,
            color = contentColor,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Modifier.trailingFadingEdge(enabled: Boolean): Modifier {
    if (!enabled) return this
    return graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color.Black,
                    0.94f to Color.Black,
                    1f to Color.Transparent,
                )
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

private val CHIP_HEIGHT = 32.dp
private val CHIP_GAP = 6.dp
private val TWO_ROW_RAIL_HEIGHT = 70.dp
