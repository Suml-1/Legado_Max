package io.legado.app.help.config

import android.content.Context
import android.graphics.Color
import androidx.annotation.Keep
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.help.DefaultData
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.R
import splitties.init.appCtx
import java.util.UUID
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

/**
 * 应用主题管理器。
 *
 * 将主题、顶栏、底栏、封面图集四个维度的配置组合为一个
 * 「应用主题」整体方案，支持一键切换、导出/导入 zip 包。
 *
 * 导出的 zip 包含 application_theme.json 清单文件和所有
 * 关联资源（背景图、壁纸、图标、封面图片）。
 * 导入时会自动解压资源、创建对应的子配置项。
 *
 * 配置存储在 filesDir/applicationThemes.json 中，单文件最大 5MB。
 */
object ApplicationThemeManager {

    private const val fileName = "applicationThemes.json"
    private const val currentIdKey = "currentApplicationThemeId"
    private const val maxConfigBytes = 5L * 1024 * 1024
    internal const val maxManifestBytes = 2L * 1024 * 1024
    private const val maxAssetBytes = 64L * 1024 * 1024
    internal const val maxCoverImages = 500
    private val filePath = FileUtils.getPath(appCtx.filesDir, fileName)

    /** 内置默认应用主题的固定 ID，不落盘，不可删除、不可重命名、不可编辑，始终显示在列表首位 */
    const val builtinThemeId = "builtin_default_app_theme"

    /** 内置默认应用主题引用的 assets 默认阅读主题名（日间/夜间） */
    private const val builtinDayThemeName = "蓝煤灰"
    private const val builtinNightThemeName = "黑白"

    /**
     * 内置默认应用主题：日间用「蓝煤灰」、夜间用「黑白」，顶栏/底栏取默认内置配置，封面图集不设置。
     * 日夜间子主题取 assets 默认阅读主题的拷贝，找不到同名时回退到该模式的第一个默认主题。
     * 注意：深拷贝的前提是 [ThemeConfig.Config] 只含原始类型/String 字段；若日后增加集合等
     * 可变引用字段，[builtinConfigCopy] 需要同步加深拷贝层级。
     */
    private val builtinConfig: Config by lazy {
        val defaults = DefaultData.themeConfigs
        val navConfigs = NavigationBarConfig.loadConfigs(appCtx)
        Config(
            id = builtinThemeId,
            dayTheme = (
                defaults.firstOrNull { !it.isNightTheme && it.themeName == builtinDayThemeName }
                    ?: defaults.firstOrNull { !it.isNightTheme }
                )?.copy(backgroundImgPath = null),
            nightTheme = (
                defaults.firstOrNull { it.isNightTheme && it.themeName == builtinNightThemeName }
                    ?: defaults.firstOrNull { it.isNightTheme }
                )?.copy(backgroundImgPath = null),
            dayBottomBarId = navConfigs.firstOrNull { !it.isNight && it.isBuiltin }?.id,
            nightBottomBarId = navConfigs.firstOrNull { it.isNight && it.isBuiltin }?.id,
            updatedAt = 0L,
        )
    }

    /**
     * 深拷贝内置默认主题，避免列表项与共享实例互相影响可变字段；
     * 名称现场读取，语言切换后无需重启进程即可更新显示名。
     */
    private fun builtinConfigCopy(): Config = builtinConfig.copy(
        name = appCtx.getString(R.string.application_theme_builtin_name),
        dayTheme = builtinConfig.dayTheme?.copy(),
        nightTheme = builtinConfig.nightTheme?.copy(),
    )

    /**
     * 应用主题配置数据类。
     *
     * 将日间/夜间两套子配置组合在一起。
     *
     * @property id 唯一标识符
     * @property name 主题名称
     * @property dayTheme 日间主题配置（[ThemeConfig.Config]），null 表示不设置
     * @property nightTheme 夜间主题配置，null 表示不设置
     * @property dayTopBarDir 日间顶栏配置包目录名
     * @property nightTopBarDir 夜间顶栏配置包目录名
     * @property dayBottomBarId 日间底栏配置 ID
     * @property nightBottomBarId 夜间底栏配置 ID
     * @property dayCoverGroupId 日间封面图集组 ID
     * @property nightCoverGroupId 夜间封面图集组 ID
     * @property updatedAt 最后更新时间戳
     */
    @Keep
    data class Config(
        val id: String = UUID.randomUUID().toString(),
        var name: String = "",
        var dayTheme: ThemeConfig.Config? = null,
        var nightTheme: ThemeConfig.Config? = null,
        var dayTopBarDir: String = TopBarConfig.DEFAULT_DIR_NAME,
        var nightTopBarDir: String = TopBarConfig.DEFAULT_DIR_NAME,
        var dayBottomBarId: String? = null,
        var nightBottomBarId: String? = null,
        var dayCoverGroupId: Long? = null,
        var nightCoverGroupId: Long? = null,
        var updatedAt: Long = System.currentTimeMillis(),
    )

    /** zip 包内部数据结构，包含清单和各子配置的打包数据 */
    @Keep
    private data class PackageData(
        val version: Int = 1,
        val config: Config,
        val dayTopBar: TopBarConfig.Config? = null,
        val nightTopBar: TopBarConfig.Config? = null,
        val dayBottomBar: NavigationBarConfig? = null,
        val nightBottomBar: NavigationBarConfig? = null,
        val dayCover: CoverPayload? = null,
        val nightCover: CoverPayload? = null,
    )

    /** zip 包内封面图集的载荷数据 */
    @Keep
    private data class CoverPayload(
        val name: String,
        val images: List<String>,
    )

    /**
     * 主题导出格式枚举。
     *
     * 支持将当前分支的应用主题导出为以下格式：
     * - [NATIVE]：当前分支原生格式（application_theme.json）
     * - [ARCHIVE]：archive_primate_beta 分支格式（appearance_kit.json）
     * - [MD3]：MD3-main 分支格式（manifest.json）
     * - [RED]：Reeden 阅读 App 格式（theme.json + RED 头）
     */
    enum class ExportFormat {
        NATIVE,
        ARCHIVE,
        MD3,
        RED,
    }

