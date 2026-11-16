package io.legado.app.ui.config

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFlatActionRail
import io.legado.app.ui.design.components.compose.NgFlatActionRailItem
import io.legado.app.ui.design.components.compose.NgFlatActionRailVariant
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.toastOnUi
import kotlin.math.floor
import kotlin.math.roundToInt

/** Shared editor only. Its host owns dismissal, sampling, reset and confirmation. */
@Composable
internal fun NgColorPickerContent(
    state: NgColorPickerState,
    originalColor: Int,
    modifier: Modifier = Modifier,
    showAlphaSlider: Boolean = true,
    onColorChanged: (Int) -> Unit = {},
    hexTrailing: (@Composable () -> Unit)? = null,
    compactHexRow: Boolean = false,
    onRestoreOriginal: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = NgTheme.colors
    val hexLabel = stringResource(R.string.ng_color_picker_hex)
    val currentCallback by rememberUpdatedState(onColorChanged)
    val change: (() -> Unit) -> Unit = { edit ->
        val previous = state.color
        edit()
        if (state.isInputValid && previous != state.color) currentCallback(state.color)
    }
    val restoreOriginal = {
        if (onRestoreOriginal == null) change { state.setColor(originalColor) }
        else {
            state.setColor(originalColor)
            onRestoreOriginal()
        }
    }
    val modeLabels = listOf(
        stringResource(R.string.ng_color_picker_mode_grid),
        stringResource(R.string.ng_color_picker_mode_spectrum),
        stringResource(R.string.ng_color_picker_mode_rgb),
    )
    Column(modifier = modifier.fillMaxWidth()) {
        NgFlatActionRail(
            items = NgColorPickerMode.entries.mapIndexed { index, mode ->
                NgFlatActionRailItem(label = modeLabels[index], emphasized = state.mode == mode)
            },
            onItemClick = { index ->
                focusManager.clearFocus()
                keyboard?.hide()
                if (state.setMode(NgColorPickerMode.entries[index])) {
                    context.toastOnUi(R.string.ng_color_picker_unfinished)
                }
            },
            variant = NgFlatActionRailVariant.TEXT_MODE_PICKER,
        )
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(244.dp)) {
            when (state.mode) {
                NgColorPickerMode.GRID -> NgPickerGrid(state, change)
                NgColorPickerMode.SPECTRUM -> NgPickerSpectrum(state, change)
                NgColorPickerMode.RGB -> NgPickerRgb(state, change)
            }
        }
        Spacer(Modifier.height(18.dp))
        if (showAlphaSlider) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.ng_color_picker_opacity),
                    color = Color(colors.onSurfaceVariant),
                    fontSize = 13.sp,
                )
                Text(
                    "${(state.alpha / 255f * 100).roundToInt()}%",
                    color = Color(colors.onSurface),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            NgPickerSlider(
                value = state.alpha.toFloat(),
                max = 255f,
                description = stringResource(R.string.ng_color_picker_opacity),
                valueDescription = "${(state.alpha / 255f * 100).roundToInt()}%",
                gradient = listOf(
                    Color(state.color or (0xFF shl 24)).copy(alpha = 0f),
                    Color(state.color or (0xFF shl 24)),
                ),
                checkerboard = true,
                onValueChange = { change { state.setAlpha(it.roundToInt()) } },
            )
            Spacer(Modifier.height(12.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val stackPreview = hexTrailing != null && if (compactHexRow) {
                maxWidth < 220.dp || fontScale > 1.8f
            } else {
                maxWidth < 320.dp || fontScale > 1.15f
            }
            val moveResetBelow = compactHexRow && hexTrailing != null && !stackPreview &&
                (maxWidth < 280.dp || fontScale > 1.15f)
            val previewHexGap = when {
                !compactHexRow -> 14.dp
                maxWidth < 280.dp || fontScale > 1.15f -> 6.dp
                maxWidth < 292.dp -> 12.dp
                maxWidth < 320.dp -> 18.dp
                else -> 36.dp
            }
            Column {
                if (stackPreview) {
                    NgPickerPreviewPair(originalColor, state.color, restoreOriginal)
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!stackPreview) {
                        NgPickerPreviewPair(originalColor, state.color, restoreOriginal)
                        Spacer(Modifier.width(previewHexGap))
                    }
                    Text(hexLabel, color = Color(colors.onSurfaceVariant), fontSize = 11.sp)
                    Spacer(Modifier.width(if (compactHexRow) 4.dp else 8.dp))
                    NgFormField(
                        label = hexLabel,
                        value = state.hexInput,
                        onValueChange = { text ->
                            if (text.length <= 9) change { state.editHex(text) }
                        },
                        modifier = Modifier.weight(1f).semantics { contentDescription = hexLabel },
                        isError = state.isHexInputError,
                        variant = NgFormFieldVariant.DIALOG_UNDERLINE,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }),
                    )
                    if (hexTrailing != null && !moveResetBelow) {
                        Spacer(Modifier.width(if (compactHexRow) 4.dp else 6.dp))
                        hexTrailing()
                    }
                }
                if (moveResetBelow) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        hexTrailing()
                    }
                }
            }
        }
        if (!state.isInputValid) {
            Text(
                stringResource(R.string.ng_color_picker_input_error),
                modifier = Modifier.padding(top = 5.dp),
                color = Color(colors.error),
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        NgPickerSavedColors(state, change)
    }
}

