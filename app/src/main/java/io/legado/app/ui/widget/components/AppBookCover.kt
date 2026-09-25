package io.legado.app.ui.widget.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.HtmlCoverRenderer
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.BookCover
import io.legado.app.ui.theme.AppDimens
import io.legado.app.utils.textHeight
import io.legado.app.utils.toStringArray
import kotlinx.coroutines.suspendCancellableCoroutine
import splitties.init.appCtx
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * 书籍 / 分组封面的 Compose 实现。
 *
 * 与 View 版 [io.legado.app.ui.widget.image.CoverImageView] 保持同一套取图优先级：
 * 封面图集默认封面 → 真实封面图片 → HTML 模板封面 → 默认封面；图片缺失或加载失败时
 * 在封面上叠加竖排书名与作者（由封面设置控制）。
 *
 * 加载按主题样式规范 §7.3 的 Compose 图片链路实现：Glide bitmap 链路 + 显式 override 尺寸，
 * 组合离开时取消在途请求（`LaunchedEffect` 取消 → `clear` target），不借用 View 版 API。
 */
@Composable
fun AppBookCover(
    modifier: Modifier = Modifier,
    name: String?,
    author: String?,
    coverPath: String?,
    galleryIdentity: String?,
    contentDescription: String?,
    sourceOrigin: String? = null,
    cornerRadius: Dp = AppDimens.bookCoverCornerRadius,
    loadOnlyWifi: Boolean = false,
) {
    val context = LocalContext.current
    val galleryCover = BookCover.getGalleryDefaultCover(galleryIdentity, coverPath)
    val realPath = galleryCover ?: coverPath?.takeIf { it.isNotBlank() }
    // 图集默认封面优先于"强制默认封面"：命中图集时仍显示图集封面
    val useDefaultCover = AppConfig.useDefaultCover && galleryCover == null
    val htmlCover = !useDefaultCover && realPath == null && HtmlCoverRenderer.isApplicable(name)
    val drawName = BookCover.drawBookName && !name.isNullOrBlank()
    // 既没有真实图片也没有 HTML 封面，最终落到默认封面：此时书名叠加是"封面本身"的一部分
    val fallbackCover = !htmlCover && realPath == null

    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val requestKey = listOf(realPath, sourceOrigin, htmlCover, useDefaultCover, name, author)
        .joinToString("|")
    var bitmap by remember(requestKey) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(requestKey) { mutableStateOf(false) }

    LaunchedEffect(requestKey, bounds) {
        // 等控件测量出尺寸后再发请求，保证 override 的是真实显示尺寸
        if (bounds.width <= 0 || bounds.height <= 0) return@LaunchedEffect
        val loaded = when {
            htmlCover -> HtmlCoverRenderer.load(name.orEmpty(), author) ?: defaultCoverBitmap()
            realPath != null -> loadCoverBitmap(
                context = context,
                path = realPath,
                sourceOrigin = sourceOrigin,
                loadOnlyWifi = loadOnlyWifi,
                requestSize = bounds,
            )

            else -> defaultCoverBitmap()
        }
        bitmap = loaded
        loadFailed = loaded == null
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                if (AppConfig.bookCoverShadow) MaterialTheme.colorScheme.background
                else Color.Transparent
            )
            .onSizeChanged { bounds = it }
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        // 有真实图片时只在加载失败后叠加书名；默认封面/无封面场景则始终叠加
        if (drawName && !htmlCover && (fallbackCover || loadFailed)) {
            BookCoverTextOverlay(
                name = name.orEmpty(),
                author = author.orEmpty(),
                drawAuthor = BookCover.drawBookAuthor,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 封面上的竖排书名 / 作者叠层。
 *
 * 书名从左上 20% 位置竖排、作者从右下 95% 位置向上竖排，均带主题背景色描边；
 * 与 View 版 `CoverImageView.generateCoverBitmap` 的绘制参数保持一致。
 */
@Composable
internal fun BookCoverTextOverlay(
    name: String,
    author: String,
    drawAuthor: Boolean,
    modifier: Modifier = Modifier,
) {
    val bgColor = remember { ThemeStore.backgroundColor(appCtx) }
    val accentColor = remember { appCtx.accentColor }

    Canvas(modifier = modifier) {
        val viewWidth = size.width
        val viewHeight = size.height
        val canvas = drawContext.canvas.nativeCanvas

        val namePaint = TextPaint().apply {
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = viewWidth / 7f
            strokeWidth = textSize / 6f
        }

        val nameChars = name.toStringArray()
        var startX = viewWidth * 0.2f
        var startY = viewHeight * 0.2f
        var line = 0
        nameChars.forEachIndexed { index, char ->
            namePaint.color = bgColor
            namePaint.style = Paint.Style.STROKE
            canvas.drawText(char, startX, startY, namePaint)
            namePaint.color = accentColor
            namePaint.style = Paint.Style.FILL
            canvas.drawText(char, startX, startY, namePaint)
            startY += namePaint.textHeight
            if (startY > viewHeight * 0.9f) {
                if ((nameChars.size - index - 1) == 1) {
                    startY -= namePaint.textHeight / 5f
                    namePaint.textSize = viewWidth / 9f
                    return@forEachIndexed
                }
                startX += namePaint.textSize
                line++
                namePaint.textSize = viewWidth / 10f
                startY = viewHeight * 0.2f + namePaint.textHeight * line
            } else if (startY > viewHeight * 0.8f && (nameChars.size - index - 1) > 2) {
                startX += namePaint.textSize
                line++
                namePaint.textSize = viewWidth / 10f
                startY = viewHeight * 0.2f + namePaint.textHeight * line
            }
        }

        if (drawAuthor && author.isNotBlank()) {
            val authorPaint = TextPaint(namePaint).apply {
                typeface = Typeface.DEFAULT
                textSize = viewWidth / 10f
                strokeWidth = textSize / 5f
            }
            val authorChars = author.toStringArray()
            startX = viewWidth * 0.8f
            startY = viewHeight * 0.95f - authorChars.size * authorPaint.textHeight
            startY = maxOf(startY, viewHeight * 0.3f)
            authorChars.forEach {
                authorPaint.color = bgColor
                authorPaint.style = Paint.Style.STROKE
                canvas.drawText(it, startX, startY, authorPaint)
                authorPaint.color = accentColor
                authorPaint.style = Paint.Style.FILL
                canvas.drawText(it, startX, startY, authorPaint)
                startY += authorPaint.textHeight
                if (startY > viewHeight * 0.95f) return@forEach
            }
        }
    }
}

/**
 * 加载真实封面图片为 Bitmap。
 *
 * 取消时清掉 target，避免列表滑走后 Glide 继续解码；`onResourceReady` 与 `onLoadFailed`
 * 可能被先后调用（后台恢复时 Glide 会重新调度资源），用 [AtomicBoolean] 保证只 resume 一次。
 */
private suspend fun loadCoverBitmap(
    context: Context,
    path: String,
    sourceOrigin: String?,
    loadOnlyWifi: Boolean,
    requestSize: IntSize,
): Bitmap? = suspendCancellableCoroutine { cont ->
    // 先在协程存活时取到 RequestManager：取消回调里 Activity 可能已 destroy，
    // 那时再 Glide.with(context) 会抛 "You cannot start a load for a destroyed activity"
    val requestManager = Glide.with(context)
    val resumed = AtomicBoolean(false)
    val target = object : CustomTarget<Bitmap>() {
        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
            if (resumed.compareAndSet(false, true) && cont.isActive) {
                cont.resume(resource)
            }
        }

        override fun onLoadCleared(placeholder: Drawable?) = Unit

        override fun onLoadFailed(errorDrawable: Drawable?) {
            if (resumed.compareAndSet(false, true) && cont.isActive) {
                cont.resume(null)
            }
        }
    }
    cont.invokeOnCancellation {
        runCatching { requestManager.clear(target) }
    }
    var options = RequestOptions().set(OkHttpModelLoader.loadOnlyWifiOption, loadOnlyWifi)
    if (sourceOrigin != null) {
        options = options.set(OkHttpModelLoader.sourceOriginOption, sourceOrigin)
    }
    val builder = ImageLoader.loadBitmap(context, path).apply(options).centerCrop()
    // 高清封面设置开启时不做降采样，与 View 版行为一致
    if (!AppConfig.loadCoverHighQuality) {
        builder.override(requestSize.width, requestSize.height)
    }
    builder.into(target)
}

/**
 * 默认封面位图。
 *
 * [BookCover.defaultDrawable] 由 600x900 的位图或内置 jpg 构造，两种情况都是 [BitmapDrawable]，
 * 直接取底层位图引用即可，不做像素级处理。
 */
private fun defaultCoverBitmap(): Bitmap? = (BookCover.defaultDrawable as? BitmapDrawable)?.bitmap
