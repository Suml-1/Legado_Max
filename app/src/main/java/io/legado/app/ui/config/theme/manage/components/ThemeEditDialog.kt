package io.legado.app.ui.config.theme.manage.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.config.widget.ConfigEditDialogFrame
import io.legado.app.utils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 主题编辑弹窗（Compose 版）。
 *
 * 还原旧版 RecyclerView 列表行布局：颜色属性行（标题+色号+颜色方块）、
 * 选项行（标题+值，点击触发回调）、开关行（标题+Switch）。
 * 编辑结果通过 onUpdateDraft 回调更新 ViewModel 的 draft 状态。
 */
@Composable
fun ThemeEditDialog(
    draft: ThemeConfig.Config?,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onSelectImage: () -> Unit,
    onUpdateDraft: ((ThemeConfig.Config) -> ThemeConfig.Config) -> Unit,
    onColorClick: (colorKey: String, currentColor: String) -> Unit = { _, _ -> },
    onBlurClick: (currentBlur: Int) -> Unit = {},
) {
    val config = draft ?: return

    // 走统一外框：`AlertDialog` + 无上限的 `verticalScroll` 会被长表单顶出屏幕，
    // 底部的「取消/确认」跟着被裁掉（小屏或放大字号时完全看不见）
    ConfigEditDialogFrame(
        title = stringResource(if (isNew) R.string.add_theme else R.string.edit_theme),
        onDismissRequest = onDismiss,
        onConfirm = onSave,
        confirmEnabled = config.themeName.isNotBlank(),
    ) {
        // 主题名称
        OutlinedTextField(
            value = config.themeName,
            onValueChange = { name -> onUpdateDraft { cfg -> cfg.copy(themeName = name) } },
            label = { Text(stringResource(R.string.theme_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // 主色调
        ColorRow(
            title = stringResource(R.string.primary),
            hexColor = config.primaryColor,
            isAccent = false,
            onClick = { onColorClick("primaryColor", config.primaryColor) },
        )

        // 强调色
        ColorRow(
            title = stringResource(R.string.accent_color),
            hexColor = config.accentColor,
            isAccent = true,
            onClick = { onColorClick("accentColor", config.accentColor) },
        )

        // 背景色
        ColorRow(
            title = stringResource(R.string.background_color),
            hexColor = config.backgroundColor,
            isAccent = false,
            onClick = { onColorClick("backgroundColor", config.backgroundColor) },
        )

        // 底栏背景色
        ColorRow(
            title = stringResource(R.string.bottom_background_color),
            hexColor = config.bottomBackground,
            isAccent = false,
            onClick = { onColorClick("bottomBackground", config.bottomBackground) },
        )

        // 导航栏颜色透明
        SwitchRow(
            title = stringResource(R.string.top_bar_transparent),
            subtitle = stringResource(R.string.top_bar_transparent_hint),
            checked = config.transparentNavBar,
            onCheckedChange = { checked -> onUpdateDraft { cfg -> cfg.copy(transparentNavBar = checked) } },
        )

        // 背景图片
        BackgroundImageRow(
            title = stringResource(R.string.background_image),
            path = config.backgroundImgPath,
            onClick = onSelectImage,
            onClear = { onUpdateDraft { cfg -> cfg.copy(backgroundImgPath = null, backgroundImgBlur = 0) } },
        )

        // 背景图片虚化
        OptionRow(
            title = stringResource(R.string.background_image_blurring),
            value = "${config.backgroundImgBlur}",
            onClick = { onBlurClick(config.backgroundImgBlur) },
        )
    }
}

/**
 * 颜色属性行：左边标题，中间色号，右边颜色方块
 * isAccent=true 时颜色方块更大（还原旧版行为：强调色方块比其他色大）
 */
@Composable
private fun ColorRow(
    title: String,
    hexColor: String,
    isAccent: Boolean,
    onClick: () -> Unit,
) {
    val currentColor = remember(hexColor) {
        runCatching { Color(hexColor.toColorInt()) }.getOrDefault(Color.Gray)
    }
    val displayHex = remember(hexColor) { hexColor.uppercase() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )

            Text(
                text = displayHex,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(100.dp),
            )

            Spacer(Modifier.width(10.dp))

            // 颜色方块：强调色比其他色大
            Box(
                modifier = Modifier
                    .size(
                        width = if (isAccent) 36.dp else 28.dp,
                        height = if (isAccent) 28.dp else 22.dp,
                    )
                    .clip(RoundedCornerShape(5.dp))
                    .background(currentColor),
            )
        }
    }
}

/**
 * 通用属性行：左边标题，右边值文本，点击触发回调
 */
@Composable
private fun OptionRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )

            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
    }
}

/**
 * 背景图片行：左边标题，中间缩略图预览（异步解码），右边文件名。
 *
 * 预览不做虚化渲染，仅用于确认已选图片；虚化值在应用主题时生效。
 * 未选图片时不画占位框：空占位框会把行撑到 40dp 高，弹窗里看着像一段空白。
 */
@Composable
private fun BackgroundImageRow(
    title: String,
    path: String?,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    // 在 IO 线程异步解码缩略图，避免阻塞主线程；解码失败或未选图时置空
    val thumbnail: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            if (path.isNullOrBlank()) {
                null
            } else {
                runCatching { BitmapUtils.decodeBitmap(path, 256)?.asImageBitmap() }.getOrNull()
            }
        }
    }

    val fileName = remember(path) {
        path?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.ifBlank { path }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )

            // 缩略图预览：仅已选图片且解码成功时显示，行高与其它选项行一致
            thumbnail?.let { image ->
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = image,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }
                Spacer(Modifier.width(10.dp))
            }

            Text(
                text = fileName ?: stringResource(R.string.select_image),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )

            if (path != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.clear),
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * 开关行：左边标题，右边Switch
 */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 15.sp,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

// ── 预览 ────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun ThemeEditDialogPreview() {
    MaterialTheme {
        ThemeEditDialog(
            draft = ThemeConfig.Config(
                themeName = "我的主题",
                isNightTheme = false,
                primaryColor = "#FF607D8B",
                accentColor = "#FF8BC34A",
                backgroundColor = "#FFF5F5F5",
                bottomBackground = "#FF424242",
                transparentNavBar = true,
                backgroundImgPath = null,
                backgroundImgBlur = 0,
            ),
            isNew = false,
            onDismiss = {},
            onSave = {},
            onSelectImage = {},
            onUpdateDraft = {},
            onColorClick = { _, _ -> },
            onBlurClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ThemeEditDialogNewPreview() {
    MaterialTheme {
        ThemeEditDialog(
            draft = ThemeConfig.Config(
                themeName = "新建主题",
                isNightTheme = true,
                primaryColor = "#FF1A1A2E",
                accentColor = "#FFE94560",
                backgroundColor = "#FF16213E",
                bottomBackground = "#FF0F3460",
                transparentNavBar = false,
                backgroundImgPath = null,
                backgroundImgBlur = 50,
            ),
            isNew = true,
            onDismiss = {},
            onSave = {},
            onSelectImage = {},
            onUpdateDraft = {},
            onColorClick = { _, _ -> },
            onBlurClick = {},
        )
    }
}
