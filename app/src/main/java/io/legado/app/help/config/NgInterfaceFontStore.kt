package io.legado.app.help.config

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.core.provider.FontsContractCompat
import io.legado.app.help.font.FontLibraryStore
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.getPrefString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** A decoded source, not an installed file. content: fonts deliberately have no local file. */
internal class PreparedInterfaceFont internal constructor(
    val typeface: Typeface,
    val choice: String,
    val file: File?,
    internal val source: String,
    internal val fingerprint: String,
    internal val generation: Long,
)

/** Font choices stay separate from reader fonts; new choices reference their original source. */
internal object NgInterfaceFontStore {
    private const val CHOICE = "ngInterfaceFont.choice"
    private const val FILE = "ngInterfaceFont.file"
    private const val NAME = "ngInterfaceFont.name"
    const val DIRECTORY = "ngInterfaceFont.directory"
    private const val MAX_BYTES = 64L * 1024 * 1024
    private val cacheLock = Any()
    private data class SourceKey(val source: String, val stamp: String)
    private data class AppliedFont(val choice: String, val legacyFile: String?, val prepared: PreparedInterfaceFont)
    private val cache = LruCache<SourceKey, PreparedInterfaceFont>(12)
    private val generations = mutableMapOf<String, Long>()
    private var sequence = 0L
    private var applied: AppliedFont? = null
    private val loader = Semaphore(2)
    private val appliedChanges = MutableStateFlow(0L)
    val revision = appliedChanges.asStateFlow()
    val systemChoices = listOf("system", "serif", "monospace")

    fun choice(context: Context): String = context.getPrefString(CHOICE)
        ?: NgThemeLibraryStore.activeTheme(context)?.let { theme ->
            theme.resolvePackageAsset(theme.resourceProfile?.appFont)?.toURI()?.toString()
        } ?: "system"
    fun name(context: Context): String = context.getPrefString(NAME).orEmpty()
    fun hasOverride(context: Context): Boolean = context.getPrefString(CHOICE) != null
    fun systemTypeface(choice: String): Typeface = when (choice) {
        "serif" -> Typeface.SERIF
        "monospace" -> Typeface.MONOSPACE
        else -> Typeface.DEFAULT
    }

    suspend fun load(context: Context, choice: String, reloadFromSource: Boolean = false): PreparedInterfaceFont {
        val generation = synchronized(cacheLock) {
            if (reloadFromSource) {
                generations[choice] = ++sequence
                cache.snapshot().filterValues { it.choice == choice }.keys.forEach(cache::remove)
            }
            generations[choice] ?: 0L
        }
        if (choice in systemChoices) {
            currentCoroutineContext().ensureActive()
            return PreparedInterfaceFont(systemTypeface(choice), choice, null, choice, "", generation)
        }
        return withContext(Dispatchers.IO) {
            loader.withPermit {
                currentCoroutineContext().ensureActive()
                val result = prepare(context, choice, reloadFromSource, generation)
                currentCoroutineContext().ensureActive()
                result
            }
        }
    }

    /** Already applied typefaces remain in memory if a source vanishes; preferences are not rewritten. */
    fun typeface(context: Context): Typeface {
        val selected = choice(context)
        if (selected in systemChoices) return systemTypeface(selected)
        val legacy = context.getPrefString(FILE)
        synchronized(cacheLock) {
            applied?.takeIf { it.choice == selected && it.legacyFile == legacy }?.let { return it.prepared.typeface }
        }
        return runCatching {
            val generation = synchronized(cacheLock) { generations[selected] ?: 0L }
            val loaded = prepare(context, selected, false, generation)
            val unchanged = choice(context) == selected && context.getPrefString(FILE) == legacy
            synchronized(cacheLock) {
                if (unchanged) applied = AppliedFont(selected, legacy, loaded)
            }
            loaded.typeface
        }.getOrDefault(Typeface.DEFAULT)
    }

