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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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

/**
 * 横向 3D 立本书架（参考 Wenku8_Flutter 的立体书架）。
 *
 * 每本书由「窄书脊 + 封面」两块板组成：书脊上竖排书名，封面放书籍封面；
 * 几何尺寸按屏幕宽度自适应。横向滑动，中间一本聚焦放大、两侧沿轨道略低、缩小变淡。
 * 点击书本沿纵深上浮、放大并轻微翻转，模拟取出。低端设备 / 墨水屏已在配置层回退普通书架。
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

    // 档位微调系数（设置里的宽窄 / 高矮 / 间距三档）
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
        val itemW = spineW + coverW

        val itemWpx = with(density) { itemW.toPx() }
        val containerCenterPx = with(density) { maxWidth.toPx() / 2f }
        val scrollX = listState.firstVisibleItemIndex * itemWpx +
            listState.firstVisibleItemScrollOffset

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(pitchGap),
            contentPadding = PaddingValues(
                start = with(density) { (containerCenterPx - itemWpx / 2f).toDp() },
                end = with(density) { (containerCenterPx - itemWpx / 2f).toDp() },
                top = 8.dp,
                bottom = with(density) { (marginPx + bottomPaddingPx).toDp() },
            ),
        ) {
            itemsIndexed(items = shelfItems, key = { _, it -> it.key }) { index, bookItem ->
                val itemCenterX = index * itemWpx + itemWpx / 2f
                val factor = ((itemCenterX - scrollX - containerCenterPx)
                    .let { if (it < 0) -it else it } / containerCenterPx)
                    .coerceIn(0f, 1f)
                BookVolume(
                    bookItem = bookItem,
                    spineW = spineW,
                    coverW = coverW,
                    bookH = bookH,
                    focusFactor = factor,
                    onClick = onBookClick,
                    onLongClick = onBookLongClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookVolume(
    bookItem: BookshelfBookItem,
    spineW: Dp,
    coverW: Dp,
    bookH: Dp,
    focusFactor: Float,
    onClick: (BookshelfBookItem) -> Unit,
    onLongClick: (BookshelfBookItem) -> Unit,
) {
    val display = bookItem.display
    var lifted by remember { mutableStateOf(false) }
    val elevation by animateFloatAsState(
        targetValue = if (lifted) 20f else 5f,
        label = "volumeElevation",
    )
    val scale by animateFloatAsState(
        targetValue = if (lifted) 1.07f else 1f - focusFactor * 0.16f,
        label = "volumeScale",
    )
    val spinY by animateFloatAsState(
        targetValue = if (lifted) -12f else 0f,
        label = "volumeRotationY",
    )
    val liftY by animateFloatAsState(
        targetValue = if (lifted) -16f else focusFactor * 18f,
        label = "volumeLiftY",
    )
    val alpha = 1f - focusFactor * 0.35f
    val spineColor = spineColor(bookItem.key)
    val onSpine = readableOn(spineColor)

    Row(
        modifier = Modifier
            .height(bookH)
            .graphicsLayer {
                shadowElevation = elevation
                scaleX = scale
                scaleY = scale
                rotationY = spinY
                translationY = liftY
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
        verticalAlignment = Alignment.Bottom,
    ) {
        // 书脊面板：竖排书名 + 侧边明暗厚度
        Box(
            modifier = Modifier
                .width(spineW)
                .fillMaxSize()
                .clip(
                    RoundedCornerShape(
                        topStart = 3.dp,
                        bottomStart = 1.dp,
                    )
                )
                .background(spineColor),
        ) {
            SpineTitle(
                title = display.name,
                textColor = onSpine,
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
        // 封面面板
        Box(
            modifier = Modifier
                .width(coverW)
                .fillMaxSize()
                .clip(
                    RoundedCornerShape(
                        topEnd = 3.dp,
                        bottomEnd = 1.dp,
                    )
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
}

/**
 * 书脊竖排书名：中文逐字一行；英文 / 数字连续整词保留不拆（横排旋转）；
 * 最多取 7 段，超出加省略号；窄书脊上用小字号保证不挤出。
 */
@Composable
private fun SpineTitle(
    title: String,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val runs = remember(title) { spineRuns(title) }
    Column(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        runs.take(7).forEach { run ->
            Text(
                text = run,
                color = textColor,
                fontSize = if (isLatinRun(run)) 9.sp else 10.sp,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (runs.size > 7) {
            Text("…", color = textColor, fontSize = 10.sp)
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
