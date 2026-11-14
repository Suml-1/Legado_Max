package io.legado.app.model

import androidx.collection.LruCache
import com.google.gson.reflect.TypeToken
import com.script.CompiledScript
import com.script.ScriptBindings
import com.script.rhino.RhinoClassShutter
import com.script.rhino.RhinoScriptEngine
import io.legado.app.constant.AppLog
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.htmlunit.corejs.javascript.BaseFunction
import org.htmlunit.corejs.javascript.Context
import org.htmlunit.corejs.javascript.Scriptable
import org.htmlunit.corejs.javascript.ScriptableObject
import org.htmlunit.corejs.javascript.VarScope
import splitties.init.appCtx
import java.io.File
import java.lang.ref.WeakReference
import java.security.SecureRandom
import kotlin.coroutines.CoroutineContext

/**
 * 书源共享 JS 作用域管理器。
 *
 * 书源 `BookSource.jsLib` 允许作者声明一段跨规则复用的 JS 代码：
 * 可以是普通 JS 字符串、JSON 对象 `{ "名字": "内联JS 或 远程URL" }`，
 * 或结构化 bundle（`legado.js.library/1`，见 [JsLibraryBundle]）。
 *
 * 本对象承担 4 件事：
 * 1. 把 jsLib 中指向 URL 的片段下载并落到 [ACache] 磁盘缓存，避免每次执行都联网；
 * 2. 把所有片段拼装成一份可直接 eval / 内联注入的 JS 字符串（[getJsLibString]）；
 * 3. 构建共享 [ScriptBindings] 运行时作用域（[getScope]）并按书源命名空间隔离；
 * 4. 内置 CryptoJS（[installCryptoJs] / [getCryptoScope]）：从 assets 读取
 *    `scripts/cryptojs.min.js` 编译一次，脚本里可直接 `CryptoJS.MD5(...)`。
 *
 * 典型调用点：
 * - AnalyzeRule / AnalyzeUrl 执行书源规则时通过 [getShareScope] 取共享作用域；
 * - [io.legado.app.model.jsSource.JsSourceEngine] 通过 [getCryptoScope] 兜底；
 * - [io.legado.app.help.webView.WebJsExtensions] 通过 [getJsLibString] 内联注入 WebView。
 *
 * 注意：legacy 映射形式的 URL 片段在缓存未命中时用 [runBlocking] 同步下载，
 * 调用方应尽量放到 IO 线程，或提前用 [prefetch] 预热。
 */
object SharedJsScope {

    private const val CRYPTO_JS_ASSET = "scripts/cryptojs.min.js"
    private const val SECURE_RANDOM_BINDING = "__legadoSecureRandomInt"

