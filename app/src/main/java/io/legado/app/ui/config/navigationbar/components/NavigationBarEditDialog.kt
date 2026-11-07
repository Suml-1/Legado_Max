package io.legado.app.ui.config.navigationbar.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.help.config.NavigationBarConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.eInkGrayscale
import io.legado.app.ui.widget.components.dialog.AppRadioChoiceDialog
import io.legado.app.ui.config.widget.ConfigDialogRow
import io.legado.app.ui.config.widget.ConfigEditDialogFrame
import io.legado.app.ui.config.widget.ConfigSliderDialog
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.AppDrawablePainter
import java.util.Locale

/** 底栏编辑弹窗的对外回调。 */
data class NavigationBarEditCallbacks(
    val onDraftChange: ((NavigationBarConfig) -> NavigationBarConfig) -> Unit = {},
    val onRequestColor: (target: Int, current: Int) -> Unit = { _, _ -> },
    val onRequestIcon: (itemKey: String, state: String, title: String) -> Unit = { _, _, _ -> },
    val onSave: () -> Unit = {},
    val onDismiss: () -> Unit = {}
)

/** 颜色选择目标：与 Activity 里 ColorPickerDialog 的 dialogId 对应 */
object NavigationBarColorTarget {
    const val BORDER = 1
    const val BACKGROUND = 2
}

/**
 * 底栏配置编辑弹窗。
 *
 * 布局模式/材质模式/颜色来源走统一的单选弹窗，百分比走统一的滑块弹窗，
 * 图标行支持逐项替换与删除（菜单内联在图标按钮上）。
 *
 * @param config 当前草稿
 * @param isEdit 是否为编辑既有配置（决定标题）
 * @param defaultBottomColor 「跟随主题」时的底色
 * @param callbacks 对外回调集合
 */