    /** 导入应用主题时的可选创建范围，日夜间可独立控制 */
    @Keep
    data class ImportOptions(
        val importDayTheme: Boolean = true,
        val importNightTheme: Boolean = true,
        val importDayTopBar: Boolean = true,
        val importNightTopBar: Boolean = true,
        val importDayBottomBar: Boolean = true,
        val importNightBottomBar: Boolean = true,
        val importDayCover: Boolean = true,
        val importNightCover: Boolean = true,
    )

    /** 删除应用主题时的可选范围，控制是否一并删除关联组件，默认全部不勾选 */
    @Keep
    data class DeleteOptions(
        val deleteDayTheme: Boolean = false,
        val deleteNightTheme: Boolean = false,
        val deleteDayTopBar: Boolean = false,
        val deleteNightTopBar: Boolean = false,
        val deleteDayBottomBar: Boolean = false,
        val deleteNightBottomBar: Boolean = false,
        val deleteDayCover: Boolean = false,
        val deleteNightCover: Boolean = false,
    )

    /** 将导入选项持久化到 SharedPreferences */
    fun saveImportOptions(context: Context, options: ImportOptions) {
        context.putPrefBoolean(PreferKey.appThemeImportDayTheme, options.importDayTheme)
        context.putPrefBoolean(PreferKey.appThemeImportNightTheme, options.importNightTheme)
        context.putPrefBoolean(PreferKey.appThemeImportDayTopBar, options.importDayTopBar)
        context.putPrefBoolean(PreferKey.appThemeImportNightTopBar, options.importNightTopBar)
        context.putPrefBoolean(PreferKey.appThemeImportDayBottomBar, options.importDayBottomBar)
        context.putPrefBoolean(PreferKey.appThemeImportNightBottomBar, options.importNightBottomBar)
        context.putPrefBoolean(PreferKey.appThemeImportDayCover, options.importDayCover)
        context.putPrefBoolean(PreferKey.appThemeImportNightCover, options.importNightCover)
    }

    /** 从 SharedPreferences 读取持久化的导入选项，默认全部为 true */
    fun getImportOptions(context: Context): ImportOptions = ImportOptions(
        importDayTheme = context.getPrefBoolean(PreferKey.appThemeImportDayTheme, true),
        importNightTheme = context.getPrefBoolean(PreferKey.appThemeImportNightTheme, true),
        importDayTopBar = context.getPrefBoolean(PreferKey.appThemeImportDayTopBar, true),
        importNightTopBar = context.getPrefBoolean(PreferKey.appThemeImportNightTopBar, true),
        importDayBottomBar = context.getPrefBoolean(PreferKey.appThemeImportDayBottomBar, true),
        importNightBottomBar = context.getPrefBoolean(PreferKey.appThemeImportNightBottomBar, true),
        importDayCover = context.getPrefBoolean(PreferKey.appThemeImportDayCover, true),
        importNightCover = context.getPrefBoolean(PreferKey.appThemeImportNightCover, true),
    )

    /** 将删除选项持久化到 SharedPreferences */
    fun saveDeleteOptions(context: Context, options: DeleteOptions) {
        context.putPrefBoolean(PreferKey.appThemeDeleteDayTheme, options.deleteDayTheme)
        context.putPrefBoolean(PreferKey.appThemeDeleteNightTheme, options.deleteNightTheme)
        context.putPrefBoolean(PreferKey.appThemeDeleteDayTopBar, options.deleteDayTopBar)
        context.putPrefBoolean(PreferKey.appThemeDeleteNightTopBar, options.deleteNightTopBar)
        context.putPrefBoolean(PreferKey.appThemeDeleteDayBottomBar, options.deleteDayBottomBar)
        context.putPrefBoolean(PreferKey.appThemeDeleteNightBottomBar, options.deleteNightBottomBar)
        context.putPrefBoolean(PreferKey.appThemeDeleteDayCover, options.deleteDayCover)
        context.putPrefBoolean(PreferKey.appThemeDeleteNightCover, options.deleteNightCover)
    }

    /** 从 SharedPreferences 读取持久化的删除选项，默认全部为 false */
    fun getDeleteOptions(context: Context): DeleteOptions = DeleteOptions(
        deleteDayTheme = context.getPrefBoolean(PreferKey.appThemeDeleteDayTheme, false),
        deleteNightTheme = context.getPrefBoolean(PreferKey.appThemeDeleteNightTheme, false),
        deleteDayTopBar = context.getPrefBoolean(PreferKey.appThemeDeleteDayTopBar, false),
        deleteNightTopBar = context.getPrefBoolean(PreferKey.appThemeDeleteNightTopBar, false),
        deleteDayBottomBar = context.getPrefBoolean(PreferKey.appThemeDeleteDayBottomBar, false),
        deleteNightBottomBar = context.getPrefBoolean(PreferKey.appThemeDeleteNightBottomBar, false),
        deleteDayCover = context.getPrefBoolean(PreferKey.appThemeDeleteDayCover, false),
        deleteNightCover = context.getPrefBoolean(PreferKey.appThemeDeleteNightCover, false),
    )

    /** 从文件加载所有应用主题配置（内置默认主题始终排在首位且不落盘），自动校验大小和格式 */
    fun load(): MutableList<Config> {
        val items = loadPersisted()
        items.add(0, builtinConfigCopy())
        return items
    }

    /** 判断是否为内置默认应用主题 */
    fun isBuiltin(config: Config): Boolean = config.id == builtinThemeId

    /** 从文件加载用户持久化的应用主题配置，不含内置默认主题 */
    private fun loadPersisted(): MutableList<Config> {
        val file = File(filePath)
        if (!file.isFile) return mutableListOf()
        require(file.length() <= maxConfigBytes) { appCtx.getString(R.string.app_theme_config_too_large) }
        val parsed = GSON.fromJsonArray<Config>(file.readText()).getOrElse {
            throw IllegalStateException(appCtx.getString(R.string.app_theme_config_corrupted), it)
        }
        return parsed.map { sanitize(it) }
            // 防御历史落盘数据中残留的内置 ID，避免 load() 出现重复的内置项
            .filterNot { it.id == builtinThemeId }
            .toMutableList()
    }

    /** 获取当前激活的应用主题 ID */
    fun currentId(context: Context): String = context.getPrefString(currentIdKey).orEmpty()

    /** 按 ID 查找配置 */
    fun find(id: String): Config? = load().firstOrNull { it.id == id }

