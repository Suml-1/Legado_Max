package io.legado.app.ui.main.bookshelf.style1.books

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.main.bookshelf.compose.BookshelfBookItem
import io.legado.app.ui.widget.components.AppBookCover
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.abs

/** 纵深投影系数（参考款 depthX / depthY） */
private const val DepthX = 0.66f
private const val DepthY = -0.30f

/** 聚焦权重半径（参考款 radius） */
private const val FocusRadius = 1.48f

/** 书脊奶白底（参考款 0xFFF2F0E9） */
private val SpineBackground = Color(0xFFF2F0E9)
private val SpineText = Color(0xFF262626)
private val SpineMuted = Color(0xFF9A927F)

/**
 * 横向 3D 立本书架（按 Wenku8_Flutter 参考图重绘）。
 *
 * 一排白色书脊竖立在前：顶部编号、中间竖排书名、底部圆点；只有弹出的一本把彩色封面
 * 朝前翻开叠在前面，其余书只显示书脊。两侧沿弧线退远、缩小变淡。
 *
 * 翻开行为由「选中书籍自动翻开封面」开关决定：
 * - 开启：滑到正中间的那本自动翻开；
 * - 关闭：点哪本书脊，哪本才弹出；再点已翻开的封面进入阅读，滑动离开自动收回。
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
    val canScrollBack by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    LaunchedEffect(canScrollBack) {
        onScrollBackwardChange(canScrollBack)
    }

    val density = LocalDensity.current
    val infoBarTop = AppConfig.bookshelf3dInfoBarTop
    val autoOpen = AppConfig.bookshelf3dAutoOpen

    // 手动模式下用户点击弹出的那一本；自动模式下不使用，直接跟随居中本
    var manualOpenIndex by remember { mutableStateOf<Int?>(null) }

    val widthFactor = when (AppConfig.bookshelf3dWidth) {
        1 -> 0.90f
        3 -> 1.10f
        else -> 1.00f
    }
    val heightFactor = when (AppConfig.bookshelf3dHeight) {
        1 -> 0.88f
        3 -> 1.12f
        else -> 1.00f
    }
    val gap = when (AppConfig.bookshelf3dSpacing) {
        1 -> 3.dp
        3 -> 7.dp
        else -> 5.dp
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val spineW = (maxWidth * 0.055f).coerceIn(18.dp, 24.dp) * widthFactor
        val coverW = (maxWidth * 0.30f).coerceIn(96.dp, 124.dp)
        val bookH = (((coverW - 20.dp) * 166 / 118) + 54.dp) * heightFactor

        val itemWpx = with(density) { spineW.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val stepPx = itemWpx + gapPx
        val centerPx = with(density) { maxWidth.toPx() / 2f }
        val scrollX = listState.firstVisibleItemIndex * stepPx +
            listState.firstVisibleItemScrollOffset

        fun distOf(index: Int): Float {
            val itemCenterX = index * stepPx + itemWpx / 2f
            return (itemCenterX - scrollX - centerPx) / stepPx
        }

        val focusIndex = if (shelfItems.isEmpty()) 0 else
            shelfItems.indices.minByOrNull { abs(distOf(it)) } ?: 0

        // 手动模式：居中本离开原来选中本超过一位后，自动收回封面
        LaunchedEffect(focusIndex, autoOpen) {
            if (!autoOpen && manualOpenIndex != null &&
                abs((manualOpenIndex ?: 0) - focusIndex) > 1
            ) {
                manualOpenIndex = null
            }
        }

        val openedIndex = if (autoOpen) focusIndex else manualOpenIndex

        Column(modifier = Modifier.fillMaxSize()) {
            if (infoBarTop) {
                CurrentBookBar(
                    bookItem = shelfItems.getOrNull(focusIndex),
                    bottomPaddingPx = bottomPaddingPx,
                    marginPx = marginPx,
                    density = density,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(gap),
                contentPadding = PaddingValues(
                    start = with(density) { (centerPx - itemWpx / 2f).toDp() },
                    end = with(density) { (centerPx - itemWpx / 2f).toDp() },
                    top = 8.dp,
                    bottom = 4.dp,
                ),
            ) {
                itemsIndexed(items = shelfItems, key = { _, it -> it.key }) { index, bookItem ->
                    val dist = distOf(index)
                    val weight = focusWeight(dist)
                    val railY = railY(dist)
                    val opened = openedIndex == index
                    BookVolume(
                        index = index,
                        bookItem = bookItem,
                        spineW = spineW,
                        coverW = coverW,
                        bookH = bookH,
                        weight = weight,
                        opened = opened,
                        railY = railY,
                        onClick = {
                            val openOnly = !autoOpen && manualOpenIndex != index
                            if (openOnly) {
                                manualOpenIndex = index
                            } else {
                                onBookClick(bookItem)
                            }
                        },
                        onLongClick = { onBookLongClick(bookItem) },
                    )
                }
            }
            if (!infoBarTop) {
                CurrentBookBar(
                    bookItem = shelfItems.getOrNull(focusIndex),
                    bottomPaddingPx = bottomPaddingPx,
                    marginPx = marginPx,
                    density = density,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 聚焦权重：中间为 1，向两侧按余弦平方平滑衰减到 0 */
