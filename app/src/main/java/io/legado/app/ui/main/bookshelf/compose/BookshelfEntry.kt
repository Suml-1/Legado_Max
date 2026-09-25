package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.AppBookCover

/** 分组（文件夹）条目只保留渲染字段：BookGroup 是可变实体，直接持有会破坏 Compose 的相等性判断 */
@Immutable
data class BookshelfFolderItem(
    val groupId: Long,
    val name: String,
    val cover: String?,
) {
    companion object {
        fun from(group: BookGroup): BookshelfFolderItem = BookshelfFolderItem(
            groupId = group.groupId,
            name = group.groupName,
            cover = group.cover,
        )

        /** 分组封面走封面图集的取图身份，与 View 版 CoverImageView.load(group) 一致 */
        fun coverGalleryIdentity(groupId: Long): String = "bookGroup:$groupId"
    }
}

/** style2 书架的条目：书籍或分组文件夹 */
@Immutable
sealed interface BookshelfEntry {
    val key: String
}

@Immutable
data class BookshelfBookEntry(val book: BookshelfBookItem) : BookshelfEntry {
    override val key: String = "book:${book.key}"
}

@Immutable
data class BookshelfFolderEntry(val folder: BookshelfFolderItem) : BookshelfEntry {
    override val key: String = "folder:${folder.groupId}"
}

private const val SHELF_GRID_FOLDER_NAME_TEXT_SIZE = 12

/**
 * 更新某本书的"更新中"状态；只有状态真的变化时才替换该条目，
 * 其余条目保持同一实例，让 Compose 跳过它们的重组。
 */
fun updateBookshelfEntryUpdating(
    entries: List<BookshelfEntry>,
    bookUrl: String,
    isUpdating: (String) -> Boolean,
): List<BookshelfEntry> {
    var changed = false
    val updated = entries.map { entry ->
        if (entry !is BookshelfBookEntry) return@map entry
        val nextBook = updateBookshelfBookUpdating(listOf(entry.book), bookUrl, isUpdating).first()
        if (nextBook === entry.book) return@map entry
        changed = true
        BookshelfBookEntry(nextBook)
    }
    return if (changed) updated else entries
}

/**
 * 分组文件夹条目。
 *
 * 与 View 版两种文件夹布局一致：列表样式是「封面 + 分组名」的横排，
 * 网格样式是「封面 + 下方分组名」；文件夹不显示未读角标与章节信息。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfFolderItemView(
    modifier: Modifier = Modifier,
    folder: BookshelfFolderItem,
    folderLayout: Int,
    onClick: (BookshelfFolderItem) -> Unit,
    onLongClick: (BookshelfFolderItem) -> Unit,
) {
    if (folderLayout >= 2) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick(folder) },
                    onLongClick = { onLongClick(folder) },
                )
                .padding(AppDimens.shelfGridItemPadding),
        ) {
            Box(modifier = Modifier.padding(AppDimens.shelfGridCoverMargin)) {
                FolderCover(modifier = Modifier.fillMaxWidth(), folder = folder)
            }
            Text(
                text = folder.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppDimens.shelfGridNameSpacing),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = SHELF_GRID_FOLDER_NAME_TEXT_SIZE.sp,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick(folder) },
                    onLongClick = { onLongClick(folder) },
                ),
        ) {
            Box(
                modifier = Modifier.padding(
                    start = AppDimens.shelfFolderCoverStartMargin,
                    top = AppDimens.shelfFolderCoverTopMargin,
                    bottom = AppDimens.shelfFolderCoverBottomMargin,
                )
            ) {
                FolderCover(modifier = Modifier.width(AppDimens.shelfCoverWidth), folder = folder)
            }
            Spacer(modifier = Modifier.width(AppDimens.shelfCoverNameSpacing))
            Text(
                text = folder.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        top = AppDimens.shelfFolderCoverTopMargin,
                        bottom = AppDimens.shelfMetaSpacing,
                        start = AppDimens.shelfTitleStartPadding,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FolderCover(modifier: Modifier, folder: BookshelfFolderItem) {
    AppBookCover(
        modifier = modifier.aspectRatio(1f / AppDimens.BOOK_COVER_ASPECT),
        name = folder.name,
        author = null,
        coverPath = folder.cover,
        galleryIdentity = BookshelfFolderItem.coverGalleryIdentity(folder.groupId),
        contentDescription = folder.name,
    )
}
