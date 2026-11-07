package io.legado.app.ui.config.topbar.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.help.config.TopBarConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.config.widget.ConfigDialogRow
import io.legado.app.ui.config.widget.ConfigEditDialogFrame
import io.legado.app.ui.config.widget.ConfigSliderDialog
import io.legado.app.ui.widget.components.dialog.AppRadioChoiceDialog
import java.util.Locale

/** 顶栏编辑弹窗的对外回调。 */
data class TopBarEditCallbacks(
    val onDraftChange: ((TopBarConfig.Config) -> TopBarConfig.Config) -> Unit = {},
    val onRequestColor: (target: Int, current: Int) -> Unit = { _, _ -> },
    val onRequestWallpaper: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDismiss: () -> Unit = {}
)

/** 颜色选择目标：与 Activity 里 ColorPickerDialog 的 dialogId 对应 */
object TopBarColorTarget {
    const val BACKGROUND = 5101
    const val TAG_BAR = 5102
    const val TAG_SELECTED = 5103
}

/**
 * 顶栏配置编辑弹窗。
 *
 * 「默认顶栏」只暴露标签栏颜色相关的 4 项；「规则顶栏」另有圆角缩放、背景色、
 * 壁纸与筛选器默认展开等 6 项。配色来源（跟随主题/主色/强调色/自定义）走统一单选弹窗，
 * 自定义颜色交给 Activity 的 ColorPickerDialog。
 *
 * @param config 当前草稿
 * @param isEdit 是否为编辑既有配置
 * @param callbacks 对外回调集合
 */
