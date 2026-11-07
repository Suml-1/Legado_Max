package io.legado.app.help.config

import android.content.Context
import android.graphics.Color
import androidx.annotation.Keep
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getFile
import io.legado.app.utils.getPrefString
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.putPrefString
import splitties.init.appCtx
import java.io.File

/**
 * 顶栏配置管理器。
 *
 * 管理顶栏（TitleBar）的外观配置包，包括标签栏颜色、选中色、
 * 壁纸、圆角等。配置以文件系统目录形式存储，每个包为一个
 * 目录，包含 top_bar.json 配置文件和可选的壁纸图片。
 *
 * 支持日间/夜间模式独立配置，以及 zip 导入导出功能。
 * 内部维护了一个 "_active" 快照目录，用于加速当前配置的读取。
 */
object TopBarConfig {

    /** 默认配置目录名 */
    const val DEFAULT_DIR_NAME = "default"

    /** 样式：默认（跟随主题） */
    const val STYLE_DEFAULT = "default"

    /** 样式：常规（独立背景/圆角） */
    const val STYLE_REGULAR = "regular"

    private const val packageFileName = "top_bar.json"
    private const val activeSnapshotDirName = "_active"
    private const val legacyConfigsKey = "customTopBarConfigs"
    private const val legacyActiveDayKey = "activeDayTopBarId"
    private const val legacyActiveNightKey = "activeNightTopBarId"
    private const val legacyMigratedKey = "topBarLegacyMigrated"

    private const val activeDayKey = PreferKey.topBarPackageDay
    private const val activeNightKey = PreferKey.topBarPackageNight

    /** 顶栏宽高比的经验估计（手机顶栏约 4:1 ~ 7:1，取中间值）：用来估计壁纸纵向露出多少，见 [wallpaperAverageColor] */
    private const val topBarAspectRatio = 5f

    /** [currentEntry] 的读取缓存：key 含日夜、目录名与快照文件签名 */
    private var cachedEntry: Pair<String, Entry>? = null

    /** [currentWallpaperFile] 的解析缓存，key 与当前条目同源 */
    private var cachedWallpaper: Pair<String, File?>? = null

    val rootDir: File
        get() = appCtx.externalFiles.getFile("topBarPackages")

    private val tempDir: File
        get() = appCtx.externalFiles.getFile("topBarTemp").apply { mkdirs() }

    /**
     * 顶栏配置数据类。
     *
     * @property name 配置名称
     * @property isNightMode 是否为夜间模式
     * @property style 样式类型：[STYLE_DEFAULT] 或 [STYLE_REGULAR]
     * @property tagBarColor 标签栏背景色，null 表示跟随主题
     * @property tagBarAlpha 标签栏透明度（0-100）
     * @property tagSelectedColor 选中标签颜色，null 表示跟随主题
     * @property tagSelectedAlpha 选中标签透明度（0-100）
     * @property wallpaperPath 壁纸图片路径（相对于包目录的相对路径）
     * @property wallpaperAlpha 壁纸透明度（0-100）
     * @property backgroundColor 常规样式下的背景色
     * @property cornerScale 圆角缩放系数（0-3）
     * @property expandFiltersByDefault 默认是否展开筛选标签
     * @property updatedAt 最后更新时间戳
     */
    @Keep
    data class Config(
        var name: String,
        var isNightMode: Boolean,
        var style: String = STYLE_DEFAULT,
        var tagBarColor: Int? = null,
        var tagBarAlpha: Int = 100,
        var tagSelectedColor: Int? = null,
        var tagSelectedAlpha: Int = 100,
        var wallpaperPath: String? = null,
        var wallpaperAlpha: Int = 100,
        var backgroundColor: Int? = null,
        var cornerScale: Float? = null,
        var expandFiltersByDefault: Boolean = false,
        var updatedAt: Long = System.currentTimeMillis(),
    ) {
        fun toJson(): String = GSON.toJson(this)
    }

