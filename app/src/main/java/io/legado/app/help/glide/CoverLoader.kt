package io.legado.app.help.glide

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.widget.ImageView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.legado.app.R
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.model.BookCover
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.textHeight
import io.legado.app.utils.toStringArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import splitties.init.appCtx

/**
 * 封面加载工具类
 *
 * 提供统一的封面加载逻辑，支持：
 * - 网络图片加载
 * - 默认封面（全局设置）
 * - HTML模板生成封面
 * - Canvas绘制书名作者
 *
 * 使用 CoverImageView 的共享缓存，避免缓存双轨制。
 * 可用于任何 ImageView，不强制固定宽高比，
 * 适用于瀑布流等需要自由图片比例的场景。
 */
object CoverLoader {
    // 使用 CoverImageView 的共享缓存
    private val nameBitmapCache get() = CoverImageView.nameBitmapCache
    private val needNameBitmap get() = CoverImageView.needNameBitmap

    // 触发通道（全局共享，用于触发书名绘制）
    private val triggerChannel = Channel<Unit>(Channel.CONFLATED)

    // Job tag key
    private const val TAG_KEY_JOB = "cover_loader_job"

    /**
     * 清除HTML封面缓存
     * 调用 CoverImageView 的方法，保持一致性
     */
    fun clearHtmlCoverCache() {
        CoverImageView.clearHtmlCoverCache()
    }

    /**
     * 清除所有封面缓存
     */
    fun clearAllCache() {
        CoverImageView.clearAllCache()
    }

    /**
     * 取消 ImageView 上正在运行的 Job
     */
    private fun cancelJob(imageView: ImageView) {
        val job = imageView.getTag(R.id.tag_cover_loader_job) as? Job
        job?.cancel()
        imageView.setTag(R.id.tag_cover_loader_job, null)
    }

    /**
     * 存储 Job 到 ImageView
     */
    private fun storeJob(imageView: ImageView, job: Job) {
        imageView.setTag(R.id.tag_cover_loader_job, job)
    }

    /**
     * 加载封面到 ImageView
     *
     * @param imageView 目标 ImageView
     * @param searchBook 搜索书籍信息
     * @param loadOnlyWifi 是否仅在WiFi下加载
     * @param fragment Fragment（可选，用于生命周期管理）
     * @param lifecycle Lifecycle（可选，用于生命周期管理）
     * @param overrideWidth 覆盖宽度（可选）
     * @param overrideHeight 覆盖高度（可选）
     * @param fixedRatio 是否强制 3:4 比例，false 时保持图片原始比例
     * @param onLoadFinish 加载完成回调
     */
    fun load(
        imageView: ImageView,
        searchBook: SearchBook,
        loadOnlyWifi: Boolean = false,
        fragment: Fragment? = null,
        lifecycle: Lifecycle? = null,
        overrideWidth: Int = 0,
        overrideHeight: Int = 0,
        fixedRatio: Boolean = false,
        onLoadFinish: (() -> Unit)? = null
    ) {
        val galleryIdentity = listOf(
            searchBook.bookUrl,
            searchBook.origin,
            searchBook.name,
            searchBook.author
        ).joinToString("|")
        load(
            imageView,
            searchBook.coverUrl,
            searchBook.name,
            searchBook.author,
            loadOnlyWifi,
            searchBook.origin,
            fragment,
            lifecycle,
            galleryIdentity,
            overrideWidth,
            overrideHeight,
            fixedRatio,
            onLoadFinish
        )
    }

    /**
     * 加载封面到 ImageView
     *
     * @param imageView 目标 ImageView
     * @param book 书籍信息
     * @param loadOnlyWifi 是否仅在WiFi下加载
     * @param fragment Fragment（可选）
     * @param lifecycle Lifecycle（可选）
     * @param overrideWidth 覆盖宽度（可选）
     * @param overrideHeight 覆盖高度（可选）
     * @param fixedRatio 是否强制 3:4 比例
     * @param onLoadFinish 加载完成回调
     */
    fun load(
        imageView: ImageView,
        book: Book,
        loadOnlyWifi: Boolean = false,
        fragment: Fragment? = null,
        lifecycle: Lifecycle? = null,
        overrideWidth: Int = 0,
        overrideHeight: Int = 0,
        fixedRatio: Boolean = false,
        onLoadFinish: (() -> Unit)? = null
    ) {
        load(
            imageView,
            book.getDisplayCover(),
            book.name,
            book.author,
            loadOnlyWifi,
            book.origin,
            fragment,
            lifecycle,
            book.bookUrl,
            overrideWidth,
            overrideHeight,
            fixedRatio,
            onLoadFinish
        )
    }

