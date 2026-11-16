package io.legado.app.help.config.md3

import androidx.annotation.ColorInt
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.ui.design.theme.NgColorSpec
import io.legado.app.ui.design.theme.NgContrastLevel
import io.legado.app.ui.design.theme.NgPaletteStyle

/**
 * 外部主题包进入 Reading NG 后的无副作用规范化结果。
 *
 * `normalizedFields` 使用 JSON 字面量保存值，可避免尚未接入消费者的字段在中间层丢失。
 * 运行时必须按 coverage registry 的 area/disposition 显式消费，而不是整包写入偏好。
 */
@Keep
internal data class NgThemePackageSpec(
    @SerializedName("schemaVersion") val schemaVersion: Int = SCHEMA_VERSION,
    @SerializedName("name") val name: String,
    @SerializedName("sourceFormat") val sourceFormat: Md3ThemePackageFormat,
    @SerializedName("sourceRenderer") val sourceRenderer: NgThemePackageSourceRenderer,
    @SerializedName("targetRenderer")
    val targetRenderer: NgThemePackageTargetRenderer = NgThemePackageTargetRenderer.READING_NG,
    @SerializedName("rendererConverted") val rendererConverted: Boolean,
    @SerializedName("themeModeHint") val themeModeHint: String?,
    @SerializedName("colorProfile") val colorProfile: NgThemePackageColorProfile,
    @SerializedName("backgroundProfile") val backgroundProfile: NgThemePackageBackgroundProfile,
    @SerializedName("normalizedFields") val normalizedFields: Map<String, String>,
    @SerializedName("resources") val resources: Map<String, String>,
    @SerializedName("coverAlbums") val coverAlbums: List<Md3ThemePackageCoverAlbum>,
    @SerializedName("coverSelection") val coverSelection: Md3ThemePackageCoverSelection,
    @SerializedName("unknownFields") val unknownFields: Map<String, String>,
    @SerializedName("rawManifestJson") val rawManifestJson: String,
    @SerializedName("warnings") val warnings: List<String>,
) {
    fun fields(area: Md3ThemeFieldArea): Map<String, String> = normalizedFields.filterKeys { name ->
        Md3ThemeCoverageRegistry.byName[name]?.area == area
    }

    fun fields(disposition: Md3ThemeFieldDisposition): Map<String, String> =
        normalizedFields.filterKeys { name ->
            Md3ThemeCoverageRegistry.byName[name]?.disposition == disposition
        }

    companion object {
        const val SCHEMA_VERSION = 2
    }
}

/**
 * 兼容层保留每个明暗外观各自的颜色来源。
 *
 * MD3 允许日间使用手动六色、夜间继续使用种子色生成；这里不能提前压成
 * [io.legado.app.ui.design.theme.NgColorSystem] 的单一 mode，否则导入前就已丢失语义。
 */
@Keep
internal data class NgThemePackageColorProfile(
    @SerializedName("paletteStyle") val paletteStyle: NgPaletteStyle,
    @SerializedName("contrast") val contrast: NgContrastLevel,
    @SerializedName("colorSpec") val colorSpec: NgColorSpec,
    @SerializedName("pureBlack") val pureBlack: Boolean,
    @SerializedName("light") val light: NgThemePackageAppearanceColors,
    @SerializedName("dark") val dark: NgThemePackageAppearanceColors,
)

@Keep
internal data class NgThemePackageAppearanceColors(
    @SerializedName("source") val source: NgThemePackageColorSource,
    @SerializedName("sourceThemeCode") val sourceThemeCode: String,
    @SerializedName("seed") @ColorInt val seed: Int?,
    @SerializedName("manual") val manual: NgThemePackageManualColors?,
)

internal enum class NgThemePackageColorSource {
    DYNAMIC,
    BUILT_IN,
    PALETTE,
    MANUAL,
}

/** null 表示 MD3 在该槽位使用生成色，而不是透明黑。 */
@Keep
internal data class NgThemePackageManualColors(
    @SerializedName("primary") @ColorInt val primary: Int?,
    @SerializedName("secondary") @ColorInt val secondary: Int?,
    @SerializedName("primaryText") @ColorInt val primaryText: Int?,
    @SerializedName("secondaryText") @ColorInt val secondaryText: Int?,
    @SerializedName("background") @ColorInt val background: Int?,
    @SerializedName("labelContainer") @ColorInt val labelContainer: Int?,
) {
    val hasAnyExplicitColor: Boolean
        get() = listOf(
            primary,
            secondary,
            primaryText,
            secondaryText,
            background,
            labelContainer,
        ).any { it != null }
}

@Keep
internal data class NgThemePackageBackgroundProfile(
    @SerializedName("light") val light: NgThemePackageBackgroundVariant,
    @SerializedName("dark") val dark: NgThemePackageBackgroundVariant,
    @SerializedName("largeContainerLight") val largeContainerLight: String?,
    @SerializedName("largeContainerDark") val largeContainerDark: String?,
    @SerializedName("itemLight") val itemLight: String?,
    @SerializedName("itemDark") val itemDark: String?,
    @SerializedName("containerBackgroundEnabled") val containerBackgroundEnabled: Boolean,
)

@Keep
internal data class NgThemePackageBackgroundVariant(
    @SerializedName("archivePath") val archivePath: String?,
    @SerializedName("blur") val blur: Int,
)

internal enum class NgThemePackageSourceRenderer {
    MATERIAL,
    MIUIX,
    LEGACY_VIEW,
    UNKNOWN,
}

internal enum class NgThemePackageTargetRenderer {
    READING_NG,
}

internal data class NgThemePackagePreview(
    val spec: NgThemePackageSpec,
    val compatibility: Md3ThemeCompatibilityReport,
)