@Composable
fun TopBarEditDialog(
    config: TopBarConfig.Config,
    isEdit: Boolean,
    callbacks: TopBarEditCallbacks
) {
    val context = LocalContext.current
    val accent = context.accentColor
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val defaultTagBar = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
    var picker by remember { mutableStateOf(TopBarPicker.None) }

    ConfigEditDialogFrame(
        title = stringResource(if (isEdit) R.string.edit else R.string.add),
        onDismissRequest = callbacks.onDismiss,
        onConfirm = callbacks.onSave,
        confirmEnabled = config.name.isNotBlank()
    ) {
        OutlinedTextField(
            value = config.name,
            onValueChange = { value ->
                callbacks.onDraftChange { it.copy(name = value) }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.top_bar_name)) }
        )

        ConfigDialogRow(
            title = stringResource(R.string.top_bar_style),
            value = stringResource(
                if (config.style == TopBarConfig.STYLE_REGULAR) {
                    R.string.regular_top_bar
                } else {
                    R.string.default_top_bar
                }
            ),
            onClick = { picker = TopBarPicker.Style }
        )

        if (config.style == TopBarConfig.STYLE_REGULAR) {
            ConfigDialogRow(
                title = stringResource(R.string.corner_scale),
                value = String.format(Locale.ROOT, "%.1f", (config.cornerScale ?: 1f).coerceIn(0f, 3f)),
                onClick = { picker = TopBarPicker.CornerScale }
            )
            ConfigDialogRow(
                title = stringResource(R.string.top_bar_background_color),
                value = colorLabel(config.backgroundColor, accent, primary),
                colorPreview = Color(
                    config.backgroundColor ?: TopBarConfig.defaultBackgroundColor(config.isNightMode)
                ),
                onClick = { picker = TopBarPicker.BackgroundSource }
            )
            ConfigDialogRow(
                title = stringResource(R.string.wallpaper),
                value = stringResource(
                    if (config.wallpaperPath.isNullOrBlank()) {
                        R.string.top_bar_wallpaper_unselected
                    } else {
                        R.string.top_bar_wallpaper_selected
                    }
                ),
                onClick = { picker = TopBarPicker.Wallpaper }
            )
            ConfigDialogRow(
                title = stringResource(R.string.top_bar_wallpaper_alpha),
                value = "${config.wallpaperAlpha}%",
                onClick = { picker = TopBarPicker.WallpaperAlpha }
            )
            ConfigDialogRow(
                title = stringResource(R.string.top_bar_filter_default),
                value = stringResource(
                    if (config.expandFiltersByDefault) {
                        R.string.top_bar_filter_default_expanded
                    } else {
                        R.string.top_bar_filter_default_collapsed
                    }
                ),
                onClick = { picker = TopBarPicker.FilterDefault }
            )
        }

        ConfigDialogRow(
            title = stringResource(R.string.top_bar_tag_bar_color),
            value = colorLabel(config.tagBarColor, accent, primary),
            colorPreview = Color(config.tagBarColor ?: defaultTagBar),
            onClick = { picker = TopBarPicker.TagBarSource }
        )
        ConfigDialogRow(
            title = stringResource(R.string.tag_bar_opacity),
            value = "${config.tagBarAlpha}%",
            onClick = { picker = TopBarPicker.TagBarAlpha }
        )
        ConfigDialogRow(
            title = stringResource(R.string.top_bar_tag_selected_color),
            value = colorLabel(config.tagSelectedColor, accent, primary),
            colorPreview = Color(config.tagSelectedColor ?: primary),
            onClick = { picker = TopBarPicker.TagSelectedSource }
        )
        ConfigDialogRow(
            title = stringResource(R.string.tag_selected_opacity),
            value = "${config.tagSelectedAlpha}%",
            onClick = { picker = TopBarPicker.TagSelectedAlpha }
        )
    }

    when (picker) {
        TopBarPicker.None -> Unit

        TopBarPicker.Style -> AppRadioChoiceDialog(
            title = stringResource(R.string.top_bar_style),
            options = listOf(
                stringResource(R.string.default_top_bar),
                stringResource(R.string.regular_top_bar)
            ),
            selectedIndex = if (config.style == TopBarConfig.STYLE_REGULAR) 1 else 0,
            onSelect = { index ->
                callbacks.onDraftChange { draft ->
                    val regular = index == 1
                    draft.copy(
                        style = if (regular) TopBarConfig.STYLE_REGULAR else TopBarConfig.STYLE_DEFAULT,
                        cornerScale = if (regular) (draft.cornerScale ?: 1f) else draft.cornerScale,
                        backgroundColor = if (regular) {
                            draft.backgroundColor
                                ?: TopBarConfig.defaultBackgroundColor(draft.isNightMode)
                        } else {
                            draft.backgroundColor
                        }
                    )
                }
                picker = TopBarPicker.None
            },
            onDismissRequest = { picker = TopBarPicker.None }
        )

        TopBarPicker.CornerScale -> ConfigSliderDialog(
            title = stringResource(R.string.corner_scale),
            value = ((config.cornerScale ?: 1f).coerceIn(0f, 3f) * 10).toInt(),
            range = 0..30,
            valueText = { String.format(Locale.ROOT, "%.1f", it / 10f) },
            onConfirm = { value ->
                callbacks.onDraftChange { it.copy(cornerScale = value / 10f) }
                picker = TopBarPicker.None
            },
            onDismissRequest = { picker = TopBarPicker.None }
        )

        TopBarPicker.BackgroundSource -> ColorSourceDialog(
            title = stringResource(R.string.top_bar_background_color),
            current = config.backgroundColor,
            accent = accent,
            primary = primary,
            onSelect = { color ->
                callbacks.onDraftChange { it.copy(backgroundColor = color) }
                picker = TopBarPicker.None
            },
            onCustom = {
                picker = TopBarPicker.None
                callbacks.onRequestColor(
                    TopBarColorTarget.BACKGROUND,
                    config.backgroundColor ?: TopBarConfig.defaultBackgroundColor(config.isNightMode)
                )
            },
            onDismissRequest = { picker = TopBarPicker.None }
        )

        TopBarPicker.TagBarSource -> ColorSourceDialog(
            title = stringResource(R.string.top_bar_tag_bar_color),
            current = config.tagBarColor,
            accent = accent,
            primary = primary,
            onSelect = { color ->
                callbacks.onDraftChange { it.copy(tagBarColor = color) }
                picker = TopBarPicker.None
            },
            onCustom = {
                picker = TopBarPicker.None
                callbacks.onRequestColor(TopBarColorTarget.TAG_BAR, config.tagBarColor ?: defaultTagBar)
            },
            onDismissRequest = { picker = TopBarPicker.None }
        )

        TopBarPicker.TagSelectedSource -> ColorSourceDialog(
            title = stringResource(R.string.top_bar_tag_selected_color),
            current = config.tagSelectedColor,
            accent = accent,
            primary = primary,
            onSelect = { color ->
                callbacks.onDraftChange { it.copy(tagSelectedColor = color) }
                picker = TopBarPicker.None
            },
            onCustom = {
                picker = TopBarPicker.None
                callbacks.onRequestColor(TopBarColorTarget.TAG_SELECTED, config.tagSelectedColor ?: primary)
            },
            onDismissRequest = { picker = TopBarPicker.None }
        )

        TopBarPicker.Wallpaper -> AppRadioChoiceDialog(
            title = stringResource(R.string.wallpaper),
            options = buildList {
                add(stringResource(R.string.select_image))
                if (!config.wallpaperPath.isNullOrBlank()) add(stringResource(R.string.delete))
            },
            selectedIndex = -1,
            onSelect = { index ->
                picker = TopBarPicker.None
                if (index == 0) {
                    callbacks.onRequestWallpaper()
                } else {
                    callbacks.onDraftChange { it.copy(wallpaperPath = null) }
                }
            },
            onDismissRequest = { picker = TopBarPicker.None }
        )

        TopBarPicker.WallpaperAlpha -> ConfigSliderDialog(
            title = stringResource(R.string.top_bar_wallpaper_alpha),
            value = config.wallpaperAlpha,
            onConfirm = { value ->
                callbacks.onDraftChange { it.copy(wallpaperAlpha = value) }
                picker = TopBarPicker.None
            },
            onDismissRequest = { picker = TopBarPicker.None }
        )

        TopBarPicker.FilterDefault -> AppRadioChoiceDialog(
            title = stringResource(R.string.top_bar_filter_default),
            options = listOf(
                stringResource(R.string.top_bar_filter_default_collapsed),
                stringResource(R.string.top_bar_filter_default_expanded)
            ),
            selectedIndex = if (config.expandFiltersByDefault) 1 else 0,
            onSelect = { index ->
                callbacks.onDraftChange { it.copy(expandFiltersByDefault = index == 1) }
                picker = TopBarPicker.None
            },
            onDismissRequest = { picker = TopBarPicker.None }
        )

        TopBarPicker.TagBarAlpha -> ConfigSliderDialog(
            title = stringResource(R.string.tag_bar_opacity),
            value = config.tagBarAlpha,
            onConfirm = { value ->
                callbacks.onDraftChange { it.copy(tagBarAlpha = value) }
                picker = TopBarPicker.None
            },
            onDismissRequest = { picker = TopBarPicker.None }
        )

        TopBarPicker.TagSelectedAlpha -> ConfigSliderDialog(
            title = stringResource(R.string.tag_selected_opacity),
            value = config.tagSelectedAlpha,
            onConfirm = { value ->
                callbacks.onDraftChange { it.copy(tagSelectedAlpha = value) }
                picker = TopBarPicker.None
            },
            onDismissRequest = { picker = TopBarPicker.None }
        )
    }
}

