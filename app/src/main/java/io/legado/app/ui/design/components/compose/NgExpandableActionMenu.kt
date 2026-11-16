package io.legado.app.ui.design.components.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

enum class NgMenuSelectionStyle { INDICATOR, TEXT_ONLY, COMPACT_CHECK }

private val LeadingIconSlotSize = 20.dp
private val CompactCheckSize = 14.dp
private val CompactCheckSpacing = 4.dp

@Immutable
data class NgExpandableActionMenuItem(
    @IdRes val itemId: Int,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val dividerBefore: Boolean = false,
    val children: List<NgExpandableActionMenuItem> = emptyList(),
    val title: String? = null,
    val checked: Boolean = false,
    val selectionStyle: NgMenuSelectionStyle = NgMenuSelectionStyle.INDICATOR,
    val danger: Boolean = false,
    val themedIconKind: NgThemedActionIconKind? = null,
    val enabled: Boolean = true,
    val navigation: Boolean = false,
    val leadingIconSize: Dp = LeadingIconSlotSize,
)

enum class NgExpandableActionMenuVariant {
    DROPDOWN,
    SIDE_SLIDE,
    DRILL_IN,
    END_ANCHORED_DROPDOWN,
}

enum class NgExpandableActionMenuWidthVariant(val width: Dp?) {
    CONTENT(null),
    STANDARD(152.dp),
    GROUPED_LABELS(160.dp),
}

@Composable
internal fun rememberNgExpandableActionMenuContentWidth(
    items: List<NgExpandableActionMenuItem>,
): Dp {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodyMedium
    val minWidth = 96.dp
    val maxWidth = (configuration.screenWidthDp.dp - 16.dp)
        .coerceAtMost(240.dp)
        .coerceAtLeast(minWidth)

    return remember(items, configuration, density, textStyle, maxWidth) {
        fun itemTitle(item: NgExpandableActionMenuItem): String {
            return item.title ?: item.titleRes
                .takeIf { it != 0 }
                ?.let { titleRes -> context.getString(titleRes) }
                .orEmpty()
        }

        fun groupWidthPx(group: List<NgExpandableActionMenuItem>): Float {
            if (group.isEmpty()) return 0f
            val reserveIconSpace = group.any {
                it.iconRes != 0 || it.themedIconKind != null
            }
            return group.maxOf { item ->
                val textWidthPx = textMeasurer.measure(
                    text = AnnotatedString(itemTitle(item)),
                    style = textStyle,
                    maxLines = 1,
                ).size.width.toFloat()
                var fixedWidthDp = 24.dp
                if (reserveIconSpace) fixedWidthDp += 30.dp
                fixedWidthDp += when (item.selectionStyle) {
                    NgMenuSelectionStyle.INDICATOR -> if (item.checked) 30.dp else 0.dp
                    NgMenuSelectionStyle.TEXT_ONLY -> 0.dp
                    // Allow for separate pixel rounding of the compact row's slots.
                    NgMenuSelectionStyle.COMPACT_CHECK -> CompactCheckSpacing + CompactCheckSize + 2.dp
                }
                if (item.children.isNotEmpty() || item.navigation) fixedWidthDp += 30.dp
                val rowWidthPx = textWidthPx + with(density) { fixedWidthDp.toPx() }
                maxOf(rowWidthPx, groupWidthPx(item.children))
            }
        }

        with(density) { groupWidthPx(items).toDp() }
            .coerceIn(minWidth, maxWidth)
    }
}

/**
 * Reading NG 可原位展开的轻量操作菜单。
 *
 * 子项与一级项共用同一水平栅格，展开只改变内容高度，不创建二级窗口。
 */
