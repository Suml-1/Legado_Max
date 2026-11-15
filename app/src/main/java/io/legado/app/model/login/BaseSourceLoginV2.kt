package io.legado.app.model.login

import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.model.jsSource.JsSourceEngine
import io.legado.app.utils.isAbsUrl

/**
 * 登录界面 v2：由 `loginUi(state)` / `loginAction(action, state, form)` 两个函数驱动。
 *
 * 与 v1 的区别：v1 把表单结构与按钮动作都写在 `loginUi`/`loginUrl` 的 JS 里由宿主拼装；
 * v2 里宿主只当渲染器，界面完全（含动作）由脚本返回，`loginAction` 的 `state` 会原样回传，
 * 便于书源维护多步登录状态。
 *
 * v2 函数的两种承载：
 * - loginUrl 字段的登录 JS（LegadoT 语义，定时任务虚拟源即此形态）；
 * - JS 书源的 mainJs（NGJS 单文件书源形态）。
 */
fun BaseSource.isLoginUiV2(): Boolean =
    LoginUiV2.isV2(loginUi) || (loginUi.isNullOrBlank() && declaresLoginUiV2Functions())

/**
 * 判定 v2 登录界面能否渲染。两者都没有时（例如只写了 v2 标记、脚本里无函数的定时任务）
 * 无法渲染，调用方应回退 loginUrl 的 WebView 登录。
 */
fun BaseSource.canRenderLoginUiV2(): Boolean {
    val loginJs = loginUrl
    if (!loginJs.isNullOrBlank() && !loginJs.isAbsUrl()) return true
    val js = (this as? BookSource)?.mainJs ?: return false
    return js.contains("loginUi") && js.contains("loginAction")
}

private fun BaseSource.declaresLoginUiV2Functions(): Boolean {
    val js = (this as? BookSource)?.mainJs ?: return false
    return js.contains("loginUi") && js.contains("loginAction")
}

private fun BaseSource.jsEngineOrNull(): JsSourceEngine? {
    val bookSource = this as? BookSource ?: return null
    if (bookSource.mainJs.isNullOrBlank()) return null
    return JsSourceEngine(bookSource)
}

/** v2 渲染的函数来源：loginUrl 的登录 JS 优先（任务虚拟源形态），否则 JS 书源走 mainJs */
private fun BaseSource.loginUiV2HostJs(): String? {
    return loginUrl?.takeIf { it.isNotBlank() && !it.isAbsUrl() }
}

/** 渲染一帧：返回 `{rows:[...]}` 原文，非法结构由 [LoginUiV2.parseRender] 判负 */
fun BaseSource.evalLoginUiV2(stateJson: String): String? {
    val hostJs = loginUiV2HostJs()
    if (hostJs != null) {
        val result = evalJS("$hostJs\nloginUi(JSON.parse(String(__loginState)))") {
            put("__loginState", stateJson)
        }
        return JsSourceEngine.normalizeJsResult(result)
    }
    val engine = jsEngineOrNull() ?: return null
    return engine.callExpression(
        "loginUi(JSON.parse(__loginState))",
        listOf("__loginState" to stateJson),
    )
}

/** 执行一个按钮/开关动作：返回 `{state|error|login|close}` 原文 */
fun BaseSource.evalLoginActionV2(
    action: String,
    stateJson: String,
    formJson: String,
): String? {
    val hostJs = loginUiV2HostJs()
    if (hostJs != null) {
        val result = evalJS(
            "$hostJs\nloginAction(String(__loginAction), JSON.parse(__loginState), JSON.parse(__loginForm))",
        ) {
            put("__loginAction", action)
            put("__loginState", stateJson)
            put("__loginForm", formJson)
        }
        return JsSourceEngine.normalizeJsResult(result)
    }
    val engine = jsEngineOrNull() ?: return null
    return engine.callExpression(
        "loginAction(String(__loginAction), JSON.parse(__loginState), JSON.parse(__loginForm))",
        listOf(
            "__loginAction" to action,
            "__loginState" to stateJson,
            "__loginForm" to formJson,
        ),
    )
}
