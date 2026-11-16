package io.legado.app.help.config.md3

internal enum class Md3ThemeFieldArea {
    APPEARANCE,
    COLOR,
    RENDERER,
    SURFACE,
    APP_SHELL,
    BACKGROUND,
    TYPOGRAPHY,
    BOOK_INFO,
    BOOKSHELF,
    COVER,
    TRANSPORT,
}

internal enum class Md3ThemeFieldDisposition {
    /** 主题视觉本身，进入 NG Theme Profile。 */
    THEME_PROFILE,

    /** 包内保留基线，但允许设备或用户设置覆盖。 */
    USER_OVERRIDE,

    /** 来源引擎或枚举需要转换成 NG 语义，不直接进入运行时。 */
    NORMALIZE_TO_NG,

    /** 属于书架、书籍详情或封面等功能主题。 */
    FEATURE_PROFILE,

    /** 只用于归档运输，不是运行时配置。 */
    TRANSPORT_ONLY,
}

internal enum class Md3ThemeTransportCoverage {
    LOSSLESS,
}

internal enum class Md3ThemeStateOwner {
    THEME_PROFILE,
    USER_PREFERENCE,
    FEATURE_PROFILE,
    TRANSPORT_ARCHIVE,
}

internal enum class Md3ThemeRuntimeOwner {
    APPEARANCE,
    COLOR_RESOLVER,
    RENDERER_ADAPTER,
    SURFACE_RESOLVER,
    APP_SHELL,
    BACKGROUND_RESOLVER,
    TYPOGRAPHY,
    BOOK_INFO,
    BOOKSHELF,
    COVER,
    NONE,
}

internal enum class Md3ThemeRoundTripCoverage {
    PRESERVE_SOURCE_LITERAL,
}

internal enum class Md3ThemeRuntimeCoverage {
    /** 已能无损读取并进入统一 Profile，但运行时消费者尚未全部接入。 */
    PROFILE_READY,

    /** 纯运输字段不应进入运行时。 */
    NOT_APPLICABLE,
}

internal data class Md3ThemeFieldSpec(
    val name: String,
    val area: Md3ThemeFieldArea,
    val disposition: Md3ThemeFieldDisposition,
) {
    val transport: Md3ThemeTransportCoverage = Md3ThemeTransportCoverage.LOSSLESS

    val stateOwner: Md3ThemeStateOwner = when (disposition) {
        Md3ThemeFieldDisposition.THEME_PROFILE,
        Md3ThemeFieldDisposition.NORMALIZE_TO_NG -> Md3ThemeStateOwner.THEME_PROFILE
        Md3ThemeFieldDisposition.USER_OVERRIDE -> Md3ThemeStateOwner.USER_PREFERENCE
        Md3ThemeFieldDisposition.FEATURE_PROFILE -> Md3ThemeStateOwner.FEATURE_PROFILE
        Md3ThemeFieldDisposition.TRANSPORT_ONLY -> Md3ThemeStateOwner.TRANSPORT_ARCHIVE
    }

    val runtimeOwner: Md3ThemeRuntimeOwner = when (area) {
        Md3ThemeFieldArea.APPEARANCE -> Md3ThemeRuntimeOwner.APPEARANCE
        Md3ThemeFieldArea.COLOR -> Md3ThemeRuntimeOwner.COLOR_RESOLVER
        Md3ThemeFieldArea.RENDERER -> Md3ThemeRuntimeOwner.RENDERER_ADAPTER
        Md3ThemeFieldArea.SURFACE -> Md3ThemeRuntimeOwner.SURFACE_RESOLVER
        Md3ThemeFieldArea.APP_SHELL -> Md3ThemeRuntimeOwner.APP_SHELL
        Md3ThemeFieldArea.BACKGROUND -> Md3ThemeRuntimeOwner.BACKGROUND_RESOLVER
        Md3ThemeFieldArea.TYPOGRAPHY -> Md3ThemeRuntimeOwner.TYPOGRAPHY
        Md3ThemeFieldArea.BOOK_INFO -> Md3ThemeRuntimeOwner.BOOK_INFO
        Md3ThemeFieldArea.BOOKSHELF -> Md3ThemeRuntimeOwner.BOOKSHELF
        Md3ThemeFieldArea.COVER -> Md3ThemeRuntimeOwner.COVER
        Md3ThemeFieldArea.TRANSPORT -> Md3ThemeRuntimeOwner.NONE
    }

    val roundTrip: Md3ThemeRoundTripCoverage =
        Md3ThemeRoundTripCoverage.PRESERVE_SOURCE_LITERAL

    val runtimeCoverage: Md3ThemeRuntimeCoverage =
        if (disposition == Md3ThemeFieldDisposition.TRANSPORT_ONLY) {
            Md3ThemeRuntimeCoverage.NOT_APPLICABLE
        } else {
            Md3ThemeRuntimeCoverage.PROFILE_READY
        }
}