@Composable
fun NgExpandableActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<NgExpandableActionMenuItem>,
    onItemClick: (NgExpandableActionMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    width: Dp? = null,
    rowMinHeight: Dp = 44.dp,
    bottomPointerHeight: Dp = 0.dp,
    bottomPointerWidth: Dp = 18.dp,
    bottomPointerEndOffset: Dp = 26.dp,
    menuContainerColor: Color? = null,
    defaultExpandedItemIds: Set<Int> = emptySet(),
    variant: NgExpandableActionMenuVariant = NgExpandableActionMenuVariant.DROPDOWN,
    properties: PopupProperties = PopupProperties(),
    sideSlideEndMargin: Dp = 8.dp,
    widthVariant: NgExpandableActionMenuWidthVariant =
        NgExpandableActionMenuWidthVariant.CONTENT,
    sideSlideAnchorGap: Dp = 16.dp,
) {
    val pane = LocalNgPaneBounds.current
    val density = LocalDensity.current
    val requestedWidth = width ?: widthVariant.width ?: rememberNgExpandableActionMenuContentWidth(items)
    val resolvedWidth = if (pane == null) requestedWidth else requestedWidth.coerceAtMost(
        (with(density) { pane.width.toDp() } - 16.dp).coerceAtLeast(1.dp)
    )
    var expandedItemIds by remember(items, defaultExpandedItemIds) {
        mutableStateOf(defaultExpandedItemIds)
    }

    if (variant == NgExpandableActionMenuVariant.SIDE_SLIDE) {
        NgSideSlideExpandableActionMenu(
            modifier = modifier,
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            items = items,
            expandedItemIds = expandedItemIds,
            onToggle = { itemId ->
                expandedItemIds = if (itemId in expandedItemIds) {
                    expandedItemIds - itemId
                } else {
                    expandedItemIds + itemId
                }
            },
            onItemClick = onItemClick,
            width = resolvedWidth,
            rowMinHeight = rowMinHeight,
            menuContainerColor = menuContainerColor,
            properties = properties,
            endMargin = sideSlideEndMargin,
            anchorGap = sideSlideAnchorGap,
            onFullyHidden = { expandedItemIds = defaultExpandedItemIds }
        )
        return
    }

    if (variant == NgExpandableActionMenuVariant.DRILL_IN) {
        NgDrillInExpandableActionMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            items = items,
            onItemClick = onItemClick,
            modifier = modifier,
            offset = offset,
            width = resolvedWidth,
            rowMinHeight = rowMinHeight,
            menuContainerColor = menuContainerColor,
            properties = properties
        )
        return
    }

    LaunchedEffect(expanded) {
        if (!expanded) expandedItemIds = defaultExpandedItemIds
    }

    val cornerRadius = NgTheme.shapes.largeDp.dp
    val shape = if (bottomPointerHeight > 0.dp) {
        NgBottomPointerShape(
            cornerRadius = cornerRadius,
            pointerHeight = bottomPointerHeight,
            pointerWidth = bottomPointerWidth,
            pointerEndOffset = bottomPointerEndOffset
        )
    } else {
        RoundedCornerShape(cornerRadius)
    }
    val containerColor = menuContainerColor ?: ngDrawerContentCardColor()

    if (bottomPointerHeight > 0.dp) {
        NgBottomPointerExpandableActionMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            items = items,
            expandedItemIds = expandedItemIds,
            onToggle = { itemId ->
                expandedItemIds = if (itemId in expandedItemIds) {
                    expandedItemIds - itemId
                } else {
                    expandedItemIds + itemId
                }
            },
            onItemClick = onItemClick,
            modifier = modifier,
            offset = offset,
            width = resolvedWidth,
            rowMinHeight = rowMinHeight,
            shape = shape,
            containerColor = containerColor,
            properties = properties,
            bottomPointerHeight = bottomPointerHeight,
        )
        return
    }

    NgPaneAwareDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        modifier = modifier.width(resolvedWidth),
        shape = shape,
        containerColor = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = NgTheme.effects.overlayElevationDp.dp,
        properties = properties,
        endAnchored = variant == NgExpandableActionMenuVariant.END_ANCHORED_DROPDOWN,
    ) {
        NgExpandableActionMenuRows(
            items = items,
            expandedItemIds = expandedItemIds,
            onToggle = { itemId ->
                expandedItemIds = if (itemId in expandedItemIds) {
                    expandedItemIds - itemId
                } else {
                    expandedItemIds + itemId
                }
            },
            onItemClick = onItemClick,
            rowMinHeight = rowMinHeight
        )
    }
}