private fun focusWeight(dist: Float): Float {
    val d = abs(dist).coerceIn(0f, FocusRadius)
    if (d >= FocusRadius) return 0f
    val v = cos(d / FocusRadius * (PI / 2).toFloat())
    return v * v
}

/** 轨道高低二次曲线：离中越远越低、越偏 */
private fun railY(dist: Float): Dp {
    val d = dist.coerceIn(-7f, 7f)
    return (d * 2.5f + d * d * 0.45f).dp
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookVolume(
    index: Int,
    bookItem: BookshelfBookItem,
    spineW: Dp,
    coverW: Dp,
    bookH: Dp,
    weight: Float,
    opened: Boolean,
    railY: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val display = bookItem.display
    val densityLocal = LocalDensity.current

    val elevation by animateFloatAsState(
        targetValue = if (opened) 20f else 6f,
        label = "volumeElevation",
    )
    val scale by animateFloatAsState(
        targetValue = if (opened) 1.02f else 0.88f + 0.12f * weight,
        label = "volumeScale",
    )
    val coverOpen by animateFloatAsState(targetValue = if (opened) 1f else 0f, label = "coverOpen")
    val liftY by animateFloatAsState(
        targetValue = if (opened) -14f else 0f,
        label = "volumeLiftY",
    )
    val alpha = 0.75f + 0.25f * weight
    val railYpx = with(densityLocal) { railY.toPx() }
    val spineWpx = with(densityLocal) { spineW.toPx() }
    val coverDepthXpx = with(densityLocal) { coverW.toPx() * DepthX }
    val coverDepthYpx = with(densityLocal) { coverW.toPx() * DepthY }
    val bookHpx = with(densityLocal) { bookH.toPx() }
    val lift = weight * weight * (3f - 2f * weight)
    val liftUp = -bookHpx * 0.16f * lift

    Box(
        modifier = Modifier
            .height(bookH)
            .graphicsLayer {
                shadowElevation = elevation
                scaleX = scale
                scaleY = scale
                translationY = railYpx + liftY + liftUp
                this.alpha = alpha
                cameraDistance = 12f * densityLocal.density
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        // 封面面板：仅弹出的这本朝前翻开，叠在书脊上层；未弹出时透明退到后侧
        Box(
            modifier = Modifier
                .width(coverW)
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    alpha = coverOpen
                    rotationY = -26f * (1f - coverOpen)
                    scaleX = 0.35f + 0.65f * coverOpen
                    translationX = spineWpx * 0.30f
                }
                .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 1.dp)),
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

        // 顶面厚度盖面（斜平行四边形）
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frontTop = Offset(spineWpx, spineWpx * 0.10f)
            val depth = Offset(coverDepthXpx, coverDepthYpx)
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(frontTop.x, frontTop.y)
                lineTo(frontTop.x + depth.x, frontTop.y + depth.y)
                lineTo(depth.x, depth.y)
                close()
            }
            drawPath(path = path, color = SpineBackground.copy(alpha = 0.95f))
        }

        // 书脊面板：最前层，奶白底 + 编号 + 竖排书名 + 底圆点
        Box(
            modifier = Modifier
                .width(spineW)
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 1.dp))
                .background(SpineBackground),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "%02d".format(index + 1),
                    color = SpineMuted,
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                SpineTitle(
                    title = display.name,
                    spineW = spineW,
                    modifier = Modifier.weight(1f).align(Alignment.CenterHorizontally),
                )
                Canvas(modifier = Modifier.width(6.dp).height(6.dp)) {
                    drawCircle(color = SpineMuted, radius = size.minDimension / 2f)
                }
            }
        }
    }
}

/** 当前聚焦书的书名 / 作者信息条 */
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

/**
 * 书脊竖排书名：中文逐字一行；英文 / 数字连续整词保留不拆；
 * 字号随书脊宽度自适应，单字不裁切；最多 7 段，超出加省略号。
 */
@Composable
private fun SpineTitle(
    title: String,
    spineW: Dp,
    modifier: Modifier = Modifier,
) {
    val runs = remember(title) { spineRuns(title) }
    val fontSize = if (spineW >= 22.dp) 9.sp else 8.sp
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        runs.take(7).forEach { run ->
            Text(
                text = run,
                color = SpineText,
                fontSize = if (isLatinRun(run)) (fontSize.value - 1).sp else fontSize,
                lineHeight = (fontSize.value + 1).sp,
                textAlign = TextAlign.Center,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        if (runs.size > 7) {
            Text("…", color = SpineText, fontSize = fontSize)
        }
    }
}

/** 连续 ASCII（英文 / 数字 / 空格）作为整词，其余按单字分段 */
private fun spineRuns(title: String): List<String> {
    val runs = mutableListOf<String>()
    var latin = ""
    fun flush() {
        if (latin.trim().isNotBlank()) runs.add(latin.trim())
        latin = ""
    }
    for (ch in title) {
        if (ch in ' '..'~') latin += ch else {
            flush()
            runs.add(ch.toString())
        }
    }
    flush()
    return runs
}

private fun isLatinRun(run: String): Boolean = run.all { it in ' '..'~' }