/** MD3 portable V1 的 105 字段机器可读登记表。 */
internal object Md3ThemeCoverageRegistry {

    val fields: List<Md3ThemeFieldSpec> = buildList {
        addFields(
            Md3ThemeFieldArea.APPEARANCE,
            Md3ThemeFieldDisposition.USER_OVERRIDE,
            "themeMode",
            "launcherIcon",
            "isPredictiveBackEnabled",
            "fontScale",
        )
        addFields(
            Md3ThemeFieldArea.COLOR,
            Md3ThemeFieldDisposition.NORMALIZE_TO_NG,
            "appTheme",
            "paletteStyle",
            "materialVersion",
            "customMode",
            "customContrast",
            "enableDeepPersonalization",
            "cPrimary",
            "cNPrimary",
        )
        addFields(
            Md3ThemeFieldArea.COLOR,
            Md3ThemeFieldDisposition.THEME_PROFILE,
            "isPureBlack",
            "themeColor",
            "secondaryThemeColor",
            "primaryTextColor",
            "secondaryTextColor",
            "themeBackgroundColor",
            "labelContainerColor",
            "themeColorNight",
            "secondaryThemeColorNight",
            "primaryTextColorNight",
            "secondaryTextColorNight",
            "themeBackgroundColorNight",
            "labelContainerColorNight",
        )
        addFields(
            Md3ThemeFieldArea.RENDERER,
            Md3ThemeFieldDisposition.NORMALIZE_TO_NG,
            "composeEngine",
            "useMiuixMonet",
        )
        addFields(
            Md3ThemeFieldArea.BOOK_INFO,
            Md3ThemeFieldDisposition.FEATURE_PROFILE,
            "bookInfoInputColor",
            "bookInfoFollowCoverColor",
            "bookInfoBackgroundBlur",
            "bookInfoNetworkCoverBackground",
            "bookInfoDefaultCoverBackground",
        )
        addFields(
            Md3ThemeFieldArea.SURFACE,
            Md3ThemeFieldDisposition.THEME_PROFILE,
            "containerOpacity",
            "overrideBaseCardCornerRadius",
            "baseCardCornerRadius",
            "overrideBaseCardBorder",
            "baseCardBorderWidth",
            "baseCardBorderColor",
            "baseCardBorderColorNight",
            "disableSplicedColumnGroupCornerRadius",
            "enableItemDivider",
            "itemDividerWidth",
            "itemDividerLength",
            "itemDividerColor",
            "enableBlur",
            "enableProgressiveBlur",
            "topBarBlurRadius",
            "bottomBarBlurRadius",
            "topBarBlurAlpha",
            "bottomBarBlurAlpha",
            "bottomBarLensRadius",
            "topBarOpacity",
            "bottomBarOpacity",
            "appColumnBackgroundOpacity",
            "glassCardBackgroundOpacity",
        )
        addFields(
            Md3ThemeFieldArea.BOOKSHELF,
            Md3ThemeFieldDisposition.FEATURE_PROFILE,
            "enableCustomTagColors",
            "customTagColorsJson",
            "bookshelfCardColor",
            "bookshelfCardColorDark",
        )
        addFields(
            Md3ThemeFieldArea.APP_SHELL,
            Md3ThemeFieldDisposition.USER_OVERRIDE,
            "showHome",
            "showDiscovery",
            "showRss",
            "showStatusBar",
            "swipeAnimation",
            "tabletInterface",
            "defaultHomePage",
            "mainNavigationOrder",
        )
        addFields(
            Md3ThemeFieldArea.APP_SHELL,
            Md3ThemeFieldDisposition.THEME_PROFILE,
            "showBottomView",
            "useFloatingBottomBar",
            "useFloatingBottomBarLiquidGlass",
            "floatingBottomBarBottomDistancePx",
            "floatingBottomBarTransparency",
            "labelVisibilityMode",
            "navIconHome",
            "navIconBookshelf",
            "navIconExplore",
            "navIconRss",
            "navIconMy",
            "useFlexibleTopAppBar",
        )
        addFields(
            Md3ThemeFieldArea.BOOKSHELF,
            Md3ThemeFieldDisposition.FEATURE_PROFILE,
            "bookshelfTopBarStyle",
            "bookshelfFloatingDockTopDistancePx",
            "bookshelfFloatingDockTransparency",
        )
        addFields(
            Md3ThemeFieldArea.BACKGROUND,
            Md3ThemeFieldDisposition.THEME_PROFILE,
            "bgImageLight",
            "bgImageDark",
            "bgImageBlurring",
            "bgImageNBlurring",
            "largeContainerBackgroundImageLight",
            "largeContainerBackgroundImageDark",
            "itemBackgroundImageLight",
            "itemBackgroundImageDark",
            "enableContainerBackgroundImage",
        )
        addFields(
            Md3ThemeFieldArea.TYPOGRAPHY,
            Md3ThemeFieldDisposition.THEME_PROFILE,
            "appFontPath",
        )
        addFields(
            Md3ThemeFieldArea.COVER,
            Md3ThemeFieldDisposition.FEATURE_PROFILE,
            "selectedCoverAlbumId",
            "coverLoadOnlyWifi",
            "coverUseDefault",
            "coverShowShadow",
            "coverShowStroke",
            "coverDefaultColor",
            "coverDefaultImage",
            "coverTextColor",
            "coverShadowColor",
            "coverShowName",
            "coverShowAuthor",
            "coverDefaultImageDark",
            "coverTextColorN",
            "coverShadowColorN",
            "coverShowNameN",
            "coverShowAuthorN",
            "coverInfoOrientation",
        )
        addFields(
            Md3ThemeFieldArea.TRANSPORT,
            Md3ThemeFieldDisposition.TRANSPORT_ONLY,
            "assets",
        )
    }.also { specs ->
        check(specs.size == EXPECTED_FIELD_COUNT) {
            "MD3 field registry expected $EXPECTED_FIELD_COUNT entries, got ${specs.size}"
        }
        check(specs.map(Md3ThemeFieldSpec::name).distinct().size == specs.size) {
            "MD3 field registry contains duplicate names"
        }
    }

