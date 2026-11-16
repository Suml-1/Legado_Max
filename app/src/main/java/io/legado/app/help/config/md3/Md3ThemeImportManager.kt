package io.legado.app.help.config.md3

import android.content.Context
import android.net.Uri
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.NgCoverAlbumImport
import io.legado.app.help.config.NgCoverAlbumStore
import io.legado.app.help.config.NgManagedTheme
import io.legado.app.help.config.NgThemeBarProfile
import io.legado.app.help.config.NgThemeCoverProfile
import io.legado.app.help.config.NgThemeBackground
import io.legado.app.help.config.NgThemeLibraryStore
import io.legado.app.help.config.NgThemeNavigationAssets
import io.legado.app.help.config.NgThemePackageManager
import io.legado.app.help.config.NgThemeResourceProfile
import io.legado.app.ui.design.theme.NgColorGenerationMode
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgColorSystem
import io.legado.app.ui.design.theme.NgManualColorSet
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.design.theme.NgTopBarTextMode
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

internal data class Md3ThemeImportDraft(
    val preview: NgThemePackagePreview,
    val theme: NgManagedTheme,
    val warnings: List<String>,
)

internal data class Md3ThemeInstallResult(
    val theme: NgManagedTheme,
    val profile: NgThemePackageSpec,
    val coverAlbumIds: List<String>,
)

/**
 * MD3/旧版主题包的预览与事务式安装边界。
 *
 * 预览不写文件和偏好；安装只在用户确认后执行。外部主题包提供颜色、背景、字体、
 * 功能封面及显式声明的 Dock Profile；未声明的 NG 栏字段保持用户现有配置。
 */
internal object Md3ThemeImportManager {

    private const val PROFILE_NAME = "ng-md3-profile.json"
    private const val MAX_ENTRY_COUNT = 4096
    private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 512L * 1024 * 1024
    private const val DEFAULT_SEED = 0xFF6750A4.toInt()

    suspend fun preview(context: Context, uri: Uri): Result<Md3ThemeImportDraft> =
        withContext(Dispatchers.IO) {
            runCatching {
                val inspection = Md3ThemePackageInspector.inspect(context, uri).getOrThrow()
                val normalized = Md3ThemePackageNormalizer.normalize(inspection)
                materializeDraft(context.applicationContext, normalized, packageRoot = null)
            }
        }

