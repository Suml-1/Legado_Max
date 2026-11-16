package io.legado.app.help.config.md3

import androidx.annotation.Keep
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

internal enum class Md3ThemePackageFormat {
    PORTABLE_V1,
    LEGACY_APPLICATION_THEME_V1,
}

/**
 * MD3 portable V1 的完整配置 DTO。
 *
 * 这些字段是外部主题包协议，不是 Reading NG 的运行时主题模型。字段必须完整保留，
 * 后续由规范化层按职责映射到 NG Profile、用户覆盖项或仅保留区。
 */
@Keep
internal data class Md3ThemeExportData(
    @SerializedName("appTheme") val appTheme: String = "0",
    @SerializedName("themeMode") val themeMode: String = "0",
    @SerializedName("isPureBlack") val isPureBlack: Boolean = false,
    @SerializedName("composeEngine") val composeEngine: String = "material",
    @SerializedName("paletteStyle") val paletteStyle: String = "tonalSpot",
    @SerializedName("materialVersion") val materialVersion: String = "material3",
    @SerializedName("customMode") val customMode: String? = "tonalSpot",
    @SerializedName("customContrast") val customContrast: String = "Default",
    @SerializedName("launcherIcon") val launcherIcon: String = "ic_launcher",
    @SerializedName("isPredictiveBackEnabled") val isPredictiveBackEnabled: Boolean = true,
    @SerializedName("fontScale") val fontScale: Int = 10,
    @SerializedName("enableDeepPersonalization") val enableDeepPersonalization: Boolean = false,
    @SerializedName("cPrimary") val cPrimary: Int = 0,
    @SerializedName("cNPrimary") val cNPrimary: Int = 0,
    @SerializedName("themeColor") val themeColor: Int = 0,
    @SerializedName("secondaryThemeColor") val secondaryThemeColor: Int = 0,
    @SerializedName("primaryTextColor") val primaryTextColor: Int = 0,
    @SerializedName("secondaryTextColor") val secondaryTextColor: Int = 0,
    @SerializedName("themeBackgroundColor") val themeBackgroundColor: Int = 0,
    @SerializedName("labelContainerColor") val labelContainerColor: Int = 0,
    @SerializedName("themeColorNight") val themeColorNight: Int = 0,
    @SerializedName("secondaryThemeColorNight") val secondaryThemeColorNight: Int = 0,
    @SerializedName("primaryTextColorNight") val primaryTextColorNight: Int = 0,
    @SerializedName("secondaryTextColorNight") val secondaryTextColorNight: Int = 0,
    @SerializedName("themeBackgroundColorNight") val themeBackgroundColorNight: Int = 0,
    @SerializedName("labelContainerColorNight") val labelContainerColorNight: Int = 0,
    @SerializedName("bookInfoInputColor") val bookInfoInputColor: Int = 0,
    @SerializedName("bookInfoFollowCoverColor") val bookInfoFollowCoverColor: Boolean = true,
    @SerializedName("bookInfoBackgroundBlur") val bookInfoBackgroundBlur: String = "on",
    @SerializedName("bookInfoNetworkCoverBackground")
    val bookInfoNetworkCoverBackground: String? = null,
    @SerializedName("bookInfoDefaultCoverBackground")
    val bookInfoDefaultCoverBackground: String? = null,
    @SerializedName("containerOpacity") val containerOpacity: Int = 100,
    @SerializedName("overrideBaseCardCornerRadius")
    val overrideBaseCardCornerRadius: Boolean = false,
    @SerializedName("baseCardCornerRadius") val baseCardCornerRadius: Float = 16f,
    @SerializedName("overrideBaseCardBorder") val overrideBaseCardBorder: Boolean = false,
    @SerializedName("baseCardBorderWidth") val baseCardBorderWidth: Float = 1f,
    @SerializedName("baseCardBorderColor") val baseCardBorderColor: Int = 0,
    @SerializedName("baseCardBorderColorNight") val baseCardBorderColorNight: Int = 0,
    @SerializedName("disableSplicedColumnGroupCornerRadius")
    val disableSplicedColumnGroupCornerRadius: Boolean = false,
    @SerializedName("enableItemDivider") val enableItemDivider: Boolean = false,
    @SerializedName("itemDividerWidth") val itemDividerWidth: Float = 1f,
    @SerializedName("itemDividerLength") val itemDividerLength: Float = 80f,
    @SerializedName("itemDividerColor") val itemDividerColor: Int = 0,
    @SerializedName("enableBlur") val enableBlur: Boolean = false,
    @SerializedName("enableProgressiveBlur") val enableProgressiveBlur: Boolean = false,
    @SerializedName("topBarBlurRadius") val topBarBlurRadius: Int = 24,
    @SerializedName("bottomBarBlurRadius") val bottomBarBlurRadius: Int = 8,
    @SerializedName("topBarBlurAlpha") val topBarBlurAlpha: Int = 73,
    @SerializedName("bottomBarBlurAlpha") val bottomBarBlurAlpha: Int = 40,
    @SerializedName("bottomBarLensRadius") val bottomBarLensRadius: Float = 24f,
    @SerializedName("topBarOpacity") val topBarOpacity: Int = 100,
    @SerializedName("bottomBarOpacity") val bottomBarOpacity: Int = 100,
    @SerializedName("enableCustomTagColors") val enableCustomTagColors: Boolean = false,
    @SerializedName("customTagColorsJson") val customTagColorsJson: String? = null,
    @SerializedName("bookshelfCardColor") val bookshelfCardColor: Int = 0,
    @SerializedName("bookshelfCardColorDark") val bookshelfCardColorDark: Int = 0,
    @SerializedName("showHome") val showHome: Boolean = true,
    @SerializedName("showDiscovery") val showDiscovery: Boolean = true,
    @SerializedName("showRss") val showRss: Boolean = true,
    @SerializedName("showStatusBar") val showStatusBar: Boolean = true,
    @SerializedName("swipeAnimation") val swipeAnimation: Boolean = true,
    @SerializedName("showBottomView") val showBottomView: Boolean = true,
    @SerializedName("useFloatingBottomBar") val useFloatingBottomBar: Boolean = false,
    @SerializedName("useFloatingBottomBarLiquidGlass")
    val useFloatingBottomBarLiquidGlass: Boolean = false,
    @SerializedName("floatingBottomBarBottomDistancePx")
    val floatingBottomBarBottomDistancePx: Int? = null,
    @SerializedName("floatingBottomBarTransparency")
    val floatingBottomBarTransparency: Int? = null,
    @SerializedName("bookshelfTopBarStyle") val bookshelfTopBarStyle: String? = null,
    @SerializedName("bookshelfFloatingDockTopDistancePx")
    val bookshelfFloatingDockTopDistancePx: Int? = null,
    @SerializedName("bookshelfFloatingDockTransparency")
    val bookshelfFloatingDockTransparency: Int? = null,
    @SerializedName("tabletInterface") val tabletInterface: String = "auto",
    @SerializedName("labelVisibilityMode") val labelVisibilityMode: String = "auto",
    @SerializedName("defaultHomePage") val defaultHomePage: String = "bookshelf",
    @SerializedName("mainNavigationOrder")
    val mainNavigationOrder: String = "home,bookshelf,explore,rss,my",
    @SerializedName("navIconHome") val navIconHome: String = "",
    @SerializedName("navIconBookshelf") val navIconBookshelf: String = "",
    @SerializedName("navIconExplore") val navIconExplore: String = "",
    @SerializedName("navIconRss") val navIconRss: String = "",
    @SerializedName("navIconMy") val navIconMy: String = "",
    @SerializedName("useMiuixMonet") val useMiuixMonet: Boolean = false,
    @SerializedName("useFlexibleTopAppBar") val useFlexibleTopAppBar: Boolean = true,
    @SerializedName("bgImageLight") val bgImageLight: String? = null,
    @SerializedName("bgImageDark") val bgImageDark: String? = null,
    @SerializedName("bgImageBlurring") val bgImageBlurring: Int = 0,
    @SerializedName("bgImageNBlurring") val bgImageNBlurring: Int = 0,
    @SerializedName("largeContainerBackgroundImageLight")
    val largeContainerBackgroundImageLight: String? = null,
    @SerializedName("largeContainerBackgroundImageDark")
    val largeContainerBackgroundImageDark: String? = null,
    @SerializedName("itemBackgroundImageLight") val itemBackgroundImageLight: String? = null,
    @SerializedName("itemBackgroundImageDark") val itemBackgroundImageDark: String? = null,
    @SerializedName("enableContainerBackgroundImage")
    val enableContainerBackgroundImage: Boolean = false,
    @SerializedName("appColumnBackgroundOpacity") val appColumnBackgroundOpacity: Int = 100,
    @SerializedName("glassCardBackgroundOpacity") val glassCardBackgroundOpacity: Int = 100,
    @SerializedName("appFontPath") val appFontPath: String? = null,
    @SerializedName("selectedCoverAlbumId") val selectedCoverAlbumId: String? = null,
    @SerializedName("coverLoadOnlyWifi") val coverLoadOnlyWifi: Boolean = false,
    @SerializedName("coverUseDefault") val coverUseDefault: Boolean = false,
    @SerializedName("coverShowShadow") val coverShowShadow: Boolean = false,
    @SerializedName("coverShowStroke") val coverShowStroke: Boolean = true,
    @SerializedName("coverDefaultColor") val coverDefaultColor: Boolean = true,
    @SerializedName("coverDefaultImage") val coverDefaultImage: String = "",
    @SerializedName("coverTextColor") val coverTextColor: Int = -16777216,
    @SerializedName("coverShadowColor") val coverShadowColor: Int = -16777216,
    @SerializedName("coverShowName") val coverShowName: Boolean = true,
    @SerializedName("coverShowAuthor") val coverShowAuthor: Boolean = true,
    @SerializedName("coverDefaultImageDark") val coverDefaultImageDark: String = "",
    @SerializedName("coverTextColorN") val coverTextColorN: Int = -1,
    @SerializedName("coverShadowColorN") val coverShadowColorN: Int = -1,
    @SerializedName("coverShowNameN") val coverShowNameN: Boolean = true,
    @SerializedName("coverShowAuthorN") val coverShowAuthorN: Boolean = true,
    @SerializedName("coverInfoOrientation") val coverInfoOrientation: String = "0",
    @SerializedName("assets") val assets: Map<String, String>? = null,
)