    /** 导出当前应用主题为 zip 文件（含所有关联资源） */
    fun exportCurrent(context: Context, format: ExportFormat = ExportFormat.NATIVE): File {
        val current = (
            load().firstOrNull { isCurrent(context, it) }
                ?: captureCurrent(context, appCtx.getString(io.legado.app.R.string.application_theme_manage))
            )
            .let { config ->
                // 从 SharedPreferences 刷新背景图路径，确保导出的是当前实际使用的背景图。
                // 用户可能通过「主题设置」偏好页面修改了背景图，而已保存的应用主题配置中
                // 的 backgroundImgPath 可能仍是旧值。
                config.copy(
                    dayTheme = config.dayTheme?.copy(
                        backgroundImgPath = context.getPrefString(PreferKey.bgImage),
                    ),
                    nightTheme = config.nightTheme?.copy(
                        backgroundImgPath = context.getPrefString(PreferKey.bgImageN),
                    ),
                )
            }
        if (format == ExportFormat.NATIVE) validateForApply(context, current)
        return exportConfig(context, current, format)
    }

    /** 导出指定应用主题为 zip 文件（含所有关联资源） */
    fun exportConfig(context: Context, config: Config, format: ExportFormat = ExportFormat.NATIVE): File = when (format) {
        ExportFormat.NATIVE -> exportNative(context, config)
        ExportFormat.ARCHIVE -> ThemeExporter.exportArchive(context, config)
        ExportFormat.MD3 -> ThemeExporter.exportMd3(context, config)
        ExportFormat.RED -> ThemeExporter.exportRed(context, config)
    }

    /** 导出当前分支原生格式（application_theme.json 清单 + 所有资源） */
    private fun exportNative(context: Context, config: Config): File {
        val dir = appCtx.cacheDir.resolve("applicationThemeExports").apply { mkdirs() }
        val exportName = config.name.normalizeFileName().ifBlank { "application_theme" }
        return dir.resolve("$exportName.zip").apply {
            ZipOutputStream(outputStream().buffered()).use { zip ->
                val packagedConfig = config.copy(
                    dayTheme = packageTheme(zip, config.dayTheme, "themes/day"),
                    nightTheme = packageTheme(zip, config.nightTheme, "themes/night"),
                )
                val data = PackageData(
                    config = packagedConfig,
                    dayTopBar = packageTopBar(zip, context, false, config.dayTopBarDir, "topbar/day"),
                    nightTopBar = packageTopBar(zip, context, true, config.nightTopBarDir, "topbar/night"),
                    dayBottomBar = packageBottomBar(zip, context, false, config.dayBottomBarId, "bottombar/day"),
                    nightBottomBar = packageBottomBar(zip, context, true, config.nightBottomBarId, "bottombar/night"),
                    dayCover = packageCover(zip, config.dayCoverGroupId, "covers/day"),
                    nightCover = packageCover(zip, config.nightCoverGroupId, "covers/night"),
                )
                zip.putNextEntry(ZipEntry("application_theme.json"))
                zip.write(GSON.toJson(data).toByteArray())
                zip.closeEntry()
            }
        }
    }

    /**
     * 导入应用主题文件。
     * 支持 zip（含资源）和纯 json（仅配置）两种格式。
     * 自动处理名称冲突，添加数字后缀。
     * @param options 导入选项，控制是否创建各子配置；null 表示全部创建
     */
    suspend fun importFile(file: File, options: ImportOptions? = null): Config {
        // 检测 .red 格式（Reeden 阅读 App 主题包，RED 头 + ZIP 数据），剥离头部后作为 ZIP 处理
        val zipFile = RedAssetPackage.zipPayload(file, RedAssetPackage.tempDir())
        if (zipFile != null) {
            val result = importZip(zipFile, options)
            // 如果创建了临时文件（非原文件），导入完成后删除
            if (zipFile != file) zipFile.delete()
            return result
        }
        // 检测 GZIP 类 .red 格式（RED 头 + GZIP 或纯 GZIP），解压后作为 JSON 处理
        val gzipJson = RedAssetPackage.gzipJsonPayload(file)
        if (gzipJson != null) {
            return RedThemeImporter.importGzipJson(gzipJson, options)
        }
        // 检测 RED10 加密私有格式，给出明确提示而非误导性的"文件过大"
        if (RedAssetPackage.detectFormat(file) == RedAssetPackage.RedFormat.RED10_PRIVATE) {
            throw IllegalArgumentException(appCtx.getString(R.string.app_theme_encrypted_not_supported))
        }
        // 不是 ZIP 也不是 .red，尝试作为纯 JSON 导入
        require(file.length() <= maxManifestBytes) { appCtx.getString(R.string.app_theme_file_too_large) }
        val imported = sanitize(
            GSON.fromJson(file.readText(), Config::class.java)
                ?: throw IllegalArgumentException("Invalid application theme"),
        )
        return addImported(stripComponents(imported, options))
    }

