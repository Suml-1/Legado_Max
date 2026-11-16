package io.legado.app.help.config

import android.app.ActivityManager
import android.content.Context
import dalvik.system.DexClassLoader
import io.legado.effect.api.EffectThemeAssets
import io.legado.effect.api.EffectThemePlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.concurrent.ConcurrentHashMap

internal class EffectThemeAssetLease(private val archive: ZipFile) : EffectThemeAssets, Closeable {
    override fun open(path: String): InputStream {
        require(isSafeThemeAssetPath(path)) { "特效主题素材路径无效" }
        return archive.getInputStream(archive.getEntry("assets/$path") ?: error("特效主题素材缺失"))
    }
    override fun close() = archive.close()
}

internal class EffectThemeHandle(val plugin: EffectThemePlugin, val file: File, val sha256: String)

private class EffectApiParent(private val host: ClassLoader) : ClassLoader(Context::class.java.classLoader) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> =
        if (name.startsWith("io.legado.effect.api.")) host.loadClass(name) else super.loadClass(name, resolve)
}

/** No classes are defined by status()/inspection. Only explicit rendering calls load(). */
internal object EffectThemeRuntime {
    private data class Loaded(val identity: String, val handle: EffectThemeHandle)
    private val loaded = mutableMapOf<String, Loaded>()
    private val failures = mutableMapOf<String, String>()
    private val generations = ConcurrentHashMap<String, Long>()
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    fun revisionOf(themeId: String, target: String = "app"): Long = generations[NgEffectPackageKind.runtimeKey(themeId, target)] ?: 0L

    @Synchronized
    fun invalidate(themeId: String, target: String = "app") {
        val key = NgEffectPackageKind.runtimeKey(themeId, target)
        loaded.remove(key)
        failures.remove(key)
        generations.merge(key, 1L) { previous, increment -> previous + increment }
        mutableRevision.value += 1
    }

    @Synchronized
    fun reportFailure(themeId: String, stage: String, message: String, target: String = "app") {
        val key = NgEffectPackageKind.runtimeKey(themeId, target)
        loaded.remove(key)
        failures[key] = "${stage.take(40)}: ${message.take(500)}"
        // Do not publish a new revision: a failed renderer must not restart itself forever.
    }

    @Synchronized
    fun failureMessage(themeId: String, target: String = "app"): String? = failures[NgEffectPackageKind.runtimeKey(themeId, target)]

    fun archive(theme: NgManagedTheme): File? =
        theme.packageRootPath?.let { File(it, NgThemePackageManager.ARCHIVE_NAME) }

    fun isIntact(theme: NgManagedTheme): Boolean = try {
        val effect = requireNotNull(theme.effect)
        effect.validate()
        val file = requireNotNull(archive(theme))
        require(file.isFile && NgThemeArchive.hasMaterializedAssets(theme) && sha256(file) == theme.publication?.sha256)
        val manifest = NgThemePackageManager.inspectArchive(file)
        require(manifest.format == NgEffectPackageKind.of(theme).format && manifest.theme.effect?.targets == effect.targets &&
            manifest.theme.effect?.entryClass == effect.entryClass && manifest.theme.effect?.apiVersion == effect.apiVersion)
        true
    } catch (_: Exception) { false }

    fun status(context: Context, theme: NgManagedTheme, target: String = "app"): NgEffectThemeStatus {
        val effect = theme.effect ?: return NgEffectThemeStatus.INCOMPATIBLE
        if (effect.apiVersion != 1 || effect.targets != listOf(target)) return NgEffectThemeStatus.INCOMPATIBLE
        if (archive(theme)?.isFile != true || !NgThemeArchive.hasMaterializedAssets(theme)) return NgEffectThemeStatus.MISSING
        if (!isIntact(theme)) return NgEffectThemeStatus.CORRUPT
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (target !in effect.targets || manager != null &&
            (manager.deviceConfigurationInfo.reqGlEsVersion < effect.minGlesVersion ||
                manager.isLowRamDevice && !effect.lowRamAllowed)) return NgEffectThemeStatus.UNSUPPORTED
        return if (failureMessage(theme.id, target) != null) NgEffectThemeStatus.LOAD_ERROR else NgEffectThemeStatus.READY
    }

