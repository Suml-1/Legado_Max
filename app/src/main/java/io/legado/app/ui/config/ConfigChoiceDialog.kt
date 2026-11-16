package io.legado.app.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogDivider
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgDialogValueRow
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgFormMultilineField
import io.legado.app.ui.design.components.compose.NgFormMultilineFieldVariant
import io.legado.app.ui.design.components.compose.NgCodeHighlightMode
import io.legado.app.ui.design.components.compose.rememberNgCodeVisualTransformation
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

internal data class ConfigChoiceOption(
    val label: String,
    val value: String,
)

@Composable
internal fun ConfigChoiceDialog(
    title: String?,
    options: List<ConfigChoiceOption>,
    selectedValue: String? = null,
    onDismissRequest: () -> Unit,
    onSelected: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = title,
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.STANDARD,
            actions = {},
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((options.size * 56).coerceAtMost(420).dp),
            ) {
                itemsIndexed(
                    items = options,
                    key = { _, option -> option.value },
                ) { index, option ->
                    NgDialogValueRow(
                        title = option.label,
                        value = "",
                        onClick = { onSelected(option.value) },
                        trailingContent = if (option.value == selectedValue) {
                            {
                                Icon(
                                    painter = painterResource(R.drawable.ng_ic_popup_selected),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color(NgTheme.colors.onSurface),
                                )
                            }
                        } else {
                            null
                        },
                    )
                    if (index < options.lastIndex) NgDialogDivider()
                }
            }
        }
    }
}

@Composable
internal fun ConfigNumberPickerDialog(
    title: String,
    minValue: Int,
    maxValue: Int,
    initialValue: Int,
    decimalMode: Boolean = false,
    defaultValue: Int? = null,
    defaultText: String? = null,
    cancelText: String,
    confirmText: String,
    onDismissRequest: () -> Unit,
    onValueSelected: (Int) -> Unit,
) {
    var selectedValue by remember(minValue, maxValue, initialValue) {
        mutableIntStateOf(initialValue.coerceIn(minValue, maxValue))
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = title,
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 420.dp),
            variant = NgDialogVariant.STANDARD,
            actions = {
                if (defaultValue != null && defaultText != null) {
                    NgDialogTextActionButton(
                        text = defaultText,
                        onClick = { onValueSelected(defaultValue.coerceIn(minValue, maxValue)) },
                        secondary = true,
                    )
                    Spacer(Modifier.weight(1f))
                }
                NgDialogTextActionButton(
                    text = cancelText,
                    onClick = onDismissRequest,
                    secondary = true,
                )
                NgDialogTextActionButton(
                    text = confirmText,
                    onClick = { onValueSelected(selectedValue) },
                )
            },
        ) {
            ConfigNumberWheel(
                minValue = minValue,
                maxValue = maxValue,
                selectedValue = selectedValue,
                decimalMode = decimalMode,
                onSelectedValueChange = { selectedValue = it },
            )
        }
    }
}

@Composable
private fun ConfigNumberWheel(
    minValue: Int,
    maxValue: Int,
    selectedValue: Int,
    decimalMode: Boolean,
    onSelectedValueChange: (Int) -> Unit,
) {
    val itemCount = maxValue - minValue + 1
    val initialIndex = (selectedValue - minValue).coerceIn(0, itemCount - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(listState)
    val scope = rememberCoroutineScope()
    val centeredValue by remember(listState, minValue, itemCount) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo
                .asSequence()
                .filter { it.index in WHEEL_SPACER_ROWS until itemCount + WHEEL_SPACER_ROWS }
                .minByOrNull { item ->
                    abs(item.offset + item.size / 2 - viewportCenter)
                }
                ?.index
                ?.minus(WHEEL_SPACER_ROWS)
                ?.plus(minValue)
        }
    }
    LaunchedEffect(centeredValue) {
        centeredValue?.let(onSelectedValueChange)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WHEEL_ROW_HEIGHT * WHEEL_VISIBLE_ROWS),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WHEEL_ROW_HEIGHT)
                .padding(horizontal = 8.dp)
                .background(
                    Color(NgTheme.colors.primary).copy(alpha = 0.09f),
                    RoundedCornerShape(12.dp),
                ),
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(
                count = itemCount + WHEEL_SPACER_ROWS * 2,
                key = { virtualIndex -> "number-wheel-$virtualIndex" },
            ) { virtualIndex ->
                if (
                    virtualIndex < WHEEL_SPACER_ROWS ||
                    virtualIndex >= itemCount + WHEEL_SPACER_ROWS
                ) {
                    Spacer(Modifier.height(WHEEL_ROW_HEIGHT))
                } else {
                    val value = minValue + virtualIndex - WHEEL_SPACER_ROWS
                    val selected = value == selectedValue
                    Text(
                        text = if (decimalMode) (value / 10.0).toString() else value.toString(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WHEEL_ROW_HEIGHT)
                            .clickable {
                                scope.launch {
                                    listState.animateScrollToItem(value - minValue)
                                }
                            }
                            .padding(vertical = 10.dp),
                        color = Color(
                            if (selected) NgTheme.colors.primary
                            else NgTheme.colors.onSurfaceVariant
                        ),
                        fontSize = if (selected) 19.sp else 16.sp,
                        lineHeight = 23.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ConfigConfirmationDialog(
    title: String,
    message: String,
    cancelText: String,
    confirmText: String,
    danger: Boolean = false,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = title,
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.CLASSIC_CONFIRMATION,
            actions = {
                NgDialogTextActionButton(
                    text = cancelText,
                    onClick = onDismissRequest,
                    secondary = true,
                )
                NgDialogTextActionButton(
                    text = confirmText,
                    onClick = onConfirm,
                    danger = danger,
                )
            },
        ) {
            Text(
                text = message,
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
internal fun ConfigTextEditorDialog(
    title: String,
    initialValue: String,
    cancelText: String,
    confirmText: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = title,
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.EDITOR,
            actions = {
                NgDialogTextActionButton(
                    text = cancelText,
                    onClick = onDismissRequest,
                    secondary = true,
                )
                NgDialogTextActionButton(
                    text = confirmText,
                    onClick = { onConfirm(value) },
                )
            },
        ) {
            NgFormField(
                label = title,
                value = value,
                onValueChange = { value = it },
                variant = NgFormFieldVariant.DIALOG_UNDERLINE,
                autoFocus = true,
            )
        }
    }
}

@Composable
internal fun ConfigJsonEditorDialog(
    title: String,
    label: String,
    initialValue: String,
    cancelText: String,
    confirmText: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = title,
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 560.dp),
            variant = NgDialogVariant.EDITOR,
            actions = {
                NgDialogTextActionButton(
                    text = cancelText,
                    onClick = onDismissRequest,
                    secondary = true,
                )
                NgDialogTextActionButton(
                    text = confirmText,
                    onClick = { onConfirm(value) },
                )
            },
        ) {
            NgFormMultilineField(
                value = value,
                onValueChange = { value = it },
                label = label,
                minHeight = 160.dp,
                maxHeight = 300.dp,
                minLines = 7,
                maxLines = 14,
                visualTransformation = rememberNgCodeVisualTransformation(
                    mode = NgCodeHighlightMode.DEFAULT,
                ),
                variant = NgFormMultilineFieldVariant.DIALOG_UNDERLINE,
            )
        }
    }
}

private val WHEEL_ROW_HEIGHT = 44.dp
private const val WHEEL_VISIBLE_ROWS = 5
private const val WHEEL_SPACER_ROWS = 2
