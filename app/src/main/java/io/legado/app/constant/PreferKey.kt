package io.legado.app.constant

@Suppress("ConstPropertyName")
object PreferKey {
    const val language = "language"
    const val fontScale = "fontScale"
    const val themeMode = "themeMode"
    const val userAgent = "userAgent"
    const val customHosts = "customHosts"
    const val videoSetting = "videoSetting"
    const val editTheme = "editTheme"
    const val editThemeDark = "editThemeDark"
    const val editTemeAuto = "editTemeAuto"
    const val showUnread = "showUnread"
    const val showBooknameLayout = "showBooknameLayout"
    const val bookshelfMargin = "bookshelfMargin"
    const val bookGroupStyle = "bookGroupStyle"
    const val useDefaultCover = "useDefaultCover"
    const val loadCoverOnlyWifi = "loadCoverOnlyWifi"
    const val loadCoverHighQuality = "loadCoverHighQuality"
    const val coverShowName = "coverShowName"
    const val coverShowAuthor = "coverShowAuthor"
    const val coverShowNameN = "coverShowNameN"
    const val coverShowAuthorN = "coverShowAuthorN"
    const val coverHtmlEnable = "coverHtmlEnable"
    const val bookCoverShadow = "bookCoverShadow"
    const val paragraphBubblePackage = "paragraphBubblePackage"
    const val forceSoftwareParagraphBubble = "forceSoftwareParagraphBubble"
    const val remoteServerId = "remoteServerId"
    const val hideStatusBar = "hideStatusBar"
    const val clickActionTL = "clickActionTopLeft"
    const val clickActionTC = "clickActionTopCenter"
    const val clickActionTR = "clickActionTopRight"
    const val clickActionML = "clickActionMiddleLeft"
    const val clickActionMC = "clickActionMiddleCenter"
    const val clickActionMR = "clickActionMiddleRight"
    const val clickActionBL = "clickActionBottomLeft"
    const val clickActionBC = "clickActionBottomCenter"
    const val clickActionBR = "clickActionBottomRight"
    const val hideNavigationBar = "hideNavigationBar"
    const val precisionSearch = "precisionSearch"
    const val showSearchProgress = "showSearchProgress"
    const val searchProgressFontSize = "searchProgressFontSize"
    const val readAloudByPage = "readAloudByPage"
    const val ttsEngine = "appTtsEngine"
    const val ttsFollowSys = "ttsFollowSys"
    const val ttsSpeechRate = "ttsSpeechRate"
    const val prevKeys = "prevKeyCodes"
    const val nextKeys = "nextKeyCodes"
    const val showDiscovery = "showDiscovery"
    const val enableReview = "enableReview"
    const val showRss = "showRss"
    const val debugMode = "debugMode" // 调试模式开关，控制"我的"页面调试工具入口显示
    const val debugLogFloatingBall = "debugLogFloatingBall" // 调试日志悬浮球开关，控制应用内悬浮球显示
    const val debugFloatingBallPosX = "debugFloatingBallPosX" // 调试悬浮球拖动位置 X（像素）
    const val debugFloatingBallPosY = "debugFloatingBallPosY" // 调试悬浮球拖动位置 Y（像素）
    const val debugLogOnlyEnabled = "debugLogOnlyEnabled" // 调试专属模式总开关
    const val debugLogOnlyCategories = "debugLogOnlyCategories" // 标记为"只进调试界面"的 DebugCategory 集合，逗号分隔
    const val copyCrashLog = "copyCrashLog" // 崩溃时复制日志到剪贴板
    const val askClipboardImport = "askClipboardImport" // 已废弃，仅用于向 clipboardImportMode 迁移
    const val clipboardImportMode = "clipboardImportMode" // 剪贴板导入口令模式，取值见 ClipboardImportMode
    const val mdLinkInnerBrowser = "mdLinkInnerBrowser" // MD链接用内置浏览器打开
    const val bookshelfLayout = "bookshelfLayout"
    const val bookshelfSort = "bookshelfSort"
    const val folderLayout = "folderLayout"
    const val bookLayout = "bookLayout"
    const val showMoreInfoInList = "showMoreInfoInList"
    const val showIntroInList = "showIntroInList"
    const val showCategoryInfoInList = "showCategoryInfoInList"

    // 书架显示阅读进度开关
    const val showBookshelfReadProgress = "showBookshelfReadProgress"