/**
 * 带底部指向角的菜单需要严格锚在触发段上方。Material DropdownMenu 会额外参与
 * 窗口边界候选定位，靠近页面底部时会让业务层偏移量难以直接对应最终几何；这里
 * 保留同款 Surface 材质与内容留白，只把定位改为明确的“菜单底边对齐锚点顶边”。
 */
@Composable
private fun NgBottomPointerExpandableActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<NgExpandableActionMenuItem>,
    expandedItemIds: Set<Int>,
    onToggle: (Int) -> Unit,
    onItemClick: (NgExpandableActionMenuItem) -> Unit,
    modifier: Modifier,
    offset: DpOffset,
    width: Dp,
    rowMinHeight: Dp,
    shape: Shape,
    containerColor: Color,
    properties: PopupProperties,
    bottomPointerHeight: Dp,
) {
    if (!expanded) return

    val density = LocalDensity.current
    val horizontalOffsetPx = with(density) { offset.x.roundToPx() }
    val verticalOffsetPx = with(density) { offset.y.roundToPx() }
    val windowMarginPx = with(density) { 8.dp.roundToPx() }
    val positionProvider = remember(
        horizontalOffsetPx,
        verticalOffsetPx,
        windowMarginPx,
    ) {
        NgAnchorAbovePopupPositionProvider(
            horizontalOffsetPx = horizontalOffsetPx,
            verticalOffsetPx = verticalOffsetPx,
            windowMarginPx = windowMarginPx,
        )
    }
    val pane = LocalNgPaneBounds.current
    val paneSize = pane?.let { IntSize(it.width, it.height) }
    val scopedPositionProvider = remember(positionProvider, paneSize) {
        if (paneSize == null) positionProvider else NgPanePopupPositionProvider(positionProvider, paneSize)
    }
    val paneHeightModifier = if (pane == null) Modifier else Modifier.heightIn(
        max = (with(density) { pane.height.toDp() } - 16.dp).coerceAtLeast(1.dp),
    )

    Popup(
        popupPositionProvider = scopedPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        Surface(
            modifier = modifier.width(width).then(paneHeightModifier),
            shape = shape,
            color = containerColor,
            contentColor = Color(NgTheme.colors.onSurface),
            tonalElevation = 0.dp,
            shadowElevation = NgTheme.effects.overlayElevationDp.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                NgExpandableActionMenuRows(
                    items = items,
                    expandedItemIds = expandedItemIds,
                    onToggle = onToggle,
                    onItemClick = onItemClick,
                    rowMinHeight = rowMinHeight,
                )
                Spacer(Modifier.height(bottomPointerHeight))
            }
        }
    }
}

/**
 * 二级项进入同一锚点下的独立菜单页，保持一级菜单宽度与位置稳定。
 * 关闭二级页后重新打开会回到一级菜单。
 */
