package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

/** 文件浏览列表中的固定语义图标，业务页面只传文件类型。 */
enum class NgFileEntryIconKind {
    DIRECTORY,
    FILE,
    ARCHIVE,
    ON_BOOKSHELF,
}

enum class NgFileSelectionCheckboxVariant {
    STANDARD,
    COMPACT,
}

/**
 * 用轻量主题色容器区分目录、压缩包和已入书架文件。
 *
 * 容器沿用 NG 运行时色板，不复刻系统文件管理器的深色图标底板。
 */
@Composable
fun NgFileEntryIcon(
    kind: NgFileEntryIconKind,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = NgTheme.colors
    val containerColor = when (kind) {
        NgFileEntryIconKind.DIRECTORY -> Color(colors.primaryContainer)
        NgFileEntryIconKind.FILE -> Color(colors.surfaceContainerLow)
        NgFileEntryIconKind.ARCHIVE -> colorResource(R.color.ng_warning_container)
        NgFileEntryIconKind.ON_BOOKSHELF -> colorResource(R.color.ng_success_container)
    }
    val iconColor = when (kind) {
        NgFileEntryIconKind.DIRECTORY -> Color(colors.primary)
        NgFileEntryIconKind.FILE -> Color(colors.onSurfaceVariant)
        NgFileEntryIconKind.ARCHIVE -> colorResource(R.color.ng_warning)
        NgFileEntryIconKind.ON_BOOKSHELF -> colorResource(R.color.ng_success)
    }
    val icon = when (kind) {
        NgFileEntryIconKind.DIRECTORY -> Icons.Rounded.Folder
        NgFileEntryIconKind.FILE -> Icons.AutoMirrored.Rounded.InsertDriveFile
        NgFileEntryIconKind.ARCHIVE -> Icons.Rounded.FolderZip
        NgFileEntryIconKind.ON_BOOKSHELF -> Icons.Rounded.LibraryAddCheck
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(NgTheme.shapes.smallDp.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(21.dp),
        )
    }
}

/** 文件导入列表的主题化选择控件，避免默认纯黑描边抢占视觉层级。 */
@Composable
fun NgFileSelectionCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: NgFileSelectionCheckboxVariant = NgFileSelectionCheckboxVariant.STANDARD,
    checkmarkColor: Color = Color.White,
) {
    val colors = NgTheme.colors
    val checkbox: @Composable (Modifier) -> Unit = { checkboxModifier ->
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = checkboxModifier,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(colors.primary),
                checkmarkColor = checkmarkColor,
                uncheckedColor = Color(colors.onSurfaceVariant).copy(alpha = 0.62f),
                disabledCheckedColor = Color(colors.primary).copy(alpha = 0.38f),
                disabledUncheckedColor = Color(colors.onSurfaceVariant).copy(alpha = 0.28f),
                disabledIndeterminateColor = Color(colors.primary).copy(alpha = 0.38f),
            ),
        )
    }
    when (variant) {
        NgFileSelectionCheckboxVariant.STANDARD -> checkbox(modifier)
        NgFileSelectionCheckboxVariant.COMPACT -> {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                checkbox(
                    modifier
                        .size(32.dp)
                        .scale(0.85f)
                )
            }
        }
    }
}