@Composable
fun NavigationBarEditDialog(
    config: NavigationBarConfig,
    isEdit: Boolean,
    defaultBottomColor: Int,
    callbacks: NavigationBarEditCallbacks
) {
    val context = LocalContext.current
    val accent = context.accentColor
    var picker by remember { mutableStateOf(NavigationBarPicker.None) }

    ConfigEditDialogFrame(
        title = stringResource(if (isEdit) R.string.edit else R.string.add),
        onDismissRequest = callbacks.onDismiss,
        onConfirm = callbacks.onSave,
        confirmEnabled = config.name.isNotBlank()
    ) {
        OutlinedTextField(
            value = config.name,
            onValueChange = { value ->
                callbacks.onDraftChange { it.copySelf().apply { name = value } }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.navigation_bar_name)) }
        )

        ConfigDialogRow(
            title = stringResource(R.string.bottom_bar_layout_mode),
            value = config.getLayoutModeText(context),
            onClick = { picker = NavigationBarPicker.LayoutMode }
        )

        if (config.layoutMode == NavigationBarConfig.LAYOUT_FLOATING) {
            ConfigDialogRow(
                title = stringResource(R.string.bottom_bar_effect_mode),
                value = config.getEffectModeText(context),
                onClick = { picker = NavigationBarPicker.EffectMode }
            )
        }

        ConfigDialogRow(
            title = stringResource(R.string.bottom_background_color),
            value = backgroundColorText(config, accent),
            colorPreview = Color(config.backgroundColor ?: defaultBottomColor),
            onClick = { picker = NavigationBarPicker.BackgroundSource }
        )

        if (config.layoutMode != NavigationBarConfig.LAYOUT_SIDEBAR) {
            ConfigDialogRow(
                title = stringResource(R.string.opacity),
                value = "${config.opacity}%",
                onClick = { picker = NavigationBarPicker.Opacity }
            )
            ConfigDialogRow(
                title = stringResource(R.string.bottom_bar_border_color),
                value = borderColorText(config, accent),
                colorPreview = Color(config.borderColor ?: Color.Transparent.toArgb()),
                onClick = { picker = NavigationBarPicker.BorderSource }
            )
            if (config.borderColor?.let(::hasAlpha) == true) {
                ConfigDialogRow(
                    title = stringResource(R.string.bottom_bar_border_alpha),
                    value = "${config.borderAlpha}%",
                    onClick = { picker = NavigationBarPicker.BorderAlpha }
                )
            }
        }

        NavigationBarConfig.items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                NavBarIconButton(
                    config = config,
                    item = item,
                    selected = false,
                    callbacks = callbacks
                )
                Spacer(Modifier.size(AppDimens.panelRowTitleSpacing))
                NavBarIconButton(
                    config = config,
                    item = item,
                    selected = true,
                    callbacks = callbacks
                )
            }
        }
    }

    when (picker) {
        NavigationBarPicker.None -> Unit

        NavigationBarPicker.LayoutMode -> AppRadioChoiceDialog(
            title = stringResource(R.string.bottom_bar_layout_mode),
            options = listOf(
                stringResource(R.string.nav_bar_layout_floating),
                stringResource(R.string.nav_bar_layout_standard),
                stringResource(R.string.nav_bar_layout_sidebar)
            ),
            selectedIndex = layoutModeValues.indexOf(config.layoutMode),
            onSelect = { index ->
                val mode = layoutModeValues.getOrNull(index) ?: return@AppRadioChoiceDialog
                callbacks.onDraftChange { draft ->
                    draft.copySelf().apply {
                        layoutMode = mode
                        if (mode == NavigationBarConfig.LAYOUT_STANDARD) {
                            effectMode = NavigationBarConfig.EFFECT_SOLID
                            opacity = 100
                        }
                    }
                }
                picker = NavigationBarPicker.None
            },
            onDismissRequest = { picker = NavigationBarPicker.None }
        )

        NavigationBarPicker.EffectMode -> AppRadioChoiceDialog(
            title = stringResource(R.string.bottom_bar_effect_mode),
            options = listOf(
                stringResource(R.string.nav_bar_effect_solid),
                stringResource(R.string.nav_bar_effect_glass),
                stringResource(R.string.nav_bar_effect_frosted)
            ),
            selectedIndex = effectModeValues.indexOf(config.effectMode),
            onSelect = { index ->
                val mode = effectModeValues.getOrNull(index) ?: return@AppRadioChoiceDialog
                callbacks.onDraftChange { it.copySelf().apply { effectMode = mode } }
                picker = NavigationBarPicker.None
            },
            onDismissRequest = { picker = NavigationBarPicker.None }
        )

        NavigationBarPicker.BackgroundSource -> AppRadioChoiceDialog(
            title = stringResource(R.string.bottom_background_color),
            options = listOf(
                stringResource(R.string.bottom_bar_follow_theme),
                stringResource(R.string.accent_color),
                stringResource(R.string.custom)
            ),
            selectedIndex = when (config.backgroundColor) {
                null -> 0
                accent -> 1
                else -> 2
            },
            onSelect = { index ->
                picker = NavigationBarPicker.None
                when (index) {
                    0 -> callbacks.onDraftChange { it.copySelf().apply { backgroundColor = null } }
                    1 -> callbacks.onDraftChange { it.copySelf().apply { backgroundColor = accent } }
                    else -> callbacks.onRequestColor(
                        NavigationBarColorTarget.BACKGROUND,
                        config.backgroundColor ?: defaultBottomColor
                    )
                }
            },
            onDismissRequest = { picker = NavigationBarPicker.None }
        )

        NavigationBarPicker.BorderSource -> AppRadioChoiceDialog(
            title = stringResource(R.string.bottom_bar_border_color),
            options = listOf(
                stringResource(R.string.transparent),
                stringResource(R.string.accent_color),
                stringResource(R.string.custom)
            ),
            selectedIndex = when {
                config.borderColor?.let(::hasAlpha) != true -> 0
                config.borderColor == accent -> 1
                else -> 2
            },
            onSelect = { index ->
                picker = NavigationBarPicker.None
                when (index) {
                    0 -> callbacks.onDraftChange {
                        it.copySelf().apply { borderColor = Color.Transparent.toArgb() }
                    }
                    1 -> callbacks.onDraftChange {
                        it.copySelf().apply {
                            borderColor = accent
                            borderAlpha = borderAlpha.coerceIn(1, 100)
                        }
                    }
                    else -> callbacks.onRequestColor(
                        NavigationBarColorTarget.BORDER,
                        config.borderColor ?: accent
                    )
                }
            },
            onDismissRequest = { picker = NavigationBarPicker.None }
        )

        NavigationBarPicker.Opacity -> ConfigSliderDialog(
            title = stringResource(R.string.opacity),
            value = config.opacity,
            onConfirm = { value ->
                callbacks.onDraftChange { it.copySelf().apply { opacity = value } }
                picker = NavigationBarPicker.None
            },
            onDismissRequest = { picker = NavigationBarPicker.None }
        )

        NavigationBarPicker.BorderAlpha -> ConfigSliderDialog(
            title = stringResource(R.string.bottom_bar_border_alpha),
            value = config.borderAlpha,
            onConfirm = { value ->
                callbacks.onDraftChange { it.copySelf().apply { borderAlpha = value } }
                picker = NavigationBarPicker.None
            },
            onDismissRequest = { picker = NavigationBarPicker.None }
        )
    }
}

