package io.legado.app.model.jsSource

import android.os.SystemClock
import androidx.collection.LruCache
import cn.hutool.core.codec.Base64
import com.google.gson.JsonObject
import com.script.CompiledScript
import com.script.ScriptBindings
import com.script.buildScriptBindings
import com.script.rhino.RhinoContext
import com.script.rhino.RhinoScriptEngine
import io.legado.app.R
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.CacheManager
import io.legado.app.help.JsExtensions
import io.legado.app.help.http.CookieStore
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.source.getShareScope
import io.legado.app.model.Debug
import io.legado.app.model.JsLibraryBundle
import io.legado.app.model.SharedJsScope
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.htmlunit.corejs.javascript.Context
import org.htmlunit.corejs.javascript.Function
import org.htmlunit.corejs.javascript.NativeJSON
import org.htmlunit.corejs.javascript.Scriptable
import org.htmlunit.corejs.javascript.ScriptableObject
import org.htmlunit.corejs.javascript.Undefined
import org.htmlunit.corejs.javascript.Wrapper
import splitties.init.appCtx
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.InflaterInputStream
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 纯 JavaScript 单文件书源执行器。
 *
 * 每次业务调用创建独立局部作用域，复用按书源隔离的 Cookie / 源缓存 / 共享 jsLib /
 * 内置 CryptoJS；声明式书源继续走原有 AnalyzeRule 链，两种书源只在 [io.legado.app.model.webBook.WebBook]
 * 入口分流。
 *
 * 脚本可见的宿主对象刻意只保留字符串进出的窄门面（[postBase64Body]、[gzipUtf8ToBase64] 等），
 * 避免把整个 App 的 Java 对象图暴露给书源作者。
 */
