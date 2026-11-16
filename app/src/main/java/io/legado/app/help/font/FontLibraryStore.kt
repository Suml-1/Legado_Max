package io.legado.app.help.font

import android.graphics.Typeface
import android.os.Build
import android.util.AtomicFile
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.utils.GSON
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/** One visible asset can include old app-owned copies; external source files are never owned here. */
internal data class LocalFontAsset(
    val id: String,
    val name: String,
    val path: String,
    val paths: List<String>,
    val size: Long,
)

internal class PreparedFontAsset(val file: File, val sha256: String, val commit: () -> LocalFontAsset)

@Keep
internal data class FontLibraryLabel(
    @SerializedName("sha256") val sha256: String = "",
    @SerializedName("name") val name: String = "",
)

@Keep
internal data class FontLibraryIndex(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    @SerializedName("fonts") val fonts: List<FontLibraryLabel> = emptyList(),
)

/** File ownership checks are shared by import, resource installation and deletion recovery. */
internal object FontLibraryPaths {
    private val extensions = setOf("ttf", "otf", "font")

    fun managedFile(path: String, roots: List<File>): File? = runCatching {
        val raw = if (path.startsWith("file:")) File(java.net.URI(path)) else File(path)
        if (!raw.isAbsolute || raw.extension.lowercase() !in extensions) return null
        val file = raw.canonicalFile
        file.takeIf { candidate -> roots.any { candidate.parentFile == it.canonicalFile } }
    }.getOrNull()

    fun displayName(name: String, digest: String): String {
        val original = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("(?i)\\.(ttf|otf|font)$"), "")
        val generated = Regex("-[a-f0-9]{20}(?:-[a-f0-9-]{36})?$").containsMatchIn(original)
        val text = original
            .replace(Regex("-[a-f0-9]{20}(?:-[a-f0-9-]{36})?$"), "")
            .let { if (generated) it.replace('_', ' ') else it }
            .filterNot { it.isISOControl() }.trim().take(160)
        return text.takeUnless { it.isBlank() || it.matches(Regex("[a-f0-9]{64}")) }
            ?: "本地字体 ${digest.take(8)}"
    }
}

