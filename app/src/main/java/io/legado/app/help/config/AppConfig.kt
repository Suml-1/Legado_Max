package io.legado.app.help.config

import android.content.SharedPreferences
import android.os.Build
import io.legado.app.BuildConfig
import io.legado.app.constant.AppConst
import io.legado.app.constant.ClipboardImportMode
import io.legado.app.constant.MangaReadMode
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.model.debug.DebugCategory
import io.legado.app.utils.GSON
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefLong
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isNightMode
import io.legado.app.utils.parseIpsFromString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefLong
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref
import io.legado.app.utils.sysConfiguration
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import java.net.InetAddress

/**
 * 应用全局配置单例
 *
 * 使用 SharedPreferences 存储和读取用户配置，支持配置变更监听。
 * 所有配置项都通过 getter/setter 访问，自动持久化到本地存储。
 *
 * 主要配置分类：
 * - 主题与界面配置（themeMode, isNightTheme, isEInkMode 等）
 * - 阅读相关配置（点击区域动作、翻页设置等）
 * - 网络配置（userAgent, customHosts, webPort 等）
 * - TTS朗读配置（ttsEngine, ttsSpeechRate 等）
 * - 导出配置（exportCharset, exportType 等）
 * - 漫画模式配置（showMangaUi, mangaPreDownloadNum 等）
 * - 书架配置（bookshelfLayout, bookshelfSort 等）
 */
@Suppress("MemberVisibilityCanBePrivate", "ConstPropertyName")
object AppConfig : SharedPreferences.OnSharedPreferenceChangeListener {
    val isCronet = appCtx.getPrefBoolean(PreferKey.cronet)
    var useAntiAlias = appCtx.getPrefBoolean(PreferKey.antiAlias)
    var userAgent: String = getPrefUserAgent()
    var customHosts = appCtx.getPrefString(PreferKey.customHosts)
    var editTheme = appCtx.getPrefInt(PreferKey.editTheme, 0)
    var editThemeDark = appCtx.getPrefInt(PreferKey.editThemeDark, 0)
    var editTemeAuto = appCtx.getPrefBoolean(PreferKey.editTemeAuto)
    var isEInkMode = appCtx.getPrefString(PreferKey.themeMode) == "3"

    // ==================== 点击区域动作配置 ====================
    // 九宫格点击区域: TL(左上) TC(中上) TR(右上)
    //               ML(左中) MC(中中) MR(右中)
    //               BL(左下) BC(中下) BR(右下)
    // 动作值: 0-菜单, 1-下一页, 2-上一页, 3-下一章, 4-上一章 等
    var clickActionTL = appCtx.getPrefInt(PreferKey.clickActionTL, 2)
    var clickActionTC = appCtx.getPrefInt(PreferKey.clickActionTC, 2)
    var clickActionTR = appCtx.getPrefInt(PreferKey.clickActionTR, 1)
    var clickActionML = appCtx.getPrefInt(PreferKey.clickActionML, 2)
    var clickActionMC = appCtx.getPrefInt(PreferKey.clickActionMC, 0)
    var clickActionMR = appCtx.getPrefInt(PreferKey.clickActionMR, 1)
    var clickActionBL = appCtx.getPrefInt(PreferKey.clickActionBL, 2)
    var clickActionBC = appCtx.getPrefInt(PreferKey.clickActionBC, 1)
    var clickActionBR = appCtx.getPrefInt(PreferKey.clickActionBR, 1)

    // ==================== 主题与界面配置 ====================
    var themeMode = appCtx.getPrefString(PreferKey.themeMode, "0")
    var useDefaultCover = appCtx.getPrefBoolean(PreferKey.useDefaultCover, false)

    // ==================== 新版发现 / 新版订阅 ====================

