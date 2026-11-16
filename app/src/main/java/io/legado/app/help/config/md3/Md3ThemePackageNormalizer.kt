package io.legado.app.help.config.md3

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgColorSpec
import io.legado.app.ui.design.theme.NgContrastLevel
import io.legado.app.ui.design.theme.NgPaletteStyle
import io.legado.app.ui.design.theme.parseNgColor

/** 把两套外部协议转换成同一个 NG Profile V2 预览，不产生任何运行时副作用。 */
internal object Md3ThemePackageNormalizer {

    fun normalize(inspection: Md3ThemePackageInspection): NgThemePackagePreview {
        val spec = when (inspection.format) {
            Md3ThemePackageFormat.PORTABLE_V1 -> normalizePortable(inspection)
            Md3ThemePackageFormat.LEGACY_APPLICATION_THEME_V1 -> normalizeLegacy(inspection)
        }
        return NgThemePackagePreview(
            spec = spec,
            compatibility = inspection.compatibilityReport(spec.normalizedFields.keys),
        )
    }

    private fun normalizePortable(inspection: Md3ThemePackageInspection): NgThemePackageSpec {
        val manifest = requireNotNull(inspection.manifest)
        val root = parseRoot(inspection.rawManifestJson)
        val config = root.getAsJsonObject("config")
        val known = linkedMapOf<String, String>()
        val unknown = linkedMapOf<String, String>()
        config.entrySet().forEach { (name, value) ->
            if (name in Md3ThemeCoverageRegistry.knownFieldNames) {
                known[name] = value.toString()
            } else {
                unknown[name] = value.toString()
            }
        }
        val sourceRenderer = when {
            manifest.config.composeEngine.equals("miuix", ignoreCase = true) ||
                manifest.config.useMiuixMonet -> NgThemePackageSourceRenderer.MIUIX
            manifest.config.composeEngine.equals("material", ignoreCase = true) ->
                NgThemePackageSourceRenderer.MATERIAL
            else -> NgThemePackageSourceRenderer.UNKNOWN
        }
        return NgThemePackageSpec(
            name = inspection.name,
            sourceFormat = inspection.format,
            sourceRenderer = sourceRenderer,
            rendererConverted = sourceRenderer != NgThemePackageSourceRenderer.MATERIAL,
            themeModeHint = config.stringOrNull("themeMode"),
            colorProfile = portableColorProfile(manifest.config),
            backgroundProfile = portableBackgroundProfile(manifest, inspection.assetBindings),
            normalizedFields = known,
            resources = inspection.assetBindings,
            coverAlbums = manifest.coverAlbums,
            coverSelection = manifest.coverSelection,
            unknownFields = unknown,
            rawManifestJson = inspection.rawManifestJson,
            warnings = inspection.warnings,
        )
    }

