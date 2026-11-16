package io.legado.app.ui.design.components.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

@Immutable
data class NgFlatActionRailItem(
    @param:DrawableRes val iconRes: Int? = null,
    val label: String? = null,
    val contentDescription: String? = label,
    val enabled: Boolean = true,
    val emphasized: Boolean = false,
    /** Semantic icon color only; the label and disabled interaction keep their normal state. */
    val dangerIcon: Boolean = false,
    /** Semantic label color only; disabled interaction is unchanged. */
    val dangerLabel: Boolean = false,
)

enum class NgFlatActionRailVariant {
    SEGMENTED,
    COMPACT_SEGMENTED,
    SPACED_COMPACT,
    MODE_PICKER,
    TEXT_MODE_PICKER,
    INLINE_MODE_PICKER,
    INLINE_DIVIDED,
    /** Host-surface actions grouped by whitespace, without a container or dividers. */
    INLINE_ACTIONS,
    FORM_TEXT_PICKER,
    /** 表单内的文字分页：透明底、文字等宽分区、标签宽度的选中下划线。 */
    FORM_UNDERLINE_TABS,
}

/**
 * NG 平面分段操作轨。
 *
 * 所有操作共享同一承载层，只用分隔线组织动作；主操作通过主题选中容器强调，
 * 不增加独立描边、阴影或凸起效果。末项可以承载与该段对齐的菜单等浮层。
 */
@Composable
fun NgFlatActionRail(
    items: List<NgFlatActionRailItem>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    variant: NgFlatActionRailVariant = NgFlatActionRailVariant.SEGMENTED,
    trailingOverlay: (@Composable BoxScope.() -> Unit)? = null
) {
    require(items.isNotEmpty()) { "NgFlatActionRail requires at least one item" }
    items.forEach { item ->
        require(item.label != null || !item.contentDescription.isNullOrBlank()) {
            "Icon-only NgFlatActionRailItem requires a contentDescription"
        }
    }

    val compactSegmented = variant == NgFlatActionRailVariant.COMPACT_SEGMENTED
    val modePicker = variant == NgFlatActionRailVariant.MODE_PICKER
    val textModePicker = variant == NgFlatActionRailVariant.TEXT_MODE_PICKER
    val inlineModePicker = variant == NgFlatActionRailVariant.INLINE_MODE_PICKER
    val inlineDivided = variant == NgFlatActionRailVariant.INLINE_DIVIDED
    val inlineActions = variant == NgFlatActionRailVariant.INLINE_ACTIONS
    val formTextPicker = variant == NgFlatActionRailVariant.FORM_TEXT_PICKER
    val formUnderlineTabs = variant == NgFlatActionRailVariant.FORM_UNDERLINE_TABS
    val picker = modePicker || textModePicker || inlineModePicker || formTextPicker || formUnderlineTabs
    val segmented = variant != NgFlatActionRailVariant.SPACED_COMPACT && !inlineActions
    val dividerColor = Color(NgTheme.colors.outlineVariant).copy(
        alpha = if (NgTheme.snapshot.isEInk) 1f else 0.32f
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(
                when {
                    modePicker -> 58.dp
                    formUnderlineTabs -> 48.dp
                    textModePicker || formTextPicker -> 44.dp
                    inlineActions -> 44.dp
                    inlineModePicker || inlineDivided -> 36.dp
                    compactSegmented -> 28.dp
                    else -> 36.dp
                }
            )
            .then(if (formUnderlineTabs) Modifier.selectableGroup() else Modifier)
            .then(
                if (compactSegmented || (picker && !formTextPicker && !inlineModePicker && !formUnderlineTabs)) {
                    val railShape = RoundedCornerShape(if (picker) 12.dp else 8.dp)
                    Modifier
                        .clip(railShape)
                        .background(
                            if (picker) {
                                ngDrawerContentCardColor()
                            } else {
                                Color(NgTheme.colors.surfaceContainerHigh).copy(
                                    alpha = if (NgTheme.snapshot.isEInk) 1f else 0.38f
                                )
                            }
                        )
                        .border(
                            width = 0.5.dp,
                            color = dividerColor.copy(alpha = 0.24f),
                            shape = railShape
                        )
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0 && variant == NgFlatActionRailVariant.SPACED_COMPACT) {
                Spacer(Modifier.width(4.dp))
            }
            val slotModifier = if (
                segmented && item.label == null
            ) {
                Modifier.width(42.dp)
            } else {
                Modifier.weight(1f)
            }
            Box(
                modifier = slotModifier.fillMaxHeight()
            ) {
                if (index > 0 && segmented && (!picker || formTextPicker)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(1.dp)
                            .height(if (compactSegmented) 16.dp else 22.dp)
                            .background(dividerColor)
                    )
                }
                NgFlatActionRailSegment(
                    item = item,
                    modifier = Modifier.fillMaxWidth(),
                    variant = variant,
                    onClick = { onItemClick(index) }
                )
                if (index == items.lastIndex) {
                    trailingOverlay?.invoke(this)
                }
            }
        }
    }
}

