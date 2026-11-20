package io.legado.app.ui.main.bookshelf.style2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import io.legado.app.ui.main.bookshelf.compose.BookshelfBookEntry
import io.legado.app.ui.main.bookshelf.compose.BookshelfDisplayConfig
import io.legado.app.ui.main.bookshelf.compose.BookshelfEntry
import io.legado.app.ui.main.bookshelf.compose.BookshelfFolderEntry
import io.legado.app.ui.main.bookshelf.compose.BookshelfFolderItemView
import io.legado.app.ui.main.bookshelf.compose.BookshelfGridItem
import io.legado.app.ui.main.bookshelf.compose.BookshelfListItem
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.VerticalScrollbar

/**
 * 书架（style2 文件夹继承树）的 Compose 列表内容。
 *
 * 只负责渲染与滚动：条目、配置与"回到顶部"信号都由 Fragment 下发，滚动是否越过头部的
 * 状态回抛给 Fragment（下拉刷新需要用它判断能否下拉）。
 *
 * 每个分组各自保留一分列表状态，退出再进入分组时能回到原来的滚动位置
 * （对齐 View 版按分组保存/恢复 LayoutManager 状态）。
 */
@Composable
internal fun BookshelfShelfTreeContent(
    shelfEntries: List<BookshelfEntry>,
    displayConfig: BookshelfDisplayConfig,
    groupId: Long,
    bottomPaddingPx: Int,
    scrollToTopTick: Int,
    immediateScrollToTopTick: Int,
    onScrollBackwardChange: (Boolean) -> Unit,
    onEntryClick: (BookshelfEntry) -> Unit,
    onEntryLongClick: (BookshelfEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupListStates = remember { hashMapOf<Long, LazyListState>() }
    val groupGridStates = remember { hashMapOf<Long, LazyGridState>() }
    if (displayConfig.useSpanGrid) {
        BookshelfGridContent(
            modifier = modifier,
            shelfEntries = shelfEntries,
            displayConfig = displayConfig,
            listState = remember(groupId) {
                groupGridStates.getOrPut(groupId) { LazyGridState() }
            },
            bottomPaddingPx = bottomPaddingPx,
            scrollToTopTick = scrollToTopTick,
            immediateScrollToTopTick = immediateScrollToTopTick,
            onScrollBackwardChange = onScrollBackwardChange,
            onEntryClick = onEntryClick,
            onEntryLongClick = onEntryLongClick,
        )
    } else {
        BookshelfListContent(
            modifier = modifier,
            shelfEntries = shelfEntries,
            displayConfig = displayConfig,
            listState = remember(groupId) {
                groupListStates.getOrPut(groupId) { LazyListState() }
            },
            bottomPaddingPx = bottomPaddingPx,
            scrollToTopTick = scrollToTopTick,
            immediateScrollToTopTick = immediateScrollToTopTick,
            onScrollBackwardChange = onScrollBackwardChange,
            onEntryClick = onEntryClick,
            onEntryLongClick = onEntryLongClick,
        )
    }
}

@Composable
private fun BookshelfListContent(
    modifier: Modifier,
    shelfEntries: List<BookshelfEntry>,
    displayConfig: BookshelfDisplayConfig,
    listState: LazyListState,
    bottomPaddingPx: Int,
    scrollToTopTick: Int,
    immediateScrollToTopTick: Int,
    onScrollBackwardChange: (Boolean) -> Unit,
    onEntryClick: (BookshelfEntry) -> Unit,
    onEntryLongClick: (BookshelfEntry) -> Unit,
) {
    val spacing = rememberShelfSpacing(displayConfig.marginPx, bottomPaddingPx)
    // 底栏浮在内容之上，滚动条轨道要一起收在底栏之上，滚到底部时拖柄才不会被盖住
    val scrollbarBottomInset = with(LocalDensity.current) { bottomPaddingPx.toDp() }
    // remember 必须以 listState 为 key：分组切换会换用新的滚动状态实例，
    // 无 key 时 derivedStateOf 一直观察首个实例，回抛给 SwipeRefresh 的
    // canChildScrollUp 回调会被冻结（滚到底后滑不上去、下拉误触刷新）。
    // Fragment 侧只持单个布尔，新实例的初值与旧值相同时无需重通知
    val canScrollBack by remember(listState) {
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
            items(items = shelfEntries, key = { it.key }) { entry ->
                BookshelfEntryItem(
                    entry = entry,
                    displayConfig = displayConfig,
                    onClick = onEntryClick,
                    onLongClick = onEntryLongClick,
                )
            }
        }
        if (displayConfig.fastScrollerEnabled) {
            VerticalScrollbar(
                state = listState,
                bottomInset = scrollbarBottomInset,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun BookshelfGridContent(
    modifier: Modifier,
    shelfEntries: List<BookshelfEntry>,
    displayConfig: BookshelfDisplayConfig,
    listState: LazyGridState,
    bottomPaddingPx: Int,
    scrollToTopTick: Int,
    immediateScrollToTopTick: Int,
    onScrollBackwardChange: (Boolean) -> Unit,
    onEntryClick: (BookshelfEntry) -> Unit,
    onEntryLongClick: (BookshelfEntry) -> Unit,
) {
    val spacing = rememberShelfSpacing(displayConfig.marginPx, bottomPaddingPx)
    val scrollbarBottomInset = with(LocalDensity.current) { bottomPaddingPx.toDp() }
    // 同 BookshelfListContent：以 listState 为 key，避免分组切换后回调观察旧状态实例
    val canScrollBack by remember(listState) {
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(displayConfig.spanCount.coerceAtLeast(1)),
            state = listState,
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
            items(
                items = shelfEntries,
                key = { it.key },
                span = { entry ->
                    GridItemSpan(
                        if (entry is BookshelfFolderEntry) displayConfig.folderGridSpan()
                        else displayConfig.bookGridSpan()
                    )
                },
            ) { entry ->
                BookshelfEntryItem(
                    entry = entry,
                    displayConfig = displayConfig,
                    onClick = onEntryClick,
                    onLongClick = onEntryLongClick,
                )
            }
        }
        if (displayConfig.fastScrollerEnabled) {
            VerticalScrollbar(
                state = listState,
                bottomInset = scrollbarBottomInset,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun BookshelfEntryItem(
    entry: BookshelfEntry,
    displayConfig: BookshelfDisplayConfig,
    onClick: (BookshelfEntry) -> Unit,
    onLongClick: (BookshelfEntry) -> Unit,
) {
    when (entry) {
        is BookshelfBookEntry -> if (displayConfig.isGrid) {
            BookshelfGridItem(
                bookItem = entry.book,
                displayConfig = displayConfig,
                onClick = { onClick(entry) },
                onLongClick = { onLongClick(entry) },
            )
        } else {
            BookshelfListItem(
                bookItem = entry.book,
                displayConfig = displayConfig,
                onClick = { onClick(entry) },
                onLongClick = { onLongClick(entry) },
            )
        }

        is BookshelfFolderEntry -> BookshelfFolderItemView(
            folder = entry.folder,
            folderLayout = displayConfig.folderLayout,
            onClick = { onClick(entry) },
            onLongClick = { onLongClick(entry) },
        )
    }
}

/**
 * 列表 / 网格共用的间距：条目四周各留一个 margin（相邻条目之间即两个 margin），
 * 首个条目额外留出顶部空间，底部再叠加主导航栏高度。
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
