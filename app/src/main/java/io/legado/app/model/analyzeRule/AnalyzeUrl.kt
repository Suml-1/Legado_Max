package io.legado.app.model.analyzeRule

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import androidx.annotation.Keep
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.MediaSource
import cn.hutool.core.codec.PercentCodec
import cn.hutool.core.net.RFC3986
import cn.hutool.core.util.HexUtil
import com.bumptech.glide.load.model.GlideUrl
import com.script.buildScriptBindings
import com.script.rhino.RhinoScriptEngine
import com.script.rhino.runScriptWithContext
import io.legado.app.constant.AppConst.UA_NAME
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.CacheManager
import io.legado.app.data.repository.debug.FlowLogRecorder
import io.legado.app.help.ConcurrentRateLimiter
import io.legado.app.help.JsExtensions
import io.legado.app.help.config.AppConfig
import io.legado.app.help.exoplayer.ExoPlayerHelper
import io.legado.app.help.glide.GlideHeaders
import io.legado.app.help.http.BackstageWebView
import io.legado.app.help.http.CookieManager
import io.legado.app.help.http.CookieManager.mergeCookies
import io.legado.app.help.http.CookieStore
import io.legado.app.help.http.RequestMethod
import io.legado.app.help.http.StrResponse
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.get
import io.legado.app.help.http.getProxyClient
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.postForm
import io.legado.app.help.http.postJson
import io.legado.app.help.http.postMultipart
import io.legado.app.help.source.getShareScope
import io.legado.app.model.Debug
import io.legado.app.utils.EncoderUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.GSONStrict
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.get
import io.legado.app.utils.isJson
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.isXml
import io.legado.app.utils.parseIpsFromString
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Dns
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.max

