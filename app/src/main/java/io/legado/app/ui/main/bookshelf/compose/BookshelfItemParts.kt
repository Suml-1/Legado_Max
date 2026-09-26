package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.book.BookTagMatcher
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.composeActionShape
import io.legado.app.ui.theme.pageSecondaryTextColor

/** 条目文字字号：对齐原 item_bookshelf_* 布局，避免换风格时观感漂移 */
internal const val SHELF_TITLE_TEXT_SIZE = 16
internal const val SHELF_META_TEXT_SIZE = 13
internal const val SHELF_INTRO_TEXT_SIZE = 12
internal const val SHELF_CHIP_TEXT_SIZE = 11

/** 未读角标字号：对齐 archive-main 的 10sp，比原 BadgeView 的 11sp 再小一档 */
internal const val SHELF_BADGE_TEXT_SIZE = 10

/**
 * 各档文字的行高（sp）。
 *
 * 只传 fontSize 会继承 MaterialTheme 默认 bodyLarge 的 24sp 行高，13sp 的文字每行
 * 自带约 5dp 的上下空隙，行与行之间看起来隔了一大截；这里逐档压紧到字号 +
 * 3sp 左右，观感对齐 includeFontPadding=false 的 TextView。
 */
internal const val SHELF_TITLE_LINE_HEIGHT = 20
internal const val SHELF_META_LINE_HEIGHT = 16
internal const val SHELF_INTRO_LINE_HEIGHT = 16
internal const val SHELF_CHIP_LINE_HEIGHT = 14
internal const val SHELF_BADGE_LINE_HEIGHT = 12

/**
 * 未读角标 / 更新中转圈。
 *
 * 更新中优先显示转圈（与 View 版一致）；角标在有新章节时用强调色，否则用中性灰
 * （对齐 View 侧 `R.color.darker_gray` 的观感）；数量为 0 或开关关闭时整体隐藏。
 */
@Composable
internal fun BookshelfItemStatus(
    modifier: Modifier = Modifier,
    bookItem: BookshelfBookItem,
    showUnread: Boolean,
    loadingSize: Dp,
) {
    if (bookItem.isUpdating) {
        CircularProgressIndicator(
            modifier = modifier.size(loadingSize),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = AppDimens.shelfLoadingStrokeWidth,
        )
        return
    }
    if (!showUnread || bookItem.unreadCount <= 0) return
    val badgeColor = if (bookItem.hasNewChapter) {
        MaterialTheme.colorScheme.primary
    } else {
        pageSecondaryTextColor()
    }
    val textColor = if (badgeColor.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = AppDimens.shelfBadgeMinSize,
                minHeight = AppDimens.shelfBadgeMinSize,
            )
            .clip(RoundedCornerShape(AppDimens.shelfBadgeCornerRadius))
            .background(badgeColor)
            .padding(
                horizontal = AppDimens.shelfBadgePaddingHorizontal,
                vertical = AppDimens.shelfBadgePaddingVertical,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = bookItem.unreadCount.toString(),
            color = textColor,
            fontSize = SHELF_BADGE_TEXT_SIZE.sp,
            lineHeight = SHELF_BADGE_LINE_HEIGHT.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** 带图标的 metadata 行（作者 / 阅读进度 / 最新章节），可选在行尾追加最后更新时间 */
@Composable
internal fun BookshelfMetaLine(
    modifier: Modifier = Modifier,
    iconRes: Int,
    contentDescription: String,
    text: String?,
    spacing: Dp,
    trailing: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookshelfMetaIcon(iconRes = iconRes, contentDescription = contentDescription)
        Spacer(modifier = Modifier.width(AppDimens.shelfMetaIconSpacing))
        Text(
            text = text.orEmpty(),
            modifier = Modifier.weight(1f),
            color = pageSecondaryTextColor(),
            fontSize = SHELF_META_TEXT_SIZE.sp,
            lineHeight = SHELF_META_LINE_HEIGHT.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                color = pageSecondaryTextColor(),
                fontSize = SHELF_META_TEXT_SIZE.sp,
                lineHeight = SHELF_META_LINE_HEIGHT.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun BookshelfMetaIcon(
    modifier: Modifier = Modifier,
    iconRes: Int,
    contentDescription: String,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        tint = pageSecondaryTextColor(),
        modifier = modifier.size(AppDimens.shelfMetaIconSize),
    )
}

/** 阅读进度条与百分比：进度为 null（未读或开关关闭）时整行隐藏 */
@Composable
internal fun BookshelfReadProgress(
    modifier: Modifier = Modifier,
    progress: Float?,
) {
    if (progress == null) return
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = AppDimens.shelfProgressSpacing,
                bottom = AppDimens.shelfProgressVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(AppDimens.shelfProgressThickness)
                .clip(RoundedCornerShape(AppDimens.shelfProgressThickness))
                .background(accent.copy(alpha = AppDimens.SHELF_PROGRESS_TRACK_ALPHA))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(accent)
            )
        }
        Text(
            text = "${(progress * 100).toInt()}%",
            modifier = Modifier.padding(start = AppDimens.shelfProgressPercentSpacing),
            color = pageSecondaryTextColor(),
            fontSize = SHELF_CHIP_TEXT_SIZE.sp,
            lineHeight = SHELF_CHIP_LINE_HEIGHT.sp,
            maxLines = 1,
        )
    }
}

/** 标签 / 字数 / 分类胶囊，自动换行 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookshelfChips(
    modifier: Modifier = Modifier,
    chips: List<BookshelfChip>,
    showBorder: Boolean,
) {
    if (chips.isEmpty()) return
    val shape = composeActionShape()
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = AppDimens.shelfTagRowsSpacing),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.shelfTagChipSpacing),
        verticalArrangement = Arrangement.spacedBy(AppDimens.shelfTagChipVerticalSpacing),
    ) {
        chips.forEach { chip ->
            Box(
                modifier = Modifier
                    .then(
                        if (showBorder) {
                            Modifier
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = AppDimens.shelfTagChipBorderWidth,
                                    color = pageSecondaryTextColor(),
                                    shape = shape,
                                )
                        } else {
                            Modifier
                        }
                    )
                    .padding(
                        horizontal = AppDimens.shelfTagChipPaddingHorizontal,
                        vertical = AppDimens.shelfTagChipPaddingVertical,
                    )
            ) {
                Text(
                    text = chip.label(),
                    color = pageSecondaryTextColor(),
                    fontSize = SHELF_CHIP_TEXT_SIZE.sp,
                    lineHeight = SHELF_CHIP_LINE_HEIGHT.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 书籍标签带菱形前缀，前缀字号按 [BookTagMatcher.TAG_MARKER_SCALE] 缩小 */
private fun BookshelfChip.label(): AnnotatedString = if (bookTag) {
    buildAnnotatedString {
        withStyle(
            SpanStyle(fontSize = SHELF_CHIP_TEXT_SIZE.sp * BookTagMatcher.TAG_MARKER_SCALE)
        ) {
            append(BookTagMatcher.TAG_MARKER)
        }
        append(text)
    }
} else {
    AnnotatedString(text)
}
