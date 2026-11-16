package io.legado.app.ui.config

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.colorResource
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
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.theme.NgBuiltInColorPresets
import io.legado.app.ui.design.theme.NgColorPreset
import io.legado.app.ui.design.theme.NgColorSystem
import io.legado.app.ui.design.theme.NgTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NgColorPresetSheet(
    show: Boolean,
    current: NgColorSystem,
    onDismissRequest: () -> Unit,
    onSelected: (NgColorSystem) -> Unit,
) {
    if (!show) return
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
                .fillMaxHeight(0.60f),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                NgLongDrawerHeader(
                    title = stringResource(R.string.ng_color_presets),
                    centerTitle = true,
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(PRESETS_PER_ROW),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp),
                ) {
                    items(
                        items = NgBuiltInColorPresets.all,
                        key = { it.nameRes },
                    ) { preset ->
                        NgColorPresetOption(
                            preset = preset,
                            selected = preset.matches(current),
                            onClick = {
                                onSelected(preset.applyTo(current))
                                onDismissRequest()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NgColorPresetOption(
    preset: NgColorPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
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
                        if (selected) NgTheme.colors.primary else NgTheme.colors.outlineVariant
                    ),
                ),
                shadowElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    NgPresetSwatch(
                        lightColor = preset.lightSeed,
                        darkColor = preset.darkSeed,
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
            text = stringResource(preset.nameRes),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp),
            color = Color(
                if (selected) NgTheme.colors.primary else NgTheme.colors.onSurface
            ),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
internal fun NgPresetSwatch(
    lightColor: Int,
    darkColor: Int,
    modifier: Modifier = Modifier,
) {
    val outlineColor = Color(NgTheme.colors.outlineVariant)
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val path = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    left = center.x - radius,
                    top = center.y - radius,
                    right = center.x + radius,
                    bottom = center.y + radius,
                )
            )
        }
        clipPath(path) {
            drawRect(
                color = Color(lightColor),
                size = Size(size.width / 2f, size.height),
            )
            drawRect(
                color = Color(darkColor),
                topLeft = Offset(size.width / 2f, 0f),
                size = Size(size.width / 2f, size.height),
            )
        }
        drawCircle(
            color = outlineColor,
            radius = radius - 0.5.dp.toPx(),
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

private const val PRESETS_PER_ROW = 4