    /** 发现主界面显示新版（源选择行 + 分类标签 + 内容列表） */
    var exploreModernPage: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.exploreModernPage, false)
        set(value) = appCtx.putPrefBoolean(PreferKey.exploreModernPage, value)

    /** 订阅主界面显示新版（源选择行 + 分类标签 + 文章列表） */
    var rssModernPage: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.rssModernPage, false)
        set(value) = appCtx.putPrefBoolean(PreferKey.rssModernPage, value)

    /** 新版发现上次选中的书源 */
    var modernExploreSourceUrl: String?
        get() = appCtx.getPrefString(PreferKey.exploreModernSourceUrl)
        set(value) = appCtx.putPrefString(PreferKey.exploreModernSourceUrl, value)

    /** 新版订阅上次选中的订阅源 */
    var modernRssSourceUrl: String?
        get() = appCtx.getPrefString(PreferKey.rssModernSourceUrl)
        set(value) = appCtx.putPrefString(PreferKey.rssModernSourceUrl, value)

    var optimizeRender = CanvasRecorderFactory.isSupport &&
        appCtx.getPrefBoolean(PreferKey.optimizeRender, false)
    var recordLog = appCtx.getPrefBoolean(PreferKey.recordLog)
    var recordUrl = appCtx.getPrefBoolean(PreferKey.recordUrl)

    // ==================== 调试专属日志模式 ====================
    /** 调试专属模式总开关，控制按 category 路由分流功能是否启用 */
    var debugLogOnlyEnabled: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.debugLogOnlyEnabled, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.debugLogOnlyEnabled, value)
        }

    /** 被标记为"只进调试界面"的 DebugCategory 集合（持久化为逗号分隔字符串） */
    var debugLogOnlyCategories: Set<DebugCategory>
        get() {
            val raw = appCtx.getPrefString(PreferKey.debugLogOnlyCategories) ?: ""
            return DebugLogOnlyConfig.parseCategories(raw)
        }
        set(value) {
            appCtx.putPrefString(PreferKey.debugLogOnlyCategories, DebugLogOnlyConfig.serializeCategories(value))
        }
    // ==================== 剪贴板导入口令 ====================

    /**
     * 剪贴板导入口令的处理模式，取值见 [ClipboardImportMode]。
     */
    var clipboardImportMode: String
        get() = appCtx.getPrefString(PreferKey.clipboardImportMode) ?: ClipboardImportMode.ASK
        set(value) = appCtx.putPrefString(PreferKey.clipboardImportMode, value)

    /**
     * 将废弃的布尔开关 askClipboardImport 一次性迁移为三态 [clipboardImportMode]。
     *
     * 该布尔项只存在过一个提交、未正式发布，但装过对应构建的用户可能已改动过它，
     * 故做一次迁移：关闭（false）等价于"从不读取剪贴板"，其余统一落到"每次询问"。
     * 迁移后移除旧 key，使得再次调用时因 key 不存在而直接返回。
     */
    fun migrateClipboardImportMode() {
        if (!appCtx.defaultSharedPreferences.contains(PreferKey.askClipboardImport)) return
        if (!appCtx.getPrefBoolean(PreferKey.askClipboardImport, true)) {
            appCtx.putPrefString(PreferKey.clipboardImportMode, ClipboardImportMode.NEVER)
        }
        appCtx.removePref(PreferKey.askClipboardImport)
    }

    var editFontScale = appCtx.getPrefInt(PreferKey.editFontScale, 16)
    var editNonPrintable = appCtx.getPrefInt(PreferKey.editNonPrintable, 0)
    var editAutoWrap = appCtx.getPrefBoolean(PreferKey.editAutoWrap, true)
    var editAutoComplete = appCtx.getPrefBoolean(PreferKey.editAutoComplete, true)
    var showBoardLine = appCtx.getPrefInt(PreferKey.showBoardLine, 1)
    var adaptSpecialStyle = appCtx.getPrefBoolean(PreferKey.adaptSpecialStyle, true)
    // 阅读时保留正文里的空行（默认开启：不再压缩/丢弃原文的空行）
    var keepBlankLine = appCtx.getPrefBoolean(PreferKey.keepBlankLine, true)

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PreferKey.editFontScale -> editFontScale = appCtx.getPrefInt(PreferKey.editFontScale, 16)
            PreferKey.editNonPrintable -> editNonPrintable = appCtx.getPrefInt(PreferKey.editNonPrintable, 0)
            PreferKey.editAutoWrap -> editAutoWrap = appCtx.getPrefBoolean(PreferKey.editAutoWrap, true)
            PreferKey.editAutoComplete -> editAutoComplete = appCtx.getPrefBoolean(PreferKey.editAutoComplete, true)
            PreferKey.showBoardLine -> showBoardLine = appCtx.getPrefInt(PreferKey.showBoardLine, 1)
            PreferKey.adaptSpecialStyle -> adaptSpecialStyle = appCtx.getPrefBoolean(PreferKey.adaptSpecialStyle, true)
            PreferKey.keepBlankLine -> keepBlankLine = appCtx.getPrefBoolean(PreferKey.keepBlankLine, true)

            PreferKey.themeMode -> {
                themeMode = appCtx.getPrefString(PreferKey.themeMode, "0")
                isEInkMode = themeMode == "3"
            }

            PreferKey.clickActionTL ->
                clickActionTL =
                    appCtx.getPrefInt(PreferKey.clickActionTL, 2)

            PreferKey.clickActionTC ->
                clickActionTC =
                    appCtx.getPrefInt(PreferKey.clickActionTC, 2)

            PreferKey.clickActionTR ->
                clickActionTR =
                    appCtx.getPrefInt(PreferKey.clickActionTR, 1)

            PreferKey.clickActionML ->
                clickActionML =
                    appCtx.getPrefInt(PreferKey.clickActionML, 2)

            PreferKey.clickActionMC ->
                clickActionMC =
                    appCtx.getPrefInt(PreferKey.clickActionMC, 0)

            PreferKey.clickActionMR ->
                clickActionMR =
                    appCtx.getPrefInt(PreferKey.clickActionMR, 1)

            PreferKey.clickActionBL ->
                clickActionBL =
                    appCtx.getPrefInt(PreferKey.clickActionBL, 2)

            PreferKey.clickActionBC ->
                clickActionBC =
                    appCtx.getPrefInt(PreferKey.clickActionBC, 1)

            PreferKey.clickActionBR ->
                clickActionBR =
                    appCtx.getPrefInt(PreferKey.clickActionBR, 1)

            PreferKey.readBodyToLh ->
                ReadBookConfig.readBodyToLh =
                    appCtx.getPrefBoolean(PreferKey.readBodyToLh, true)

            PreferKey.useZhLayout ->
                ReadBookConfig.useZhLayout =
                    appCtx.getPrefBoolean(PreferKey.useZhLayout)

            PreferKey.allowPunctAtLineStart ->
                ReadBookConfig.allowPunctAtLineStart =
                    appCtx.getPrefBoolean(PreferKey.allowPunctAtLineStart)

            PreferKey.userAgent -> userAgent = getPrefUserAgent()

            PreferKey.customHosts -> {
                customHosts = appCtx.getPrefString(PreferKey.customHosts)
                _hostMap = null
                _addressCache = null
            }

            PreferKey.editTheme -> editTheme = appCtx.getPrefInt(PreferKey.editTheme, 0)

            PreferKey.editThemeDark -> editThemeDark = appCtx.getPrefInt(PreferKey.editThemeDark, 0)

            PreferKey.editTemeAuto -> editTemeAuto = appCtx.getPrefBoolean(PreferKey.editTemeAuto)

            PreferKey.antiAlias -> useAntiAlias = appCtx.getPrefBoolean(PreferKey.antiAlias)

            PreferKey.useDefaultCover ->
                useDefaultCover =
                    appCtx.getPrefBoolean(PreferKey.useDefaultCover, false)

            PreferKey.optimizeRender ->
                optimizeRender = CanvasRecorderFactory.isSupport &&
                    appCtx.getPrefBoolean(PreferKey.optimizeRender, false)

            PreferKey.recordLog -> recordLog = appCtx.getPrefBoolean(PreferKey.recordLog)

            PreferKey.recordUrl -> recordUrl = appCtx.getPrefBoolean(PreferKey.recordUrl)
        }
    }

    // dns配置
    private var _hostMap: Map<String, Any?>? = null
    val hostMap: Map<String, Any?>
        get() = _hostMap ?: run {
            val cache = GSON.fromJsonObject<Map<String, Any?>>(customHosts).getOrNull() ?: emptyMap()
            _hostMap = cache
            cache
        }
    private var _addressCache: Map<String, List<InetAddress>>? = null
    val addressCache: Map<String, List<InetAddress>>
        get() = _addressCache ?: run {
            val cache = hostMap.mapNotNull { (host, ipValue) ->
                val addresses = when (ipValue) {
                    is String -> ipValue.parseIpsFromString()
                    is List<*> -> ipValue.parseIpsFromList()
                    else -> null
                }
                addresses?.let { host to it }
            }.toMap()
            _addressCache = cache
            cache
        }
    private fun List<*>.parseIpsFromList(): List<InetAddress> = mapNotNull { element ->
        (element as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?.runCatching { InetAddress.getByName(this) }
            ?.getOrNull()
    }

    var isNightTheme: Boolean
        get() = when (themeMode) {
            "1" -> false
            "2" -> true
            "3" -> false
            else -> sysConfiguration.isNightMode
        }
        set(value) {
            if (isNightTheme != value) {
                // 同步刷新本地缓存，保证同栈内 initNightMode() 读到正确目标模式，
                // 避免缓存滞后触发错误/多余的 AppCompat 模式切换重建
                themeMode = if (value) "2" else "1"
                isEInkMode = false
                appCtx.putPrefString(PreferKey.themeMode, themeMode)
            }
        }
    var showBookname: Int
        get() = appCtx.getPrefInt(PreferKey.showBooknameLayout, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.showBooknameLayout, value)
        }
    var bookshelfMargin: Int
        get() = appCtx.getPrefInt(PreferKey.bookshelfMargin, 12)
        set(value) {
            appCtx.putPrefInt(PreferKey.bookshelfMargin, value)
        }

    var showUnread: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showUnread, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showUnread, value)
        }

    var showLastUpdateTime: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showLastUpdateTime, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showLastUpdateTime, value)
        }

    var showWaitUpCount: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showWaitUpCount, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showWaitUpCount, value)
        }

    var readBrightness: Int
        get() = if (isNightTheme) {
            appCtx.getPrefInt(PreferKey.nightBrightness, 100)
        } else {
            appCtx.getPrefInt(PreferKey.brightness, 100)
        }
        set(value) {
            if (isNightTheme) {
                appCtx.putPrefInt(PreferKey.nightBrightness, value)
            } else {
                appCtx.putPrefInt(PreferKey.brightness, value)
            }
        }

    val textSelectAble: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.textSelectAble, true)

    var mangaReadMode: String
        get() = appCtx.getPrefString(PreferKey.mangaReadMode)?.takeIf { it.isNotBlank() }
            ?: if (appCtx.getPrefBoolean(PreferKey.enableMangaHorizontalScroll, false)) {
                MangaReadMode.NORMAL
            } else {
                MangaReadMode.SCROLL
            }
        set(value) {
            appCtx.putPrefString(PreferKey.mangaReadMode, value)
        }

    val isTransparentStatusBar: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.transparentStatusBar, true)

    val immNavigationBar: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.immNavigationBar, true)

    val screenOrientation: String?
        get() = appCtx.getPrefString(PreferKey.screenOrientation)

    var bookGroupStyle: Int
        get() = appCtx.getPrefInt(PreferKey.bookGroupStyle, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.bookGroupStyle, value)
        }

    var bookshelfLayout: Int
        get() = appCtx.getPrefInt(PreferKey.bookshelfLayout, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.bookshelfLayout, value)
        }

    var folderLayout: Int
        get() = appCtx.getPrefInt(PreferKey.folderLayout, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.folderLayout, value)
        }

    var bookLayout: Int
        get() = appCtx.getPrefInt(PreferKey.bookLayout, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.bookLayout, value)
        }

    var showMoreInfoInList: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showMoreInfoInList, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showMoreInfoInList, value)
        }

    var showIntroInList: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showIntroInList, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showIntroInList, value)
        }

    var showCategoryInfoInList: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showCategoryInfoInList, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showCategoryInfoInList, value)
        }

    var showBookshelfReadProgress: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showBookshelfReadProgress, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showBookshelfReadProgress, value)
        }

    // 书籍外边框开关（默认关闭，仅在列表/紧凑列表视图时生效）
    var showBookBorder: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showBookBorder, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showBookBorder, value)
        }

    // 简介显示行数（默认2行，范围1-10）
    var introLinesInList: Int
        get() = appCtx.getPrefInt(PreferKey.introLinesInList, 2)
        set(value) {
            appCtx.putPrefInt(PreferKey.introLinesInList, value.coerceIn(1, 10))
        }