private enum class NavigationBarPicker {
    None, LayoutMode, EffectMode, BackgroundSource, BorderSource, Opacity, BorderAlpha
}

private val layoutModeValues = listOf(
    NavigationBarConfig.LAYOUT_FLOATING,
    NavigationBarConfig.LAYOUT_STANDARD,
    NavigationBarConfig.LAYOUT_SIDEBAR
)

private val effectModeValues = listOf(
    NavigationBarConfig.EFFECT_SOLID,
    NavigationBarConfig.EFFECT_GLASS,
    NavigationBarConfig.EFFECT_FROSTED
)

private fun hasAlpha(color: Int): Boolean = (color ushr 24) and 0xFF > 0

@Composable
private fun backgroundColorText(config: NavigationBarConfig, accent: Int): String {
    return when (val color = config.backgroundColor) {
        null -> stringResource(R.string.bottom_bar_follow_theme)
        accent -> stringResource(R.string.accent_color)
        else -> String.format(Locale.ROOT, "#%06X", color and 0x00FFFFFF)
    }
}

@Composable
private fun borderColorText(config: NavigationBarConfig, accent: Int): String {
    val color = config.borderColor
    return when {
        color == null || !hasAlpha(color) -> stringResource(R.string.transparent)
        color == accent -> stringResource(R.string.accent_color)
        else -> String.format(Locale.ROOT, "#%06X", color and 0x00FFFFFF)
    }
}

/**
 * 单个导航项的图标按钮（常规/选中两枚），点击后可选图片或删除自定义图标。
 */
@Composable
private fun NavBarIconButton(
    config: NavigationBarConfig,
    item: NavigationBarConfig.NavItem,
    selected: Boolean,
    callbacks: NavigationBarEditCallbacks
) {
    val context = LocalContext.current
    val description = stringResource(
        if (selected) R.string.navigation_icon_selected else R.string.navigation_icon_normal
    )
    var expanded by remember { mutableStateOf(false) }
    val state = if (selected) NavigationBarConfig.STATE_SELECTED else NavigationBarConfig.STATE_NORMAL
    val drawable = remember(config.icons, item.key, state) {
        NavigationBarConfig.previewDrawable(context, config, item, selected)
    }

    IconButton(onClick = { expanded = true }) {
        if (drawable != null) {
            Image(
                painter = remember(drawable) { AppDrawablePainter(drawable) },
                contentDescription = description,
                modifier = Modifier.size(AppDimens.panelRowIconSize)
            )
        } else {
            Icon(
                painter = painterResource(item.defaultIconRes),
                contentDescription = description,
                modifier = Modifier.size(AppDimens.panelRowIconSize)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.eInkGrayscale()
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.select_image)) },
                onClick = {
                    expanded = false
                    callbacks.onRequestIcon(item.key, state, description)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = {
                    expanded = false
                    callbacks.onDraftChange { draft ->
                        draft.copySelf().apply {
                            icons = icons.toMutableMap().apply {
                                remove(NavigationBarConfig.iconKey(item.key, state))
                            }
                        }
                    }
                }
            )
        }
    }
}