    // 书籍外边框开关（仅在列表/紧凑列表视图时生效）
    const val showBookBorder = "showBookBorder"

    // 简介显示行数（1-4行）
    const val introLinesInList = "introLinesInList"

    // 下拉选择分组开关（仅在分组样式为标签时生效）
    const val dropdownSelectGroup = "dropdownSelectGroup"

    // 显示标签栏开关
    const val showBookshelfTagBar = "showBookshelfTagBar"
    const val bookExportFileName = "bookExportFileName"
    const val bookImportFileName = "bookImportFileName"
    const val episodeExportFileName = "episodeExportFileName"
    const val recordLog = "recordLog"
    const val processText = "process_text"
    const val cleanCache = "cleanCache"
    const val saveTabPosition = "saveTabPosition"
    const val fontFolder = "fontFolder"
    const val backupPath = "backupUri"
    const val restoreIgnore = "restoreIgnore"
    const val restoreShowSelector = "restoreShowSelector"
    const val threadCount = "threadCount"
    const val cacheConcurrentRate = "cacheConcurrentRate"
    const val verboseUpdateLog = "verboseUpdateLog" // 详细更新日志开关
    const val webPort = "webPort"
    const val keepLight = "keep_light"
    const val webService = "webService"
    const val webDavUrl = "web_dav_url"
    const val webDavAccount = "web_dav_account"
    const val webDavPassword = "web_dav_password"
    const val webDavDir = "webDavDir"
    const val enableCustomExport = "enableCustomExport"
    const val exportToWebDav = "webDavCacheBackup"
    const val exportNoChapterName = "exportNoChapterName"
    const val exportType = "exportType"
    const val exportPictureFile = "exportPictureFile"
    const val changeSourceCheckAuthor = "changeSourceCheckAuthor"
    const val changeSourceLoadToc = "changeSourceLoadToc"
    const val changeSourceLoadInfo = "changeSourceLoadInfo"
    const val changeSourceLoadWordCount = "changeSourceLoadWordCount"
    const val changeSourceShowScore = "changeSourceShowScore"
    const val chineseConverterType = "chineseConverterType"
    const val launcherIcon = "launcherIcon"
    const val textSelectAble = "selectText"
    const val shareLayout = "shareLayout"
    const val mangaReadMode = "mangaReadMode"
    const val comicStyleSelect = "comicStyleSelect"
    const val readStyleSelect = "readStyleSelect"
    const val systemTypefaces = "system_typefaces"
    const val readBodyToLh = "readBodyToLh"
    const val textFullJustify = "textFullJustify"
    const val textBottomJustify = "textBottomJustify"
    const val adaptSpecialStyle = "adaptSpecialStyle"
    const val keepBlankLine = "keepBlankLine"
    const val autoReadSpeed = "autoReadSpeed"
    const val barElevation = "barElevation"
    const val transparentStatusBar = "transparentStatusBar"
    const val immNavigationBar = "immNavigationBar"
    const val defaultCover = "defaultCover"
    const val defaultCoverDark = "defaultCoverDark"
    const val coverCollectionDay = "coverCollectionDay"
    const val coverCollectionNight = "coverCollectionNight"
    const val coverCollectionModeDay = "coverCollectionModeDay"
    const val coverCollectionModeNight = "coverCollectionModeNight"
    const val replaceEnableDefault = "replaceEnableDefault"
    const val showBrightnessView = "showBrightnessView"
    const val autoClearExpired = "autoClearExpired"
    const val autoChangeSource = "autoChangeSource"
    const val importKeepName = "importKeepName"
    const val importKeepGroup = "importKeepGroup"
    const val screenOrientation = "screenOrientation"
    const val syncBookProgress = "syncBookProgress"
    const val syncBookProgressPlus = "syncBookProgressPlus"
    const val cronet = "Cronet"
    const val antiAlias = "antiAlias"
    const val bitmapCacheSize = "bitmapCacheSize"
    const val imageRetainNum = "imageRetainNum"
    const val preDownloadNum = "preDownloadNum"
    const val backwardPreDownloadNum = "backwardPreDownloadNum"
    const val mangaPreDownloadNum = "mangaPreDownloadNum"
    const val mangaAutoPageSpeed = "mangaAutoPageSpeed"
    const val mangaFooterConfig = "mangaFooterConfig"
    const val disableClickScroll = "disableClickScroll"
    const val enableMangaHorizontalScroll = "enableMangaHorizontalScroll"
    const val hideMangaTitle = "hideMangaTitle"
    const val mangaColorFilter = "mangaColorFilter"
    const val enableMangaEInk = "enableMangaEInk"
    const val mangaEInkThreshold = "mangaEInkThreshold"
    const val disableHorizontalPageSnap = "disableHorizontalPageSnap"
    const val enableMangaGray = "enableMangaGray"
    const val autoRefresh = "auto_refresh"
    const val onlyUpdateRead = "onlyUpdateRead"
    const val defaultToRead = "defaultToRead"
    const val tocPartialLoad = "tocPartialLoad"
    const val tocPartialLoadInterval = "tocPartialLoadInterval"
    const val exportCharset = "exportCharset"
    const val exportUseReplace = "exportUseReplace"
    const val useZhLayout = "useZhLayout"
    const val allowPunctAtLineStart = "allowPunctAtLineStart"
    const val brightness = "brightness"
    const val nightBrightness = "nightBrightness"
    const val expandTextMenu = "expandTextMenu"
    const val textMenuVisibleCount = "textMenuVisibleCount"
    const val textMenuCustomTitles = "textMenuCustomTitles"
    const val processTextCustomTitles = "processTextCustomTitles"
    const val hiddenTextMenuItems = "hiddenTextMenuItems" // 隐藏的文本菜单项ID列表，用逗号分隔
    const val textMenuItemOrder = "textMenuItemOrder" // 文本菜单项排序，JSON数组格式的ID列表
    const val hiddenProcessTextItems = "hiddenProcessTextItems" // 隐藏的其他应用菜单项列表，格式：包名/类名,用逗号分隔
    const val doublePageHorizontal = "doubleHorizontalPage"
    const val readUrlOpenInBrowser = "readUrlInBrowser"
    const val defaultBookTreeUri = "defaultBookTreeUri"
    const val checkSource = "checkSource"
    const val uploadRule = "uploadRule"
    const val tocUiUseReplace = "tocUiUseReplace"
    const val tocCountWords = "tocCountWords"
    const val tocCollapseVolumeName = "tocCollapseVolumeName"
    const val tocShowFullChapterName = "tocShowFullChapterName"
    const val enableReadRecord = "enableReadRecord"
    const val localBookImportSort = "localBookImportSort"
    const val customWelcome = "customWelcome"
    const val welcomeShowTime = "welcomeShowTime"
    const val welcomeImage = "welcomeImagePath"
    const val welcomeImageDark = "welcomeImagePathDark"
    const val welcomeShowText = "welcomeShowText"
    const val welcomeShowTextDark = "welcomeShowTextDark"
    const val welcomeShowIcon = "welcomeShowIcon"
    const val welcomeShowIconDark = "welcomeShowIconDark"
    const val pageTouchSlop = "pageTouchSlop"
    const val pageTouchClick = "pageTouchClick"

