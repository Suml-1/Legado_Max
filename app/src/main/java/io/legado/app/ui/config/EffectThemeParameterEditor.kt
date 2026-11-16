package io.legado.app.ui.config

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.NgManagedTheme
import io.legado.app.ui.design.components.compose.NgFormNavigationRow
import io.legado.app.ui.design.components.compose.NgFormSwitchRow
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.theme.NgTheme
import java.util.Locale
import kotlin.math.round

/** The installed theme owns its parameter schema; hosts only render these basic controls. */
@Composable
internal fun EffectThemeParameterEditor(
    theme: NgManagedTheme,
    values: Map<String, String>,
    enabled: Boolean = true,
    rangeHorizontalPadding: Dp = 0.dp,
    onValuePreview: (String, String) -> Unit,
    onValueCommitted: () -> Unit,
) {
    var choiceKey by remember(theme.id) { mutableStateOf<String?>(null) }
    var colorKey by remember(theme.id) { mutableStateOf<String?>(null) }
    val parameters = theme.effect?.parameterSchema.orEmpty()
    fun value(key: String, default: String): String = values[key] ?: theme.effectValues[key] ?: default
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parameters.forEach { parameter ->
            val current = value(parameter.key, parameter.defaultValue)
            when (parameter.type) {
                "range" -> {
                    val min = parameter.min ?: 0f
                    val max = parameter.max ?: 1f
                    if (min.isFinite() && max.isFinite() && max > min) {
                        val number = current.toFloatOrNull()?.takeIf(Float::isFinite)
                            ?.coerceIn(min, max) ?: min
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = rangeHorizontalPadding, vertical = 4.dp),
                        ) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(parameter.label, Modifier.weight(1f), color = Color(NgTheme.colors.onSurface), fontSize = 14.sp)
                                Text(number.toString().removeSuffix(".0"), color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 13.sp)
                            }
                            NgSlider(value = number, onValueChange = { raw ->
                                val step = parameter.step?.takeIf { it.isFinite() && it > 0f }
                                val snapped = if (step == null) raw else min + round((raw - min) / step) * step
                                onValuePreview(parameter.key, snapped.coerceIn(min, max).toString())
                            }, valueRange = min..max, enabled = enabled,
                                onValueChangeFinished = onValueCommitted)
                        }
                    }
                }
                "choice" -> NgFormNavigationRow(
                    title = parameter.label, arrowIcon = painterResource(R.drawable.ic_chevron_right_20),
                    value = parameter.options.firstOrNull { it.value == current }?.label ?: current,
                    onClick = { if (enabled) choiceKey = parameter.key },
                    enabled = enabled,
                )
                "boolean" -> NgFormSwitchRow(
                    title = parameter.label, checked = current.equals("true", true), enabled = enabled,
                    onCheckedChange = { checked ->
                        onValuePreview(parameter.key, checked.toString())
                        onValueCommitted()
                    },
                )
                "color" -> NgFormNavigationRow(
                    title = parameter.label, value = current, enabled = enabled,
                    arrowIcon = painterResource(R.drawable.ic_chevron_right_20),
                    onClick = { if (enabled) colorKey = parameter.key },
                )
            }
        }
    }
    parameters.firstOrNull { it.key == choiceKey }?.let { parameter ->
        ConfigChoiceDialog(title = parameter.label,
            options = parameter.options.map { ConfigChoiceOption(it.label, it.value) },
            selectedValue = value(parameter.key, parameter.defaultValue),
            onDismissRequest = { choiceKey = null },
            onSelected = { selected ->
                choiceKey = null
                if (enabled) {
                    onValuePreview(parameter.key, selected)
                    onValueCommitted()
                }
            })
    }
    parameters.firstOrNull { it.key == colorKey }?.let { parameter ->
        val current = value(parameter.key, parameter.defaultValue)
        val initial = runCatching { AndroidColor.parseColor(current) }
            .getOrElse { current.toIntOrNull() ?: AndroidColor.WHITE }
        NgColorPickerSheet(show = true, initialColor = initial, resetColor = null,
            onDismissRequest = { colorKey = null },
            onSelectionConfirmed = { selected, _ ->
                colorKey = null
                if (enabled) {
                    onValuePreview(parameter.key, String.format(Locale.ROOT, "#%08X", selected))
                    onValueCommitted()
                }
            })
    }
}
