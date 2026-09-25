package io.legado.app.ui.widget.image

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.collection.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.AdaptiveCoverTransformation
import io.legado.app.help.glide.HtmlCoverRenderer
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.model.BookCover
import io.legado.app.utils.textHeight
import io.legado.app.utils.toStringArray
import android.view.ViewOutlineProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import splitties.init.appCtx

/**
 * 封面图片视图
 * 
 * 支持多种封面来源：
 * - 网络图片（通过URL加载）
 * - 默认封面（全局设置）
 * - HTML模板生成封面（自定义HTML代码）
 * - Canvas绘制书名作者（无封面时的兜底方案）
 */
@Suppress("unused")
class CoverImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    init {
        updateCoverBackground()
    }
    companion object {
        private val _nameBitmapCache by lazy { LruCache<String, Bitmap>(33) }
        private val _needNameBitmap by lazy { LruCache<String, Boolean>(99) }

        /**
         * 书名绘制缓存（公开供 CoverLoader 使用）
         */
        val nameBitmapCache: LruCache<String, Bitmap> get() = _nameBitmapCache
        
        /**
         * 是否需要绘制书名标记缓存（公开供 CoverLoader 使用）
         */
        val needNameBitmap: LruCache<String, Boolean> get() = _needNameBitmap

        /**
         * 清除HTML封面缓存
         * 
         * 在模板内容变更、切换选中模板、启用/禁用HTML封面时调用，
         * 确保书架上的封面能及时刷新
         */
        fun clearHtmlCoverCache() {
            HtmlCoverRenderer.clearCache()
        }

        /**
         * 清除所有封面缓存
         */
        fun clearAllCache() {
            HtmlCoverRenderer.clearCache()
            _nameBitmapCache.evictAll()
            _needNameBitmap.evictAll()
        }

        // Job 存储 tag key
        private const val TAG_KEY_JOB = "cover_job"
    }
    private var viewWidth: Float = 0f
    private var viewHeight: Float = 0f
    private var currentJob: Job? = null
    private val triggerChannel = Channel<Unit>(Channel.CONFLATED)
    var bitmapPath: String? = null
        private set
    private var name: String? = null
    private var author: String? = null
    private var nameHeight = 0f
    private var authorHeight = 0f
    private var isHtmlCover = false
    private val drawBookName = BookCover.drawBookName
    private val drawBookAuthor by lazy { BookCover.drawBookAuthor }

    override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
        if (params != null) {
            val width = params.width
            if (width >= 0) {
                params.height = width * 4 / 3
            } else {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        super.setLayoutParams(params)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = measuredWidth * 4 / 3
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCoverBackground()
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, w, h, 12f)
            }
        }
        clipToOutline = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!drawBookName || isHtmlCover) return
        val currentName = this.name ?: return
        if (AppConfig.useDefaultCover || needNameBitmap[bitmapPath.toString()] == true) {
            val currentAuthor = this.author
            val pathName = if (drawBookAuthor){
                currentName + currentAuthor
            } else {
                currentName
            }
            val cacheBitmap =  nameBitmapCache[pathName + width]
            if (cacheBitmap != null) {
                canvas.drawBitmap(cacheBitmap, 0f, 0f, null)
                return
            }
            drawNameAuthor(pathName, currentName, currentAuthor, false)
        }
    }

    private fun drawNameAuthor(pathName: String, name: String, author: String?, asyncAwait: Boolean = true) {
        generateCoverAsync(pathName, name, author, asyncAwait)
    }
    private fun generateCoverAsync(pathName: String, name: String, author: String?, asyncAwait: Boolean) {
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                if (asyncAwait) {
                    withTimeoutOrNull(1200) {
                        triggerChannel.receive()
                    }
                    ensureActive()
                }
                if (width == 0) {
                    var attempts = 0
                    do {
                        delay(1L)
                        attempts++
                    } while (width == 0 && attempts < 2000)
                }
                ensureActive()
                val bitmap = generateCoverBitmap(name, author)
                ensureActive()
                needNameBitmap.put(bitmapPath.toString(), true)
                nameBitmapCache.put(pathName + width, bitmap)
                invalidate()
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun generateCoverBitmap(name: String?, author: String?): Bitmap {
        viewWidth = width.toFloat()
        viewHeight = height.toFloat()
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
                }
                else if (startY > viewHeight * 0.8 && (name.size - index - 1) > 2) {
                    startX += namePaint.textSize
                    line++
                    namePaint.textSize = viewWidth / 10
                    startY = viewHeight * 0.2f + namePaint.textHeight * line
                }
            }
        }
        if (!drawBookAuthor){
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

    fun setHeight(height: Int) {
        val width = height * 3 / 4
        minimumWidth = width
    }

    /**
     * 根据封面阴影设置更新背景色
     *
     * 当 bookCoverShadow 开启时，填充主题背景色，使透明 PNG 封面有统一底色；
     * 关闭时设为透明，避免透明区域出现灰色/深色底色。
     */
    fun updateCoverBackground() {
        if (AppConfig.bookCoverShadow) {
            setBackgroundColor(appCtx.backgroundColor)
        } else {
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private val glideListener by lazy {
        object : RequestListener<Drawable> {

            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                triggerChannel.trySend(Unit)
                needNameBitmap.put(bitmapPath.toString(), true)
                return false
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                currentJob?.cancel()
                currentJob = null
                needNameBitmap.remove(bitmapPath.toString())
                invalidate()
                return false
            }

        }
    }

    fun load(
        searchBook: SearchBook,
        loadOnlyWifi: Boolean = false,
        fragment: Fragment? = null,
        lifecycle: Lifecycle? = null,
        overrideWidth: Int = 0,
        overrideHeight: Int = 0
    ) {
        val galleryIdentity = listOf(
            searchBook.bookUrl,
            searchBook.origin,
            searchBook.name,
            searchBook.author
        ).joinToString("|")
        load(searchBook.coverUrl, searchBook.name, searchBook.author, loadOnlyWifi, searchBook.origin, fragment, lifecycle, galleryIdentity = galleryIdentity, overrideWidth = overrideWidth, overrideHeight = overrideHeight)
    }

    fun load(
        book: Book,
        loadOnlyWifi: Boolean = false,
        fragment: Fragment? = null,
        lifecycle: Lifecycle? = null,
        overrideWidth: Int = 0,
        overrideHeight: Int = 0,
        onLoadFinish: (() -> Unit)? = null
    ) {
       load(
           book.getDisplayCover(), book.name, book.author,
           loadOnlyWifi, book.origin, fragment, lifecycle,
           galleryIdentity = book.bookUrl,
           overrideWidth = overrideWidth,
           overrideHeight = overrideHeight,
           onLoadFinish = onLoadFinish
       )
    }

    /**
     * 方案A：支持 [BookShelfDisplay] 轻量数据类的封面加载重载。
     *
     * 与 [load]（Book 版本）功能一致，但直接从 [BookShelfDisplay] 获取字段，
     * 无需转换为完整 [Book] 对象。
     */
    fun load(
        display: io.legado.app.data.dao.BookShelfDisplay,
        loadOnlyWifi: Boolean = false,
        fragment: Fragment? = null,
        lifecycle: Lifecycle? = null,
        overrideWidth: Int = 0,
        overrideHeight: Int = 0,
        onLoadFinish: (() -> Unit)? = null
    ) {
        load(
            display.getDisplayCover(), display.name, display.author,
            loadOnlyWifi, display.origin, fragment, lifecycle,
            galleryIdentity = display.bookUrl,
            overrideWidth = overrideWidth,
            overrideHeight = overrideHeight,
            onLoadFinish = onLoadFinish
        )
    }

    /**
     * 书架分组（文件夹）封面加载重载。
     *
     * 以分组 id 作为封面图集的取图身份，使文件夹封面与书籍封面一致，
     * 按封面模式（随机/顺序/混合）从图集中取图；未启用图集时回退到分组自定义封面。
     */
    fun load(group: BookGroup) {
        load(
            path = group.cover,
            galleryIdentity = "bookGroup:${group.groupId}"
        )
    }

    fun load(
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
        onLoadFinish: (() -> Unit)? = null
    ) {
        updateCoverBackground()
        val currentAuthor = author?.replace(AppPattern.bdRegex, "")?.trim()?.also {
            this.author = it
        }
        val currentName = name?.replace(AppPattern.bdRegex, "")?.trim()?.also {
            this.name = it
        }
        val galleryDefaultCover = BookCover.getGalleryDefaultCover(
            galleryIdentity ?: listOfNotNull(sourceOrigin, path, name, author).joinToString("|"),
            path
        )
        val actualPath = galleryDefaultCover ?: path
        this.bitmapPath = actualPath

        // 检查是否启用HTML封面生成（由封面配置页的开关控制）
        if (galleryDefaultCover == null && currentName != null && HtmlCoverRenderer.isApplicable(currentName)) {
            isHtmlCover = true
            loadHtmlCover(currentName, currentAuthor, onLoadFinish)
            return
        }

        isHtmlCover = false

        if (AppConfig.useDefaultCover && galleryDefaultCover == null) {
            ImageLoader.load(context, BookCover.defaultDrawable)
                .centerCrop()
                .into(this)
        } else {
            if (galleryDefaultCover == null && drawBookName && currentName != null) {
                val pathName = if (drawBookAuthor){
                    currentName + currentAuthor
                } else {
                    currentName
                }
                drawNameAuthor(pathName, currentName, currentAuthor, true)
            }
            var options = RequestOptions().set(OkHttpModelLoader.loadOnlyWifiOption, loadOnlyWifi)
            if (sourceOrigin != null) {
                options = options.set(OkHttpModelLoader.sourceOriginOption, sourceOrigin)
            }
            var builder = if (fragment != null && lifecycle != null) {
                ImageLoader.load(fragment, lifecycle, actualPath)
            } else {
                ImageLoader.load(context, actualPath)
            }
            builder = builder.apply(options)
                .placeholder(BookCover.defaultDrawable)
                .error(BookCover.defaultDrawable)
                .listener(glideListener)
            if (onLoadFinish != null) {
                builder = builder.addListener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable?>,
                        isFirstResource: Boolean
                    ): Boolean {
                        onLoadFinish.invoke()
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable?>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        onLoadFinish.invoke()
                        return false
                    }
                })
            }
            if (overrideWidth > 0 && overrideHeight > 0 && !AppConfig.loadCoverHighQuality) {
                builder.override(overrideWidth, overrideHeight)
            }
            builder.centerCrop()
            builder
                .into(this)
        }
    }

    /**
     * 加载 HTML 模板封面。
     *
     * 渲染与缓存都委托给 [HtmlCoverRenderer]（固定 600x900 渲染，与控件实际像素尺寸无关），
     * 这里只负责把结果贴到控件上；渲染失败回退默认封面。
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun loadHtmlCover(bookName: String, author: String?, onLoadFinish: (() -> Unit)?) {
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                val bitmap = HtmlCoverRenderer.load(bookName, author)
                setImageDrawable(bitmap?.toDrawable(resources) ?: BookCover.defaultDrawable)
                onLoadFinish?.invoke()
            } catch (_: CancellationException) {
                // Job 被取消，不执行回调
            } catch (e: Exception) {
                e.printStackTrace()
                setImageDrawable(BookCover.defaultDrawable)
                onLoadFinish?.invoke()
            }
        }
    }

    override fun onDetachedFromWindow() {
        currentJob?.cancel()
        currentJob = null
        super.onDetachedFromWindow()
    }

    /**
     * 方案E：取消正在进行的封面图片加载请求。
     *
     * 在 RecyclerView onViewRecycled 时调用，避免回收的 ViewHolder
     * 继续持有 Glide 请求导致不必要的网络/磁盘 IO 和内存占用。
     */
    fun cancelLoad() {
        kotlin.runCatching {
            Glide.with(context).clear(this)
        }
        currentJob?.cancel()
        currentJob = null
    }

}
