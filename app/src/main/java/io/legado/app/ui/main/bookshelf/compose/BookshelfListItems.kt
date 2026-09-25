package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.dao.BookShelfDisplay
import io.legado.app.help.book.BookTagMatcher
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.widget.components.AppBookCover

/** 条目文字字号：对齐原 item_bookshelf_* 布局，避免换风格时观感漂移 */
private const val SHELF_TITLE_TEXT_SIZE = 16
private const val SHELF_META_TEXT_SIZE = 13
private const val SHELF_INTRO_TEXT_SIZE = 12
private const val SHELF_CHIP_TEXT_SIZE = 11

/**
 * 书架列表条目（标准列表 / 紧凑列表）。
 *
 * 与 View 版两个列表适配器渲染同样的内容：封面、未读角标或更新中转圈、书名、作者、
 * 阅读进度、最新章节、最后更新时间、标签与简介，全部由 [config] 控制显隐。
 */
@Composable
fun BookshelfListItem(
    item: BookshelfBookItem,
    config: BookshelfDisplayConfig,
    onClick: (BookshelfBookItem) -> Unit,
    onLongClick: (BookshelfBookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (config.isCompactList) {
        BookshelfCompactListItem(item, config, onClick, onLongClick, modifier)
    } else {
        BookshelfStandardListItem(item, config, onClick, onLongClick, modifier)
    }
}

@Composable
private fun BookshelfStandardListItem(
    item: BookshelfBookItem,
    config: BookshelfDisplayConfig,
    onClick: (BookshelfBookItem) -> Unit,
    onLongClick: (BookshelfBookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    BookshelfItemContainer(
        config = config,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
        modifier = modifier,
    ) {
        BookshelfItemCover(item, AppDimens.shelfCoverWidth)
        Spacer(modifier = Modifier.width(AppDimens.shelfCoverNameSpacing))
        Column(modifier = Modifier.weight(1f)) {
            BookshelfItemTitleRow(item, config, AppDimens.shelfListLoadingSize)
            BookshelfMetaLine(
                iconRes = R.drawable.ic_author,
                contentDescription = stringResource(R.string.author),
                text = item.display.author,
                trailing = item.lastUpdateText,
                spacing = AppDimens.shelfMetaFirstSpacing,
            )
            BookshelfMetaLine(
                iconRes = R.drawable.ic_history,
                contentDescription = stringResource(R.string.read_dur_progress),
                text = item.display.durChapterTitle,
                spacing = AppDimens.shelfMetaSpacing,
            )
            BookshelfMetaLine(
                iconRes = R.drawable.ic_book_last,
                contentDescription = stringResource(R.string.lasted_show),
                text = item.display.latestChapterTitle,
                spacing = AppDimens.shelfMetaSpacing,
            )
            BookshelfReadProgress(item.readProgress)
            BookshelfChips(item.chips, config.showBookBorder)
            item.intro?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = AppDimens.shelfIntroSpacing),
                    color = pageSecondaryTextColor(),
                    fontSize = SHELF_INTRO_TEXT_SIZE.sp,
                    maxLines = config.introLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BookshelfCompactListItem(
    item: BookshelfBookItem,
    config: BookshelfDisplayConfig,
    onClick: (BookshelfBookItem) -> Unit,
    onLongClick: (BookshelfBookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    BookshelfItemContainer(
        config = config,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
        modifier = modifier,
    ) {
        BookshelfItemCover(item, AppDimens.shelfCoverWidthCompact)
        Spacer(modifier = Modifier.width(AppDimens.shelfCoverNameSpacing))
        Column(modifier = Modifier.weight(1f)) {
            BookshelfItemTitleRow(item, config, AppDimens.shelfListLoadingSize)
            // 紧凑列表把作者、阅读进度与更新时间并到一行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppDimens.shelfMetaFirstSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookshelfMetaIcon(
                    iconRes = R.drawable.ic_author,
                    contentDescription = stringResource(R.string.author),
                )
                Text(
                    text = item.display.author.orEmpty(),
                    modifier = Modifier.weight(1f, fill = false),
                    color = pageSecondaryTextColor(),
                    fontSize = SHELF_META_TEXT_SIZE.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "•",
                    modifier = Modifier.padding(horizontal = AppDimens.shelfMetaSpacing),
                    color = pageSecondaryTextColor(),
                    fontSize = SHELF_CHIP_TEXT_SIZE.sp,
                )
                Text(
                    text = item.display.durChapterTitle.orEmpty(),
                    modifier = Modifier.weight(1f),
                    color = pageSecondaryTextColor(),
                    fontSize = SHELF_META_TEXT_SIZE.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.lastUpdateText?.let {
                    Text(
                        text = it,
                        color = pageSecondaryTextColor(),
                        fontSize = SHELF_META_TEXT_SIZE.sp,
                        maxLines = 1,
                    )
                }
            }
            BookshelfMetaLine(
                iconRes = R.drawable.ic_book_last,
                contentDescription = stringResource(R.string.lasted_show),
                text = item.display.latestChapterTitle,
                spacing = AppDimens.shelfMetaSpacing,
            )
            BookshelfReadProgress(item.readProgress)
        }
    }
}

/**
 * 条目容器：点击/长按、外边框与内边距。
 *
 * 开启"显示外边框"时用主题背景色半透明填充 + 同色系描边（对齐 View 侧 `bookBorderBackground`），
 * 并留出更大的内边距；未开启时不画背景，保持壁纸透出。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookshelfItemContainer(
    config: BookshelfDisplayConfig,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val shape = RoundedCornerShape(AppDimens.shelfItemBorderCornerRadius)
    val contrast = if (background.luminance() > 0.5f) Color.Black else Color.White
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (config.showBookBorder) {
                    Modifier
                        .clip(shape)
                        .background(background.copy(alpha = AppDimens.SHELF_ITEM_BORDER_FILL_ALPHA))
                        .border(
                            width = AppDimens.shelfItemBorderWidth,
                            color = lerp(background, contrast, AppDimens.SHELF_ITEM_BORDER_BLEND),
                            shape = shape,
                        )
                } else {
                    Modifier
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(
                if (config.showBookBorder) AppDimens.shelfItemBorderPadding
                else AppDimens.shelfItemPadding
            ),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

@Composable
private fun RowScope.BookshelfItemCover(item: BookshelfBookItem, width: Dp) {
    AppBookCover(
        name = item.display.name,
        author = item.display.author,
        coverPath = item.display.getDisplayCover(),
        galleryIdentity = item.display.bookUrl,
        sourceOrigin = item.display.origin,
        contentDescription = item.display.name,
        modifier = Modifier
            .width(width)
            .aspectRatio(1f / AppDimens.BOOK_COVER_ASPECT),
    )
}

/** 书名行：书名占满剩余宽度，右侧是未读角标或更新中转圈 */
@Composable
private fun BookshelfItemTitleRow(
    item: BookshelfBookItem,
    config: BookshelfDisplayConfig,
    loadingSize: Dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.display.name,
            modifier = Modifier
                .weight(1f)
                .padding(start = AppDimens.shelfTitleStartPadding),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = SHELF_TITLE_TEXT_SIZE.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BookshelfItemStatus(
            item = item,
            showUnread = config.showUnread,
            loadingSize = loadingSize,
            modifier = Modifier.padding(start = AppDimens.shelfBadgeRowSpacing),
        )
    }
}

@Composable
private fun BookshelfMetaLine(
    iconRes: Int,
    contentDescription: String,
    text: String?,
    spacing: Dp,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookshelfMetaIcon(iconRes = iconRes, contentDescription = contentDescription)
        Text(
            text = text.orEmpty(),
            modifier = Modifier.weight(1f),
            color = pageSecondaryTextColor(),
            fontSize = SHELF_META_TEXT_SIZE.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                color = pageSecondaryTextColor(),
                fontSize = SHELF_META_TEXT_SIZE.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BookshelfMetaIcon(iconRes: Int, contentDescription: String) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        tint = pageSecondaryTextColor(),
        modifier = Modifier
            .size(AppDimens.shelfMetaIconSize)
            .padding(AppDimens.shelfMetaIconPadding),
    )
}

/** 阅读进度条与百分比：进度为 null（未读或开关关闭）时整行隐藏 */
@Composable
private fun BookshelfReadProgress(progress: Float?) {
    if (progress == null) return
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
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
            maxLines = 1,
        )
    }
}

/** 标签 / 字数 / 分类胶囊，自动换行 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookshelfChips(chips: List<BookshelfChip>, showBorder: Boolean) {
    if (chips.isEmpty()) return
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppDimens.shelfTagRowsSpacing),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.shelfTagChipSpacing),
        verticalArrangement = Arrangement.spacedBy(AppDimens.shelfTagChipVerticalSpacing),
    ) {
        chips.forEach { chip ->
            val shape = RoundedCornerShape(AppDimens.shelfTagChipCornerRadius)
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

/**
 * 未读角标 / 更新中转圈。
 *
 * 更新中优先显示转圈（与 View 版一致）；角标在有新章节时用强调色，否则用中性灰。
 * 数量为 0 时整体隐藏。
 */
@Composable
internal fun BookshelfItemStatus(
    item: BookshelfBookItem,
    showUnread: Boolean,
    loadingSize: Dp,
    modifier: Modifier = Modifier,
) {
    if (item.isUpdating) {
        CircularProgressIndicator(
            modifier = modifier.size(loadingSize),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = AppDimens.shelfLoadingStrokeWidth,
        )
        return
    }
    if (!showUnread || item.unreadCount <= 0) return
    val badgeColor = if (item.hasNewChapter) {
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
            text = item.unreadCount.toString(),
            color = textColor,
            fontSize = SHELF_CHIP_TEXT_SIZE.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