    /**
     * 加载封面到 ImageView
     *
     * @param imageView 目标 ImageView
     * @param path 封面图片路径或URL
     * @param name 书名
     * @param author 作者
     * @param loadOnlyWifi 是否仅在WiFi下加载
     * @param sourceOrigin 书源来源标识
     * @param fragment Fragment（可选）
     * @param lifecycle Lifecycle（可选）
     * @param galleryIdentity 封面图库标识
     * @param overrideWidth 覆盖宽度（可选）
     * @param overrideHeight 覆盖高度（可选）
     * @param fixedRatio 是否强制 3:4 比例，false 时保持图片原始比例
     * @param onLoadFinish 加载完成回调
     */
    fun load(
        imageView: ImageView,
        path: String? = null,
        name: String? = null,
        author: String? = null,
        loadOnlyWifi: Boolean = false,
        sourceOrigin: String? = null,
        fragment: Fragment? = null,
        lifecycle: Lifecycle? = null,
        galleryIdentity: String? = null,
        overrideWidth: Int = 0,
        overrideHeight: Int = 0,
        fixedRatio: Boolean = false,
        onLoadFinish: (() -> Unit)? = null
    ) {
        // 取消之前的 Job
        cancelJob(imageView)

        val currentAuthor = author?.replace(AppPattern.bdRegex, "")?.trim()
        val currentName = name?.replace(AppPattern.bdRegex, "")?.trim()

        val galleryDefaultCover = BookCover.getGalleryDefaultCover(
            galleryIdentity ?: listOfNotNull(sourceOrigin, path, name, author).joinToString("|"),
            path
        )
        val actualPath = galleryDefaultCover ?: path

        // 检查是否启用HTML封面生成
        if (galleryDefaultCover == null && currentName != null && HtmlCoverRenderer.isApplicable(currentName)) {
            loadHtmlCover(imageView, currentName, currentAuthor, onLoadFinish)
            return
        }

        // 使用默认封面（当图集默认封面存在时，不使用此路径，由下方正常加载图集封面）
        if (AppConfig.useDefaultCover && galleryDefaultCover == null) {
            ImageLoader.load(imageView.context, BookCover.defaultDrawable)
                .let { builder ->
                    if (fixedRatio) builder.centerCrop() else builder
                }
                .into(imageView)
            onLoadFinish?.invoke()
            return
        }

        // 无封面URL时，绘制书名作者
        if (galleryDefaultCover == null && BookCover.drawBookName && currentName != null && path.isNullOrEmpty()) {
            val pathName = if (BookCover.drawBookAuthor) {
                currentName + currentAuthor
            } else {
                currentName
            }
            drawNameAuthor(imageView, pathName, currentName, currentAuthor, actualPath, onLoadFinish)
            return
        }

        // 加载网络图片
        var options = RequestOptions().set(OkHttpModelLoader.loadOnlyWifiOption, loadOnlyWifi)
        if (sourceOrigin != null) {
            options = options.set(OkHttpModelLoader.sourceOriginOption, sourceOrigin)
        }

        var builder = if (fragment != null && lifecycle != null) {
            ImageLoader.load(fragment, lifecycle, actualPath)
        } else {
            ImageLoader.load(imageView.context, actualPath)
        }

        builder = builder.apply(options)
            .placeholder(BookCover.defaultDrawable)
            .error(BookCover.defaultDrawable)

        // 添加加载监听
        builder = builder.addListener(createGlideListener(actualPath, onLoadFinish))

        if (overrideWidth > 0 && overrideHeight > 0 && !AppConfig.loadCoverHighQuality) {
            builder.override(overrideWidth, overrideHeight)
        }

        if (fixedRatio) {
            builder.centerCrop()
        }

        builder.into(imageView)
    }

