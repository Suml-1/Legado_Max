package io.legado.app.help.glide

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.collection.LruCache
import androidx.core.graphics.createBitmap
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.CoverHtmlTemplateConfig
import io.legado.app.model.BookCover
import io.legado.app.utils.getPrefBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import splitties.init.appCtx

/**
 * HTML 模板封面渲染器。
 *
 * 封面在没有真实图片（无封面地址、且未命中封面图集默认封面）时，可由用户配置的 HTML 模板
 * 生成：模板渲染在固定 600x900 的 WebView 里，再绘制成 Bitmap 交给调用方显示。
 *
 * 这里是 HTML 封面生成的**唯一实现**：`CoverImageView`（书架/列表等固定比例场景）与
 * `CoverLoader`（瀑布流等自由比例场景）都从这里取图，避免同一套 WebView 渲染逻辑散落多份；
 * 缓存同样集中在这里，`clearCache()` 是模板变更后唯一的失效入口。
 */
object HtmlCoverRenderer {

    /** 固定渲染尺寸：与设备上封面控件的实际像素尺寸无关，保证不同尺寸下排版一致 */
    private const val RENDER_WIDTH = 600
    private const val RENDER_HEIGHT = 900

    /** 渲染结果缓存，键为「模板 id + 书名 + 作者」 */
    private val cache by lazy { LruCache<String, Bitmap>(50) }

    /**
     * 判断当前是否满足生成 HTML 封面的条件：开关开启、选中模板内容非空、且有书名。
     * 命中封面图集默认封面或无封面地址时调用方会先走别的分支，这里只做条件判断。
     */
    fun isApplicable(bookName: String?): Boolean {
        if (bookName.isNullOrBlank()) return false
        if (!appCtx.getPrefBoolean(PreferKey.coverHtmlEnable)) return false
        return CoverHtmlTemplateConfig.getSelectedTemplate().htmlCode.isNotBlank()
    }

    /**
     * 取 HTML 封面：命中缓存直接返回，否则渲染后写入缓存。
     * 渲染失败（模板为空、WebView 异常、超时）返回 null，由调用方回退到默认封面。
     */
    suspend fun load(bookName: String, author: String?): Bitmap? {
        val template = CoverHtmlTemplateConfig.getSelectedTemplate()
        val htmlCode = template.htmlCode
        if (htmlCode.isBlank()) return null
        val cacheKey = "${template.id}-$bookName-$author"
        cache[cacheKey]?.let { return it }
        val html = BookCover.renderHtmlTemplate(htmlCode, bookName, author.orEmpty())
        val bitmap = render(html) ?: return null
        cache.put(cacheKey, bitmap)
        return bitmap
    }

    /** 清空渲染缓存：模板内容变更、切换选中模板、切换开关后调用 */
    fun clearCache() {
        cache.evictAll()
    }

    /**
     * 用 WebView 渲染 HTML 为 Bitmap。
     *
     * 用 applicationContext 创建 WebView，用完立即销毁；页面加载完成后再等 300ms 让 CSS
     * 布局稳定，最多等 2 秒（40 × 50ms），超时按当前内容绘制。
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun render(html: String): Bitmap? = withContext(Dispatchers.Main) {
        var webView: WebView? = null
        try {
            webView = WebView(appCtx).apply {
                settings.javaScriptEnabled = true
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = false
                setInitialScale(100)
                measure(
                    View.MeasureSpec.makeMeasureSpec(RENDER_WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(RENDER_HEIGHT, View.MeasureSpec.EXACTLY)
                )
                layout(0, 0, RENDER_WIDTH, RENDER_HEIGHT)
            }

            var renderComplete = false
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.postDelayed({ renderComplete = true }, 300)
                }
            }
            webView.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)

            var attempts = 0
            while (!renderComplete && attempts < 40) {
                delay(50)
                attempts++
            }

            webView.measure(
                View.MeasureSpec.makeMeasureSpec(RENDER_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(RENDER_HEIGHT, View.MeasureSpec.EXACTLY)
            )
            webView.layout(0, 0, RENDER_WIDTH, RENDER_HEIGHT)
            val bitmap = createBitmap(RENDER_WIDTH, RENDER_HEIGHT)
            webView.draw(Canvas(bitmap))
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            runCatching {
                webView?.stopLoading()
                webView?.destroy()
            }
        }
    }
}