/** All disk operations must run on IO. Lock order: resource install mutex, then this lock. */
internal object FontLibraryStore {
    val lock = Any()
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()
    private const val MAX_BYTES = 64L * 1024 * 1024
    private val legacyRoot get() = File(appCtx.externalFiles, "font")
    val downloadRoot get() = File(legacyRoot, "resource-center")
    private val oldInterfaceRoot get() = File(appCtx.filesDir, "interface-fonts")
    private val roots get() = listOf(downloadRoot, legacyRoot, oldInterfaceRoot)
    private val indexFile get() = File(appCtx.filesDir, "font-library/index.json")
    private data class FileStamp(val path: String, val size: Long, val modified: Long)
    private val digests = object : LinkedHashMap<FileStamp, String>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FileStamp, String>?) = size > 512
    }

    fun managedFile(path: String): File? = FontLibraryPaths.managedFile(path, roots)

    fun list(refresh: Boolean = false): List<LocalFontAsset> = synchronized(lock) {
        if (refresh) digests.clear()
        val names = labels().associate { it.sha256 to it.name }
        val activePath = appCtx.getPrefString("ngInterfaceFont.file")
        val activeName = appCtx.getPrefString("ngInterfaceFont.name").orEmpty()
        val files = roots.flatMap { directory ->
            if (!directory.exists()) emptyList() else {
                check(directory.isDirectory) { "字体目录不可用" }
                checkNotNull(directory.listFiles()) { "无法读取字体目录" }.mapNotNull { candidate ->
                    managedFile(candidate.absolutePath)?.takeIf { it.isFile }
                }
            }
        }.distinctBy { it.path }
        files.groupBy { file ->
            val stamp = FileStamp(file.path, file.length(), file.lastModified())
            digests.getOrPut(stamp) { hash(file) }
        }.map { (digest, copies) ->
            val preferred = copies.first()
            val label = names[digest] ?: activeName.takeIf { it.isNotBlank() && copies.any { file -> file.path == activePath } }
                ?: FontLibraryPaths.displayName(preferred.name, digest)
            LocalFontAsset(digest, label, preferred.path, copies.map { it.path }, preferred.length())
        }.sortedBy { it.name.lowercase() }
    }

    fun prepareDownload(source: File, name: String): PreparedFontAsset = synchronized(lock) {
        require(source.isFile && source.length() in 1..MAX_BYTES) { "字体为空或超过64MB" }
        validate(source)
        val digest = hash(source)
        val directory = downloadRoot.canonicalFile
        var existing = list().firstOrNull { it.id == digest }?.paths
            ?.firstOrNull { File(it).parentFile == directory }?.let(::File)
        if (existing != null && hash(existing) != digest) {
            existing = list(refresh = true).firstOrNull { it.id == digest }?.paths
                ?.firstOrNull { File(it).parentFile == directory }?.let(::File)
        }
        val extension = if (source.extension.equals("otf", true) || name.endsWith(".otf", true)) "otf" else "ttf"
        val target = (existing ?: File(directory, "$digest.$extension").let { candidate ->
            // A hash-named file may have been edited externally. Preserve it and its live references.
            if (candidate.exists() && hash(candidate) != digest) File(directory, "$digest-${UUID.randomUUID()}.$extension")
            else candidate
        }).canonicalFile
        PreparedFontAsset(target, digest) {
            synchronized(lock) {
                require(hash(source) == digest) { "字体源文件已变化，请重试" }
                checkNotNull(managedFile(target.path)) { "字体目标路径无效" }
                if (target.exists()) {
                    check(target.isFile && hash(target) == digest) { "本地字体已变化，请刷新后重试" }
                } else {
                    check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "无法创建字体目录" }
                    val temporary = File.createTempFile(".font-", ".part", target.parentFile)
                    try {
                        source.inputStream().use { input ->
                            FileOutputStream(temporary).use { output -> input.copyTo(output); output.fd.sync() }
                        }
                        check(hash(temporary) == digest) { "字体复制校验失败" }
                        check(temporary.renameTo(target)) { "无法保存字体" }
                    } finally { temporary.delete() }
                }
                val current = labels()
                if (current.none { it.sha256 == digest }) {
                    saveLabels(current + FontLibraryLabel(digest, FontLibraryPaths.displayName(name, digest)))
                }
                changes.update { it + 1 }
                list().first { it.id == digest }.copy(path = target.path)
            }
        }
    }

    /** Directory association and external-file deletion refresh readers without creating font copies. */
    fun notifyChanged() { changes.update { it + 1 } }

    /** Called only by ResourceRepository after its journal and all live usage checks. */
    fun deleteFiles(asset: LocalFontAsset) = synchronized(lock) {
        require(asset.id.matches(Regex("[a-f0-9]{64}")) && asset.paths.isNotEmpty()) { "字体记录无效" }
        val files = asset.paths.map { checkNotNull(managedFile(it)) { "不能删除外部字体原文件" } }
        val currentLabels = labels()
        // Validate every target before the first delete. Recovery may include already deleted targets.
        files.forEach { file -> check(!file.exists() || file.isFile && hash(file) == asset.id) { "字体文件已变化，请刷新后重试" } }
        files.forEach { file -> check(!file.exists() || file.delete()) { "无法删除字体，请重试" } }
        digests.keys.removeAll { stamp -> stamp.path in asset.paths }
        // A recovered deletion owns only its recorded paths, not later imports of the same bytes.
        if (list().none { it.id == asset.id }) saveLabels(currentLabels.filterNot { it.sha256 == asset.id })
        File(appCtx.cacheDir, "interface-font-preview/${asset.id}.font").delete()
        changes.update { it + 1 }
    }

    private fun validate(file: File) {
        val header = file.inputStream().use { input -> ByteArray(4).also { require(input.read(it) == 4) { "字体文件无效" } } }
        require(header.contentEquals(byteArrayOf(0, 1, 0, 0)) || header.toString(Charsets.US_ASCII) in setOf("OTTO", "true", "ttcf")) { "不是有效的字体文件" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireNotNull(Typeface.Builder(file).build()) { "字体无法解析" }
        } else Typeface.createFromFile(file)
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val bytes = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                digest.update(bytes, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun labels(): List<FontLibraryLabel> {
        val atomic = AtomicFile(indexFile)
        if (!indexFile.exists() && !File(indexFile.path + ".bak").exists()) return emptyList()
        val index = atomic.openRead().use { GSON.fromJson(it.reader(), FontLibraryIndex::class.java) }
        require(index != null && index.schemaVersion == 1) { "字体名称记录无效" }
        require(index.fonts.all { it.sha256.matches(Regex("[a-f0-9]{64}")) && it.name.isNotBlank() } &&
            index.fonts.map { it.sha256 }.distinct().size == index.fonts.size) { "字体名称记录无效" }
        return index.fonts
    }

    private fun saveLabels(labels: List<FontLibraryLabel>) {
        indexFile.parentFile!!.mkdirs()
        val atomic = AtomicFile(indexFile)
        val output = atomic.startWrite()
        try {
            output.write(GSON.toJson(FontLibraryIndex(fonts = labels)).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }
}
