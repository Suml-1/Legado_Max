package io.legado.app.ui.main.explore.compose

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateMapOf
import com.script.rhino.runScriptWithContext
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreInfoMapList
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.obtainExploreInfoMap
import io.legado.app.help.webView.PooledWebView
import io.legado.app.help.webView.WebJsExtensions.Companion.nameCache
import io.legado.app.help.webView.WebJsExtensions.Companion.nameJava
import io.legado.app.help.webView.WebJsExtensions.Companion.nameSource
import io.legado.app.help.webView.WebViewPool
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.utils.InfoMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 发现页书源分类（[ExploreKind]）的控制器。
 *
 * 承担三类不变量：
 * 1. **书源 JS 求值**——分类的展示文案（`viewName`）与交互动作（`action`）都要跑书源脚本，
 *    脚本需要 `InfoMap` 与 `SourceLoginJsExtensions`，这些是有状态对象，不适合放在 Composable 里；
 * 2. **infoMap 生命周期**——用户输入与 toggle/select 的当前项写在 infoMap 上，页面暂停时统一落盘；
 * 3. **内联 WebView 生命周期**——`useweb` 内容用的是池化 WebView，页面暂停/恢复/销毁必须成对处理。
 *
 * 作用域跟着 `viewLifecycleOwner`：视图销毁后，这里发起的 JS 任务与 WebView 都随之结束。
 */