    private fun normalizeLegacy(inspection: Md3ThemePackageInspection): NgThemePackageSpec {
        val root = requireNotNull(inspection.legacyRoot)
        val config = root.getAsJsonObject("config")
        val day = config.getAsJsonObject("dayTheme")
        val night = config.getAsJsonObject("nightTheme")
        val normalized = linkedMapOf<String, JsonElement>()
        addLegacyColorVariant(normalized, day, night = false)
        night?.let { addLegacyColorVariant(normalized, it, night = true) }
        addLegacyBackgroundVariant(normalized, day, night = false)
        night?.let { addLegacyBackgroundVariant(normalized, it, night = true) }

        val dayBottom = root.getAsJsonObject("dayBottomBar")
        val nightBottom = root.getAsJsonObject("nightBottomBar")
        val floating = listOfNotNull(dayBottom, nightBottom)
            .any { it.stringOrNull("layoutMode").equals("floating", ignoreCase = true) }
        val glass = listOfNotNull(dayBottom, nightBottom)
            .any { it.stringOrNull("effectMode").equals("glass", ignoreCase = true) }
        normalized["useFloatingBottomBar"] = JsonPrimitive(floating)
        normalized["useFloatingBottomBarLiquidGlass"] = JsonPrimitive(glass)

        val coverAlbums = legacyCoverAlbums(root, inspection.name)
        val warnings = buildList {
            addAll(inspection.warnings)
            add("旧版普通/选中导航图标作为 legacy 资源保留，等待 NG Shell 消费器映射")
            if (coverAlbums.isNotEmpty()) add("旧版日夜封面已合并为一个 NG 封面图集预览")
            if (day.booleanOrNull("transparentNavBar") == true ||
                night?.booleanOrNull("transparentNavBar") == true
            ) {
                add("旧版 transparentNavBar 仅保留兼容证据，不覆盖 NG 界面栏透明设置")
            }
        }
        return NgThemePackageSpec(
            name = inspection.name,
            sourceFormat = inspection.format,
            sourceRenderer = NgThemePackageSourceRenderer.LEGACY_VIEW,
            rendererConverted = true,
            themeModeHint = null,
            colorProfile = legacyColorProfile(day, night),
            backgroundProfile = legacyBackgroundProfile(inspection.assetBindings, day, night),
            normalizedFields = normalized.mapValues { it.value.toString() },
            resources = inspection.assetBindings,
            coverAlbums = coverAlbums,
            coverSelection = Md3ThemePackageCoverSelection(
                albumRef = coverAlbums.firstOrNull()?.ref,
            ),
            unknownFields = emptyMap(),
            rawManifestJson = inspection.rawManifestJson,
            warnings = warnings,
        )
    }

    private fun portableColorProfile(config: Md3ThemeExportData): NgThemePackageColorProfile {
        val lightManual = config.manualColors(night = false)
        val darkManual = config.manualColors(night = true)
        return NgThemePackageColorProfile(
            paletteStyle = config.paletteStyle.toNgPaletteStyle(),
            contrast = config.customContrast.toNgContrast(),
            colorSpec = config.materialVersion.toNgColorSpec(),
            pureBlack = config.isPureBlack,
            light = config.appearanceColors(lightManual, night = false),
            dark = config.appearanceColors(darkManual, night = true),
        )
    }

    private fun Md3ThemeExportData.appearanceColors(
        manual: NgThemePackageManualColors,
        night: Boolean,
    ): NgThemePackageAppearanceColors {
        val source = when (appTheme) {
            "0" -> NgThemePackageColorSource.DYNAMIC
            "12" -> if (enableDeepPersonalization && manual.hasAnyExplicitColor) {
                NgThemePackageColorSource.MANUAL
            } else {
                NgThemePackageColorSource.PALETTE
            }
            else -> NgThemePackageColorSource.BUILT_IN
        }
        return NgThemePackageAppearanceColors(
            source = source,
            sourceThemeCode = appTheme,
            seed = (if (night) cNPrimary else cPrimary).takeUnless { it == 0 },
            manual = manual.takeIf { source == NgThemePackageColorSource.MANUAL },
        )
    }

    private fun Md3ThemeExportData.manualColors(night: Boolean) =
        NgThemePackageManualColors(
            primary = (if (night) themeColorNight else themeColor).asOptionalMd3Color(),
            secondary = (if (night) secondaryThemeColorNight else secondaryThemeColor)
                .asOptionalMd3Color(),
            primaryText = (if (night) primaryTextColorNight else primaryTextColor)
                .asOptionalMd3Color(),
            secondaryText = (if (night) secondaryTextColorNight else secondaryTextColor)
                .asOptionalMd3Color(),
            background = (if (night) themeBackgroundColorNight else themeBackgroundColor)
                .asOptionalMd3Color(),
            labelContainer = (if (night) labelContainerColorNight else labelContainerColor)
                .asOptionalMd3Color(),
        )