class JsSourceEngine(
    private val source: BookSource,
    coroutineContext: CoroutineContext? = null,
) : JsExtensions {

    // 同步 JS 桥不能再调度回自己正在阻塞的线程池：只保留取消（Job）等非拦截器元素
    private val coroutineContext = coroutineContext?.withoutScriptDispatcher()

    private val libraryBundle by lazy { JsLibraryBundle.parse(source.jsLib) }

    override fun getSource(): BaseSource = source

    override fun getTag(): String = source.getTag()

    /** 只读访问书源 jsLib bundle 里的内联数据；不读文件、不联网。 */
    fun getJsLibResource(name: String): String? = libraryBundle?.resources?.get(name)

    /** 书源自带的（Base64）压缩包解包能力，仅内存内解压，绝不落盘。 */
    fun getArchiveByteArrayContent(base64: String, entryName: String): ByteArray? = MemoryArchiveReader.read(base64, entryName)

    /**
     * 纯 JS 执行沙箱（对齐参考分支的 `java.getQuickJsSandbox()`）。
     *
     * 部分书源会把签名核心 `toString()` 后丢进沙箱运行（依赖 BigInt / 64 位位运算），
     * 这里返回一个不暴露任何宿主对象的隔离执行器，只接受字符串进出。
     */
    fun getQuickJsSandbox(): JsSourceSandbox = JsSourceSandbox(coroutineContext, source.getTag())

    /**
     * 仅单文件 JS 运行时可达。用于协议确实要求原始二进制正文的请求；调用方以 Base64
     * 传入，避免把任意字节先转换成 UTF-8 字符串而损坏内容。
     */
    fun postBase64Body(url: String, bodyBase64: String, headersJson: String?): String {
        val httpUrl = url.toHttpUrl()
        require(httpUrl.isHttps) { appCtx.getString(R.string.js_source_error_https_only) }
        val body = Base64.decode(bodyBase64)
        require(body.isNotEmpty()) { appCtx.getString(R.string.js_source_content_empty) }
        require(body.size <= MAX_BINARY_REQUEST_BYTES) { appCtx.getString(R.string.js_source_error_body_too_large) }
        val headers = headersJson
            ?.let { GSON.fromJsonObject<Map<String, String>>(it).getOrNull() }
            ?: emptyMap()
        val contentType = headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.toMediaTypeOrNull()
        val response = runBlocking(coroutineContext ?: EmptyCoroutineContext) {
            okHttpClient.newCallResponse {
                url(httpUrl)
                addHeaders(headers)
                post(body.toRequestBody(contentType))
            }
        }
        response.use {
            return JsonObject().apply {
                addProperty("code", it.code)
                addProperty("body", it.body.string())
            }.toString()
        }
    }

    /** 仅单文件 JS 运行时可达；把 UTF-8 文本压成可安全跨 Rhino 边界传递的 Base64。 */
    fun gzipUtf8ToBase64(value: String): String {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(value.toByteArray(Charsets.UTF_8)) }
        return Base64.encode(output.toByteArray())
    }

    /**
     * 仅单文件 JS 运行时可达；解开 Base64 中的 gzip/zlib 数据。未压缩输入按 UTF-8 原样返回。
     */
    fun decompressBase64ToUtf8(value: String): String {
        val input = Base64.decode(value)
        val stream = when {
            input.size >= 2 && input[0] == 0x1f.toByte() && input[1] == 0x8b.toByte() ->
                GZIPInputStream(ByteArrayInputStream(input))

            input.size >= 2 && input[0] == 0x78.toByte() ->
                InflaterInputStream(ByteArrayInputStream(input))

            else -> ByteArrayInputStream(input)
        }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun callFunction(name: String, args: List<Pair<String, Any?>>): String? {
        val scope = buildScope(args)
        if (ScriptableObject.getProperty(scope, name) !is Function) {
            throw NoStackTraceException(appCtx.getString(R.string.js_source_error_function_required, name))
        }
        val expression = "$name(${args.joinToString(", ") { it.first }})"
        return normalizeJsResult(compile(expression).eval(scope, coroutineContext), coroutineContext)
    }

    fun callFunctionIfExists(name: String, args: List<Pair<String, Any?>>): String? = callOptionalFunction(name, args).value

    internal fun callOptionalFunction(
        name: String,
        args: List<Pair<String, Any?>>,
    ): OptionalCallResult {
        val scope = buildScope(args)
        if (ScriptableObject.getProperty(scope, name) !is Function) {
            return OptionalCallResult(false, null)
        }
        val expression = "$name(${args.joinToString(", ") { it.first }})"
        return OptionalCallResult(
            exists = true,
            value = normalizeJsResult(
                compile(expression).eval(scope, coroutineContext),
                coroutineContext,
            ),
        )
    }

    internal data class OptionalCallResult(
        val exists: Boolean,
        val value: String?,
    )

    private fun buildScope(args: List<Pair<String, Any?>>): ScriptBindings {
        val profiling = Debug.isLocalDebug(source.getKey())
        val started = if (profiling) SystemClock.elapsedRealtime() else 0L
        val script = source.mainJs?.takeIf { it.isNotBlank() }
            ?: throw NoStackTraceException(appCtx.getString(R.string.js_source_error_main_empty))
        val bindings = buildScriptBindings { values ->
            values["java"] = this
            values["source"] = source
            values["sourceApi"] = source
            values["baseUrl"] = source.getKey()
            values["cookie"] = CookieStore
            values["cache"] = CacheManager
            args.forEach { (key, value) -> values[key] = value }
        }
        // 有 jsLib 时用它当父作用域；否则退化成「只有内置 CryptoJS」的共享作用域
        val sharedScope = source.getShareScope(coroutineContext)
            ?: SharedJsScope.getCryptoScope(
                scopeNamespace = source.getKey(),
                coroutineContext = coroutineContext,
                bookSourceClassPolicy = true,
                bookSourceLabel = source.getTag(),
            )
        val sharedReady = if (profiling) SystemClock.elapsedRealtime() else 0L
        val scope = if (sharedScope == null) {
            RhinoScriptEngine.getRuntimeScope(bindings)
        } else {
            bindings.apply { chainTo(sharedScope) }
        }
        compile(script).eval(scope, coroutineContext)
        if (profiling) {
            Debug.log(
                source.getKey(),
                "JS初始化：共享库 ${sharedReady - started}ms，" +
                    "主脚本 ${SystemClock.elapsedRealtime() - sharedReady}ms",
            )
        }
        return scope
    }

    companion object {

        private const val MAX_BINARY_REQUEST_BYTES = 512 * 1024
        private val scriptCache = LruCache<String, CompiledScript>(64)

        private fun compile(script: String): CompiledScript {
            scriptCache[script]?.let { return it }
            return RhinoScriptEngine.compile(script).also { scriptCache.put(script, it) }
        }

        /**
         * 把 JS 返回值统一成字符串：[String] 原样，对象走 JSON.stringify，
         * `null/undefined` 归一为 null（上层据此判断「没有返回」）。
         */
        fun normalizeJsResult(
            result: Any?,
            coroutineContext: CoroutineContext? = null,
        ): String? {
            var value = result
            if (value is Wrapper) value = value.unwrap()
            return when {
                value == null || value is Undefined -> null
                value is String -> value
                value is CharSequence -> value.toString()
                value is Scriptable -> stringifyScriptable(value, coroutineContext)
                    ?: GSON.toJson(value)

                else -> GSON.toJson(value)
            }
        }

        private fun stringifyScriptable(
            value: Scriptable,
            coroutineContext: CoroutineContext?,
        ): String? {
            val topScope = value.parentScope?.let(ScriptableObject::getTopLevelScope)
                ?: return null
            val context = Context.enter() as RhinoContext
            val previousCoroutineContext = context.coroutineContext
            val previousAllowScriptRun = context.allowScriptRun
            if (coroutineContext != null && coroutineContext[Job] != null) {
                context.coroutineContext = coroutineContext
            }
            context.allowScriptRun = true
            context.recursiveCount++
            try {
                context.checkRecursive()
                val raw = try {
                    NativeJSON.stringify(context, topScope, value, null, null)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw NoStackTraceException(
                        appCtx.getString(R.string.js_source_error_stringify, error.message),
                    )
                }
                return RhinoScriptEngine.unwrapReturnValue(raw) as? String
            } finally {
                context.recursiveCount--
                context.allowScriptRun = previousAllowScriptRun
                context.coroutineContext = previousCoroutineContext
                Context.exit()
            }
        }
    }
}