class ExploreKindsController(
    private val activity: AppCompatActivity?,
    private val scope: CoroutineScope,
) {

    private val sources = HashMap<String, BookSource?>()
    private val jsExtensions = HashMap<String, SourceLoginJsExtensions>()

    /** 键为"书源 URL + 内容签名"，见 [attachWebView] */
    private val activeWebViews = linkedMapOf<String, PooledWebView>()

    /** 每个书源的"重建分类"信号：长按菜单刷新与登录后的规则回调都会用到 */
    private val refreshTicks = mutableStateMapOf<String, Int>()

    fun infoMap(sourceUrl: String): InfoMap = obtainExploreInfoMap(sourceUrl)

    /** 当前生效的重建信号，条目用它做重组键；0 表示从未刷新过 */
    fun refreshTick(sourceUrl: String): Int = refreshTicks[sourceUrl] ?: 0

    /** 要求重建某个书源的分类内容（对齐原实现的"刷新"与 `reUiView` 回调） */
    fun requestRefresh(sourceUrl: String) {
        refreshTicks[sourceUrl] = (refreshTicks[sourceUrl] ?: 0) + 1
    }

    /** 读取书源的发现分类；[exploreKinds] 自带进程内缓存，这里不再叠一层缓存。 */
    suspend fun loadKinds(sourceUrl: String): List<ExploreKind> {
        val source = bookSource(sourceUrl) ?: return emptyList()
        return source.exploreKinds()
    }

    /** 清掉分类缓存后重新求值（长按菜单里的"刷新"）。 */
    suspend fun reloadKinds(sourceUrl: String): List<ExploreKind> {
        val source = bookSource(sourceUrl) ?: return emptyList()
        source.clearExploreKindsCache()
        return source.exploreKinds()
    }

    /**
     * 求值分类的 `viewName` 脚本，得到动态展示文案。
     *
     * 返回 `Result` 而不是可空值：调用方要区分"求值失败（显示 err）"与"求值为空（显示 null）"，
     * 这与原实现 `onSuccess { 空 → null } / onError { err }` 的兜底口径一致。
     */
    suspend fun evalName(sourceUrl: String, jsStr: String): Result<String?> {
        val source = bookSource(sourceUrl)
            ?: return Result.success(null)
        return withContext(IO) {
            runCatching {
                runScriptWithContext {
                    source.evalJS(jsStr) {
                        put("infoMap", infoMap(sourceUrl))
                    }.toString()
                }
            }.onFailure {
                AppLog.put(source.getTag() + " exploreUi err:" + (it.localizedMessage ?: it.toString()), it)
            }
        }
    }

    /**
     * 执行分类的 `action` 脚本（button / toggle / select / 文本输入的点击行为）。
     *
     * 不阻塞调用方：点击后立即返回，脚本在 [scope] 上跑。
     */
    fun evalAction(sourceUrl: String, jsStr: String, name: String) {
        scope.launch(IO) {
            val source = bookSource(sourceUrl) ?: return@launch
            try {
                // JS 桥要在进入脚本环境前就绪：runScriptWithContext 的 lambda 不是挂起上下文
                val java = sourceJsExtensions(sourceUrl)
                val infoMap = infoMap(sourceUrl)
                runScriptWithContext {
                    source.evalJS(jsStr) {
                        put("java", java)
                        put("infoMap", infoMap)
                    }
                }
            } catch (e: Exception) {
                AppLog.put("ExploreUI Button $name JavaScript error", e)
            }
        }
    }

    /**
     * 解析 html 类分类项的内容（原 `ExploreAdapter.resolveHtmlContent`）。
     *
     * 内容有三个来源，按优先级依次是：`url` 里的 html 载荷、`title` 里的 html 载荷、
     * `viewName`（字面量或 JS 求值）。三者都不是 html 载荷时返回 null，调用方据此不渲染。
     */
    suspend fun resolveHtmlContent(kind: ExploreKind, sourceUrl: String): String? {
        kind.url?.takeIf { it.isHtmlPayload() }?.let { return it }
        kind.title.takeIf { it.isHtmlPayload() }?.let { return it }
        val viewName = kind.viewName ?: return null
        if (viewName.isQuotedLiteral()) {
            return viewName.substring(1, viewName.length - 1)
        }
        return evalName(sourceUrl, viewName).getOrNull()
    }

    /** 供内联 WebView 判断场景用（是否有 `useweb` 载荷、取书源对象给 JS 桥） */
    suspend fun source(sourceUrl: String): BookSource? = bookSource(sourceUrl)

    private suspend fun bookSource(sourceUrl: String): BookSource? {
        sources[sourceUrl]?.let { return it }
        val source = withContext(IO) { appDb.bookSourceDao.getBookSource(sourceUrl) }
        sources[sourceUrl] = source
        return source
    }

    private suspend fun sourceJsExtensions(sourceUrl: String): SourceLoginJsExtensions {
        jsExtensions[sourceUrl]?.let { return it }
        val source = bookSource(sourceUrl)
        val extensions = SourceLoginJsExtensions(
            activity,
            source,
            callback = object : SourceLoginJsExtensions.Callback {
                override fun upUiData(data: Map<String, Any?>?) = Unit

                override fun reUiView(deltaUp: Boolean) {
                    requestRefresh(sourceUrl)
                }
            }
        )
        jsExtensions[sourceUrl] = extensions
        return extensions
    }

    // ── 内联 WebView 生命周期 ──

    fun attachWebView(key: String, pooledWebView: PooledWebView) {
        activeWebViews[key] = pooledWebView
    }

    fun releaseWebView(key: String) {
        activeWebViews.remove(key)?.let(::releasePooledWebView)
    }

    fun pauseWebViews() {
        activeWebViews.values.forEach { it.realWebView.onPause() }
        saveInfoMaps()
    }

    fun resumeWebViews() {
        activeWebViews.values.forEach { it.realWebView.onResume() }
    }

    fun releaseAllWebViews() {
        activeWebViews.keys.toList().forEach(::releaseWebView)
    }

    /**
     * 归还池化 WebView。
     *
     * 必须先摘掉按书源注入的 JS 桥——池化实例会被下一个书源的行复用，
     * 残留桥会让回调打到错误的书源与上下文。
     */
    private fun releasePooledWebView(pooledWebView: PooledWebView) {
        pooledWebView.realWebView.apply {
            removeJavascriptInterface(nameCache)
            removeJavascriptInterface(nameSource)
            removeJavascriptInterface(nameJava)
        }
        WebViewPool.release(pooledWebView)
    }

    /** 把标记为待保存的 infoMap 立即落盘（对齐原 `ExploreAdapter.onPause` 的行为） */
    fun saveInfoMaps() {
        exploreInfoMapList.snapshot()
            .filter { (_, infoMap) -> infoMap.needSave }
            .forEach { (_, infoMap) -> infoMap.saveNow() }
    }
}
