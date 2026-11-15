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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.roundToInt

/** 纵深系数（参考款 depthX / depthY） */
private const val DepthX = 0.66f

/** 聚焦权重半径（参考款 radius） */
private const val FocusRadius = 1.48f

/**
 * 横向 3D 立本书架（按 Wenku8_Flutter 的几何与曲线用 Compose 重绘）。
 *
 * 每本书由「窄书脊 + 封面」两块板组成，书脊竖排书名；横向滑动，中间一本按余弦权重
 * 放大提亮，两侧沿二次曲线轨道略低、缩小变淡；书体顶部画出厚度盖面。点击书本上浮拉出。
 * 低端设备 / 墨水屏已在配置层回退普通书架。
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
    val pitchGap = when (AppConfig.bookshelf3dSpacing) {
        1 -> 6.dp
        3 -> 10.dp
        else -> 8.dp
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 几何：书脊宽 / 封面宽 / 书高，按屏宽自适应后乘档位系数
        val spineW = (maxWidth * 0.043f).coerceIn(14.dp, 18.dp) * widthFactor
        val coverW = (maxWidth * 0.32f).coerceIn(100.dp, 128.dp)
        val bookH = (((coverW - 20.dp) * 166 / 118) + 54.dp) * heightFactor
        val pitch = spineW + 11.dp
        val itemW = spineW + coverW

        val itemWpx = with(density) { itemW.toPx() }
        val pitchPx = with(density) { pitch.toPx() }
        val centerPx = with(density) { maxWidth.toPx() / 2f }
        val scrollX = listState.firstVisibleItemIndex * itemWpx +
            listState.firstVisibleItemScrollOffset
        val focusIndex = (scrollX / pitchPx).roundToInt()
            .coerceIn(0, shelfItems.lastIndex.coerceAtLeast(0))

        Column(modifier = Modifier.fillMaxSize()) {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(pitchGap),
                contentPadding = PaddingValues(
                    start = with(density) { (centerPx - itemWpx / 2f).toDp() },
                    end = with(density) { (centerPx - itemWpx / 2f).toDp() },
                    top = 8.dp,
                    bottom = 4.dp,
                ),
            ) {
                itemsIndexed(items = shelfItems, key = { _, it -> it.key }) { index, bookItem ->
                    val itemCenterX = index * itemWpx + itemWpx / 2f
                    val dist = (itemCenterX - scrollX - centerPx) / pitchPx
                    val weight = focusWeight(dist)
                    val railY = railY(dist)
                    BookVolume(
                        bookItem = bookItem,
                        spineW = spineW,
                        coverW = coverW,
                        bookH = bookH,
                        weight = weight,
                        railY = railY,
                        onClick = onBookClick,
                        onLongClick = onBookLongClick,
                    )
                }
            }
            // 下方当前聚焦书的标题 / 作者信息条
            shelfItems.getOrNull(focusIndex)?.let { current ->
                CurrentBookBar(
                    bookItem = current,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .padding(bottom = with(density) { (marginPx + bottomPaddingPx).toDp() }),
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
    bookItem: BookshelfBookItem,
    spineW: Dp,
    coverW: Dp,
    bookH: Dp,
    weight: Float,
    railY: Dp,
    onClick: (BookshelfBookItem) -> Unit,
    onLongClick: (BookshelfBookItem) -> Unit,
) {
    val display = bookItem.display
    val densityLocal = LocalDensity.current
    var lifted by remember { mutableStateOf(false) }
    val elevation by animateFloatAsState(
        targetValue = if (lifted) 20f else 5f,
        label = "volumeElevation",
    )
    val scale by animateFloatAsState(
        targetValue = if (lifted) 1.07f else 0.84f + 0.16f * weight,
        label = "volumeScale",
    )
    val spinY by animateFloatAsState(
        targetValue = if (lifted) -12f else 0f,
        label = "volumeRotationY",
    )
    val liftY by animateFloatAsState(
        targetValue = if (lifted) -16f else 0f,
        label = "volumeLiftY",
    )
    val alpha = 0.70f + 0.30f * weight
    val railYpx = with(densityLocal) { railY.toPx() }
    val spineColor = spineColor(bookItem.key)
    val onSpine = readableOn(spineColor)
    val spineWpx = with(densityLocal) { spineW.toPx() }
    val coverDepthPx = with(densityLocal) { coverW.toPx() * DepthX }

    Box(
        modifier = Modifier
            .height(bookH)
            .graphicsLayer {
                shadowElevation = elevation
                scaleX = scale
                scaleY = scale
                rotationY = spinY
                translationY = railYpx + liftY
                this.alpha = alpha
                cameraDistance = 12f * density
            }
            .combinedClickable(
                onClick = {
                    lifted = true
                    onClick(bookItem)
                },
                onLongClick = { onLongClick(bookItem) },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Bottom,
        ) {
            // 书脊面板：竖排书名 + 侧边明暗厚度
            Box(
                modifier = Modifier
                    .width(spineW)
                    .fillMaxSize()
                    .clip(
                        RoundedCornerShape(topStart = 3.dp, bottomStart = 1.dp)
                    )
                    .background(spineColor),
            ) {
                SpineTitle(
                    title = display.name,
                    textColor = onSpine,
                    spineW = spineW,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(2.dp)
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.26f))
                )
            }
            // 封面面板：未聚焦时轻微向里折，模拟立体角度
            Box(
                modifier = Modifier
                    .width(coverW)
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = -6f * (1f - weight)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .clip(
                        RoundedCornerShape(topEnd = 3.dp, bottomEnd = 1.dp)
                    ),
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
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(2.dp)
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.16f))
                )
            }
        }
        // 顶面厚度盖面（平行四边形渐变），叠加在书体顶部
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.TopCenter),
        ) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(0f, h)
                lineTo(spineWpx, 0f)
                lineTo(w - coverDepthPx, 0f)
                lineTo(w, h)
                close()
            }
            drawPath(
                path = path,
                color = spineColor.copy(alpha = 0.9f),
            )
        }
    }
}

/** 下方当前聚焦书的标题 / 作者信息条 */
@Composable
private fun CurrentBookBar(
    bookItem: BookshelfBookItem,
    modifier: Modifier = Modifier,
) {
    val display = bookItem.display
    Column(modifier = modifier) {
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
 * 最多取 7 段，超出加省略号；字号随书脊宽度自适应。
 */
@Composable
private fun SpineTitle(
    title: String,
    textColor: Color,
    spineW: Dp,
    modifier: Modifier = Modifier,
) {
    val runs = remember(title) { spineRuns(title) }
    val fontSize = if (spineW >= 17.dp) 10.sp else 8.sp
    Column(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        runs.take(7).forEach { run ->
            Text(
                text = run,
                color = textColor,
                fontSize = if (isLatinRun(run)) (fontSize.value - 1).sp else fontSize,
                lineHeight = (fontSize.value + 2).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (runs.size > 7) {
            Text("…", color = textColor, fontSize = fontSize)
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

/** 按书籍身份派生柔和书脊底色（封面缺失兜底） */
private fun spineColor(identity: String): Color {
    val hue = ((identity.hashCode() % 360) + 360) % 360
    return Color.hsl(hue.toFloat(), 0.38f, 0.52f)
}

/** 按底色亮度选对比色：浅底深字，深底白字 */
private fun readableOn(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.6f) Color(0xFF262626) else Color.White
}
