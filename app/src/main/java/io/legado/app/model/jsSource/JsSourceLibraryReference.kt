package io.legado.app.model.jsSource

import io.legado.app.R
import io.legado.app.exception.NoStackTraceException
import splitties.init.appCtx
import java.security.MessageDigest

/**
 * 主脚本对内联 JSON 书源外置 jsLib 的引用。
 *
 * 允许 JS 单文件书源只带 `legado.js.library.ref/1:<sha256>` 而把真正的库留在
 * 已导入的 JSON 书源里，避免同一份大库重复两份；哈希不匹配时拒绝加载。
 */
internal object JsSourceLibraryReference {

    const val SUPPORT_BINDING = "__legadoJsLibraryReferenceVersion"
    const val VERSION = 1
    private const val PREFIX = "legado.js.library.ref/1:"
    private val digestPattern = Regex("[0-9a-f]{64}")

    fun resolve(value: String, externalJsLib: String?): String {
        if (!value.startsWith("legado.js.library.ref/")) return value
        val expected = value.removePrefix(PREFIX)
        if (!value.startsWith(PREFIX) || !digestPattern.matches(expected)) {
            throw NoStackTraceException(appCtx.getString(R.string.js_source_library_ref_invalid))
        }
        if (externalJsLib.isNullOrBlank()) {
            throw NoStackTraceException(appCtx.getString(R.string.js_source_library_missing))
        }
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(externalJsLib.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (actual != expected) {
            throw NoStackTraceException(appCtx.getString(R.string.js_source_library_mismatch))
        }
        return externalJsLib
    }
}
