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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.roundToInt

/** 纵深投影系数（参考款 depthX / depthY） */
private const val DepthX = 0.66f
private const val DepthY = -0.30f

/** 聚焦权重半径（参考款 radius） */
private const val FocusRadius = 1.48f

/** 书脊浅色底（参考款奶白） */
private val SpineBackground = Color(0xFFF2F0E9)

/**
 * 横向 3D 立本书架（按 Wenku8_Flutter 的斜投影几何用 Compose 重绘）。
 *
 * 视觉模型：一排书脊朝前竖立在架子上，封面挂在书脊右后侧沿纵深折入；
 * 只有中间聚焦的一本沿纵深拉出、把封面翻开放大，两侧退远、缩小变淡。
 * 书体顶部画斜平行四边形厚度盖面；下方（或顶部）显示当前聚焦书的书名 / 作者。
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
        // 几何（对齐参考款）：书脊宽 / 封面宽 / 书高 / 纵深脚印
        val spineW = (maxWidth * 0.043f).coerceIn(14.dp, 18.dp) * widthFactor
        val coverW = (maxWidth * 0.32f).coerceIn(100.dp, 128.dp)
        val bookH = (((coverW - 20.dp) * 166 / 118) + 54.dp) * heightFactor
        val pitch = spineW + 11.dp
        // 书在轨道上占的横向宽 = 书脊 + 封面纵深投影宽
        val footprint = spineW + coverW * DepthX

        val footprintPx = with(density) { footprint.toPx() }
        val pitchPx = with(density) { pitch.toPx() }
        val centerPx = with(density) { maxWidth.toPx() / 2f }
        val scrollX = listState.firstVisibleItemIndex * footprintPx +
            listState.firstVisibleItemScrollOffset

        fun distOf(index: Int): Float {
            val itemCenterX = index * footprintPx + footprintPx / 2f
            return (itemCenterX - scrollX - centerPx) / pitchPx
        }

        // 真正居中（|距离|最小）的那本，用于信息条与聚焦判定
        val focusIndex = if (shelfItems.isEmpty()) 0 else
            shelfItems.indices.minByOrNull { abs(distOf(it)) } ?: 0

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
                horizontalArrangement = Arrangement.spacedBy(pitchGap),
                contentPadding = PaddingValues(
                    start = with(density) { (centerPx - footprintPx / 2f).toDp() },
                    end = with(density) { (centerPx - footprintPx / 2f).toDp() },
                    top = 8.dp,
                    bottom = 4.dp,
                ),
            ) {
                itemsIndexed(items = shelfItems, key = { _, it -> it.key }) { index, bookItem ->
                    val dist = distOf(index)
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
        targetValue = if (lifted) 20f else 6f,
        label = "volumeElevation",
    )
    val scale by animateFloatAsState(
        targetValue = if (lifted) 1.06f else 0.86f + 0.14f * weight,
        label = "volumeScale",
    )
    // 封面绕书脊左缘翻开：未聚焦折进侧面，聚焦翻到正面
    val coverOpen by animateFloatAsState(
        targetValue = weight,
        label = "coverOpen",
    )
    val liftY by animateFloatAsState(
        targetValue = if (lifted) -14f else 0f,
        label = "volumeLiftY",
    )
    val alpha = 0.72f + 0.28f * weight
    val railYpx = with(densityLocal) { railY.toPx() }
    val spineWpx = with(densityLocal) { spineW.toPx() }
    val coverDepthXpx = with(densityLocal) { coverW.toPx() * DepthX }
    val coverDepthYpx = with(densityLocal) { coverW.toPx() * DepthY }
    val bookHpx = with(densityLocal) { bookH.toPx() }
    val coverWpx = with(densityLocal) { coverW.toPx() }
    // 选中本沿纵深拉出距离（参考 pull = pullDistance*weight*(1.24-0.24*weight)）
    val pull = coverWpx * 0.84f * weight * (1.24f - 0.24f * weight)
    val pullX = -pull * DepthX
    val pullY = -pull * DepthY
    val lift = weight * weight * (3f - 2f * weight)
    val liftUp = -bookHpx * 0.18f * lift

    Box(
        modifier = Modifier
            .height(bookH)
            .graphicsLayer {
                shadowElevation = elevation
                scaleX = scale
                scaleY = scale
                translationX = pullX
                translationY = railYpx + pullY + liftY + liftUp
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
        // 封面面板：挂在书脊右后侧，随聚焦程度翻开（先画，被书脊盖住左缘）
        Box(
            modifier = Modifier
                .width(coverW)
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationY = -38f * (1f - coverOpen)
                    scaleX = 0.72f + 0.28f * coverOpen
                    translationX = spineWpx
                    translationY = spineWpx * 0.10f
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
        }

        // 顶面厚度盖面（斜平行四边形），叠在书体顶部
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

        // 书脊面板：最前层，浅色底 + 竖排书名
        Box(
            modifier = Modifier
                .width(spineW)
                .fillMaxSize()
                .clip(
                    RoundedCornerShape(topStart = 3.dp, bottomStart = 1.dp)
                )
                .background(SpineBackground),
        ) {
            SpineTitle(
                title = display.name,
                spineW = spineW,
                modifier = Modifier.align(Alignment.Center),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(1.dp)
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f))
            )
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
 * 最多取 7 段，超出加省略号；字号随书脊宽度自适应。
 */
@Composable
private fun SpineTitle(
    title: String,
    spineW: Dp,
    modifier: Modifier = Modifier,
) {
    val runs = remember(title) { spineRuns(title) }
    val fontSize = if (spineW >= 17.dp) 10.sp else 9.sp
    Column(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        runs.take(7).forEach { run ->
            Text(
                text = run,
                color = Color(0xFF262626),
                fontSize = if (isLatinRun(run)) (fontSize.value - 1).sp else fontSize,
                lineHeight = (fontSize.value + 2).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (runs.size > 7) {
            Text("…", color = Color(0xFF262626), fontSize = fontSize)
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
