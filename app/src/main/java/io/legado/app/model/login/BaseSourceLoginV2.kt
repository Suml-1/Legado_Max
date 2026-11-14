package io.legado.app.model.login

import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.model.jsSource.JsSourceEngine

/**
 * 登录界面 v2：由书源的 `loginUi(state)` / `loginAction(action, state, form)` 两个函数驱动。
 *
 * 与 v1 的区别：v1 把表单结构与按钮动作都写在 `loginUi`/`loginUrl` 的 JS 里由宿主拼装；
 * v2 里宿主只当渲染器，界面完全（含动作）由脚本返回，`loginAction` 的 `state` 会原样回传，
 * 便于书源维护多步登录状态。
 */
fun BaseSource.isLoginUiV2(): Boolean = LoginUiV2.isV2(loginUi) || declaresLoginUiV2InMainJs()

/**
 * 兼容标记落库前就导入的源（例如商店里已有的 JS 源）：只在内存里判断。
 *
 * 只做子串判断——菜单可见性这条路径会被频繁调用，编译 mainJs 太贵；
 * 真正渲染时仍会走 [evalLoginUiV2]，由 `LoginUiV2.parseRender` 校验结构。
 */
private fun BaseSource.declaresLoginUiV2InMainJs(): Boolean {
    if (!loginUi.isNullOrBlank()) return false
    val js = (this as? BookSource)?.mainJs ?: return false
    return js.contains("loginUi") && js.contains("loginAction")
}

private fun BaseSource.jsEngineOrNull(): JsSourceEngine? {
    val bookSource = this as? BookSource ?: return null
    if (bookSource.mainJs.isNullOrBlank()) return null
    return JsSourceEngine(bookSource)
}

/** 渲染一帧：返回 `{rows:[...]}` 原文，非法结构由 [LoginUiV2.parseRender] 判负 */
fun BaseSource.evalLoginUiV2(stateJson: String): String? {
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
