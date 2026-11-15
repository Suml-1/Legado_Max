package io.legado.app.ui.main.bookshelf.style1.books

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.main.bookshelf.compose.BookshelfBookItem
import io.legado.app.ui.widget.components.AppBookCover

/** 层板厚度 */
private val BoardHeight = 10.dp

/** 层板左右内缩 */
private val BoardSidePadding = 6.dp

/** 侧边阴影条宽度 */
private val SpineEdge = 3.dp

/**
 * 3D 立本多层书架。
 *
 * 书脊一层一层立在木质层板上，纵向滚动；每排本数按屏幕宽度与书脊宽度档位自适应。
 * 书脊宽窄、书籍高矮、层间距都可取自书架设置档位。底部内边距让出主导航栏，
 * 内容不会顶到屏幕最下方被按钮压住。点击时书脊上浮、放大并轻微翻转，模拟取出。
 * 低端设备 / 墨水屏已在配置层回退到普通列表或网格。
 */
@Composable
internal fun Bookshelf3DShelfContent(
    shelfItems: List<BookshelfBookItem>,
    bottomPaddingPx: Int,
    onScrollBackwardChange: (Boolean) -> Unit,
    onBookClick: (BookshelfBookItem) -> Unit,
    onBookLongClick: (BookshelfBookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val canScrollBack by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    LaunchedEffect(canScrollBack) {
        onScrollBackwardChange(canScrollBack)
    }

    val density = LocalDensity.current
    val targetSpine = when (AppConfig.bookshelf3dWidth) {
        1 -> 44.dp
        3 -> 70.dp
        else -> 56.dp
    }
    val spineHeight = when (AppConfig.bookshelf3dHeight) {
        1 -> 150.dp
        3 -> 200.dp
        else -> 176.dp
    }
    val bookGap = when (AppConfig.bookshelf3dSpacing) {
        1 -> 6.dp
        3 -> 10.dp
        else -> 8.dp
    }
    val layerGap = when (AppConfig.bookshelf3dSpacing) {
        1 -> 12.dp
        3 -> 22.dp
        else -> 16.dp
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableWidth = maxWidth - 24.dp
        val columns = ((availableWidth + bookGap) / (targetSpine + bookGap)).toInt()
            .coerceIn(3, 6)
        val groups = remember(shelfItems, columns) { shelfItems.chunked(columns) }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = with(density) { bottomPaddingPx.toDp() },
            ),
            verticalArrangement = Arrangement.spacedBy(layerGap),
        ) {
            items(items = groups, key = { it.first().key }) { group ->
                ShelfRow(
                    group = group,
                    spineHeight = spineHeight,
                    bookGap = bookGap,
                    onBookClick = onBookClick,
                    onBookLongClick = onBookLongClick,
                )
            }
        }
    }
}

@Composable
private fun ShelfRow(
    group: List<BookshelfBookItem>,
    spineHeight: Dp,
    bookGap: Dp,
    onBookClick: (BookshelfBookItem) -> Unit,
    onBookLongClick: (BookshelfBookItem) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(bookGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            group.forEach { bookItem ->
                BookSpine(
                    bookItem = bookItem,
                    spineHeight = spineHeight,
                    onClick = onBookClick,
                    onLongClick = onBookLongClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // 木质层板
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BoardSidePadding)
                .height(BoardHeight)
                .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f))
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookSpine(
    bookItem: BookshelfBookItem,
    spineHeight: Dp,
    onClick: (BookshelfBookItem) -> Unit,
    onLongClick: (BookshelfBookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val display = bookItem.display
    var lifted by remember { mutableStateOf(false) }
    val elevation by animateFloatAsState(
        targetValue = if (lifted) 20f else 5f,
        label = "spineElevation",
    )
    val scale by animateFloatAsState(
        targetValue = if (lifted) 1.06f else 1f,
        label = "spineScale",
    )
    val spinY by animateFloatAsState(
        targetValue = if (lifted) -12f else 0f,
        label = "spineRotationY",
    )
    val spineColor = spineColor(bookItem.key)
    val onSpine = readableOn(spineColor)

    Box(
        modifier = modifier
            .height(spineHeight)
            .graphicsLayer {
                shadowElevation = elevation
                scaleX = scale
                scaleY = scale
                rotationY = spinY
                cameraDistance = 12f * density
            }
            .clip(
                RoundedCornerShape(
                    topStart = 3.dp,
                    topEnd = 3.dp,
                    bottomEnd = 1.dp,
                    bottomStart = 1.dp,
                )
            )
            .background(spineColor)
            .combinedClickable(
                onClick = {
                    lifted = true
                    onClick(bookItem)
                },
                onLongClick = { onLongClick(bookItem) },
            )
    ) {
        AppBookCover(
            modifier = Modifier.fillMaxSize(),
            name = display.name,
            author = display.author,
            coverPath = display.getDisplayCover(),
            galleryIdentity = display.bookUrl,
            sourceOrigin = display.origin,
            contentDescription = display.name,
        )
        // 书名竖排居中，对比色；居中一条半透明衬底保证在花哨封面上也能看清
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 3.dp)
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                display.name.take(10).forEach { ch ->
                    Text(
                        text = ch.toString(),
                        color = onSpine,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // 底部作者小字
        if (!display.author.isNullOrBlank()) {
            Text(
                text = display.author,
                color = onSpine.copy(alpha = 0.75f),
                fontSize = 8.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 3.dp, vertical = 4.dp)
                    .fillMaxWidth(),
            )
        }
        // 左侧亮边 / 右侧暗边，模拟书脊厚度
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(SpineEdge)
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.18f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(SpineEdge)
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
        )
    }
}

/** 按书籍身份派生一个柔和的书脊底色（封面缺失时兜底） */
private fun spineColor(identity: String): Color {
    val hue = ((identity.hashCode() % 360) + 360) % 360
    return Color.hsl(hue.toFloat(), 0.38f, 0.52f)
}

/** 根据底色亮度选对比色：浅底用深字，深底用白字，保证可读 */
private fun readableOn(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.6f) Color(0xFF262626) else Color.White
}