    /** 配置条目：包含配置数据和来源信息（内置/本地），用于列表展示 */
    data class Entry(
        val config: Config,
        val source: Source,
        val dirName: String,
        val localDir: File? = null,
    )

    /** 配置来源类型：内置（默认）或本地（用户自定义） */
    enum class Source { BUILTIN, LOCAL }

    /** 旧版配置数据类，用于从 SharedPreferences 迁移到文件系统存储 */
    @Keep
    private data class LegacyConfig(
        var id: String = "",
        var name: String = "",
        var isNight: Boolean = false,
        var isBuiltin: Boolean = false,
        var style: String = STYLE_DEFAULT,
        var cornerScale: Float = 1f,
        var backgroundColor: Int? = null,
        var wallpaperPath: String? = null,
        var wallpaperAlpha: Int = 100,
        var tagBarColor: Int? = null,
        var tagBarAlpha: Int = 100,
        var tagSelectedColor: Int? = null,
        var tagSelectedAlpha: Int = 100,
        var expandFiltersByDefault: Boolean = false,
        var updatedAt: Long = System.currentTimeMillis(),
    )

    /** 创建默认配置 */
    fun defaultConfig(context: Context, isNight: Boolean): Config = Config(
        name = defaultName(isNight),
        isNightMode = isNight,
        tagBarColor = context.primaryColor,
        tagSelectedColor = context.primaryColor,
        backgroundColor = defaultBackgroundColor(isNight),
        cornerScale = 1f,
        updatedAt = 0L,
    )

    /** 获取当前激活的配置 */
    fun currentConfig(context: Context, isNight: Boolean = AppConfig.isNightTheme): Config = currentEntry(context, isNight).config

    /** 获取当前激活的配置包目录名 */
    fun activeDirName(isNight: Boolean): String = appCtx.getPrefString(if (isNight) activeNightKey else activeDayKey, DEFAULT_DIR_NAME)
        ?.ifBlank { DEFAULT_DIR_NAME }
        ?: DEFAULT_DIR_NAME

    /** 生成当前配置签名，用于缓存失效判断 */
    fun currentSignature(isNight: Boolean): String {
        val dirName = activeDirName(isNight)
        if (dirName == DEFAULT_DIR_NAME) return "$isNight|$DEFAULT_DIR_NAME"
        val configFile = File(activeLocalDir(isNight), packageFileName)
            .takeIf { it.exists() }
            ?: File(localDir(isNight, dirName), packageFileName)
        return "$isNight|$dirName|${configFile.lastModified()}"
    }

    /**
     * 获取当前配置条目。
     * 若激活的是本地配置，会优先读取 _active 快照目录；
     * 若快照不存在则从源目录复制一份。
     *
     * 读取有缓存：自定义配置包（复杂主题）在快照目录里存的是 JSON 文件，
     * 未命中缓存时要 `readText` + 解析，而 [currentConfig] / [currentWallpaperFile] 会被
     * Compose 顶栏在**每次重组**时调用（见 `ui/theme/CommonPageColors.pageTopBarColors`），
     * 于是主界面切换与列表滚动都被这份文件 IO 拖住。缓存 key 含快照文件的时间与大小，
     * 配置包被应用/编辑后（写盘会改签名）或 [clearCurrentCache] 显式清空时自动失效。
     */
    fun currentEntry(context: Context, isNight: Boolean): Entry {
        migrateLegacyIfNeeded(context)
        val dirName = activeDirName(isNight)
        if (dirName == DEFAULT_DIR_NAME) return defaultEntry(context, isNight)
        val snapshotDir = activeLocalDir(isNight)
        val snapshotFile = File(snapshotDir, packageFileName)
        val key = "$isNight|$dirName|${snapshotFile.lastModified()}|${snapshotFile.length()}"
        cachedEntry?.takeIf { it.first == key }?.let { return it.second }
        val entry = readEntry(snapshotDir)?.copy(dirName = dirName)
            ?: readEntry(localDir(isNight, dirName))?.let { source ->
                runCatching { writeActiveSnapshot(source) }
                readEntry(activeLocalDir(isNight))?.copy(dirName = dirName) ?: source
            }
            ?: return defaultEntry(context, isNight)
        cachedEntry = key to entry
        return entry
    }

