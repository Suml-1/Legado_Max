package io.legado.app.ui.config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.NgSoftGradientColorMode
import io.legado.app.help.config.NgSoftGradientLightFieldPreset
import io.legado.app.help.config.NgSoftGradientTheme
import io.legado.app.help.config.NgThemeModeGroup
import io.legado.app.help.config.NgThemePresentationMode
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgCompactSettingsDivider
import io.legado.app.ui.design.components.compose.NgCompactSettingsItem
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.components.compose.NgFloatingTabBarVariant
import io.legado.app.ui.design.theme.NgDockSelectionColors
import io.legado.app.ui.design.theme.NgTheme

/** Only this appearance form owns these controls; shared settings defaults stay unchanged. */
@Composable
internal fun ThemeModeGroupSetting(
    selected: NgThemeModeGroup,
    onSelected: (NgThemeModeGroup) -> Unit,
    selectionColors: NgDockSelectionColors,
) {
    val groups = NgThemeModeGroup.entries
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.theme_mode),
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 6.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Normal,
        )
        NgFloatingTabBar(
            items = groups.map { group ->
                NgFloatingTabSpec(
                    text = stringResource(group.labelRes()),
                    iconVector = if (group == NgThemeModeGroup.STANDARD) Icons.Rounded.Palette else Icons.Rounded.GridView,
                )
            },
            selectedIndex = groups.indexOf(selected),
            onTabSelected = { onSelected(groups[it]) },
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
            variant = NgFloatingTabBarVariant.APPEARANCE_SELECTION,
            selectionColors = selectionColors,
        )
    }
}

@Composable
internal fun ThemeInternalModeChoices(
    state: ThemeConfigScreenState,
    onSelected: (NgThemePresentationMode) -> Unit,
) {
    val softGradient = remember(
        state.softGradientColorMode, state.softGradientColor,
        state.softGradientCustomColor, state.softGradientLightField,
    ) {
        val profile = if (state.softGradientColorMode == NgSoftGradientColorMode.CUSTOM) {
            NgSoftGradientTheme.customGradient(state.softGradientCustomColor, state.softGradientLightField)
        } else {
            NgSoftGradientTheme.gradient(state.softGradientColor, state.softGradientLightField)
        }
        profile
    }
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        INTERNAL_MODES.forEach { mode ->
            val selected = state.internalThemeMode == mode
            Column(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .selectable(selected, role = Role.RadioButton, onClick = { onSelected(mode) })
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.widthIn(max = 112.dp).fillMaxWidth().aspectRatio(1.4f)
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (selected) Modifier.border(
                            1.5.dp, Color(NgTheme.colors.primary), RoundedCornerShape(12.dp),
                        ) else Modifier),
                ) {
                    ThemeAppearancePreview(mode, softGradient.colors, Modifier.fillMaxSize())
                }
                Text(
                    text = stringResource(mode.labelRes()),
                    modifier = Modifier.padding(top = 7.dp),
                    color = Color(if (selected) NgTheme.colors.primary else NgTheme.colors.onSurface),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun ThemeDisplayModeSetting(
    selectedMode: String,
    onSelected: (String) -> Unit,
    selectionColors: NgDockSelectionColors,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.ng_theme_display_mode),
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 6.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Normal,
        )
        NgFloatingTabBar(
            items = DISPLAY_MODES.map { mode ->
                val label = stringResource(when (mode) {
                    "1" -> R.string.theme_mode_day_short
                    "2" -> R.string.theme_mode_night_short
                    else -> R.string.theme_mode_follow_short
                })
                NgFloatingTabSpec(
                    text = label,
                    iconVector = when (mode) {
                        "1" -> Icons.Rounded.LightMode
                        "2" -> Icons.Rounded.DarkMode
                        else -> Icons.Rounded.Contrast
                    },
                    contentDescription = if (mode == "0") stringResource(R.string.flow_sys) else label,
                )
            },
            selectedIndex = DISPLAY_MODES.indexOf(selectedMode),
            onTabSelected = { onSelected(DISPLAY_MODES[it]) },
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
            variant = NgFloatingTabBarVariant.APPEARANCE_SELECTION,
            selectionColors = selectionColors,
        )
    }
}

