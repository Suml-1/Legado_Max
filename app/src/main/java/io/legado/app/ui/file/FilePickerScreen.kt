package io.legado.app.ui.file

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.getPrimaryDisabledTextColor
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.ui.design.components.NgButtonShapeVariant
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgIconButton
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.file.utils.FilePickerIcon
import io.legado.app.utils.ConvertUtils
import java.io.File

@Composable
internal fun FilePickerScreen(
    title: String,
    breadcrumbs: List<File>,
    entries: List<FilePickerEntry>,
    currentDirectory: File?,
    directoryMode: Boolean,
    selectedFile: File?,
    isFileAllowed: (FilePickerEntry) -> Boolean,
    onRootClick: () -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    onEntryClick: (FilePickerEntry) -> Unit,
    onCreateFolderClick: () -> Unit,
    onConfirmClick: () -> Unit,
) {
    val icons = rememberFilePickerIcons()
    val context = LocalContext.current
    val lightText = !AppConfig.isNightTheme
    val primaryText = remember(context, lightText) {
        Color(context.getPrimaryTextColor(lightText))
    }
    val disabledText = remember(context, lightText) {
        Color(context.getPrimaryDisabledTextColor(lightText))
    }
    val surface = colorResource(R.color.ng_surface)
    val listSurface = colorResource(R.color.background_card)
    val dividerColor = colorResource(R.color.bg_divider_line)
    val dividerThickness = with(LocalDensity.current) { 1.toDp() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = surface,
        shape = RoundedCornerShape(dimensionResource(R.dimen.ng_dialog_radius)),
    ) {
        Column(Modifier.fillMaxSize()) {
            FilePickerHeader(
                title = title,
                onCreateFolderClick = onCreateFolderClick,
            )
            FilePickerBreadcrumbs(
                breadcrumbs = breadcrumbs,
                arrow = icons.arrow,
                background = listSurface,
                onRootClick = onRootClick,
                onBreadcrumbClick = onBreadcrumbClick,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(listSurface),
                contentPadding = PaddingValues(0.dp),
            ) {
                items(
                    items = entries,
                    key = { entry -> entry.file.absolutePath },
                ) { entry ->
                    val kind = when {
                        breadcrumbs.isNotEmpty() && entry.file == currentDirectory -> {
                            FilePickerEntryKind.UP
                        }
                        entry.isDirectory -> FilePickerEntryKind.DIRECTORY
                        else -> FilePickerEntryKind.FILE
                    }
                    val enabled = kind != FilePickerEntryKind.FILE ||
                        (!directoryMode && isFileAllowed(entry))
                    FilePickerEntryRow(
                        label = if (kind == FilePickerEntryKind.UP) ".." else entry.name,
                        icon = when (kind) {
                            FilePickerEntryKind.UP -> icons.up
                            FilePickerEntryKind.DIRECTORY -> icons.folder
                            FilePickerEntryKind.FILE -> icons.file
                        },
                        selected = entry.file == selectedFile,
                        enabled = enabled,
                        primaryText = primaryText,
                        disabledText = disabledText,
                        dividerColor = dividerColor,
                        dividerThickness = dividerThickness,
                        onClick = { onEntryClick(entry) },
                    )
                }
            }
            FilePickerConfirmBar(onConfirmClick = onConfirmClick)
        }
    }
}

@Composable
private fun FilePickerHeader(
    title: String,
    onCreateFolderClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        NgIconButton(
            onClick = onCreateFolderClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_create_folder_outline),
                contentDescription = stringResource(R.string.create_folder),
                modifier = Modifier.size(24.dp),
                tint = Color(NgTheme.colors.onSurface),
            )
        }
    }
}

@Composable
private fun FilePickerBreadcrumbs(
    breadcrumbs: List<File>,
    arrow: ImageBitmap,
    background: Color,
    onRootClick: () -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .zIndex(1f)
            .shadow(5.dp)
            .background(background),
        contentPadding = PaddingValues(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "root") {
            FilePickerBreadcrumb(
                label = "root",
                arrow = arrow,
                onClick = onRootClick,
            )
        }
        itemsIndexed(
            items = breadcrumbs,
            key = { _, file -> file.absolutePath },
        ) { index, file ->
            FilePickerBreadcrumb(
                label = file.name,
                arrow = arrow,
                onClick = { onBreadcrumbClick(index) },
            )
        }
    }
}

@Composable
private fun FilePickerBreadcrumb(
    label: String,
    arrow: ImageBitmap,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 16.sp,
            lineHeight = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Image(
            bitmap = arrow,
            contentDescription = null,
            modifier = Modifier
                .width(20.dp)
                .height(24.dp),
            contentScale = ContentScale.FillBounds,
        )
    }
}

@Composable
private fun FilePickerEntryRow(
    label: String,
    icon: ImageBitmap,
    selected: Boolean,
    enabled: Boolean,
    primaryText: Color,
    disabledText: Color,
    dividerColor: Color,
    dividerThickness: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 1.dp,
                            color = primaryText,
                            shape = RoundedCornerShape(1.dp),
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable(enabled = enabled, onClick = onClick)
                .padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = label,
                color = if (enabled) primaryText else disabledText,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dividerThickness)
                .background(dividerColor),
        )
    }
}

@Composable
private fun FilePickerConfirmBar(onConfirmClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(R.color.ng_surface))
            .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 16.dp),
    ) {
        NgButton(
            onClick = onConfirmClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            variant = NgButtonVariant.PRIMARY,
            shapeVariant = NgButtonShapeVariant.ROUNDED,
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = stringResource(R.string.ok),
                fontSize = 17.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun FilePickerCreateFolderDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val placeholder = "文件夹名"
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.create_folder),
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.CLASSIC_CONFIRMATION,
            titleFontWeight = FontWeight.Normal,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismissRequest,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.ok),
                    onClick = onConfirm,
                )
            },
        ) {
            NgFormField(
                label = placeholder,
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                variant = NgFormFieldVariant.DIALOG_UNDERLINE,
                autoFocus = true,
            )
        }
    }
}

@Composable
private fun rememberFilePickerIcons(): FilePickerIcons = remember {
    FilePickerIcons(
        folder = requireNotNull(ConvertUtils.toBitmap(FilePickerIcon.getFolder())).asImageBitmap(),
        file = requireNotNull(ConvertUtils.toBitmap(FilePickerIcon.getFile())).asImageBitmap(),
        up = requireNotNull(ConvertUtils.toBitmap(FilePickerIcon.getUpDir())).asImageBitmap(),
        arrow = requireNotNull(ConvertUtils.toBitmap(FilePickerIcon.getArrow())).asImageBitmap(),
    )
}

private data class FilePickerIcons(
    val folder: ImageBitmap,
    val file: ImageBitmap,
    val up: ImageBitmap,
    val arrow: ImageBitmap,
)

private enum class FilePickerEntryKind {
    UP,
    DIRECTORY,
    FILE,
}
