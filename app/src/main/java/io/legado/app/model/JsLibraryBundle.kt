package io.legado.app.model

import com.google.gson.JsonParser

/**
 * 结构化 jsLib 包（`legado.js.library/1`）解析器。
 *
 * 与「普通 JS 文本 / {名字: 代码} 映射」两种历史写法区分：只有 format 字段命中
 * [FORMAT] 才按 bundle 解析，否则返回 null 交给 [SharedJsScope] 的旧路径处理。
 * scripts 是可执行代码，resources 只能是数据（不得被当作 JS 求值）。
 */
internal class JsLibraryBundle private constructor(
    val scripts: List<String>,
    val resources: Map<String, String>,
    val usesCryptoJs: Boolean,
) {
    companion object {
        private const val FORMAT = "legado.js.library/1"
        private const val MAX_SIZE = 2_000_000

        fun parse(value: String?): JsLibraryBundle? {
            if (value == null || !value.trimStart().startsWith('{')) return null
            // A plain JS library may itself start with a block; preserve that path.
            val root = runCatching { JsonParser.parseString(value) }.getOrNull() ?: return null
            if (!root.isJsonObject) return null
            val obj = root.asJsonObject
            val format = obj.get("format")
            if (format == null || !format.isJsonPrimitive || !format.asJsonPrimitive.isString ||
                !format.asString.startsWith("legado.js.library/")
            ) {
                return null
            }
            require(format.asString == FORMAT) { "Unsupported jsLib bundle format" }
            require(value.length <= MAX_SIZE && value.toByteArray(Charsets.UTF_8).size <= MAX_SIZE) {
                "jsLib bundle exceeds size limit"
            }
            require(
                obj.keySet().containsAll(setOf("format", "scripts", "resources")) &&
                    obj.keySet().all { it in setOf("format", "scripts", "resources", "builtins") }
            ) {
                "Invalid jsLib bundle fields"
            }
            // Legacy libraries retain the automatic CryptoJS global. A new bundle
            // can explicitly omit unused builtins instead of paying their startup cost.
            val builtins = obj.get("builtins")
            if (builtins != null) {
                require(
                    builtins.isJsonArray && builtins.asJsonArray.size() <= 1 &&
                        builtins.asJsonArray.all {
                            it.isJsonPrimitive && it.asJsonPrimitive.isString &&
                                it.asString == "cryptoJS"
                        }
                ) { "Invalid jsLib bundle builtins" }
            }
            val scripts = obj.get("scripts")
            val resources = obj.get("resources")
            require(scripts?.isJsonArray == true && scripts.asJsonArray.size() <= 32) {
                "Invalid jsLib bundle scripts"
            }
            require(resources?.isJsonObject == true && resources.asJsonObject.size() <= 64) {
                "Invalid jsLib bundle resources"
            }
            val code = scripts.asJsonArray.map {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isString) {
                    "jsLib script must be a string"
                }
                it.asString
            }
            val data = resources.asJsonObject.entrySet().associate { (name, content) ->
                require(name.isNotEmpty() && name.length <= 128 && !name.any { it.code < 32 }) {
                    "Invalid jsLib resource name"
                }
                require(content.isJsonPrimitive && content.asJsonPrimitive.isString) {
                    "jsLib resource must be a string"
                }
                name to content.asString
            }
            return JsLibraryBundle(code, data, builtins == null || builtins.asJsonArray.size() == 1)
        }
    }
}
