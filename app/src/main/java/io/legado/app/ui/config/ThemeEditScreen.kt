package io.legado.app.ui.config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.BookshelfFloatingDockConfig
import io.legado.app.help.config.BookshelfFloatingDockSearchPosition
import io.legado.app.help.config.BookshelfTopBarStyle
import io.legado.app.help.config.FloatingBottomBarConfig
import io.legado.app.help.config.NgManagedTheme
import io.legado.app.help.config.NgThemeBarProfile
import io.legado.app.help.config.NgThemeBackground
import io.legado.app.ui.design.components.compose.NgDockSlider
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.components.compose.NgFormGroup
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.theme.NgTheme
import java.io.File
import kotlin.math.roundToInt

@Composable
internal fun ThemeEditScreen(
    theme: NgManagedTheme,
    copyOnSave: Boolean,
    onThemeChanged: (NgManagedTheme) -> Unit,
    onEditBackground: (Boolean) -> Unit,
    onSelectDrawerImage: (Boolean) -> Unit,
) {
    var drawerExpanded by rememberSaveable { mutableStateOf(false) }
    ThemeColorConfigScreen(
        colors = theme.colors,
        onColorsChanged = { onThemeChanged(theme.copy(colors = it)) },
        compact = true,
        headerContent = {
            item(key = "theme-info") {
                NgFormGroup(title = stringResource(R.string.ng_theme_details)) {
                    ThemeNameInput(
                        value = theme.name,
                        onValueChange = { onThemeChanged(theme.copy(name = it)) },
                        isError = theme.name.isBlank()
                    )
                    if (copyOnSave) {
                        NgFormGroupDivider()
                        ThemeCompactSettingRow(
                            title = stringResource(R.string.ng_theme_built_in_copy),
                            summary = stringResource(R.string.ng_theme_built_in_copy_summary),
                            enabled = false,
                        )
                    }
                    NgFormGroupDivider()
                    ThemeBackgroundItem(
                        dark = false,
                        background = theme.lightBackground,
                        onClick = { onEditBackground(false) }
                    )
                    NgFormGroupDivider()
                    ThemeBackgroundItem(
                        dark = true,
                        background = theme.darkBackground,
                        onClick = { onEditBackground(true) }
                    )
                }
            }
            if (theme.effect != null && theme.effect.parameterSchema.isNotEmpty()) {
                item(key = "effect-parameters") {
                    NgFormGroup(title = stringResource(R.string.effect_theme_settings)) {
                        EffectThemeParameterEditor(
                            theme = theme,
                            values = theme.effectValues,
                            rangeHorizontalPadding = 14.dp,
                            onValuePreview = { key, value ->
                                onThemeChanged(theme.copy(effectValues = theme.effectValues + (key to value)))
                            },
                            onValueCommitted = {},
                        )
                    }
                }
            }
            item(key = "theme-drawers") {
                NgFormGroup(title = stringResource(R.string.ng_drawer_appearance)) {
                    ThemeCompactExpandableRow(
                        title = stringResource(R.string.ng_drawer_background_source),
                        summary = ngDrawerBackgroundSourceName(theme.drawerProfile?.source ?: "theme_color"),
                        expanded = drawerExpanded,
                        onExpandedChange = { drawerExpanded = it },
                    ) {
                        NgDrawerBackgroundSettingsContent(
                            profile = theme.drawerProfile,
                            colors = theme.colors,
                            onProfileChanged = { onThemeChanged(theme.copy(drawerProfile = it)) },
                            onProfileChangeFinished = {},
                            onSelectImage = onSelectDrawerImage,
                        )
                    }
                }
            }
            item(key = "theme-bars") {
                NgFormGroup(title = stringResource(R.string.ng_theme_bars)) {
                    ThemeBarProfileEditor(
                        profile = theme.barProfile ?: NgThemeBarProfile(),
                        onProfileChanged = {
                            onThemeChanged(theme.copy(barProfile = it))
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun ThemeBarProfileEditor(
    profile: NgThemeBarProfile,
    onProfileChanged: (NgThemeBarProfile) -> Unit
) {
    var bottomBarExpanded by rememberSaveable { mutableStateOf(false) }
    var topBarExpanded by rememberSaveable { mutableStateOf(false) }
    val useFloatingBottomBar = profile.useFloatingBottomBar ?: false
    val bottomDistancePx = profile.floatingBottomBarBottomDistancePx
        ?: NgThemeBarProfile.EDITOR_DEFAULT_BOTTOM_DISTANCE_PX
    val bottomTransparency = profile.floatingBottomBarTransparency
        ?: FloatingBottomBarConfig.DEFAULT_TRANSPARENCY_PERCENT
    val topBarStyle = BookshelfTopBarStyle.fromValue(
        profile.bookshelfTopBarStyle ?: BookshelfTopBarStyle.DEFAULT.value
    )
    val topDistancePx = profile.bookshelfFloatingDockTopDistancePx
        ?: NgThemeBarProfile.EDITOR_DEFAULT_TOP_DISTANCE_PX
    val topTransparency = profile.bookshelfFloatingDockTransparency
        ?: BookshelfFloatingDockConfig.DEFAULT_TRANSPARENCY_PERCENT
    val searchPosition = BookshelfFloatingDockSearchPosition.fromValue(
        profile.bookshelfFloatingDockSearchPosition
            ?: BookshelfFloatingDockSearchPosition.LEFT.value
    )

    ThemeCompactExpandableRow(
        title = stringResource(R.string.main_bottom_bar_style),
        summary = stringResource(
            if (useFloatingBottomBar) {
                R.string.floating_bottom_bar
            } else {
                R.string.traditional_bottom_bar
            }
        ),
        expanded = bottomBarExpanded,
        onExpandedChange = { bottomBarExpanded = it }
    ) {
        NgFloatingTabBar(
            items = listOf(
                NgFloatingTabSpec(
                    text = stringResource(R.string.traditional_bottom_bar),
                    iconRes = R.drawable.ic_bookshelf_top_bar_traditional
                ),
                NgFloatingTabSpec(
                    text = stringResource(R.string.floating_bottom_bar),
                    iconRes = R.drawable.ic_bookshelf_top_bar_floating
                )
            ),
            selectedIndex = if (useFloatingBottomBar) 1 else 0,
            onTabSelected = { index ->
                onProfileChanged(
                    profile.copy(
                        useFloatingBottomBar = index == 1,
                        floatingBottomBarBottomDistancePx =
                            profile.floatingBottomBarBottomDistancePx
                                ?: NgThemeBarProfile.EDITOR_DEFAULT_BOTTOM_DISTANCE_PX,
                        floatingBottomBarTransparency =
                            profile.floatingBottomBarTransparency
                                ?: FloatingBottomBarConfig.DEFAULT_TRANSPARENCY_PERCENT,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        AnimatedVisibility(visible = useFloatingBottomBar) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NgDockSlider(
                    title = stringResource(R.string.floating_bottom_bar_bottom_distance),
                    valueText = stringResource(
                        R.string.bookshelf_floating_dock_top_distance_value,
                        bottomDistancePx
                    ),
                    minimumText = stringResource(
                        R.string.bookshelf_floating_dock_top_distance_value,
                        FloatingBottomBarConfig.MIN_BOTTOM_DISTANCE_PX
                    ),
                    maximumText = stringResource(
                        R.string.bookshelf_floating_dock_top_distance_value,
                        FloatingBottomBarConfig.MAX_BOTTOM_DISTANCE_PX
                    ),
                    value = bottomDistancePx.toFloat(),
                    valueRange = FloatingBottomBarConfig.MIN_BOTTOM_DISTANCE_PX.toFloat()..
                        FloatingBottomBarConfig.MAX_BOTTOM_DISTANCE_PX.toFloat(),
                    steps = FloatingBottomBarConfig.BOTTOM_DISTANCE_SLIDER_STEPS,
                    showBoundLabels = false,
                    onValueChange = { value ->
                        onProfileChanged(
                            profile.copy(
                                floatingBottomBarBottomDistancePx = value.roundToInt()
                            )
                        )
                    },
                    onValueChangeFinished = {}
                )
                NgDockSlider(
                    title = stringResource(R.string.floating_bottom_bar_transparency),
                    valueText = stringResource(
                        R.string.bookshelf_floating_dock_transparency_value,
                        bottomTransparency
                    ),
                    minimumText = stringResource(
                        R.string.bookshelf_floating_dock_transparency_value,
                        FloatingBottomBarConfig.MIN_TRANSPARENCY_PERCENT
                    ),
                    maximumText = stringResource(
                        R.string.bookshelf_floating_dock_transparency_value,
                        FloatingBottomBarConfig.MAX_TRANSPARENCY_PERCENT
                    ),
                    value = bottomTransparency.toFloat(),
                    valueRange = FloatingBottomBarConfig.MIN_TRANSPARENCY_PERCENT.toFloat()..
                        FloatingBottomBarConfig.MAX_TRANSPARENCY_PERCENT.toFloat(),
                    showBoundLabels = false,
                    onValueChange = { value ->
                        onProfileChanged(
                            profile.copy(floatingBottomBarTransparency = value.roundToInt())
                        )
                    },
                    onValueChangeFinished = {}
                )
            }
        }
    }

    NgFormGroupDivider()
    ThemeCompactExpandableRow(
        title = stringResource(R.string.bookshelf_top_bar_style),
        summary = stringResource(
            when (topBarStyle) {
                BookshelfTopBarStyle.COMPACT_TOOLBAR ->
                    R.string.bookshelf_top_bar_compact_toolbar
                BookshelfTopBarStyle.GROUP_NAVIGATION ->
                    R.string.bookshelf_top_bar_group_navigation
            }
        ),
        expanded = topBarExpanded,
        onExpandedChange = { topBarExpanded = it }
    ) {
        NgFloatingTabBar(
            items = listOf(
                NgFloatingTabSpec(
                    text = stringResource(R.string.bookshelf_top_bar_compact_toolbar),
                    iconRes = R.drawable.ic_bookshelf_top_bar_traditional
                ),
                NgFloatingTabSpec(
                    text = stringResource(R.string.bookshelf_top_bar_group_navigation),
                    iconRes = R.drawable.ic_bookshelf_top_bar_floating
                )
            ),
            selectedIndex = BookshelfTopBarStyle.entries.indexOf(topBarStyle),
            onTabSelected = { index ->
                onProfileChanged(
                    profile.copy(
                        bookshelfTopBarStyle = BookshelfTopBarStyle.entries[index].value,
                        bookshelfFloatingDockTopDistancePx =
                            profile.bookshelfFloatingDockTopDistancePx
                                ?: NgThemeBarProfile.EDITOR_DEFAULT_TOP_DISTANCE_PX,
                        bookshelfFloatingDockTransparency =
                            profile.bookshelfFloatingDockTransparency
                                ?: BookshelfFloatingDockConfig.DEFAULT_TRANSPARENCY_PERCENT,
                        bookshelfFloatingDockSearchPosition =
                            profile.bookshelfFloatingDockSearchPosition
                                ?: BookshelfFloatingDockSearchPosition.LEFT.value,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeCompactSettingRow(
                title = stringResource(R.string.bookshelf_floating_dock_search_position),
                trailingContent = {
                    NgFloatingTabBar(
                        items = listOf(
                            NgFloatingTabSpec(text = stringResource(R.string.left)),
                            NgFloatingTabSpec(text = stringResource(R.string.right))
                        ),
                        selectedIndex =
                            BookshelfFloatingDockSearchPosition.entries.indexOf(searchPosition),
                        onTabSelected = { index ->
                            onProfileChanged(
                                profile.copy(
                                    bookshelfFloatingDockSearchPosition =
                                        BookshelfFloatingDockSearchPosition.entries[index].value
                                )
                            )
                        },
                        modifier = Modifier.width(132.dp)
                    )
                }
            )
            NgDockSlider(
                title = stringResource(R.string.bookshelf_floating_dock_top_distance),
                    valueText = stringResource(
                        R.string.bookshelf_floating_dock_top_distance_value,
                        topDistancePx
                    ),
                    minimumText = stringResource(
                        R.string.bookshelf_floating_dock_top_distance_value,
                        BookshelfFloatingDockConfig.MIN_TOP_DISTANCE_PX
                    ),
                    maximumText = stringResource(
                        R.string.bookshelf_floating_dock_top_distance_value,
                        BookshelfFloatingDockConfig.MAX_TOP_DISTANCE_PX
                    ),
                    value = topDistancePx.toFloat(),
                    valueRange = BookshelfFloatingDockConfig.MIN_TOP_DISTANCE_PX.toFloat()..
                        BookshelfFloatingDockConfig.MAX_TOP_DISTANCE_PX.toFloat(),
                    steps = BookshelfFloatingDockConfig.TOP_DISTANCE_SLIDER_STEPS,
                    showBoundLabels = false,
                    onValueChange = { value ->
                        onProfileChanged(
                            profile.copy(
                                bookshelfFloatingDockTopDistancePx = value.roundToInt()
                            )
                        )
                    },
                    onValueChangeFinished = {}
            )
            NgDockSlider(
                title = stringResource(R.string.bookshelf_floating_dock_transparency),
                    valueText = stringResource(
                        R.string.bookshelf_floating_dock_transparency_value,
                        topTransparency
                    ),
                    minimumText = stringResource(
                        R.string.bookshelf_floating_dock_transparency_value,
                        BookshelfFloatingDockConfig.MIN_TRANSPARENCY_PERCENT
                    ),
                    maximumText = stringResource(
                        R.string.bookshelf_floating_dock_transparency_value,
                        BookshelfFloatingDockConfig.MAX_TRANSPARENCY_PERCENT
                    ),
                    value = topTransparency.toFloat(),
                    valueRange = BookshelfFloatingDockConfig.MIN_TRANSPARENCY_PERCENT.toFloat()..
                        BookshelfFloatingDockConfig.MAX_TRANSPARENCY_PERCENT.toFloat(),
                    showBoundLabels = false,
                    onValueChange = { value ->
                        onProfileChanged(
                            profile.copy(
                                bookshelfFloatingDockTransparency = value.roundToInt()
                            )
                        )
                    },
                    onValueChangeFinished = {}
            )
        }
    }
}

@Composable
private fun ThemeCompactExpandableRow(
    title: String,
    summary: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "ThemeCompactExpandableArrow",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .heightIn(min = 52.dp)
                .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = title,
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_20),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(arrowRotation),
                tint = Color(NgTheme.colors.onSurfaceVariant),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun ThemeCompactSettingRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val contentAlpha = if (enabled) 1f else 0.42f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .heightIn(min = if (summary.isNullOrBlank()) 44.dp else 52.dp)
            .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = title,
                color = Color(NgTheme.colors.onSurface).copy(alpha = contentAlpha),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    color = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = contentAlpha),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailingContent()
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_20),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = contentAlpha),
            )
        }
    }
}

@Composable
private fun ThemeNameInput(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    val shape = RoundedCornerShape(10.dp)
    val colors = NgTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = NgTheme.fontFamily,
            color = Color(colors.onSurface),
            fontSize = 16.sp,
            lineHeight = 20.sp
        ),
        cursorBrush = SolidColor(Color(colors.primary)),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .then(
                        if (isError) {
                            Modifier.border(1.2.dp, Color(colors.error), shape)
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                innerTextField()
            }
        }
    )
}

@Composable
private fun ThemeBackgroundItem(
    dark: Boolean,
    background: NgThemeBackground,
    onClick: () -> Unit
) {
    val path = background.path
    val source = when {
        path.isNullOrBlank() -> stringResource(R.string.ng_theme_background_none)
        path.startsWith("asset://") -> path.substringAfterLast('/')
        else -> File(path).name.ifBlank { path }
    }
    ThemeCompactSettingRow(
        title = stringResource(
            if (dark) R.string.ng_theme_dark_background else R.string.ng_theme_light_background
        ),
        summary = stringResource(R.string.ng_theme_background_summary, source, background.blur),
        onClick = onClick
    )
}