    private suspend fun importZip(file: File, options: ImportOptions?): Config {
        val temp = appCtx.cacheDir.resolve("applicationThemeImport/${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            ZipFile(file).use { zip ->
                // 检测 zip 包内的清单文件类型，兼容四种格式
                val manifestEntry = zip.getEntry("application_theme.json")
                val appearanceKitEntry = zip.entries().asSequence()
                    .firstOrNull { !it.isDirectory && (it.name == "appearance_kit.json" || it.name.endsWith("/appearance_kit.json")) }
                val md3ManifestEntry = zip.getEntry("manifest.json")
                // .red 格式的清单文件（Reeden 阅读 App 的主题包）
                val redThemeEntry = zip.getEntry("theme.json")

                when {
                    // 当前分支格式
                    manifestEntry != null -> return importNativeFormat(zip, temp, manifestEntry, options)
                    // archive_primate_beta 分支格式（AppearanceKit）
                    appearanceKitEntry != null -> return AppearanceKitImporter.import(zip, temp, appearanceKitEntry, options)
                    // MD3-main 分支格式
                    md3ManifestEntry != null -> return Md3ThemeImporter.import(zip, temp, md3ManifestEntry, options)
                    // .red 格式（Reeden 阅读 App 主题包）
                    redThemeEntry != null -> return RedThemeImporter.import(zip, temp, redThemeEntry, options)
                    else -> throw IllegalArgumentException(appCtx.getString(R.string.app_theme_missing_manifest))
                }
            }
        } finally {
            temp.deleteRecursively()
        }
    }

    /** 导入当前分支的原生格式（application_theme.json） */
    private suspend fun importNativeFormat(
        zip: ZipFile,
        temp: File,
        manifestEntry: ZipEntry,
        options: ImportOptions?,
    ): Config {
        require(manifestEntry.size in 0..maxManifestBytes) { appCtx.getString(R.string.app_theme_manifest_too_large) }
        val data = zip.getInputStream(manifestEntry).bufferedReader().use {
            GSON.fromJson(it, PackageData::class.java)
        } ?: throw IllegalArgumentException(appCtx.getString(R.string.app_theme_invalid_format))
        require(data.version == 1) { appCtx.getString(R.string.app_theme_unsupported_version) }
        validatePackage(zip, data, options)
        val source = sanitize(data.config)
        val importDayTheme = options?.importDayTheme ?: true
        val importNightTheme = options?.importNightTheme ?: true
        val importDayTopBar = options?.importDayTopBar ?: true
        val importNightTopBar = options?.importNightTopBar ?: true
        val importDayBottomBar = options?.importDayBottomBar ?: true
        val importNightBottomBar = options?.importNightBottomBar ?: true
        val importDayCover = options?.importDayCover ?: true
        val importNightCover = options?.importNightCover ?: true
        val dayTheme = restoreThemeAsset(zip, temp, source.dayTheme, false, registerTheme = importDayTheme)
        val nightTheme = restoreThemeAsset(zip, temp, source.nightTheme, true, registerTheme = importNightTheme)
        val dayTop = if (importDayTopBar) restoreTopBar(zip, temp, false, source.dayTopBarDir, data.dayTopBar) else TopBarConfig.DEFAULT_DIR_NAME
        val nightTop = if (importNightTopBar) restoreTopBar(zip, temp, true, source.nightTopBarDir, data.nightTopBar) else TopBarConfig.DEFAULT_DIR_NAME
        val dayBottom = if (importDayBottomBar) restoreBottomBar(zip, temp, false, data.dayBottomBar) else null
        val nightBottom = if (importNightBottomBar) restoreBottomBar(zip, temp, true, data.nightBottomBar) else null
        val dayCover = if (importDayCover) restoreCover(zip, temp, data.dayCover) else null
        val nightCover = if (importNightCover) restoreCover(zip, temp, data.nightCover) else null
        return addImported(
            source.copy(
                dayTheme = dayTheme,
                nightTheme = nightTheme,
                dayTopBarDir = dayTop,
                nightTopBarDir = nightTop,
                dayBottomBarId = dayBottom,
                nightBottomBarId = nightBottom,
                dayCoverGroupId = dayCover,
                nightCoverGroupId = nightCover,
            ),
        )
    }

    /**
     * 按导入选项剥离未选中的组件，将对应字段置为默认值。
     *
     * 主题组件存储的是内联数据（颜色 + 背景图路径），而非引用 ID，
     * 因此不剥离背景图路径——无论是否勾选「主题」，应用主题配置中
     * 都保留完整的主题数据，以便预览和应用时能正确显示背景图。
     * 「主题」勾选仅控制是否在主题管理列表中注册新条目（见 restoreThemeAsset）。
     */
    internal fun stripComponents(config: Config, options: ImportOptions?): Config {
        if (options == null) return config
        return config.copy(
            dayTopBarDir = if (options.importDayTopBar) config.dayTopBarDir else TopBarConfig.DEFAULT_DIR_NAME,
            nightTopBarDir = if (options.importNightTopBar) config.nightTopBarDir else TopBarConfig.DEFAULT_DIR_NAME,
            dayBottomBarId = if (options.importDayBottomBar) config.dayBottomBarId else null,
            nightBottomBarId = if (options.importNightBottomBar) config.nightBottomBarId else null,
            dayCoverGroupId = if (options.importDayCover) config.dayCoverGroupId else null,
            nightCoverGroupId = if (options.importNightCover) config.nightCoverGroupId else null,
        )
    }

    internal fun addImported(imported: Config): Config {
        val items = load()
        val baseName = imported.name.trim().ifBlank { appCtx.getString(io.legado.app.R.string.application_theme_manage) }
        // 同名配置直接覆盖（复用原有 ID），避免反复追加“名称 2”；内置默认主题不参与同名覆盖
        val existingIndex = items.indexOfFirst { it.id != builtinThemeId && it.name == baseName }
        val next = if (existingIndex >= 0) {
            imported.copy(id = items[existingIndex].id, name = baseName, updatedAt = System.currentTimeMillis())
        } else {
            imported.copy(
                id = UUID.randomUUID().toString(),
                name = uniqueName(baseName, items.map { it.name }.toSet()),
                updatedAt = System.currentTimeMillis(),
            )
        }
        if (existingIndex >= 0) items[existingIndex] = next else items.add(next)
        save(items)
        return next
    }

    /** 判断指定配置是否与当前系统状态完全匹配 */
    fun isCurrent(context: Context, config: Config): Boolean = currentId(context) == config.id &&
        (config.dayTheme == null || context.getPrefString(PreferKey.dThemeName).orEmpty() == config.dayTheme?.themeName) &&
        (config.nightTheme == null || context.getPrefString(PreferKey.dNThemeName).orEmpty() == config.nightTheme?.themeName) &&
        (config.dayTopBarDir.isBlank() || TopBarConfig.activeDirName(false) == config.dayTopBarDir) &&
        (config.nightTopBarDir.isBlank() || TopBarConfig.activeDirName(true) == config.nightTopBarDir) &&
        (config.dayBottomBarId == null || NavigationBarConfig.activeConfig(context, false).id == config.dayBottomBarId) &&
        (config.nightBottomBarId == null || NavigationBarConfig.activeConfig(context, true).id == config.nightBottomBarId) &&
        (config.dayCoverGroupId == null || selectedCoverGroupId(context, false) == config.dayCoverGroupId) &&
        (config.nightCoverGroupId == null || selectedCoverGroupId(context, true) == config.nightCoverGroupId)

    /** 从当前系统状态快照生成一个新配置 */
    fun captureCurrent(context: Context, name: String, id: String? = null): Config {
        val dayThemeName = context.getPrefString(PreferKey.dThemeName).orEmpty()
        val nightThemeName = context.getPrefString(PreferKey.dNThemeName).orEmpty()
        val dayTheme = ThemeConfig.configList.firstOrNull {
            !it.isNightTheme && it.themeName == dayThemeName
        }?.copy()
        val nightTheme = ThemeConfig.configList.firstOrNull {
            it.isNightTheme && it.themeName == nightThemeName
        }?.copy()
        // 使用 SharedPreferences 中的实时值覆盖 backgroundImgPath。
        // 用户可能通过「主题设置」偏好页面直接修改了背景图（写入 PreferKey.bgImage / bgImageN），
        // 而该修改不会同步更新 ThemeConfig.configList 中的模板配置，
        // 因此必须从 SharedPreferences 读取当前实际使用的背景图路径。
        dayTheme?.backgroundImgPath = context.getPrefString(PreferKey.bgImage)
        nightTheme?.backgroundImgPath = context.getPrefString(PreferKey.bgImageN)
        return Config(
            id = id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            dayTheme = dayTheme,
            nightTheme = nightTheme,
            dayTopBarDir = TopBarConfig.activeDirName(false),
            nightTopBarDir = TopBarConfig.activeDirName(true),
            dayBottomBarId = NavigationBarConfig.activeConfig(context, false).id,
            nightBottomBarId = NavigationBarConfig.activeConfig(context, true).id,
            dayCoverGroupId = selectedCoverGroupId(context, false),
            nightCoverGroupId = selectedCoverGroupId(context, true),
        )
    }

    /** 新增配置（名称不能重复） */
    fun add(config: Config) {
        val items = load()
        require(config.name.isNotBlank())
        require(items.none { it.name == config.name })
        items.add(config)
        save(items)
    }

    /** 替换配置（同 ID 覆盖，名称不能与其他配置重复）；内置默认主题固定不变，忽略覆盖 */
    fun replace(config: Config) {
        if (config.id == builtinThemeId) return
        val items = load()
        require(config.name.isNotBlank())
        require(items.none { it.id != config.id && it.name == config.name })
        val index = items.indexOfFirst { it.id == config.id }
        if (index >= 0) items[index] = config else items.add(config)
        save(items)
    }

    /** 重命名配置；内置默认主题名称固定，忽略重命名 */
    fun rename(id: String, name: String) {
        if (id == builtinThemeId) return
        val items = load()
        val nextName = name.trim()
        require(nextName.isNotBlank())
        require(items.none { it.id != id && it.name == nextName })
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            items[index] = items[index].copy(name = nextName, updatedAt = System.currentTimeMillis())
            save(items)
        }
    }

    /** 删除配置，若删除的是当前配置则清除激活标记；内置默认主题不可删除 */
    fun delete(context: Context, id: String) {
        if (id == builtinThemeId) return
        save(load().filterNot { it.id == id })
        if (currentId(context) == id) context.putPrefString(currentIdKey, "")
    }

    /**
     * 将指定配置按给定顺序移到用户配置区最前。
     *
     * 落盘顺序即列表顺序（内置默认主题由代码生成、恒在首位），因此按 [ids] 的迭代顺序
     * 取出目标项再整体保存即可；内置主题不在 [Config.id] 命中范围内，天然不受影响。
     */
    fun toTop(ids: Set<String>) {
        if (ids.isEmpty()) return
        val items = load().filterNot { it.id == builtinThemeId }.toMutableList()
        val moved = items.filter { it.id in ids }
        if (moved.isEmpty()) return
        items.removeAll { it.id in ids }
        items.addAll(0, moved)
        save(items)
    }

    /**
     * 删除配置并可选删除关联组件。
     *
     * @param context 上下文
     * @param id 要删除的主题配置 ID
     * @param options 删除选项，控制是否一并删除关联的主题、顶栏、底栏、封面图集
     */
    suspend fun deleteWithComponents(context: Context, id: String, options: DeleteOptions) {
        // 内置默认主题不可删除
        if (id == builtinThemeId) return
        val config = load().firstOrNull { it.id == id }

        // 删除关联的日间主题配置
        if (options.deleteDayTheme && config?.dayTheme != null) {
            val theme = config.dayTheme!!
            val index = ThemeConfig.configList.indexOfFirst {
                it.themeName == theme.themeName && !it.isNightTheme
            }
            if (index >= 0) ThemeConfig.delConfig(index)
        }

        // 删除关联的夜间主题配置
        if (options.deleteNightTheme && config?.nightTheme != null) {
            val theme = config.nightTheme!!
            val index = ThemeConfig.configList.indexOfFirst {
                it.themeName == theme.themeName && it.isNightTheme
            }
            if (index >= 0) ThemeConfig.delConfig(index)
        }

        // 删除关联的日间顶栏配置
        if (options.deleteDayTopBar &&
            config != null &&
            config.dayTopBarDir.isNotBlank() &&
            config.dayTopBarDir != TopBarConfig.DEFAULT_DIR_NAME
        ) {
            val entry = TopBarConfig.loadEntries(context, false)
                .firstOrNull { it.dirName == config.dayTopBarDir }
            entry?.let { TopBarConfig.deleteLocal(it) }
        }

        // 删除关联的夜间顶栏配置
        if (options.deleteNightTopBar &&
            config != null &&
            config.nightTopBarDir.isNotBlank() &&
            config.nightTopBarDir != TopBarConfig.DEFAULT_DIR_NAME
        ) {
            val entry = TopBarConfig.loadEntries(context, true)
                .firstOrNull { it.dirName == config.nightTopBarDir }
            entry?.let { TopBarConfig.deleteLocal(it) }
        }

        // 删除关联的日间底栏配置
        if (options.deleteDayBottomBar && config?.dayBottomBarId != null) {
            val navConfigs = NavigationBarConfig.loadConfigs(context)
            val filtered = navConfigs.filterNot {
                it.isNight == false && it.id == config.dayBottomBarId && !it.isBuiltin
            }
            NavigationBarConfig.saveConfigs(context, filtered)
        }

        // 删除关联的夜间底栏配置
        if (options.deleteNightBottomBar && config?.nightBottomBarId != null) {
            val navConfigs = NavigationBarConfig.loadConfigs(context)
            val filtered = navConfigs.filterNot {
                it.isNight == true && it.id == config.nightBottomBarId && !it.isBuiltin
            }
            NavigationBarConfig.saveConfigs(context, filtered)
        }

        // 删除关联的日间封面图集
        if (options.deleteDayCover && config?.dayCoverGroupId != null) {
            CoverGalleryRepository().deleteGroup(config.dayCoverGroupId!!)
        }

        // 删除关联的夜间封面图集
        if (options.deleteNightCover && config?.nightCoverGroupId != null) {
            CoverGalleryRepository().deleteGroup(config.nightCoverGroupId!!)
        }

        // 最后删除主题配置本身
        save(load().filterNot { it.id == id })
        if (currentId(context) == id) context.putPrefString(currentIdKey, "")
    }

    /**
     * 应用主题配置。
     * 依次应用主题颜色、顶栏、底栏、封面图集，
     * 然后发送事件总线通知 UI 刷新。
     */
    fun apply(context: Context, config: Config) {
        validateForApply(context, config)
        val wasNight = AppConfig.isNightTheme
        config.dayTheme?.let { ThemeConfig.applyConfig(context, it.copy(isNightTheme = false), applyNow = false) }
        config.nightTheme?.let { ThemeConfig.applyConfig(context, it.copy(isNightTheme = true), applyNow = false) }

        applyTopBar(context, false, config.dayTopBarDir)
        applyTopBar(context, true, config.nightTopBarDir)
        applyBottomBar(context, false, config.dayBottomBarId)
        applyBottomBar(context, true, config.nightBottomBarId)

        val coverRepository = CoverGalleryRepository()
        // 封面图集为 null 表示「未设置」，应用时重置为默认（不使用图集），而非保持现状
        coverRepository.setSelectedGroup(false, config.dayCoverGroupId)
        coverRepository.setSelectedGroup(true, config.nightCoverGroupId)

        if (config.dayTheme != null && context.getPrefString(PreferKey.dThemeName) != config.dayTheme?.themeName) {
            throw IllegalStateException(appCtx.getString(R.string.app_theme_day_apply_failed))
        }
        if (config.nightTheme != null && context.getPrefString(PreferKey.dNThemeName) != config.nightTheme?.themeName) {
            throw IllegalStateException(appCtx.getString(R.string.app_theme_night_apply_failed))
        }

        AppConfig.isNightTheme = wasNight
        ThemeConfig.applyDayNight(context)
        context.putPrefString(currentIdKey, config.id)
        postEvent(EventBus.TOP_BAR_CHANGED, wasNight)
        postEvent(EventBus.NAVIGATION_BAR_CHANGED, wasNight)
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
    }

    /** 生成配置摘要文本，用于列表展示 */
    fun summary(context: Context, config: Config): String {
        val dayTheme = config.dayTheme?.themeName ?: context.getString(io.legado.app.R.string.application_theme_not_set)
        val nightTheme = config.nightTheme?.themeName ?: context.getString(io.legado.app.R.string.application_theme_not_set)
        val dayTop = topBarName(context, false, config.dayTopBarDir)
        val nightTop = topBarName(context, true, config.nightTopBarDir)
        val dayBottom = bottomBarName(context, false, config.dayBottomBarId)
        val nightBottom = bottomBarName(context, true, config.nightBottomBarId)
        val covers = CoverGalleryRepository()
        val dayCover = covers.getGroupName(config.dayCoverGroupId) ?: context.getString(io.legado.app.R.string.application_theme_not_set)
        val nightCover = covers.getGroupName(config.nightCoverGroupId) ?: context.getString(io.legado.app.R.string.application_theme_not_set)
        return appCtx.getString(R.string.app_theme_summary_format, dayTheme, dayTop, dayBottom, dayCover, nightTheme, nightTop, nightBottom, nightCover)
    }

    /** 安全保存配置列表：先写临时文件再原子替换，并备份原文件；内置默认主题由代码生成，不落盘 */
    private fun save(items: List<Config>) {
        val persisted = items.filterNot { it.id == builtinThemeId }
        val target = FileUtils.createFileIfNotExist(filePath)
        val temp = File("$filePath.tmp")
        temp.writeText(GSON.toJson(persisted))
        if (target.exists()) target.copyTo(File("$filePath.bak"), overwrite = true)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun applyTopBar(context: Context, isNight: Boolean, dirName: String) {
        TopBarConfig.loadEntries(context, isNight).firstOrNull { it.dirName == dirName }
            ?.let(TopBarConfig::apply)
    }

    private fun applyBottomBar(context: Context, isNight: Boolean, id: String?) {
        NavigationBarConfig.loadConfigs(context)
            .firstOrNull { it.isNight == isNight && it.id == id }
            ?.let { NavigationBarConfig.setActiveId(context, isNight, it.id) }
    }

    private fun topBarName(context: Context, isNight: Boolean, dirName: String): String = TopBarConfig.loadEntries(context, isNight)
        .firstOrNull { it.dirName == dirName }?.config?.name
        ?: context.getString(io.legado.app.R.string.application_theme_not_set)

    private fun bottomBarName(context: Context, isNight: Boolean, id: String?): String = NavigationBarConfig.loadConfigs(context)
        .firstOrNull { it.isNight == isNight && it.id == id }?.name
        ?: context.getString(io.legado.app.R.string.application_theme_not_set)

    private fun selectedCoverGroupId(context: Context, isNight: Boolean): Long? = context.getPrefString(
        if (isNight) PreferKey.coverCollectionNight else PreferKey.coverCollectionDay,
    )?.toLongOrNull()

    private fun packageTheme(zip: ZipOutputStream, theme: ThemeConfig.Config?, prefix: String): ThemeConfig.Config? {
        theme ?: return null
        val path = theme.backgroundImgPath ?: return theme.copy()
        if (path.startsWith("http", true)) return theme.copy()
        val source = File(path).takeIf { it.isFile } ?: appCtx.externalFiles
            .getFile(if (theme.isNightTheme) PreferKey.bgImageN else PreferKey.bgImage)
            .getFile(path)
            .takeIf { it.isFile }
        val entry = source?.let { addZipFile(zip, it, "$prefix/background") }
        return theme.copy(backgroundImgPath = entry)
    }

    private fun packageTopBar(
        zip: ZipOutputStream,
        context: Context,
        isNight: Boolean,
        dirName: String,
        prefix: String,
    ): TopBarConfig.Config? {
        if (dirName.isBlank() || dirName == TopBarConfig.DEFAULT_DIR_NAME) return null
        val entry = TopBarConfig.loadEntries(context, isNight).firstOrNull { it.dirName == dirName } ?: return null
        val wallpaper = entry.config.wallpaperPath?.let { path ->
            val file = File(path).takeIf { it.isFile }
                ?: entry.localDir?.resolve(path)?.takeIf { it.isFile }
            file?.let { addZipFile(zip, it, "$prefix/wallpaper") }
        }
        return entry.config.copy(wallpaperPath = wallpaper)
    }

    private fun packageBottomBar(
        zip: ZipOutputStream,
        context: Context,
        isNight: Boolean,
        id: String?,
        prefix: String,
    ): NavigationBarConfig? {
        val config = NavigationBarConfig.loadConfigs(context)
            .firstOrNull { it.isNight == isNight && it.id == id } ?: return null
        val icons = config.icons.mapNotNull { (key, path) ->
            File(path).takeIf { it.isFile }?.let { key to addZipFile(zip, it, "$prefix/$key") }
        }.toMap()
        return config.copy(icons = icons)
    }

    private fun packageCover(zip: ZipOutputStream, groupId: Long?, prefix: String): CoverPayload? {
        val group = CoverGalleryRepository().allGroupsWithImages()
            .firstOrNull { it.group.id == groupId } ?: return null
        val images = group.images.mapIndexedNotNull { index, image ->
            File(image.path).takeIf { it.isFile }?.let { addZipFile(zip, it, "$prefix/$index") }
        }
        return CoverPayload(group.group.name, images)
    }

    private fun addZipFile(zip: ZipOutputStream, file: File, basePath: String): String {
        val extension = file.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        val path = "$basePath$extension"
        zip.putNextEntry(ZipEntry(path))
        file.inputStream().buffered().use { it.copyTo(zip) }
        zip.closeEntry()
        return path
    }

    private suspend fun restoreThemeAsset(
        zip: ZipFile,
        temp: File,
        theme: ThemeConfig.Config?,
        isNight: Boolean,
        registerTheme: Boolean = true,
    ): ThemeConfig.Config? {
        theme ?: return null
        val path = theme.backgroundImgPath
        var restored = theme.copy(isNightTheme = isNight)
        if (path != null && !path.startsWith("http", true)) {
            val extracted = extractAsset(zip, temp, path)
            if (extracted != null) {
                val dir = appCtx.externalFiles.getFile(if (isNight) PreferKey.bgImageN else PreferKey.bgImage).apply { mkdirs() }
                val target = dir.getFile("application_theme_${UUID.randomUUID()}.${extracted.extension.ifBlank { "jpg" }}")
                extracted.copyTo(target, overwrite = true)
                restored = restored.copy(backgroundImgPath = target.absolutePath)
            } else {
                restored = restored.copy(backgroundImgPath = null)
            }
        }
        if (registerTheme) {
            // 将导入的主题添加到 ThemeConfig.configList，使其在主题管理列表中可见
            // ThemeConfig.addConfig 已支持同名覆盖，无需 uniqueName
            ThemeConfig.addConfig(restored)
        }
        return restored
    }

    private fun restoreTopBar(
        zip: ZipFile,
        temp: File,
        isNight: Boolean,
        originalDir: String,
        packaged: TopBarConfig.Config?,
    ): String {
        if (originalDir.isBlank()) return ""
        if (originalDir == TopBarConfig.DEFAULT_DIR_NAME) return TopBarConfig.DEFAULT_DIR_NAME
        val source = packaged ?: throw IllegalArgumentException(appCtx.getString(R.string.app_theme_missing_top_bar))
        val wallpaper = source.wallpaperPath?.let { extractAsset(zip, temp, it)?.absolutePath }
        // 同名顶栏直接复用已有条目进行更新，避免追加“名称 2”
        val existingEntry = TopBarConfig.loadEntries(appCtx, isNight)
            .firstOrNull { it.config.name == source.name.trim() }
        return TopBarConfig.addOrUpdate(
            source.copy(isNightMode = isNight, wallpaperPath = wallpaper),
            oldEntry = existingEntry,
        ).dirName
    }

    private fun restoreBottomBar(
        zip: ZipFile,
        temp: File,
        isNight: Boolean,
        packaged: NavigationBarConfig?,
    ): String? {
        packaged ?: return null
        if (packaged.isBuiltin) {
            return NavigationBarConfig.loadConfigs(appCtx)
                .firstOrNull { it.isNight == isNight && it.isBuiltin }?.id
        }
        val existing = NavigationBarConfig.loadConfigs(appCtx)
        // 同名底栏直接覆盖已有配置，避免追加"名称 2"
        val existingIndex = existing.indexOfFirst { it.isNight == isNight && it.name == packaged.name.trim() && !it.isBuiltin }
        val id = if (existingIndex >= 0) existing[existingIndex].id else UUID.randomUUID().toString()
        val iconDir = appCtx.externalFiles.getFile("navigationBarIcons", id).apply { mkdirs() }
        val icons = packaged.icons.mapNotNull { (key, path) ->
            extractAsset(zip, temp, path)?.let { source ->
                val target = iconDir.getFile("$key.${source.extension.ifBlank { "png" }}")
                source.copyTo(target, overwrite = true)
                key to target.absolutePath
            }
        }.toMap()
        val next = packaged.copy(id = id, name = packaged.name.trim(), isNight = isNight, isBuiltin = false, icons = icons)
        if (existingIndex >= 0) existing[existingIndex] = next else existing.add(next)
        NavigationBarConfig.saveConfigs(appCtx, existing)
        return id
    }

    private suspend fun restoreCover(zip: ZipFile, temp: File, payload: CoverPayload?): Long? {
        payload ?: return null
        val repository = CoverGalleryRepository()
        val baseName = payload.name.trim().ifBlank { appCtx.getString(R.string.app_theme_component_default_name) }
        // 同名封面图集直接复用已有图集，避免管理列表堆积重复组件
        val existingGroup = repository.allGroupsWithImages().firstOrNull { it.group.name == baseName }
        val groupId = existingGroup?.group?.id ?: repository.addGroup(baseName)
        val files = payload.images.mapNotNull { extractAsset(zip, temp, it) }
        if (files.isNotEmpty()) repository.addImageFiles(appCtx, groupId, files)
        return groupId
    }

    internal fun extractAsset(zip: ZipFile, temp: File, path: String): File? {
        val entry = zip.getEntry(path) ?: return null
        require(!entry.isDirectory) { appCtx.getString(R.string.app_theme_invalid_asset) }
        require(entry.size in 0..maxAssetBytes) { appCtx.getString(R.string.app_theme_asset_too_large) }
        val target = temp.resolve("${UUID.randomUUID()}.${path.substringAfterLast('.', "bin")}")
        zip.getInputStream(entry).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun uniqueName(base: String, used: Set<String>): String {
        val normalized = base.trim().ifBlank { appCtx.getString(R.string.app_theme_component_default_name) }
        if (normalized !in used) return normalized
        var index = 2
        while ("$normalized $index" in used) index++
        return "$normalized $index"
    }

    private fun validatePackage(zip: ZipFile, data: PackageData, options: ImportOptions? = null) {
        val importDayTopBar = options?.importDayTopBar ?: true
        val importNightTopBar = options?.importNightTopBar ?: true
        val importDayBottomBar = options?.importDayBottomBar ?: true
        val importNightBottomBar = options?.importNightBottomBar ?: true
        val importDayCover = options?.importDayCover ?: true
        val importNightCover = options?.importNightCover ?: true
        val assetPaths = buildList {
            // 主题背景图始终需要校验：无论是否勾选「主题」，
            // 背景图资源都会从 zip 中提取以供预览和应用使用
            listOfNotNull(data.config.dayTheme, data.config.nightTheme).forEach { theme ->
                theme.backgroundImgPath?.takeUnless { it.startsWith("http", true) }?.let(::add)
            }
            if (importDayTopBar) {
                data.dayTopBar?.wallpaperPath?.let(::add)
            }
            if (importNightTopBar) {
                data.nightTopBar?.wallpaperPath?.let(::add)
            }
            if (importDayBottomBar) {
                data.dayBottomBar?.icons?.values?.let(::addAll)
            }
            if (importNightBottomBar) {
                data.nightBottomBar?.icons?.values?.let(::addAll)
            }
            if (importDayCover) {
                data.dayCover?.let { cover ->
                    require(cover.images.size <= maxCoverImages) { appCtx.getString(R.string.app_theme_too_many_cover_images) }
                    addAll(cover.images)
                }
            }
            if (importNightCover) {
                data.nightCover?.let { cover ->
                    require(cover.images.size <= maxCoverImages) { appCtx.getString(R.string.app_theme_too_many_cover_images) }
                    addAll(cover.images)
                }
            }
        }
        require(assetPaths.distinct().size == assetPaths.size) { appCtx.getString(R.string.app_theme_duplicate_assets) }
        assetPaths.forEach { path ->
            val entry = zip.getEntry(path) ?: throw IllegalArgumentException(appCtx.getString(R.string.app_theme_missing_asset, path))
            require(!entry.isDirectory && entry.size in 0..maxAssetBytes) { appCtx.getString(R.string.app_theme_invalid_asset_path, path) }
        }
    }

    /** 校验配置是否可以应用：颜色格式、背景图存在性、子配置是否存在 */
    private fun validateForApply(context: Context, config: Config) {
        listOfNotNull(config.dayTheme, config.nightTheme).forEach { theme ->
            runCatching {
                Color.parseColor(theme.primaryColor)
                Color.parseColor(theme.accentColor)
                Color.parseColor(theme.backgroundColor)
                Color.parseColor(theme.bottomBackground)
            }.getOrElse { throw IllegalArgumentException(appCtx.getString(R.string.app_theme_invalid_color, theme.themeName), it) }
            theme.backgroundImgPath?.takeIf { File(it).isAbsolute }?.let { path ->
                require(File(path).isFile) { appCtx.getString(R.string.app_theme_bg_image_missing, theme.themeName) }
            }
        }
        if (config.dayTopBarDir.isNotBlank()) {
            require(TopBarConfig.loadEntries(context, false).any { it.dirName == config.dayTopBarDir }) { appCtx.getString(R.string.app_theme_day_top_bar_missing) }
        }
        if (config.nightTopBarDir.isNotBlank()) {
            require(TopBarConfig.loadEntries(context, true).any { it.dirName == config.nightTopBarDir }) { appCtx.getString(R.string.app_theme_night_top_bar_missing) }
        }
        config.dayBottomBarId?.let { id ->
            require(NavigationBarConfig.loadConfigs(context).any { !it.isNight && it.id == id }) { appCtx.getString(R.string.app_theme_day_nav_bar_missing) }
        }
        config.nightBottomBarId?.let { id ->
            require(NavigationBarConfig.loadConfigs(context).any { it.isNight && it.id == id }) { appCtx.getString(R.string.app_theme_night_nav_bar_missing) }
        }
        val groupIds = CoverGalleryRepository().allGroupsWithImages().map { it.group.id }.toSet()
        config.dayCoverGroupId?.let { require(it in groupIds) { appCtx.getString(R.string.app_theme_day_cover_missing) } }
        config.nightCoverGroupId?.let { require(it in groupIds) { appCtx.getString(R.string.app_theme_night_cover_missing) } }
    }

    /** 清洗并验证导入的配置数据，确保所有字段合法 */
    private fun sanitize(source: Config): Config {
        val id = runCatching { source.id }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        val name = runCatching { source.name }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException(appCtx.getString(R.string.app_theme_name_invalid))
        return Config(
            id = id,
            name = name,
            dayTheme = runCatching { source.dayTheme }.getOrNull(),
            nightTheme = runCatching { source.nightTheme }.getOrNull(),
            dayTopBarDir = runCatching { source.dayTopBarDir }.getOrNull().orEmpty(),
            nightTopBarDir = runCatching { source.nightTopBarDir }.getOrNull().orEmpty(),
            dayBottomBarId = runCatching { source.dayBottomBarId }.getOrNull(),
            nightBottomBarId = runCatching { source.nightBottomBarId }.getOrNull(),
            dayCoverGroupId = runCatching { source.dayCoverGroupId }.getOrNull(),
            nightCoverGroupId = runCatching { source.nightCoverGroupId }.getOrNull(),
            updatedAt = runCatching { source.updatedAt }.getOrDefault(System.currentTimeMillis()),
        )
    }
}