@Composable
private fun NgPickerPreviewPair(originalColor: Int, currentColor: Int, onRestoreOriginal: () -> Unit) {
    Row {
        NgPickerPreview(
            color = originalColor,
            label = stringResource(R.string.ng_color_picker_original),
            modifier = Modifier.clip(RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.ng_color_picker_restore_original),
                    onClick = onRestoreOriginal,
                ),
        )
        NgPickerPreview(
            color = currentColor,
            label = stringResource(R.string.ng_color_picker_current),
            modifier = Modifier.clip(RoundedCornerShape(topEnd = 9.dp, bottomEnd = 9.dp)),
        )
    }
}

@Composable
private fun NgPickerGrid(state: NgColorPickerState, change: (() -> Unit) -> Unit) {
    val palette = remember { NgColorPickerColors.gridColors() }
    val selectedIndex = palette.indexOfFirst { (it and 0x00FFFFFF) == (state.color and 0x00FFFFFF) }
    val description = stringResource(R.string.ng_color_picker_mode_grid)
    val border = Color(NgTheme.colors.outlineVariant)
    fun select(index: Int) {
        change { state.setColor((palette[index.coerceIn(0, palette.lastIndex)] and 0x00FFFFFF) or (state.alpha shl 24)) }
    }
    Canvas(
        Modifier.fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .pickerGesture { position, size ->
                if (size.width > 0 && size.height > 0) {
                    val column = floor(position.x / size.width * 12).toInt().coerceIn(0, 11)
                    val row = floor(position.y / size.height * 10).toInt().coerceIn(0, 9)
                    select(row * 12 + column)
                }
            }
            .semantics {
                contentDescription = description
                stateDescription = NgColorPickerColors.format(state.color)
                progressBarRangeInfo = ProgressBarRangeInfo(selectedIndex.coerceAtLeast(0).toFloat(), 0f..119f, 118)
                setProgress { value ->
                    if (value.isFinite()) {
                        select(value.roundToInt())
                        true
                    } else false
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else {
                    val step = when (event.key) {
                        Key.DirectionLeft -> -1
                        Key.DirectionRight -> 1
                        Key.DirectionUp -> -12
                        Key.DirectionDown -> 12
                        else -> 0
                    }
                    if (step != 0) select(selectedIndex.coerceAtLeast(0) + step)
                    step != 0
                }
            }
            .focusable(),
    ) {
        val cellWidth = size.width / 12
        val cellHeight = size.height / 10
        palette.forEachIndexed { index, color ->
            drawRect(
                Color(color),
                topLeft = Offset(index % 12 * cellWidth, index / 12 * cellHeight),
                size = Size(cellWidth + 0.5f, cellHeight + 0.5f),
            )
        }
        if (selectedIndex >= 0) {
            val row = selectedIndex / 12
            val column = selectedIndex % 12
            // Leave room for the outer stroke and follow the panel's rounded corner at its edges.
            val inset = 3.dp.toPx()
            val position = Offset(column * cellWidth + inset, row * cellHeight + inset)
            val cellSize = Size((cellWidth - 2 * inset).coerceAtLeast(0f), (cellHeight - 2 * inset).coerceAtLeast(0f))
            val innerCorner = CornerRadius(2.dp.toPx())
            val outerCorner = CornerRadius((12.dp.toPx() - inset).coerceAtLeast(0f))
            val outline = Path().apply {
                addRoundRect(RoundRect(
                    left = position.x,
                    top = position.y,
                    right = position.x + cellSize.width,
                    bottom = position.y + cellSize.height,
                    topLeftCornerRadius = if (row == 0 && column == 0) outerCorner else innerCorner,
                    topRightCornerRadius = if (row == 0 && column == 11) outerCorner else innerCorner,
                    bottomRightCornerRadius = if (row == 9 && column == 11) outerCorner else innerCorner,
                    bottomLeftCornerRadius = if (row == 9 && column == 0) outerCorner else innerCorner,
                ))
            }
            drawPath(outline, Color.Black.copy(alpha = 0.65f), style = Stroke(4.dp.toPx()))
            drawPath(outline, Color.White, style = Stroke(2.dp.toPx()))
        }
        drawRoundRect(border.copy(alpha = 0.4f), cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun NgPickerSpectrum(state: NgColorPickerState, change: (() -> Unit) -> Unit) {
    // 13dp outer ring + half its 2dp stroke, plus one physical pixel for antialiasing.
    val cursorMargin = with(LocalDensity.current) { 14.dp.toPx() } + 1f
    fun inset(width: Float, height: Float) = minOf(cursorMargin, width / 2f, height / 2f)
    val hueColor = Color(NgColorPickerColors.hsvToColor(state.hue, 1f, 1f))
    val description = stringResource(R.string.ng_color_picker_mode_spectrum)
    val valueDescription = stringResource(
        R.string.ng_color_picker_spectrum_description,
        (state.saturation * 100).roundToInt(),
        (state.value * 100).roundToInt(),
    )
    val increaseSaturation = stringResource(R.string.ng_color_picker_increase_saturation)
    val decreaseSaturation = stringResource(R.string.ng_color_picker_decrease_saturation)
    val increaseBrightness = stringResource(R.string.ng_color_picker_increase_brightness)
    val decreaseBrightness = stringResource(R.string.ng_color_picker_decrease_brightness)
    fun move(saturation: Float = state.saturation, value: Float = state.value): Boolean {
        change { state.setSaturationValue(saturation, value) }
        return true
    }
    Column(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxWidth().weight(1f)
                .pickerGesture { position, size ->
                    if (size.width > 0 && size.height > 0) {
                        val margin = inset(size.width.toFloat(), size.height.toFloat())
                        move(
                            (position.x - margin) / (size.width - margin * 2f).coerceAtLeast(1f),
                            1f - (position.y - margin) / (size.height - margin * 2f).coerceAtLeast(1f),
                        )
                    }
                }
                .semantics {
                    contentDescription = description
                    stateDescription = valueDescription
                    customActions = listOf(
                        CustomAccessibilityAction(increaseSaturation) { move(saturation = state.saturation + 0.05f) },
                        CustomAccessibilityAction(decreaseSaturation) { move(saturation = state.saturation - 0.05f) },
                        CustomAccessibilityAction(increaseBrightness) { move(value = state.value + 0.05f) },
                        CustomAccessibilityAction(decreaseBrightness) { move(value = state.value - 0.05f) },
                    )
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                        Key.DirectionLeft -> move(saturation = state.saturation - 0.01f)
                        Key.DirectionRight -> move(saturation = state.saturation + 0.01f)
                        Key.DirectionUp -> move(value = state.value + 0.01f)
                        Key.DirectionDown -> move(value = state.value - 0.01f)
                        else -> false
                    }
                }
                .focusable(),
        ) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val margin = inset(size.width, size.height)
            val colorWidth = (size.width - margin * 2f).coerceAtLeast(1f)
            val colorHeight = (size.height - margin * 2f).coerceAtLeast(1f)
            val panel = Path().apply {
                addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(12.dp.toPx())))
            }
            clipPath(panel) {
                drawRect(Brush.horizontalGradient(
                    listOf(Color.White, hueColor), startX = margin, endX = margin + colorWidth,
                ))
                drawRect(Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black), startY = margin, endY = margin + colorHeight,
                ))
            }
            // Use the same bounds for the gradient, pointer mapping and cursor: even at white /
            // black corners the visible center still identifies the selected color precisely.
            val cursor = Offset(
                (margin + state.saturation * colorWidth).coerceAtMost(size.width - margin),
                (margin + (1f - state.value) * colorHeight).coerceAtMost(size.height - margin),
            )
            val cursorScale = margin / cursorMargin
            drawCircle(Color.Black.copy(alpha = 0.35f), 13.dp.toPx() * cursorScale, cursor, style = Stroke(2.dp.toPx() * cursorScale))
            drawCircle(Color.White, 11.dp.toPx() * cursorScale, cursor, style = Stroke(3.dp.toPx() * cursorScale))
        }
        Spacer(Modifier.height(10.dp))
        NgPickerSlider(
            value = state.hue,
            max = 360f,
            description = stringResource(R.string.ng_color_picker_hue),
            valueDescription = "${state.hue.roundToInt()}°",
            gradient = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
            onValueChange = { change { state.setHue(it) } },
        )
    }
}