private enum class TopBarPicker {
    None, Style, CornerScale, BackgroundSource, TagBarSource, TagSelectedSource,
    Wallpaper, WallpaperAlpha, FilterDefault, TagBarAlpha, TagSelectedAlpha
}

@Composable
private fun ColorSourceDialog(
    title: String,
    current: Int?,
    accent: Int,
    primary: Int,
    onSelect: (Int?) -> Unit,
    onCustom: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AppRadioChoiceDialog(
        title = title,
        options = listOf(
            stringResource(R.string.top_bar_follow_theme),
            stringResource(R.string.top_bar_primary_color),
            stringResource(R.string.accent_color),
            stringResource(R.string.custom)
        ),
        selectedIndex = when (current) {
            null -> 0
            primary -> 1
            accent -> 2
            else -> 3
        },
        onSelect = { index ->
            when (index) {
                0 -> onSelect(null)
                1 -> onSelect(primary)
                2 -> onSelect(accent)
                else -> onCustom()
            }
        },
        onDismissRequest = onDismissRequest
    )
}

@Composable
private fun colorLabel(color: Int?, accent: Int, primary: Int): String {
    return when {
        color == null -> stringResource(R.string.top_bar_follow_theme)
        color == accent -> stringResource(R.string.accent_color)
        color == primary -> stringResource(R.string.top_bar_primary_color)
        else -> String.format(Locale.ROOT, "#%06X", color and 0x00FFFFFF)
    }
}
