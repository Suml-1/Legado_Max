package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.BookshelfFloatingDockConfig
import io.legado.app.help.config.BookshelfFloatingDockSearchPosition
import io.legado.app.help.config.BookshelfTopBarStyle
import io.legado.app.help.config.FloatingBottomBarConfig
import io.legado.app.help.config.NgEffectThemeSelection
import io.legado.app.help.config.NgThemeDrawerProfile
import io.legado.app.help.config.NgInterfaceFontStore
import io.legado.app.help.config.NgSoftGradientColorMode
import io.legado.app.help.config.NgSoftGradientColorPreset
import io.legado.app.help.config.NgSoftGradientLightFieldPreset
import io.legado.app.help.config.NgSoftGradientTheme
import io.legado.app.help.config.NgMulticolorTheme
import io.legado.app.help.config.NgThemeModeGroup
import io.legado.app.help.config.NgThemePresentationMode
import io.legado.app.help.config.NgVisualSystem
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgDockSlider
import io.legado.app.ui.design.components.compose.NgCompactSettingsGroup
import io.legado.app.ui.design.components.compose.NgCompactSettingsItem
import io.legado.app.ui.design.components.compose.NgCompactSettingsDivider
import io.legado.app.ui.design.components.compose.NgExpandableSettingsItem
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.components.compose.NgLauncherIcon
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsSectionLabel
import io.legado.app.help.config.NgColorConfigStore
import io.legado.app.ui.design.theme.rememberAppearanceDockSelectionColors
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

internal data class ThemeConfigScreenState(
    val themeModeGroup: NgThemeModeGroup = NgThemeModeGroup.STANDARD,
    val presentationMode: NgThemePresentationMode = NgThemePresentationMode.STANDARD,
    val standardThemeMode: String = "0",
    val internalThemeMode: NgThemePresentationMode =
        NgThemePresentationMode.SOFT_GRADIENT,
    val softGradientColorMode: NgSoftGradientColorMode = NgSoftGradientColorMode.PRESET,
    val softGradientColor: NgSoftGradientColorPreset = NgSoftGradientColorPreset.CLEAR_BLUE,
    val softGradientCustomColor: Int = NgSoftGradientTheme.defaultCustomColor,
    val softGradientLightField: NgSoftGradientLightFieldPreset =
        NgSoftGradientLightFieldPreset.BALANCED,
    val effectThemeName: String? = null,
    val installedThemeCount: Int = 0,
    val visualSystem: NgVisualSystem = NgVisualSystem.DEFAULT,
    val showLauncherIcon: Boolean = true,
    @param:DrawableRes val launcherIconRes: Int = R.mipmap.ic_launcher,
    val floatingBottomBar: Boolean = false,
    val floatingBottomBarBottomDistancePx: Int = 0,
    val floatingBottomBarTransparency: Int =
        FloatingBottomBarConfig.DEFAULT_TRANSPARENCY_PERCENT,
    val drawerProfile: NgThemeDrawerProfile = NgThemeDrawerProfile(),
    val bookshelfTopBarStyle: BookshelfTopBarStyle = BookshelfTopBarStyle.DEFAULT,
    val bookshelfFloatingDockMinTopDistancePx: Int = 0,
    val bookshelfFloatingDockTopDistancePx: Int = 0,
    val bookshelfFloatingDockTransparency: Int =
        BookshelfFloatingDockConfig.DEFAULT_TRANSPARENCY_PERCENT,
    val bookshelfFloatingDockSearchPosition: BookshelfFloatingDockSearchPosition =
        BookshelfFloatingDockSearchPosition.LEFT,
    val hideSystemNavigationBar: Boolean = true,
    val autoRefresh: Boolean = false,
    val onlyUpdateRead: Boolean = false,
    val defaultToRead: Boolean = false,
    val showHome: Boolean = true,
    val showDiscovery: Boolean = true,
    val showRss: Boolean = true,
    val defaultHomePage: String = "home",
    val fontScaleSummary: String = "",
    val dayBackgroundSummary: String = "",
    val nightBackgroundSummary: String = ""
)

