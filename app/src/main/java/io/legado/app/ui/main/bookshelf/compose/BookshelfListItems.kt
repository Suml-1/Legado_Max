package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.composePanelShape
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.widget.components.AppBookCover

/**
 * 书架列表条目（标准列表 / 紧凑列表）。
 *
 * 与 View 版两个列表适配器渲染同样的内容：封面、未读角标或更新中转圈、书名、作者、
 * 阅读进度、最新章节、最后更新时间、标签与简介，全部由 [displayConfig] 控制显隐。
 */
@Composable
fun BookshelfListItem(
    modifier: Modifier = Modifier,
    bookItem: BookshelfBookItem,
    displayConfig: BookshelfDisplayConfig,
    onClick: (BookshelfBookItem) -> Unit,
    onLongClick: (BookshelfBookItem) -> Unit,
) {
    if (displayConfig.isCompactList) {
        BookshelfCompactListItem(modifier, bookItem, displayConfig, onClick, onLongClick)
    } else {
        BookshelfStandardListItem(modifier, bookItem, displayConfig, onClick, onLongClick)
    }
}

@Composable
private fun BookshelfStandardListItem(
    modifier: Modifier,
    bookItem: BookshelfBookItem,
    displayConfig: BookshelfDisplayConfig,
    onClick: (BookshelfBookItem) -> Unit,
    onLongClick: (BookshelfBookItem) -> Unit,
) {
    BookshelfItemContainer(
        modifier = modifier,
        displayConfig = displayConfig,
        onClick = { onClick(bookItem) },
        onLongClick = { onLongClick(bookItem) },
    ) {
        BookshelfItemCover(bookItem, AppDimens.shelfCoverWidth)
        Spacer(modifier = Modifier.width(AppDimens.shelfCoverNameSpacing))
        Column(modifier = Modifier.weight(1f)) {
            BookshelfItemTitleRow(bookItem, displayConfig, AppDimens.shelfListLoadingSize)
            BookshelfMetaLine(
                iconRes = R.drawable.ic_author,
                contentDescription = stringResource(R.string.author),
                text = bookItem.display.author,
                spacing = AppDimens.shelfMetaFirstSpacing,
                trailing = bookItem.lastUpdateText,
            )
            BookshelfMetaLine(
                iconRes = R.drawable.ic_history,
                contentDescription = stringResource(R.string.read_dur_progress),
                text = bookItem.display.durChapterTitle,
                spacing = AppDimens.shelfMetaSpacing,
            )
            BookshelfMetaLine(
                iconRes = R.drawable.ic_book_last,
                contentDescription = stringResource(R.string.lasted_show),
                text = bookItem.display.latestChapterTitle,
                spacing = AppDimens.shelfMetaSpacing,
            )
            BookshelfReadProgress(progress = bookItem.readProgress)
            BookshelfChips(chips = bookItem.chips, showBorder = displayConfig.showBookBorder)
            bookItem.intro?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = AppDimens.shelfIntroSpacing),
                    color = pageSecondaryTextColor(),
                    fontSize = SHELF_INTRO_TEXT_SIZE.sp,
                    maxLines = displayConfig.introLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BookshelfCompactListItem(
    modifier: Modifier,
    bookItem: BookshelfBookItem,
    displayConfig: BookshelfDisplayConfig,
    onClick: (BookshelfBookItem) -> Unit,
    onLongClick: (BookshelfBookItem) -> Unit,
) {
    BookshelfItemContainer(
        modifier = modifier,
        displayConfig = displayConfig,
        onClick = { onClick(bookItem) },
        onLongClick = { onLongClick(bookItem) },
    ) {
        BookshelfItemCover(bookItem, AppDimens.shelfCoverWidthCompact)
        Spacer(modifier = Modifier.width(AppDimens.shelfCoverNameSpacing))
        Column(modifier = Modifier.weight(1f)) {
            BookshelfItemTitleRow(bookItem, displayConfig, AppDimens.shelfListLoadingSize)
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
                Spacer(modifier = Modifier.width(AppDimens.shelfMetaIconSpacing))
                Text(
                    text = bookItem.display.author.orEmpty(),
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
                    text = bookItem.display.durChapterTitle.orEmpty(),
                    modifier = Modifier.weight(1f),
                    color = pageSecondaryTextColor(),
                    fontSize = SHELF_META_TEXT_SIZE.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                bookItem.lastUpdateText?.let {
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
                text = bookItem.display.latestChapterTitle,
                spacing = AppDimens.shelfMetaSpacing,
            )
            BookshelfReadProgress(progress = bookItem.readProgress)
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
    modifier: Modifier,
    displayConfig: BookshelfDisplayConfig,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val shape = composePanelShape()
    val contrast = if (background.luminance() > 0.5f) Color.Black else Color.White
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (displayConfig.showBookBorder) {
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
                if (displayConfig.showBookBorder) AppDimens.shelfItemBorderPadding
                else AppDimens.shelfItemPadding
            ),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

@Composable
private fun RowScope.BookshelfItemCover(bookItem: BookshelfBookItem, width: Dp) {
    AppBookCover(
        modifier = Modifier
            .width(width)
            .aspectRatio(1f / AppDimens.BOOK_COVER_ASPECT),
        name = bookItem.display.name,
        author = bookItem.display.author,
        coverPath = bookItem.display.getDisplayCover(),
        galleryIdentity = bookItem.display.bookUrl,
        sourceOrigin = bookItem.display.origin,
        contentDescription = bookItem.display.name,
    )
}

/** 书名行：书名占满剩余宽度，右侧是未读角标或更新中转圈 */
@Composable
private fun BookshelfItemTitleRow(
    bookItem: BookshelfBookItem,
    displayConfig: BookshelfDisplayConfig,
    loadingSize: Dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = bookItem.display.name,
            modifier = Modifier
                .weight(1f)
                .padding(start = AppDimens.shelfTitleStartPadding),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = SHELF_TITLE_TEXT_SIZE.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BookshelfItemStatus(
            modifier = Modifier.padding(start = AppDimens.shelfBadgeRowSpacing),
            bookItem = bookItem,
            showUnread = displayConfig.showUnread,
            loadingSize = loadingSize,
        )
    }
}