// 下拉选择分组开关（默认关闭，仅在分组样式为标签时生效）
    var dropdownSelectGroup: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.dropdownSelectGroup, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.dropdownSelectGroup, value)
        }

// 显示标签栏开关（默认关闭，开启后在书架分组下方显示二级标签栏）
    var showBookshelfTagBar: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showBookshelfTagBar, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showBookshelfTagBar, value)
        }

    /**
     * 书架每个分组配置的标签列表。
     * key = groupId, value = 标签名列表
     */
    var bookshelfGroupTags: Map<Long, List<String>>
        get() {
            val rawMap = GSON.fromJsonObject<Map<String, List<String>>>(
                appCtx.getPrefString(PreferKey.bookshelfGroupTags),
            ).getOrDefault(emptyMap())
            return rawMap.mapNotNull { (key, value) ->
                key.toLongOrNull()?.let { k -> k to value }
            }.toMap()
        }
        set(value) {
            val normalized = value
                .mapKeys { it.key.toString() }
                .mapValues { (_, tags) -> tags.map { it.trim() }.filter { it.isNotBlank() }.distinct() }
                .filterValues { it.isNotEmpty() }
            if (normalized.isEmpty()) {
                appCtx.removePref(PreferKey.bookshelfGroupTags)
            } else {
                appCtx.putPrefString(PreferKey.bookshelfGroupTags, GSON.toJson(normalized))
            }
        }

    /**
     * 书架每个分组隐藏的标签集合。
     * key = groupId, value = 隐藏的标签名集合
     */
    var bookshelfHiddenTags: Map<Long, Set<String>>
        get() {
            val rawMap = GSON.fromJsonObject<Map<String, List<String>>>(
                appCtx.getPrefString(PreferKey.bookshelfHiddenTags),
            ).getOrDefault(emptyMap())
            return rawMap.mapNotNull { (key, value) ->
                key.toLongOrNull()?.let { k -> k to value.toSet() }
            }.toMap()
        }
        set(value) {
            val normalized = value
                .mapKeys { it.key.toString() }
                .mapValues { (_, tags) -> tags.map { it.trim() }.filter { it.isNotBlank() }.distinct() }
                .filterValues { it.isNotEmpty() }
            if (normalized.isEmpty()) {
                appCtx.removePref(PreferKey.bookshelfHiddenTags)
            } else {
                appCtx.putPrefString(PreferKey.bookshelfHiddenTags, GSON.toJson(normalized))
            }
        }

    var saveTabPosition: Int
        get() = appCtx.getPrefInt(PreferKey.saveTabPosition, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.saveTabPosition, value)
        }

    var bookExportFileName: String?
        get() = appCtx.getPrefString(PreferKey.bookExportFileName)
        set(value) {
            appCtx.putPrefString(PreferKey.bookExportFileName, value)
        }

    // 保存 自定义导出章节模式 文件名js表达式
    var episodeExportFileName: String?
        get() = appCtx.getPrefString(PreferKey.episodeExportFileName, "")
        set(value) {
            appCtx.putPrefString(PreferKey.episodeExportFileName, value)
        }

    var bookImportFileName: String?
        get() = appCtx.getPrefString(PreferKey.bookImportFileName)
        set(value) {
            appCtx.putPrefString(PreferKey.bookImportFileName, value)
        }

    var backupPath: String?
        get() = appCtx.getPrefString(PreferKey.backupPath)
        set(value) {
            if (value.isNullOrEmpty()) {
                appCtx.removePref(PreferKey.backupPath)
            } else {
                appCtx.putPrefString(PreferKey.backupPath, value)
            }
        }

    // 恢复时显示选择菜单，默认关闭
    val restoreShowSelector: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.restoreShowSelector, false)

    // 书籍保存位置
    var defaultBookTreeUri: String?
        get() = appCtx.getPrefString(PreferKey.defaultBookTreeUri)
        set(value) {
            if (value.isNullOrEmpty()) {
                appCtx.removePref(PreferKey.defaultBookTreeUri)
            } else {
                appCtx.putPrefString(PreferKey.defaultBookTreeUri, value)
            }
        }

    val showDiscovery: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showDiscovery, true)

    val showRSS: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showRss, true)

    val showHomepage: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showHomepage, true)

    // 调试模式，开启后"我的"页面显示调试工具入口
    val debugMode: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.debugMode, false)

    // 调试日志悬浮球，开启后在应用内任何页面显示调试日志悬浮球
    val debugLogFloatingBall: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.debugLogFloatingBall, false)

    // 崩溃时复制日志到剪贴板，默认开启
    val copyCrashLog: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.copyCrashLog, true)

    // MD链接用内置浏览器打开，默认关闭
    val mdLinkInnerBrowser: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.mdLinkInnerBrowser, false)

    val autoRefreshBook: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.autoRefresh)

    val onlyUpdateRead: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.onlyUpdateRead)

    val isTocPartialLoad: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.tocPartialLoad)

    var tocPartialLoadInterval: Int
        get() = appCtx.getPrefInt(PreferKey.tocPartialLoadInterval, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.tocPartialLoadInterval, value)
        }

    var enableReview: Boolean
        get() = BuildConfig.DEBUG && appCtx.getPrefBoolean(PreferKey.enableReview, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableReview, value)
        }

    var threadCount: Int
        get() = appCtx.getPrefInt(PreferKey.threadCount, 16)
        set(value) {
            appCtx.putPrefInt(PreferKey.threadCount, value)
        }

    /**
     * 缓存并发率，格式与书源 concurrentRate 一致：
     * null/空 = 不限制，"1500" = 间隔1500ms，"20/60000" = 60s内最多20次
     */
    var cacheConcurrentRate: String?
        get() = appCtx.getPrefString(PreferKey.cacheConcurrentRate)
        set(value) {
            if (value.isNullOrBlank()) {
                appCtx.removePref(PreferKey.cacheConcurrentRate)
            } else {
                appCtx.putPrefString(PreferKey.cacheConcurrentRate, value)
            }
        }

    var verboseUpdateLog: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.verboseUpdateLog, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.verboseUpdateLog, value)
        }

    var remoteServerId: Long
        get() = appCtx.getPrefLong(PreferKey.remoteServerId)
        set(value) {
            appCtx.putPrefLong(PreferKey.remoteServerId, value)
        }

    // 添加本地选择的目录
    var importBookPath: String?
        get() = appCtx.getPrefString("importBookPath")
        set(value) {
            if (value == null) {
                appCtx.removePref("importBookPath")
            } else {
                appCtx.putPrefString("importBookPath", value)
            }
        }

    var ttsFlowSys: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.ttsFollowSys, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.ttsFollowSys, value)
        }

    val noAnimScrollPage: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.noAnimScrollPage, false)

    const val defaultSpeechRate = 5

    var ttsSpeechRate: Int
        get() = appCtx.getPrefInt(PreferKey.ttsSpeechRate, defaultSpeechRate)
        set(value) {
            appCtx.putPrefInt(PreferKey.ttsSpeechRate, value)
        }

    var ttsTimer: Int
        get() = appCtx.getPrefInt(PreferKey.ttsTimer, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.ttsTimer, value)
        }

    val speechRatePlay: Int get() = if (ttsFlowSys) defaultSpeechRate else ttsSpeechRate

    var chineseConverterType: Int
        get() = appCtx.getPrefInt(PreferKey.chineseConverterType)
        set(value) {
            appCtx.putPrefInt(PreferKey.chineseConverterType, value)
        }

    var systemTypefaces: Int
        get() = appCtx.getPrefInt(PreferKey.systemTypefaces)
        set(value) {
            appCtx.putPrefInt(PreferKey.systemTypefaces, value)
        }

    var elevation: Int
        get() = if (isEInkMode) {
            0
        } else {
            appCtx.getPrefInt(
                PreferKey.barElevation,
                AppConst.sysElevation,
            )
        }
        set(value) {
            appCtx.putPrefInt(PreferKey.barElevation, value)
        }

    var readUrlInBrowser: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.readUrlOpenInBrowser)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.readUrlOpenInBrowser, value)
        }

    var exportCharset: String
        get() {
            val c = appCtx.getPrefString(PreferKey.exportCharset)
            if (c.isNullOrBlank()) {
                return "UTF-8"
            }
            return c
        }
        set(value) {
            appCtx.putPrefString(PreferKey.exportCharset, value)
        }

    var exportUseReplace: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.exportUseReplace, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.exportUseReplace, value)
        }

    var exportToWebDav: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.exportToWebDav)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.exportToWebDav, value)
        }
    var exportNoChapterName: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.exportNoChapterName)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.exportNoChapterName, value)
        }

    // 是否启用自定义导出 default->false
    var enableCustomExport: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.enableCustomExport, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableCustomExport, value)
        }

    var exportType: Int
        get() = appCtx.getPrefInt(PreferKey.exportType)
        set(value) {
            appCtx.putPrefInt(PreferKey.exportType, value)
        }
    var exportPictureFile: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.exportPictureFile, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.exportPictureFile, value)
        }

    var parallelExportBook: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.parallelExportBook, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.parallelExportBook, value)
        }

    var changeSourceCheckAuthor: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.changeSourceCheckAuthor)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.changeSourceCheckAuthor, value)
        }

    var ttsEngine: String?
        get() = appCtx.getPrefString(PreferKey.ttsEngine)
        set(value) {
            appCtx.putPrefString(PreferKey.ttsEngine, value)
        }

    var webPort: Int
        get() = appCtx.getPrefInt(PreferKey.webPort, 1122)
        set(value) {
            appCtx.putPrefInt(PreferKey.webPort, value)
        }

    var webServiceAuthEnabled: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.webServiceAuthEnabled, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.webServiceAuthEnabled, value)
        }

    var webServiceToken: String
        get() = appCtx.getPrefString(PreferKey.webServiceToken, "")
            .orEmpty()
            .ifBlank { AppConst.androidId }
        set(value) {
            appCtx.putPrefString(PreferKey.webServiceToken, value)
        }

    var unsafeSsl: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.unsafeSsl, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.unsafeSsl, value)
        }

    var tocUiUseReplace: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.tocUiUseReplace)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.tocUiUseReplace, value)
        }

    var tocCountWords: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.tocCountWords, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.tocCountWords, value)
        }

    var tocCollapseVolumeName: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.tocCollapseVolumeName)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.tocCollapseVolumeName, value)
        }

    var tocShowFullChapterName: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.tocShowFullChapterName)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.tocShowFullChapterName, value)
        }

    var enableReadRecord: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.enableReadRecord, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableReadRecord, value)
        }

    var bookInfoShowReadRecord: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.bookInfoShowReadRecord, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.bookInfoShowReadRecord, value)
        }

    var bookInfoShowAuthorOtherWorks: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.bookInfoShowAuthorOtherWorks, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.bookInfoShowAuthorOtherWorks, value)
        }

    var bookInfoShowBookTag: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.bookInfoShowBookTag, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.bookInfoShowBookTag, value)
        }

    val autoChangeSource: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.autoChangeSource, true)

    var changeSourceLoadInfo: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.changeSourceLoadInfo)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.changeSourceLoadInfo, value)
        }

    var changeSourceLoadToc: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.changeSourceLoadToc)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.changeSourceLoadToc, value)
        }

    var changeSourceLoadWordCount: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.changeSourceLoadWordCount)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.changeSourceLoadWordCount, value)
        }

    var changeSourceShowScore: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.changeSourceShowScore, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.changeSourceShowScore, value)
        }

    var openBookInfoByClickTitle: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.openBookInfoByClickTitle, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.openBookInfoByClickTitle, value)
        }

    var showBookshelfFastScroller: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showBookshelfFastScroller, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showBookshelfFastScroller, value)
        }

    var contentSelectSpeakMod: Int
        get() = appCtx.getPrefInt(PreferKey.contentSelectSpeakMod)
        set(value) {
            appCtx.putPrefInt(PreferKey.contentSelectSpeakMod, value)
        }

    var batchChangeSourceDelay: Int
        get() = appCtx.getPrefInt(PreferKey.batchChangeSourceDelay)
        set(value) {
            appCtx.putPrefInt(PreferKey.batchChangeSourceDelay, value)
        }

    val importKeepName get() = appCtx.getPrefBoolean(PreferKey.importKeepName)
    val importKeepGroup get() = appCtx.getPrefBoolean(PreferKey.importKeepGroup)
    var importKeepEnable: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.importKeepEnable, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.importKeepEnable, value)
        }
    var importShowComment: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.importShowComment, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.importShowComment, value)
        }

    // 点击图片方式
    val clickImgWay: String?
        get() = appCtx.getPrefString(PreferKey.clickImgWay)

    // 预下载章节数量
    var preDownloadNum
        get() = appCtx.getPrefInt(PreferKey.preDownloadNum, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.preDownloadNum, value)
        }

    // 向前预下载章节数量
    var backwardPreDownloadNum
        get() = appCtx.getPrefInt(PreferKey.backwardPreDownloadNum, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.backwardPreDownloadNum, value)
        }

    val syncBookProgress get() = appCtx.getPrefBoolean(PreferKey.syncBookProgress, true)

    val syncBookProgressPlus get() = appCtx.getPrefBoolean(PreferKey.syncBookProgressPlus, false)

    val mediaButtonOnExit get() = appCtx.getPrefBoolean("mediaButtonOnExit", true)

    val readAloudByMediaButton
        get() = appCtx.getPrefBoolean(PreferKey.readAloudByMediaButton, false)

    val replaceEnableDefault get() = appCtx.getPrefBoolean(PreferKey.replaceEnableDefault, true)

    val webDavDir get() = appCtx.getPrefString(PreferKey.webDavDir, "legado")

    val webDavDeviceName get() = appCtx.getPrefString(PreferKey.webDavDeviceName, Build.MODEL)

    val recordHeapDump get() = appCtx.getPrefBoolean(PreferKey.recordHeapDump, false)

    val loadCoverOnlyWifi get() = appCtx.getPrefBoolean(PreferKey.loadCoverOnlyWifi, false)

    val loadCoverHighQuality get() = appCtx.getPrefBoolean(PreferKey.loadCoverHighQuality, false)

    val bookCoverShadow get() = appCtx.getPrefBoolean(PreferKey.bookCoverShadow, true)

    val showAddToShelfAlert get() = appCtx.getPrefBoolean(PreferKey.showAddToShelfAlert, true)

    val ignoreAudioFocus get() = appCtx.getPrefBoolean(PreferKey.ignoreAudioFocus, false)

    var pauseReadAloudWhilePhoneCalls
        get() = appCtx.getPrefBoolean(PreferKey.pauseReadAloudWhilePhoneCalls, false)
        set(value) = appCtx.putPrefBoolean(PreferKey.pauseReadAloudWhilePhoneCalls, value)

    val onlyLatestBackup get() = appCtx.getPrefBoolean(PreferKey.onlyLatestBackup, false)

    var autoBackup: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.autoBackup, true)
        set(value) = appCtx.putPrefBoolean(PreferKey.autoBackup, value)

    val autoCheckNewBackup get() = appCtx.getPrefBoolean(PreferKey.autoCheckNewBackup, true)

    val defaultHomePage get() = appCtx.getPrefString(PreferKey.defaultHomePage, "bookshelf") ?: "bookshelf"

    /**
     * 底部导航栏排序，逗号分隔的页面key列表。
     * 排序顺序与默认主页选择相互独立。
     * 若未设置，使用默认顺序。
     */
    val navItemOrder: List<String>
        get() {
            val stored = appCtx.getPrefString(PreferKey.navItemOrder)
            if (!stored.isNullOrBlank()) {
                val parsed = stored.split(",").filter { it.isNotBlank() }
                if (parsed.isNotEmpty()) return parsed
            }
            return listOf("bookshelf", "homepage", "explore", "rss", "my")
        }

    fun setNavItemOrder(order: List<String>) {
        appCtx.putPrefString(PreferKey.navItemOrder, order.joinToString(","))
    }

    fun setDefaultHomePage(key: String) {
        appCtx.putPrefString(PreferKey.defaultHomePage, key)
    }

    val updateToVariant get() = appCtx.getPrefString(PreferKey.updateToVariant, "default_version")

    val streamReadAloudAudio get() = appCtx.getPrefBoolean(PreferKey.streamReadAloudAudio, false)

    val readAloudFloatingUi get() = appCtx.getPrefBoolean(PreferKey.readAloudFloatingUi, false)

    val doublePageHorizontal: String?
        get() = appCtx.getPrefString(PreferKey.doublePageHorizontal)

    val progressBarBehavior: String?
        get() = appCtx.getPrefString(PreferKey.progressBarBehavior, "page")

    // ==================== 翻页与按键配置 ====================
    val keyPageOnLongPress
        get() = appCtx.getPrefBoolean(PreferKey.keyPageOnLongPress, false)

    val volumeKeyPage
        get() = appCtx.getPrefBoolean(PreferKey.volumeKeyPage, true)

    val volumeKeyPageOnPlay
        get() = appCtx.getPrefBoolean(PreferKey.volumeKeyPageOnPlay, true)

    val mouseWheelPage
        get() = appCtx.getPrefBoolean(PreferKey.mouseWheelPage, true)

    val paddingDisplayCutouts
        get() = appCtx.getPrefBoolean(PreferKey.paddingDisplayCutouts, false)

    var searchScope: String
        get() = appCtx.getPrefString("searchScope") ?: ""
        set(value) {
            appCtx.putPrefString("searchScope", value)
        }

    var searchGroup: String
        get() = appCtx.getPrefString("searchGroup") ?: ""
        set(value) {
            appCtx.putPrefString("searchGroup", value)
        }

    // ==================== 触摸灵敏度配置 ====================
    /** 页面触摸滑动灵敏度，0表示使用系统默认值 */
    var pageTouchSlop: Int
        get() = appCtx.getPrefInt(PreferKey.pageTouchSlop, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.pageTouchSlop, value)
        }

    /** 页面边缘点击阈值，用于扩大或缩小边缘点击区域 */
    var pageTouchClick: Int
        get() = appCtx.getPrefInt(PreferKey.pageTouchClick, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.pageTouchClick, value)
        }

    /** 触摸翻页动画速度(毫秒)，数值越小动画越快 */
    var touchPageAnimSpeed: Int
        get() = appCtx.getPrefInt(PreferKey.touchPageAnimSpeed, 300)
        set(value) {
            appCtx.putPrefInt(PreferKey.touchPageAnimSpeed, value)
        }

    /** 按键翻页动画速度(毫秒)，数值越小动画越快 */
    var keyPageAnimSpeed: Int
        get() = appCtx.getPrefInt(PreferKey.keyPageAnimSpeed, 100)
        set(value) {
            appCtx.putPrefInt(PreferKey.keyPageAnimSpeed, value)
        }

    var bookshelfSort: Int
        get() = appCtx.getPrefInt(PreferKey.bookshelfSort, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.bookshelfSort, value)
        }

    fun getBookSortByGroupId(groupId: Long): Int = appDb.bookGroupDao.getByID(groupId)?.getRealBookSort()
        ?: bookshelfSort

    private fun getPrefUserAgent(): String {
        val ua = appCtx.getPrefString(PreferKey.userAgent)
        if (ua.isNullOrBlank()) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + BuildConfig.Cronet_Main_Version + " Safari/537.36"
        }
        return ua
    }

    var bitmapCacheSize: Int
        get() = appCtx.getPrefInt(PreferKey.bitmapCacheSize, 50)
        set(value) {
            appCtx.putPrefInt(PreferKey.bitmapCacheSize, value)
        }

    var imageRetainNum: Int
        get() = appCtx.getPrefInt(PreferKey.imageRetainNum, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.imageRetainNum, value)
        }

    var showReadTitleBarAddition: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showReadTitleAddition, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showReadTitleAddition, value)
        }
    var readBarStyleFollowPage: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.readBarStyleFollowPage, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.readBarStyleFollowPage, value)
        }

    var sourceRecycleBinEnabled: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.sourceRecycleBinEnabled, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.sourceRecycleBinEnabled, value)
        }

    var sourceEditMaxLine: Int
        get() {
            val maxLine = appCtx.getPrefInt(PreferKey.sourceEditMaxLine, Int.MAX_VALUE)
            if (maxLine < 10) {
                return Int.MAX_VALUE
            }
            return maxLine
        }
        set(value) {
            appCtx.putPrefInt(PreferKey.sourceEditMaxLine, value)
        }

    var audioPlayUseWakeLock: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.audioPlayWakeLock)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.audioPlayWakeLock, value)
        }

    /**  音频边播放边缓存：开启后播放的音频会写进书籍音频缓存目录（听完即可离线），默认关闭  **/
    var audioPlayCacheEnabled: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.audioPlayCache)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.audioPlayCache, value)
        }

    var brightnessVwPos: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.brightnessVwPos)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.brightnessVwPos, value)
        }

    fun detectClickArea() {
        if (clickActionTL * clickActionTC * clickActionTR
            * clickActionML * clickActionMC * clickActionMR
            * clickActionBL * clickActionBC * clickActionBR != 0
        ) {
            appCtx.putPrefInt(PreferKey.clickActionMC, 0)
            appCtx.toastOnUi("当前没有配置菜单区域,自动恢复中间区域为菜单.")
        }
    }

    // 跳转到漫画界面不使用富文本模式
    val showMangaUi: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showMangaUi, true)

    // 禁用漫画缩放
    var disableMangaScale: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.disableMangaScale, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.disableMangaScale, value)
        }

    var disableMangaPageAnim: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.disableMangaPageAnim, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.disableMangaPageAnim, value)
        }

    // 漫画预加载数量
    var mangaPreDownloadNum
        get() = appCtx.getPrefInt(PreferKey.mangaPreDownloadNum, 10)
        set(value) {
            appCtx.putPrefInt(PreferKey.mangaPreDownloadNum, value)
        }

    // 点击翻页
    var disableClickScroll
        get() = appCtx.getPrefBoolean(PreferKey.disableClickScroll, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.disableClickScroll, value)
        }

    // 漫画滚动速度
    var mangaAutoPageSpeed
        get() = appCtx.getPrefInt(PreferKey.mangaAutoPageSpeed, 3)
        set(value) {
            appCtx.putPrefInt(PreferKey.mangaAutoPageSpeed, value)
        }

    // 漫画页脚配置
    var mangaFooterConfig
        get() = appCtx.getPrefString(PreferKey.mangaFooterConfig, "")
        set(value) {
            appCtx.putPrefString(PreferKey.mangaFooterConfig, value)
        }

    // 漫画水平滚动
    var enableMangaHorizontalScroll
        get() = mangaReadMode != MangaReadMode.SCROLL
        set(value) {
            mangaReadMode = if (value) {
                MangaReadMode.NORMAL
            } else {
                MangaReadMode.SCROLL
            }
        }

    var mangaColorFilter
        get() = appCtx.getPrefString(PreferKey.mangaColorFilter, "")
        set(value) {
            appCtx.putPrefString(PreferKey.mangaColorFilter, value)
        }

    // 禁用漫画内标题
    var hideMangaTitle
        get() = appCtx.getPrefBoolean(PreferKey.hideMangaTitle, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.hideMangaTitle, value)
        }

    // 开启墨水屏模式
    var enableMangaEInk
        get() = appCtx.getPrefBoolean(PreferKey.enableMangaEInk, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableMangaEInk, value)
        }

    var mangaEInkThreshold
        get() = appCtx.getPrefInt(PreferKey.mangaEInkThreshold, 150)
        set(value) {
            appCtx.putPrefInt(PreferKey.mangaEInkThreshold, value)
        }

    var disableHorizontalPageSnap
        get() = appCtx.getPrefBoolean(PreferKey.disableHorizontalPageSnap, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.disableHorizontalPageSnap, value)
        }

    var enableMangaGray
        get() = appCtx.getPrefBoolean(PreferKey.enableMangaGray, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableMangaGray, value)
        }

    var welcomeImage
        get() = appCtx.getPrefString(PreferKey.welcomeImage)
        set(value) {
            appCtx.putPrefString(PreferKey.welcomeImage, value)
        }

    var welcomeShowText
        get() = appCtx.getPrefBoolean(PreferKey.welcomeShowText, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.welcomeShowText, value)
        }

    var welcomeShowIcon
        get() = appCtx.getPrefBoolean(PreferKey.welcomeShowIcon, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.welcomeShowIcon, value)
        }

    var welcomeImageDark
        get() = appCtx.getPrefString(PreferKey.welcomeImageDark)
        set(value) {
            appCtx.putPrefString(PreferKey.welcomeImageDark, value)
        }

    var welcomeShowTextDark
        get() = appCtx.getPrefBoolean(PreferKey.welcomeShowTextDark, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.welcomeShowTextDark, value)
        }

    var welcomeShowIconDark
        get() = appCtx.getPrefBoolean(PreferKey.welcomeShowIconDark, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.welcomeShowIconDark, value)
        }

    val autoUpdateVariant get() = appCtx.getPrefBoolean("autoUpdateVariant", true)

    // ==================== 字重精细调整配置 ====================

    /**
     * 是否已显示过精细调整首次提示
     * 用于控制只在第一次切换到精细模式时显示提示对话框
     */
    var textBoldFineTipShown: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.textBoldFineTipShown, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.textBoldFineTipShown, value)
        }

    /**
     * 字重调整模式
     * - 0: 粗略模式，三个固定选项（正常/粗体/细体）
     * - 1: 精细模式，SeekBar 进度条（100~900）
     */
    var textBoldMode: Int
        get() = appCtx.getPrefInt(PreferKey.textBoldMode, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.textBoldMode, value)
        }

    /**
     * 书架状态图标样式
     * - 0: 新版样式（封面右上角显示图标）
     * - 1: 经典样式（书名前显示小绿点）
     */
    var bookshelfIconStyle: Int
        get() = appCtx.getPrefString(PreferKey.bookshelfIconStyle)?.toIntOrNull() ?: 0
        set(value) {
            appCtx.putPrefString(PreferKey.bookshelfIconStyle, value.toString())
        }

    var forceSoftwareParagraphBubble: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.forceSoftwareParagraphBubble, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.forceSoftwareParagraphBubble, value)
        }
}
