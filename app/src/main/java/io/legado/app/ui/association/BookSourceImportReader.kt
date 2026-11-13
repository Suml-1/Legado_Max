package io.legado.app.ui.association

import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.jsSource.JsSourceConfig
import io.legado.app.model.jsSource.JsSourceUpsert
import io.legado.app.utils.GSON
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import java.io.Reader

/**
 * 文件 / 网络 / 粘贴共用的书源导入解析。
 *
 * 关键约定：首字符不是 `[` / `{` 的一律当作**单文件 JS 书源**（这正是 `.js` 网址与本地 `.js` 文件
 * 能导入的原因）；JSON 一次只反序列化一条，绝不把整个合集变成字符串。
 */
internal suspend fun readBookSourceImport(
    input: Reader,
    allowSourceUrls: Boolean,
    onSource: (BookSource) -> Unit,
    onSourceUrl: suspend (String) -> Unit,
): Int {
    val reader = input.buffered()
    var first: Int
    do {
        currentCoroutineContext().ensureActive()
        reader.mark(1)
        first = reader.read()
    } while (first != -1 && (first.toChar().isWhitespace() || first == 0xfeff))
    if (first == -1) throw NoStackTraceException("书源内容为空")
    reader.reset()
    if (first != '['.code && first != '{'.code) {
        val script = readBoundedSourceScript(reader)
        JsSourceUpsert.validatePayload(script)?.let {
            throw NoStackTraceException(
                if (it == JsSourceUpsert.PayloadIssue.EMPTY) {
                    "JS 书源内容为空"
                } else {
                    "JS 书源不能超过 1 MiB"
                },
            )
        }
        onSource(withTimeout(30_000L) { JsSourceConfig.extract(script, currentCoroutineContext()) })
        return 0
    }
    val json = JsonReader(reader).apply { strictness = Strictness.LENIENT }
    fun emit(source: BookSource?) {
        if (source == null) throw JsonSyntaxException("书源列表不能存在 null 元素")
        if (source.bookSourceUrl.isEmpty()) throw NoStackTraceException("不是书源")
        onSource(source)
    }
    if (json.peek() == JsonToken.BEGIN_ARRAY) {
        var accepted = 0
        var skipped = 0
        json.beginArray()
        while (json.hasNext()) {
            currentCoroutineContext().ensureActive()
            val source = GSON.fromJson<BookSource>(json, BookSource::class.java)
            // 合集中的空地址记录不影响其它书源；语法、类型和 null 错误仍正常抛出。
            if (source != null && source.bookSourceUrl.isEmpty()) {
                skipped++
            } else {
                emit(source)
                accepted++
            }
        }
        json.endArray()
        if (json.peek() != JsonToken.END_DOCUMENT) throw JsonSyntaxException("书源 JSON 尾部存在多余内容")
        if (accepted == 0 && skipped > 0) throw NoStackTraceException("不是书源：所有记录的书源地址均为空")
        return skipped
    } else if (allowSourceUrls) {
        // 此处最多一条源或一个链接包装对象，不构建整个书源数组的 JSON 树。
        val root = GSON.fromJson<JsonObject>(json, JsonObject::class.java)
        val urls = root.get("sourceUrls")
        if (urls?.isJsonArray == true) {
            for (url in urls.asJsonArray) {
                currentCoroutineContext().ensureActive()
                onSourceUrl(url.asString)
            }
        } else {
            emit(GSON.fromJson(root, BookSource::class.java))
        }
    } else {
        emit(GSON.fromJson<BookSource>(json, BookSource::class.java))
    }
    if (json.peek() != JsonToken.END_DOCUMENT) throw JsonSyntaxException("书源 JSON 尾部存在多余内容")
    return 0
}

/** 保留原先 trim 后的 JS 大小规则；超长尾部空白不占据无限缓冲。 */
private suspend fun readBoundedSourceScript(reader: Reader): String {
    val limit = JsSourceUpsert.MAX_SOURCE_BYTES
    val text = StringBuilder()
    val buffer = CharArray(8192)
    var position = 0L
    var end = 0
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = reader.read(buffer)
        if (count == -1) break
        for (index in 0 until count) {
            val char = buffer[index]
            if (position < limit) text.append(char)
            if (!char.isWhitespace()) {
                if (position >= limit) throw NoStackTraceException("JS 书源不能超过 1 MiB")
                end = text.length
            }
            position++
        }
    }
    return text.substring(0, end)
}
