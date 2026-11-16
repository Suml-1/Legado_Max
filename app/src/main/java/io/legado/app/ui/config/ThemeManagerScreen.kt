package io.legado.app.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.help.config.NgManagedTheme
import io.legado.app.help.config.NgEffectThemeStatus
import io.legado.app.help.config.NgPreparedThemeImport
import io.legado.app.help.config.isBuiltIn
import io.legado.app.help.config.md3.Md3ThemeImportDraft
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgActionBarButton
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogDivider
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgDialogValueRow
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgFloatingSearchToolbar
import io.legado.app.ui.design.components.compose.NgFloatingToolbarActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgSettingsCardSurface
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.components.compose.NgSwipeToDelete
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeResolver
import kotlin.math.roundToInt

@Composable
internal fun ThemeManagerScreen(
    savedThemes: List<NgManagedTheme>,
    activeThemeId: String?,
    currentThemeName: String,
    onBack: () -> Unit,
    onSaveCurrent: (String) -> Unit,
    onImportPackage: () -> Unit,
    onThemeSelected: (NgManagedTheme) -> Unit,
    onThemeEdit: (NgManagedTheme) -> Unit,
    editingTheme: NgManagedTheme?,
    draftTheme: NgManagedTheme?,
    onDismissThemeEditor: () -> Unit,
    onDraftThemeChanged: (NgManagedTheme) -> Unit,
    onSelectBackground: (Boolean) -> Unit,
    onBackgroundBlurChanged: (Boolean, Int) -> Unit,
    onClearBackground: (Boolean) -> Unit,
    onSelectDrawerImage: (Boolean) -> Unit,
    savingTheme: Boolean,
    onSaveTheme: () -> Unit,
    onThemeExport: (NgManagedTheme) -> Unit,
    onThemeDelete: (NgManagedTheme) -> Unit,
    md3ImportDraft: Md3ThemeImportDraft?,
    md3ImportInstalling: Boolean,
    onDismissMd3Import: () -> Unit,
    onConfirmMd3Import: (Boolean) -> Unit,
    effectStatuses: Map<String, NgEffectThemeStatus>,
    effectBusy: Boolean,
    appEffectEnabled: Boolean,
    appThemeEnabled: Boolean,
    themePackageImport: NgPreparedThemeImport?,
    themePackageInstalling: Boolean,
    onDismissThemePackageImport: () -> Unit,
    onConfirmThemePackageImport: (Boolean) -> Unit,
    onEffectApply: (NgManagedTheme) -> Unit,
    onEffectApplyStatic: (NgManagedTheme) -> Unit,
    onGetMore: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    var themeNameInitial by rememberSaveable { mutableStateOf<String?>(null) }
    var backgroundActionsDark by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var backgroundBlurDark by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var pendingDeleteTheme by remember { mutableStateOf<NgManagedTheme?>(null) }

    val normalizedQuery = query.trim()
    val visibleThemes = remember(savedThemes, normalizedQuery) {
        savedThemes.filter { theme ->
            (theme.effect == null || "app" in theme.effect.targets) &&
                theme.name.contains(normalizedQuery, ignoreCase = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ThemeManagerTopBar(
                query = query,
                onQueryChange = { query = it },
                onBack = onBack,
                onSaveCurrent = { themeNameInitial = currentThemeName },
                onImportPackage = onImportPackage,
                onGetMore = onGetMore,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visibleThemes, key = { it.id }) { theme ->
                    val selected = appThemeEnabled && activeThemeId == theme.id
                    val hasEffect = theme.effect != null
                    val effectStatus = effectStatuses[theme.id]
                    val menuItems = remember(hasEffect, selected, appEffectEnabled, effectStatus) {
                        appThemeMenuItems(hasEffect, selected, appEffectEnabled, effectStatus)
                    }
                    NgSwipeToDelete(
                        deletable = true,
                        reordering = false,
                        onDeleteRequested = { pendingDeleteTheme = theme },
                    ) {
                        NgThemeManagementCard(
                            theme = theme,
                            selected = selected,
                            effectStatus = effectStatus,
                            menuItems = menuItems,
                            menuEnabled = !effectBusy,
                            onMenuAction = { action ->
                                when (action) {
                                    THEME_CARD_APPLY -> if (hasEffect) onEffectApply(theme)
                                        else onThemeSelected(theme)
                                    THEME_CARD_APPLY_STATIC -> onEffectApplyStatic(theme)
                                    THEME_CARD_EDIT -> onThemeEdit(theme)
                                    THEME_CARD_EXPORT -> onThemeExport(theme)
                                    THEME_CARD_DELETE -> pendingDeleteTheme = theme
                                }
                            },
                        )
                    }
                }
            }
        }

        if (visibleThemes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(if (normalizedQuery.isNotEmpty()) R.string.ng_theme_no_results
                            else R.string.ng_theme_library_empty),
                        color = colorResource(R.color.ng_on_surface), fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                    if (normalizedQuery.isEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NgActionBarButton(
                                text = stringResource(R.string.ng_theme_import_package),
                                icon = ImageVector.vectorResource(R.drawable.ic_theme_menu_import),
                                onClick = onImportPackage,
                                modifier = Modifier.widthIn(min = 112.dp),
                                variant = NgButtonVariant.PRIMARY_LIGHT_CONTENT,
                            )
                            NgActionBarButton(
                                text = stringResource(R.string.ng_theme_get_themes),
                                icon = ImageVector.vectorResource(R.drawable.ic_theme_menu_download),
                                onClick = onGetMore,
                                modifier = Modifier.widthIn(min = 112.dp),
                                variant = NgButtonVariant.PRIMARY_LIGHT_CONTENT,
                            )
                        }
                    }
                }
            }
        }
    }

    themePackageImport?.let { candidate ->
        ThemePackageImportDialog(
            candidate, themePackageInstalling, onDismissThemePackageImport, onConfirmThemePackageImport,
        )
    }
    if (editingTheme != null && draftTheme != null) {
        NgThemeEditorSheet(
            draftTheme = draftTheme,
            copyOnSave = editingTheme.isBuiltIn,
            onDismissRequest = onDismissThemeEditor,
            onThemeChanged = onDraftThemeChanged,
            onEditBackground = { backgroundActionsDark = it },
            onSelectDrawerImage = onSelectDrawerImage,
            savingTheme = savingTheme,
            onSave = onSaveTheme,
            onExport = { onThemeExport(draftTheme) }
        )
    }
    if (md3ImportDraft != null) {
        NgMd3ThemeImportPreviewSheet(
            draft = md3ImportDraft,
            installing = md3ImportInstalling,
            onDismissRequest = onDismissMd3Import,
            onSaveOnly = { onConfirmMd3Import(false) },
            onSaveAndApply = { onConfirmMd3Import(true) },
        )
    }
    themeNameInitial?.let { initialName ->
        NgThemeNameDialog(
            initialName = initialName,
            onDismissRequest = { themeNameInitial = null },
            onConfirm = { name ->
                themeNameInitial = null
                onSaveCurrent(name)
            },
        )
    }
    backgroundActionsDark?.let { dark ->
        val background = if (dark) draftTheme?.darkBackground else draftTheme?.lightBackground
        NgThemeBackgroundActionsDialog(
            hasImage = !background?.path.isNullOrBlank(),
            onDismissRequest = { backgroundActionsDark = null },
            onSelectImage = {
                backgroundActionsDark = null
                onSelectBackground(dark)
            },
            onEditBlur = {
                backgroundActionsDark = null
                backgroundBlurDark = dark
            },
            onClear = {
                backgroundActionsDark = null
                onClearBackground(dark)
            },
        )
    }
    backgroundBlurDark?.let { dark ->
        val background = if (dark) draftTheme?.darkBackground else draftTheme?.lightBackground
        NgThemeBackgroundBlurDialog(
            initialValue = background?.blur ?: 0,
            onDismissRequest = { backgroundBlurDark = null },
            onConfirm = { blur ->
                backgroundBlurDark = null
                onBackgroundBlurChanged(dark, blur)
            },
        )
    }
    pendingDeleteTheme?.let { theme ->
        if (theme.effect != null) {
            EffectThemeDeleteDialog(theme.name,
                activeThemeId == theme.id,
                onDismiss = { pendingDeleteTheme = null }, onConfirm = {
                    pendingDeleteTheme = null; onThemeDelete(theme)
                })
        } else NgThemeDeleteDialog(
            themeName = theme.name,
            onDismissRequest = { pendingDeleteTheme = null },
            onConfirm = {
                pendingDeleteTheme = null
                onThemeDelete(theme)
            },
        )
    }
}

