package io.legado.app.ui.config

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.NgSoftGradientColorMode
import io.legado.app.help.config.NgSoftGradientColorPreset
import io.legado.app.help.config.NgSoftGradientTheme
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.theme.NgTheme

/** 柔光色调选择；复用配色预设抽屉的四列网格规格。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NgSoftGradientColorPresetSheet(
    show: Boolean,
    currentMode: NgSoftGradientColorMode,
    current: NgSoftGradientColorPreset,
    customColor: Int,
    onDismissRequest: () -> Unit,
    onSelected: (NgSoftGradientColorPreset) -> Unit,
    onCustomColorSelected: (Int) -> Unit,
) {
    if (!show) return
    var selectedTab by remember(currentMode) {
        mutableIntStateOf(
            if (currentMode == NgSoftGradientColorMode.CUSTOM) CUSTOM_TAB else PRESET_TAB,
        )
    }
    val colorState = remember(customColor) { NgColorPickerState(customColor, forceOpaque = true) }
    val takeColor = rememberNgColorEyedropper { colorState.sampleRgb(it) }
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
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(if (selectedTab == CUSTOM_TAB) 0.80f else 0.60f),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                if (selectedTab == CUSTOM_TAB) {
                    NgColorPickerHeader(
                        title = stringResource(R.string.ng_soft_gradient_color),
                        onPick = takeColor,
                        onClose = onDismissRequest,
                    )
                } else {
                    NgLongDrawerHeader(
                        title = stringResource(R.string.ng_soft_gradient_color),
                        centerTitle = true,
                    )
                }
                NgFloatingTabBar(
                    items = listOf(
                        NgFloatingTabSpec(
                            text = stringResource(R.string.ng_soft_gradient_color_preset_tab),
                        ),
                        NgFloatingTabSpec(
                            text = stringResource(R.string.ng_soft_gradient_color_custom_tab),
                        ),
                    ),
                    selectedIndex = selectedTab,
                    onTabSelected = { tab ->
                        selectedTab = tab
                        if (tab == CUSTOM_TAB) {
                            colorState.reset(customColor)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
                if (selectedTab == PRESET_TAB) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(PRESETS_PER_ROW),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp),
                    ) {
                        items(
                            items = NgSoftGradientColorPreset.entries,
                            key = { it.storageValue },
                        ) { preset ->
                            NgSoftGradientColorPresetOption(
                                preset = preset,
                                selected = currentMode == NgSoftGradientColorMode.PRESET &&
                                    preset == current,
                                onClick = {
                                    if (
                                        currentMode != NgSoftGradientColorMode.PRESET ||
                                        preset != current
                                    ) {
                                        onSelected(preset)
                                    }
                                    onDismissRequest()
                                },
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                    ) {
                        NgColorPickerContent(
                            state = colorState,
                            originalColor = customColor or AndroidColor.BLACK,
                            showAlphaSlider = false,
                        )
                    }
                    NgFormActionButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            onCustomColorSelected(colorState.color)
                            onDismissRequest()
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        enabled = colorState.isInputValid,
                        variant = NgButtonVariant.PRIMARY,
                        buttonHeight = 44.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun NgSoftGradientColorPresetOption(
    preset: NgSoftGradientColorPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = NgSoftGradientTheme.colors(preset).manualLight
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 6.dp)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(68.dp)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(18.dp),
                color = ngDrawerContentCardColor(),
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = Color(
                        if (selected) NgTheme.colors.primary else NgTheme.colors.outlineVariant,
                    ),
                ),
                shadowElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    NgPresetSwatch(
                        lightColor = colors.primary,
                        darkColor = colors.labelContainer,
                        modifier = Modifier.size(46.dp),
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = Color(NgTheme.colors.primary),
                    ) {}
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = Color(NgTheme.colors.onPrimary),
                    )
                }
            }
        }
        Text(
            text = stringResource(preset.labelRes()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp),
            color = Color(if (selected) NgTheme.colors.primary else NgTheme.colors.onSurface),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private const val PRESETS_PER_ROW = 4
private const val PRESET_TAB = 0
private const val CUSTOM_TAB = 1
