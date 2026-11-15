package io.legado.app

import com.script.ScriptBindings
import com.script.rhino.RhinoClassShutter
import com.script.rhino.RhinoScriptEngine
import org.junit.Assert
import org.junit.Test

/**
 * 书源执行期类访问白名单（RhinoClassShutter.bookSourceDirectClassImports）回归测试。
 *
 * 背景：书源规则执行期间 io.legado.app.* 类默认拒绝，仅白名单可构造；
 * 被拒的类会退化成 NativeJavaPackage，`new` 时抛
 * "TypeError: [JavaPackage …] 不是函数，它是 object"。
 * 新增白名单条目时必须同步补充这里的允许用例与默认拒绝用例。
 */
class RhinoClassShutterTest {

    private fun evalInBookSourcePolicy(js: String): Any? {
        val scope = RhinoScriptEngine.getRuntimeScope(ScriptBindings())
        return RhinoClassShutter.withBookSourceClassPolicy(
            enabled = true,
            sourceLabel = "RhinoClassShutterTest",
        ) {
            RhinoScriptEngine.eval(js, scope)
        }
    }

    /**
     * 用户报错复现（2026-11-15）：请求头规则里各分支书源生态探测 loginUi v2 的
     * 事实标准写法，要求白名单内可直接构造 BookSource 空实例。
     */
    @Test
    fun bookSourceDirectImportConstructibleUnderPolicy() {
        val result = evalInBookSourcePolicy(
            """
            var StrResponse = Packages.io.legado.app.help.http.StrResponse
            var emptySource = new Packages.io.legado.app.data.entities.BookSource()
            emptySource.bookSourceUrl
            """.trimIndent()
        )
        Assert.assertEquals("", result)
    }

    /** 白名单外的 app 类必须维持默认拒绝。 */
    @Test
    fun unlistedAppClassDeniedUnderPolicy() {
        try {
            evalInBookSourcePolicy(
                "new Packages.io.legado.app.data.entities.BookChapter()"
            )
            Assert.fail("未列入白名单的 app 类应被默认拒绝")
        } catch (ignored: Exception) {
            // 预期：ScriptException 包装的 "JavaPackage 不是函数" TypeError
        }
    }
}