    /**
     * 获取当前壁纸文件，不存在时返回 null。
     *
     * 解析结果随 [currentEntry] 一起缓存：路径与包目录都没变时不必反复 `exists()`；
     * 配置包里换了壁纸必然重写 top_bar.json，签名随之变化，缓存自然失效。
     */
    fun currentWallpaperFile(context: Context, isNight: Boolean): File? {
        val entry = currentEntry(context, isNight)
        val path = entry.config.wallpaperPath?.takeIf { it.isNotBlank() } ?: return null
        val key = "${entry.config.isNightMode}|${entry.dirName}|$path|${entry.localDir?.absolutePath}"
        cachedWallpaper?.takeIf { it.first == key }?.let { return it.second }
        val file = File(path)
        val resolved = if (file.isAbsolute) {
            file
        } else {
            File(entry.localDir ?: localDir(entry.config.isNightMode, entry.dirName), path)
        }
        return resolved.takeIf { it.exists() && it.isFile }.also {
            cachedWallpaper = key to it
        }
    }

    /** 加载所有配置条目（内置 + 本地），按更新时间排序 */
    fun loadEntries(context: Context, isNight: Boolean): List<Entry> {
        migrateLegacyIfNeeded(context)
        return buildList {
            add(defaultEntry(context, isNight))
            addAll(loadLocal(isNight))
        }.sortedWith(
            compareBy<Entry> { it.dirName != DEFAULT_DIR_NAME }
                .thenByDescending { it.config.updatedAt }
                .thenBy { it.config.name }
                .thenBy { it.dirName },
        )
    }

    /**
     * 新增或更新配置。若 [oldEntry] 不为空且为本地配置，则更新原目录；
     * 否则创建新目录。名称重名时抛出异常。
     */
    fun addOrUpdate(config: Config, oldEntry: Entry? = null): Entry {
        val normalized = normalizeConfig(config)
        val name = normalized.name.trim().ifBlank { defaultName(normalized.isNightMode) }
        val keepOldDir = oldEntry != null &&
            oldEntry.dirName.isNotBlank() &&
            oldEntry.dirName != DEFAULT_DIR_NAME
        val dirName = if (keepOldDir) {
            oldEntry.dirName
        } else {
            name.normalizeFileName().ifBlank { "top_bar_${System.currentTimeMillis()}" }
        }
        if (!keepOldDir && readEntry(localDir(normalized.isNightMode, dirName)) != null) {
            throw IllegalArgumentException(appCtx.getString(R.string.top_bar_name_exists))
        }
        val dir = localDir(normalized.isNightMode, dirName).apply { mkdirs() }
        val next = normalized.copy(
            name = name,
            wallpaperPath = normalizeWallpaperPath(normalized.wallpaperPath, dir),
            updatedAt = System.currentTimeMillis(),
        )
        File(dir, packageFileName).writeText(GSON.toJson(next))
        clearCurrentCache()
        return Entry(next, Source.LOCAL, dirName, localDir = dir)
    }

    /** 应用指定配置：若为默认配置则清除快照，否则写入快照并设置激活目录 */
    fun apply(entry: Entry) {
        clearCurrentCache()
        if (entry.dirName == DEFAULT_DIR_NAME) {
            FileUtils.delete(activeLocalDir(entry.config.isNightMode), deleteRootDir = true)
            appCtx.putPrefString(
                if (entry.config.isNightMode) activeNightKey else activeDayKey,
                DEFAULT_DIR_NAME,
            )
            return
        }
        writeActiveSnapshot(entry)
        appCtx.putPrefString(
            if (entry.config.isNightMode) activeNightKey else activeDayKey,
            entry.dirName,
        )
    }

