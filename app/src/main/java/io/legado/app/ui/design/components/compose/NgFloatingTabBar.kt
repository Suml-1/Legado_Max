package io.legado.app.ui.design.components.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgDockSelectionColors
import io.legado.app.ui.design.theme.NgTheme

data class NgFloatingTabSpec(
    val text: String? = null,
    @param:DrawableRes val iconRes: Int? = null,
    val iconVector: ImageVector? = null,
    val count: Int? = null,
    val contentDescription: String? = text
)

enum class NgFloatingTabBarVariant {
    STANDARD,
    SOLID_LIGHT_CONTENT,
    SOLID_WHITE_CONTENT,
    MANGA_SETTINGS,
    /** 短抽屉中的双模式切换：白色选中项、强调色文字。 */
    DRAWER_SELECTION,
    /** 外观模式单选：图标和文字横排，沿用标准 Dock 的选中填充。 */
    APPEARANCE_SELECTION,
}
enum class NgFloatingTabBarSize { STANDARD, COMPACT }

/** 与 View 版 NgFloatingTabBar 对齐的 48dp 等宽悬浮 Dock。 */
@Composable
fun NgFloatingTabBar(
    items: List<NgFloatingTabSpec>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    variant: NgFloatingTabBarVariant = NgFloatingTabBarVariant.STANDARD,
    size: NgFloatingTabBarSize = NgFloatingTabBarSize.STANDARD,
    enabled: Boolean = true,
    selectionColors: NgDockSelectionColors? = null,
    semanticRole: Role? = null,
) {
    items.forEach { item ->
        require(item.text != null || !item.contentDescription.isNullOrBlank()) {
            "Icon-only NgFloatingTabSpec requires a contentDescription"
        }
    }
    val drawerSelection = variant == NgFloatingTabBarVariant.DRAWER_SELECTION
    val appearanceSelection = variant == NgFloatingTabBarVariant.APPEARANCE_SELECTION
    val outerShape = RoundedCornerShape(if (drawerSelection) 14.dp else 12.dp)
    val height = if (drawerSelection) 43.dp
        else if (size == NgFloatingTabBarSize.COMPACT) 40.dp else 48.dp
    val tabs: @Composable RowScope.() -> Unit = {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex.coerceIn(items.indices)
            val contentColor = when {
                drawerSelection && !selected -> Color(NgTheme.colors.onSurfaceVariant)
                !selected -> Color(NgTheme.colors.onSurface)
                selectionColors != null && (variant == NgFloatingTabBarVariant.STANDARD || appearanceSelection) ->
                    Color(selectionColors.content)
                variant == NgFloatingTabBarVariant.MANGA_SETTINGS -> Color(NgTheme.colors.onPrimary)
                variant == NgFloatingTabBarVariant.SOLID_LIGHT_CONTENT ||
                    variant == NgFloatingTabBarVariant.SOLID_WHITE_CONTENT -> Color.White
                else -> Color(NgTheme.colors.primary)
            }
            val selectedContainerColor = when (variant) {
                NgFloatingTabBarVariant.STANDARD -> Color(selectionColors?.container ?: NgTheme.colors.selectedContainer)
                NgFloatingTabBarVariant.APPEARANCE_SELECTION ->
                    Color(selectionColors?.container ?: NgTheme.colors.selectedContainer)
                NgFloatingTabBarVariant.DRAWER_SELECTION -> ngDrawerContentCardColor()
                NgFloatingTabBarVariant.SOLID_LIGHT_CONTENT,
                NgFloatingTabBarVariant.SOLID_WHITE_CONTENT,
                NgFloatingTabBarVariant.MANGA_SETTINGS -> Color(NgTheme.colors.primary)
            }
            if (appearanceSelection) {
                NgAppearanceSelectionTab(
                    item = item,
                    selected = selected,
                    enabled = enabled,
                    contentColor = contentColor,
                    selectedContainerColor = selectedContainerColor,
                    role = semanticRole ?: Role.RadioButton,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            } else Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(if (drawerSelection) 11.dp else 10.dp))
                    .then(
                        if (selected) {
                            Modifier.background(selectedContainerColor)
                        } else {
                            Modifier
                        }
                    )
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = semanticRole ?: if (variant == NgFloatingTabBarVariant.MANGA_SETTINGS) Role.RadioButton else Role.Tab,
                        onClick = { onTabSelected(index) }
                    )
                    .semantics(mergeDescendants = true) {
                        item.contentDescription?.let { contentDescription = it }
                    }
                    .padding(horizontal = if (variant == NgFloatingTabBarVariant.MANGA_SETTINGS) 0.dp else 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    item.iconVector != null -> Icon(
                        imageVector = item.iconVector,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = contentColor
                    )

                    item.iconRes != null -> Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = contentColor
                    )
                }
                val label = when {
                    item.count != null && !item.text.isNullOrEmpty() ->
                        "${item.text}\n${item.count}"
                    item.count != null -> item.count.toString()
                    else -> item.text.orEmpty()
                }
                if (label.isNotEmpty()) {
                    if (item.iconRes != null || item.iconVector != null) {
                        androidx.compose.foundation.layout.Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        text = label,
                        color = contentColor,
                        fontSize = if (drawerSelection || variant == NgFloatingTabBarVariant.MANGA_SETTINGS) 14.sp else 13.sp,
                        lineHeight = if (variant == NgFloatingTabBarVariant.MANGA_SETTINGS) TextUnit.Unspecified else 15.sp,
                        fontWeight = if (drawerSelection && selected) FontWeight.SemiBold
                            else if (variant == NgFloatingTabBarVariant.MANGA_SETTINGS) null
                            else if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = if (variant == NgFloatingTabBarVariant.MANGA_SETTINGS) TextAlign.Unspecified else TextAlign.Center,
                        maxLines = if (item.count != null && !item.text.isNullOrEmpty()) 2 else 1,
                        overflow = if (variant == NgFloatingTabBarVariant.MANGA_SETTINGS) TextOverflow.Clip else TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    when (variant) {
        NgFloatingTabBarVariant.APPEARANCE_SELECTION -> NgSettingsCardSurface(
            modifier = modifier.fillMaxWidth(),
            cornerRadius = 12.dp,
            role = NgMaterialRole.CONTROL,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(2.dp)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
                content = tabs,
            )
        }

        NgFloatingTabBarVariant.STANDARD -> NgSettingsCardSurface(
            modifier = modifier
                .fillMaxWidth()
                .height(height),
            cornerRadius = 12.dp,
            shape = outerShape,
            role = NgMaterialRole.CONTROL,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
                content = tabs,
            )
        }

        NgFloatingTabBarVariant.MANGA_SETTINGS,
        NgFloatingTabBarVariant.DRAWER_SELECTION -> Row(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(outerShape)
                .background(Color(NgTheme.colors.onSurface).copy(alpha = 0.06f))
                .padding(if (drawerSelection) 4.dp else 3.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            content = tabs,
        )

        NgFloatingTabBarVariant.SOLID_LIGHT_CONTENT,
        NgFloatingTabBarVariant.SOLID_WHITE_CONTENT -> Row(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(outerShape)
                .background(
                    colorResource(
                        if (variant == NgFloatingTabBarVariant.SOLID_WHITE_CONTENT) {
                            R.color.ng_surface_card
                        } else {
                            R.color.ng_neutral_container
                        }
                    )
                )
                .border(
                    0.6.dp,
                    colorResource(R.color.ng_settings_item_stroke),
                    outerShape,
                )
                .padding(3.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            content = tabs,
        )
    }
}

@Composable
private fun NgAppearanceSelectionTab(
    item: NgFloatingTabSpec,
    selected: Boolean,
    enabled: Boolean,
    contentColor: Color,
    selectedContainerColor: Color,
    role: Role,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .then(if (selected) Modifier.background(selectedContainerColor) else Modifier)
            .selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
            .semantics(mergeDescendants = true) {
                item.contentDescription?.let { contentDescription = it }
            }
            .heightIn(min = 40.dp)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            item.iconVector != null -> Icon(item.iconVector, null, Modifier.size(20.dp), tint = contentColor)
            item.iconRes != null -> Icon(painterResource(item.iconRes), null, Modifier.size(20.dp), tint = contentColor)
        }
        val label = when {
            item.count != null && !item.text.isNullOrEmpty() -> "${item.text}\n${item.count}"
            item.count != null -> item.count.toString()
            else -> item.text.orEmpty()
        }
        if (label.isNotEmpty()) {
            if (item.iconVector != null || item.iconRes != null) Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
