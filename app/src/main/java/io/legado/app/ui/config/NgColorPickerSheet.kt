package io.legado.app.ui.config

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgFlatActionRail
import io.legado.app.ui.design.components.compose.NgFlatActionRailItem
import io.legado.app.ui.design.components.compose.NgFlatActionRailVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgTopBarTextMode
import io.legado.app.utils.toastOnUi

/** The standalone picker owns a draft; only the final confirmation writes to its caller. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NgColorPickerSheet(
    show: Boolean,
    initialColor: Int,
    initialTopBarTextMode: NgTopBarTextMode? = null,
    resetColor: Int? = AndroidColor.TRANSPARENT,
    showAlphaSlider: Boolean = true,
    onDismissRequest: () -> Unit,
    onSelectionConfirmed: (Int, NgTopBarTextMode?) -> Unit
) {
    if (!show) return
    val state = remember { NgColorPickerState(initialColor) }
    var topBarTextMode by remember { mutableStateOf(initialTopBarTextMode) }
    LaunchedEffect(initialColor, initialTopBarTextMode) {
        state.syncColor(initialColor)
        topBarTextMode = initialTopBarTextMode
    }
    val focusManager = LocalFocusManager.current
    val takeColor = rememberNgColorEyedropper { state.sampleRgb(it) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        contentColor = Color(NgTheme.colors.onSurface),
        shape = RectangleShape,
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(
                if (initialTopBarTextMode == null) 0.86f else 0.94f,
            ),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().navigationBarsPadding()
                    .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 20.dp),
            ) {
                NgColorPickerHeader(
                    title = stringResource(R.string.ng_select_color),
                    onPick = takeColor,
                    onClose = onDismissRequest,
                )
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                ) {
                    NgColorPickerContent(state = state, originalColor = initialColor,
                        showAlphaSlider = showAlphaSlider)
                    topBarTextMode?.let { mode ->
                        Spacer(Modifier.height(14.dp))
                        NgTopBarTextModeSelector(mode) { topBarTextMode = it }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 2.dp),
                    color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.35f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 2.dp, top = 18.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (resetColor != null) {
                        val restoreColor = if (NgTheme.snapshot.isDark) {
                            Color(NgTheme.colors.onSurfaceVariant)
                        } else Color(0xFF6B776B)
                        TextButton(
                            onClick = { focusManager.clearFocus(); state.reset(resetColor) },
                            modifier = Modifier.height(44.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_color_picker_restore),
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = restoreColor,
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                stringResource(R.string.restore_default),
                                color = restoreColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.sp,
                                maxLines = 1,
                            )
                        }
                    }
                    NgFormActionButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            focusManager.clearFocus()
                            onSelectionConfirmed(state.color, topBarTextMode)
                            onDismissRequest()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = state.isInputValid,
                        variant = NgButtonVariant.PRIMARY,
                        appearance = NgFormActionButtonAppearance.COLOR_PICKER_CONFIRM,
                        buttonHeight = 44.dp,
                        textSize = 15.sp,
                        textLineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

/** Embedded hosts retain their existing live preview and reset/return semantics. */
@Composable
internal fun NgInlineColorPicker(
    title: String,
    initialColor: Int,
    onBack: () -> Unit,
    onColorChanged: (Int) -> Unit,
    onReset: () -> Unit,
    showAlphaSlider: Boolean = true,
    forceOpaque: Boolean = false,
    backgroundRenderer: ((Int, Int) -> Bitmap)? = null,
    fillAvailableHeight: Boolean = false,
    resetInContent: Boolean = false,
    compactResetAction: Boolean = false,
    onRestoreOriginal: (() -> Unit)? = null,
    headerContentSpacing: Dp = 0.dp,
    heightLimit: Dp? = null,
) {
    val state = remember(title, forceOpaque) { NgColorPickerState(initialColor, forceOpaque) }
    val originalColor = remember(title, forceOpaque) { state.color }
    LaunchedEffect(initialColor) { state.syncColor(initialColor) }
    val onChange by rememberUpdatedState(onColorChanged)
    val takeColor = rememberNgColorEyedropper(backgroundRenderer) { sampled ->
        val previous = state.color
        state.sampleRgb(sampled)
        if (state.color != previous) onChange(state.color)
    }
    val focusManager = LocalFocusManager.current
    val maxHeight = heightLimit ?: minOf(620, (LocalConfiguration.current.screenHeightDp * 0.78f).toInt()).dp
    val heightModifier = if (fillAvailableHeight) Modifier.fillMaxHeight() else Modifier.heightIn(max = maxHeight)
    Column(modifier = Modifier.fillMaxWidth().then(heightModifier)) {
        NgColorPickerHeader(title = title, onPick = takeColor, onClose = onBack, inline = true)
        Spacer(Modifier.height(headerContentSpacing))
        Column(modifier = Modifier.weight(1f, fill = fillAvailableHeight).verticalScroll(rememberScrollState())) {
            NgColorPickerContent(
                state = state,
                originalColor = originalColor,
                showAlphaSlider = showAlphaSlider,
                onColorChanged = onChange,
                onRestoreOriginal = onRestoreOriginal,
                compactHexRow = resetInContent && compactResetAction,
                hexTrailing = if (resetInContent) {
                    {
                        val resetColor = Color(NgTheme.colors.primary)
                        if (compactResetAction) {
                            Row(
                                modifier = Modifier.heightIn(min = 44.dp)
                                    .clickable(role = Role.Button) {
                                        focusManager.clearFocus(); state.reset(state.color); onReset()
                                    }
                                    .padding(horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(painterResource(R.drawable.ic_color_picker_restore), null, Modifier.size(16.dp), tint = resetColor)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.reset), color = resetColor, fontSize = 13.sp, maxLines = 1)
                            }
                        } else {
                            TextButton(
                                onClick = { focusManager.clearFocus(); state.reset(state.color); onReset() },
                                modifier = Modifier.heightIn(min = 44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = resetColor.copy(alpha = 0.08f),
                                    contentColor = resetColor,
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                            ) {
                                Icon(painterResource(R.drawable.ic_color_picker_restore), null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.reset), fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                } else null,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (!resetInContent) {
            TextButton(
                onClick = { focusManager.clearFocus(); state.reset(state.color); onReset() },
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text(stringResource(R.string.ng_reset_color),
                    color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 14.sp)
            }
        }
    }
}

@Composable
internal fun NgColorPickerHeader(
    title: String,
    onPick: () -> Unit,
    onClose: () -> Unit,
    inline: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NgThemeSheetActionButton(
            onClick = { focusManager.clearFocus(); if (inline) onClose() else onPick() },
            contentDescription = stringResource(if (inline) R.string.back else R.string.ng_color_picker_eyedropper),
            touchSize = 44.dp,
        ) {
            Icon(if (inline) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Colorize,
                contentDescription = null, modifier = Modifier.size(22.dp),
                tint = Color(if (inline) NgTheme.colors.onSurface else NgTheme.colors.primary))
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(title, color = Color(NgTheme.colors.onSurface), fontSize = 18.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        NgThemeSheetActionButton(
            onClick = { focusManager.clearFocus(); if (inline) onPick() else onClose() },
            contentDescription = stringResource(if (inline) R.string.ng_color_picker_eyedropper else R.string.cancel),
            touchSize = 44.dp,
        ) {
            Icon(if (inline) Icons.Rounded.Colorize else Icons.Rounded.Close,
                contentDescription = null, modifier = Modifier.size(22.dp),
                tint = Color(if (inline) NgTheme.colors.primary else NgTheme.colors.onSurface))
        }
    }
}

/** A separate fullscreen sampling session leaves every business dialog mounted and unchanged. */
@Composable
internal fun rememberNgColorEyedropper(
    backgroundRenderer: ((Int, Int) -> Bitmap)? = null,
    onPicked: (Int) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val snapshot = NgTheme.snapshot
    val callback by rememberUpdatedState(onPicked)
    val currentRenderer by rememberUpdatedState(backgroundRenderer)
    var session by remember { mutableStateOf<NgColorEyedropperSession?>(null) }
    DisposableEffect(context) {
        onDispose { session?.cancel(); session = null }
    }
    return {
        if (session == null) {
            var current: Context = context
            while (current is ContextWrapper && current !is Activity) current = current.baseContext
            val activity = current as? Activity
            if (activity == null) {
                context.toastOnUi(R.string.ng_color_eyedropper_failed)
            } else {
                session = showNgColorEyedropper(
                    activity = activity,
                    themeSnapshot = snapshot,
                    backgroundRenderer = currentRenderer ?: { width, height ->
                        renderNgColorThemeBackground(activity, width, height)
                    },
                    onPicked = { color -> session = null; callback(color) },
                    onCancelled = { session = null },
                    onFailure = { session = null; context.toastOnUi(R.string.ng_color_eyedropper_failed) },
                )
            }
        }
    }
}

@Composable
private fun NgTopBarTextModeSelector(
    selected: NgTopBarTextMode,
    onSelected: (NgTopBarTextMode) -> Unit
) {
    val colors = NgTheme.colors
    val options = listOf(
        NgTopBarTextMode.AUTO to stringResource(R.string.ng_top_bar_text_auto),
        NgTopBarTextMode.LIGHT to stringResource(R.string.ng_top_bar_text_light),
        NgTopBarTextMode.DARK to stringResource(R.string.ng_top_bar_text_dark)
    )
    Text(
        text = stringResource(R.string.ng_top_bar_text),
        modifier = Modifier.fillMaxWidth(),
        color = Color(colors.onSurface),
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium
    )
    Spacer(Modifier.height(8.dp))
    NgFlatActionRail(
        items = options.map { (mode, label) ->
            NgFlatActionRailItem(
                label = label,
                emphasized = mode == selected,
            )
        },
        onItemClick = { index ->
            options.getOrNull(index)?.first?.let(onSelected)
        },
        variant = NgFlatActionRailVariant.TEXT_MODE_PICKER,
    )
}

@Composable
internal fun NgDrawerBackground(
    drawable: Drawable,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(drawable)
            }
        },
        update = { imageView ->
            if (imageView.drawable !== drawable) {
                imageView.setImageDrawable(drawable)
            }
        }
    )
}
