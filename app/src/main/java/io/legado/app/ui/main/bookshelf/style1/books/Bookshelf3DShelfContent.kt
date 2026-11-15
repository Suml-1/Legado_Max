package io.legado.app.ui.main.bookshelf.style1.books

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.main.bookshelf.compose.BookshelfBookItem
import io.legado.app.ui.widget.components.AppBookCover

/** 书脊宽度 */
private val SpineWidth = 48.dp

/** 书脊高度 */
private val SpineHeight = 190.dp

/** 书脊厚度（侧边阴影条宽度） */
private val SpineEdge = 3.dp

/** 层板厚度 */
private val ShelfBoardHeight = 12.dp

/**
 * 3D 立本书架内容。
 *
 * 书脊横向立排在一条层板上，带轻微透视与阴影；点击时书脊上浮、放大并轻微翻转，
 * 模拟"从架上取出"的反馈，随后进入书籍详情。封面缺失时用书脊底色 + 竖排书名兜底。
 *
 * 仅在 [io.legado.app.help.config.AppConfig.canUse3DBookshelf] 为真时才由上层分发到这里，
 * 低端设备 / 墨水屏已在配置层回退到普通列表或网格。
 */
@Composable
internal fun Bookshelf3DShelfContent(
    shelfItems: List<BookshelfBookItem>,
    onScrollBackwardChange: (Boolean) -> Unit,
    onBookClick: (BookshelfBookItem) -> Unit,
    onBookLongClick: (BookshelfBookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowState = rememberLazyListState()
    val canScrollBack by remember {
        derivedStateOf {
            rowState.firstVisibleItemIndex > 0 || rowState.firstVisibleItemScrollOffset > 0
        }
    }
    LaunchedEffect(canScrollBack) {
        onScrollBackwardChange(canScrollBack)
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 4.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            items(items = shelfItems, key = { it.key }) { bookItem ->
                BookSpine(
                    bookItem = bookItem,
                    onClick = onBookClick,
                    onLongClick = onBookLongClick,
                )
            }
        }
        // 木质层板
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ShelfBoardHeight)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f))
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookSpine(
    bookItem: BookshelfBookItem,
    onClick: (BookshelfBookItem) -> Unit,
    onLongClick: (BookshelfBookItem) -> Unit,
) {
    val display = bookItem.display
    var lifted by remember { mutableStateOf(false) }
    // 取出反馈：上浮、放大、轻微绕 Y 轴翻转
    val elevation by animateFloatAsState(
        targetValue = if (lifted) 20f else 5f,
        label = "spineElevation",
    )
    val scale by animateFloatAsState(
        targetValue = if (lifted) 1.07f else 1f,
        label = "spineScale",
    )
    val rotationY by animateFloatAsState(
        targetValue = if (lifted) -14f else 0f,
        label = "spineRotationY",
    )

    Box(
        modifier = Modifier
            .width(SpineWidth)
            .height(SpineHeight)
            .graphicsLayer {
                shadowElevation = elevation
                scaleX = scale
                scaleY = scale
                rotationY = rotationY
                cameraDistance = 12f * density
            }
            .clip(
                RoundedCornerShape(
                    topStart = 2.dp,
                    topEnd = 2.dp,
                    bottomEnd = 1.dp,
                    bottomStart = 1.dp,
                )
            )
            .background(spineColor(bookItem.key))
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
        // 竖排书名兜底叠层：封面未覆盖时也能在书脊上读到书名
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            display.name.take(7).forEach { ch ->
                Text(
                    text = ch.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                .background(Color.Black.copy(alpha = 0.28f))
        )
    }
}

/** 按书籍身份派生一个柔和的书脊底色（封面缺失时兜底） */
private fun spineColor(identity: String): Color {
    val hue = ((identity.hashCode() % 360) + 360) % 360
    return Color.hsl(hue.toFloat(), 0.38f, 0.52f)
}
