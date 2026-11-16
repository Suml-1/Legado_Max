package io.legado.app.help.config.md3

import android.content.Context
import android.net.Uri
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * 外部主题包只读检查器。
 *
 * 这里不解压文件、不写偏好，也不触发 Activity 重建；它只负责识别协议、验证包结构，
 * 并保留后续规范化所需的原始清单信息。
 */
internal object Md3ThemePackageInspector {

    suspend fun inspect(context: Context, uri: Uri): Result<Md3ThemePackageInspection> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use(::inspect)
                    ?: error("无法读取主题包")
            }
        }

    fun inspect(input: InputStream): Md3ThemePackageInspection {
        val entries = linkedSetOf<String>()
        val caseInsensitiveEntries = hashSetOf<String>()
        var manifestJson: String? = null
        var legacyJson: String? = null
        var entryCount = 0
        var totalBytes = 0L

        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ENTRY_COUNT) { "主题包文件数量超过 $MAX_ENTRY_COUNT" }

                val normalizedName = validateEntryPath(entry.name, entry.isDirectory)
                val duplicateKey = normalizedName.lowercase(Locale.ROOT)
                require(caseInsensitiveEntries.add(duplicateKey)) {
                    "主题包包含重名路径: $normalizedName"
                }
                entries += normalizedName

                val capture = !entry.isDirectory &&
                    (normalizedName == MANIFEST_NAME || normalizedName == LEGACY_MANIFEST_NAME)
                val capturedBytes = if (capture) ByteArrayOutputStream() else null
                var entryBytes = 0L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    entryBytes += read
                    totalBytes += read
                    require(entryBytes <= MAX_ENTRY_BYTES) { "主题包单个文件过大: $normalizedName" }
                    require(totalBytes <= MAX_TOTAL_BYTES) { "主题包解压后体积过大" }
                    if (capturedBytes != null) {
                        require(capturedBytes.size() + read <= MAX_MANIFEST_BYTES) {
                            "主题包清单文件过大"
                        }
                        capturedBytes.write(buffer, 0, read)
                    }
                }
                capturedBytes?.toByteArray()?.toString(Charsets.UTF_8)?.let { text ->
                    when (normalizedName) {
                        MANIFEST_NAME -> manifestJson = text
                        LEGACY_MANIFEST_NAME -> legacyJson = text
                    }
                }
                zip.closeEntry()
            }
        }

        require(manifestJson != null || legacyJson != null) {
            "主题包缺少 manifest.json 或 application_theme.json"
        }
        if (manifestJson != null && legacyJson != null) {
            // A Reading NG export may include the source manifest among its assets.
            val root = parseRoot(manifestJson, MANIFEST_NAME)
            if (root.get("format")?.takeUnless { it.isJsonNull }?.asString == READING_NG_FORMAT) {
                throw Md3ThemePackageNotRecognizedException()
            }
            error("主题包同时包含两种清单，无法确定格式")
        }
        return manifestJson?.let { inspectPortable(it, entries) }
            ?: inspectLegacy(requireNotNull(legacyJson), entries)
    }

    private fun inspectPortable(
        rawJson: String,
        entries: Set<String>,
    ): Md3ThemePackageInspection {
        val root = parseRoot(rawJson, MANIFEST_NAME)
        if (root.get("format")?.takeUnless { it.isJsonNull }?.asString == READING_NG_FORMAT) {
            throw Md3ThemePackageNotRecognizedException()
        }
        require(root.has("formatVersion") && root.has("config")) { "MD3 主题包清单不完整" }
        require(root["config"].isJsonObject) { "MD3 主题包 config 不是对象" }
        val manifest = GSON.fromJson(root, Md3ThemePackageManifest::class.java)
            ?: error("MD3 主题包清单为空")
        require(manifest.formatVersion == SUPPORTED_VERSION) {
            "不支持的 MD3 主题包版本: ${manifest.formatVersion}"
        }

        val configFields = root.getAsJsonObject("config").keySet().toSet()
        val unknownFields = configFields - Md3ThemeCoverageRegistry.knownFieldNames
        val warnings = mutableListOf<String>()
        if (unknownFields.isNotEmpty()) {
            warnings += "包含 ${unknownFields.size} 个 NG 尚未登记的配置字段，将原样保留"
        }

        val assetBindings = manifest.assets.toMutableMap().apply {
            manifest.config.appFontPath
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { putIfAbsent(Md3ThemeAssetSlots.FONT_APP, it) }
        }
        val unknownSlots = manifest.assets.keys - Md3ThemeAssetSlots.all
        if (unknownSlots.isNotEmpty()) {
            warnings += "包含 ${unknownSlots.size} 个未知资源槽位，将原样保留"
        }
        validateAssetBindings(assetBindings, entries)
        validateCoverAlbums(manifest.coverAlbums, entries)
        manifest.coverSelection.albumRef?.let { selectedRef ->
            require(manifest.coverAlbums.any { it.ref == selectedRef }) {
                "主题包选择了不存在的封面图集: $selectedRef"
            }
        }
        if (manifest.config.composeEngine.equals("miuix", ignoreCase = true) ||
            manifest.config.useMiuixMonet
        ) {
            warnings += "Miuix 来源标记会转换成 NG 组件语义，不启用 Miuix 引擎"
        }
        if ("themeMode" in configFields) {
            warnings += "主题模式仅用于预览和兼容报告，导入时不会自动覆盖当前模式"
        }

        return Md3ThemePackageInspection(
            format = Md3ThemePackageFormat.PORTABLE_V1,
            name = manifest.name?.trim()?.takeIf(String::isNotEmpty) ?: DEFAULT_THEME_NAME,
            manifest = manifest,
            legacyRoot = null,
            rawManifestJson = rawJson,
            archiveEntries = entries.toSet(),
            presentConfigFields = configFields,
            unknownConfigFields = unknownFields,
            assetBindings = assetBindings,
            warnings = warnings,
        )
    }

    private fun inspectLegacy(
        rawJson: String,
        entries: Set<String>,
    ): Md3ThemePackageInspection {
        val root = parseRoot(rawJson, LEGACY_MANIFEST_NAME)
        require(root.has("version") && root.has("config")) { "旧版主题包清单不完整" }
        require(root["config"].isJsonObject) { "旧版主题包 config 不是对象" }
        val version = root["version"].asInt
        require(version == SUPPORTED_VERSION) { "不支持的旧版主题包版本: $version" }
        val config = root.getAsJsonObject("config")
        require(config["dayTheme"]?.isJsonObject == true) { "旧版主题包缺少日间主题" }

        val warnings = mutableListOf("旧版主题会先转换成 NG Profile，再进入导入预览")
        if (config["nightTheme"]?.isJsonObject != true) {
            warnings += "旧版主题未提供夜间主题，预览时将标记为缺失"
        }
        val legacyAssets = legacyAssetBindings(root)
        validateAssetBindings(legacyAssets, entries)

        val name = config.stringOrNull("name")
            ?: config.getAsJsonObject("dayTheme").stringOrNull("themeName")
            ?: DEFAULT_THEME_NAME
        return Md3ThemePackageInspection(
            format = Md3ThemePackageFormat.LEGACY_APPLICATION_THEME_V1,
            name = name,
            manifest = null,
            legacyRoot = root,
            rawManifestJson = rawJson,
            archiveEntries = entries.toSet(),
            presentConfigFields = emptySet(),
            unknownConfigFields = emptySet(),
            assetBindings = legacyAssets,
            warnings = warnings,
        )
    }

    private fun legacyAssetBindings(root: JsonObject): Map<String, String> = buildMap {
        val config = root.getAsJsonObject("config")
        config.getAsJsonObject("dayTheme")?.stringOrNull("backgroundImgPath")?.let {
            put(Md3ThemeAssetSlots.BACKGROUND_LIGHT, it)
        }
        config.getAsJsonObject("nightTheme")?.stringOrNull("backgroundImgPath")?.let {
            put(Md3ThemeAssetSlots.BACKGROUND_DARK, it)
        }
        addLegacyIconBindings(root, "dayBottomBar", "day")
        addLegacyIconBindings(root, "nightBottomBar", "night")
        addLegacyCoverBindings(root, "dayCover", "day")
        addLegacyCoverBindings(root, "nightCover", "night")
    }

    private fun MutableMap<String, String>.addLegacyIconBindings(
        root: JsonObject,
        objectName: String,
        appearance: String,
    ) {
        val icons = root.getAsJsonObject(objectName)?.getAsJsonObject("icons") ?: return
        icons.entrySet().forEach { (name, value) ->
            if (!value.isJsonNull) put("legacy.navigation.$appearance.$name", value.asString)
        }
    }

    private fun MutableMap<String, String>.addLegacyCoverBindings(
        root: JsonObject,
        objectName: String,
        appearance: String,
    ) {
        val images = root.getAsJsonObject(objectName)?.getAsJsonArray("images") ?: return
        images.forEachIndexed { index, value ->
            if (!value.isJsonNull) put("legacy.cover.$appearance.$index", value.asString)
        }
    }

    private fun validateAssetBindings(bindings: Map<String, String>, entries: Set<String>) {
        bindings.forEach { (slot, path) ->
            val safePath = validateReferencePath(path, "资源槽位 $slot")
            require(safePath in entries) { "主题包资源不存在: $safePath" }
        }
    }

    private fun validateCoverAlbums(
        albums: List<Md3ThemePackageCoverAlbum>,
        entries: Set<String>,
    ) {
        val refs = hashSetOf<String>()
        albums.forEach { album ->
            require(album.ref.isNotBlank()) { "封面图集缺少 ref" }
            require(refs.add(album.ref)) { "封面图集 ref 重复: ${album.ref}" }
            (album.lightImages + album.darkImages).forEach { image ->
                val safePath = validateReferencePath(image.path, "封面图集 ${album.ref}")
                require(safePath in entries) { "主题包封面资源不存在: $safePath" }
            }
        }
    }

    private fun parseRoot(rawJson: String, fileName: String): JsonObject {
        val element = runCatching { JsonParser.parseString(rawJson) }
            .getOrElse { error("$fileName 不是有效 JSON: ${it.message}") }
        require(element.isJsonObject) { "$fileName 根节点不是对象" }
        return element.asJsonObject
    }

    private fun validateEntryPath(path: String, directory: Boolean): String {
        require(path.isNotBlank() && '\u0000' !in path) { "主题包包含空路径" }
        require('\\' !in path) { "主题包路径必须使用 /: $path" }
        require(!path.startsWith('/') && !DRIVE_PATH.containsMatchIn(path)) {
            "主题包包含绝对路径: $path"
        }
        val normalized = if (directory) path.trimEnd('/') else path
        require(normalized.isNotBlank()) { "主题包包含空目录路径" }
        require(normalized.split('/').all { it.isNotBlank() && it != "." && it != ".." }) {
            "主题包包含越界或异常路径: $path"
        }
        return normalized
    }

    private fun validateReferencePath(path: String, source: String): String {
        require(path.isNotBlank()) { "$source 的资源路径为空" }
        return validateEntryPath(path, directory = false)
    }

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf(String::isNotEmpty)

    private const val DEFAULT_THEME_NAME = "未命名主题"
    private const val MANIFEST_NAME = "manifest.json"
    private const val LEGACY_MANIFEST_NAME = "application_theme.json"
    private const val SUPPORTED_VERSION = 1
    private const val READING_NG_FORMAT = "reading-ng-theme"
    private const val MAX_ENTRY_COUNT = 4096
    private const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 512L * 1024 * 1024
    private val DRIVE_PATH = Regex("^[A-Za-z]:")
}

/** 仅用于让统一导入入口回退到 Reading NG 自有包解析器。 */
internal class Md3ThemePackageNotRecognizedException : IllegalArgumentException()
