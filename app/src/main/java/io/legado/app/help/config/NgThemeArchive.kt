package io.legado.app.help.config

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.JsonObject
import io.legado.app.constant.PreferKey
import io.legado.app.help.plugin.CompiledPackageInspector
import io.legado.app.utils.GSON
import io.legado.app.utils.defaultSharedPreferences
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Immutable inspected candidate. Inspection never constructs a renderer. */
internal class NgPreparedThemeImport internal constructor(
    internal val file: File,
    internal val manifest: NgThemePackageManifest,
    val previous: NgManagedTheme?,
    val sha256: String,
    val action: NgThemeImportAction,
) {
    val kind: NgEffectPackageKind get() = NgEffectPackageKind.fromFormat(manifest.format)
    val theme: NgManagedTheme get() = manifest.theme
    val sizeBytes: Long get() = file.length()
    val hasCustomizations: Boolean = previous?.let {
        it.isCustomized || (kind == NgEffectPackageKind.THEME && it.publication?.installedFingerprint == null)
    } == true
    val incompatibleParameters: List<String> get() = previous?.effectValues.orEmpty().keys.filter { key ->
        val value = previous?.effectValues?.get(key)
        theme.effect?.parameterSchema?.firstOrNull { it.key == key }?.let { value != null && it.accepts(value) } != true
    }
    private var consumed = false

    fun readPreview(): ByteArray? = theme.effect?.preview?.let { name ->
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(name) ?: return@use null
            require(entry.size in 1..(2L * 1024 * 1024))
            zip.getInputStream(entry).use { it.readBytes() }
        }
    }

    fun discard() = synchronized(NgThemeArchive.installLock) { if (!consumed) file.parentFile?.deleteRecursively(); Unit }

    fun commit(context: Context, allowReplace: Boolean = false, allowDowngrade: Boolean = false,
        keepCustomizations: Boolean = true): NgManagedTheme =
        commit(context, allowReplace, allowDowngrade, keepCustomizations, true)

    internal fun commit(context: Context, allowReplace: Boolean, allowDowngrade: Boolean,
        keepCustomizations: Boolean, saveToLibrary: Boolean): NgManagedTheme =
        synchronized(NgThemeArchive.installLock) {
            check(!consumed && file.isFile) { "导入预览已失效，请重新选择文件" }
            kind.requireContent(theme)
            if (saveToLibrary && theme.id.isNotEmpty()) {
                check(NgEffectPackageLibrary.current(context, kind).savedThemes.firstOrNull { it.id == theme.id } == previous) {
                    "主题已变化，请重新导入确认"
                }
            }
            if (action == NgThemeImportAction.NO_OP) return@synchronized requireNotNull(previous)
            check(action != NgThemeImportAction.REPLACE || allowReplace) { "请明确确认替换主题" }
            check(action != NgThemeImportAction.DOWNGRADE || allowDowngrade) { "请明确确认降级主题" }
            check(!keepCustomizations || !hasCustomizations || incompatibleParameters.isEmpty()) {
                "新版本不支持现有参数，请选择恢复默认"
            }
            check(EffectThemeRuntime.sha256(file) == sha256) { "待导入文件已变化" }
            val root = File(File(context.filesDir, kind.directory), UUID.randomUUID().toString())
            var createdAlbums = emptyList<String>()
            try {
                NgThemeArchive.extract(file, root)
                NgThemeArchive.copyReadOnly(file, File(root, NgThemePackageManager.ARCHIVE_NAME))
                var imported = NgThemePackageManager.materializeTheme(
                    root, manifest,
                    id = theme.id.ifEmpty { "${NgThemePackageManager.ANONYMOUS_ID_PREFIX}${UUID.randomUUID()}" },
                    name = if (kind == NgEffectPackageKind.THEME) {
                        previous?.name ?: NgEffectPackageLibrary.uniqueName(context, kind, theme.name)
                    } else theme.name,
                )
                val publication = theme.publication?.copy(
                    sha256 = sha256, source = "local", sourceId = null, installedFingerprint = null,
                )
                val customizations = previous?.takeIf { hasCustomizations && keepCustomizations }
                imported = imported.copy(publication = publication, ownedCoverAlbumIds = null)
                if (customizations == null) {
                    val albums = manifest.coverAlbums?.takeIf { it.isNotEmpty() }?.let {
                        NgCoverAlbumStore.importFromPackage(context, root, imported.name, it)
                    }.orEmpty()
                    createdAlbums = albums.map { it.id }
                    val covers = imported.coverProfile
                    val selectedRef = manifest.selectedCoverAlbumRef
                    val profile = when {
                        selectedRef != null -> requireNotNull(covers).copy(
                            albumId = albums.first { it.ref == selectedRef }.id,
                        )
                        covers?.albumId != null && NgCoverAlbumStore.current(context).albums.none { it.id == covers.albumId } ->
                            covers.copy(applyAlbumSelection = false, albumId = null)
                        else -> covers
                    }
                    imported = imported.copy(coverProfile = profile, ownedCoverAlbumIds = createdAlbums.takeIf { it.isNotEmpty() })
                }
                val baselineFingerprint = imported.customizationFingerprint()
                if (customizations != null) {
                    imported = NgThemeArchive.keepCustomization(context, customizations, imported)
                }
                imported = imported.copy(publication = publication?.copy(installedFingerprint = baselineFingerprint))
                val saved = if (saveToLibrary) {
                    NgEffectPackageLibrary.saveResource(context, kind, imported, previous)
                } else imported
                consumed = true
                file.parentFile?.deleteRecursively()
                discardPreviousPackage(context)
                saved
            } catch (error: Throwable) {
                if (!consumed && NgEffectPackageLibrary.current(context, kind).savedThemes.none {
                        it.packageRootPath == root.absolutePath
                    }) {
                    runCatching { NgCoverAlbumStore.removeImported(context, createdAlbums) }
                    root.deleteRecursively()
                }
                throw error
            }
        }

    internal fun discardPreviousPackage(context: Context) {
        previous?.let { NgThemeArchive.releaseThemeAssets(context, it, kind) }
    }
}