@Composable
private fun NgThemeManagementCard(
    theme: NgManagedTheme,
    selected: Boolean,
    effectStatus: NgEffectThemeStatus?,
    menuItems: List<NgExpandableActionMenuItem>,
    menuEnabled: Boolean,
    onMenuAction: (Int) -> Unit,
) {
    val primary = Color(NgTheme.colors.primary)
    val secondaryText = colorResource(R.color.ng_on_surface_variant)
    val effect = theme.effect
    val publication = theme.publication
    val source = stringResource(when {
        publication?.source == "resource" && !publication.sourceId.isNullOrBlank() -> R.string.ng_theme_source_resource_center
        publication?.source == "local" -> R.string.ng_theme_source_imported
        theme.packageRootPath != null -> R.string.ng_theme_source_imported_unknown
        else -> R.string.ng_theme_source_created
    })
    val version = publication?.versionName?.takeIf(String::isNotBlank)
    val menuState = remember(theme.id) { NgPopupToggleState() }
    NgSettingsCardSurface(
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
        cornerRadius = 18.dp,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 78.dp)
                .padding(start = 16.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(colorResource(R.color.ng_settings_icon_bg)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (effect == null) Icons.Rounded.Palette else Icons.Rounded.AutoAwesome,
                    null, Modifier.size(26.dp), tint = primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(theme.name, Modifier.weight(1f), color = colorResource(R.color.ng_on_surface),
                        fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (selected) {
                        val statusTextColor = lerp(Color(NgTheme.colors.onSurface), primary, 0.45f)
                        Row(
                            modifier = Modifier.clip(CircleShape)
                                .background(primary.copy(alpha = 0.10f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(Modifier.size(4.dp).background(primary, CircleShape))
                            Text(stringResource(R.string.ng_theme_in_use),
                                color = statusTextColor, fontSize = 11.sp, lineHeight = 14.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(if (effect == null) R.string.effect_theme_filter_normal else R.string.effect_theme_filter_effect),
                        Modifier.clip(CircleShape).background(primary.copy(alpha = 0.10f))
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                        color = primary, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1)
                    Text(listOfNotNull(source, version?.let { "v$it" }).joinToString(" · "),
                        color = secondaryText, fontSize = 12.sp, lineHeight = 16.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ThemeColorPreview(theme)
                effectStatus?.takeUnless { it.canRun }?.let { status ->
                    Text(status.label, color = Color(NgTheme.colors.error), fontSize = 12.sp,
                        lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .clickable(enabled = menuEnabled, role = Role.Button, onClick = menuState::onAnchorClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.more), Modifier.size(22.dp), tint = secondaryText)
                }
                NgExpandableActionMenu(
                    expanded = menuState.expanded, onDismissRequest = menuState::onDismissRequest,
                    items = menuItems,
                    variant = NgExpandableActionMenuVariant.DROPDOWN,
                    menuContainerColor = ngDrawerContentCardColor(),
                    properties = PopupProperties(focusable = true),
                    onItemClick = { item ->
                        if (menuState.expanded) {
                            menuState.close()
                            if (menuEnabled) onMenuAction(item.itemId)
                        }
                    },
                )
            }
        }
    }
}

private const val THEME_CARD_EDIT = 0x6E7411
private const val THEME_CARD_EXPORT = 0x6E7412
private const val THEME_CARD_DELETE = 0x6E7413
private const val THEME_CARD_APPLY = 0x6E7414
private const val THEME_CARD_APPLY_STATIC = 0x6E7415

internal fun appThemeMenuItems(
    hasEffect: Boolean,
    selected: Boolean,
    appEffectEnabled: Boolean,
    effectStatus: NgEffectThemeStatus?,
): List<NgExpandableActionMenuItem> = buildList {
    val applyStatic = hasEffect && (effectStatus == NgEffectThemeStatus.UNSUPPORTED ||
        effectStatus == NgEffectThemeStatus.LOAD_ERROR)
    val appliesEffect = hasEffect && !applyStatic
    val alreadyApplied = selected && (!hasEffect || appEffectEnabled == appliesEffect)
    if (!alreadyApplied) {
        add(NgExpandableActionMenuItem(
            itemId = if (applyStatic) THEME_CARD_APPLY_STATIC else THEME_CARD_APPLY,
            titleRes = when {
                applyStatic -> R.string.effect_theme_apply_static
                hasEffect && effectStatus == null -> R.string.effect_theme_checking
                else -> R.string.ng_theme_menu_apply
            },
            iconRes = R.drawable.ic_bgm_menu_play,
            enabled = !appliesEffect || effectStatus?.canRun == true,
        ))
    }
    add(NgExpandableActionMenuItem(THEME_CARD_EDIT, R.string.edit, R.drawable.ic_edit))
    add(NgExpandableActionMenuItem(THEME_CARD_EXPORT, R.string.export, R.drawable.ic_export))
    add(NgExpandableActionMenuItem(THEME_CARD_DELETE, R.string.delete, R.drawable.ic_bgm_menu_delete, danger = true))
}

@Composable
private fun ThemeManagerTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onSaveCurrent: () -> Unit,
    onImportPackage: () -> Unit,
    onGetMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val menuState = remember { NgPopupToggleState() }
    val menuItems = remember {
        listOf(
            NgExpandableActionMenuItem(
                itemId = THEME_ACTION_SAVE_CURRENT,
                titleRes = R.string.ng_theme_save_current,
                iconRes = R.drawable.ic_theme_menu_save,
            ),
            NgExpandableActionMenuItem(
                itemId = THEME_ACTION_IMPORT_PACKAGE,
                titleRes = R.string.ng_theme_import_package,
                iconRes = R.drawable.ic_theme_menu_import,
            ),
            NgExpandableActionMenuItem(
                itemId = THEME_ACTION_GET_MORE,
                titleRes = R.string.ng_theme_get_themes,
                iconRes = R.drawable.ic_theme_menu_download,
            ),
        )
    }
    NgFloatingSearchToolbar(
        query = query,
        onQueryChange = onQueryChange,
        hint = stringResource(R.string.search_theme),
        onBack = onBack,
        modifier = modifier,
    ) {
        Box {
            NgFloatingToolbarActionButton(
                iconRes = R.drawable.ic_grid_menu,
                iconSize = 24.dp,
                contentDescription = stringResource(R.string.menu),
                onClick = menuState::onAnchorClick,
            )
            NgExpandableActionMenu(
                expanded = menuState.expanded,
                onDismissRequest = menuState::onDismissRequest,
                items = menuItems,
                variant = NgExpandableActionMenuVariant.SIDE_SLIDE,
                sideSlideEndMargin = 16.dp,
                menuContainerColor = ngDrawerContentCardColor(),
                properties = PopupProperties(focusable = true, clippingEnabled = false),
                onItemClick = { item ->
                    menuState.close()
                    when (item.itemId) {
                        THEME_ACTION_SAVE_CURRENT -> onSaveCurrent()
                        THEME_ACTION_IMPORT_PACKAGE -> onImportPackage()
                        THEME_ACTION_GET_MORE -> onGetMore()
                    }
                },
            )
        }
    }
}

private const val THEME_ACTION_SAVE_CURRENT = 0x6E7401
private const val THEME_ACTION_IMPORT_PACKAGE = 0x6E7402
private const val THEME_ACTION_GET_MORE = 0x6E7403

@Composable
private fun ThemeColorPreview(theme: NgManagedTheme) {
    val context = LocalContext.current
    val light = remember(context, theme.colors) {
        NgThemeResolver.resolveColorScheme(context, theme.colors, false)
    }
    val dark = remember(context, theme.colors) {
        NgThemeResolver.resolveColorScheme(context, theme.colors, true)
    }
    ThemeColorPreview(
        lightColors = intArrayOf(light.primary, light.secondary, light.background),
        darkColors = intArrayOf(dark.primary, dark.secondary, dark.background),
        labeled = true,
    )
}

@Composable
private fun ThemeColorPreview(lightColors: IntArray, darkColors: IntArray, labeled: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (labeled) Text(stringResource(R.string.ng_theme_palette_day),
            color = colorResource(R.color.ng_on_surface_variant), fontSize = 12.sp, lineHeight = 16.sp)
        lightColors.forEach { color -> ThemeColorDot(color) }
        Spacer(Modifier.width(if (labeled) 4.dp else 6.dp))
        if (labeled) Text(stringResource(R.string.ng_theme_palette_night),
            color = colorResource(R.color.ng_on_surface_variant), fontSize = 12.sp, lineHeight = 16.sp)
        darkColors.forEach { color -> ThemeColorDot(color) }
    }
}

@Composable
private fun ThemeColorDot(color: Int) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(Color(color))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NgThemeEditorSheet(
    draftTheme: NgManagedTheme,
    copyOnSave: Boolean,
    onDismissRequest: () -> Unit,
    onThemeChanged: (NgManagedTheme) -> Unit,
    onEditBackground: (Boolean) -> Unit,
    onSelectDrawerImage: (Boolean) -> Unit,
    savingTheme: Boolean,
    onSave: () -> Unit,
    onExport: () -> Unit
) {
    val baseSnapshot = NgTheme.snapshot
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        contentColor = Color(baseSnapshot.colors.onSurface),
        shape = RectangleShape
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                NgLongDrawerHeader(
                    title = stringResource(R.string.ng_theme_edit),
                    statusText = if (savingTheme) stringResource(R.string.ng_drawer_theme_saving) else null,
                    secondaryActionIconRes = R.drawable.ic_share,
                    secondaryActionContentDescription = stringResource(R.string.share),
                    onSecondaryActionClick = if (savingTheme) null else onExport,
                    actionIconRes = R.drawable.ic_save,
                    actionContentDescription = stringResource(R.string.save),
                    onActionClick = if (savingTheme) null else onSave,
                    centerTitle = true,
                )
                Box(modifier = Modifier.weight(1f)) {
                    ThemeEditScreen(
                        theme = draftTheme,
                        copyOnSave = copyOnSave,
                        onThemeChanged = onThemeChanged,
                        onEditBackground = onEditBackground,
                        onSelectDrawerImage = onSelectDrawerImage,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NgMd3ThemeImportPreviewSheet(
    draft: Md3ThemeImportDraft,
    installing: Boolean,
    onDismissRequest: () -> Unit,
    onSaveOnly: () -> Unit,
    onSaveAndApply: (() -> Unit)?,
) {
    val context = LocalContext.current
    val baseSnapshot = NgTheme.snapshot
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val light = remember(context, draft.theme.colors) {
        NgThemeResolver.resolveColorScheme(context, draft.theme.colors, false)
    }
    val dark = remember(context, draft.theme.colors) {
        NgThemeResolver.resolveColorScheme(context, draft.theme.colors, true)
    }
    val backgroundCount = listOf(
        draft.preview.spec.backgroundProfile.light.archivePath,
        draft.preview.spec.backgroundProfile.dark.archivePath,
    ).count { path -> path != null && path in draft.preview.spec.resources.values }

    ModalBottomSheet(
        onDismissRequest = { if (!installing) onDismissRequest() },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        contentColor = Color(baseSnapshot.colors.onSurface),
        shape = RectangleShape,
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier.fillMaxWidth(),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            val snapshot = NgTheme.snapshot
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.ng_theme_import_preview),
                    color = Color(snapshot.colors.onSurface),
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(snapshot.colors.surfaceContainer))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = draft.theme.name,
                        color = Color(snapshot.colors.onSurface),
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    ThemeColorPreview(
                        lightColors = intArrayOf(light.primary, light.secondary, light.background),
                        darkColors = intArrayOf(dark.primary, dark.secondary, dark.background),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.ng_theme_import_summary,
                        backgroundCount,
                        draft.preview.spec.coverAlbums.size,
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    color = Color(snapshot.colors.onSurfaceVariant),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NgActionBarButton(
                        text = stringResource(R.string.ng_theme_import_save_only),
                        icon = Icons.Rounded.FileDownload,
                        onClick = onSaveOnly,
                        modifier = Modifier.weight(1f),
                        enabled = !installing,
                        variant = NgButtonVariant.OUTLINE,
                    )
                    if (onSaveAndApply != null) NgActionBarButton(
                        text = stringResource(R.string.ng_theme_import_save_apply),
                        icon = Icons.Rounded.Check,
                        onClick = onSaveAndApply,
                        modifier = Modifier.weight(1f),
                        enabled = !installing,
                        variant = NgButtonVariant.PRIMARY,
                    )
                }
            }
        }
    }
}

@Composable
private fun NgThemeNameDialog(
    initialName: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val confirm = {
        val normalized = name.trim()
        if (normalized.isNotEmpty()) {
            onConfirm(normalized)
        } else {
            onDismissRequest()
        }
        Unit
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.theme_name),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.EDITOR,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismissRequest,
                    secondary = true,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.ok),
                    onClick = confirm,
                )
            },
        ) {
            NgFormField(
                label = stringResource(R.string.theme_name),
                value = name,
                onValueChange = { name = it },
                variant = NgFormFieldVariant.DIALOG_UNDERLINE,
                autoFocus = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
            )
        }
    }
}