@Composable
private fun NgDrillInExpandableActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<NgExpandableActionMenuItem>,
    onItemClick: (NgExpandableActionMenuItem) -> Unit,
    modifier: Modifier,
    offset: DpOffset,
    width: Dp,
    rowMinHeight: Dp,
    menuContainerColor: Color?,
    properties: PopupProperties
) {
    var activeParentId by remember(items) { mutableStateOf<Int?>(null) }
    LaunchedEffect(expanded) {
        if (!expanded) activeParentId = null
    }
    val activeParent = activeParentId?.let { parentId ->
        items.firstOrNull {
            it.itemId == parentId && it.enabled && it.children.isNotEmpty()
        }
    }
    val visibleItems = activeParent?.children ?: items
    val shape = RoundedCornerShape(NgTheme.shapes.largeDp.dp)

    NgPaneAwareDropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            activeParentId = null
            onDismissRequest()
        },
        offset = offset,
        modifier = modifier.width(width),
        shape = shape,
        containerColor = menuContainerColor ?: ngDrawerContentCardColor(),
        tonalElevation = 0.dp,
        shadowElevation = NgTheme.effects.overlayElevationDp.dp,
        properties = properties
    ) {
        activeParent?.let { parent ->
            Text(
                text = parent.title ?: stringResource(parent.titleRes),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        NgExpandableActionMenuRows(
            items = visibleItems,
            expandedItemIds = emptySet(),
            onToggle = { activeParentId = it },
            onItemClick = onItemClick,
            rowMinHeight = rowMinHeight
        )
    }
}

@Composable
private fun NgPaneAwareDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    offset: DpOffset,
    shape: Shape,
    containerColor: Color,
    tonalElevation: Dp,
    shadowElevation: Dp,
    properties: PopupProperties,
    endAnchored: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pane = LocalNgPaneBounds.current
    if (pane == null && !endAnchored) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            offset = offset,
            shape = shape,
            containerColor = containerColor,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
            properties = properties,
            content = content,
        )
        return
    }
    // Compose 1.7.6 uses the visible display frame for Popup's windowSize, even in a narrow Dialog.
    // Use window-local pane dimensions; anchorBounds already uses positionInWindow coordinates.
    val density = LocalDensity.current
    val paneSize = pane?.let { IntSize(it.width, it.height) }
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded
    if (!expandedState.currentState && !expandedState.targetState) return
    val transformOrigin = remember { mutableStateOf(TransformOrigin.Center) }
    // Let the position provider measure the available height before laying out the full list.
    val anchoredHeightLimit = remember { mutableIntStateOf(1) }
    val horizontalOffset = with(density) { offset.x.roundToPx() }
    val verticalOffset = with(density) { offset.y.roundToPx() }
    val margin = with(density) { if (endAnchored) 8.dp.roundToPx() else 48.dp.roundToPx() }
    val positionProvider = remember(endAnchored, paneSize, horizontalOffset, verticalOffset, margin) {
        if (endAnchored) {
            NgEndAnchoredDropdownPositionProvider(
                paneSize = paneSize,
                horizontalOffset = horizontalOffset,
                gap = verticalOffset.coerceAtLeast(0),
                margin = margin,
            ) { heightLimit, origin ->
                anchoredHeightLimit.intValue = heightLimit
                transformOrigin.value = origin
            }
        } else {
            NgPaneDropdownPositionProvider(checkNotNull(paneSize), horizontalOffset, verticalOffset, margin) { origin ->
                transformOrigin.value = origin
            }
        }
    }
    @Suppress("DEPRECATION")
    val transition = updateTransition(expandedState, "NgPaneDropdownMenu")
    val scale by transition.animateFloat(
        transitionSpec = {
            if (false isTransitioningTo true) tween(120, easing = LinearOutSlowInEasing)
            else tween(1, delayMillis = 74)
        },
        label = "scale",
    ) { if (it) 1f else 0.8f }
    val alpha by transition.animateFloat(
        transitionSpec = { tween(if (false isTransitioningTo true) 30 else 75) },
        label = "alpha",
    ) { if (it) 1f else 0f }
    val maxHeight = with(density) {
        if (endAnchored) anchoredHeightLimit.intValue.toDp()
        else (checkNotNull(paneSize).height.toDp() - 96.dp).coerceAtLeast(1.dp)
    }
    Popup(positionProvider, onDismissRequest = onDismissRequest, properties = properties) {
        Surface(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                this.transformOrigin = transformOrigin.value
            },
            shape = shape,
            color = containerColor,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
        ) {
            Column(
                modifier = modifier.heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                content = content,
            )
        }
    }
}