/**
 * Created by GKF on 2018/1/24.
 * 搜索URL规则解析
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@Keep
@SuppressLint("DefaultLocale")
class AnalyzeUrl(
    private val mUrl: String,
    private val key: String? = null,
    private val page: Int? = null,
    private val speakText: String? = null,
    private val speakSpeed: Int? = null,
    private var baseUrl: String = "",
    private val source: BaseSource? = null,
    private val ruleData: RuleDataInterface? = null,
    private val chapter: BookChapter? = null,
    private val readTimeout: Long? = null,
    private val callTimeout: Long? = null,
    private var coroutineContext: CoroutineContext = EmptyCoroutineContext,
    headerMapF: Map<String, String>? = null,
    hasLoginHeader: Boolean = true,
    private val infoMap: MutableMap<String, String>? = null
) : JsExtensions {
    constructor(mUrl: String) : this(mUrl, null)

    var ruleUrl = ""
        private set
    var url: String = ""
        private set
    var type: String? = null
        private set
    val headerMap = LinkedHashMap<String, String>()
    private var body: String? = null
    var urlNoQuery: String = ""
        private set
    private var encodedForm: String? = null
    private var encodedQuery: String? = null
    private var charset: String? = null
    private var method = RequestMethod.GET
    private var proxy: String? = null
    private var retry: Int = 0
    private var useWebView: Boolean = false
    private var webJs: String? = null
    private var bodyJs: String? = null
    private var dnsIp: String? = null
    private val enabledCookieJar = source?.enabledCookieJar == true
    private val domain: String
    private var webViewDelayTime: Long = 0
    private val concurrentRateLimiter = ConcurrentRateLimiter(source)

    // 服务器ID
    var serverID: Long? = null
        private set

    /**
     * 本地调试模式：为 true 时 [getStrResponseAwait] 不发起流水线网络请求，
     * 规则结果是内容则直接返回，是网址则打印请求摘要后终止链路。
     * 仅源调试会话置位，规则内部 JS 自建的 AnalyzeUrl 不受影响。
     */
    var localDebug = false

    init {
        coroutineContext = coroutineContext.minusKey(ContinuationInterceptor)
        val urlMatcher = paramPattern.matcher(baseUrl)
        if (urlMatcher.find()) baseUrl = baseUrl.substring(0, urlMatcher.start())
        (headerMapF ?: runScriptWithContext(coroutineContext) {
            source?.getHeaderMap(hasLoginHeader)
        })?.let {
            headerMap.putAll(it)
            if (it.containsKey("proxy")) {
                proxy = it["proxy"]
                headerMap.remove("proxy")
            }
        }
        initUrl()
        domain = NetworkUtils.getSubDomain(source?.getKey() ?: url)
    }

    /**
     * 处理url
     */
    fun initUrl() {
        ruleUrl = mUrl
        //执行@js,<js></js>
        analyzeJs()
        //替换参数
        replaceKeyPageJs()
        //处理URL
        analyzeUrl()
    }

    /**
     * 执行@js,<js></js>
     */
    private fun analyzeJs() {
        var start = 0
        val jsMatcher = AppPattern.JS_PATTERN.matcher(ruleUrl)
        var result = ruleUrl
        while (jsMatcher.find()) {
            if (jsMatcher.start() > start) {
                ruleUrl.substring(start, jsMatcher.start()).trim().let {
                    if (it.isNotEmpty()) {
                        result = it.replace("@result", result)
                    }
                }
            }
            result = evalJS(jsMatcher.group(2) ?: jsMatcher.group(1), result).toString()
            start = jsMatcher.end()
        }
        if (ruleUrl.length > start) {
            ruleUrl.substring(start).trim().let {
                if (it.isNotEmpty()) {
                    result = it.replace("@result", result)
                }
            }
        }
        ruleUrl = result
    }

    /**
     * 替换关键字,页数,JS
     */
    private fun replaceKeyPageJs() { //先替换内嵌规则再替换页数规则，避免内嵌规则中存在大于小于号时，规则被切错
        //js
        if (ruleUrl.contains("{{") && ruleUrl.contains("}}")) {
            val analyze = RuleAnalyzer(ruleUrl) //创建解析
            //替换所有内嵌{{js}}
            val url = analyze.innerRule("{{", "}}") {
                val jsEval = evalJS(it) ?: ""
                when (jsEval) {
                    is String -> jsEval
                    is Double if jsEval % 1.0 == 0.0 -> String.format("%.0f", jsEval)
                    else -> jsEval.toString()
                }
            }
            if (url.isNotEmpty()) ruleUrl = url
        }
        //page
        page?.let {
            val matcher = pagePattern.matcher(ruleUrl)
            while (matcher.find()) {
                val pages = matcher.group(1)!!.split(",")
                ruleUrl = if (page < pages.size) { //pages[pages.size - 1]等同于pages.last()
                    ruleUrl.replace(matcher.group(), pages[page - 1].trim { it <= ' ' })
                } else {
                    ruleUrl.replace(matcher.group(), pages.last().trim { it <= ' ' })
                }
            }
        }
    }

    /**
     * 解析Url
     */
    private fun analyzeUrl() {
        //replaceKeyPageJs已经替换掉额外内容，此处url是基础形式，可以直接切首个‘,’之前字符串。
        val urlMatcher = paramPattern.matcher(ruleUrl)
        val urlNoOption =
            if (urlMatcher.find()) ruleUrl.substring(0, urlMatcher.start()) else ruleUrl
        url = NetworkUtils.getAbsoluteURL(baseUrl, urlNoOption)
        NetworkUtils.getBaseUrl(url)?.let {
            baseUrl = it
        }
        if (urlNoOption.length != ruleUrl.length) {
            val urlOptionStr = ruleUrl.substring(urlMatcher.end())
            var urlOption = GSONStrict.fromJsonObject<UrlOption>(urlOptionStr).getOrNull()
            if (urlOption == null) {
                urlOption = GSON.fromJsonObject<UrlOption>(urlOptionStr).getOrNull()
                if (urlOption != null) {
                    log("链接参数 JSON 格式不规范，请改为规范格式")
                }
            }
            urlOption?.let { option ->
                option.getMethod()?.let {
                    method = when (it.uppercase()) {
                        "POST" -> RequestMethod.POST
                        "HEAD" -> RequestMethod.HEAD
                        else -> RequestMethod.GET
                    }
                }
                option.getHeaderMap()?.forEach { entry ->
                    headerMap[entry.key.toString()] = entry.value.toString()
                }
                option.getBody()?.let {
                    body = it
                }
                type = option.getType()
                charset = option.getCharset()
                retry = option.getRetry()
                useWebView = option.useWebView()
                webJs = option.getWebJs()
                bodyJs = option.getBodyJs()
                dnsIp = option.getDnsIp()
                option.getJs()?.let { jsStr ->
                    evalJS(jsStr, url)?.toString()?.let {
                        url = it
                    }
                }
                serverID = option.getServerID()
                webViewDelayTime = max(0, option.getWebViewDelayTime() ?: 0)
            }
        }
        urlNoQuery = url
        when (method) {
            RequestMethod.POST -> body?.let {
                if (!it.isJson() && !it.isXml() && headerMap["Content-Type"].isNullOrEmpty()) {
                    analyzeFields(it)
                }
            }

            else -> {
                val pos = url.indexOf('?')
                if (pos != -1) {
                    analyzeQuery(url.substring(pos + 1))
                    urlNoQuery = url.substring(0, pos)
                }
            }
        }
    }

    /**
     * 解析QueryMap <key>=<value>
     * name=
     * name=name
     * name=<BASE64> eg name=bmFtZQ==
     */
    private fun analyzeFields(fieldsTxt: String) {
        encodedForm = encodeParams(fieldsTxt, charset, false)
    }

    private fun analyzeQuery(query: String) {
        encodedQuery = encodeParams(query, charset, true)
    }

    private fun encodeParams(params: String, charset: String?, isQuery: Boolean): String {
        val checkEncoded = charset.isNullOrEmpty()
        val charset = when {
            charset.isNullOrEmpty() -> Charsets.UTF_8
            charset == "escape" -> null
            else -> charset(charset)
        }
        if (isQuery && charset != null) {
            if (NetworkUtils.encodedQuery(params)) {
                return params
            }
            return queryEncoder.encode(params, charset)
        }
        val len = params.length
        val sb = StringBuilder()
        var pos = 0
        while (pos <= len) {
            if (sb.isNotEmpty()) {
                sb.append("&")
            }
            var ampOffset = params.indexOf("&", pos)
            if (ampOffset == -1) {
                ampOffset = len
            }
            val eqOffset = params.indexOf("=", pos)
            val key: String
            val value: String?
            if (eqOffset == -1 || eqOffset > ampOffset) {
                key = params.substring(pos, ampOffset)
                value = null
            } else {
                key = params.substring(pos, eqOffset)
                value = params.substring(eqOffset + 1, ampOffset)
            }
            sb.appendEncoded(key, checkEncoded, charset)
            if (value != null) {
                sb.append("=")
                sb.appendEncoded(value, checkEncoded, charset)
            }
            pos = ampOffset + 1
        }
        return sb.toString()
    }

    private fun StringBuilder.appendEncoded(
        value: String,
        checkEncoded: Boolean,
        charset: Charset?
    ) {
        if (checkEncoded && NetworkUtils.encodedForm(value)) {
            append(value)
        } else if (charset == null) {
            append(EncoderUtils.escape(value))
        } else {
            append(URLEncoder.encode(value, charset))
        }
    }


    /**
     * 执行JS
     */
    fun evalJS(jsStr: String, result: Any? = null): Any? {
        val bindings = buildScriptBindings { bindings ->
            bindings["java"] = this
            bindings["baseUrl"] = baseUrl
            bindings["cookie"] = CookieStore
            bindings["cache"] = CacheManager
            bindings["page"] = page
            bindings["key"] = key
            bindings["speakText"] = speakText
            bindings["speakSpeed"] = speakSpeed
            bindings["book"] = ruleData as? Book
            bindings["source"] = source
            bindings["result"] = result
            bindings["infoMap"] = infoMap
        }
        val sharedScope = source?.getShareScope(coroutineContext)
        val scope = if (sharedScope == null) {
            RhinoScriptEngine.getRuntimeScope(bindings)
        } else {
            bindings.apply {
                chainTo(sharedScope)
            }
        }
        return RhinoScriptEngine.eval(jsStr, scope, coroutineContext)
    }

    fun put(key: String, value: String): String {
        if (key == "bookName" || key == "title") {
            Debug.log("≡变量 $key 在特定情况下会被覆盖，建议使用其他键名")
        }
        chapter?.putVariable(key, value)
            ?: ruleData?.putVariable(key, value)
        return value
    }

    fun get(key: String): String {
        when (key) {
            "bookName" -> (ruleData as? Book)?.let {
                return it.name
            }

            "title" -> chapter?.let {
                return it.title
            }
        }
        return chapter?.getVariable(key)?.takeIf { it.isNotEmpty() }
            ?: ruleData?.getVariable(key)?.takeIf { it.isNotEmpty() }
            ?: ""
    }

    /**
     * 访问网站,返回StrResponse
     */
    suspend fun getStrResponseAwait(
        jsStr: String? = null,
        sourceRegex: String? = null,
        useWebView: Boolean = true,
        isTest: Boolean = false,
        skipRateLimit: Boolean = false
    ): StrResponse {
        // Data URL 短路：解码后返回 hex 编码的字符串，不走 OkHttp / WebView。
        // 必须返回 hex 编码以与 type 路径（HexUtil.encodeHexStr(getByteArrayAwait())）保持一致，
        // 否则 JS 侧 hexDecodeToString() 会因遇到非 hex 字符而抛出异常。
        getDataUrlHexContent()?.let { return it }
        if (localDebug) {
            return localDebugStrResponse()
        }
        if (type != null) {
            return StrResponse(url, HexUtil.encodeHexStr(getByteArrayAwait()))
        }
        if (skipRateLimit) {
            return executeStrRequest(jsStr, sourceRegex, useWebView, isTest)
        }
        concurrentRateLimiter.withLimit {
            return executeStrRequest(jsStr, sourceRegex, useWebView, isTest)
        }
    }

    /**
     * 解析 Data URL 并返回 hex 编码的 StrResponse。
     *
     * 与 [getByteArrayAwait] + [HexUtil.encodeHexStr] 路径返回格式一致，
     * 确保 JS 侧 `hexDecodeToString()` 能正常解码。
     *
     * 支持两种格式：
     * - `data:text/html;charset=utf-8;base64,<base64>` — base64 编码
     * - `data:text/html;charset=utf-8,<raw>` — 非 base64 的原始文本（percent-decoded）
     *
     * @return hex 编码的 StrResponse；如果当前 URL 不是 data URL 或解析失败则返回 null
     */
    private fun getDataUrlHexContent(): StrResponse? {
        if (!urlNoQuery.startsWith("data:")) return null
        val dataUriRegex = AppPattern.dataUriRegex
        // 获取 data URL 声明的 charset，优先取选项中的 charset
        val charsetName = charset ?: extractCharsetFromDataUrl(urlNoQuery) ?: "UTF-8"
        val base64Match = dataUriRegex.find(urlNoQuery)
        if (base64Match != null) {
            val base64Data = base64Match.groupValues[1]
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            return StrResponse(url, HexUtil.encodeHexStr(bytes))
        }
        // 非 base64 的 data URL：data:[<mediatype>][;charset=xxx],<data>
        val commaIdx = urlNoQuery.indexOf(',')
        if (commaIdx != -1) {
            val raw = urlNoQuery.substring(commaIdx + 1)
            // data URL 非基础编码部分使用 percent-encoding
            val decoded = java.net.URLDecoder.decode(raw, charsetName)
            return StrResponse(url, HexUtil.encodeHexStr(decoded.toByteArray(Charset.forName(charsetName))))
        }
        // 格式不合法的 data URL，返回空 body 避免崩溃
        return StrResponse(url, HexUtil.encodeHexStr(ByteArray(0)))
    }

    /**
     * 从 data URL 的 media type 部分提取 charset 参数。
     * 例: `data:text/html;charset=utf-8;base64,...` → `utf-8`
     */
    private fun extractCharsetFromDataUrl(dataUrl: String): String? {
        val commaIdx = dataUrl.indexOf(',')
        val header = if (commaIdx != -1) dataUrl.substring(0, commaIdx) else dataUrl
        val charsetIdx = header.indexOf("charset=", ignoreCase = true)
        if (charsetIdx != -1) {
            val start = charsetIdx + "charset=".length
            val end = header.indexOf(';', start)
            val endIdx = if (end != -1) end else header.length
            return header.substring(start, endIdx).takeIf { it.isNotBlank() }
        }
        return null
    }

    /**
     * 本地调试：URL 规则在构造时已在本地执行完毕，这里不再发起流水线网络请求。
     * 规则结果是内容则直接作为本阶段网页内容返回；是网址则打印请求摘要后以空内容继续跑解析规则
     * （纯 JS 规则不依赖网页内容，照常执行）；为空则终止链路。
     * 规则内部发起的取内容请求（如 JS 里的 java.ajax）走 JsExtensions 自建的 AnalyzeUrl，不经过本分支。
     */
    private fun localDebugStrResponse(): StrResponse {
        val sourceKey = source?.getKey()
        return when (judgeLocalDebugResult(ruleUrl)) {
            LocalDebugResult.Content -> {
                Debug.log(sourceKey, "≡本地调试：规则返回内容，直接解析")
                StrResponse(sourceKey ?: url, ruleUrl)
            }

            LocalDebugResult.Empty -> {
                Debug.log(sourceKey, "≡本地调试：规则结果为空")
                throw NoStackTraceException("本地调试：规则结果为空，调试结束")
            }

            LocalDebugResult.Request -> {
                Debug.log(sourceKey, "≡本地调试：规则算出请求 $method $url")
                headerMap.forEach { (key, value) ->
                    Debug.log(sourceKey, "├$key: $value")
                }
                body?.let { Debug.log(sourceKey, "├body: $it") }
                // 不联网，以空内容继续跑解析规则：纯 JS 规则（不使用网页内容）照常执行打印，
                // 依赖网页内容的规则解析结果为空——这是"不联网"的如实语义
                Debug.log(sourceKey, "≡本地调试：未取网页内容，以空内容继续解析")
                StrResponse(sourceKey ?: url, "")
            }
        }
    }

    fun isSimpleGetRequest(): Boolean {
        return method == RequestMethod.GET &&
            encodedForm.isNullOrEmpty() &&
            body.isNullOrEmpty()
    }

    private suspend fun executeStrRequest(
        jsStr: String? = null,
        sourceRegex: String? = null,
        useWebView: Boolean = true,
        isTest: Boolean = false
    ): StrResponse {
        setCookie()
        val startTime = System.currentTimeMillis()
        
        FlowLogRecorder.logNetwork(
            source = source,
            message = "发起网络请求",
            url = url,
            method = method.name,
            // 传递请求头和 Cookie 用于调试日志展示，Cookie 单独提取便于独立显示
            requestHeaders = headerMap,
            cookies = headerMap["Cookie"]
        )
        
        val strResponse: StrResponse
        try {
            if (this.useWebView && useWebView) {
                strResponse = when (method) {
                    RequestMethod.POST -> {
                        val res = getClient().newCallStrResponse(retry) {
                            addHeaders(headerMap)
                            url(urlNoQuery)
                            if (!encodedForm.isNullOrEmpty() || body.isNullOrBlank()) {
                                postForm(encodedForm ?: "")
                            } else {
                                postJson(body)
                            }
                        }
                        BackstageWebView(
                            url = res.url,
                            html = res.body,
                            tag = source?.getKey(),
                            javaScript = webJs ?: jsStr,
                            sourceRegex = sourceRegex,
                            headerMap = headerMap,
                            delayTime = webViewDelayTime
                        ).getStrResponse()
                    }

                    else -> BackstageWebView(
                        url = url,
                        tag = source?.getKey(),
                        javaScript = webJs ?: jsStr,
                        sourceRegex = sourceRegex,
                        headerMap = headerMap,
                        delayTime = webViewDelayTime
                    ).getStrResponse()
                }
            } else {
                strResponse = getClient().newCallStrResponse(retry) {
                    addHeaders(headerMap)
                    when (method) {
                        RequestMethod.POST -> {
                            url(urlNoQuery)
                            val contentType = headerMap["Content-Type"]
                            val body = body
                            if (!encodedForm.isNullOrEmpty() || body.isNullOrBlank()) {
                                postForm(encodedForm ?: "")
                            } else if (!contentType.isNullOrBlank()) {
                                val requestBody = body.toRequestBody(contentType.toMediaType())
                                post(requestBody)
                            } else {
                                postJson(body)
                            }
                        }

                        RequestMethod.HEAD -> {
                            get(urlNoQuery, encodedQuery)
                            head()
                        }

                        else -> get(urlNoQuery, encodedQuery)
                    }
                }.let {
                    val isXml = it.raw.body.contentType()?.toString()
                        ?.matches(AppPattern.xmlContentTypeRegex) == true
                    if (isXml && it.body?.trim()?.startsWith("<?xml", true) == false) {
                        StrResponse(it.raw, "<?xml version=\"1.0\"?>" + it.body)
                    } else if (bodyJs != null) {
                        val body = evalJS(bodyJs!!, it.body).toString()
                        StrResponse(it.raw, body)
                    } else it
                }
            }
            val connectionTime = System.currentTimeMillis() - startTime
            strResponse.putCallTime(connectionTime.toInt())
            
            FlowLogRecorder.logNetwork(
                source = source,
                message = "网络请求成功",
                url = url,
                method = method.name,
                statusCode = strResponse.code(),
                duration = connectionTime
            )
            
            return strResponse
        } catch (e: Exception) {
            FlowLogRecorder.logNetwork(
                source = source,
                message = "网络请求失败: ${e.message}",
                url = url,
                method = method.name,
                error = e
            )
            
            if (!isTest) {
                throw e
            }
            val errorCode = when (e) {
                is java.net.SocketTimeoutException -> -2  // 超时错误
                is java.net.UnknownHostException -> -3   // 未找到域名
                is java.net.ConnectException -> -4       // 连接被拒绝
                is java.net.SocketException -> -5        // Socket错误（包括连接重置）
                is javax.net.ssl.SSLException -> -6      // SSL证书或握手错误
                is java.io.InterruptedIOException -> {
                    if (e.message?.contains("timeout") == true) {
                        -1  // 超过设定时间
                    } else -7
                }
                else -> -7  // 其它错误
            }
            return StrResponse(url, e.message).apply {
                putCallTime(errorCode)
            }
        }
    }

    @JvmOverloads
    fun getStrResponse(
        jsStr: String? = null,
        sourceRegex: String? = null,
        useWebView: Boolean = true,
    ): StrResponse {
        return runBlocking(coroutineContext) {
            getStrResponseAwait(jsStr, sourceRegex, useWebView)
        }
    }

    /**
     * 访问网站,返回Response
     */
    suspend fun getResponseAwait(): Response {
        concurrentRateLimiter.withLimit {
            setCookie()
            val response = getClient().newCallResponse(retry) {
                addHeaders(headerMap)
                when (method) {
                    RequestMethod.POST -> {
                        url(urlNoQuery)
                        val contentType = headerMap["Content-Type"]
                        val body = body
                        if (!encodedForm.isNullOrEmpty() || body.isNullOrBlank()) {
                            postForm(encodedForm ?: "")
                        } else if (!contentType.isNullOrBlank()) {
                            val requestBody = body.toRequestBody(contentType.toMediaType())
                            post(requestBody)
                        } else {
                            postJson(body)
                        }
                    }

                    else -> get(urlNoQuery, encodedQuery)
                }
            }
            return response
        }
    }

    /**
     * 返回一个errResponse
     */
    fun getErrResponse(e: Throwable): Response = Response.Builder()
        .request(Request.Builder().url(url).build())
        .protocol(okhttp3.Protocol.HTTP_1_1)
        .code(500)
        .message(e.message ?: "Error Response")
        .body(e.stackTraceStr.toResponseBody(null))
        .build()

    /**
     * 返回一个errStrResponse
     */
    fun getErrStrResponse(e: Throwable): StrResponse =
        StrResponse(getErrResponse(e), e.stackTraceStr)

    private fun getClient(): OkHttpClient {
        val client = getProxyClient(proxy)
        if (readTimeout == null && callTimeout == null && dnsIp == null) {
            return client
        }
        if (AppConfig.isCronet && dnsIp != null) {
            customIp[urlNoQuery] = dnsIp!!
        }
        return client.newBuilder().run {
            if (readTimeout != null) {
                readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                callTimeout(max(60 * 1000L, readTimeout * 2), TimeUnit.MILLISECONDS)
            }
            if (callTimeout != null) {
                callTimeout(callTimeout, TimeUnit.MILLISECONDS)
            }
            if (dnsIp != null) {
                val inetAddress = dnsIp!!.parseIpsFromString()
                dns { hostname ->
                    inetAddress ?: Dns.SYSTEM.lookup(hostname)
                }
            }
            build()
        }
    }

    private fun extractHostFromUrl(url: String): String? {
        return AppPattern.domainRegex.find(url)?.groupValues?.getOrNull(1)
    }


    fun getResponse(): Response {
        return runBlocking(coroutineContext) {
            getResponseAwait()
        }
    }

    private fun getByteArrayIfDataUri(): ByteArray? {
        if (!urlNoQuery.startsWith("data:")) {
            return null
        }
        val dataUriFindResult = AppPattern.dataUriRegex.find(urlNoQuery)
        if (dataUriFindResult != null) {
            val dataUriBase64 = dataUriFindResult.groupValues[1]
            val byteArray = Base64.decode(dataUriBase64, Base64.DEFAULT)
            return byteArray
        }
        return null
    }

    /**
     * 访问网站,返回ByteArray
     */
    suspend fun getByteArrayAwait(): ByteArray {
        getByteArrayIfDataUri()?.let {
            return it
        }
        return getResponseAwait().body.bytes()
    }

    fun getByteArray(): ByteArray {
        return runBlocking(coroutineContext) {
            getByteArrayAwait()
        }
    }

    /**
     * 访问网站,返回InputStream
     */
    suspend fun getInputStreamAwait(): InputStream {
        getByteArrayIfDataUri()?.let {
            return ByteArrayInputStream(it)
        }
        return getResponseAwait().body.byteStream()
    }

    fun getInputStream(): InputStream {
        return runBlocking(coroutineContext) {
            getInputStreamAwait()
        }
    }

    /**
     * 本地调试下 URL 规则计算结果的类别
     */
    enum class LocalDebugResult {
        /** 结果为空 */
        Empty,

        /** 结果是一个待请求的网址（含 data:、相对地址） */
        Request,

        /** 结果是网页/接口内容，可直接交给解析规则 */
        Content
    }

    /**
     * 上传文件
     */
    suspend fun upload(fileName: String, file: Any, contentType: String): StrResponse {
        return getProxyClient(proxy).newCallStrResponse(retry) {
            url(urlNoQuery)
            val bodyMap = GSON.fromJsonObject<HashMap<String, Any>>(body).getOrNull()!!
            bodyMap.forEach { entry ->
                if (entry.value.toString() == "fileRequest") {
                    bodyMap[entry.key] = mapOf(
                        Pair("fileName", fileName),
                        Pair("file", file),
                        Pair("contentType", contentType)
                    )
                }
            }
            postMultipart(type, bodyMap)
        }
    }

    /**
     * 设置cookie 优先级
     * urlOption临时cookie > 数据库cookie
     */
    private fun setCookie() {
        val cookie = kotlin.run {
            /* 每次调用getXX cookieJar已经保存过了
            if (enabledCookieJar) {
                val key = "${domain}_cookieJar"
                CacheManager.getFromMemory(key)?.let {
                    return@run it
                }
            }
            */
            CookieStore.getCookie(domain)
        }
        if (cookie.isNotEmpty()) {
            mergeCookies(cookie, headerMap["Cookie"])?.let {
                headerMap.put("Cookie", it)
            }
        }
        if (enabledCookieJar) {
            headerMap[CookieManager.cookieJarHeader] = "1"
        } else {
            headerMap.remove(CookieManager.cookieJarHeader)
        }
    }

    /**
     * 保存cookieJar中的cookie在访问结束时就保存,不等到下次访问
     */
    private fun saveCookie() {
        //书源启用保存cookie时 添加内存中的cookie到数据库
        if (enabledCookieJar) {
            val key = "${domain}_cookieJar"
            CacheManager.getFromMemory(key)?.let {
                if (it is String) {
                    CookieStore.replaceCookie(domain, it)
                    CacheManager.deleteMemory(key)
                }
            }
        }
    }

    /**
     *获取处理过阅读定义的urlOption和cookie的GlideUrl
     */
    fun getGlideUrl(): GlideUrl {
        setCookie()
        return GlideUrl(url, GlideHeaders(headerMap))
    }

    fun getUserAgent(): String {
        return headerMap.get(UA_NAME, true) ?: AppConfig.userAgent
    }

    fun isPost(): Boolean {
        return method == RequestMethod.POST
    }

    override fun getSource(): BaseSource? {
        return source
    }

    override fun getTag(): String? {
        return source?.getTag()
    }

    companion object {
        val paramPattern: Pattern = Pattern.compile("\\s*,\\s*(?=\\{)")
        private val pagePattern = Pattern.compile("<(.*?)>")
        private val queryEncoder =
            RFC3986.UNRESERVED.orNew(PercentCodec.of("!$%&()*+,/:;=?@[\\]^`{|}"))
        val customIp by lazy { ConcurrentHashMap<String, String>() }

        /**
         * 本地调试结果判定（纯函数）：先按 [paramPattern] 切掉尾部 ,{...} 选项段
         * （选项段只对真实请求有意义），剩余部分按前缀启发式判定为请求还是内容。
         */
        fun judgeLocalDebugResult(ruleUrl: String): LocalDebugResult {
            val matcher = paramPattern.matcher(ruleUrl)
            val noOption = if (matcher.find()) ruleUrl.substring(0, matcher.start()) else ruleUrl
            val trimmed = noOption.trim()
            if (trimmed.isEmpty()) {
                return LocalDebugResult.Empty
            }
            return if (trimmed.startsWith("http://", true) ||
                trimmed.startsWith("https://", true) ||
                trimmed.startsWith("data:", true) ||
                trimmed.startsWith("/")
            ) {
                LocalDebugResult.Request
            } else {
                LocalDebugResult.Content
            }
        }

        fun AnalyzeUrl.getMediaItem(): MediaItem {
            setCookie()
            return ExoPlayerHelper.createMediaItem(url, headerMap)
        }

        /**
         * 音频播放用的 MediaSource：与 [getMediaItem] 一样先做 cookie 处理，
         * 区别是会优先读该书的音频离线缓存目录（已缓存章节离线可播）
         * @param writable false 时只读缓存不写，"边播放边缓存"关闭
         */
        fun AnalyzeUrl.getMediaSource(
            context: Context,
            book: Book?,
            writable: Boolean
        ): MediaSource {
            setCookie()
            return ExoPlayerHelper.getMediaSource(context, url, book, headerMap, writable)
        }

    }

    @Keep
    data class UrlOption(
        private var method: String? = null,
        private var charset: String? = null,
        private var headers: Any? = null,
        private var body: Any? = null,
        /**
         * 源Url
         **/
        private var origin: String? = null,
        /**
         * 重试次数
         **/
        private var retry: Int? = null,
        /**
         * 类型
         **/
        private var type: String? = null,
        /**
         * 是否使用webView
         **/
        private var webView: Any? = null,
        /**
         * webView中执行的js
         **/
        private var webJs: String? = null,
        /**
         * 自定义的域名ip
         **/
        private var dnsIp: String? = null,
        /**
         * 解析完url参数时执行的js
         * 执行结果会赋值给url
         */
        private var js: String? = null,
        /**
         * 得到访问结果后执行的js,对结果进行二次处理
         * 执行结果返回为body
         */
        private var bodyJs: String? = null,
        /**
         * 服务器id
         */
        private var serverID: Long? = null,
        /**
         * webview等待页面加载完毕的延迟时间（毫秒）
         */
        private var webViewDelayTime: Long? = null,
    ) {
        fun setMethod(value: String?) {
            method = if (value.isNullOrBlank()) null else value
        }

        fun getMethod(): String? {
            return method
        }

        fun setCharset(value: String?) {
            charset = if (value.isNullOrBlank()) null else value
        }

        fun getCharset(): String? {
            return charset
        }

        fun setOrigin(value: String?) {
            origin = if (value.isNullOrBlank()) null else value
        }

        fun getOrigin(): String? {
            return origin
        }

        fun setRetry(value: String?) {
            retry = if (value.isNullOrEmpty()) null else value.toIntOrNull()
        }

        fun getRetry(): Int {
            return retry ?: 0
        }

        fun setType(value: String?) {
            type = if (value.isNullOrBlank()) null else value
        }

        fun getType(): String? {
            return type
        }

        fun useWebView(): Boolean {
            return when (webView) {
                null, "", false, "false" -> false
                else -> true
            }
        }

        fun useWebView(boolean: Boolean) {
            webView = if (boolean) true else null
        }

        fun setHeaders(value: String?) {
            headers = if (value.isNullOrBlank()) {
                null
            } else {
                GSON.fromJsonObject<Map<String, Any>>(value).getOrNull()
            }
        }

        fun getHeaderMap(): Map<*, *>? {
            return when (val value = headers) {
                is Map<*, *> -> value
                is String -> GSON.fromJsonObject<Map<String, Any>>(value).getOrNull()
                else -> null
            }
        }

        fun setBody(value: String?) {
            body = when {
                value.isNullOrBlank() -> null
                value.isJsonObject() -> GSON.fromJsonObject<Map<String, Any>>(value).getOrNull()
                value.isJsonArray() -> GSON.fromJsonArray<Map<String, Any>>(value).getOrNull()
                else -> value
            }
        }

        fun getBody(): String? {
            return body?.let {
                it as? String ?: GSON.toJson(it)
            }
        }

        fun setWebJs(value: String?) {
            webJs = if (value.isNullOrBlank()) null else value
        }

        fun getWebJs(): String? {
            return webJs
        }
        fun setDnsIp(value: String?) {
            dnsIp = if (value.isNullOrBlank()) null else value
        }

        fun getDnsIp(): String? {
            return dnsIp
        }

        fun setJs(value: String?) {
            js = if (value.isNullOrBlank()) null else value
        }

        fun getJs(): String? {
            return js
        }

        fun setBodyJs(value: String?) {
            bodyJs = if (value.isNullOrBlank()) null else value
        }

        fun getBodyJs(): String? {
            return bodyJs
        }

        fun setServerID(value: String?) {
            serverID = if (value.isNullOrBlank()) null else value.toLong()
        }

        fun getServerID(): Long? {
            return serverID
        }

        fun setWebViewDelayTime(value: String?) {
            webViewDelayTime = if (value.isNullOrBlank()) null else value.toLong()
        }

        fun getWebViewDelayTime(): Long? {
            return webViewDelayTime
        }
    }

    data class ConcurrentRecord(
        /**
         * 开始访问时间
         */
        var time: Long,
        /**
         * 限制次数
         */
        var accessLimit : Int,
        /**
         * 间隔时间
         */
        var interval : Int,
        /**
         * 正在访问的个数
         */
        var frequency: Int
    )

}