@Composable
private fun NgThemeBackgroundActionsDialog(
    hasImage: Boolean,
    onDismissRequest: () -> Unit,
    onSelectImage: () -> Unit,
    onEditBlur: () -> Unit,
    onClear: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.background_image),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.STANDARD,
            actions = {},
        ) {
            NgDialogValueRow(
                title = stringResource(R.string.select_image),
                value = "",
                onClick = onSelectImage,
            )
            NgDialogDivider()
            NgDialogValueRow(
                title = stringResource(R.string.background_image_blurring),
                value = "",
                onClick = onEditBlur,
            )
            if (hasImage) {
                NgDialogDivider()
                NgDialogValueRow(
                    title = stringResource(R.string.clear),
                    value = "",
                    onClick = onClear,
                )
            }
        }
    }
}

@Composable
private fun NgThemeBackgroundBlurDialog(
    initialValue: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue.coerceIn(0, 25)) }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.background_image_blurring),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.STANDARD,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismissRequest,
                    secondary = true,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.ok),
                    onClick = { onConfirm(value) },
                )
            },
        ) {
            Text(
                text = value.toString(),
                modifier = Modifier.fillMaxWidth(),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 17.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            NgSlider(
                value = value.toFloat(),
                onValueChange = { value = it.roundToInt().coerceIn(0, 25) },
                valueRange = 0f..25f,
                steps = 24,
                variant = NgSliderVariant.DISCRETE,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.background_image_hint),
                modifier = Modifier.padding(top = 6.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun NgThemeDeleteDialog(
    themeName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.delete),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.CLASSIC_CONFIRMATION,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.no),
                    onClick = onDismissRequest,
                    secondary = true,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.yes),
                    onClick = onConfirm,
                    danger = true,
                )
            },
        ) {
            Text(
                text = stringResource(R.string.ng_theme_delete_message, themeName),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        }
    }
}
