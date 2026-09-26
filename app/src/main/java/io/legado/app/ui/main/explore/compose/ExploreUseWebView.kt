package io.legado.app.ui.main.explore.compose

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.LruCache
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.help.WebCacheManager
import io.legado.app.help.webView.PooledWebView
import io.legado.app.help.webView.Scope
import io.legado.app.help.webView.WebJsExtensions
import io.legado.app.help.webView.WebJsExtensions.Companion.buildUseWebInjection
import io.legado.app.help.webView.WebJsExtensions.Companion.nameCache
import io.legado.app.help.webView.WebJsExtensions.Companion.nameJava
import io.legado.app.help.webView.WebJsExtensions.Companion.nameSource
import io.legado.app.help.webView.WebJsExtensions.Companion.wrapUseWebHtml
import io.legado.app.help.webView.WebViewPool
import io.legado.app.help.webView.WebViewPool.currentInlineContentGeneration
import io.legado.app.help.webView.WebViewPool.installInlineContentRefitOnTouch
import io.legado.app.help.webView.WebViewPool.prepareForInlineContent
import io.legado.app.help.webView.WebViewPool.scheduleInlineContentFit
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.ui.theme.AppDimens
import io.legado.app.utils.GSON
import io.legado.app.utils.InfoMap
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.openUrl
import io.legado.app.utils.startActivity

/**
 * `useweb` 内容的进程内高度缓存。
 *
 * 行被回收后再回来时先用上次测出的高度占位，避免内容从占位高度跳到真实高度的闪动。
 * 键由书源 URL + 内容 + infoMap 上下文 + 页码签名得到（见 [buildExploreUseWebLayoutKey]）。
 */
private val exploreWebViewHeightCache = LruCache<String, Int>(99)

/**
 * `<useweb>` 网页内容。
 *
 * Compose 侧只负责"给多高、什么时候回收"：WebView 由 [WebViewPool] 池化提供，
 * 页面脚本注入、JS 桥、URL 拦截、高度测量都沿用原 View 实现的行为。
 * 与 View 版的唯一差异是高度拟合时机——这里高度由 Compose 状态驱动（[fitInlineContent] 立即取真实高度），
 * 不再用动画逐帧改 View 的 layoutParams。
 */
