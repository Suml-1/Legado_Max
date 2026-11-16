package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

/** 编辑页工具栏中的纯图标动作。 */
@Immutable
data class NgEditorTopBarAction(
    val icon: Painter,
    val contentDescription: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val iconSize: Dp = 22.dp,
)

/**
 * 高密度编辑页顶栏。标题保持左对齐，动作使用固定槽位，避免业务页逐个调整间距。
 */
@Composable
fun NgEditorTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: List<NgEditorTopBarAction> = emptyList(),
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NgEditorTopBarIconButton(
            icon = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.back),
            onClick = onBack,
            modifier = Modifier.size(48.dp),
            iconSize = 24.dp,
        )
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp, end = 4.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions.forEach { action ->
            NgEditorTopBarIconButton(
                icon = action.icon,
                contentDescription = action.contentDescription,
                onClick = action.onClick,
                enabled = action.enabled,
                iconSize = action.iconSize,
            )
        }
        trailingContent?.invoke(this)
    }
}

/** 编辑页顶栏的 44dp 动作槽。 */
@Composable
fun NgEditorTopBarIconButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = Color(NgTheme.colors.onSurface).copy(
                alpha = if (enabled) 1f else 0.38f,
            ),
        )
    }
}

@Immutable
data class NgEditorSelectOption(
    val value: String,
    val label: String,
)

@Immutable
data class NgEditorToggleItem(
    val key: String,
    val title: String,
    val checked: Boolean,
)

/**
 * 编辑页两行三列连续配置面板。首格为选择器，其余格为紧凑复选项。
 */
@Composable
fun NgEditorConfigPanel(
    selectTitle: String,
    selectedValue: String,
    selectOptions: List<NgEditorSelectOption>,
    firstRowToggles: List<NgEditorToggleItem>,
    secondRowToggles: List<NgEditorToggleItem>,
    onSelect: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(firstRowToggles.size == 2)
    require(secondRowToggles.size == 3)
    NgFormPanel(modifier = modifier) {
        NgEditorGridRow {
            NgEditorSelectCell(
                title = selectTitle,
                selectedValue = selectedValue,
                options = selectOptions,
                onSelected = onSelect,
                modifier = Modifier.weight(1f),
            )
            NgEditorGridVerticalDivider()
            firstRowToggles.forEachIndexed { index, item ->
                NgEditorToggleCell(
                    item = item,
                    onCheckedChange = { onToggle(item.key, it) },
                    modifier = Modifier.weight(1f),
                )
                if (index == 0) NgEditorGridVerticalDivider()
            }
        }
        HorizontalDivider(
            thickness = 0.6.dp,
            color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f),
        )
        NgEditorGridRow {
            secondRowToggles.forEachIndexed { index, item ->
                NgEditorToggleCell(
                    item = item,
                    onCheckedChange = { onToggle(item.key, it) },
                    modifier = Modifier.weight(1f),
                )
                if (index < secondRowToggles.lastIndex) NgEditorGridVerticalDivider()
            }
        }
    }
}

@Composable
private fun NgEditorGridRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 订阅源编辑专用布局：首行三个开关，次行两个选择器和预加载开关。 */
@Composable
fun NgRssEditorConfigPanel(
    toggles: List<NgEditorToggleItem>,
    typeTitle: String,
    typeValue: String,
    typeOptions: List<NgEditorSelectOption>,
    styleTitle: String,
    styleValue: String,
    styleOptions: List<NgEditorSelectOption>,
    onTypeSelected: (String) -> Unit,
    onStyleSelected: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(toggles.size == 4)
    NgFormPanel(modifier = modifier) {
        NgEditorGridRow {
            toggles.take(3).forEachIndexed { index, item ->
                NgEditorToggleCell(
                    item = item,
                    onCheckedChange = { onToggle(item.key, it) },
                    modifier = Modifier.weight(1f),
                )
                if (index < 2) NgEditorGridVerticalDivider()
            }
        }
        HorizontalDivider(
            thickness = 0.6.dp,
            color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f),
        )
        NgEditorGridRow {
            NgEditorSelectCell(
                title = typeTitle,
                selectedValue = typeValue,
                options = typeOptions,
                onSelected = onTypeSelected,
                modifier = Modifier.weight(1f),
            )
            NgEditorGridVerticalDivider()
            NgEditorSelectCell(
                title = styleTitle,
                selectedValue = styleValue,
                options = styleOptions,
                onSelected = onStyleSelected,
                modifier = Modifier.weight(1f),
            )
            NgEditorGridVerticalDivider()
            NgEditorToggleCell(
                item = toggles.last(),
                onCheckedChange = { onToggle(toggles.last().key, it) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NgEditorGridVerticalDivider() {
    VerticalDivider(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 9.dp),
        thickness = 0.6.dp,
        color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f),
    )
}

@Composable
private fun NgEditorToggleCell(
    item: NgEditorToggleItem,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .toggleable(
                value = item.checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Checkbox(
                checked = item.checked,
                onCheckedChange = null,
                modifier = Modifier
                    .size(30.dp)
                    .scale(0.84f),
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(NgTheme.colors.primary),
                    checkmarkColor = Color.White,
                    uncheckedColor = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.68f),
                ),
            )
        }
        Text(
            text = item.title,
            color = Color(NgTheme.colors.onSurface),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NgEditorSelectCell(
    title: String,
    selectedValue: String,
    options: List<NgEditorSelectOption>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    var widthPx by remember { mutableStateOf(0) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label
        ?: selectedValue
    Box(
        modifier = modifier
            .fillMaxHeight()
            .onSizeChanged { widthPx = it.width },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    enabled = options.isNotEmpty(),
                    role = Role.Button,
                    onClick = { expanded = true },
                )
                .semantics {
                    contentDescription = title
                    stateDescription = selectedLabel
                }
                .padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
            )
            Text(
                text = selectedLabel,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
                lineHeight = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(NgTheme.colors.onSurfaceVariant),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(density) { widthPx.toDp() }),
            shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
            containerColor = colorResource(R.color.ng_surface_card),
            tonalElevation = 0.dp,
            shadowElevation = NgTheme.effects.overlayElevationDp.dp,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    modifier = Modifier
                        .height(44.dp)
                        .semantics { selected = option.value == selectedValue },
                    text = {
                        Text(
                            text = option.label,
                            color = Color(NgTheme.colors.onSurface),
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option.value)
                    },
                )
            }
        }
    }
}

/** 六等分文字分页栏，选中项仅使用主题色文字与 2dp 下划线。 */
@Composable
fun NgEditorTextTabRow(
    titles: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(NgTheme.colors.background)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            titles.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Tab) { onSelected(index) }
                        .semantics { this.selected = selected },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        color = Color(
                            if (selected) NgTheme.colors.primary
                            else NgTheme.colors.onSurfaceVariant
                        ),
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Color(NgTheme.colors.primary)),
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            thickness = 0.6.dp,
            color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f),
        )
    }
}