internal object NgThemeArchive {
    val installLock = Any()
    const val MAX_PACKAGE_BYTES = 64L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 128L * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024

    fun hasMaterializedAssets(theme: NgManagedTheme): Boolean {
        if (theme.effect == null) return true
        val root = theme.packageRootPath?.let(::File) ?: return false
        return listOf(theme.lightBackground.path, theme.darkBackground.path).all {
            it != null && File(it).isFile
        } && theme.effect.preview?.let { isSafeThemeAssetPath(it) && File(root, it).isFile } == true
    }

    fun inspect(file: File): NgThemePackageManifest {
        require(file.length() in 1..MAX_PACKAGE_BYTES) { "主题包为空或过大" }
        return ZipFile(file).use { zip ->
            val names = hashSetOf<String>()
            var count = 0
            var total = 0L
            zip.entries().asSequence().forEach { entry ->
                require(++count <= 512) { "主题包文件过多" }
                val name = if (entry.isDirectory) entry.name.removeSuffix("/") else entry.name
                require(isSafeThemeAssetPath(name) && names.add(name)) { "主题包路径无效或重复" }
                require(entry.name != NgThemePackageManager.ARCHIVE_NAME && !entry.name.startsWith("lib/") &&
                    !entry.name.endsWith(".so", true)) { "主题包包含不支持的文件" }
                require(entry.size in 0..MAX_ENTRY_BYTES) { "主题包文件过大" }
                if (!entry.isDirectory) {
                    var readBytes = 0L
                    val crc = CRC32()
                    zip.getInputStream(entry).use { stream ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = stream.read(buffer)
                            if (read < 0) break
                            readBytes += read; total += read
                            require(readBytes <= MAX_ENTRY_BYTES && total <= MAX_TOTAL_BYTES) { "主题解压内容过大" }
                            crc.update(buffer, 0, read)
                        }
                    }
                    require(readBytes == entry.size && crc.value == entry.crc) { "主题包文件校验失败" }
                }
            }
            val manifest = readManifest(zip)
            val id = manifest.theme.id
            val kind = NgEffectPackageKind.fromFormat(manifest.format)
            require(manifest.version in 1..2) { "不支持的资源包版本" }
            val theme = manifest.theme
            kind.requireContent(theme)
            require(theme.schemaVersion == NG_MANAGED_THEME_SCHEMA_VERSION && theme.name.trim().length in 1..120 &&
                !theme.isBuiltIn && (kind == NgEffectPackageKind.THEME || id.isNotEmpty())) { "主题信息无效" }
            theme.normalized()
            fun asset(path: String?, required: Boolean = false) {
                if (path == null) { require(!required) { "特效主题缺少静态备用外观" }; return }
                require(isSafeThemeAssetPath(path) && zip.getEntry(path)?.let { !it.isDirectory && it.size > 0 } == true) {
                    "主题素材不存在或路径无效"
                }
            }
            asset(manifest.lightBackgroundAsset, theme.effect != null)
            asset(manifest.darkBackgroundAsset, theme.effect != null)
            asset(manifest.lightDrawerImageAsset)
            asset(manifest.darkDrawerImageAsset)
            val coverRefs = hashSetOf<String>()
            manifest.coverAlbums.orEmpty().forEach { album ->
                require(album.ref.isNotBlank() && coverRefs.add(album.ref)) { "封面图集引用无效或重复" }
                val images = album.lightImages + album.darkImages
                require(images.isNotEmpty()) { "封面图集缺少图片" }
                images.forEach { path ->
                    require(path.startsWith("assets/ng-cover-albums/") &&
                        path !in listOf(manifest.lightBackgroundAsset, manifest.darkBackgroundAsset,
                            manifest.lightDrawerImageAsset, manifest.darkDrawerImageAsset, theme.effect?.preview)
                    ) { "封面图集资源路径无效" }
                    asset(path, required = true)
                }
            }
            manifest.selectedCoverAlbumRef?.let { ref ->
                require(theme.coverProfile?.applyAlbumSelection == true && ref in coverRefs) { "所选封面图集无效" }
            }
            theme.effect?.let { effect ->
                require(manifest.version == 2 &&
                    (id.isEmpty() || Regex("[a-z][A-Za-z0-9._-]{0,127}").matches(id))) { "特效主题身份无效" }
                effect.validate()
                val publication = requireNotNull(theme.publication) { "特效主题缺少发布版本" }
                require(publication.versionCode > 0 && publication.versionName.length in 1..64 &&
                    publication.author.length <= 128) { "特效主题发布信息无效" }
                require(effect.preview != null) { "特效主题缺少静态预览" }
                asset(effect.preview, true)
                val preview = requireNotNull(zip.getEntry(effect.preview))
                require(preview.size <= 2L * 1024 * 1024) { "主题预览过大" }
                listOfNotNull(manifest.lightBackgroundAsset, manifest.darkBackgroundAsset).distinct().forEach { path ->
                    val fallbackBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    zip.getInputStream(zip.getEntry(path)).use { BitmapFactory.decodeStream(it, null, fallbackBounds) }
                    require(fallbackBounds.outWidth in 1..8192 && fallbackBounds.outHeight in 1..8192 &&
                        fallbackBounds.outWidth.toLong() * fallbackBounds.outHeight <= 32L * 1024 * 1024) { "静态备用外观无效" }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                zip.getInputStream(preview).use { BitmapFactory.decodeStream(it, null, bounds) }
                require(bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048) { "主题预览无效" }
                require(theme.effectValues.all { (key, value) ->
                    effect.parameterSchema.firstOrNull { it.key == key }?.accepts(value) == true
                }) { "特效主题参数值无效" }
                CompiledPackageInspector.validate(zip, effect.entryClass,
                    "Lio/legado/effect/api/EffectThemePlugin;", "Lio/legado/effect/api/")
            }
            if (theme.effect == null) require(names.none { Regex("classes(?:[0-9]+)?\\.dex").matches(it) }) {
                "普通主题不能携带未声明的执行代码"
            }
            manifest
        }
    }