    /** 删除本地配置目录，若删除的是当前激活配置则重置为默认 */
    fun deleteLocal(entry: Entry) {
        if (entry.dirName == DEFAULT_DIR_NAME) return
        clearCurrentCache()
        FileUtils.delete(entry.localDir ?: localDir(entry.config.isNightMode, entry.dirName), deleteRootDir = true)
        resetActiveIfNeeded(entry)
    }

    /**
     * 将指定配置包置顶。
     *
     * 列表顺序由 [loadEntries] 按 `updatedAt` 倒序生成，因此「置顶」= 把包内的
     * `updatedAt` 刷成当前时刻后重写 JSON；内置默认包恒在首位，不参与排序。
     */
    fun toTop(entry: Entry) {
        if (entry.dirName == DEFAULT_DIR_NAME) return
        val dir = entry.localDir ?: localDir(entry.config.isNightMode, entry.dirName)
        val file = File(dir, packageFileName)
        if (!file.isFile) return
        val config = GSON.fromJsonObject<Config>(file.readText()).getOrNull() ?: return
        file.writeText(GSON.toJson(config.copy(updatedAt = System.currentTimeMillis())))
    }

    /** 将配置包导出为 zip 文件 */
    fun exportZip(entry: Entry): File {
        val dir = entry.localDir ?: localDir(entry.config.isNightMode, entry.dirName)
        val zipFile = tempDir.getFile("${entry.dirName}.zip")
        if (zipFile.exists()) zipFile.delete()
        ZipUtils.zipFile(dir, zipFile)
        return zipFile
    }

    /** 从 zip 文件导入配置包，自动生成唯一目录名 */
    fun importZip(zipFile: File): Entry {
        val unzipDir = tempDir.getFile("import_${System.currentTimeMillis()}").apply {
            if (exists()) FileUtils.delete(this, deleteRootDir = true)
            mkdirs()
        }
        return try {
            ZipUtils.unZipToPath(zipFile, unzipDir)
            val packageFile = unzipDir.walkTopDown().firstOrNull { it.isFile && it.name == packageFileName }
                ?: throw IllegalArgumentException(appCtx.getString(R.string.top_bar_config_missing))
            val config = normalizeConfig(GSON.fromJsonObject<Config>(packageFile.readText()).getOrThrow())
            config.updatedAt = System.currentTimeMillis()
            val dirName = uniqueDirName(
                config.isNightMode,
                config.name.normalizeFileName().ifBlank { "top_bar_${System.currentTimeMillis()}" },
            )
            val targetDir = localDir(config.isNightMode, dirName)
            targetDir.mkdirs()
            packageFile.parentFile?.copyRecursively(targetDir, overwrite = true)
            val finalConfig = config.copy(wallpaperPath = normalizeWallpaperPath(config.wallpaperPath, targetDir))
            File(targetDir, packageFileName).writeText(GSON.toJson(finalConfig))
            Entry(finalConfig, Source.LOCAL, dirName, localDir = targetDir)
        } finally {
            FileUtils.delete(unzipDir, deleteRootDir = true)
        }
    }

    /** 从 JSON 字符串导入配置，兼容旧版格式 */
    fun importJson(json: String, isNight: Boolean): Entry {
        val config = GSON.fromJsonObject<Config>(json).getOrNull()
            ?: GSON.fromJsonObject<LegacyConfig>(json).getOrThrow().toConfig()
        config.isNightMode = isNight
        return addOrUpdate(config)
    }

    /** 透明度百分比（0-100）转 Alpha 值（0-255） */
    fun opacityToAlpha(opacity: Int): Int = opacity.coerceIn(0, 100) * 255 / 100