    suspend fun install(
        context: Context,
        uri: Uri,
        applyAfterInstall: Boolean,
    ): Result<Md3ThemeInstallResult> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = context.applicationContext
            val inspection = Md3ThemePackageInspector.inspect(appContext, uri).getOrThrow()
            val normalized = Md3ThemePackageNormalizer.normalize(inspection)
            val packageParent = File(appContext.filesDir, NgThemePackageManager.PACKAGE_DIR)
                .apply { mkdirs() }
            val stagingRoot = File(packageParent, ".staging-${UUID.randomUUID()}")
            val installedRoot = File(packageParent, UUID.randomUUID().toString())
            var libraryThemeId: String? = null
            var importedCoverAlbumIds = emptyList<String>()
            try {
                extract(appContext, uri, stagingRoot)
                File(stagingRoot, PROFILE_NAME).writeText(GSON.toJson(normalized.spec))
                moveIntoPlace(stagingRoot, installedRoot)
                val draft = materializeDraft(appContext, normalized, installedRoot)
                val importedCoverAlbums = NgCoverAlbumStore.importFromPackage(
                    context = appContext,
                    packageRoot = installedRoot,
                    sourceThemeName = normalized.spec.name,
                    imports = normalized.spec.coverAlbums.map { album ->
                        NgCoverAlbumImport(
                            ref = album.ref,
                            name = album.name,
                            lightImages = album.lightImages.map { it.path },
                            darkImages = album.darkImages.map { it.path },
                        )
                    },
                )
                importedCoverAlbumIds = importedCoverAlbums.map { it.id }
                val importedCoverAlbumIdsByRef = importedCoverAlbums.associate { it.ref to it.id }
                val installed = NgThemeLibraryStore.addOrReplace(
                    appContext,
                    draft.theme.copy(
                        id = "local.${UUID.randomUUID()}",
                        name = NgThemeLibraryStore.uniqueName(appContext, draft.theme.name),
                        packageRootPath = installedRoot.absolutePath,
                        coverProfile = materializeCoverProfile(
                            normalized.spec,
                            importedCoverAlbumIdsByRef,
                        ),
                        ownedCoverAlbumIds = importedCoverAlbumIds,
                    )
                )
                libraryThemeId = installed.id
                if (applyAfterInstall) NgThemeLibraryStore.apply(appContext, installed)
                Md3ThemeInstallResult(installed, normalized.spec, importedCoverAlbumIds)
            } catch (error: Throwable) {
                libraryThemeId?.let { NgThemeLibraryStore.remove(appContext, it) }
                runCatching {
                    NgCoverAlbumStore.removeImported(appContext, importedCoverAlbumIds)
                }
                installedRoot.deleteRecursively()
                throw error
            } finally {
                stagingRoot.deleteRecursively()
            }
        }
    }

    fun readInstalledProfile(theme: NgManagedTheme): NgThemePackageSpec? {
        val root = theme.packageRootPath?.let(::File)?.canonicalFile ?: return null
        val profile = File(root, PROFILE_NAME).canonicalFile
        if (!profile.toPath().startsWith(root.toPath()) || !profile.isFile) return null
        return runCatching {
            GSON.fromJson(profile.readText(), NgThemePackageSpec::class.java)
                ?.takeIf { it.schemaVersion == NgThemePackageSpec.SCHEMA_VERSION }
        }.getOrNull()
    }

    internal fun materializeCoverProfile(
        spec: NgThemePackageSpec,
        importedAlbumIdsByRef: Map<String, String>,
    ): NgThemeCoverProfile? {
        val coverFields = listOf(
            "coverLoadOnlyWifi",
            "coverUseDefault",
            "coverShowName",
            "coverShowAuthor",
            "coverShowNameN",
            "coverShowAuthorN",
        )
        val selectedRef = spec.coverSelection.albumRef
        if (selectedRef == null && coverFields.none(spec.normalizedFields::containsKey)) {
            return null
        }
        val selectedAlbumId = selectedRef?.let { ref ->
            requireNotNull(importedAlbumIdsByRef[ref]) {
                "主题包选择的封面图集未成功安装: $ref"
            }
        }
        return NgThemeCoverProfile(
            applyAlbumSelection = selectedRef != null,
            albumId = selectedAlbumId,
            loadOnlyWifi = spec.booleanField("coverLoadOnlyWifi"),
            useDefault = spec.booleanField("coverUseDefault"),
            showName = spec.booleanField("coverShowName"),
            showAuthor = spec.booleanField("coverShowAuthor"),
            showNameDark = spec.booleanField("coverShowNameN"),
            showAuthorDark = spec.booleanField("coverShowAuthorN"),
        )
    }

    internal fun materializeBarProfile(spec: NgThemePackageSpec): NgThemeBarProfile? {
        val directFields = listOf(
            "useFloatingBottomBar",
            "floatingBottomBarBottomDistancePx",
            "floatingBottomBarTransparency",
            "bookshelfTopBarStyle",
            "bookshelfFloatingDockTopDistancePx",
            "bookshelfFloatingDockTransparency",
            "topBarOpacity",
            "bottomBarOpacity",
        )
        if (directFields.none(spec.normalizedFields::containsKey)) return null
        val bottomTransparency = spec.intField("floatingBottomBarTransparency")
            ?: spec.intField("bottomBarOpacity")?.let { 100 - it.coerceIn(0, 100) }
        val topTransparency = spec.intField("bookshelfFloatingDockTransparency")
            ?: spec.intField("topBarOpacity")?.let { 100 - it.coerceIn(0, 100) }
        return NgThemeBarProfile(
            useFloatingBottomBar = spec.booleanField("useFloatingBottomBar"),
            floatingBottomBarBottomDistancePx =
                spec.intField("floatingBottomBarBottomDistancePx"),
            floatingBottomBarTransparency = bottomTransparency,
            bookshelfTopBarStyle = spec.topBarStyleField("bookshelfTopBarStyle"),
            bookshelfFloatingDockTopDistancePx =
                spec.intField("bookshelfFloatingDockTopDistancePx"),
            bookshelfFloatingDockTransparency = topTransparency,
        ).normalized()
    }

    private fun NgThemePackageSpec.booleanField(name: String): Boolean? =
        normalizedFields[name]?.let { value ->
            runCatching { GSON.fromJson(value, Boolean::class.java) }.getOrNull()
        }

    private fun NgThemePackageSpec.intField(name: String): Int? =
        normalizedFields[name]?.let { value ->
            runCatching { GSON.fromJson(value, Int::class.java) }.getOrNull()
        }

    private fun NgThemePackageSpec.topBarStyleField(name: String): Int? {
        val value = normalizedFields[name] ?: return null
        runCatching { GSON.fromJson(value, Int::class.java) }.getOrNull()?.let {
            return io.legado.app.help.config.BookshelfTopBarStyle.fromValue(it).value
        }
        val text = runCatching { GSON.fromJson(value, String::class.java) }
            .getOrNull()
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: return null
        return when (text) {
            "1", "floating", "floating_dock", "dock" -> 1
            "0", "traditional" -> 0
            else -> null
        }
    }

    private fun materializeDraft(
        context: Context,
        preview: NgThemePackagePreview,
        packageRoot: File?,
    ): Md3ThemeImportDraft {
        val profile = preview.spec.colorProfile
        val warnings = preview.spec.warnings.toMutableList()
        val light = materializeAppearance(context, profile, isDark = false, warnings)
        val dark = materializeAppearance(context, profile, isDark = true, warnings)
        val lightSeed = profile.light.seed ?: profile.light.manual?.primary ?: DEFAULT_SEED
        val darkSeed = profile.dark.seed ?: profile.dark.manual?.primary ?: DEFAULT_SEED
        val colors = NgColorSystem(
            mode = NgColorGenerationMode.MANUAL,
            lightSeed = lightSeed,
            darkSeed = darkSeed,
            paletteStyle = profile.paletteStyle,
            contrast = profile.contrast,
            colorSpec = profile.colorSpec,
            manualLight = light,
            manualDark = dark,
            lightTopBarTextMode = NgTopBarTextMode.AUTO,
            darkTopBarTextMode = NgTopBarTextMode.AUTO,
        ).normalized()
        val backgrounds = preview.spec.backgroundProfile
        val resources = preview.spec.resources
        val theme = NgManagedTheme(
            id = "preview.md3",
            name = preview.spec.name,
            colors = colors,
            lightBackground = NgThemeBackground(
                path = resolveInstalledAsset(packageRoot, backgrounds.light.archivePath),
                blur = backgrounds.light.blur,
            ),
            darkBackground = NgThemeBackground(
                path = resolveInstalledAsset(packageRoot, backgrounds.dark.archivePath),
                blur = backgrounds.dark.blur,
            ),
            transparentAppBars = true,
            barProfile = materializeBarProfile(preview.spec),
            packageRootPath = packageRoot?.absolutePath,
            resourceProfile = NgThemeResourceProfile(
                navigation = NgThemeNavigationAssets(
                    home = resources[Md3ThemeAssetSlots.NAVIGATION_HOME],
                    bookshelf = resources[Md3ThemeAssetSlots.NAVIGATION_BOOKSHELF],
                    explore = resources[Md3ThemeAssetSlots.NAVIGATION_EXPLORE],
                    rss = resources[Md3ThemeAssetSlots.NAVIGATION_RSS],
                    my = resources[Md3ThemeAssetSlots.NAVIGATION_MY],
                ),
                appFont = resources[Md3ThemeAssetSlots.FONT_APP],
            ),
        ).normalized()
        return Md3ThemeImportDraft(
            preview = preview,
            theme = theme,
            warnings = warnings.distinct(),
        )
    }

    private fun materializeAppearance(
        context: Context,
        profile: NgThemePackageColorProfile,
        isDark: Boolean,
        warnings: MutableList<String>,
    ): NgManualColorSet {
        val appearance = if (isDark) profile.dark else profile.light
        val manual = appearance.manual
        val seed = appearance.seed ?: manual?.primary ?: DEFAULT_SEED
        if (appearance.source != NgThemePackageColorSource.MANUAL && appearance.seed == null) {
            warnings += "${if (isDark) "夜间" else "日间"}配色未携带可复现种子，已使用 NG 默认种子"
        }
        val placeholders = NgManualColorSet(
            primary = seed,
            secondary = seed,
            primaryText = 0xFF000000.toInt(),
            secondaryText = 0xFF000000.toInt(),
            background = if (isDark) 0xFF121212.toInt() else 0xFFF5F5F5.toInt(),
            labelContainer = if (isDark) 0xFF1E1E1E.toInt() else 0xFFEEEEEE.toInt(),
        )
        val generated = NgThemeResolver.resolveColorScheme(
            context = context,
            colors = NgColorSystem(
                mode = NgColorGenerationMode.PALETTE,
                lightSeed = seed,
                darkSeed = seed,
                paletteStyle = profile.paletteStyle,
                contrast = profile.contrast,
                colorSpec = profile.colorSpec,
                manualLight = placeholders,
                manualDark = placeholders,
            ),
            isDark = isDark,
        )
        val background = manual?.background
            ?: if (isDark && profile.pureBlack) 0xFF000000.toInt() else generated.background
        return NgManualColorSet(
            primary = NgColorMath.opaque(manual?.primary ?: generated.primary),
            secondary = NgColorMath.opaque(manual?.secondary ?: generated.topBarContainer),
            primaryText = NgColorMath.opaque(manual?.primaryText ?: generated.onBackground),
            secondaryText = NgColorMath.opaque(manual?.secondaryText ?: generated.onSurface),
            background = NgColorMath.opaque(background),
            labelContainer = NgColorMath.opaque(manual?.labelContainer ?: generated.surface),
        )
    }

    private fun resolveInstalledAsset(root: File?, relativePath: String?): String? {
        if (root == null || relativePath.isNullOrBlank()) return null
        val canonicalRoot = root.canonicalFile
        val target = File(canonicalRoot, relativePath).canonicalFile
        return target.absolutePath.takeIf {
            target.toPath().startsWith(canonicalRoot.toPath()) && target.isFile
        }
    }

    private fun extract(context: Context, uri: Uri, root: File) {
        root.mkdirs()
        val canonicalRoot = root.canonicalFile
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取主题包")
        val names = hashSetOf<String>()
        var entryCount = 0
        var totalBytes = 0L
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ENTRY_COUNT) { "主题包文件数量过多" }
                val name = entry.name.replace('\\', '/')
                require(name.isNotBlank() && !name.startsWith('/')) { "主题包包含异常路径" }
                require(names.add(name.lowercase(Locale.ROOT))) { "主题包包含重名路径" }
                val target = File(canonicalRoot, name).canonicalFile
                require(target.toPath().startsWith(canonicalRoot.toPath())) { "主题包包含越界路径" }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    var entryBytes = 0L
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            entryBytes += read
                            totalBytes += read
                            require(entryBytes <= MAX_ENTRY_BYTES) { "主题包单个文件过大" }
                            require(totalBytes <= MAX_TOTAL_BYTES) { "主题包解压后体积过大" }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun moveIntoPlace(stagingRoot: File, installedRoot: File) {
        installedRoot.parentFile?.mkdirs()
        runCatching {
            Files.move(
                stagingRoot.toPath(),
                installedRoot.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            Files.move(stagingRoot.toPath(), installedRoot.toPath())
        }
    }
}