internal enum class ThemeConfigSection {
    ALL,
    APPEARANCE,
    INTERFACE
}

@Composable
internal fun ThemeConfigScreen(
    state: ThemeConfigScreenState,
    section: ThemeConfigSection,
    onThemeModeGroupSelected: (NgThemeModeGroup) -> Unit,
    onStandardThemeModeSelected: (String) -> Unit,
    onInternalThemeModeSelected: (NgThemePresentationMode) -> Unit,
    onSoftGradientColorSelected: (NgSoftGradientColorPreset) -> Unit,
    onSoftGradientCustomColorSelected: (Int) -> Unit,
    onSoftGradientLightFieldSelected: (NgSoftGradientLightFieldPreset) -> Unit,
    onVisualSystemSelected: (NgVisualSystem) -> Unit,
    onLauncherIconClick: () -> Unit,
    onFloatingBottomBarChanged: (Boolean) -> Unit,
    onFloatingBottomBarBottomDistanceChanged: (Int) -> Unit,
    onFloatingBottomBarBottomDistanceChangeFinished: () -> Unit,
    onFloatingBottomBarTransparencyChanged: (Int) -> Unit,
    onFloatingBottomBarTransparencyChangeFinished: () -> Unit,
    onDrawerProfileChanged: (NgThemeDrawerProfile) -> Unit,
    onDrawerProfileChangeFinished: () -> Unit,
    onSelectDrawerImage: (Boolean) -> Unit,
    onBookshelfTopBarStyleSelected: (BookshelfTopBarStyle) -> Unit,
    onBookshelfFloatingDockTopDistanceChanged: (Int) -> Unit,
    onBookshelfFloatingDockTopDistanceChangeFinished: () -> Unit,
    onBookshelfFloatingDockTransparencyChanged: (Int) -> Unit,
    onBookshelfFloatingDockTransparencyChangeFinished: () -> Unit,
    onBookshelfFloatingDockSearchPositionSelected:
        (BookshelfFloatingDockSearchPosition) -> Unit,
    onHideSystemNavigationBarChanged: (Boolean) -> Unit,
    onAutoRefreshChanged: (Boolean) -> Unit,
    onOnlyUpdateReadChanged: (Boolean) -> Unit,
    onDefaultToReadChanged: (Boolean) -> Unit,
    onShowHomeChanged: (Boolean) -> Unit,
    onShowDiscoveryChanged: (Boolean) -> Unit,
    onShowRssChanged: (Boolean) -> Unit,
    onDefaultHomePageSelected: (String) -> Unit,
    onOpenCustomColors: () -> Unit,
    onOpenFontScale: () -> Unit,
    onOpenCoverConfig: () -> Unit,
    onOpenThemeManager: () -> Unit,
    onOpenDayBackground: () -> Unit,
    onOpenNightBackground: () -> Unit
) {
    val context = LocalContext.current
    val drawerColors = when (state.presentationMode) {
        NgThemePresentationMode.SOFT_GRADIENT -> NgSoftGradientTheme.colors(context)
        NgThemePresentationMode.MULTICOLOR -> NgMulticolorTheme.colors
        NgThemePresentationMode.EFFECT_THEME -> NgEffectThemeSelection.colors(context)
        else -> NgColorConfigStore.current(context)
    }
    val showAppearance = section != ThemeConfigSection.INTERFACE
    val showInterface = section != ThemeConfigSection.APPEARANCE
    val dockSelectionColors = rememberAppearanceDockSelectionColors(state.presentationMode)
    var visualSystemExpanded by rememberSaveable { mutableStateOf(false) }
    var bottomBarExpanded by rememberSaveable { mutableStateOf(false) }
    var drawerAppearanceExpanded by rememberSaveable { mutableStateOf(false) }
    var bookshelfTopBarExpanded by rememberSaveable { mutableStateOf(false) }
    var showSoftGradientColorSheet by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        if (showAppearance) {
            NgCompactSettingsGroup {
                ThemeModeGroupSetting(state.themeModeGroup, onThemeModeGroupSelected, dockSelectionColors)
                NgCompactSettingsDivider()
                if (state.themeModeGroup == NgThemeModeGroup.INTERNAL) {
                    ThemeInternalModeChoices(state, onInternalThemeModeSelected)
                    if (state.internalThemeMode == NgThemePresentationMode.SOFT_GRADIENT) {
                        NgCompactSettingsDivider()
                        ThemeSoftGradientSettings(
                            state = state,
                            onLightFieldSelected = onSoftGradientLightFieldSelected,
                            onOpenColor = { showSoftGradientColorSheet = true },
                            selectionColors = dockSelectionColors,
                        )
                    }
                } else {
                    ThemeDisplayModeSetting(state.standardThemeMode, onStandardThemeModeSelected, dockSelectionColors)
                    NgCompactSettingsDivider()
                    NgCompactSettingsItem(
                        title = stringResource(R.string.theme_list),
                        summary = stringResource(R.string.ng_theme_installed_count, state.installedThemeCount),
                        onClick = onOpenThemeManager,
                    )
                    NgCompactSettingsDivider()
                    NgCompactSettingsItem(
                        title = stringResource(R.string.ng_custom_colors),
                        summary = stringResource(R.string.ng_theme_day_night_colors),
                        onClick = onOpenCustomColors,
                    )
                    if (state.presentationMode != NgThemePresentationMode.EFFECT_THEME) {
                        NgCompactSettingsDivider()
                        ThemeBackgroundSetting(
                            daySummary = state.dayBackgroundSummary,
                            nightSummary = state.nightBackgroundSummary,
                            onOpenDay = onOpenDayBackground,
                            onOpenNight = onOpenNightBackground,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            NgSettingsSectionLabel(stringResource(R.string.ng_appearance_common))
            NgCompactSettingsGroup {
                if (state.presentationMode != NgThemePresentationMode.EINK) {
                    NgCompactSettingsItem(
                        title = stringResource(R.string.ng_visual_system),
                        summary = stringResource(state.visualSystem.labelRes()),
                        onClick = { visualSystemExpanded = !visualSystemExpanded },
                    )
                    if (visualSystemExpanded) {
                        NgFloatingTabBar(
                            items = listOf(
                                NgFloatingTabSpec(text = stringResource(R.string.ng_visual_system_transparent_glass)),
                                NgFloatingTabSpec(text = stringResource(R.string.ng_visual_system_liquid_glass)),
                            ),
                            selectedIndex = NgVisualSystem.entries.indexOf(state.visualSystem),
                            onTabSelected = { onVisualSystemSelected(NgVisualSystem.entries[it]) },
                            selectionColors = dockSelectionColors,
                            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                        )
                    }
                    NgCompactSettingsDivider()
                }
                if (state.showLauncherIcon) {
                    NgCompactSettingsItem(
                        title = stringResource(R.string.change_icon),
                        summary = stringResource(R.string.change_icon_summary),
                        trailing = NgSettingsTrailing.CUSTOM,
                        onClick = onLauncherIconClick,
                        customTrailing = {
                            LauncherIconPreview(state.launcherIconRes, stringResource(R.string.change_icon))
                        },
                    )
                    NgCompactSettingsDivider()
                }
                NgCompactSettingsItem(
                    title = stringResource(R.string.font_scale),
                    summary = state.fontScaleSummary,
                    onClick = onOpenFontScale,
                )
                NgCompactSettingsDivider()
                val interfaceFontRevision by NgInterfaceFontStore.revision.collectAsState()
                val fontChoice = NgInterfaceFontStore.choice(context)
                val fontName = NgInterfaceFontStore.name(context)
                val systemFontIndex = NgInterfaceFontStore.systemChoices.indexOf(fontChoice)
                val fontSummary = remember(interfaceFontRevision, fontChoice, fontName, context) {
                    if (systemFontIndex >= 0) context.resources.getStringArray(R.array.system_typefaces)[systemFontIndex]
                    else fontName.ifBlank { context.getString(R.string.font_mode_custom) }
                }
                NgCompactSettingsItem(
                    title = stringResource(R.string.interface_font),
                    summary = fontSummary,
                    onClick = {},
                )
            }
        }

        if (showInterface) NgSettingsGroup {
            if (showInterface) {
                NgExpandableSettingsItem(
                title = stringResource(R.string.main_bottom_bar_style),
                summary = stringResource(
                    if (state.floatingBottomBar) {
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
                    selectedIndex = if (state.floatingBottomBar) 1 else 0,
                    onTabSelected = { index -> onFloatingBottomBarChanged(index == 1) },
                    modifier = Modifier.fillMaxWidth(),
                    selectionColors = dockSelectionColors,
                )
                AnimatedVisibility(visible = state.floatingBottomBar) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NgDockSlider(
                            title = stringResource(
                                R.string.floating_bottom_bar_bottom_distance
                            ),
                            valueText = stringResource(
                                R.string.bookshelf_floating_dock_top_distance_value,
                                state.floatingBottomBarBottomDistancePx
                            ),
                            minimumText = stringResource(
                                R.string.bookshelf_floating_dock_top_distance_value,
                                FloatingBottomBarConfig.MIN_BOTTOM_DISTANCE_PX
                            ),
                            maximumText = stringResource(
                                R.string.bookshelf_floating_dock_top_distance_value,
                                FloatingBottomBarConfig.MAX_BOTTOM_DISTANCE_PX
                            ),
                            value = state.floatingBottomBarBottomDistancePx.toFloat(),
                            valueRange = FloatingBottomBarConfig.MIN_BOTTOM_DISTANCE_PX.toFloat()..
                                FloatingBottomBarConfig.MAX_BOTTOM_DISTANCE_PX.toFloat(),
                            steps = FloatingBottomBarConfig.BOTTOM_DISTANCE_SLIDER_STEPS,
                            onValueChange = { value ->
                                onFloatingBottomBarBottomDistanceChanged(value.roundToInt())
                            },
                            onValueChangeFinished =
                                onFloatingBottomBarBottomDistanceChangeFinished
                        )
                        NgDockSlider(
                            title = stringResource(R.string.floating_bottom_bar_transparency),
                            valueText = stringResource(
                                R.string.bookshelf_floating_dock_transparency_value,
                                state.floatingBottomBarTransparency
                            ),
                            minimumText = stringResource(
                                R.string.bookshelf_floating_dock_transparency_value,
                                FloatingBottomBarConfig.MIN_TRANSPARENCY_PERCENT
                            ),
                            maximumText = stringResource(
                                R.string.bookshelf_floating_dock_transparency_value,
                                FloatingBottomBarConfig.MAX_TRANSPARENCY_PERCENT
                            ),
                            value = state.floatingBottomBarTransparency.toFloat(),
                            valueRange = FloatingBottomBarConfig.MIN_TRANSPARENCY_PERCENT.toFloat()..
                                FloatingBottomBarConfig.MAX_TRANSPARENCY_PERCENT.toFloat(),
                            onValueChange = { value ->
                                onFloatingBottomBarTransparencyChanged(value.roundToInt())
                            },
                            onValueChangeFinished =
                                onFloatingBottomBarTransparencyChangeFinished
                        )
                    }
                }
            }
                NgExpandableSettingsItem(
                title = stringResource(R.string.ng_drawer_appearance),
                summary = ngDrawerBackgroundSourceName(state.drawerProfile.source),
                expanded = drawerAppearanceExpanded,
                onExpandedChange = { drawerAppearanceExpanded = it },
            ) {
                NgDrawerBackgroundSettingsContent(
                    profile = state.drawerProfile,
                    colors = drawerColors,
                    onProfileChanged = onDrawerProfileChanged,
                    onProfileChangeFinished = onDrawerProfileChangeFinished,
                    onSelectImage = onSelectDrawerImage,
                )
            }
                NgExpandableSettingsItem(
                title = stringResource(R.string.bookshelf_top_bar_style),
                summary = stringResource(
                    when (state.bookshelfTopBarStyle) {
                        BookshelfTopBarStyle.COMPACT_TOOLBAR ->
                            R.string.bookshelf_top_bar_compact_toolbar

                        BookshelfTopBarStyle.GROUP_NAVIGATION ->
                            R.string.bookshelf_top_bar_group_navigation
                    }
                ),
                expanded = bookshelfTopBarExpanded,
                onExpandedChange = { bookshelfTopBarExpanded = it }
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
                    selectedIndex = BookshelfTopBarStyle.entries.indexOf(
                        state.bookshelfTopBarStyle
                    ),
                    onTabSelected = { index ->
                        onBookshelfTopBarStyleSelected(BookshelfTopBarStyle.entries[index])
                    },
                    modifier = Modifier.fillMaxWidth(),
                    selectionColors = dockSelectionColors,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        NgSettingsItem(
                            title = stringResource(
                                R.string.bookshelf_floating_dock_search_position
                            ),
                            trailing = NgSettingsTrailing.CUSTOM,
                            customTrailing = {
                                NgFloatingTabBar(
                                    items = listOf(
                                        NgFloatingTabSpec(
                                            text = stringResource(R.string.left)
                                        ),
                                        NgFloatingTabSpec(
                                            text = stringResource(R.string.right)
                                        )
                                    ),
                                    selectedIndex =
                                        BookshelfFloatingDockSearchPosition.entries.indexOf(
                                            state.bookshelfFloatingDockSearchPosition
                                        ),
                                    onTabSelected = { index ->
                                        onBookshelfFloatingDockSearchPositionSelected(
                                            BookshelfFloatingDockSearchPosition.entries[index]
                                        )
                                    },
                                    modifier = Modifier.width(132.dp),
                                    selectionColors = dockSelectionColors,
                                )
                            }
                        )
                        NgDockSlider(
                            title = stringResource(R.string.bookshelf_floating_dock_top_distance),
                            valueText = stringResource(
                                R.string.bookshelf_floating_dock_top_distance_value,
                                state.bookshelfFloatingDockTopDistancePx
                            ),
                            minimumText = stringResource(
                                R.string.bookshelf_floating_dock_top_distance_value,
                                state.bookshelfFloatingDockMinTopDistancePx
                            ),
                            maximumText = stringResource(
                                R.string.bookshelf_floating_dock_top_distance_value,
                                BookshelfFloatingDockConfig.MAX_TOP_DISTANCE_PX
                            ),
                            value = state.bookshelfFloatingDockTopDistancePx.toFloat(),
                            valueRange = state.bookshelfFloatingDockMinTopDistancePx.toFloat()..
                                BookshelfFloatingDockConfig.MAX_TOP_DISTANCE_PX.toFloat(),
                            steps = BookshelfFloatingDockConfig.TOP_DISTANCE_SLIDER_STEPS,
                            onValueChange = { value ->
                                onBookshelfFloatingDockTopDistanceChanged(value.roundToInt())
                            },
                            onValueChangeFinished =
                                onBookshelfFloatingDockTopDistanceChangeFinished
                        )
                        NgDockSlider(
                            title = stringResource(R.string.bookshelf_floating_dock_transparency),
                            valueText = stringResource(
                                R.string.bookshelf_floating_dock_transparency_value,
                                state.bookshelfFloatingDockTransparency
                            ),
                            minimumText = stringResource(
                                R.string.bookshelf_floating_dock_transparency_value,
                                BookshelfFloatingDockConfig.MIN_TRANSPARENCY_PERCENT
                            ),
                            maximumText = stringResource(
                                R.string.bookshelf_floating_dock_transparency_value,
                                BookshelfFloatingDockConfig.MAX_TRANSPARENCY_PERCENT
                            ),
                            value = state.bookshelfFloatingDockTransparency.toFloat(),
                            valueRange = BookshelfFloatingDockConfig.MIN_TRANSPARENCY_PERCENT.toFloat()..
                                BookshelfFloatingDockConfig.MAX_TRANSPARENCY_PERCENT.toFloat(),
                            onValueChange = { value ->
                                onBookshelfFloatingDockTransparencyChanged(value.roundToInt())
                            },
                            onValueChangeFinished =
                                onBookshelfFloatingDockTransparencyChangeFinished
                        )
                }
            }
                NgSettingsItem(
                title = stringResource(R.string.pt_hide_navigation_bar),
                summary = stringResource(R.string.ps_hide_navigation_bar),
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.hideSystemNavigationBar,
                onCheckedChange = onHideSystemNavigationBarChanged,
                onClick = {
                    onHideSystemNavigationBarChanged(!state.hideSystemNavigationBar)
                }
            )
            }
            if (section == ThemeConfigSection.ALL) {
                NgSettingsItem(
                    title = stringResource(R.string.cover_config),
                    summary = stringResource(R.string.cover_config_summary),
                    onClick = onOpenCoverConfig
                )
            }
        }

        if (section == ThemeConfigSection.INTERFACE) {
            Spacer(Modifier.height(4.dp))
            NgSettingsSectionLabel(stringResource(R.string.main_activity))
            NgSettingsGroup {
                InterfaceSwitchSettingItem(
                    title = stringResource(R.string.pt_auto_refresh),
                    summary = stringResource(R.string.ps_auto_refresh),
                    checked = state.autoRefresh,
                    onCheckedChange = onAutoRefreshChanged
                )
                AnimatedVisibility(visible = state.autoRefresh) {
                    InterfaceSwitchSettingItem(
                        title = stringResource(R.string.only_update_read),
                        summary = stringResource(R.string.ps_only_update_read),
                        checked = state.onlyUpdateRead,
                        onCheckedChange = onOnlyUpdateReadChanged
                    )
                }
                InterfaceSwitchSettingItem(
                    title = stringResource(R.string.pt_default_read),
                    summary = stringResource(R.string.ps_default_read),
                    checked = state.defaultToRead,
                    onCheckedChange = onDefaultToReadChanged
                )
                InterfaceSwitchSettingItem(
                    title = stringResource(R.string.show_home),
                    checked = state.showHome,
                    onCheckedChange = onShowHomeChanged
                )
                InterfaceSwitchSettingItem(
                    title = stringResource(R.string.show_discovery),
                    checked = state.showDiscovery,
                    onCheckedChange = onShowDiscoveryChanged
                )
                InterfaceSwitchSettingItem(
                    title = stringResource(R.string.show_rss),
                    checked = state.showRss,
                    onCheckedChange = onShowRssChanged
                )
                DefaultHomePageSettingItem(
                    selectedValue = state.defaultHomePage,
                    showHome = state.showHome,
                    onValueSelected = onDefaultHomePageSelected
                )
            }
        }
    }
    NgSoftGradientColorPresetSheet(
        show = showSoftGradientColorSheet,
        currentMode = state.softGradientColorMode,
        current = state.softGradientColor,
        customColor = state.softGradientCustomColor,
        onDismissRequest = { showSoftGradientColorSheet = false },
        onSelected = onSoftGradientColorSelected,
        onCustomColorSelected = onSoftGradientCustomColorSelected,
    )
}

internal fun NgSoftGradientLightFieldPreset.labelRes(): Int = when (this) {
    NgSoftGradientLightFieldPreset.BALANCED -> R.string.ng_soft_gradient_light_balanced
    NgSoftGradientLightFieldPreset.CLEAR -> R.string.ng_soft_gradient_light_clear
    NgSoftGradientLightFieldPreset.STILL_SEA -> R.string.ng_soft_gradient_light_still_sea
    NgSoftGradientLightFieldPreset.AQUA -> R.string.ng_soft_gradient_light_aqua
    NgSoftGradientLightFieldPreset.FLOW_SHADOW -> R.string.ng_soft_gradient_light_flow_shadow
}

internal fun NgSoftGradientColorPreset.labelRes(): Int = when (this) {
    NgSoftGradientColorPreset.CLEAR_BLUE -> R.string.ng_soft_gradient_clear_blue
    NgSoftGradientColorPreset.DUSK_VIOLET -> R.string.ng_soft_gradient_dusk_violet
    NgSoftGradientColorPreset.YOUNG_BAMBOO -> R.string.ng_soft_gradient_young_bamboo
    NgSoftGradientColorPreset.FOREST_AFTER_RAIN -> R.string.ng_soft_gradient_forest_after_rain
    NgSoftGradientColorPreset.CHERRY_GLOW -> R.string.ng_soft_gradient_cherry_glow
    NgSoftGradientColorPreset.APRICOT -> R.string.ng_soft_gradient_apricot
    NgSoftGradientColorPreset.AMBER -> R.string.ng_soft_gradient_amber
    NgSoftGradientColorPreset.INDIGO_SEA -> R.string.ng_soft_gradient_indigo_sea
    NgSoftGradientColorPreset.CELADON -> R.string.ng_soft_gradient_celadon
    NgSoftGradientColorPreset.MOON_WHITE -> R.string.ng_soft_gradient_moon_white
    NgSoftGradientColorPreset.COCOA -> R.string.ng_soft_gradient_cocoa
    NgSoftGradientColorPreset.GRAPHITE -> R.string.ng_soft_gradient_graphite
}


private fun NgVisualSystem.labelRes(): Int = when (this) {
    NgVisualSystem.TRANSPARENT_GLASS -> R.string.ng_visual_system_transparent_glass
    NgVisualSystem.LIQUID_GLASS -> R.string.ng_visual_system_liquid_glass
}

@Composable
private fun InterfaceSwitchSettingItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null
) {
    NgSettingsItem(
        title = title,
        summary = summary,
        trailing = NgSettingsTrailing.SWITCH,
        checked = checked,
        onCheckedChange = onCheckedChange,
        onClick = { onCheckedChange(!checked) }
    )
}