    /**
     * 创建 Glide 加载监听器
     */
    private fun createGlideListener(bitmapPath: String?, onLoadFinish: (() -> Unit)?): RequestListener<Drawable> {
        return object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                triggerChannel.trySend(Unit)
                needNameBitmap.put(bitmapPath.toString(), true)
                onLoadFinish?.invoke()
                return false
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                needNameBitmap.remove(bitmapPath.toString())
                onLoadFinish?.invoke()
                return false
            }
        }
    }

    /**
     * 绘制书名作者到 ImageView
     */
    private fun drawNameAuthor(
        imageView: ImageView,
        pathName: String,
        name: String,
        author: String?,
        bitmapPath: String?,
        onLoadFinish: (() -> Unit)?
    ) {
        generateCoverAsync(imageView, pathName, name, author, bitmapPath, onLoadFinish)
    }

    /**
     * 异步生成封面
     * 使用 View.setTag 存储 Job，在 ImageView 回收时取消
     */
    private fun generateCoverAsync(
        imageView: ImageView,
        pathName: String,
        name: String,
        author: String?,
        bitmapPath: String?,
        onLoadFinish: (() -> Unit)?
    ) {
        val job = CoroutineScope(Dispatchers.Main).launch {
            try {
                withTimeoutOrNull(1200) {
                    triggerChannel.receive()
                }

                // 等待 ImageView 有尺寸
                var attempts = 0
                while (imageView.width == 0 && attempts < 2000) {
                    delay(1L)
                    attempts++
                    ensureActive()
                }

                if (imageView.width == 0) {
                    imageView.setImageDrawable(BookCover.defaultDrawable)
                    onLoadFinish?.invoke()
                    return@launch
                }

                // 瀑布流布局中 height 可能是 WRAP_CONTENT（值为0或不确定）
                // 使用 width * 4 / 3 作为默认高度
                val targetHeight = if (imageView.height <= 0) {
                    imageView.width * 4 / 3
                } else {
                    imageView.height
                }

                val bitmap = generateCoverBitmap(imageView.width, targetHeight, name, author)
                needNameBitmap.put(bitmapPath.toString(), true)
                nameBitmapCache.put(pathName + imageView.width, bitmap)
                imageView.setImageBitmap(bitmap)
                onLoadFinish?.invoke()
            } catch (_: CancellationException) {
                // Job 被取消，不执行回调
            } catch (e: Exception) {
                e.printStackTrace()
                imageView.setImageDrawable(BookCover.defaultDrawable)
                onLoadFinish?.invoke()
            }
        }
        storeJob(imageView, job)
    }

    private fun ensureActive() {
        // 空实现，用于协程检查
    }

    /**
     * 生成封面 Bitmap（Canvas绘制书名作者）
     */
    private fun generateCoverBitmap(width: Int, height: Int, name: String?, author: String?): Bitmap {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val bitmap = createBitmap(width, height)
        val bitmapCanvas = Canvas(bitmap)
        var startX = width * 0.2f
        var startY = viewHeight * 0.2f
        val backgroundColor = appCtx.backgroundColor
        val accentColor = appCtx.accentColor
        val namePaint = TextPaint().apply {
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        name?.toStringArray()?.let { name ->
            var line = 0
            namePaint.textSize = viewWidth / 7
            namePaint.strokeWidth = namePaint.textSize / 6
            name.forEachIndexed { index, char ->
                namePaint.color = backgroundColor
                namePaint.style = Paint.Style.STROKE
                bitmapCanvas.drawText(char, startX, startY, namePaint)
                namePaint.color = accentColor
                namePaint.style = Paint.Style.FILL
                bitmapCanvas.drawText(char, startX, startY, namePaint)
                startY += namePaint.textHeight
                if (startY > viewHeight * 0.9) {
                    if ((name.size - index - 1) == 1) {
                        startY -= namePaint.textHeight / 5
                        namePaint.textSize = viewWidth / 9
                        return@forEachIndexed
                    }
                    startX += namePaint.textSize
                    line++
                    namePaint.textSize = viewWidth / 10
                    startY = viewHeight * 0.2f + namePaint.textHeight * line
                } else if (startY > viewHeight * 0.8 && (name.size - index - 1) > 2) {
                    startX += namePaint.textSize
                    line++
                    namePaint.textSize = viewWidth / 10
                    startY = viewHeight * 0.2f + namePaint.textHeight * line
                }
            }
        }

        if (!BookCover.drawBookAuthor) {
            return bitmap
        }

        val authorPaint = TextPaint(namePaint).apply {
            typeface = Typeface.DEFAULT
        }
        author?.toStringArray()?.let { author ->
            authorPaint.textSize = viewWidth / 10
            authorPaint.strokeWidth = authorPaint.textSize / 5
            startX = width * 0.8f
            startY = viewHeight * 0.95f - author.size * authorPaint.textHeight
            startY = maxOf(startY, viewHeight * 0.3f)
            author.forEach {
                authorPaint.color = backgroundColor
                authorPaint.style = Paint.Style.STROKE
                bitmapCanvas.drawText(it, startX, startY, authorPaint)
                authorPaint.color = accentColor
                authorPaint.style = Paint.Style.FILL
                bitmapCanvas.drawText(it, startX, startY, authorPaint)
                startY += authorPaint.textHeight
                if (startY > viewHeight * 0.95) {
                    return@let
                }
            }
        }
        return bitmap
    }

    /**
     * 加载 HTML 模板封面。
     *
     * 渲染与缓存都委托给 [HtmlCoverRenderer]，这里只负责贴图和取消：
     * Job 存到 ImageView 的 tag 上，ImageView 回收时一起取消。
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun loadHtmlCover(
        imageView: ImageView,
        bookName: String,
        author: String?,
        onLoadFinish: (() -> Unit)?
    ) {
        val job = CoroutineScope(Dispatchers.Main).launch {
            try {
                val bitmap = HtmlCoverRenderer.load(bookName, author)
                imageView.setImageDrawable(
                    bitmap?.toDrawable(imageView.resources) ?: BookCover.defaultDrawable
                )
                onLoadFinish?.invoke()
            } catch (_: CancellationException) {
                // Job 被取消，不执行回调
            } catch (e: Exception) {
                e.printStackTrace()
                imageView.setImageDrawable(BookCover.defaultDrawable)
                onLoadFinish?.invoke()
            }
        }
        storeJob(imageView, job)
    }

}