    private fun prepare(context: Context, choice: String, reload: Boolean, generation: Long): PreparedInterfaceFont {
        if (choice in systemChoices) return PreparedInterfaceFont(systemTypeface(choice), choice, null, choice, "", generation)
        val legacy = context.getPrefString(FILE).takeIf { choice == context.getPrefString(CHOICE) }
        if (!reload && choice == context.getPrefString(CHOICE)) synchronized(cacheLock) {
            applied?.takeIf { it.choice == choice && it.legacyFile == legacy }?.let { return it.prepared }
        }
        // Existing installations retain their explicit old FILE until the user reselects a source.
        val source = if (!reload && legacy != null && File(legacy).isFile) File(legacy).toURI().toString() else choice
        val file = sourceFile(source)
        val key = SourceKey(source, file?.let { "${it.lastModified()}:${it.length()}" }.orEmpty())
        if (!reload) synchronized(cacheLock) {
            cache.get(key)?.takeIf { it.choice == choice && it.generation == generation && (file == null || file.isFile) }?.let { return it }
        }
        val before = fingerprint(context, source)
        val typeface = decode(context, source)
        check(fingerprint(context, source) == before) { "字体读取期间发生变化，请重试" }
        return PreparedInterfaceFont(typeface, choice, file, source, before, generation).also { prepared ->
            synchronized(cacheLock) {
                if ((generations[choice] ?: 0L) == generation) cache.put(key, prepared)
            }
        }
    }

    private fun decode(context: Context, source: String): Typeface {
        sourceFile(source)?.let { file ->
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireNotNull(Typeface.Builder(file).build()) { "字体文件无法解析" }
            } else Typeface.createFromFile(file)
        }
        val uri = Uri.parse(source)
        require(uri.scheme == "content") { "不支持的字体来源" }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireNotNull(context.contentResolver.openFileDescriptor(uri, "r")) { "无法读取字体" }.use {
                requireNotNull(Typeface.Builder(it.fileDescriptor).build()) { "字体文件无法解析" }
            }
        } else {
            // AndroidX's public API handles API 21-25 descriptors; any internal temporary file is removed.
            requireNotNull(FontsContractCompat.buildTypeface(context, null,
                arrayOf(FontsContractCompat.FontInfo(uri, 0, 400, false, 0)))) { "字体文件无法解析" }
        }
    }

    private fun sourceFile(source: String): File? {
        val uri = Uri.parse(source)
        return when (uri.scheme) {
            null -> File(source)
            "file" -> File(requireNotNull(uri.path) { "字体路径无效" })
            else -> null
        }
    }

    private fun fingerprint(context: Context, source: String): String {
        val input: InputStream = sourceFile(source)?.inputStream()
            ?: requireNotNull(context.contentResolver.openInputStream(Uri.parse(source))) { "无法读取字体" }
        return input.use {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = ByteArray(32 * 1024)
            val header = ByteArray(4)
            var headerSize = 0
            var total = 0L
            while (true) {
                val n = input.read(bytes)
                if (n < 0) break
                total += n
                require(total <= MAX_BYTES) { "字体文件超过64MB" }
                if (headerSize < 4) {
                    val count = minOf(4 - headerSize, n)
                    bytes.copyInto(header, headerSize, 0, count)
                    headerSize += count
                }
                digest.update(bytes, 0, n)
            }
            require(headerSize == 4 && (header.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                header.toString(Charsets.US_ASCII) in setOf("OTTO", "true", "ttcf"))) { "字体文件无效" }
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
    }

    fun save(context: Context, choice: String, name: String, prepared: PreparedInterfaceFont?) {
        saveIfCurrent(context, choice, name, prepared) { apply -> apply(); true }
    }

    fun saveIfCurrent(
        context: Context,
        choice: String,
        name: String,
        prepared: PreparedInterfaceFont?,
        commit: (() -> Unit) -> Boolean,
    ): Boolean {
        val selected = if (choice in systemChoices) null else requireNotNull(prepared) { "请等待字体加载完成" }.also {
            require(it.choice == choice && it.source == choice) { "请重新选择字体" }
            check(fingerprint(context, choice) == it.fingerprint) { "字体已变化，请重新选择" }
        }
        return synchronized(FontLibraryStore.lock) {
            selected?.file?.let { check(it.isFile) { "字体已删除，请重新选择" } }
            val preferences = context.defaultSharedPreferences.edit().putString(NAME, name)
                .putString(CHOICE, choice).remove(FILE)
            // Failed or out-of-date loads cannot publish to either preferences or the active cache.
            synchronized(cacheLock) inner@{
                if (selected != null && (generations[choice] ?: 0L) != selected.generation) return@inner false
                commit {
                    preferences.apply()
                    applied = selected?.let { AppliedFont(choice, null, it) }
                    appliedChanges.update { it + 1 }
                }
            }
        }
    }
}