@Composable
private fun NgFlatActionRailSegment(
    item: NgFlatActionRailItem,
    modifier: Modifier = Modifier,
    variant: NgFlatActionRailVariant,
    onClick: () -> Unit
) {
    val primaryColor = Color(NgTheme.colors.primary)
    val disabledColor = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.48f)
    val neutralColor = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.90f)
    val textModePicker = variant == NgFlatActionRailVariant.TEXT_MODE_PICKER
    val inlineModePicker = variant == NgFlatActionRailVariant.INLINE_MODE_PICKER
    val inlineDivided = variant == NgFlatActionRailVariant.INLINE_DIVIDED
    val inlineActions = variant == NgFlatActionRailVariant.INLINE_ACTIONS
    val formTextPicker = variant == NgFlatActionRailVariant.FORM_TEXT_PICKER
    val formUnderlineTabs = variant == NgFlatActionRailVariant.FORM_UNDERLINE_TABS
    val textOnlyPicker = textModePicker || formTextPicker || formUnderlineTabs
    val picker = variant == NgFlatActionRailVariant.MODE_PICKER || inlineModePicker || textOnlyPicker
    val contentColor = when {
        !item.enabled -> disabledColor
        picker -> {
            if (item.emphasized) primaryColor else neutralColor
        }
        inlineDivided || inlineActions -> if (item.emphasized) primaryColor else neutralColor
        variant == NgFlatActionRailVariant.COMPACT_SEGMENTED -> neutralColor
        else -> primaryColor
    }
    val iconColor = if (item.dangerIcon) Color(NgTheme.colors.error) else contentColor
    val labelColor = if (item.dangerLabel) Color(NgTheme.colors.error) else contentColor
    val selectedContainer = Color(NgTheme.colors.selectedContainer).copy(
        alpha = when {
            NgTheme.snapshot.isEInk -> 1f
            item.enabled -> 0.72f
            else -> 0.24f
        }
    )
    val compactSegmented = variant == NgFlatActionRailVariant.COMPACT_SEGMENTED
    val shape = RoundedCornerShape(if (compactSegmented) 8.dp else 10.dp)
    val interactionModifier = Modifier
        .clickable(
            enabled = item.enabled,
            role = if (formUnderlineTabs) Role.Tab else Role.Button,
            onClick = onClick
        )
        .semantics(mergeDescendants = true) {
            item.contentDescription?.let { contentDescription = it }
            if (formUnderlineTabs) selected = item.emphasized
        }
    if (picker) {
        Box(
            modifier = modifier
                .fillMaxHeight()
                .then(interactionModifier),
            contentAlignment = Alignment.Center,
        ) {
            if (formUnderlineTabs) {
                Column(
                    modifier = Modifier.width(IntrinsicSize.Max),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = item.label.orEmpty(),
                        color = labelColor,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = if (item.emphasized) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (item.emphasized) contentColor else Color.Transparent)
                    )
                }
            } else if (inlineModePicker) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    item.iconRes?.let { iconRes ->
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                if (item.emphasized) primaryColor else Color.Transparent
                            )
                    )
                }
            } else if (formTextPicker) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(21.dp))
                    item.label?.let { label ->
                        Text(
                            text = label,
                            color = labelColor,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier.size(15.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.emphasized) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(shape)
                        .then(
                            if (item.emphasized) {
                                Modifier.background(selectedContainer, shape)
                            } else {
                                Modifier
                            }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    item.iconRes?.let { iconRes ->
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    item.label?.let { label ->
                        if (item.iconRes != null) Spacer(Modifier.height(2.dp))
                        Text(
                            text = label,
                            color = labelColor,
                            fontSize = if (textOnlyPicker) 14.sp else 12.sp,
                            lineHeight = if (textOnlyPicker) 18.sp else 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxHeight()
                .clip(shape)
                .then(
                    if (item.emphasized && variant != NgFlatActionRailVariant.SPACED_COMPACT && !inlineActions) {
                        Modifier.background(selectedContainer, shape)
                    } else {
                        Modifier
                    }
                )
                .then(interactionModifier),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.iconRes?.let { iconRes ->
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(if (compactSegmented) 16.dp else 19.dp)
                )
            }
            item.label?.let { label ->
                if (item.iconRes != null) {
                    Spacer(Modifier.width(if (compactSegmented) 4.dp else 5.dp))
                }
                Text(
                    text = label,
                    color = labelColor,
                    fontSize = if (compactSegmented) 12.sp else 13.sp,
                    lineHeight = if (compactSegmented) 16.sp else 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