@Keep
internal data class Md3ThemePackageManifest(
    @SerializedName("formatVersion") val formatVersion: Int = 1,
    @SerializedName("name") val name: String? = null,
    @SerializedName("config") val config: Md3ThemeExportData = Md3ThemeExportData(),
    @SerializedName("assets") val assets: Map<String, String> = emptyMap(),
    @SerializedName("coverAlbums")
    val coverAlbums: List<Md3ThemePackageCoverAlbum> = emptyList(),
    @SerializedName("coverSelection")
    val coverSelection: Md3ThemePackageCoverSelection = Md3ThemePackageCoverSelection(),
)

@Keep
internal data class Md3ThemePackageCoverAlbum(
    @SerializedName("ref") val ref: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("lightImages")
    val lightImages: List<Md3ThemePackageCoverImage> = emptyList(),
    @SerializedName("darkImages")
    val darkImages: List<Md3ThemePackageCoverImage> = emptyList(),
)

@Keep
internal data class Md3ThemePackageCoverImage(
    @SerializedName("path") val path: String = "",
)

@Keep
internal data class Md3ThemePackageCoverSelection(
    @SerializedName("albumRef") val albumRef: String? = null,
)

internal data class Md3ThemePackageInspection(
    val format: Md3ThemePackageFormat,
    val name: String,
    val manifest: Md3ThemePackageManifest?,
    val legacyRoot: JsonObject?,
    val rawManifestJson: String,
    val archiveEntries: Set<String>,
    val presentConfigFields: Set<String>,
    val unknownConfigFields: Set<String>,
    val assetBindings: Map<String, String>,
    val warnings: List<String>,
)