    private fun portableBackgroundProfile(
        manifest: Md3ThemePackageManifest,
        assets: Map<String, String>,
    ) = NgThemePackageBackgroundProfile(
        light = NgThemePackageBackgroundVariant(
            archivePath = assets[Md3ThemeAssetSlots.BACKGROUND_LIGHT]
                ?: manifest.config.bgImageLight,
            blur = manifest.config.bgImageBlurring.coerceIn(0, 25),
        ),
        dark = NgThemePackageBackgroundVariant(
            archivePath = assets[Md3ThemeAssetSlots.BACKGROUND_DARK]
                ?: manifest.config.bgImageDark,
            blur = manifest.config.bgImageNBlurring.coerceIn(0, 25),
        ),
        largeContainerLight = assets[Md3ThemeAssetSlots.CONTAINER_LARGE_LIGHT]
            ?: manifest.config.largeContainerBackgroundImageLight,
        largeContainerDark = assets[Md3ThemeAssetSlots.CONTAINER_LARGE_DARK]
            ?: manifest.config.largeContainerBackgroundImageDark,
        itemLight = assets[Md3ThemeAssetSlots.CONTAINER_ITEM_LIGHT]
            ?: manifest.config.itemBackgroundImageLight,
        itemDark = assets[Md3ThemeAssetSlots.CONTAINER_ITEM_DARK]
            ?: manifest.config.itemBackgroundImageDark,
        containerBackgroundEnabled = manifest.config.enableContainerBackgroundImage,
    )

    private fun legacyColorProfile(
        day: JsonObject,
        night: JsonObject?,
    ) = NgThemePackageColorProfile(
        paletteStyle = NgPaletteStyle.TONAL_SPOT,
        contrast = NgContrastLevel.DEFAULT,
        colorSpec = NgColorSpec.MATERIAL_3_2021,
        pureBlack = false,
        light = day.legacyAppearanceColors(),
        dark = (night ?: day).legacyAppearanceColors(),
    )

    private fun JsonObject.legacyAppearanceColors(): NgThemePackageAppearanceColors {
        val manual = NgThemePackageManualColors(
            primary = requiredColor("accentColor"),
            secondary = requiredColor("primaryColor"),
            primaryText = NgColorMath.contentColorFor(requiredColor("backgroundColor")),
            secondaryText = NgColorMath.contentColorFor(requiredColor("bottomBackground")),
            background = requiredColor("backgroundColor"),
            labelContainer = requiredColor("bottomBackground"),
        )
        return NgThemePackageAppearanceColors(
            source = NgThemePackageColorSource.MANUAL,
            sourceThemeCode = "legacy",
            seed = manual.primary,
            manual = manual,
        )
    }

    private fun legacyBackgroundProfile(
        assets: Map<String, String>,
        day: JsonObject,
        night: JsonObject?,
    ) = NgThemePackageBackgroundProfile(
        light = NgThemePackageBackgroundVariant(
            archivePath = assets[Md3ThemeAssetSlots.BACKGROUND_LIGHT],
            blur = (day.intOrNull("backgroundImgBlur") ?: 0).coerceIn(0, 25),
        ),
        dark = NgThemePackageBackgroundVariant(
            archivePath = assets[Md3ThemeAssetSlots.BACKGROUND_DARK],
            blur = (night?.intOrNull("backgroundImgBlur") ?: 0).coerceIn(0, 25),
        ),
        largeContainerLight = null,
        largeContainerDark = null,
        itemLight = null,
        itemDark = null,
        containerBackgroundEnabled = false,
    )

    private fun addLegacyColorVariant(
        output: MutableMap<String, JsonElement>,
        source: JsonObject,
        night: Boolean,
    ) {
        val primary = source.requiredColor("accentColor")
        val secondary = source.requiredColor("primaryColor")
        val background = source.requiredColor("backgroundColor")
        val label = source.requiredColor("bottomBackground")
        val suffix = if (night) "Night" else ""
        output["themeColor$suffix"] = JsonPrimitive(primary)
        output["secondaryThemeColor$suffix"] = JsonPrimitive(secondary)
        output["primaryTextColor$suffix"] = JsonPrimitive(NgColorMath.contentColorFor(background))
        output["secondaryTextColor$suffix"] = JsonPrimitive(NgColorMath.contentColorFor(label))
        output["themeBackgroundColor$suffix"] = JsonPrimitive(background)
        output["labelContainerColor$suffix"] = JsonPrimitive(label)
        output[if (night) "cNPrimary" else "cPrimary"] = JsonPrimitive(primary)
    }