    /** 触摸翻页动画速度(毫秒) */
    const val touchPageAnimSpeed = "touchPageAnimSpeed"

    /** 按键翻页动画速度(毫秒) */
    const val keyPageAnimSpeed = "keyPageAnimSpeed"
    const val showAddToShelfAlert = "showAddToShelfAlert"
    const val ignoreAudioFocus = "ignoreAudioFocus"
    const val parallelExportBook = "parallelExportBook"
    const val progressBarBehavior = "progressBarBehavior"
    const val sourceEditMaxLine = "sourceEditMaxLine"
    const val ttsTimer = "ttsTimer"
    const val noAnimScrollPage = "noAnimScrollPage"
    const val webDavDeviceName = "webDavDeviceName"
    const val webServiceWakeLock = "webServiceWakeLock"
    const val webServiceToken = "webServiceToken"
    const val webServiceAuthEnabled = "webServiceAuthEnabled"
    const val unsafeSsl = "unsafeSsl"
    const val audioPlayWakeLock = "audioPlayWakeLock"
    const val audioPlayCache = "audioPlayCache"
    const val readAloudWakeLock = "readAloudWakeLock"
    const val showLastUpdateTime = "showLastUpdateTime"
    const val showWaitUpCount = "showWaitUpCount"
    const val clearWebViewData = "clearWebViewData"
    const val onlyLatestBackup = "onlyLatestBackup"
    const val autoBackup = "autoBackup"
    const val brightnessVwPos = "brightnessVwPos"
    const val shrinkDatabase = "shrinkDatabase"
    const val batchChangeSourceDelay = "batchChangeSourceDelay"
    const val openBookInfoByClickTitle = "openBookInfoByClickTitle"
    const val defaultHomePage = "defaultHomePage"
    const val showBookshelfFastScroller = "showBookshelfFastScroller"
    const val importKeepEnable = "importKeepEnable"
    const val importShowComment = "importShowComment"
    const val clickImgWay = "clickImgWay"
    const val keyPageOnLongPress = "keyPageOnLongPress"
    const val volumeKeyPage = "volumeKeyPage"
    const val volumeKeyPageOnPlay = "volumeKeyPageOnPlay"
    const val mouseWheelPage = "mouseWheelPage"
    const val recordHeapDump = "recordHeapDump"
    const val optimizeRender = "optimizeRender"
    const val updateToVariant = "updateToVariant"
    const val streamReadAloudAudio = "streamReadAloudAudio"
    const val readAloudFloatingUi = "readAloudFloatingUi"
    const val pauseReadAloudWhilePhoneCalls = "pauseReadAloudWhilePhoneCalls"
    const val readAloudByMediaButton = "readAloudByMediaButton"
    const val showMangaUi = "showMangaUi"
    const val disableMangaScale = "disableMangaScale"
    const val disableMangaPageAnim = "disableMangaPageAnim"
    const val paddingDisplayCutouts = "paddingDisplayCutouts"
    const val autoCheckNewBackup = "autoCheckNewBackup"
    const val readRecordRepairVersion = "readRecordRepairVersion"