internal class NgEndAnchoredDropdownPositionProvider(
    private val paneSize: IntSize?,
    private val horizontalOffset: Int,
    private val gap: Int,
    private val margin: Int,
    private val onPlacement: (heightLimit: Int, transformOrigin: TransformOrigin) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val width = minOf(windowSize.width, paneSize?.width ?: windowSize.width)
        val height = minOf(windowSize.height, paneSize?.height ?: windowSize.height)
        val horizontalSpace = (width - popupContentSize.width).coerceAtLeast(0)
        val sideMargin = margin.coerceAtMost(horizontalSpace / 2)
        val desiredX = when (layoutDirection) {
            LayoutDirection.Ltr -> anchorBounds.right - popupContentSize.width + horizontalOffset
            LayoutDirection.Rtl -> anchorBounds.left - horizontalOffset
        }
        val x = desiredX.coerceIn(sideMargin, horizontalSpace - sideMargin)

        val belowTop = anchorBounds.bottom + gap
        val aboveBottom = anchorBounds.top - gap
        val belowHeight = (height - margin - belowTop).coerceAtLeast(0)
        val aboveHeight = (aboveBottom - margin).coerceAtLeast(0)
        val placeBelow = popupContentSize.height <= belowHeight || belowHeight >= aboveHeight
        val y = if (placeBelow) belowTop else aboveBottom - popupContentSize.height

        val pivotX = ((anchorBounds.center.x - x).toFloat() / popupContentSize.width.coerceAtLeast(1))
            .coerceIn(0f, 1f)
        onPlacement(
            maxOf(belowHeight, aboveHeight),
            TransformOrigin(pivotX, if (placeBelow) 0f else 1f),
        )
        return IntOffset(x, y)
    }
}