    @Synchronized
    fun load(context: Context, theme: NgManagedTheme, target: String = "app"): EffectThemeHandle? {
        val effect = theme.effect ?: return null
        if (effect.targets != listOf(target)) return null
        val cacheKey = NgEffectPackageKind.runtimeKey(theme.id, target)
        val file = archive(theme) ?: return null
        val identity = "${file.absolutePath}|${theme.publication?.sha256}|${effect.entryClass}"
        loaded[cacheKey]?.takeIf { it.identity == identity }?.let { return it.handle }
        if (failures.containsKey(cacheKey)) return null
        return try {
            require(effect.apiVersion == 1 && file.isFile && NgThemeArchive.hasMaterializedAssets(theme) && sha256(file) == theme.publication?.sha256) {
                "特效主题文件缺失或损坏"
            }
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            require(manager == null || manager.deviceConfigurationInfo.reqGlEsVersion >= effect.minGlesVersion &&
                (!manager.isLowRamDevice || effect.lowRamAllowed)) { "此设备不支持该特效主题" }
            require(isIntact(theme)) { "资源包用途或内容不匹配" }
            check(!file.canWrite() || file.setReadOnly()) { "无法保护特效主题代码" }
            val optimized = File(context.codeCacheDir, "effect-themes/$target").apply { mkdirs() }
            val parent = EffectApiParent(EffectThemePlugin::class.java.classLoader!!)
            val loader = DexClassLoader(file.absolutePath, optimized.absolutePath, null, parent)
            val type = loader.loadClass(effect.entryClass)
            require(EffectThemePlugin::class.java.isAssignableFrom(type)) { "特效主题入口不兼容" }
            val plugin = type.getDeclaredConstructor().newInstance() as EffectThemePlugin
            EffectThemeHandle(plugin, file, requireNotNull(theme.publication?.sha256)).also {
                loaded[cacheKey] = Loaded(identity, it)
            }
        } catch (error: Throwable) {
            rethrowFatal(error)
            reportFailure(theme.id, "load", error.message ?: error.javaClass.simpleName, target)
            null
        }
    }

    fun assets(theme: NgManagedTheme): EffectThemeAssetLease =
        EffectThemeAssetLease(ZipFile(requireNotNull(archive(theme)) { "主题包缺失" }))

    fun parameters(theme: NgManagedTheme, overrides: Map<String, String> = emptyMap()): Map<String, String> =
        theme.effect?.parameterSchema.orEmpty().associate { parameter ->
            parameter.key to (overrides[parameter.key]?.takeIf(parameter::accepts)
                ?: theme.effectValues[parameter.key]?.takeIf(parameter::accepts)
                ?: parameter.defaultValue)
        }

    fun diagnostics(context: Context, theme: NgManagedTheme): String = buildString {
        appendLine("主题：${theme.name}")
        appendLine("ID：${theme.id}")
        appendLine("版本：${theme.publication?.versionName ?: "-"} (${theme.publication?.versionCode ?: 0})")
        appendLine("接口：${theme.effect?.apiVersion ?: 0}")
        appendLine("位置：${theme.effect?.targets.orEmpty().joinToString()}")
        appendLine("SHA-256：${theme.publication?.sha256 ?: "-"}")
        appendLine("状态：${status(context, theme, theme.effect?.targets?.firstOrNull() ?: "app").label}")
        failureMessage(theme.id, theme.effect?.targets?.singleOrNull() ?: "app")?.let { appendLine(it) }
    }

    private fun rethrowFatal(error: Throwable) {
        when (error) {
            is VirtualMachineError, is ThreadDeath -> throw error
            is java.lang.reflect.InvocationTargetException -> rethrowFatal(error.targetException)
            is ExceptionInInitializerError -> error.exception?.let(::rethrowFatal)
        }
    }

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
