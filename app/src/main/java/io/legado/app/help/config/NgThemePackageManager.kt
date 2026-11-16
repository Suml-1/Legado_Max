package io.legado.app.help.config

import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Keep
internal data class NgThemePackageManifest(
    @SerializedName("format") val format: String,
    @SerializedName("version") val version: Int,
    @SerializedName("theme") val theme: NgManagedTheme,
    @SerializedName("lightBackgroundAsset") val lightBackgroundAsset: String? = null,
    @SerializedName("darkBackgroundAsset") val darkBackgroundAsset: String? = null,
    @SerializedName("lightDrawerImageAsset") val lightDrawerImageAsset: String? = null,
    @SerializedName("darkDrawerImageAsset") val darkDrawerImageAsset: String? = null,
    @SerializedName("coverAlbums") val coverAlbums: List<NgCoverAlbumImport>? = null,
    @SerializedName("selectedCoverAlbumRef") val selectedCoverAlbumRef: String? = null,
)

/** Reading NG 自有主题包；完整保留可选栏配置，MD3/旧 JSON 由兼容层按字段映射。 */
internal object NgThemePackageManager {

    const val PACKAGE_DIR = "ng_theme_packages"
    internal const val ARCHIVE_NAME = "package.zip"
    // Generated for packages without an update ID and never written to a portable manifest.
    internal const val ANONYMOUS_ID_PREFIX = "local.anonymous."
    private const val MANIFEST_NAME = "manifest.json"
    private const val VERSION = 1