    val byName: Map<String, Md3ThemeFieldSpec> = fields.associateBy(Md3ThemeFieldSpec::name)

    val knownFieldNames: Set<String> = byName.keys

    const val EXPECTED_FIELD_COUNT = 110

    private fun MutableList<Md3ThemeFieldSpec>.addFields(
        area: Md3ThemeFieldArea,
        disposition: Md3ThemeFieldDisposition,
        vararg names: String,
    ) {
        names.forEach { name -> add(Md3ThemeFieldSpec(name, area, disposition)) }
    }
}

internal object Md3ThemeAssetSlots {
    const val BACKGROUND_LIGHT = "background.light"
    const val BACKGROUND_DARK = "background.dark"
    const val CONTAINER_LARGE_LIGHT = "container.large.light"
    const val CONTAINER_LARGE_DARK = "container.large.dark"
    const val CONTAINER_ITEM_LIGHT = "container.item.light"
    const val CONTAINER_ITEM_DARK = "container.item.dark"
    const val NAVIGATION_HOME = "navigation.home"
    const val NAVIGATION_BOOKSHELF = "navigation.bookshelf"
    const val NAVIGATION_EXPLORE = "navigation.explore"
    const val NAVIGATION_RSS = "navigation.rss"
    const val NAVIGATION_MY = "navigation.my"
    const val FONT_APP = "font.app"

    val all = setOf(
        BACKGROUND_LIGHT,
        BACKGROUND_DARK,
        CONTAINER_LARGE_LIGHT,
        CONTAINER_LARGE_DARK,
        CONTAINER_ITEM_LIGHT,
        CONTAINER_ITEM_DARK,
        NAVIGATION_HOME,
        NAVIGATION_BOOKSHELF,
        NAVIGATION_EXPLORE,
        NAVIGATION_RSS,
        NAVIGATION_MY,
        FONT_APP,
    )
}