@Composable
private fun DefaultHomePageSettingItem(
    selectedValue: String,
    showHome: Boolean,
    onValueSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val allOptions = listOf(
        "home" to stringResource(R.string.home_title),
        "bookshelf" to stringResource(R.string.bookshelf),
        "explore" to stringResource(R.string.discovery),
        "rss" to stringResource(R.string.rss),
        "my" to stringResource(R.string.my)
    )
    val options = allOptions.filter { showHome || it.first != "home" }
    val selectedLabel = options.firstOrNull { it.first == selectedValue }?.second
        ?: stringResource(R.string.bookshelf)
    NgSettingsItem(
        title = stringResource(R.string.default_home_page),
        value = selectedLabel,
        trailing = NgSettingsTrailing.VALUE,
        onClick = { expanded = true },
        valueOverlay = {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = DpOffset(x = 0.dp, y = (-20).dp),
                containerColor = colorResource(R.color.ng_surface_card),
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label, color = Color(NgTheme.colors.onSurface)) },
                        onClick = {
                            expanded = false
                            onValueSelected(value)
                        }
                    )
                }
            }
        },
    )
}

@Composable
private fun LauncherIconPreview(
    @DrawableRes iconRes: Int,
    contentDescription: String
) {
    NgLauncherIcon(
        iconRes = iconRes,
        contentDescription = contentDescription,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp)),
    )
}