private class NgPanePopupPositionProvider(
    private val delegate: PopupPositionProvider,
    private val paneSize: IntSize,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val availableSize = IntSize(minOf(paneSize.width, windowSize.width), minOf(paneSize.height, windowSize.height))
        val position = delegate.calculatePosition(anchorBounds, availableSize, layoutDirection, popupContentSize)
        return IntOffset(
            position.x.coerceIn(0, (availableSize.width - popupContentSize.width).coerceAtLeast(0)),
            position.y.coerceIn(0, (availableSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

private class NgPaneDropdownPositionProvider(
    private val paneSize: IntSize,
    private val horizontalOffset: Int,
    private val verticalOffset: Int,
    private val margin: Int,
    private val onTransformOrigin: (TransformOrigin) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val availableSize = IntSize(minOf(paneSize.width, windowSize.width), minOf(paneSize.height, windowSize.height))
        val signedOffset = if (layoutDirection == LayoutDirection.Ltr) horizontalOffset else -horizontalOffset
        val fromLeft = anchorBounds.left + signedOffset
        val fromRight = anchorBounds.right - popupContentSize.width + signedOffset
        val maxX = (availableSize.width - popupContentSize.width).coerceAtLeast(0)
        val edgeX = if (anchorBounds.center.x < availableSize.width / 2) 0 else maxX
        val candidates = if (layoutDirection == LayoutDirection.Ltr) {
            listOf(fromLeft, fromRight, edgeX)
        } else listOf(fromRight, fromLeft, edgeX)
        val x = candidates.firstOrNull { it in 0..maxX } ?: edgeX
        val maxY = (availableSize.height - popupContentSize.height - margin).coerceAtLeast(0)
        val minY = margin.coerceAtMost(maxY)
        val below = anchorBounds.bottom + verticalOffset
        val above = anchorBounds.top - popupContentSize.height + verticalOffset
        val center = anchorBounds.top - popupContentSize.height / 2 + verticalOffset
        val edgeY = if (anchorBounds.center.y < availableSize.height / 2) minY else maxY
        val y = listOf(below, above, center).firstOrNull { it in minY..maxY } ?: edgeY
        val pivotX = when {
            x >= anchorBounds.right -> 0f
            x + popupContentSize.width <= anchorBounds.left -> 1f
            else -> ((maxOf(x, anchorBounds.left) + minOf(x + popupContentSize.width, anchorBounds.right)) / 2f - x) /
                popupContentSize.width.coerceAtLeast(1)
        }
        val pivotY = when {
            y >= anchorBounds.bottom -> 0f
            y + popupContentSize.height <= anchorBounds.top -> 1f
            else -> ((maxOf(y, anchorBounds.top) + minOf(y + popupContentSize.height, anchorBounds.bottom)) / 2f - y) /
                popupContentSize.height.coerceAtLeast(1)
        }
        onTransformOrigin(TransformOrigin(pivotX.coerceIn(0f, 1f), pivotY.coerceIn(0f, 1f)))
        return IntOffset(x, y)
    }
}

@Composable
private fun NgSideSlideExpandableActionMenu(
    modifier: Modifier,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<NgExpandableActionMenuItem>,
    expandedItemIds: Set<Int>,
    onToggle: (Int) -> Unit,
    onItemClick: (NgExpandableActionMenuItem) -> Unit,
    width: Dp,
    rowMinHeight: Dp,
    menuContainerColor: Color?,
    properties: PopupProperties,
    endMargin: Dp,
    anchorGap: Dp,
    onFullyHidden: () -> Unit
) {
    val slideFraction = remember {
        Animatable(if (expanded) 0f else 1f)
    }
    var popupVisible by remember { mutableStateOf(expanded) }
    val motion = NgTheme.snapshot.motion
    val durationMs = if (motion.enabled) motion.mediumDurationMs else 0
    val density = LocalDensity.current
    val endMarginPx = with(density) { endMargin.roundToPx() }
    val anchorBottomOffsetPx = with(density) { anchorGap.roundToPx() }
    // Popup observes snapshot reads made by its position provider. Keep this
    // provider stable so a submenu remeasure cannot recreate the slide source.
    val positionProvider = remember(
        endMarginPx,
        anchorBottomOffsetPx
    ) {
        NgWindowEndBelowAnchorPopupPositionProvider(
            marginPx = endMarginPx,
            anchorBottomOffsetPx = anchorBottomOffsetPx,
            horizontalSlideFraction = { slideFraction.value }
        )
    }
    val pane = LocalNgPaneBounds.current
    val availableHeight = pane?.let { with(density) { it.height.toDp() } }
        ?: LocalConfiguration.current.screenHeightDp.dp
    val requestedHeight = (availableHeight - 220.dp).coerceAtLeast(rowMinHeight)
    val maxHeight = if (pane == null) requestedHeight else requestedHeight.coerceAtMost(
        (availableHeight - 16.dp).coerceAtLeast(1.dp)
    )

    LaunchedEffect(expanded) {
        if (expanded) {
            popupVisible = true
            slideFraction.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = durationMs,
                    easing = LinearOutSlowInEasing
                )
            )
        } else if (popupVisible) {
            slideFraction.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = durationMs,
                    easing = FastOutLinearInEasing
                )
            )
            popupVisible = false
            onFullyHidden()
        }
    }

    if (expanded || popupVisible) {
        val menuContent: @Composable () -> Unit = {
            val shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
            Column(
                modifier = modifier
                    .width(width)
                    .height(maxHeight)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    color = menuContainerColor ?: ngDrawerContentCardColor(),
                    contentColor = Color(NgTheme.colors.onSurface),
                    border = BorderStroke(
                        width = if (NgTheme.snapshot.isEInk) 1.dp else 0.5.dp,
                        color = Color(NgTheme.colors.outlineVariant).copy(
                            alpha = if (NgTheme.snapshot.isEInk) 1f else 0.45f
                        )
                    ),
                    tonalElevation = 0.dp,
                    shadowElevation = NgTheme.effects.overlayElevationDp.dp
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = maxHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 4.dp)
                    ) {
                        NgExpandableActionMenuRows(
                            items = items,
                            expandedItemIds = expandedItemIds,
                            onToggle = onToggle,
                            onItemClick = onItemClick,
                            rowMinHeight = rowMinHeight
                        )
                    }
                }
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismissRequest
                        )
                )
            }
        }
        if (pane == null) {
            Popup(positionProvider, onDismissRequest = onDismissRequest, properties = properties) {
                menuContent()
            }
        } else {
            val paneSize = IntSize(pane.width, pane.height)
            val menuSize = with(density) { IntSize(width.roundToPx(), maxHeight.roundToPx()) }
            val menuOffset = remember { mutableStateOf(IntOffset.Zero) }
            val panePositionProvider = remember(paneSize, menuSize, positionProvider) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        val availableSize = IntSize(
                            minOf(paneSize.width, windowSize.width),
                            minOf(paneSize.height, windowSize.height),
                        )
                        val visibleMenuSize = IntSize(
                            minOf(menuSize.width, availableSize.width),
                            minOf(menuSize.height, availableSize.height),
                        )
                        menuOffset.value = positionProvider.calculatePosition(
                            anchorBounds, availableSize, layoutDirection, visibleMenuSize,
                        )
                        return IntOffset.Zero
                    }
                }
            }
            Popup(panePositionProvider, onDismissRequest = onDismissRequest, properties = properties) {
                Box(
                    Modifier.size(
                        with(density) { pane.width.toDp() },
                        with(density) { pane.height.toDp() },
                    ).clipToBounds().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    )
                ) {
                    Box(Modifier.offset { menuOffset.value }) { menuContent() }
                }
            }
        }
    }
}

