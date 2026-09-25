package io.legado.app.ui.main.bookshelf.style1.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.main.bookshelf.compose.BookshelfBookItem
import io.legado.app.ui.main.bookshelf.compose.BookshelfDisplayConfig
import io.legado.app.ui.main.bookshelf.compose.BookshelfGridItem
import io.legado.app.ui.main.bookshelf.compose.BookshelfListItem
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.VerticalScrollbar

/**
 * 书架（style1 单个分组）的 Compose 列表内容。
 *
 * 只负责渲染与滚动：数据、配置与"回到顶部"信号都由 Fragment 下发，
 * 滚动是否越过头部的状态回抛给 Fragment（下拉刷新需要用它判断能否下拉）。
 */
@Composable
internal fun BookshelfShelfContent(
    shelfItems: List<BookshelfBookItem>,
    displayConfig: BookshelfDisplayConfig,
    bottomPaddingPx: Int,
    scrollToTopTick: Int,
    immediateScrollToTopTick: Int,
    onScrollBackwardChange: (Boolean) -> Unit,
    onBookClick: (BookshelfBookItem) -> Unit,
    onBookLongClick: (BookshelfBookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (displayConfig.isGrid) {
        BookshelfGridContent(
            modifier = modifier,
            shelfItems = shelfItems,
            displayConfig = displayConfig,
            bottomPaddingPx = bottomPaddingPx,
            scrollToTopTick = scrollToTopTick,
            immediateScrollToTopTick = immediateScrollToTopTick,
            onScrollBackwardChange = onScrollBackwardChange,
            onBookClick = onBookClick,
            onBookLongClick = onBookLongClick,
        )
    } else {
        BookshelfListContent(
            modifier = modifier,
            shelfItems = shelfItems,
            displayConfig = displayConfig,
            bottomPaddingPx = bottomPaddingPx,
            scrollToTopTick = scrollToTopTick,
            immediateScrollToTopTick = immediateScrollToTopTick,
            onScrollBackwardChange = onScrollBackwardChange,
            onBookClick = onBookClick,
            onBookLongClick = onBookLongClick,
        )
    }
}

@Composable
private fun BookshelfListContent(
    modifier: Modifier,
    shelfItems: List<BookshelfBookItem>,
    displayConfig: BookshelfDisplayConfig,
    bottomPaddingPx: Int,
    scrollToTopTick: Int,
    immediateScrollToTopTick: Int,
    onScrollBackwardChange: (Boolean) -> Unit,
    onBookClick: (BookshelfBookItem) -> Unit,
    onBookLongClick: (BookshelfBookItem) -> Unit,
) {
    val listState = rememberLazyListState()
    val spacing = rememberShelfSpacing(displayConfig.marginPx, bottomPaddingPx)
    val canScrollBack by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    LaunchedEffect(canScrollBack) {
        onScrollBackwardChange(canScrollBack)
    }
    LaunchedEffect(immediateScrollToTopTick) {
        if (immediateScrollToTopTick > 0) listState.scrollToItem(0)
    }
    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick > 0) {
            if (AppConfig.isEInkMode) listState.scrollToItem(0)
            else listState.animateScrollToItem(0)
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppDimens.shelfContentHorizontalPadding,
                top = spacing.topPadding,
                end = AppDimens.shelfContentHorizontalPadding,
                bottom = spacing.bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.itemSpacing),
        ) {
            items(items = shelfItems, key = { it.key }) { bookItem ->
                BookshelfListItem(
                    bookItem = bookItem,
                    displayConfig = displayConfig,
                    onClick = onBookClick,
                    onLongClick = onBookLongClick,
                )
            }
        }
        if (displayConfig.fastScrollerEnabled) {
            VerticalScrollbar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun BookshelfGridContent(
    modifier: Modifier,
    shelfItems: List<BookshelfBookItem>,
    displayConfig: BookshelfDisplayConfig,
    bottomPaddingPx: Int,
    scrollToTopTick: Int,
    immediateScrollToTopTick: Int,
    onScrollBackwardChange: (Boolean) -> Unit,
    onBookClick: (BookshelfBookItem) -> Unit,
    onBookLongClick: (BookshelfBookItem) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val spacing = rememberShelfSpacing(displayConfig.marginPx, bottomPaddingPx)
    val canScrollBack by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
        }
    }
    LaunchedEffect(canScrollBack) {
        onScrollBackwardChange(canScrollBack)
    }
    LaunchedEffect(immediateScrollToTopTick) {
        if (immediateScrollToTopTick > 0) gridState.scrollToItem(0)
    }
    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick > 0) {
            if (AppConfig.isEInkMode) gridState.scrollToItem(0)
            else gridState.animateScrollToItem(0)
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(displayConfig.bookLayout.coerceAtLeast(2)),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppDimens.shelfContentHorizontalPadding + spacing.itemMargin,
                top = spacing.topPadding,
                end = AppDimens.shelfContentHorizontalPadding + spacing.itemMargin,
                bottom = spacing.bottomPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(spacing.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(spacing.itemSpacing),
        ) {
            items(items = shelfItems, key = { it.key }) { bookItem ->
                BookshelfGridItem(
                    bookItem = bookItem,
                    displayConfig = displayConfig,
                    onClick = onBookClick,
                    onLongClick = onBookLongClick,
                )
            }
        }
        if (displayConfig.fastScrollerEnabled) {
            VerticalScrollbar(
                state = gridState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

/**
 * 列表 / 网格共用的间距。
 *
 * 对齐原 ItemDecoration 的口径：条目四周各留一个 margin（相邻条目之间即两个 margin），
 * 首个条目额外留出顶部空间，底部再叠加主导航栏高度以让内容能滚到底栏之上。
 */
@Composable
private fun rememberShelfSpacing(marginPx: Int, bottomPaddingPx: Int): ShelfSpacing {
    val density = LocalDensity.current
    return remember(marginPx, bottomPaddingPx, density) {
        with(density) {
            ShelfSpacing(
                itemMargin = marginPx.toDp(),
                itemSpacing = (marginPx * 2).toDp(),
                topPadding = (marginPx + AppDimens.shelfFirstItemExtraTop.toPx()).toDp(),
                bottomPadding = (marginPx + bottomPaddingPx).toDp(),
            )
        }
    }
}

/** 由显示配置换算出的间距 */
private data class ShelfSpacing(
    val itemMargin: Dp,
    val itemSpacing: Dp,
    val topPadding: Dp,
    val bottomPadding: Dp,
)
