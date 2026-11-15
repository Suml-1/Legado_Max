package io.legado.app.ui.main.bookshelf.style1.books

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.main.bookshelf.compose.BookshelfBookItem
import io.legado.app.ui.widget.components.AppBookCover
import kotlin.math.abs

/** 浅米白底（参考款书架背景） */
private val ShelfBackground = Color(0xFFF5F3EF)

private fun lerp(start: Float, stop: Float, fraction: Float) =
    start + (stop - start) * fraction

/**
 * 横向封面轮播书架：一排封面卡片，居中本放大清晰，两侧缩小变淡；滑动带居中吸附。
 * 封面走现成封面组件（本地图集 → 联网 → 缓存 → 默认兜底），点书进入阅读，长按保留。
 * 信息条可在顶部或底部显示，跟随真正居中的那本。
 */
@Composable
internal fun Bookshelf3DShelfContent(
    shelfItems: List<BookshelfBookItem>,
    bottomPaddingPx: Int,
    marginPx: Int,
    onScrollBackwardChange: (Boolean) -> Unit,
    onBookClick: (BookshelfBookItem) -> Unit,
    onBookLongClick: (BookshelfBookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val infoBarTop = AppConfig.bookshelf3dInfoBarTop

    val canScrollBack by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    LaunchedEffect(canScrollBack) {
        onScrollBackwardChange(canScrollBack)
    }

    // 真正居中的那一本
    val centerIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = layoutInfo.viewportEndOffset / 2
            var closest = 0
            var minDistance = Float.MAX_VALUE
            layoutInfo.visibleItemsInfo.forEach { item ->
                val distance = abs(item.offset + item.size / 2 - viewportCenter)
                if (distance < minDistance) {
                    minDistance = distance.toFloat()
                    closest = item.index
                }
            }
            closest
        }
    }

    val coverW = 132.dp
    val coverH = coverW * 166 / 118
    val coverWpx = with(density) { coverW.toPx() }
    val spacingDp = 16.dp
    val spacingPx = with(density) { spacingDp.toPx() }
    val maxDistance = coverWpx + with(density) { 100.dp.toPx() }

    Column(modifier = modifier.fillMaxSize().background(ShelfBackground)) {
        if (infoBarTop) {
            CurrentBookBar(
                bookItem = shelfItems.getOrNull(centerIndex),
                bottomPaddingPx = bottomPaddingPx,
                marginPx = marginPx,
                density = density,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacingDp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            contentPadding = PaddingValues(
                horizontal = with(density) {
                    (listState.layoutInfo.viewportEndOffset / 2f - coverWpx / 2f).toDp()
                },
            ),
        ) {
            itemsIndexed(items = shelfItems, key = { _, it -> it.key }) { index, bookItem ->
                val layoutInfo = listState.layoutInfo
                val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == index }
                val viewportCenter = layoutInfo.viewportEndOffset / 2f
                val distance = if (visibleItem != null) {
                    abs(visibleItem.offset + visibleItem.size / 2 - viewportCenter)
                } else {
                    abs(index - centerIndex) * (coverWpx + spacingPx)
                }
                val normalized = (distance / maxDistance).coerceIn(0f, 1f)
                val targetScale = lerp(1f, 0.82f, normalized)
                val targetAlpha = lerp(1f, 0.45f, normalized)

                BookCoverCard(
                    index = index,
                    bookItem = bookItem,
                    coverW = coverW,
                    coverH = coverH,
                    scale = targetScale,
                    alpha = targetAlpha,
                    onClick = { onBookClick(bookItem) },
                    onLongClick = { onBookLongClick(bookItem) },
                )
            }
        }
        if (!infoBarTop) {
            CurrentBookBar(
                bookItem = shelfItems.getOrNull(centerIndex),
                bottomPaddingPx = bottomPaddingPx,
                marginPx = marginPx,
                density = density,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCoverCard(
    index: Int,
    bookItem: BookshelfBookItem,
    coverW: Dp,
    coverH: Dp,
    scale: Float,
    alpha: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val display = bookItem.display
    val densityLocal = LocalDensity.current
    val scaleAnimated by animateFloatAsState(
        targetValue = scale, spring(0.7f, 300f), label = "cardScale",
    )
    val alphaAnimated by animateFloatAsState(
        targetValue = alpha, spring(0.7f, 300f), label = "cardAlpha",
    )

    Box(
        modifier = Modifier
            .width(coverW)
            .height(coverH)
            .graphicsLayer {
                scaleX = scaleAnimated
                scaleY = scaleAnimated
                this.alpha = alphaAnimated
                cameraDistance = 12f * densityLocal.density
            }
            .shadow(14.dp, RoundedCornerShape(4.dp), ambientColor = Color.Black.copy(0.45f), spotColor = Color.Black.copy(0.45f))
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
        // 左侧书脊条
        Box(
            modifier = Modifier
                .width(14.dp)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(0.55f), Color.Black.copy(0.20f), Color.Transparent)
                    )
                )
        )
        // 右侧暗边
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .background(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(0.35f)))
                )
        )
        // 顶部高光
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(Brush.verticalGradient(listOf(Color.White.copy(0.20f), Color.Transparent)))
        )
        // 底部书名作者遮罩
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f))))
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(10.dp),
        ) {
            Text(
                text = display.name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                lineHeight = 15.sp,
                overflow = TextOverflow.Ellipsis,
            )
            if (!display.author.isNullOrBlank()) {
                Text(
                    text = display.author,
                    color = Color.White.copy(0.7f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 编号角标
        Box(
            modifier = Modifier
                .padding(6.dp)
                .align(Alignment.TopStart)
                .background(Color.Black.copy(0.55f), RoundedCornerShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        ) {
            Text(
                text = "NO.%03d".format(index + 1),
                color = Color.White.copy(0.85f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** 当前居中书的书名 / 作者信息条 */
@Composable
private fun CurrentBookBar(
    bookItem: BookshelfBookItem?,
    bottomPaddingPx: Int,
    marginPx: Int,
    density: Density,
    modifier: Modifier = Modifier,
) {
    val display = bookItem?.display ?: return
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = with(density) { (marginPx + bottomPaddingPx).toDp() }),
    ) {
        Text(
            text = display.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!display.author.isNullOrBlank()) {
            Text(
                text = display.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