    suspend fun inspectTheme(context: Context, uri: Uri,
        kind: NgEffectPackageKind = NgEffectPackageKind.THEME,
        findPrevious: Boolean = true): Result<NgPreparedThemeImport> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = NgThemeArchive.copyUri(context, uri)
                try {
                    val manifest = inspectArchive(file)
                    require(NgEffectPackageKind.fromFormat(manifest.format) == kind) {
                        if (kind == NgEffectPackageKind.THEME) "听书特效已内置，请在播放器的播放动效中选择" else "请选择听书特效 ZIP，主题包请在主题管理导入"
                    }
                    val hash = EffectThemeRuntime.sha256(file)
                    val theme = manifest.theme
                    val previous = if (findPrevious && theme.id.isNotEmpty()) {
                        NgEffectPackageLibrary.current(context, kind).savedThemes.firstOrNull { it.id == theme.id }
                    } else null
                    require(previous == null || previous.isEffect == theme.isEffect) { "同一 ID 的主题类型不一致" }
                    val action = if (kind == NgEffectPackageKind.LISTENING_EFFECT && previous != null &&
                        previous.publication?.sha256 == hash && NgThemeArchive.hasMaterializedAssets(previous) &&
                        EffectThemeRuntime.archive(previous)?.let { it.isFile && EffectThemeRuntime.sha256(it) == hash } == true
                    ) NgThemeImportAction.NO_OP else themeImportAction(theme, previous)
                    NgPreparedThemeImport(file, manifest, previous, hash, action)
                } catch (error: Throwable) {
                    file.parentFile?.deleteRecursively()
                    throw error
                }
            }
        }

    internal fun inspectArchive(file: File): NgThemePackageManifest = NgThemeArchive.inspect(file)

    internal fun themeImportAction(theme: NgManagedTheme, previous: NgManagedTheme?): NgThemeImportAction {
        if (previous == null) return NgThemeImportAction.INSTALL
        val incomingVersion = theme.publication?.versionCode
        val installedVersion = previous.publication?.versionCode
        return when {
            incomingVersion == null || installedVersion == null -> NgThemeImportAction.REPLACE
            incomingVersion > installedVersion -> NgThemeImportAction.UPDATE
            incomingVersion < installedVersion -> NgThemeImportAction.DOWNGRADE
            else -> NgThemeImportAction.REPLACE
        }
    }

    suspend fun importTheme(context: Context, uri: Uri, saveToLibrary: Boolean = true,
        kind: NgEffectPackageKind = NgEffectPackageKind.THEME): Result<NgManagedTheme> =
        withContext(Dispatchers.IO) {
            runCatching {
                val candidate = inspectTheme(context, uri, kind, findPrevious = saveToLibrary).getOrThrow()
                try {
                    candidate.commit(context, allowReplace = false, allowDowngrade = false,
                        keepCustomizations = true, saveToLibrary = saveToLibrary)
                } finally { candidate.discard() }
            }
        }

    suspend fun exportTheme(context: Context, theme: NgManagedTheme, uri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("无法创建主题包")
                output.use { writeTheme(context, theme, it) }
            }
        }

    /** Copies the compiled payload unchanged while writing this theme's effective configuration. */
    internal fun writeTheme(context: Context, theme: NgManagedTheme, output: java.io.OutputStream) {
        val kind = NgEffectPackageKind.of(theme)
        kind.requireContent(theme)
        val root = theme.packageRootPath?.let(::File)?.canonicalFile
        val archive = EffectThemeRuntime.archive(theme)?.takeIf(File::isFile)?.let(::ZipFile)
        if (theme.effect != null) require(archive != null) { "特效主题代码包缺失" }
        try {
            val backgrounds = linkedMapOf<String, ThemeAsset>()
            fun register(path: String?, role: String): String? {
                if (path.isNullOrBlank()) return null
                backgrounds.values.firstOrNull { it.source == path }?.let { return it.entryName }
                val entry = if (!path.startsWith("asset://") && root != null && File(path).canonicalFile.toPath().startsWith(root.toPath())) {
                    File(path).canonicalFile.relativeTo(root).invariantSeparatorsPath
                } else "assets/background-$role.${backgroundExtension(path)}"
                require(isSafeThemeAssetPath(entry))
                backgrounds[entry] = ThemeAsset(path, entry)
                return entry
            }
            val light = register(theme.lightBackground.path, "light")
            val dark = register(theme.darkBackground.path, "dark")
            val drawer = theme.drawerProfile?.normalized()
            val lightDrawer = register(drawer?.light?.imagePath, "drawer-light")
            val darkDrawer = register(drawer?.dark?.imagePath, "drawer-dark")
            val selectedAlbumId = theme.coverProfile?.takeIf { it.applyAlbumSelection }?.albumId
            val albumIds = (theme.ownedCoverAlbumIds.orEmpty() + listOfNotNull(selectedAlbumId)).distinct()
            val coverAssets = linkedMapOf<String, File>()
            val albumRefs = mutableMapOf<String, String>()
            val coverAlbums = if (albumIds.isEmpty()) emptyList() else {
                val albums = NgCoverAlbumStore.current(context).albums.associateBy { it.id }
                val assetRoot = "assets/ng-cover-albums/${UUID.randomUUID()}"
                albumIds.mapNotNull(albums::get).mapIndexed { index, album ->
                    val ref = "album-${index + 1}"
                    albumRefs[album.id] = ref
                    fun assets(paths: List<String>, role: String): List<String> = paths.mapIndexed { imageIndex, path ->
                        val source = NgCoverAlbumStore.packageImage(context, album.id, path)
                        val asset = "$assetRoot/$ref/$role-${imageIndex + 1}.${backgroundExtension(source.name)}"
                        coverAssets[asset] = source
                        asset
                    }
                    NgCoverAlbumImport(ref, album.name, assets(album.lightImages, "day"), assets(album.darkImages, "night"))
                }
            }
            val selectedAlbumRef = selectedAlbumId?.let {
                requireNotNull(albumRefs[it]) { "主题所选封面图集已不存在" }
            }
            val portable = theme.copy(
                packageRootPath = null,
                lightBackground = theme.lightBackground.copy(path = null),
                darkBackground = theme.darkBackground.copy(path = null),
                drawerProfile = drawer?.withoutImagePaths(),
                coverProfile = theme.coverProfile?.copy(albumId = null),
                ownedCoverAlbumIds = null,
                publication = theme.publication?.copy(sha256 = null, installedFingerprint = null,
                    derived = theme.publication.derived || theme.isCustomized),
            )
            val manifest = NgThemePackageManifest(kind.format, if (theme.effect == null) VERSION else 2,
                portable, light, dark, lightDrawer, darkDrawer,
                coverAlbums = coverAlbums.takeIf { it.isNotEmpty() }, selectedCoverAlbumRef = selectedAlbumRef)
            val previousCoverAssets = archive?.let(NgThemeArchive::readManifest)?.coverAlbums.orEmpty()
                .flatMapTo(hashSetOf()) { it.lightImages + it.darkImages }
            val manifestJson = GSON.toJsonTree(manifest).asJsonObject
            if (theme.id.isEmpty() || theme.id.startsWith(ANONYMOUS_ID_PREFIX)) {
                manifestJson.getAsJsonObject("theme").remove("id")
            }
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                val written = hashSetOf(MANIFEST_NAME)
                zip.putNextEntry(ZipEntry(MANIFEST_NAME))
                zip.write(GSON.toJson(manifestJson).toByteArray(Charsets.UTF_8)); zip.closeEntry()
                archive?.entries()?.asSequence()?.filter {
                    !it.isDirectory && it.name != MANIFEST_NAME && it.name !in backgrounds && it.name !in previousCoverAssets
                }?.forEach { entry ->
                    require(isSafeThemeAssetPath(entry.name) && written.add(entry.name))
                    zip.putNextEntry(ZipEntry(entry.name))
                    archive.getInputStream(entry).use { it.copyTo(zip) }; zip.closeEntry()
                }
                // Locally saved themes can own assets without an imported source ZIP.
                if (archive == null && root != null) root.walkTopDown().filter { it.isFile }.forEach { file ->
                    val name = file.relativeTo(root).invariantSeparatorsPath
                    if (name != ARCHIVE_NAME && name !in backgrounds && written.add(name)) {
                        require(isSafeThemeAssetPath(name))
                        zip.putNextEntry(ZipEntry(name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                }
                backgrounds.values.forEach { asset ->
                    require(written.add(asset.entryName))
                    zip.putNextEntry(ZipEntry(asset.entryName))
                    openThemeAsset(context, asset.source).use { it.copyTo(zip) }; zip.closeEntry()
                }
                coverAssets.forEach { (name, file) ->
                    require(written.add(name))
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } finally { archive?.close() }
    }

    internal fun materializeTheme(root: File, manifest: NgThemePackageManifest, id: String, name: String): NgManagedTheme =
        manifest.theme.copy(id = id, name = name, packageRootPath = root.absolutePath,
            lightBackground = manifest.theme.lightBackground.copy(path = resolveInstalledAsset(root, manifest.lightBackgroundAsset)),
            darkBackground = manifest.theme.darkBackground.copy(path = resolveInstalledAsset(root, manifest.darkBackgroundAsset)),
            drawerProfile = resolveInstalledDrawerProfile(root, manifest),
        ).normalized()

    /** Save-as owns every file; removing the original theme cannot invalidate the copy. */
    internal fun copyOwnedTheme(context: Context, theme: NgManagedTheme): NgManagedTheme {
        val old = theme.packageRootPath?.let(::File)?.canonicalFile ?: error("特效主题包缺失")
        val kind = NgEffectPackageKind.of(theme)
        require(old.isDirectory && old.parentFile == File(context.filesDir, kind.directory).canonicalFile)
        val copied = File(File(context.filesDir, kind.directory), UUID.randomUUID().toString())
        try {
            check(copied.mkdirs())
            old.walkTopDown().filter { it.isFile }.forEach { source ->
                val target = File(copied, source.relativeTo(old).invariantSeparatorsPath)
                if (source.name == ARCHIVE_NAME) NgThemeArchive.copyReadOnly(source, target)
                else { target.parentFile?.mkdirs(); source.copyTo(target) }
            }
            fun remap(path: String?): String? = path?.let { source ->
                if (source.startsWith("asset://")) source else File(source).canonicalFile.let { file ->
                    if (file.toPath().startsWith(old.toPath())) File(copied, file.relativeTo(old).path).absolutePath else source
                }
            }
            return theme.copy(packageRootPath = copied.absolutePath,
                lightBackground = theme.lightBackground.copy(path = remap(theme.lightBackground.path)),
                darkBackground = theme.darkBackground.copy(path = remap(theme.darkBackground.path)),
                drawerProfile = theme.drawerProfile?.let { drawer -> drawer.copy(
                    light = drawer.light.copy(imagePath = remap(drawer.light.imagePath)),
                    dark = drawer.dark.copy(imagePath = remap(drawer.dark.imagePath))) },
                publication = theme.publication?.copy(derived = true),
            )
        } catch (error: Throwable) { copied.deleteRecursively(); throw error }
    }

    private fun resolveInstalledAsset(root: File, relativePath: String?): String? {
        relativePath ?: return null
        require(isSafeThemeAssetPath(relativePath)) { "主题包包含非法资源路径" }
        val canonicalRoot = root.canonicalFile
        val file = File(canonicalRoot, relativePath).canonicalFile
        require(file.toPath().startsWith(canonicalRoot.toPath()) && file.isFile) {
            "主题包背景资源不存在"
        }
        return file.absolutePath
    }

    private fun NgThemeDrawerProfile.withoutImagePaths(): NgThemeDrawerProfile = copy(
        light = light.copy(imagePath = null),
        dark = dark.copy(imagePath = null),
    )

    private fun resolveInstalledDrawerProfile(
        root: File,
        manifest: NgThemePackageManifest,
    ): NgThemeDrawerProfile? {
        val profile = manifest.theme.drawerProfile?.normalized() ?: return null
        fun resolve(path: String?): String? = resolveInstalledAsset(root, path)?.also {
            NgThemeDrawerAssets.validateImage(File(it))
        }
        // Only manifest-owned package assets are accepted; embedded local paths are not trusted.
        return profile.copy(
            light = profile.light.copy(imagePath = resolve(manifest.lightDrawerImageAsset)),
            dark = profile.dark.copy(imagePath = resolve(manifest.darkDrawerImageAsset)),
        )
    }

    internal fun openThemeAsset(context: Context, source: String) = when {
        source.startsWith("asset://") -> context.assets.open(source.removePrefix("asset://"))
        else -> File(source).inputStream()
    }

    internal fun backgroundExtension(path: String): String {
        val source = path.substringBefore('?').lowercase()
        return when {
            source.endsWith(".9.png") -> "9.png"
            source.endsWith(".png") -> "png"
            source.endsWith(".webp") -> "webp"
            source.endsWith(".gif") -> "gif"
            source.endsWith(".jpeg") -> "jpeg"
            else -> "jpg"
        }
    }

    private data class ThemeAsset(val source: String, val entryName: String)
}

internal fun isSafeThemeAssetPath(path: String): Boolean {
    if (path.isBlank() || path.startsWith('/') || ':' in path || '\\' in path) return false
    return path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
}