    /** 给颜色叠加指定透明度（百分比 0-100） */
    fun withOpacity(color: Int, opacity: Int): Int {
        val alpha = opacityToAlpha(opacity)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun defaultBackgroundColor(isNight: Boolean): Int = if (isNight) Color.BLACK else Color.WHITE

    fun resolveBackgroundColor(config: Config): Int = config.backgroundColor ?: defaultBackgroundColor(config.isNightMode)

    /**
     * 顶栏壁纸的平均色（不含 alpha），没壁纸或解不出来时返回 null。
     *
     * 供顶栏前景取色使用（见 `resolveTopBarContentColor`）：壁纸是**半透明压在顶栏底色之上**的，
     * 只看壁纸本身的深浅会把"44% 透明壁纸 + 米白底色"判成深底，于是顶栏配上白字压在米白底上
     * ——2026-10-12 反馈的"顶栏文字看不清"就是这个原因。取色方拿到平均色后再按 wallpaperAlpha 合成。
     *
     * 平均色按 [topBarAspectRatio] 取"居中裁切后实际会显示"的那条区域：顶栏又宽又扁，
     * 壁纸纵向只露出中间一条，宽图上下常见的留白/黑边根本不在顶栏上。
     */
    fun wallpaperAverageColor(wallpaperFile: File?): Int? {
        if (wallpaperFile == null) return null
        return ThemeConfig.getImageAverageColor(
            path = wallpaperFile.absolutePath,
            cropAspectRatio = topBarAspectRatio,
        )
    }

    fun resolveCornerScale(config: Config): Float = config.cornerScale ?: 1f

    private fun defaultEntry(context: Context, isNight: Boolean): Entry = Entry(defaultConfig(context, isNight), Source.BUILTIN, DEFAULT_DIR_NAME)

    private fun loadLocal(isNight: Boolean): List<Entry> = typeDir(isNight).listFiles()
        ?.filter { it.isDirectory }
        ?.mapNotNull { readEntry(it) }
        .orEmpty()

    private fun readEntry(dir: File): Entry? {
        val file = File(dir, packageFileName)
        if (!file.exists()) return null
        val config = GSON.fromJsonObject<Config>(file.readText()).getOrNull()?.let(::normalizeConfig)
            ?: return null
        return Entry(config, Source.LOCAL, dir.name, localDir = dir)
    }

    private fun normalizeConfig(config: Config): Config {
        config.style = when (config.style) {
            STYLE_DEFAULT, STYLE_REGULAR -> config.style
            "immersive", "flow" -> STYLE_REGULAR
            else -> STYLE_DEFAULT
        }
        config.tagBarAlpha = config.tagBarAlpha.coerceIn(0, 100)
        config.tagSelectedAlpha = config.tagSelectedAlpha.coerceIn(0, 100)
        config.wallpaperAlpha = config.wallpaperAlpha.coerceIn(0, 100)
        config.wallpaperPath = config.wallpaperPath?.takeIf { it.isNotBlank() }
        config.cornerScale = config.cornerScale?.coerceIn(0f, 3f)
        return config
    }

    private fun normalizeWallpaperPath(path: String?, dir: File): String? {
        val value = path?.takeIf { it.isNotBlank() } ?: return null
        val source = File(value)
        if (!source.isAbsolute) return value
        if (!source.exists() || !source.isFile) return null
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("top_bar_wallpaper.") }
            ?.forEach { it.delete() }
        val suffix = source.extension.takeIf { it.isNotBlank() } ?: "jpg"
        val target = File(dir, "top_bar_wallpaper.$suffix")
        if (source.absolutePath != target.absolutePath) {
            source.copyTo(target, overwrite = true)
        }
        return target.name
    }