    private fun addLegacyBackgroundVariant(
        output: MutableMap<String, JsonElement>,
        source: JsonObject,
        night: Boolean,
    ) {
        source.stringOrNull("backgroundImgPath")?.let { path ->
            output[if (night) "bgImageDark" else "bgImageLight"] = JsonPrimitive(path)
        }
        source.intOrNull("backgroundImgBlur")?.let { blur ->
            output[if (night) "bgImageNBlurring" else "bgImageBlurring"] =
                JsonPrimitive(blur)
        }
    }

    private fun legacyCoverAlbums(
        root: JsonObject,
        fallbackName: String,
    ): List<Md3ThemePackageCoverAlbum> {
        val light = root.getAsJsonObject("dayCover")?.getAsJsonArray("images")
            ?.mapNotNull { it.takeUnless { value -> value.isJsonNull }?.asString }
            .orEmpty()
        val dark = root.getAsJsonObject("nightCover")?.getAsJsonArray("images")
            ?.mapNotNull { it.takeUnless { value -> value.isJsonNull }?.asString }
            .orEmpty()
        if (light.isEmpty() && dark.isEmpty()) return emptyList()
        val name = root.getAsJsonObject("dayCover")?.stringOrNull("name") ?: fallbackName
        return listOf(
            Md3ThemePackageCoverAlbum(
                ref = "legacy.default",
                name = name,
                lightImages = light.map(::Md3ThemePackageCoverImage),
                darkImages = dark.map(::Md3ThemePackageCoverImage),
            )
        )
    }

    private fun parseRoot(rawJson: String): JsonObject =
        com.google.gson.JsonParser.parseString(rawJson).asJsonObject

    private fun JsonObject.requiredColor(name: String): Int {
        val raw = stringOrNull(name) ?: error("旧版主题缺少颜色字段: $name")
        return requireNotNull(parseNgColor(raw)) { "旧版主题颜色无效: $name=$raw" }
    }

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonObject.intOrNull(name: String): Int? =
        get(name)?.takeUnless { it.isJsonNull }?.asInt

    private fun JsonObject.booleanOrNull(name: String): Boolean? =
        get(name)?.takeUnless { it.isJsonNull }?.asBoolean

    private fun Int.asOptionalMd3Color(): Int? = takeUnless { it == 0 }

    private fun String.toNgPaletteStyle(): NgPaletteStyle = when (this) {
        "neutral" -> NgPaletteStyle.NEUTRAL
        "vibrant" -> NgPaletteStyle.VIBRANT
        "expressive" -> NgPaletteStyle.EXPRESSIVE
        "rainbow" -> NgPaletteStyle.RAINBOW
        "fruitSalad" -> NgPaletteStyle.FRUIT_SALAD
        "monochrome" -> NgPaletteStyle.MONOCHROME
        "fidelity" -> NgPaletteStyle.FIDELITY
        "content" -> NgPaletteStyle.CONTENT
        else -> NgPaletteStyle.TONAL_SPOT
    }

    private fun String.toNgContrast(): NgContrastLevel = when (lowercase()) {
        "medium" -> NgContrastLevel.MEDIUM
        "high" -> NgContrastLevel.HIGH
        else -> NgContrastLevel.DEFAULT
    }

    private fun String.toNgColorSpec(): NgColorSpec =
        if (equals("material3Expressive", ignoreCase = true)) {
            NgColorSpec.MATERIAL_3_EXPRESSIVE_2025
        } else {
            NgColorSpec.MATERIAL_3_2021
        }
}