/**
 * 水平方向按整扇窗口贴右，纵向则跟随顶栏菜单按钮的底边。
 * 展开内容过长时由窗口边界反向收口，避免越出屏幕。
 */
private class NgWindowEndBelowAnchorPopupPositionProvider(
    private val marginPx: Int,
    private val anchorBottomOffsetPx: Int,
    private val horizontalSlideFraction: () -> Float
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - marginPx)
            .coerceAtLeast(marginPx)
        val slideOffsetPx = (
            (popupContentSize.width + marginPx) *
                horizontalSlideFraction().coerceIn(0f, 1f)
            ).roundToInt()
        val x = when (layoutDirection) {
            LayoutDirection.Ltr -> maxX + slideOffsetPx
            LayoutDirection.Rtl -> marginPx - slideOffsetPx
        }
        val maxY = (windowSize.height - popupContentSize.height - marginPx)
            .coerceAtLeast(marginPx)
        val y = (anchorBounds.bottom + anchorBottomOffsetPx)
            .coerceIn(marginPx, maxY)
        return IntOffset(x, y)
    }
}

/**
 * 让带底部指向角的菜单稳定展开在锚点上方；偏移量直接作用于最终位置，不再经过
 * DropdownMenu 的多候选位置选择。
 */
private class NgAnchorAbovePopupPositionProvider(
    private val horizontalOffsetPx: Int,
    private val verticalOffsetPx: Int,
    private val windowMarginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val desiredX = when (layoutDirection) {
            LayoutDirection.Ltr -> anchorBounds.left + horizontalOffsetPx
            LayoutDirection.Rtl -> anchorBounds.right - popupContentSize.width - horizontalOffsetPx
        }
        val maxX = (windowSize.width - popupContentSize.width - windowMarginPx)
            .coerceAtLeast(windowMarginPx)
        val x = desiredX.coerceIn(windowMarginPx, maxX)

        val desiredY = anchorBounds.top - popupContentSize.height + verticalOffsetPx
        val maxY = (windowSize.height - popupContentSize.height - windowMarginPx)
            .coerceAtLeast(windowMarginPx)
        val y = desiredY.coerceIn(windowMarginPx, maxY)
        return IntOffset(x, y)
    }
}

/**
 * 带底部指向角的菜单外形，用于让浮层与触发按钮形成明确的气泡锚定关系。
 */