@Composable
private fun NgPickerRgb(state: NgColorPickerState, change: (() -> Unit) -> Unit) {
    val colors = NgTheme.colors
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val labels = listOf(
        stringResource(R.string.ng_color_picker_red),
        stringResource(R.string.ng_color_picker_green),
        stringResource(R.string.ng_color_picker_blue),
    )
    val values = listOf(state.red, state.green, state.blue)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        labels.forEachIndexed { channel, label ->
            Column {
                Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = Color(colors.onSurface), fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(listOf("R", "G", "B")[channel], color = Color(colors.onSurfaceVariant), fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    val isError = state.rgbInputErrors[channel]
                    BasicTextField(
                        value = state.rgbInputs[channel],
                        onValueChange = { text ->
                            if (text.length <= 3) change { state.editRgbChannel(channel, text) }
                        },
                        modifier = Modifier.width(59.dp).height(34.dp).semantics { contentDescription = label },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color(if (isError) colors.error else colors.onSurface),
                            fontSize = 15.sp,
                            fontFamily = NgTheme.fontFamily,
                            textAlign = TextAlign.Center,
                        ),
                        cursorBrush = SolidColor(Color(colors.primary)),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboard?.hide() }),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxSize()
                                    .background(Color(colors.inputContainer), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(if (isError) colors.error else colors.outlineVariant), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 3.dp),
                                contentAlignment = Alignment.Center,
                            ) { innerTextField() }
                        },
                    )
                }
                val shift = (2 - channel) * 8
                val start = (state.color or (0xFF shl 24)) and (0xFF shl shift).inv()
                NgPickerSlider(
                    value = values[channel].toFloat(),
                    max = 255f,
                    description = label,
                    valueDescription = values[channel].toString(),
                    gradient = listOf(Color(start), Color(start or (0xFF shl shift))),
                    onValueChange = { change { state.setRgbChannel(channel, it.roundToInt()) } },
                )
            }
        }
    }
}