    /** jsLib 下载后的磁盘缓存目录（APP cacheDir/shareJs），随系统清理 */
    private val aCache by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ACache.get(File(appCtx.cacheDir, "shareJs"))
    }

    /** jsLib → 共享作用域缓存，key 含策略前缀 + 命名空间 + MD5(jsLib) */
    private val scopeMap = LruCache<String, WeakReference<ScriptBindings>>(16)

    /** 无 jsLib 但需要内置 CryptoJS 时的兜底作用域缓存 */
    private val cryptoScopeMap = LruCache<String, WeakReference<ScriptBindings>>(16)
    private val cryptoScopeLock = Any()

    @Volatile
    private var cryptoJsScript: CompiledScript? = null
    private val cryptoCompileLock = Any()
    private val secureRandom by lazy { SecureRandom() }

    /** [prefetch] 专用后台作用域，单个 URL 失败不影响其它 URL */
    private val prefetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 把内置 CryptoJS 装进 [scope]，并把 `WordArray.random` 换成宿主 [SecureRandom]。
     *
     * 脚本侧的 `Math.random`/`CryptoJS.lib.WordArray.random` 默认实现安全性不足，
     * 这里用临时绑定 `__legadoSecureRandomInt` 把宿主随机源注入后立即删除。
     *
     * @return 资源缺失（老安装包）时返回 false，调用方应视为「无 CryptoJS」继续运行
     */
    internal fun installCryptoJs(
        scope: ScriptBindings,
        coroutineContext: CoroutineContext?,
    ): Boolean {
        val cryptoJs = compiledCryptoJs() ?: return false
        cryptoJs.eval(scope, coroutineContext)
        val secureRandomFunction = object : BaseFunction() {
            override fun call(
                cx: Context,
                callScope: VarScope,
                thisObj: Scriptable,
                args: Array<Any>,
            ): Any = secureRandom.nextInt().toDouble()
        }.apply {
            parentScope = scope
            prototype = ScriptableObject.getFunctionPrototype(scope)
        }
        ScriptableObject.putProperty(scope, SECURE_RANDOM_BINDING, secureRandomFunction)
        try {
            RhinoScriptEngine.eval(SECURE_RANDOM_PATCH, scope, coroutineContext)
        } finally {
            scope.delete(SECURE_RANDOM_BINDING)
        }
        return true
    }

    /**
     * 取「只含内置 CryptoJS」的共享作用域（书源没有有效 jsLib 时的兜底）。
     *
     * key 带线程 id：ScriptBindings 的全局对象不是线程安全的，
     * 不同线程各自持有一份，避免并发执行同一 JS 书源时互相踩。
     */
    fun getCryptoScope(
        scopeNamespace: String,
        coroutineContext: CoroutineContext?,
        bookSourceClassPolicy: Boolean = false,
        bookSourceLabel: String? = null,
    ): ScriptBindings? {
        return RhinoClassShutter.withBookSourceClassPolicy(
            enabled = bookSourceClassPolicy,
            sourceLabel = bookSourceLabel,
        ) {
            val key = "${if (bookSourceClassPolicy) "bookSource" else "default"}:" +
                "$scopeNamespace:${Thread.currentThread().id}"
            synchronized(cryptoScopeLock) {
                cryptoScopeMap[key]?.get()?.let { return@withBookSourceClassPolicy it }
                val scope = RhinoScriptEngine.getRuntimeScope(ScriptBindings())
                if (!installCryptoJs(scope, coroutineContext)) {
                    return@withBookSourceClassPolicy null
                }
                scope.globalThis.preventExtensions()
                cryptoScopeMap.put(key, WeakReference(scope))
                scope
            }
        }
    }

    /**
     * 取或构建基于 jsLib 的共享作用域。
     *
     * @param jsLib 书源 jsLib 字段原文，空则返回 `null`
     * @param coroutineContext 传给 eval 的协程上下文，用于取消传播
     * @param bookSourceClassPolicy 是否套用书源类访问策略（书源规则执行时为 true）
     * @param scopeNamespace 书源命名空间，`bookSourceClassPolicy` 为 true 时必填
     */
    fun getScope(
        jsLib: String?,
        coroutineContext: CoroutineContext?,
        bookSourceClassPolicy: Boolean = false,
        bookSourceLabel: String? = null,
        scopeNamespace: String? = null
    ): ScriptBindings? {
        if (jsLib.isNullOrBlank()) {
            return null
        }
        return RhinoClassShutter.withBookSourceClassPolicy(
            enabled = bookSourceClassPolicy,
            sourceLabel = bookSourceLabel
        ) {
            val key = scopeKey(jsLib, bookSourceClassPolicy, scopeNamespace)
            var scope = scopeMap[key]?.get()
            if (scope == null) {
                scope = RhinoScriptEngine.run {
                    getRuntimeScope(ScriptBindings())
                }
                val bundle = JsLibraryBundle.parse(jsLib)
                // 未声明 builtins 的历史 bundle 与所有旧写法默认都装 CryptoJS
                if (bundle?.usesCryptoJs != false) installCryptoJs(scope, coroutineContext)
                if (bundle != null) {
                    bundle.scripts.forEach { script ->
                        RhinoScriptEngine.eval(script, scope, coroutineContext)
                    }
                } else if (jsLib.isJsonObject()) {
                    val jsMap: Map<String, String> = GSON.fromJson(
                        jsLib,
                        TypeToken.getParameterized(
                            Map::class.java,
                            String::class.java,
                            String::class.java
                        ).type
                    )
                    jsMap.values.forEach { value ->
                        if (value.isAbsUrl()) {
                            evalLegacyUrlFragment(value, scope, coroutineContext)
                        }
                    }
                } else {
                    RhinoScriptEngine.eval(jsLib, scope, coroutineContext)
                }
                /**
                 * 阻止新全局增加（即函数内未用var的隐性全局变量创建）,会直接隐性创建失败,提示变量未定义
                 */
                scope.globalThis.preventExtensions()
                scopeMap.put(key, WeakReference(scope))
            }
            scope
        }
    }

    /**
     * 构建一次性隔离作用域：不进 [scopeMap] 缓存，全局对象与常规书源请求共享的那份
     * 作用域完全独立。定时任务等并行后台 worker 专用——worker 脚本对内置对象/原型、
     * 作用域变量的篡改只影响本次执行，不会污染书源/订阅源请求共用的共享作用域。
     */
    fun createIsolatedScope(
        jsLib: String?,
        coroutineContext: CoroutineContext?,
    ): ScriptBindings {
        val scope = RhinoScriptEngine.run {
            getRuntimeScope(ScriptBindings())
        }
        installCryptoJs(scope, coroutineContext)
        if (!jsLib.isNullOrBlank()) {
            val bundle = JsLibraryBundle.parse(jsLib)
            if (bundle != null) {
                bundle.scripts.forEach { script ->
                    RhinoScriptEngine.eval(script, scope, coroutineContext)
                }
            } else if (jsLib.isJsonObject()) {
                val jsMap: Map<String, String> = GSON.fromJson(
                    jsLib,
                    TypeToken.getParameterized(
                        Map::class.java,
                        String::class.java,
                        String::class.java
                    ).type
                )
                jsMap.values.forEach { value ->
                    if (value.isAbsUrl()) {
                        evalLegacyUrlFragment(value, scope, coroutineContext)
                    }
                }
            } else {
                RhinoScriptEngine.eval(jsLib, scope, coroutineContext)
            }
        }
        // 与常规共享作用域一致：阻止隐性全局变量创建
        scope.globalThis.preventExtensions()
        return scope
    }

    /**
     * 只做「下载 + 拼装」，返回可直接塞给 WebView / eval 的 JS 字符串，
     * 不构建作用域，也不进 [scopeMap] 缓存。
     */
    fun getJsLibString(jsLib: String?): String? {
        if (jsLib.isNullOrBlank()) {
            return null
        }
        JsLibraryBundle.parse(jsLib)?.let { return it.scripts.joinToString("\n") }
        if (!jsLib.isJsonObject()) {
            return jsLib
        }
        val jsMap: Map<String, String> = GSON.fromJson(
            jsLib,
            TypeToken.getParameterized(
                Map::class.java,
                String::class.java,
                String::class.java
            ).type
        )
        return buildString {
            jsMap.values.forEach { value ->
                val js = if (value.isAbsUrl()) loadLegacyUrlFragment(value) else value
                if (!js.isNullOrBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(js)
                }
            }
        }.takeIf { it.isNotBlank() }
    }

    /**
     * 后台异步预下载 legacy 映射形式里 URL 片段到磁盘缓存。
     *
     * - bundle / 纯内联 jsLib：no-op（bundle 的代码本来就是内联的）；
     * - 下载失败静默，仅打 [AppLog]，不抛。
     */
    fun prefetch(jsLib: String?) {
        if (jsLib.isNullOrBlank() || !jsLib.isJsonObject()) return
        if (JsLibraryBundle.parse(jsLib) != null) return
        val jsMap: Map<String, String> = runCatching {
            GSON.fromJson(
                jsLib,
                TypeToken.getParameterized(
                    Map::class.java,
                    String::class.java,
                    String::class.java
                ).type
            ) as? Map<String, String>
        }.getOrNull() ?: return
        jsMap.values.forEach { value ->
            if (!value.isAbsUrl()) return@forEach
            val fileName = MD5Utils.md5Encode(value)
            if (aCache.getAsString(fileName) != null) return@forEach
            prefetchScope.launch {
                runCatching {
                    okHttpClient.newCallStrResponse {
                        url(value)
                    }.body
                }.onSuccess { body ->
                    if (!body.isNullOrBlank()) {
                        aCache.put(fileName, body)
                    }
                }.onFailure { e ->
                    AppLog.put("预下载 jsLib 失败: $value", e)
                }
            }
        }
    }

    /**
     * 让 jsLib 相关缓存失效：书源被编辑或删除时调用。
     *
     * 磁盘缓存只针对 legacy 映射里的 URL；内存作用域按 MD5 后缀整体清理。
     */
    fun remove(jsLib: String?) {
        if (jsLib.isNullOrBlank()) {
            return
        }
        if (JsLibraryBundle.parse(jsLib) == null && jsLib.isJsonObject()) {
            val jsMap: Map<String, String> = GSON.fromJson(
                jsLib,
                TypeToken.getParameterized(
                    Map::class.java,
                    String::class.java,
                    String::class.java
                ).type
            )
            jsMap.values.forEach { value ->
                if (value.isAbsUrl()) {
                    val fileName = MD5Utils.md5Encode(value)
                    aCache.remove(fileName)
                }
            }
        }
        val jsLibHash = MD5Utils.md5Encode(jsLib)
        scopeMap.snapshot().keys.forEach { key ->
            if (key.endsWith(":$jsLibHash")) {
                scopeMap.remove(key)
            }
        }
    }

    private fun compiledCryptoJs(): CompiledScript? {
        cryptoJsScript?.let { return it }
        synchronized(cryptoCompileLock) {
            cryptoJsScript?.let { return it }
            val script = runCatching {
                appCtx.assets.open(CRYPTO_JS_ASSET).bufferedReader().use { it.readText() }
            }.getOrNull() ?: return null
            return RhinoScriptEngine.compile(script).also { cryptoJsScript = it }
        }
    }

    /** legacy 映射里的 URL 片段：先取磁盘缓存，未命中同步下载并回写 */
    private fun loadLegacyUrlFragment(url: String): String? {
        val fileName = MD5Utils.md5Encode(url)
        aCache.getAsString(fileName)?.let { return it }
        val js = runBlocking {
            okHttpClient.newCallStrResponse {
                url(url)
            }.body
        }
        if (js != null) {
            aCache.put(fileName, js)
        } else {
            throw NoStackTraceException("下载jsLib-${url}失败")
        }
        return js
    }

    private fun evalLegacyUrlFragment(
        url: String,
        scope: ScriptBindings,
        coroutineContext: CoroutineContext?
    ) {
        loadLegacyUrlFragment(url)?.let {
            RhinoScriptEngine.eval(it, scope, coroutineContext)
        }
    }

    private fun scopeKey(
        jsLib: String,
        bookSourceClassPolicy: Boolean,
        scopeNamespace: String?
    ): String {
        val policyPrefix = if (bookSourceClassPolicy) {
            require(!scopeNamespace.isNullOrBlank()) {
                "BookSource shared JS scope requires a source namespace"
            }
            "bookSource:$scopeNamespace"
        } else {
            "default"
        }
        return "$policyPrefix:${MD5Utils.md5Encode(jsLib)}"
    }

    private const val SECURE_RANDOM_PATCH = """
        CryptoJS.lib.WordArray.random = (function(nextInt) {
            return function(nBytes) {
                var words = [];
                for (var i = 0; i < nBytes; i += 4) {
                    words.push(nextInt());
                }
                return CryptoJS.lib.WordArray.create(words, nBytes);
            };
        })(__legadoSecureRandomInt);
    """
}