private class NgBottomPointerShape(
    private val cornerRadius: Dp,
    private val pointerHeight: Dp,
    private val pointerWidth: Dp,
    private val pointerEndOffset: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radiusPx = with(density) { cornerRadius.toPx() }
        val pointerHeightPx = with(density) { pointerHeight.toPx() }
        val pointerWidthPx = with(density) { pointerWidth.toPx() }
        val pointerEndOffsetPx = with(density) { pointerEndOffset.toPx() }
        val bodyBottom = (size.height - pointerHeightPx).coerceAtLeast(0f)
        val pointerCenterX = when (layoutDirection) {
            LayoutDirection.Ltr -> size.width - pointerEndOffsetPx
            LayoutDirection.Rtl -> pointerEndOffsetPx
        }.coerceIn(pointerWidthPx / 2f, size.width - pointerWidthPx / 2f)
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, size.width, bodyBottom),
                    cornerRadius = CornerRadius(radiusPx),
                )
            )
            moveTo(pointerCenterX - pointerWidthPx / 2f, bodyBottom - 1f)
            lineTo(pointerCenterX, size.height)
            lineTo(pointerCenterX + pointerWidthPx / 2f, bodyBottom - 1f)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun NgExpandableActionMenuRows(
    items: List<NgExpandableActionMenuItem>,
    expandedItemIds: Set<Int>,
    onToggle: (Int) -> Unit,
    onItemClick: (NgExpandableActionMenuItem) -> Unit,
    rowMinHeight: Dp
) {
    val reserveIconSpace = items.any { it.iconRes != 0 || it.themedIconKind != null }
    items.forEach { item ->
        key(item.itemId) {
            if (item.dividerBefore) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                    color = Color(NgTheme.colors.outlineVariant).copy(
                        alpha = if (NgTheme.snapshot.isEInk) 1f else 0.35f
                    )
                )
            }
            val isExpanded = item.enabled && item.itemId in expandedItemIds
            NgExpandableActionMenuRow(
                item = item,
                isExpanded = isExpanded,
                reserveIconSpace = reserveIconSpace,
                rowMinHeight = rowMinHeight,
                onClick = {
                    if (item.children.isEmpty()) {
                        onItemClick(item)
                    } else {
                        onToggle(item.itemId)
                    }
                }
            )
            if (isExpanded) {
                NgExpandableActionMenuRows(
                    items = item.children,
                    expandedItemIds = expandedItemIds,
                    onToggle = onToggle,
                    onItemClick = onItemClick,
                    rowMinHeight = rowMinHeight
                )
            }
        }
    }
}

@Composable
private fun NgExpandableActionMenuRow(
    item: NgExpandableActionMenuItem,
    isExpanded: Boolean,
    reserveIconSpace: Boolean,
    rowMinHeight: Dp,
    onClick: () -> Unit
) {
    val contentColor = Color(
        when {
            item.danger -> NgTheme.colors.error
            item.checked && item.selectionStyle == NgMenuSelectionStyle.TEXT_ONLY -> NgTheme.colors.primary
            else -> NgTheme.colors.onSurface
        }
    ).copy(alpha = if (item.enabled) 1f else 0.38f)
    val themedIconKind = item.themedIconKind
    val compactCheck = item.selectionStyle == NgMenuSelectionStyle.COMPACT_CHECK
    val interaction = if (compactCheck) {
        Modifier.selectable(
            selected = item.checked,
            enabled = item.enabled,
            role = Role.RadioButton,
            onClick = onClick,
        )
    } else {
        Modifier.clickable(enabled = item.enabled, onClick = onClick)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = rowMinHeight)
            .then(interaction)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (reserveIconSpace) {
            Box(
                modifier = Modifier.size(LeadingIconSlotSize),
                contentAlignment = Alignment.Center,
            ) {
                if (themedIconKind != null) {
                    NgThemedActionIcon(
                        kind = themedIconKind,
                        contentDescription = null,
                        modifier = Modifier.size(item.leadingIconSize),
                        tint = contentColor,
                    )
                } else if (item.iconRes != 0) {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(item.leadingIconSize),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = item.title ?: stringResource(item.titleRes),
            color = contentColor,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (compactCheck) {
            Spacer(Modifier.width(CompactCheckSpacing))
            Box(Modifier.size(CompactCheckSize)) {
                if (item.checked) {
                    Icon(
                        painter = painterResource(R.drawable.ng_ic_popup_compact_check),
                        contentDescription = null,
                        tint = Color(NgTheme.colors.primary).copy(alpha = contentColor.alpha),
                        modifier = Modifier.size(CompactCheckSize),
                    )
                }
            }
        } else if (item.checked && item.selectionStyle == NgMenuSelectionStyle.INDICATOR) {
            Spacer(Modifier.width(10.dp))
            Icon(
                painter = painterResource(R.drawable.ng_ic_popup_selected),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        if (item.children.isNotEmpty() || item.navigation) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        if (isExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_right
                    ),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
