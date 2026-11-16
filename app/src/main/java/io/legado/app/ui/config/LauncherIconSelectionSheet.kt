package io.legado.app.ui.config

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.components.compose.NgLauncherIcon
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.theme.NgTheme

/** 外观设置中的启动图标选择；完整使用 NG Compose 标准抽屉与固定四列网格。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LauncherIconSelectionSheet(
    currentValue: String,
    onDismissRequest: () -> Unit,
    onSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    val values = remember(context) { context.resources.getStringArray(R.array.icons).toList() }
    val iconResources = remember(context, values) {
        values.map { iconName ->
            context.resources.getIdentifier(iconName, "mipmap", context.packageName)
        }
    }
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
                .fillMaxHeight(0.50f),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                NgLongDrawerHeader(
                    title = stringResource(R.string.change_icon),
                    centerTitle = true,
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(ICONS_PER_ROW),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 10.dp),
                ) {
                    itemsIndexed(
                        items = values,
                        key = { _, value -> value },
                    ) { index, value ->
                        LauncherIconOption(
                            iconRes = iconResources[index],
                            index = index,
                            selected = value == currentValue,
                            onClick = {
                                if (value != currentValue) onSelected(value)
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
private fun LauncherIconOption(
    iconRes: Int,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val description = "${stringResource(R.string.change_icon)} ${index + 1}"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(78.dp)) {
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
                NgLauncherIcon(
                    iconRes = iconRes,
                    contentDescription = description,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .padding(end = 1.dp, bottom = 1.dp)
                        .size(24.dp)
                        .align(Alignment.BottomEnd),
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
    }
}

private const val ICONS_PER_ROW = 4