@Composable
internal fun ExploreUseWebContent(
    modifier: Modifier,
    payload: String,
    sourceUrl: String,
    controller: ExploreKindsController,
) {
    val endIndex = payload.lastIndexOf('<')
    if (endIndex < HTML_KIND_USE_WEB.length) return
    val useWebHtml = payload.substring(HTML_KIND_USE_WEB.length, endIndex)
    val bookSource by produceState<BookSource?>(initialValue = null, key1 = sourceUrl) {
        value = controller.source(sourceUrl)
    }
    val source = bookSource ?: return
    val context = LocalContext.current
    val density = LocalDensity.current
    val loadingHeightPx = with(density) { AppDimens.exploreWebLoadingHeight.roundToPx() }
    val infoMap = remember(sourceUrl, controller) { controller.infoMap(sourceUrl) }
    val initialPage = remember(sourceUrl, useWebHtml) {
        infoMap["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    }
    val pageStateKey = remember(sourceUrl, useWebHtml) {
        buildExploreUseWebStateKey(source, useWebHtml, infoMap)
    }
    val pageLayoutKey = remember(pageStateKey, initialPage) {
        buildExploreUseWebLayoutKey(pageStateKey, initialPage)
    }
    val cachedHeight = remember(pageLayoutKey) {
        exploreWebViewHeightCache[pageLayoutKey]?.takeIf { it > 1 }
    }
    var webViewHeightPx by remember(pageLayoutKey) {
        mutableIntStateOf(cachedHeight ?: loadingHeightPx)
    }
    val container = remember(pageLayoutKey, context) {
        FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }
    var pooledWebView by remember(pageLayoutKey) { mutableStateOf<PooledWebView?>(null) }

    LaunchedEffect(pageLayoutKey) {
        val pooled = createExploreWebHost(
            container = container,
            context = context,
            activity = context as? AppCompatActivity,
            useWebHtml = useWebHtml,
            source = source,
            pageStateKey = pageStateKey,
            initialPage = initialPage,
            initialHeightPx = cachedHeight ?: loadingHeightPx,
            pageLayoutKey = pageLayoutKey,
            onHeightMeasured = { heightPx ->
                exploreWebViewHeightCache.put(pageLayoutKey, heightPx)
                webViewHeightPx = heightPx
            },
        )
        controller.attachWebView(pageLayoutKey, pooled)
        pooledWebView = pooled
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(with(LocalDensity.current) { webViewHeightPx.toDp() }),
        factory = { container },
        onRelease = {
            // 视图离开组合（折叠、滑出屏幕、页面销毁）即归还 WebView，与 View 版的回收时机一致
            controller.releaseWebView(pageLayoutKey)
            pooledWebView = null
        },
    )
}

/**
 * 创建并加载 useweb 宿主：池化 WebView + 加载占位 + 脚本注入 + 高度测量。
 *
 * 对齐原 `ExploreAdapter.bindExploreWebView`。
 */
@SuppressLint("SetJavaScriptEnabled")
private fun createExploreWebHost(
    container: FrameLayout,
    context: Context,
    activity: AppCompatActivity?,
    useWebHtml: String,
    source: BookSource,
    pageStateKey: String,
    pageLayoutKey: String,
    initialPage: Int,
    initialHeightPx: Int,
    onHeightMeasured: (Int) -> Unit,
): PooledWebView {
    val pageJs = buildExploreUseWebPageInjection(pageStateKey, initialPage)
    val html = wrapExploreUseWebHtml(useWebHtml, source, pageJs)
    val pooledWebView = WebViewPool.acquire(context, Scope.INLINE)
    val webView = pooledWebView.realWebView
    webView.onResume()
    prepareForInlineContent(webView, initialHeightPx)
    val loadingIndicator = createLoadingIndicator(context, initialHeightPx)
    container.removeAllViews()
    container.addView(loadingIndicator)
    webView.visibility = View.INVISIBLE
    webView.webViewClient = ExploreInlineWebViewClient(
        container = container,
        activity = activity,
        source = source,
        pageJs = pageJs,
        pageLayoutKey = pageLayoutKey,
        loadingIndicator = loadingIndicator,
        onHeightMeasured = onHeightMeasured,
    )
    installInlineContentRefitOnTouch(webView) {
        syncExploreWebHeight(webView, onHeightMeasured)
        container.requestLayout()
    }
    webView.addJavascriptInterface(WebCacheManager, nameCache)
    webView.addJavascriptInterface(source as BaseSource, nameSource)
    webView.addJavascriptInterface(WebJsExtensions(source, activity, webView), nameJava)
    container.addView(webView)
    val baseUrl = source.bookSourceUrl.takeIf { it.startsWith("http", true) }
    webView.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", baseUrl)
    return pooledWebView
}

/** 把 WebView 当前拟合出的高度同步给 Compose（高度由 Compose 决定，View 侧只负责测量） */
private fun syncExploreWebHeight(webView: WebView, onHeightMeasured: (Int) -> Unit) {
    val height = webView.layoutParams?.height ?: 0
    if (height > 1) {
        onHeightMeasured(height)
    }
}

private fun createLoadingIndicator(context: Context, heightPx: Int): ProgressBar {
    return ProgressBar(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            heightPx,
            Gravity.CENTER,
        )
        indeterminateTintList = ColorStateList.valueOf(context.accentColor)
    }
}

/**
 * 内联网页的 WebViewClient：对齐原 `ExploreAdapter.ExploreHtmlWebViewClient`，
 * 只把"高度写进 View 布局参数"换成"高度回抛给 Compose"。
 */
private class ExploreInlineWebViewClient(
    private val container: FrameLayout,
    private val activity: AppCompatActivity?,
    private val source: BaseSource?,
    private val pageJs: String,
    private val pageLayoutKey: String,
    private val loadingIndicator: ProgressBar,
    private val onHeightMeasured: (Int) -> Unit,
) : WebViewClient() {

    private val jsStr = buildString {
        append(buildUseWebInjection(source))
        if (pageJs.isNotBlank()) {
            append('\n')
            append(pageJs)
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        request?.let {
            val uri = it.url
            return when (uri.scheme) {
                "http", "https" -> false
                "legado", "yuedu" -> {
                    activity?.startActivity<OnLineImportActivity> { data = uri }
                    true
                }

                else -> {
                    activity?.findViewById<View>(android.R.id.content)
                        ?.longSnackbar(R.string.jump_to_another_app, R.string.confirm) {
                            activity.openUrl(uri)
                        }
                    true
                }
            }
        }
        return true
    }

    private fun injectPageState(webView: WebView, delayedRetries: LongArray = longArrayOf()) {
        if (jsStr.isBlank()) return
        webView.evaluateJavascript(jsStr, null)
        delayedRetries.forEach { delayMillis ->
            webView.postDelayed({
                if (!webView.isAttachedToWindow) return@postDelayed
                webView.evaluateJavascript(jsStr, null)
            }, delayMillis)
        }
    }

    private fun cacheMeasuredHeight(webView: WebView) {
        syncExploreWebHeight(webView, onHeightMeasured)
        loadingIndicator.visibility = View.GONE
        webView.visibility = View.VISIBLE
        container.requestLayout()
    }

    private fun fitAndCacheHeight(webView: WebView, delayed: Boolean) {
        if (delayed) {
            scheduleInlineContentFit(webView, { cacheMeasuredHeight(webView) }, longArrayOf(120L, 360L, 720L))
        } else {
            WebViewPool.fitInlineContent(
                webView,
                currentInlineContentGeneration(webView),
                afterLayout = { cacheMeasuredHeight(webView) },
            )
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view?.let { webView -> injectPageState(webView) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.let { webView ->
            injectPageState(webView, longArrayOf(120L, 360L))
            fitAndCacheHeight(webView, delayed = false)
            fitAndCacheHeight(webView, delayed = true)
        }
    }
}

// ── 页面状态 / 布局签名（对齐原实现，保证相同书源与上下文复用同一页与同一高度） ──

private fun buildExploreUseWebStateKey(
    source: BookSource?,
    html: String,
    infoMap: InfoMap? = null,
): String {
    val contextSignature = buildExploreUseWebContextSignature(infoMap)
    return buildString {
        append("useweb_state_")
        append(source?.bookSourceUrl?.let(MD5Utils::md5Encode16) ?: "default")
        append('_')
        append(MD5Utils.md5Encode16(html))
        append('_')
        append(contextSignature)
    }
}

private fun buildExploreUseWebLayoutKey(stateKey: String, page: Int): String {
    return "${stateKey}_layout_${page.coerceAtLeast(1)}"
}

/** 上下文签名：排除 `page` 字段后对 infoMap 签名，用于判断"同一份内容的不同上下文" */
private fun buildExploreUseWebContextSignature(infoMap: InfoMap?): String {
    if (infoMap == null || infoMap.isEmpty()) return "default"
    val normalizedContext = infoMap.entries
        .asSequence()
        .filter { (key, _) -> !key.equals("page", ignoreCase = true) }
        .sortedBy { it.key }
        .joinToString("&") { (key, value) -> "$key=$value" }
    return if (normalizedContext.isBlank()) {
        "default"
    } else {
        MD5Utils.md5Encode16(normalizedContext)
    }
}

/**
 * 注入 `page` 属性的读写能力，让书源脚本里的分页状态在 WebView 重载后仍能恢复。
 * 对齐原 `ExploreAdapter.buildExploreUseWebPageInjection`。
 */
private fun buildExploreUseWebPageInjection(pageKey: String, initialPage: Int): String {
    val safePage = initialPage.coerceAtLeast(1)
    val keyJson = GSON.toJson(pageKey)
    return """
        try{
            const __useWebPageKey = $keyJson;
            const __useWebDefaultPage = $safePage;
            const __readUseWebPage = () => {
                const cachedPage = parseInt(cache.getFromMemory(__useWebPageKey), 10);
                return Number.isFinite(cachedPage) && cachedPage > 0 ? cachedPage : __useWebDefaultPage;
            };
            const __writeUseWebPage = value => {
                const nextPage = parseInt(value, 10);
                const safeNextPage = Number.isFinite(nextPage) && nextPage > 0 ? nextPage : __useWebDefaultPage;
                cache.putMemory(__useWebPageKey, String(safeNextPage));
                return safeNextPage;
            };
            Object.defineProperty(window, 'page', {
                configurable: true,
                get() {
                    return __readUseWebPage();
                },
                set(value) {
                    __writeUseWebPage(value);
                }
            });
            if (typeof Element !== 'undefined' && !Object.getOwnPropertyDescriptor(Element.prototype, 'page')) {
                Object.defineProperty(Element.prototype, 'page', {
                    configurable: true,
                    get() {
                        return window.page;
                    },
                    set(value) {
                        window.page = value;
                    }
                });
            }
            if (java && typeof java.open === 'function' && !java.__exploreUseWebPageWrapped) {
                const __rawOpen = java.open.bind(java);
                java.open = function(name, url, title, origin) {
                    if (name === 'explore' && typeof url === 'string') {
                        const match = url.match(/[?&]page=(\d+)/i);
                        if (match) {
                            __writeUseWebPage(parseInt(match[1], 10) + 1);
                        }
                    }
                    return __rawOpen(name, url, title, origin);
                };
                java.__exploreUseWebPageWrapped = true;
            }
        }catch(e){}
    """.trimIndent()
}

/**
 * 包装 useweb 的 HTML：透明背景 + 注入脚本。
 *
 * 透明背景是必须的——发现页的壁纸由主界面承载，WebView 自己画白底会盖住壁纸。
 */
private fun wrapExploreUseWebHtml(html: String, source: BookSource?, pageJs: String): String {
    val inlineStyle = """
        <style>
        html,body{background:transparent;}
        </style>
    """.trimIndent()
    val injection = buildString {
        val baseJs = buildUseWebInjection(source).trim()
        if (baseJs.isNotEmpty()) {
            append(baseJs)
        }
        if (pageJs.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(pageJs.trim())
        }
    }
    if (injection.isBlank()) {
        return "$inlineStyle\n$html"
    }
    val safeInjection = Regex("(?i)</script>").replace(injection, "<\\\\/script>")
    return "$inlineStyle\n<script>\n$safeInjection\n</script>\n$html"
}
