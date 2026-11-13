package io.legado.app.model.jsSource

import com.script.buildScriptBindings
import com.script.rhino.RhinoClassShutter
import com.script.rhino.RhinoScriptEngine
import io.legado.app.exception.NoStackTraceException
import org.json.JSONObject
import kotlin.coroutines.CoroutineContext

/**
 * 单文件 JS 书源可用的纯 JS 执行沙箱，对齐参考分支 `java.getQuickJsSandbox().evalString(...)` 的契约。
 *
 * 典型用法是把签名核心函数 `toString()` 之后连同参数丢进沙箱执行（需要 BigInt / 64 位位运算），
 * 只依赖标准 JS，不依赖宿主对象。因此这里用与主引擎同源的 Rhino，在一个**全新空作用域**里执行：
 * - 不注入 java / source / cache 等任何宿主对象；
 * - 套用书源类访问策略，禁止触碰 Android 与反射相关类；
 * - 每次调用都是新作用域，沙箱脚本无法污染也读不到书源主脚本的全局变量；
 * - 结果统一按字符串返回（对象转 JSON），与 QuickJS 侧 `evalString` 的返回形态一致。
 */
class JsSourceSandbox(
    private val coroutineContext: CoroutineContext? = null,
    private val sourceLabel: String? = null,
) {

    fun evalString(script: String): String = eval(script)

    /** 兼容带数据入口：数据以字符串字面量作为唯一参数传入，沙箱内不额外注入宿主对象。 */
    fun evalStringWithData(script: String, data: String): String = eval("(function(__data){$script})(" + JSONObject.quote(data) + ")")

    private fun eval(script: String): String {
        if (script.isBlank()) throw NoStackTraceException("沙箱脚本为空")
        if (script.length > MAX_SCRIPT_CHARS) throw NoStackTraceException("沙箱脚本过大")
        val scope = RhinoScriptEngine.getRuntimeScope(buildScriptBindings { })
        val result = RhinoClassShutter.withBookSourceClassPolicy(
            enabled = true,
            sourceLabel = sourceLabel ?: "JS书源沙箱",
        ) {
            RhinoScriptEngine.compile(script).eval(scope, coroutineContext)
        }
        val string = JsSourceEngine.normalizeJsResult(result, coroutineContext) ?: "null"
        if (string.length > MAX_OUTPUT_CHARS) throw NoStackTraceException("沙箱返回值过大")
        return string
    }

    private companion object {
        const val MAX_SCRIPT_CHARS = 1_000_000
        const val MAX_OUTPUT_CHARS = 4_000_000
    }
}