@Composable
internal fun ThemeBackgroundSetting(
    daySummary: String,
    nightSummary: String,
    onOpenDay: () -> Unit,
    onOpenNight: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ThemeValueSetting(
        title = stringResource(R.string.ng_theme_custom_background), value = "",
        expanded = expanded, onClick = { expanded = !expanded },
    )
    AnimatedVisibility(expanded) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
            val stacked = maxWidth < 280.dp || LocalDensity.current.fontScale > 1.2f
            val entry: @Composable (Boolean, Modifier) -> Unit = { dark, modifier ->
                val label = stringResource(if (dark) R.string.ng_theme_dark_background else R.string.ng_theme_light_background)
                val summary = if (dark) nightSummary else daySummary
                Row(
                    modifier = modifier.clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = if (dark) onOpenNight else onOpenDay)
                        .semantics(mergeDescendants = true) { contentDescription = "$label, $summary" }
                        .heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(if (dark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        null, Modifier.size(20.dp), tint = Color(NgTheme.colors.onSurface))
                    Text(label,
                        modifier = Modifier.weight(1f), color = Color(NgTheme.colors.onSurface),
                        fontSize = 13.sp, lineHeight = 17.sp)
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = Color(NgTheme.colors.onSurfaceVariant))
                }
            }
            val frame = Modifier.fillMaxWidth().border(0.6.dp,
                Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f), RoundedCornerShape(12.dp))
            if (stacked) {
                Column(frame) {
                    entry(false, Modifier.fillMaxWidth())
                    NgCompactSettingsDivider()
                    entry(true, Modifier.fillMaxWidth())
                }
            } else {
                Row(frame, verticalAlignment = Alignment.CenterVertically) {
                    entry(false, Modifier.weight(1f))
                    Box(Modifier.width(0.6.dp).height(24.dp).background(Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f)))
                    entry(true, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun ThemeSoftGradientSettings(
    state: ThemeConfigScreenState,
    onLightFieldSelected: (NgSoftGradientLightFieldPreset) -> Unit,
    onOpenColor: () -> Unit,
    selectionColors: NgDockSelectionColors,
) {
    val currentColor = if (state.softGradientColorMode == NgSoftGradientColorMode.CUSTOM) {
        state.softGradientCustomColor
    } else {
        NgSoftGradientTheme.colors(state.softGradientColor).manualLight.primary
    }
    NgCompactSettingsItem(
        title = stringResource(R.string.ng_soft_gradient_color),
        trailing = NgSettingsTrailing.CUSTOM,
        onClick = onOpenColor,
        customTrailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(14.dp).background(Color(currentColor), CircleShape))
                Text(
                    text = stringResource(if (state.softGradientColorMode == NgSoftGradientColorMode.CUSTOM)
                        R.string.ng_soft_gradient_color_custom_tab else state.softGradientColor.labelRes()),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = Color(NgTheme.colors.onSurfaceVariant))
            }
        },
    )
    NgCompactSettingsDivider()
    Text(
        text = stringResource(R.string.ng_soft_gradient_light_field),
        modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 6.dp),
        color = Color(NgTheme.colors.onSurface),
        fontSize = 15.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
    )
    NgFloatingTabBar(
        items = NgSoftGradientLightFieldPreset.entries.map { preset ->
            NgFloatingTabSpec(text = stringResource(preset.labelRes()))
        },
        selectedIndex = NgSoftGradientLightFieldPreset.entries.indexOf(state.softGradientLightField),
        onTabSelected = { onLightFieldSelected(NgSoftGradientLightFieldPreset.entries[it]) },
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
        selectionColors = selectionColors,
    )
}

@Composable
private fun ThemeValueSetting(
    title: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
    swatch: Int? = null,
) {
    NgCompactSettingsItem(
        title = title, trailing = NgSettingsTrailing.CUSTOM, onClick = onClick,
        customTrailing = {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (swatch != null) Box(Modifier.size(14.dp).background(Color(swatch), CircleShape))
                    if (value.isNotEmpty()) Text(value, color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        null, Modifier.size(20.dp), tint = Color(NgTheme.colors.onSurfaceVariant))
                }
            }
        },
    )
}

private fun NgThemeModeGroup.labelRes(): Int = when (this) {
    NgThemeModeGroup.STANDARD -> R.string.ng_theme_mode_standard_short
    NgThemeModeGroup.INTERNAL -> R.string.ng_theme_mode_internal_short
}

private fun NgThemePresentationMode.labelRes(): Int = when (this) {
    NgThemePresentationMode.MULTICOLOR -> R.string.ng_theme_mode_multicolor
    NgThemePresentationMode.SOFT_GRADIENT -> R.string.ng_theme_mode_soft_gradient
    else -> R.string.theme_mode_eink_short
}

private val INTERNAL_MODES = listOf(
    NgThemePresentationMode.MULTICOLOR,
    NgThemePresentationMode.SOFT_GRADIENT,
    NgThemePresentationMode.EINK,
)
private val DISPLAY_MODES = listOf("0", "1", "2")