    private fun resetActiveIfNeeded(entry: Entry) {
        clearCurrentCache()
        if (activeDirName(entry.config.isNightMode) == entry.dirName) {
            FileUtils.delete(activeLocalDir(entry.config.isNightMode), deleteRootDir = true)
            appCtx.putPrefString(
                if (entry.config.isNightMode) activeNightKey else activeDayKey,
                DEFAULT_DIR_NAME,
            )
        }
    }

    private fun writeActiveSnapshot(entry: Entry) {
        val sourceDir = entry.localDir ?: localDir(entry.config.isNightMode, entry.dirName)
        val targetDir = activeLocalDir(entry.config.isNightMode)
        if (targetDir.exists()) {
            FileUtils.delete(targetDir, deleteRootDir = true)
        }
        sourceDir.copyRecursively(targetDir, overwrite = true)
    }

    /**
     * 清空当前配置与壁纸的读取缓存。
     *
     * 写盘会改变快照签名、缓存本会自然失效；这里在配置包的增删改与应用处再显式清一次，
     * 兜住"同一毫秒内写入且长度未变"这类签名看不出来的改动。
     */
    private fun clearCurrentCache() {
        cachedEntry = null
        cachedWallpaper = null
    }

    private fun activeLocalDir(isNight: Boolean): File = rootDir.getFile(activeSnapshotDirName)
        .getFile(if (isNight) "night" else "day")

    private fun localDir(isNight: Boolean, dirName: String): File = typeDir(isNight).getFile(dirName)

    private fun uniqueDirName(isNight: Boolean, preferred: String): String {
        val clean = preferred.normalizeFileName().ifBlank { "top_bar_${System.currentTimeMillis()}" }
        var candidate = clean
        var index = 1
        while (localDir(isNight, candidate).exists()) {
            candidate = "${clean}_$index"
            index++
        }
        return candidate
    }

    private fun typeDir(isNight: Boolean): File = rootDir.getFile(if (isNight) "night" else "day").apply { mkdirs() }

    private fun defaultName(isNight: Boolean): String = appCtx.getString(
        if (isNight) R.string.top_bar_night_default_name else R.string.top_bar_day_default_name,
    )

    /** 从旧版 SharedPreferences 格式迁移到文件系统存储（仅执行一次） */
    private fun migrateLegacyIfNeeded(context: Context) {
        if (appCtx.getPrefString(legacyMigratedKey, "").orEmpty().isNotBlank()) return
        val legacyJsons = context.getPrefString(legacyConfigsKey)
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            .orEmpty()
        legacyJsons.forEach { json ->
            runCatching {
                val legacy = GSON.fromJsonObject<LegacyConfig>(json).getOrThrow()
                if (!legacy.isBuiltin) addOrUpdate(legacy.toConfig())
            }
        }
        migrateLegacyActive(false)
        migrateLegacyActive(true)
        appCtx.putPrefString(legacyMigratedKey, "1")
    }

    private fun migrateLegacyActive(isNight: Boolean) {
        val legacyId = appCtx.getPrefString(if (isNight) legacyActiveNightKey else legacyActiveDayKey)
            ?.takeIf { it.isNotBlank() }
            ?: return
        val entry = loadLocal(isNight).firstOrNull { it.dirName == legacyId || it.config.name == legacyId }
            ?: return
        appCtx.putPrefString(if (isNight) activeNightKey else activeDayKey, entry.dirName)
    }

    private fun LegacyConfig.toConfig(): Config = Config(
        name = name.ifBlank { defaultName(isNight) },
        isNightMode = isNight,
        style = style,
        tagBarColor = tagBarColor,
        tagBarAlpha = tagBarAlpha,
        tagSelectedColor = tagSelectedColor,
        tagSelectedAlpha = tagSelectedAlpha,
        wallpaperPath = wallpaperPath,
        wallpaperAlpha = wallpaperAlpha,
        backgroundColor = backgroundColor,
        cornerScale = cornerScale,
        expandFiltersByDefault = expandFiltersByDefault,
        updatedAt = updatedAt,
    )
}
