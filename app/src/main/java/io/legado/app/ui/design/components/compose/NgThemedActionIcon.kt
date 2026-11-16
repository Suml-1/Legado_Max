package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.theme.NgTheme

/** 需要与主题强调色联动的常用操作图标。 */
enum class NgThemedActionIconKind {
    CONTENTS,
    DOWNLOAD,
    CHANGE_SOURCE,
    LISTEN,
    SIMULATED_READING,
    BOOK_SCAN,
    CHARACTER_PROFILE,
    MOVE_TO_GROUP,
    EXPORT,
    REFRESH,
    CLEAR_CACHE,
}

enum class NgThemedActionIconTone {
    DEFAULT,
    MUTED,
}

/**
 * 使用 Material Icons 的完整官方图形，以深色线稿承载语义，只在局部叠加运行时主题色。
 * 业务页面只选择图标语义，不负责拆分路径、固定色值或日夜分支。
 */
@Composable
fun NgThemedActionIcon(
    kind: NgThemedActionIconKind,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tone: NgThemedActionIconTone = NgThemedActionIconTone.DEFAULT,
    tint: Color? = null,
) {
    val baseIcon = kind.baseIcon()
    val baseColor = tint ?: when (tone) {
        NgThemedActionIconTone.DEFAULT -> Color(NgTheme.colors.onSurface)
        NgThemedActionIconTone.MUTED -> Color(NgTheme.colors.onSurfaceVariant)
            .copy(alpha = 0.64f)
    }
    val accentColor = Color(NgTheme.colors.primary)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = baseIcon,
            contentDescription = contentDescription,
            tint = baseColor,
            modifier = Modifier.fillMaxSize(),
        )

        if (tint == null && tone == NgThemedActionIconTone.DEFAULT) when (kind) {
            NgThemedActionIconKind.LISTEN -> {
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            val contentScope = this
                            clipRect(
                                top = size.height * 0.50f,
                                right = size.width * 0.40f,
                            ) {
                                contentScope.drawContent()
                            }
                            clipRect(
                                left = size.width * 0.60f,
                                top = size.height * 0.50f,
                            ) {
                                contentScope.drawContent()
                            }
                        },
                )
                // 使用同轮廓的实心耳罩铺底，再重绘线稿，避免上下露白或越界。
                Icon(
                    imageVector = baseIcon,
                    contentDescription = null,
                    tint = baseColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            NgThemedActionIconKind.SIMULATED_READING -> Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(13.dp),
            )

            NgThemedActionIconKind.BOOK_SCAN -> Icon(
                imageVector = Icons.Rounded.HorizontalRule,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(12.dp),
            )

            NgThemedActionIconKind.CLEAR_CACHE -> Icon(
                imageVector = Icons.Rounded.HorizontalRule,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .size(14.dp)
                    .offset(y = 6.dp),
            )

            else -> kind.accentClips().forEach { clip ->
                Icon(
                    imageVector = baseIcon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            val contentScope = this
                            clipRect(
                                left = size.width * clip.left,
                                top = size.height * clip.top,
                                right = size.width * clip.right,
                                bottom = size.height * clip.bottom,
                            ) {
                                contentScope.drawContent()
                            }
                        },
                )
            }
        }
    }
}

private data class AccentClip(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
)

private fun NgThemedActionIconKind.baseIcon(): ImageVector = when (this) {
    NgThemedActionIconKind.CONTENTS -> Icons.AutoMirrored.Outlined.FormatListBulleted
    NgThemedActionIconKind.DOWNLOAD -> Icons.Outlined.Download
    NgThemedActionIconKind.CHANGE_SOURCE -> Icons.Outlined.SwapHoriz
    NgThemedActionIconKind.LISTEN -> Icons.Outlined.Headphones
    NgThemedActionIconKind.SIMULATED_READING -> Icons.Outlined.PlayCircleOutline
    NgThemedActionIconKind.BOOK_SCAN -> Icons.Outlined.DocumentScanner
    NgThemedActionIconKind.CHARACTER_PROFILE -> Icons.Outlined.PersonOutline
    NgThemedActionIconKind.MOVE_TO_GROUP -> Icons.Outlined.Folder
    NgThemedActionIconKind.EXPORT -> Icons.Outlined.IosShare
    NgThemedActionIconKind.REFRESH -> Icons.Rounded.Refresh
    NgThemedActionIconKind.CLEAR_CACHE -> Icons.Outlined.CleaningServices
}

private fun NgThemedActionIconKind.accentClips(): List<AccentClip> = when (this) {
    NgThemedActionIconKind.CONTENTS -> listOf(AccentClip(right = 0.34f))
    NgThemedActionIconKind.DOWNLOAD -> listOf(AccentClip(top = 0.68f))
    NgThemedActionIconKind.CHANGE_SOURCE -> listOf(AccentClip(top = 0.52f))
    NgThemedActionIconKind.CHARACTER_PROFILE -> listOf(AccentClip(top = 0.70f))
    NgThemedActionIconKind.LISTEN,
    NgThemedActionIconKind.CLEAR_CACHE,
    NgThemedActionIconKind.SIMULATED_READING,
    NgThemedActionIconKind.BOOK_SCAN,
    NgThemedActionIconKind.MOVE_TO_GROUP,
    NgThemedActionIconKind.EXPORT,
    NgThemedActionIconKind.REFRESH -> emptyList()
}
