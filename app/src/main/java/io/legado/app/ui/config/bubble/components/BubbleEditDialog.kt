package io.legado.app.ui.config.bubble.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.ui.config.widget.ConfigDialogRow
import io.legado.app.ui.config.widget.ConfigEditDialogFrame
import io.legado.app.ui.config.widget.ConfigSliderDialog
import java.util.Locale

/** 气泡编辑弹窗的对外回调。 */
data class BubbleEditCallbacks(
    val onDraftChange: ((BubblePackageManager.Config) -> BubblePackageManager.Config) -> Unit = {},
    val onRequestColor: (target: Int, current: String) -> Unit = { _, _ -> },
    val onEditSvg: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDismiss: () -> Unit = {}
)

/** 颜色选择目标：与 Activity 里 ColorPickerDialog 的 dialogId 对应 */
object BubbleColorTarget {
    const val DAY_NORMAL = 7101
    const val DAY_EMPHASIS = 7102
    const val NIGHT_NORMAL = 7103
    const val NIGHT_EMPHASIS = 7104
}

/**
 * 气泡包编辑弹窗。
 *
 * 颜色以 `#RRGGBB` 字符串存在配置里，预览与解析都在渲染侧完成，因此这里只做取值与回填。
 * SVG 模板交给代码编辑页（[io.legado.app.ui.code.CodeEditActivity]）编辑。
 *
 * @param config 当前草稿
 * @param isEdit 是否为编辑既有配置
 * @param callbacks 对外回调集合
 */
@Composable
fun BubbleEditDialog(
    config: BubblePackageManager.Config,
    isEdit: Boolean,
    callbacks: BubbleEditCallbacks
) {
    var picker by remember { mutableStateOf(BubblePicker.None) }

    ConfigEditDialogFrame(
        title = stringResource(if (isEdit) R.string.edit else R.string.add),
        onDismissRequest = callbacks.onDismiss,
        onConfirm = callbacks.onSave,
        confirmEnabled = config.name.isNotBlank()
    ) {
        OutlinedTextField(
            value = config.name,
            onValueChange = { value -> callbacks.onDraftChange { it.copy(name = value) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.bubble_name)) }
        )

        ConfigDialogRow(
            title = stringResource(R.string.bubble_size_scale),
            value = String.format(Locale.ROOT, "%.1f", config.sizeScale),
            onClick = { picker = BubblePicker.SizeScale }
        )

        ColorRow(
            title = stringResource(R.string.bubble_day_normal),
            value = colorOr(config.dayNormalColor, BubblePackageManager.DEFAULT_NORMAL_COLOR),
            onClick = { picker = BubblePicker.DayNormal }
        )
        ColorRow(
            title = stringResource(R.string.bubble_day_emphasis),
            value = colorOr(config.dayEmphasisColor, BubblePackageManager.DEFAULT_EMPHASIS_COLOR),
            onClick = { picker = BubblePicker.DayEmphasis }
        )
        ColorRow(
            title = stringResource(R.string.bubble_night_normal),
            value = colorOr(config.nightNormalColor, BubblePackageManager.DEFAULT_NORMAL_COLOR),
            onClick = { picker = BubblePicker.NightNormal }
        )
        ColorRow(
            title = stringResource(R.string.bubble_night_emphasis),
            value = colorOr(config.nightEmphasisColor, BubblePackageManager.DEFAULT_EMPHASIS_COLOR),
            onClick = { picker = BubblePicker.NightEmphasis }
        )

        ConfigDialogRow(
            title = stringResource(R.string.bubble_svg_edit_title),
            value = stringResource(R.string.bubble_svg_edit_hint),
            onClick = callbacks.onEditSvg
        )
    }

    when (picker) {
        BubblePicker.None -> Unit

        BubblePicker.SizeScale -> ConfigSliderDialog(
            title = stringResource(R.string.bubble_size_scale),
            value = (config.sizeScale * 10).toInt().coerceIn(
                (BubblePackageManager.MIN_SIZE_SCALE * 10).toInt(),
                (BubblePackageManager.MAX_SIZE_SCALE * 10).toInt()
            ),
            range = (BubblePackageManager.MIN_SIZE_SCALE * 10).toInt()..
                (BubblePackageManager.MAX_SIZE_SCALE * 10).toInt(),
            valueText = { String.format(Locale.ROOT, "%.1f", it / 10f) },
            onConfirm = { value ->
                callbacks.onDraftChange { it.copy(sizeScale = value / 10f) }
                picker = BubblePicker.None
            },
            onDismissRequest = { picker = BubblePicker.None }
        )

        BubblePicker.DayNormal -> requestColor(
            BubbleColorTarget.DAY_NORMAL,
            colorOr(config.dayNormalColor, BubblePackageManager.DEFAULT_NORMAL_COLOR),
            callbacks
        ) { picker = BubblePicker.None }

        BubblePicker.DayEmphasis -> requestColor(
            BubbleColorTarget.DAY_EMPHASIS,
            colorOr(config.dayEmphasisColor, BubblePackageManager.DEFAULT_EMPHASIS_COLOR),
            callbacks
        ) { picker = BubblePicker.None }

        BubblePicker.NightNormal -> requestColor(
            BubbleColorTarget.NIGHT_NORMAL,
            colorOr(config.nightNormalColor, BubblePackageManager.DEFAULT_NORMAL_COLOR),
            callbacks
        ) { picker = BubblePicker.None }

        BubblePicker.NightEmphasis -> requestColor(
            BubbleColorTarget.NIGHT_EMPHASIS,
            colorOr(config.nightEmphasisColor, BubblePackageManager.DEFAULT_EMPHASIS_COLOR),
            callbacks
        ) { picker = BubblePicker.None }
    }
}

private enum class BubblePicker {
    None, SizeScale, DayNormal, DayEmphasis, NightNormal, NightEmphasis
}

private fun requestColor(
    target: Int,
    current: String,
    callbacks: BubbleEditCallbacks,
    onDone: () -> Unit
) {
    onDone()
    callbacks.onRequestColor(target, current)
}

@Composable
private fun ColorRow(title: String, value: String, onClick: () -> Unit) {
    ConfigDialogRow(
        title = title,
        value = value.uppercase(Locale.ROOT),
        colorPreview = Color(runCatching { value.toColorInt() }.getOrDefault(0xFF808080.toInt())),
        onClick = onClick
    )
}

private fun colorOr(value: String?, fallback: String): String {
    val normalized = value?.takeIf { it.isNotBlank() }
        ?.let { if (it.startsWith("#")) it else "#$it" }
        ?: fallback
    return runCatching {
        normalized.toColorInt()
        normalized
    }.getOrDefault(fallback)
}