@Composable
private fun NgPickerSlider(
    value: Float,
    max: Float,
    description: String,
    valueDescription: String,
    gradient: List<Color>,
    checkerboard: Boolean = false,
    onValueChange: (Float) -> Unit,
) {
    val currentCallback by rememberUpdatedState(onValueChange)
    val outline = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.4f)
    Canvas(
        Modifier.fillMaxWidth().height(44.dp)
            .pickerGesture { position, size ->
                // Match the visible thumb travel, including its radius at both ends.
                val radius = size.height * 13f / 44f
                val fraction = ((position.x - radius) / (size.width - radius * 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                currentCallback(fraction * max)
            }
            .semantics {
                contentDescription = description
                stateDescription = valueDescription
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, max), 0f..max)
                setProgress { next ->
                    if (next.isFinite()) {
                        currentCallback(next.coerceIn(0f, max))
                        true
                    } else false
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                    Key.DirectionLeft, Key.DirectionDown -> { currentCallback((value - 1f).coerceAtLeast(0f)); true }
                    Key.DirectionRight, Key.DirectionUp -> { currentCallback((value + 1f).coerceAtMost(max)); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        val trackHeight = 24.dp.toPx()
        val trackTop = (size.height - trackHeight) / 2f
        val path = Path().apply {
            addRoundRect(RoundRect(Rect(0f, trackTop, size.width, trackTop + trackHeight), CornerRadius(trackHeight / 2)))
        }
        val radius = 13.dp.toPx()
        clipPath(path) {
            if (checkerboard) drawPickerCheckerboard()
            drawRect(
                Brush.horizontalGradient(gradient, startX = radius, endX = (size.width - radius).coerceAtLeast(radius + 1f)),
                Offset(0f, trackTop),
                Size(size.width, trackHeight),
            )
        }
        drawRoundRect(outline, Offset(0f, trackTop), Size(size.width, trackHeight), CornerRadius(trackHeight / 2), style = Stroke(1.dp.toPx()))
        val thumb = Offset(radius + (size.width - radius * 2).coerceAtLeast(0f) * (value / max), size.height / 2)
        drawCircle(Color.Black.copy(alpha = 0.25f), radius + 1.dp.toPx(), thumb, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White, radius - 1.dp.toPx(), thumb, style = Stroke(3.dp.toPx()))
    }
}

/** One gesture coroutine survives every color/frame update; it reads the newest callback. */
@Composable
private fun Modifier.pickerGesture(onPosition: (Offset, IntSize) -> Unit): Modifier {
    val currentCallback by rememberUpdatedState(onPosition)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            currentCallback(down.position, size)
            do {
                val event = awaitPointerEvent()
                val pointer = event.changes.firstOrNull { it.id == down.id }
                if (pointer?.pressed == true) currentCallback(pointer.position, size)
                pointer?.consume()
            } while (pointer?.pressed == true)
        }
    }
}

