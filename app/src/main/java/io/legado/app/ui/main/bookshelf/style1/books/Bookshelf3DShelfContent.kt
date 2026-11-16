package io.legado.app.ui.main.bookshelf.style1.books

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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

/** 书脊奶白 */
private val SpineColor = Color(0xFFFBFAF7)

private fun lerp(start: Float, stop: Float, fraction: Float) =
    start + (stop - start) * fraction

/**
 * 横向立体书架：一排白色书脊，只有吸附到正中间的那本把彩色封面朝前翻出；
 * 两侧书脊沿距离变窄、变淡。封面走现成封面组件（本地图集 → 联网 → 缓存 → 默认兜底）。
 * 点书进入阅读，长按保留；信息条可置顶 / 置底，跟随居中本。
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

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val widthLevel = AppConfig.bookshelf3dWidth.coerceIn(1, 10)
        val heightLevel = AppConfig.bookshelf3dHeight.coerceIn(1, 10)
        val spacingLevel = AppConfig.bookshelf3dSpacing.coerceIn(1, 10)

        // 宽度：档位 1 最窄 → 10 最宽，按屏宽线性拉伸，不卡区间
        val widthFraction = 0.22f + (widthLevel - 1) / 9f * 0.50f
        // 高度：档位 1 矮 → 10 高（细长）；基准用扣除信息条与底栏后的安全高度，避免压到底部导航
        val safeMaxH = maxHeight * 0.80f
        val heightFraction = 0.50f + (heightLevel - 1) / 9f * 0.45f
        var coverW = maxWidth * widthFraction
        var coverH = safeMaxH * heightFraction
        // 仅防溢出兜底：不小于可读尺寸、不超出书架安全区。
        // 量算瞬间容器宽度/高度可能为 0（零尺寸占位帧），上限先保底到下限，避免空区间崩溃。
        val minCoverW = 60.dp
        val minCoverH = 120.dp
        coverW = coverW.coerceIn(minCoverW, maxWidth.coerceAtLeast(minCoverW))
        coverH = coverH.coerceIn(minCoverH, safeMaxH.coerceAtLeast(minCoverH))
        val coverWpx = with(density) { coverW.toPx() }
        val spineW = coverW * 0.27f
        val spineWBasePx = with(density) { spineW.toPx() }
        val spacingDp = 4.dp + 24.dp * ((spacingLevel - 1) / 9f)
        val spacingPx = with(density) { spacingDp.toPx() }
        val maxDistance = coverWpx + with(density) { 120.dp.toPx() }

        Column(modifier = Modifier.fillMaxSize()) {
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
                    val viewportW = listState.layoutInfo.viewportEndOffset.toFloat()
                    ((viewportW - coverWpx) / 2f).coerceAtLeast(0f).toDp()
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
                val isCenter = index == centerIndex

                if (isCenter) {
                    BookCoverFace(
                        index = index,
                        bookItem = bookItem,
                        coverW = coverW,
                        coverH = coverH,
                        onClick = { onBookClick(bookItem) },
                        onLongClick = { onBookLongClick(bookItem) },
                    )
                } else {
                    val spineWidth = with(density) {
                        lerp(spineWBasePx, spineWBasePx * 0.72f, normalized).toDp()
                    }
                    val spineAlpha = lerp(0.95f, 0.35f, normalized)
                    SpineFace(
                        index = index,
                        bookItem = bookItem,
                        spineW = spineWidth,
                        spineH = coverH,
                        alpha = spineAlpha,
                        onClick = { onBookClick(bookItem) },
                        onLongClick = { onBookLongClick(bookItem) },
                    )
                }
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
}

/** 居中聚焦本：彩色封面 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCoverFace(
    index: Int,
    bookItem: BookshelfBookItem,
    coverW: Dp,
    coverH: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val display = bookItem.display
    Box(
        modifier = Modifier
            .width(coverW)
            .height(coverH)
            .shadow(16.dp, RoundedCornerShape(4.dp), ambientColor = Color.Black.copy(0.4f), spotColor = Color.Black.copy(0.4f))
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
    }
}

/** 非居中本：白色书脊（编号 + 竖排书名 + 底部圆点） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpineFace(
    index: Int,
    bookItem: BookshelfBookItem,
    spineW: Dp,
    spineH: Dp,
    alpha: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val display = bookItem.display
    val alphaAnimated by animateFloatAsState(
        targetValue = alpha, spring(0.7f, 300f), label = "spineAlpha",
    )
    val titleChars = display.name.filter { !it.isWhitespace() }.take(18)

    Box(
        modifier = Modifier
            .width(spineW)
            .height(spineH)
            .graphicsLayer { this.alpha = alphaAnimated }
            .clip(RoundedCornerShape(2.dp))
            .background(SpineColor)
            .border(0.5.dp, Color.Black.copy(0.08f), RoundedCornerShape(2.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "NO.%03d".format(index + 1),
                color = Color(0xFF9A9A9A),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
            )
            // 竖排书名
            Column(
                modifier = Modifier.fillMaxHeight().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                titleChars.forEach { ch ->
                    Text(
                        text = ch.toString(),
                        color = Color(0xFF333333),
                        fontSize = 10.5.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
            // 底部空心圆点
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .border(1.dp, Color(0xFF888888), CircleShape)
                    .background(SpineColor, CircleShape),
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