    internal fun readManifest(zip: ZipFile): NgThemePackageManifest {
        val entry = zip.getEntry("manifest.json") ?: error("主题包缺少 manifest.json")
        require(entry.size in 1..(1024L * 1024)) { "主题清单过大或为空" }
        val json = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use {
            GSON.fromJson(it, JsonObject::class.java)
        } ?: error("主题清单无效")
        val theme = json.getAsJsonObject("theme") ?: error("主题信息无效")
        val id = theme.get("id")?.takeUnless { it.isJsonNull }?.let {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "主题 ID 无效" }
            it.asString.trim()
        }.orEmpty()
        require(!id.startsWith(NgThemePackageManager.ANONYMOUS_ID_PREFIX)) { "主题 ID 无效" }
        theme.addProperty("id", id)
        if (theme.get("effectValues")?.isJsonNull != false) theme.add("effectValues", JsonObject())
        return GSON.fromJson(json, NgThemePackageManifest::class.java)
    }

    fun copyUri(context: Context, uri: Uri): File {
        val directory = File(context.cacheDir, "ng_theme_import/${UUID.randomUUID()}").apply { mkdirs() }
        val target = File(directory, "candidate.zip")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("无法读取主题包")
            input.use { source ->
                FileOutputStream(target).use { output ->
                    check(target.setReadOnly()) { "无法保护主题代码文件" }
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_PACKAGE_BYTES) { "主题包过大" }
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
            return target
        } catch (error: Throwable) { directory.deleteRecursively(); throw error }
    }

    fun copyReadOnly(source: File, target: File) {
        target.parentFile?.mkdirs()
        source.inputStream().use { input ->
            FileOutputStream(target).use { output ->
                check(target.setReadOnly()) { "无法保护主题代码文件" }
                input.copyTo(output)
                output.fd.sync()
            }
        }
        check(source.length() == target.length() && EffectThemeRuntime.sha256(source) == EffectThemeRuntime.sha256(target)) {
            "主题包复制校验失败"
        }
    }

    fun extract(file: File, root: File) {
        check(root.mkdirs() || root.isDirectory)
        ZipFile(file).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (!entry.isDirectory && !Regex("classes(?:[0-9]+)?\\.dex").matches(entry.name)) {
                    require(isSafeThemeAssetPath(entry.name))
                    val target = File(root, entry.name).canonicalFile
                    require(target.toPath().startsWith(root.canonicalFile.toPath()))
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output); output.fd.sync() }
                    }
                }
            }
        }
    }

    internal fun releaseThemeAssets(context: Context, theme: NgManagedTheme, kind: NgEffectPackageKind) =
        synchronized(installLock) {
            val remaining = NgEffectPackageLibrary.current(context, kind).savedThemes
            if (!theme.ownedCoverAlbumIds.isNullOrEmpty()) runCatching {
                val selectedAlbum = NgCoverAlbumStore.current(context).selectedAlbumId
                val orphaned = orphanedCoverAlbumIds(theme, remaining).filterNot { it == selectedAlbum }
                NgCoverAlbumStore.removeImported(context, orphaned)
            }
            val rootPath = theme.packageRootPath ?: return@synchronized
            runCatching {
                if (remaining.any { it.packageRootPath == rootPath }) return@runCatching
                if (kind == NgEffectPackageKind.THEME) {
                    val root = File(rootPath).canonicalFile.toPath()
                    fun inOldPackage(path: String?): Boolean =
                        path != null && File(path).canonicalFile.toPath().startsWith(root)

                    val prefs = context.defaultSharedPreferences
                    val drawer = NgDrawerProfileStore.current(context)
                    val livePaths = listOf(prefs.getString(PreferKey.bgImage, null),
                        prefs.getString(PreferKey.bgImageN, null), drawer?.light?.imagePath, drawer?.dark?.imagePath)
                    if (livePaths.any(::inOldPackage) || remaining.any { saved ->
                            listOf(saved.lightBackground.path, saved.darkBackground.path,
                                saved.drawerProfile?.light?.imagePath, saved.drawerProfile?.dark?.imagePath).any(::inOldPackage)
                        }) return@runCatching
                }
                // Runtime ZIP leases preserve open readers after unlinking the old package.
                deleteOwnedRoot(context, rootPath, kind)
            }
            Unit
        }

    fun deleteOwnedRoot(context: Context, path: String, kind: NgEffectPackageKind = NgEffectPackageKind.THEME) {
        val root = File(path).canonicalFile
        val owner = File(context.filesDir, kind.directory).canonicalFile
        if (root.parentFile == owner && root.isDirectory) root.deleteRecursively()
    }

    fun keepCustomization(context: Context, old: NgManagedTheme, incoming: NgManagedTheme): NgManagedTheme {
        val root = File(requireNotNull(incoming.packageRootPath))
        fun copyImage(path: String?, role: String): String? {
            if (path.isNullOrBlank()) return null
            val target = File(root, "assets/custom-$role.${NgThemePackageManager.backgroundExtension(path)}")
            target.parentFile?.mkdirs()
            NgThemePackageManager.openThemeAsset(context, path).use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            return target.absolutePath
        }
        val drawer = old.drawerProfile?.let { profile ->
            profile.copy(
                light = profile.light.copy(imagePath = copyImage(profile.light.imagePath, "drawer-light")),
                dark = profile.dark.copy(imagePath = copyImage(profile.dark.imagePath, "drawer-dark")),
            )
        }
        return incoming.copy(
            name = old.name, colors = old.colors, transparentAppBars = old.transparentAppBars,
            barProfile = old.barProfile, coverProfile = old.coverProfile, ownedCoverAlbumIds = old.ownedCoverAlbumIds,
            lightBackground = old.lightBackground.copy(path = copyImage(old.lightBackground.path, "light")),
            darkBackground = old.darkBackground.copy(path = copyImage(old.darkBackground.path, "dark")),
            centerCropBackground = old.centerCropBackground,
            drawerProfile = drawer, effectValues = old.effectValues,
        )
    }
}
