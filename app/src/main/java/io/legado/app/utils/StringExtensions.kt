@file:Suppress("unused")

package io.legado.app.utils

import android.annotation.SuppressLint
import android.icu.text.Collator
import android.icu.util.ULocale
import android.net.Uri
import android.text.Editable
import cn.hutool.core.net.URLEncodeUtil
import io.legado.app.constant.AppPattern
import io.legado.app.constant.AppPattern.dataUriRegex
import java.io.File
import java.lang.Character.codePointCount
import java.lang.Character.offsetByCodePoints
import java.net.InetAddress
import java.text.BreakIterator
import java.util.Locale
import java.util.regex.Pattern
import androidx.core.net.toUri

fun String?.safeTrim() = if (this.isNullOrBlank()) null else this.trim()

/**
 * 源编辑界面超出字符限制后，按字符簇边界安全截断，不会切断代理对（emoji）或组合字符序列，避免截断出乱码。
 * 结果长度不超过 limit 个 char（UTF-16 code unit）。
 */
fun String.safeTake(limit: Int): String {
    if (length <= limit) return this
    return runCatching {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(this)
        // preceding 返回不超过 limit 的最后一个字符簇边界
        substring(0, iterator.preceding(limit + 1))
    }.getOrElse {
        // 退路：截断点若落在代理对中间则回退一位，保证不切出半个 emoji
        val end = if (Character.isHighSurrogate(this[limit - 1])) limit - 1 else limit
        substring(0, end)
    }
}

fun String?.isContentScheme(): Boolean = this?.startsWith("content://") == true

fun String.toEditable(): Editable = Editable.Factory.getInstance().newEditable(this)

fun String.parseToUri(): Uri = if (isUri()) {
    this.toUri()
} else {
    Uri.fromFile(File(this))
}

fun String?.isUri(): Boolean {
    this ?: return false
    return this.startsWith("file://", true) || isContentScheme()
}

fun String?.isAbsUrl() = this?.let {
    it.startsWith("http://", true) || it.startsWith("https://", true)
} ?: false

fun String?.isDataUrl() = this?.let {
    dataUriRegex.matches(it)
} ?: false

fun String?.isJson(): Boolean = this?.run {
    val str = this.trim()
    when {
        str.startsWith("{") && str.endsWith("}") -> true
        str.startsWith("[") && str.endsWith("]") -> true
        else -> false
    }
} ?: false

fun String?.isJsonObject(): Boolean = this?.run {
    val str = this.trim()
    str.startsWith("{") && str.endsWith("}")
} ?: false

fun String?.isJsonArray(): Boolean = this?.run {
    val str = this.trim()
    str.startsWith("[") && str.endsWith("]")
} ?: false

fun String?.isXml(): Boolean = this?.run {
    val str = this.trim()
    str.startsWith("<") && str.endsWith(">")
} ?: false

fun String?.isTrue(nullIsTrue: Boolean = false): Boolean {
    if (this.isNullOrBlank() || this == "null") {
        return nullIsTrue
    }
    return !this.trim().matches("(?i)^(?:false|no|not|0|0.0)$".toRegex())
}

fun String.isHex(): Boolean = all { c ->
    c in '0'..'9' || c in 'A'..'F' || c in 'a'..'f'
}

fun String.splitNotBlank(vararg delimiter: String, limit: Int = 0): Array<String> = run {
    this.split(*delimiter, limit = limit).map { it.trim() }.filterNot { it.isBlank() }
        .toTypedArray()
}

fun String.splitNotBlank(regex: Regex, limit: Int = 0): Array<String> = run {
    this.split(regex, limit).map { it.trim() }.filterNot { it.isBlank() }.toTypedArray()
}

@SuppressLint("ObsoleteSdkInt")
fun String.cnCompare(other: String): Int = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
    Collator.getInstance(ULocale.SIMPLIFIED_CHINESE).compare(this, other)
} else {
    java.text.Collator.getInstance(Locale.CHINA).compare(this, other)
}

/**
 * 字符串所占内存大小
 */
fun String?.memorySize(): Int {
    this ?: return 0
    return 40 + 2 * length
}

/**
 * 是否中文
 */
fun String.isChinese(): Boolean {
    val p = Pattern.compile("[\u4e00-\u9fa5]")
    val m = p.matcher(this)
    return m.find()
}

/**
 * 将字符串拆分为单个字符,包含emoji
 */
fun CharSequence.toStringArray(): Array<String> {
    var codePointIndex = 0
    return try {
        Array(codePointCount(this, 0, length)) {
            val start = codePointIndex
            codePointIndex = offsetByCodePoints(this, start, 1)
            substring(start, codePointIndex)
        }
    } catch (e: Exception) {
        split("").toTypedArray()
    }
}

fun String.escapeRegex(): String = replace(AppPattern.regexCharRegex, "\\\\$0")

fun String.encodeURI(): String = URLEncodeUtil.encodeQuery(this)

fun String.normalizeFileName(): String = replace(AppPattern.fileNameRegex2, "_")

/**
 * 文件名非法字符 → 全角同形字符（导出书籍时用）。
 *
 * 导出文件名由书名/作者/章节名拼出来，其中 `/`、`\` 会被当成目录分隔符（导出的书跑进
 * 多出来的子目录、文件名只剩后半段），`:`、`*`、`?`、`"`、`<`、`>`、`|` 在部分文件系统
 * 与 SAF 上直接创建失败（表现为"文件不存在"）。
 *
 * 换成同形的全角字符既能写进文件系统，又保留原书名的可读性：`A/B` → `A／B`，
 * 也不会像统一替换成 `_` 那样让 `A/B` 与 `A_B` 撞成同一个名字。
 * 章节标题里残留的换行、制表符等控制字符替换为空格。
 */
private val fileNameCharMap: Map<Char, Char> = mapOf(
    '/' to '／',
    '\\' to '＼',
    ':' to '：',
    '*' to '＊',
    '?' to '？',
    '"' to '＂',
    '<' to '＜',
    '>' to '＞',
    '|' to '｜',
)

fun String.toSafeFileName(): String = buildString(length) {
    for (c in this@toSafeFileName) {
        when {
            c < ' ' -> append(' ')
            else -> append(fileNameCharMap[c] ?: c)
        }
    }
}

/**
 * 将ip字符串转为InetAddress
 */
fun String.parseIpsFromString(): List<InetAddress>? = split(",")
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .mapNotNull { it.runCatching { InetAddress.getByName(this) }.getOrNull() }
    .takeIf { it.isNotEmpty() }

fun String.quoteReplacementJs(): String {
    if (!this.contains('\\') && !this.contains('$')) {
        return this
    }
    val sb = StringBuilder()
    for (c in this) {
        when (c) {
            '\\' -> sb.append("\\\\")
            '$' -> sb.append("\\$")
            else -> sb.append(c)
        }
    }
    return sb.toString()
}