@Composable
private fun NgPickerPreview(color: Int, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(width = 48.dp, height = 44.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label ${NgColorPickerColors.format(color)}" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawPickerCheckerboard()
            drawRect(Color(color))
        }
        Text(
            label,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.3f)),
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun NgPickerSavedColors(state: NgColorPickerState, change: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    val colors = NgTheme.colors
    var savedColors by remember(context) { mutableStateOf(NgSavedColors.load(context)) }
    var deletingColor by remember { mutableStateOf<Int?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.ng_color_picker_saved_colors), color = Color(colors.onSurfaceVariant), fontSize = 13.sp)
        Text(stringResource(R.string.ng_color_picker_long_press_delete), color = Color(colors.onSurfaceVariant), fontSize = 11.sp)
    }
    Spacer(Modifier.height(6.dp))
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        savedColors.forEach { color ->
            val description = stringResource(R.string.ng_color_picker_saved_color_description, NgColorPickerColors.format(color))
            val selected = state.color == color
            Box(
                modifier = Modifier.size(44.dp)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = { change { state.setColor(color) } },
                        onLongClickLabel = stringResource(R.string.ng_color_picker_delete_saved),
                        onLongClick = { deletingColor = color },
                    )
                    .semantics {
                        contentDescription = description
                        this.selected = selected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    Modifier.size(34.dp).clip(CircleShape)
                        .border(1.dp, Color(colors.outlineVariant).copy(alpha = 0.5f), CircleShape),
                ) {
                    drawPickerCheckerboard()
                    drawRect(Color(color))
                }
                if (selected) {
                    Box(Modifier.size(40.dp).border(1.5.dp, Color(colors.primary), CircleShape))
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                }
            }
        }
        Box(
            modifier = Modifier.size(44.dp)
                .clickable(enabled = state.isInputValid, role = Role.Button) {
                    val result = NgSavedColors.add(context, state.color)
                    savedColors = NgSavedColors.load(context)
                    context.toastOnUi(when (result) {
                        NgSavedColors.AddResult.ADDED -> R.string.ng_color_picker_saved_added
                        NgSavedColors.AddResult.ALREADY_SAVED -> R.string.ng_color_picker_already_saved
                        NgSavedColors.AddResult.FULL -> R.string.ng_color_picker_saved_full
                    })
                }
                .semantics { contentDescription = context.getString(R.string.ng_color_picker_add_saved) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(34.dp).background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(23.dp),
                    tint = Color(0xFF1F1F23).copy(alpha = if (state.isInputValid) 1f else 0.4f),
                )
            }
        }
    }
    deletingColor?.let { color ->
        Dialog(onDismissRequest = { deletingColor = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            NgDialog(
                title = stringResource(R.string.ng_color_picker_delete_saved),
                modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 420.dp),
                variant = NgDialogVariant.COMPACT_CONFIRMATION,
                actions = {
                    NgDialogTextActionButton(stringResource(R.string.cancel), onClick = { deletingColor = null }, secondary = true)
                    NgDialogTextActionButton(stringResource(R.string.delete), onClick = {
                        savedColors = NgSavedColors.remove(context, color)
                        deletingColor = null
                        context.toastOnUi(R.string.ng_color_picker_saved_deleted)
                    }, danger = true)
                },
            ) {
                Text(NgColorPickerColors.format(color), color = Color(colors.onSurface), fontSize = 16.sp)
            }
        }
    }
}

private fun DrawScope.drawPickerCheckerboard() {
    val edge = 6.dp.toPx()
    val columns = (size.width / edge).toInt() + 1
    val rows = (size.height / edge).toInt() + 1
    repeat(rows) { row ->
        repeat(columns) { column ->
            drawRect(
                if ((row + column) % 2 == 0) Color.White else Color(0xFFD9DED7),
                Offset(column * edge, row * edge),
                Size(edge, edge),
            )
        }
    }
}
