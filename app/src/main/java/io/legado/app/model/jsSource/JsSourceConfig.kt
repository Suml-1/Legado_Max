package io.legado.app.model.jsSource

import com.google.gson.JsonObject
import com.script.ScriptBindings
import com.script.buildScriptBindings
import com.script.rhino.RhinoClassShutter
import com.script.rhino.RhinoScriptEngine
import io.legado.app.R
import io.legado.app.constant.BookSourceType
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.SharedJsScope
import io.legado.app.model.login.LoginUiV2
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import org.htmlunit.corejs.javascript.Function
import org.htmlunit.corejs.javascript.Parser
import org.htmlunit.corejs.javascript.Scriptable
import org.htmlunit.corejs.javascript.ScriptableObject
import org.htmlunit.corejs.javascript.ast.FunctionCall
import org.htmlunit.corejs.javascript.ast.Name
import org.htmlunit.corejs.javascript.ast.NumberLiteral
import org.htmlunit.corejs.javascript.ast.ObjectLiteral
import org.htmlunit.corejs.javascript.ast.ObjectProperty
import org.htmlunit.corejs.javascript.ast.StringLiteral
import org.htmlunit.corejs.javascript.ast.VariableInitializer
import splitties.init.appCtx
import kotlin.coroutines.CoroutineContext

/**
 * 从单文件脚本提取稳定的 [BookSource] 配置并校验函数契约。
 *
 * 设计前提：脚本必须自带顶层 `config` 对象（兼容旧版 `source`），
 * 且导出 `search/getChapters/getContent`（文件源为 `search/getBookInfo`）。
 * 声明式规则字段一律剥离，避免 JS 书源混入规则解析链。
 */
object JsSourceConfig {

    private const val CONFIG_PROPERTY = "config"
    private const val LEGACY_CONFIG_PROPERTY = "source"

    val requiredFunctions = listOf("search", "getChapters", "getContent")
    private val fileSourceRequiredFunctions = listOf("search", "getBookInfo")
    private val strippedKeys = listOf(
        "mainJs",
        "ruleSearch",
        "ruleExplore",
        "ruleBookInfo",
        "ruleToc",
        "ruleContent",
        "ruleReview",
    )

    fun extract(
        text: String,
        coroutineContext: CoroutineContext? = null,
        externalJsLib: String? = null,
    ): BookSource {
        try {
            return RhinoClassShutter.withBookSourceClassPolicy(
                enabled = true,
                sourceLabel = "JS书源导入",
            ) {
                extractInternal(text, coroutineContext, externalJsLib)
            }
        } catch (error: CancellationException) {
            throw error
        }
    }

    private fun extractInternal(
        text: String,
        coroutineContext: CoroutineContext?,
        externalJsLib: String?,
    ): BookSource {
        val scope = RhinoScriptEngine.getRuntimeScope(
            buildScriptBindings { values ->
                values[JsSourceLibraryReference.SUPPORT_BINDING] = JsSourceLibraryReference.VERSION
            },
        )
        SharedJsScope.installCryptoJs(scope, coroutineContext)
        try {
            RhinoScriptEngine.eval(text, scope, coroutineContext)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw NoStackTraceException(appCtx.getString(R.string.js_source_error_script_failed, error.message))
        }
        val (configName, config) = findConfig(scope, coroutineContext)
        val json = JsSourceEngine.normalizeJsResult(config, coroutineContext)
            ?: throw NoStackTraceException(appCtx.getString(R.string.js_source_error_config_invalid, configName))
        val jsonObject = runCatching { GSON.fromJson(json, JsonObject::class.java) }.getOrNull()
            ?: throw NoStackTraceException(appCtx.getString(R.string.js_source_error_config_invalid, configName))
        strippedKeys.forEach(jsonObject::remove)
        jsonObject.get("jsLib")?.takeIf {
            it.isJsonPrimitive && it.asJsonPrimitive.isString
        }?.let { library ->
            jsonObject.addProperty(
                "jsLib",
                JsSourceLibraryReference.resolve(library.asString, externalJsLib),
            )
        }
        normalizeExploreUrl(jsonObject)
        normalizeLoginUi(jsonObject)
        val source = runCatching { GSON.fromJson(jsonObject, BookSource::class.java) }.getOrNull()
            ?: throw NoStackTraceException(appCtx.getString(R.string.js_source_error_config_invalid, configName))
        if (source.bookSourceUrl.isBlank()) {
            throw NoStackTraceException(appCtx.getString(R.string.js_source_error_url_required))
        }
        if (source.bookSourceName.isBlank()) {
            throw NoStackTraceException(appCtx.getString(R.string.js_source_error_name_required))
        }
        val required = if (source.bookSourceType == BookSourceType.file) {
            fileSourceRequiredFunctions
        } else {
            requiredFunctions
        }
        required.forEach { name ->
            if (ScriptableObject.getProperty(scope, name) !is Function) {
                throw NoStackTraceException(appCtx.getString(R.string.js_source_error_function_required, name))
            }
        }
        if (!source.exploreUrl.isNullOrBlank() &&
            ScriptableObject.getProperty(scope, "explore") !is Function
        ) {
            throw NoStackTraceException(appCtx.getString(R.string.js_source_error_explore_function))
        }
        // 登录界面 v2：脚本声明 loginUi(state)/loginAction(...) 时，落一个持久标记给界面判断
        if (ScriptableObject.getProperty(scope, "loginUi") is Function) {
            if (!source.loginUi.isNullOrBlank()) {
                throw NoStackTraceException(
                    appCtx.getString(R.string.js_source_error_config_invalid, "loginUi"),
                )
            }
            if (ScriptableObject.getProperty(scope, "loginAction") !is Function) {
                throw NoStackTraceException(
                    appCtx.getString(R.string.js_source_error_function_required, "loginAction"),
                )
            }
            source.loginUi = LoginUiV2.MARKER
        }
        source.mainJs = text
        return source
    }