    const val rssSourceSort = "rssSourceSort"
    const val rssSourceSortAscending = "rssSourceSortAscending"
    const val rssSourceGroupByDomain = "rssSourceGroupByDomain"

    const val dThemeName = "durThemeName"
    const val dNThemeName = "durThemeNameNight"
    const val topBarPackageDay = "topBarPackageDay"
    const val topBarPackageNight = "topBarPackageNight"

    const val cPrimary = "colorPrimary"
    const val cAccent = "colorAccent"
    const val cBackground = "colorBackground"
    const val cBBackground = "colorBottomBackground"
    const val bgImage = "backgroundImage"
    const val bgImageBlurring = "backgroundImageBlurring"
    const val tNavBar = "transparentNavBar"

    const val cNPrimary = "colorPrimaryNight"
    const val cNAccent = "colorAccentNight"
    const val cNBackground = "colorBackgroundNight"
    const val cNBBackground = "colorBottomBackgroundNight"
    const val bgImageN = "backgroundImageNight"
    const val bgImageNBlurring = "backgroundImageNightBlurring"
    const val tNavBarN = "transparentNavBarNight"

    const val showReadTitleAddition = "showReadTitleAddition"
    const val readBarStyleFollowPage = "readBarStyleFollowPage"
    const val contentSelectSpeakMod = "contentReadAloudMod"
    const val editFontScale = "editFontScale"
    const val editNonPrintable = "editNonPrintable"
    const val editAutoWrap = "editAutoWrap"
    const val editAutoComplete = "editAutoComplete"
    const val showBoardLine = "showBoardLine"
    const val recordUrl = "recordUrl"
    const val textBoldFineTipShown = "textBoldFineTipShown"
    const val textBoldMode = "textBoldMode"
    const val readRecordDisplayMode = "readRecordDisplayMode"
    const val bookInfoShowReadRecord = "bookInfoShowReadRecord"
    const val bookInfoShowAuthorOtherWorks = "bookInfoShowAuthorOtherWorks"
    const val bookInfoShowBookTag = "bookInfoShowBookTag"
    const val highlightRuleDialog = "highlightRuleDialog"
    const val highlightRuleBookTitle = "highlightRuleBookTitle"
    const val highlightRuleBracketNote = "highlightRuleBracketNote"
    const val highlightRuleItems = "highlightRuleItems"
    const val highlightRuleGroups = "highlightRuleGroups"
    const val highlightRuleCurrentGroup = "highlightRuleCurrentGroup"

    const val blockRuleItems = "exploreBlockRuleItems"

    /** 混淆版本遗留的屏蔽规则原始 JSON 备份（恢复失败时保留，避免数据彻底丢失） */
    const val blockRuleItemsLegacyBackup = "exploreBlockRuleItemsLegacyBackup"
    const val blockRuleGroups = "exploreBlockRuleGroups"
    const val blockRuleCurrentGroup = "exploreBlockRuleCurrentGroup"
    const val blockRuleShowProgress = "exploreBlockRuleShowProgress"
    const val blockRuleEnabled = "exploreBlockRuleEnabled"

