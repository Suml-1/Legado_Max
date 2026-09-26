package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.AppBookCover

/** 网格书名显示位置：0 封面下方 / 1 不显示 / 2 叠在封面上（与 AppConfig.showBookname 取值一致） */
private const val SHOW_BOOK_NAME_BELOW = 0
private const val SHOW_BOOK_NAME_ON_COVER = 2

/** 网格书名：下方 12sp 两行居中，封面上 11sp 两行左对齐 */
private const val SHELF_GRID_NAME_TEXT_SIZE = 12
private const val SHELF_GRID_OVERLAY_NAME_TEXT_SIZE = 11

/** 封面底部渐变遮罩，对齐原 bg_gradient_cover（底部黑 63% → 30% 处 38% → 顶部透明） */
private val gridNameOverlayBrush = Brush.verticalGradient(
    0.0f to Color.Transparent,
    0.3f to Color.Black.copy(alpha = 0.38f),
    1.0f to Color.Black.copy(alpha = 0.63f)
)

/**
 * 书架网格条目。
 *
 * 与 View 版网格适配器一致：封面、未读角标或更新中转圈、封面底部阅读进度条，
 * 书名的位置由"书名显示位置"开关决定（封面下方 / 不显示 / 叠在封面上）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfGridItem(
    modifier: Modifier = Modifier,
    bookItem: BookshelfBookItem,
    displayConfig: BookshelfDisplayConfig,
    onClick: (BookshelfBookItem) -> Unit,
    onLongClick: (BookshelfBookItem) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick(bookItem) },
                onLongClick = { onLongClick(bookItem) },
            )
            .padding(AppDimens.shelfGridItemPadding),
    ) {
        Box {
            AppBookCover(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / AppDimens.BOOK_COVER_ASPECT),
                name = bookItem.display.name,
                author = bookItem.display.author,
                coverPath = bookItem.display.getDisplayCover(),
                galleryIdentity = bookItem.display.bookUrl,
                sourceOrigin = bookItem.display.origin,
                contentDescription = bookItem.display.name,
            )
            bookItem.readProgress?.let { progress ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(AppDimens.shelfProgressThickness)
                        .background(
                            MaterialTheme.colorScheme.primary
                                .copy(alpha = AppDimens.SHELF_PROGRESS_TRACK_ALPHA)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(AppDimens.shelfProgressThickness)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            BookshelfItemStatus(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(AppDimens.shelfBadgeMargin),
                bookItem = bookItem,
                showUnread = displayConfig.showUnread,
                loadingSize = AppDimens.shelfGridLoadingSize,
            )
            if (displayConfig.showBookName == SHOW_BOOK_NAME_ON_COVER) {
                Text(
                    text = bookItem.display.name,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(gridNameOverlayBrush)
                        .padding(
                            start = AppDimens.shelfGridOverlayNamePadding,
                            end = AppDimens.shelfGridOverlayNamePadding,
                            top = AppDimens.shelfMetaFirstSpacing,
                            bottom = AppDimens.shelfGridOverlayNamePadding,
                        ),
                    color = Color.White,
                    fontSize = SHELF_GRID_OVERLAY_NAME_TEXT_SIZE.sp,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (displayConfig.showBookName == SHOW_BOOK_NAME_BELOW) {
            Text(
                text = bookItem.display.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppDimens.shelfGridNameSpacing),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = SHELF_GRID_NAME_TEXT_SIZE.sp,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