    private fun findConfig(
        scope: ScriptBindings,
        coroutineContext: CoroutineContext?,
    ): Pair<String, Any> {
        val config = ScriptableObject.getProperty(scope, CONFIG_PROPERTY)
        val legacyConfig = ScriptableObject.getProperty(scope, LEGACY_CONFIG_PROPERTY)
        val hasConfig = config != null && config !== Scriptable.NOT_FOUND
        val hasLegacyConfig = legacyConfig != null && legacyConfig !== Scriptable.NOT_FOUND
        if (hasConfig && (!hasLegacyConfig || isCompleteConfig(config, coroutineContext))) {
            return CONFIG_PROPERTY to requireNotNull(config)
        }
        if (hasLegacyConfig) {
            return LEGACY_CONFIG_PROPERTY to requireNotNull(legacyConfig)
        }
        throw NoStackTraceException(appCtx.getString(R.string.js_source_error_no_config))
    }

    private fun isCompleteConfig(
        value: Any?,
        coroutineContext: CoroutineContext?,
    ): Boolean {
        val json = JsSourceEngine.normalizeJsResult(value, coroutineContext) ?: return false
        val jsonObject = runCatching { GSON.fromJson(json, JsonObject::class.java) }.getOrNull()
            ?: return false
        return runCatching {
            jsonObject.get("bookSourceUrl")?.asString?.isNotBlank() == true &&
                jsonObject.get("bookSourceName")?.asString?.isNotBlank() == true
        }.getOrDefault(false)
    }

    private fun normalizeExploreUrl(jsonObject: JsonObject) {
        val element = jsonObject.get("exploreUrl") ?: return
        if (!element.isJsonArray) return
        val array = element.asJsonArray
        if (array.size() == 0) {
            jsonObject.remove("exploreUrl")
            return
        }
        array.forEachIndexed { index, item ->
            val title = runCatching { item.asJsonObject.get("title")?.asString }.getOrNull()
            if (title.isNullOrBlank()) {
                throw NoStackTraceException(appCtx.getString(R.string.js_source_error_explore_title, index + 1))
            }
        }
        jsonObject.addProperty("exploreUrl", GSON.toJson(array))
    }

    private fun normalizeLoginUi(jsonObject: JsonObject) {
        val element = jsonObject.get("loginUi") ?: return
        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            if (element.asString.filterNot(Char::isWhitespace) == "[]") {
                jsonObject.remove("loginUi")
            }
            return
        }
        if (!element.isJsonArray) return
        val array = element.asJsonArray
        if (array.size() == 0) {
            jsonObject.remove("loginUi")
            return
        }
        array.forEachIndexed { index, item ->
            val name = runCatching { item.asJsonObject.get("name")?.asString }.getOrNull()
            if (name.isNullOrBlank()) {
                throw NoStackTraceException(appCtx.getString(R.string.js_source_error_login_ui_name, index + 1))
            }
        }
        jsonObject.addProperty("loginUi", GSON.toJson(array))
    }

    /**
     * 把脚本里 `config` 对象的 `lastUpdateTime` 改成 [stamp]。
     *
     * 只在能精确定位到字面量/`Date.now()` 时替换，找不到就返回 null（调用方保留原文），
     * 避免正则误伤脚本体里的其它数字。
     */
    fun stampLastUpdateTime(text: String, stamp: Long): String? {
        val ranges = runCatching {
            mutableListOf<IntRange>().apply {
                Parser().parse(text, null, 1).visit { node ->
                    val initializer = node as? VariableInitializer ?: return@visit true
                    val name = (initializer.target as? Name)?.identifier
                    val config = initializer.initializer as? ObjectLiteral
                    if (initializer.enclosingFunction != null ||
                        name != CONFIG_PROPERTY &&
                        name != LEGACY_CONFIG_PROPERTY ||
                        config == null
                    ) {
                        return@visit true
                    }
                    config.elements.filterIsInstance<ObjectProperty>().forEach { property ->
                        val key = when (val nodeKey = property.key) {
                            is Name -> nodeKey.identifier
                            is StringLiteral -> nodeKey.value
                            else -> null
                        }
                        val value = property.value
                        val supported = value is NumberLiteral ||
                            value is FunctionCall &&
                            value.arguments.isEmpty() &&
                            value.target.toSource() == "Date.now"
                        if (key == "lastUpdateTime" && supported) {
                            add(value.absolutePosition until value.absolutePosition + value.length)
                        }
                    }
                    true
                }
            }
        }.getOrNull().orEmpty()
        if (ranges.isEmpty()) return null
        return ranges.sortedByDescending(IntRange::first).fold(text) { script, range ->
            script.replaceRange(range, stamp.toString())
        }
    }
}