    const val backupBookCache = "backupBookCache"

    const val sourceRecycleBinEnabled = "sourceRecycleBinEnabled"

    /** 发现列表网格模式列数配置，默认2列 */
    const val exploreShowColumn = "exploreShowColumn"

    /** 发现列表瀑布流模式列数配置，默认2列 */
    const val exploreShowColumnWaterfall = "exploreShowColumnWaterfall"

    /** 发现列表布局模式，由"切换布局"菜单控制 */
    const val exploreGridMode = "exploreGridMode"

    /** 发现主界面是否显示新版（源选择行 + 分类标签 + 内容列表） */
    const val exploreModernPage = "exploreModernPage"

    /** 订阅主界面是否显示新版（源选择行 + 分类标签 + 文章列表） */
    const val rssModernPage = "rssModernPage"

    /** 新版发现上次选中的书源 */
    const val exploreModernSourceUrl = "exploreModernSourceUrl"

    /** 新版订阅上次选中的订阅源 */
    const val rssModernSourceUrl = "rssModernSourceUrl"

    /** 跳转确认记忆，存储 JSON: {"sourceUrl":"allow|deny", ...} */
    const val openUrlConfirmMemory = "openUrlConfirmMemory"

    /** 首页布局模式 0: 混合列表 1: 分源Tab */
    const val homepageLayoutMode = "homepageLayoutMode"

    /** 首页隐藏书源列表JSON */
    const val homepageSourceHidden = "homepageSourceHidden"

    /** 首页预加载模式：0=仅当前集，1=当前集+相邻集 */
    const val homepagePreload = "homepagePreload"

    /** 是否显示首页tab */
    const val showHomepage = "showHomepage"

    /** 书架状态图标样式：0=新版（图标），1=经典（绿点） */
    const val bookshelfIconStyle = "bookshelfIconStyle"

    /** 发现详情页显示分类Tab开关 */
    const val exploreShowCategoryTab = "exploreShowCategoryTab"

    /** 发现详情页预加载模式：0=仅当前分类，1=当前分类+相邻分类 */
    const val exploreShowPreload = "exploreShowPreload"

    /** 底部导航栏排序，逗号分隔的页面key，第一项为默认主页 */
    const val navItemOrder = "navItemOrder"

    /** 导入应用主题时各组件的创建开关（日夜间独立） */
    const val appThemeImportDayTheme = "appThemeImportDayTheme"
    const val appThemeImportNightTheme = "appThemeImportNightTheme"
    const val appThemeImportDayTopBar = "appThemeImportDayTopBar"
    const val appThemeImportNightTopBar = "appThemeImportNightTopBar"
    const val appThemeImportDayBottomBar = "appThemeImportDayBottomBar"
    const val appThemeImportNightBottomBar = "appThemeImportNightBottomBar"
    const val appThemeImportDayCover = "appThemeImportDayCover"
    const val appThemeImportNightCover = "appThemeImportNightCover"

    /** 删除应用主题时各组件的删除开关（日夜间独立） */
    const val appThemeDeleteDayTheme = "appThemeDeleteDayTheme"
    const val appThemeDeleteNightTheme = "appThemeDeleteNightTheme"
    const val appThemeDeleteDayTopBar = "appThemeDeleteDayTopBar"
    const val appThemeDeleteNightTopBar = "appThemeDeleteNightTopBar"
    const val appThemeDeleteDayBottomBar = "appThemeDeleteDayBottomBar"
    const val appThemeDeleteNightBottomBar = "appThemeDeleteNightBottomBar"
    const val appThemeDeleteDayCover = "appThemeDeleteDayCover"
    const val appThemeDeleteNightCover = "appThemeDeleteNightCover"

    /** 书架分组配置的标签，JSON Map<groupId, List<tag>> */
    const val bookshelfGroupTags = "bookshelfGroupTags"

    /** 书架分组隐藏的标签，JSON Map<groupId, Set<tag>> */
    const val bookshelfHiddenTags = "bookshelfHiddenTags"

    /** 智能标签总开关 */
    const val smartTagsEnabled = "smartTagsEnabled"

    /** 被关闭的智能标签规则 id 集合（黑名单，未记录即开启） */
    const val smartTagsDisabledRules = "smartTagsDisabledRules"
}
