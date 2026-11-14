package io.legado.app.model.login

import com.google.gson.JsonObject
import io.legado.app.data.entities.rule.RowUi
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject

/**
 * 动态 JS 登录界面（loginUi v2）的渲染与动作协议。
 *
 * 书源在 `mainJs` 里声明两个函数：
 * - `loginUi(state)` 返回 `{rows:[...]}`，rows 元素即 [RowUi]（用 `key` 做表单键、`name` 做显示名）；
 * - `loginAction(action, state, form)` 返回 `{state:{...}}` / `{login:{...}}` / `{error:{...}}` / `{close:true}`。
 *
 * 导入时把 [MARKER] 写进 `book_sources.loginUi` 列，作为「这个源用 v2」的持久标记。
 */
object LoginUiV2 {

    const val MARKER = """{"version":2}"""
    private val knownCommands = setOf("state", "error", "login", "close")

    fun isV2(loginUi: String?): Boolean {
        val text = loginUi?.trim() ?: return false
        if (!text.startsWith("{")) return false
        val obj = GSON.fromJsonObject<JsonObject>(text).getOrNull() ?: return false
        return runCatching { obj.get("version")?.asInt == 2 }.getOrDefault(false)
    }

    /** 解析 `loginUi(state)` 的返回值；结构非法时返回 null（界面显示渲染失败） */
    fun parseRender(json: String?): List<RowUi>? {
        if (json.isNullOrBlank()) return null
        val obj = GSON.fromJsonObject<JsonObject>(json).getOrNull() ?: return null
        val rowsJson = obj.get("rows")?.takeIf { it.isJsonArray } ?: return null
        val rows = GSON.fromJsonArray<RowUi>(rowsJson.toString()).getOrNull() ?: return null
        if (rows.isEmpty() || rows.any { !it.isValid() }) return null
        val keys = rows.filter {
            it.type == RowUi.Type.text ||
                it.type == RowUi.Type.password ||
                it.type == RowUi.Type.select ||
                it.type == RowUi.Type.toggle
        }.mapNotNull(RowUi::key)
        val actions = rows.filter {
            it.type == RowUi.Type.button || it.type == RowUi.Type.toggle
        }.mapNotNull(RowUi::action)
        if (keys.size != keys.distinct().size || actions.size != actions.distinct().size) {
            return null
        }
        return rows
    }

    private fun RowUi.isValid(): Boolean {
        if (name.isBlank() || countdown?.let { it < 0 } == true) return false
        return when (type) {
            RowUi.Type.text, RowUi.Type.password -> !key.isNullOrBlank()
            RowUi.Type.label -> true
            RowUi.Type.select -> !key.isNullOrBlank() && choices().isNotEmpty()
            RowUi.Type.toggle -> !key.isNullOrBlank() &&
                (value == null || value == "true" || value == "false") &&
                (action == null || action.isNotBlank())
            RowUi.Type.button -> !action.isNullOrBlank()
            else -> false
        }
    }

    data class ActionResult(
        val stateJson: String? = null,
        val error: Map<String, String>? = null,
        val loginJson: String? = null,
        val close: Boolean = false,
        val unknownKeys: List<String> = emptyList(),
        val malformed: Boolean = false,
    )

    /** 解析 `loginAction(...)` 的返回值；结构非法时 `malformed = true`（界面忽略并提示） */
    fun parseActionResult(json: String?): ActionResult {
        if (json.isNullOrBlank()) return ActionResult()
        val obj = GSON.fromJsonObject<JsonObject>(json).getOrNull()
            ?: return ActionResult(malformed = true)
        val state = obj.get("state")?.takeUnless { it.isJsonNull }
        val error = obj.get("error")?.takeUnless { it.isJsonNull }
        val login = obj.get("login")?.takeUnless { it.isJsonNull }
        val close = obj.get("close")?.takeUnless { it.isJsonNull }
        if (state != null && !state.isJsonObject ||
            error != null && !error.isJsonObject ||
            login != null && !login.isJsonObject ||
            close != null && (!close.isJsonPrimitive || !close.asJsonPrimitive.isBoolean)
        ) {
            return ActionResult(malformed = true)
        }
        val errors = error?.let {
            GSON.fromJsonObject<Map<String, String>>(it.toString()).getOrNull()
                ?: return ActionResult(malformed = true)
        }
        val closeValue = close?.let {
            runCatching { it.asBoolean }.getOrNull()
                ?: return ActionResult(malformed = true)
        } ?: false
        return ActionResult(
            stateJson = state?.toString(),
            error = errors,
            loginJson = login?.toString(),
            close = closeValue,
            unknownKeys = obj.keySet().filterNot { it in knownCommands },
        )
    }

    /**
     * 字段初值优先级：本次渲染值 > 本次会话已输入值 > 已保存的登录信息。
     */
    fun resolveFieldValue(
        renderValue: String?,
        sessionInput: String?,
        stored: String?,
    ): String? = renderValue ?: sessionInput ?: stored
}
