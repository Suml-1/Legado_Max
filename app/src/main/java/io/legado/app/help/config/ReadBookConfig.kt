package io.legado.app.help.config

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.Keep
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PageAnim
import io.legado.app.constant.PreferKey
import io.legado.app.help.DefaultData
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.theme.EInkRender
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.argbHexString
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFolderReplace
import io.legado.app.utils.externalCache
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getFile
import io.legado.app.utils.getMeanColor
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.resizeAndRecycle
import splitties.init.appCtx
import java.io.File
import androidx.core.graphics.drawable.toDrawable

/**
 * 阅读界面配置
 */
@Suppress("ConstPropertyName")
@Keep
object ReadBookConfig {
    const val configFileName = "readConfig.json"
    const val shareConfigFileName = "shareReadConfig.json"
    val configFilePath = FileUtils.getPath(appCtx.filesDir, configFileName)
    val shareConfigFilePath = FileUtils.getPath(appCtx.filesDir, shareConfigFileName)
    val configList: ArrayList<Config> = arrayListOf()
    lateinit var shareConfig: Config
    var durConfig
        get() = getConfig(styleSelect)
        set(value) {
            configList[styleSelect] = value
            if (shareLayout) {
                shareConfig = value
            }
        }

    var isComic: Boolean = false
    var bg: Drawable? = null
    var bgMeanColor: Int = 0
    val textColor: Int get() = durConfig.curTextColor()
    val textAccentColor: Int get() = durConfig.curTextAccentColor()

    /**
     * 当前搜索命中块的底色：低透明度强调色叠在页面背景上。
     * TextLine 画块与变细擦除共用同一来源，避免两处各写一份颜色公式。
     */
    val currentSearchHitBgColor: Int
        get() = (0x33 shl 24) or (textAccentColor and 0x00FFFFFF)
    var isNineBgImg = false

    init {
        initConfigs()
        initShareConfig()
    }

    @Synchronized
    fun getConfig(index: Int): Config {
        if (configList.size < 5) {
            resetAll()
        }
        return configList.getOrNull(index) ?: configList[0]
    }

    fun initConfigs() {
        val configFile = File(configFilePath)
        var configs: List<Config>? = null
        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                configs = GSON.fromJsonArray<Config>(json).getOrThrow()
            } catch (e: Exception) {
                AppLog.put("读取排版配置文件出错", e)
            }
        }
        (configs ?: DefaultData.readConfigs).let {
            configList.clear()
            configList.addAll(it)
        }
    }

    fun initShareConfig() {
        val configFile = File(shareConfigFilePath)
        var c: Config? = null
        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                c = GSON.fromJsonObject<Config>(json).getOrThrow()
            } catch (e: Exception) {
                e.printOnDebug()
            }
        }
        shareConfig = c ?: configList.getOrNull(5) ?: Config()
    }

    fun upBg(width: Int, height: Int) {
        val drawable = durConfig.curBgDrawable(width, height)
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            bgMeanColor = drawable.bitmap.getMeanColor()
        } else if (drawable is ColorDrawable) {
            bgMeanColor = drawable.color
        }
        val tmp = bg
        bg = drawable
        if (tmp is BitmapDrawable) { // 太快执行，可能还正在被使用，延时防崩溃
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                tmp.bitmap?.recycle()
            }
        }
    }

    fun save() {
        Coroutine.async {
            synchronized(this) {
                GSON.toJson(configList).let {
                    FileUtils.delete(configFilePath)
                    FileUtils.createFileIfNotExist(configFilePath).writeText(it)
                }
                GSON.toJson(shareConfig).let {
                    FileUtils.delete(shareConfigFilePath)
                    FileUtils.createFileIfNotExist(shareConfigFilePath).writeText(it)
                }
            }
        }
    }

    fun getAllPicBgStr(): ArrayList<String> {
        val list = arrayListOf<String>()
        configList.forEach {
            addPicBgStr(list, it)
        }
        if (::shareConfig.isInitialized) {
            addPicBgStr(list, shareConfig)
        }
        return list
    }

    private fun addPicBgStr(list: MutableList<String>, config: Config) {
        if (config.bgType == 2) {
            list.add(config.bgStr)
        }
        if (config.bgTypeNight == 2) {
            list.add(config.bgStrNight)
        }
    }

    fun deleteDur(): Boolean {
        if (configList.size > 5) {
            val removeIndex = styleSelect
            configList.removeAt(removeIndex)
            if (removeIndex <= readStyleSelect) {
                readStyleSelect -= 1
            }
            if (removeIndex <= comicStyleSelect) {
                comicStyleSelect -= 1
            }
            return true
        }
        return false
    }

    fun clearBgAndCache() {
        val bgs = hashSetOf<String>()
        configList.forEach { config ->
            repeat(2) {
                config.getBgPath(it)?.let { path ->
                    bgs.add(path)
                }
            }
        }
        appCtx.externalFiles.getFile("bg").listFiles()?.forEach {
            if (!bgs.contains(it.absolutePath)) {
                it.delete()
            }
        }
        FileUtils.delete(appCtx.externalCache.getFile("readConfig"))
        val configZipPath = FileUtils.getPath(appCtx.externalCache, "readConfig.zip")
        FileUtils.delete(configZipPath)
    }

    private fun resetAll() {
        DefaultData.readConfigs.let {
            configList.clear()
            configList.addAll(it)
            save()
        }
    }

    // 配置写入读取
    var readBodyToLh = appCtx.getPrefBoolean(PreferKey.readBodyToLh, true)
    var autoReadSpeed = appCtx.getPrefInt(PreferKey.autoReadSpeed, 10)
        set(value) {
            field = value
            appCtx.putPrefInt(PreferKey.autoReadSpeed, value)
        }
    var styleSelect: Int
        get() = if (isComic) comicStyleSelect else readStyleSelect
        set(value) {
            if (isComic) {
                comicStyleSelect = value
            } else {
                readStyleSelect = value
            }
        }
    var readStyleSelect = appCtx.getPrefInt(PreferKey.readStyleSelect)
        set(value) {
            field = value
            if (appCtx.getPrefInt(PreferKey.readStyleSelect) != value) {
                appCtx.putPrefInt(PreferKey.readStyleSelect, value)
            }
        }
    var comicStyleSelect = appCtx.getPrefInt(PreferKey.comicStyleSelect, readStyleSelect)
        set(value) {
            field = value
            if (appCtx.getPrefInt(PreferKey.comicStyleSelect) != value) {
                appCtx.putPrefInt(PreferKey.comicStyleSelect, value)
            }
        }
    var shareLayout = appCtx.getPrefBoolean(PreferKey.shareLayout)
        set(value) {
            field = value
            if (appCtx.getPrefBoolean(PreferKey.shareLayout) != value) {
                appCtx.putPrefBoolean(PreferKey.shareLayout, value)
            }
        }

    /**
     * 两端对齐
     */
    val textFullJustify get() = appCtx.getPrefBoolean(PreferKey.textFullJustify, true)

    /**
     * 底部对齐
     */
    val textBottomJustify get() = appCtx.getPrefBoolean(PreferKey.textBottomJustify, true)

    var hideStatusBar = appCtx.getPrefBoolean(PreferKey.hideStatusBar)
    var hideNavigationBar = appCtx.getPrefBoolean(PreferKey.hideNavigationBar)
    var useZhLayout = appCtx.getPrefBoolean(PreferKey.useZhLayout)

    /** 标点允许出现在行首：无视中文标点禁则，行满一律按宽度断行（需配合 ZhLayout） */
    var allowPunctAtLineStart = appCtx.getPrefBoolean(PreferKey.allowPunctAtLineStart)

    val config get() = if (shareLayout) shareConfig else durConfig

    var bgAlpha: Int
        get() = config.bgAlpha
        set(value) {
            config.bgAlpha = value
        }

    var pageAnim: Int
        get() = config.curPageAnim()
        set(@PageAnim.Anim value) {
            config.setCurPageAnim(value)
        }

    var textFont: String
        get() = config.textFont
        set(value) {
            config.textFont = value
        }

    var textBold: Int
        get() = config.textBold
        set(value) {
            config.textBold = value
        }

    var titleBold: Int
        get() = config.titleBold
        set(value) {
            config.titleBold = value
        }

    /**
     * 获取正文字重值
     *
     * 根据当前模式返回对应的字重值：
     * - 精细模式：直接返回 textBold 值（100~900）
     * - 粗略模式：将 textBold 值转换为标准字重
     *   - 0 (正常) -> 400
     *   - 1 (粗体) -> 700
     *   - 2 (细体) -> 300
     *
     * @return 实际字重值，范围 100~900
     */
    fun getTextBoldWeight(): Int {
        val boldValue = textBold
        return if (AppConfig.textBoldMode == 1) {
            boldValue.coerceIn(100, 900)
        } else {
            when (boldValue) {
                1 -> 700
                2 -> 300
                else -> 400
            }
        }
    }

    /**
     * 获取标题字重值
     *
     * 精细模式下返回独立的标题字重值；
     * 粗略模式下跟随 [textBold] 映射：正常→500(Medium)，粗体→900，细体→400。
     * 粗略模式不再一律用 700：标题只需比正文略重，700 会让"正常"档的标题明显过粗。
     *
     * @return 实际字重值，范围 100~900
     */
    fun getTitleBoldWeight(): Int = if (AppConfig.textBoldMode == 1) {
        titleBold.coerceIn(100, 900)
    } else {
        when (textBold) {
            1 -> 900
            2 -> 400
            else -> 500
        }
    }

    var textSize: Int
        get() = config.textSize
        set(value) {
            config.textSize = value
        }

    var letterSpacing: Float
        get() = config.letterSpacing
        set(value) {
            config.letterSpacing = value
        }

    var lineSpacingExtra: Int
        get() = config.lineSpacingExtra
        set(value) {
            config.lineSpacingExtra = value
        }

    var paragraphSpacing: Int
        get() = config.paragraphSpacing
        set(value) {
            config.paragraphSpacing = value
        }

    /**
     * 正文标题分段后的标题行距：与正文 [lineSpacingExtra] 同单位同量程，但只作用于
     * 分段后的标题各行之间，不跟随正文行距与段距；未开启分段时该项无实际效果
     */
    var titleLineSpacingExtra: Int
        get() = config.titleLineSpacingExtra ?: 12
        set(value) {
            config.titleLineSpacingExtra = value
        }

    /**
     * 标题位置 0:居左 1:居中 2:隐藏 3:靠右
     */
    var titleMode: Int
        get() = config.titleMode
        set(value) {
            config.titleMode = value
        }
    var titleSize: Int
        get() = config.titleSize
        set(value) {
            config.titleSize = value
        }

    /**
     * 是否标题居中
     */
    val isMiddleTitle get() = titleMode == 1

    /**
     * 是否标题靠右
     */
    val isRightTitle get() = titleMode == 3

    var titleTopSpacing: Int
        get() = config.titleTopSpacing
        set(value) {
            config.titleTopSpacing = value
        }

    var titleBottomSpacing: Int
        get() = config.titleBottomSpacing
        set(value) {
            config.titleBottomSpacing = value
        }

    /**
     * 正文标题分段模式 0:关闭 1:按位置 2:按标记 3:按正则
     *
     * 只影响正文标题的显示换行，页眉页脚、书架、目录、朗读标题与替换规则仍用未分段的标题
     */
    var titleSplitMode: Int
        get() = config.titleSplitMode
        set(value) {
            config.titleSplitMode = value
        }

    /**
     * 正文标题分段依据：按位置填数字、按标记填单个字符串（按字面使用）、按正则填表达式
     *
     * 用 orEmpty 兜底：GSON 反序列化旧版本配置时会绕过构造器的默认值，
     * 老用户升级后这个新增字段会是 null，直接读会把 null 当非空 String 用出去
     */
    var titleSplitValue: String
        get() = config.titleSplitValue.orEmpty()
        set(value) {
            config.titleSplitValue = value
        }

    /**
     * 正文标题次行字号倍数：分段后除第一段外的行用它，相对首行标题字号的倍数（0~2），
     * 小于 1 次行比首行小，大于 1 比首行大。
     *
     * 底层字段用可空类型：GSON 反序列化会绕过构造器默认值，旧配置里没有这个字段时
     * 非空 Float 会落成 0f（次行字变成 1px 看不见），可空字段才会是 null，从而与
     * 「用户主动设为 0」区分开，读取时回落默认 1 倍
     */
    var titleSubScale: Float
        get() = config.titleSubScale ?: 1f
        set(value) {
            config.titleSubScale = value
        }

    var paragraphIndent: String
        get() = config.paragraphIndent
        set(value) {
            config.paragraphIndent = value
        }

    var underlineMode: Int
        get() = config.underlineMode
        set(value) {
            config.underlineMode = value
        }

    var underlineOffset: Float
        get() = config.underlineOffset
        set(value) {
            config.underlineOffset = value
        }

    var paddingBottom: Int
        get() = config.paddingBottom
        set(value) {
            config.paddingBottom = value
        }

    var paddingLeft: Int
        get() = config.paddingLeft
        set(value) {
            config.paddingLeft = value
        }

    var paddingRight: Int
        get() = config.paddingRight
        set(value) {
            config.paddingRight = value
        }

    var paddingTop: Int
        get() = config.paddingTop
        set(value) {
            config.paddingTop = value
        }

    var headerPaddingBottom: Int
        get() = config.headerPaddingBottom
        set(value) {
            config.headerPaddingBottom = value
        }

    var headerPaddingLeft: Int
        get() = config.headerPaddingLeft
        set(value) {
            config.headerPaddingLeft = value
        }

    var headerPaddingRight: Int
        get() = config.headerPaddingRight
        set(value) {
            config.headerPaddingRight = value
        }

    var headerPaddingTop: Int
        get() = config.headerPaddingTop
        set(value) {
            config.headerPaddingTop = value
        }

    var footerPaddingBottom: Int
        get() = config.footerPaddingBottom
        set(value) {
            config.footerPaddingBottom = value
        }

    var footerPaddingLeft: Int
        get() = config.footerPaddingLeft
        set(value) {
            config.footerPaddingLeft = value
        }

    var footerPaddingRight: Int
        get() = config.footerPaddingRight
        set(value) {
            config.footerPaddingRight = value
        }

    var footerPaddingTop: Int
        get() = config.footerPaddingTop
        set(value) {
            config.footerPaddingTop = value
        }

    var showHeaderLine: Boolean
        get() = config.showHeaderLine
        set(value) {
            config.showHeaderLine = value
        }

    var showFooterLine: Boolean
        get() = config.showFooterLine
        set(value) {
            config.showFooterLine = value
        }

    fun getExportConfig(): Config {
        val exportConfig = durConfig.copy()
        if (shareLayout) {
            exportConfig.textFont = shareConfig.textFont
            exportConfig.textBold = shareConfig.textBold
            exportConfig.textSize = shareConfig.textSize
            exportConfig.letterSpacing = shareConfig.letterSpacing
            exportConfig.lineSpacingExtra = shareConfig.lineSpacingExtra
            exportConfig.paragraphSpacing = shareConfig.paragraphSpacing
            exportConfig.titleMode = shareConfig.titleMode
            exportConfig.titleSize = shareConfig.titleSize
            exportConfig.titleTopSpacing = shareConfig.titleTopSpacing
            exportConfig.titleBottomSpacing = shareConfig.titleBottomSpacing
            exportConfig.titleSplitMode = shareConfig.titleSplitMode
            exportConfig.titleSplitValue = shareConfig.titleSplitValue
            exportConfig.titleSubScale = shareConfig.titleSubScale
            exportConfig.titleLineSpacingExtra = shareConfig.titleLineSpacingExtra
            exportConfig.paddingBottom = shareConfig.paddingBottom
            exportConfig.paddingLeft = shareConfig.paddingLeft
            exportConfig.paddingRight = shareConfig.paddingRight
            exportConfig.paddingTop = shareConfig.paddingTop
            exportConfig.headerPaddingBottom = shareConfig.headerPaddingBottom
            exportConfig.headerPaddingLeft = shareConfig.headerPaddingLeft
            exportConfig.headerPaddingRight = shareConfig.headerPaddingRight
            exportConfig.headerPaddingTop = shareConfig.headerPaddingTop
            exportConfig.footerPaddingBottom = shareConfig.footerPaddingBottom
            exportConfig.footerPaddingLeft = shareConfig.footerPaddingLeft
            exportConfig.footerPaddingRight = shareConfig.footerPaddingRight
            exportConfig.footerPaddingTop = shareConfig.footerPaddingTop
            exportConfig.showHeaderLine = shareConfig.showHeaderLine
            exportConfig.showFooterLine = shareConfig.showFooterLine
            exportConfig.tipHeaderLeft = shareConfig.tipHeaderLeft
            exportConfig.tipHeaderMiddle = shareConfig.tipHeaderMiddle
            exportConfig.tipHeaderRight = shareConfig.tipHeaderRight
            exportConfig.tipFooterLeft = shareConfig.tipFooterLeft
            exportConfig.tipFooterMiddle = shareConfig.tipFooterMiddle
            exportConfig.tipFooterRight = shareConfig.tipFooterRight
            exportConfig.tipHeaderLeftTemplate = shareConfig.tipHeaderLeftTemplate
            exportConfig.tipHeaderMiddleTemplate = shareConfig.tipHeaderMiddleTemplate
            exportConfig.tipHeaderRightTemplate = shareConfig.tipHeaderRightTemplate
            exportConfig.tipFooterLeftTemplate = shareConfig.tipFooterLeftTemplate
            exportConfig.tipFooterMiddleTemplate = shareConfig.tipFooterMiddleTemplate
            exportConfig.tipFooterRightTemplate = shareConfig.tipFooterRightTemplate
            exportConfig.tipColor = shareConfig.tipColor
            exportConfig.headerMode = shareConfig.headerMode
            exportConfig.footerMode = shareConfig.footerMode
        }
        return exportConfig
    }

    fun getBackupConfigList(): List<Config> = configList.map { normalizeBgPathsForBackup(it.copy()) }

    fun getBackupShareConfig(): Config = normalizeBgPathsForBackup(shareConfig.copy())

    private fun normalizeBgPathsForBackup(config: Config): Config {
        if (config.bgType == 2) {
            config.bgStr = FileUtils.getName(config.bgStr)
        }
        if (config.bgTypeNight == 2) {
            config.bgStrNight = FileUtils.getName(config.bgStrNight)
        }
        return config
    }

    fun import(byteArray: ByteArray): Config {
        val configZipPath = FileUtils.getPath(appCtx.externalCache, "readConfig.zip")
        FileUtils.delete(configZipPath)
        val zipFile = FileUtils.createFileIfNotExist(configZipPath)
        zipFile.writeBytes(byteArray)
        val configDir = appCtx.externalCache.getFile("readConfig")
        configDir.createFolderReplace()
        ZipUtils.unZipToPath(zipFile, configDir)
        val configFile = configDir.getFile(configFileName)
        val config: Config = GSON.fromJsonObject<Config>(configFile.readText()).getOrThrow()
        if (config.textFont.isNotEmpty()) {
            val fontName = config.textFont
            val fontPath =
                FileUtils.getPath(appCtx.externalFiles, "font", fontName)
            val fontFile = configDir.getFile(fontName)
            if (fontFile.exists()) {
                if (!FileUtils.exist(fontPath)) {
                    fontFile.copyTo(File(fontPath))
                }
                config.textFont = fontPath
            } else {
                config.textFont = ""
            }
        }
        if (config.bgType == 2) {
            val bgName = FileUtils.getName(config.bgStr)
            config.bgStr = bgName
            val bgPath = FileUtils.getPath(appCtx.externalFiles, "bg", bgName)
            if (!FileUtils.exist(bgPath)) {
                val bgFile = configDir.getFile(bgName)
                if (bgFile.exists()) {
                    bgFile.copyTo(File(bgPath))
                }
            }
            config.bgStr = bgPath
        } else if (config.bgType == 0) {
            config.bgStr.toColorInt()
        }
        if (config.bgTypeNight == 2) {
            val bgName = FileUtils.getName(config.bgStrNight)
            config.bgStrNight = bgName
            val bgPath = FileUtils.getPath(appCtx.externalFiles, "bg", bgName)
            if (!FileUtils.exist(bgPath)) {
                val bgFile = configDir.getFile(bgName)
                if (bgFile.exists()) {
                    bgFile.copyTo(File(bgPath))
                }
            }
            config.bgStrNight = bgPath
        } else if (config.bgTypeNight == 0) {
            config.bgStrNight.toColorInt()
        }
        config.curTextColor()
        config.curTextAccentColor()
        return config
    }

    @Keep
    data class Config(
        var name: String = "",
        var bgStr: String = "#EEEEEE", // 白天背景
        var bgStrNight: String = "#000000", // 夜间背景
        var bgAlpha: Int = 100, // 背景透明度
        var bgType: Int = 0, // 白天背景类型 0:颜色, 1:assets图片, 2其它图片
        var bgTypeNight: Int = 0, // 夜间背景类型
        private var darkStatusIcon: Boolean = true, // 白天是否暗色状态栏
        private var darkStatusIconNight: Boolean = false, // 晚上是否暗色状态栏
        private var darkStatusIconEInk: Boolean = true,
        private var textColor: String = "#3E3D3B", // 白天文字颜色
        private var textColorNight: String = "#ADADAD", // 夜间文字颜色
        private var textColorEInk: String = "#000000",
        private var textAccentColor: String = "#E53935", // 白天强调文字颜色
        private var textAccentColorNight: String = "#FE4D55", // 夜间强调文字颜色
        private var textAccentColorEInk: String = "#000000",
        private var pageAnim: Int = 0, // 翻页动画
        private var pageAnimEInk: Int = 4,
        var textFont: String = "", // 字体
        var textBold: Int = 0, // 正文字重 粗略模式: 0=正常, 1=粗体, 2=细体; 精细模式: 100~900
        var titleBold: Int = 0, // 标题字重 精细模式专用: 100~900，粗略模式下忽略此值
        var textSize: Int = 20, // 文字大小
        var letterSpacing: Float = 0.1f, // 字间距
        var lineSpacingExtra: Int = 12, // 行间距
        var paragraphSpacing: Int = 2, // 段距
        var titleMode: Int = 0, // 标题位置 0:居左 1:居中 2:隐藏
        var titleSize: Int = 0,
        var titleTopSpacing: Int = 0,
        var titleBottomSpacing: Int = 0,
        var titleSplitMode: Int = 0, // 正文标题分段模式 0:关闭 1:按位置 2:按标记 3:按正则
        var titleSplitValue: String = "", // 正文标题分段依据：位置数字 / 标记 / 正则
        var titleSubScale: Float? = 1f, // 正文标题次行字号相对首行的倍数，0~2；可空用于识别旧配置缺失
        var titleLineSpacingExtra: Int? = 12, // 正文标题分段后的标题行距，与正文行距同单位同量程；可空用于识别旧配置缺失
        var paragraphIndent: String = "　　", // 段落缩进
        var underlineMode: Int = 0, // 下划线
        var underlineWidth: Float = 0.5f, // 下划线粗细(dp)
        private var underlineColor: String = "", // 白天下划线颜色，空=跟随文字颜色
        private var underlineColorNight: String = "", // 夜间下划线颜色，空=跟随文字颜色
        var underlineOffset: Float = 2f, // 下划线距离(dp)
        var paddingBottom: Int = 6,
        var paddingLeft: Int = 16,
        var paddingRight: Int = 16,
        var paddingTop: Int = 6,
        var headerPaddingBottom: Int = 0,
        var headerPaddingLeft: Int = 16,
        var headerPaddingRight: Int = 16,
        var headerPaddingTop: Int = 0,
        var footerPaddingBottom: Int = 6,
        var footerPaddingLeft: Int = 16,
        var footerPaddingRight: Int = 16,
        var footerPaddingTop: Int = 6,
        var showHeaderLine: Boolean = false,
        var showFooterLine: Boolean = true,
        var tipHeaderLeft: Int = ReadTipConfig.time,
        var tipHeaderMiddle: Int = ReadTipConfig.none,
        var tipHeaderRight: Int = ReadTipConfig.battery,
        var tipFooterLeft: Int = ReadTipConfig.chapterTitle,
        var tipFooterMiddle: Int = ReadTipConfig.none,
        var tipFooterRight: Int = ReadTipConfig.pageAndTotal,
        /** 页眉页脚自定义模板，null 表示沿用对应的旧枚举规则 */
        var tipHeaderLeftTemplate: String? = null,
        var tipHeaderMiddleTemplate: String? = null,
        var tipHeaderRightTemplate: String? = null,
        var tipFooterLeftTemplate: String? = null,
        var tipFooterMiddleTemplate: String? = null,
        var tipFooterRightTemplate: String? = null,
        var tipColor: Int = 0,
        var tipDividerColor: Int = -1,
        var headerMode: Int = 0,
        var footerMode: Int = 0,
        /** 页眉字体大小(sp) */
        var headerFontSize: Int = 12,
        /** 页脚字体大小(sp) */
        var footerFontSize: Int = 12,
    ) {

        @Transient
        private var textColorIntEInk = -1

        @Transient
        private var textColorIntNight = -1

        @Transient
        private var textColorInt = -1

        @Transient
        private var initColorInt = false

        /**
         * 墨水屏渲染是否使用独立的 EInk 配置槽。
         *
         * 墨水屏渲染与日夜解耦：日间用 EInk 槽（纸白底/墨黑字），夜间回到夜间槽
         * （墨黑底/浅灰字），与主流墨水屏设备夜间开启前光后的观感一致。
         * 翻页动画例外，只要开着墨水屏渲染一律走 EInk 槽（见 [curPageAnim]）。
         */
        private fun useEInkSlot(): Boolean = AppConfig.isEInkMode && !AppConfig.isNightTheme

        private fun initColorInt() {
            textColorIntEInk = textColorEInk.toColorIntOrDefault(0xFF000000.toInt())
            textColorIntNight = textColorNight.toColorIntOrDefault(0xFFADADAD.toInt())
            textColorInt = textColor.toColorIntOrDefault(0xFF3E3D3B.toInt())
            initColorInt = true
        }

        @Transient
        private var textAccentColorIntEInk = -1

        @Transient
        private var textAccentColorIntNight = -1

        @Transient
        private var textAccentColorInt = -1

        @Transient
        private var initAccentColorInt = false

        private fun initAccentColorInt() {
            textAccentColorIntEInk = textAccentColorEInk.toColorIntOrDefault(0xFF000000.toInt())
            textAccentColorIntNight = textAccentColorNight.toColorIntOrDefault(0xFFFE4D55.toInt())
            textAccentColorInt = textAccentColor.toColorIntOrDefault(0xFFE53935.toInt())
            initAccentColorInt = true
        }

        /**
         * 颜色字符串容错解析。
         * 配置可能来自导入/恢复的其它分支数据（如把颜色"重置为完全透明"写出的 "#0"），
         * 非法值只应回退到默认色，不能让 Color.parseColor 抛出的异常冒到界面。
         */
        private fun String.toColorIntOrDefault(default: Int): Int = runCatching { toColorInt() }.getOrDefault(default)

        fun setCurTextColor(color: Int) {
            val hex = color.argbHexString
            when {
                useEInkSlot() -> {
                    textColorEInk = hex
                    textColorIntEInk = color
                }

                AppConfig.isNightTheme -> {
                    textColorNight = hex
                    textColorIntNight = color
                }

                else -> {
                    textColor = hex
                    textColorInt = color
                }
            }
        }

        fun curTextColor(): Int {
            if (!initColorInt) {
                initColorInt()
            }
            return when {
                useEInkSlot() -> textColorIntEInk
                AppConfig.isNightTheme -> textColorIntNight
                else -> textColorInt
            }
        }

        fun setCurTextAccentColor(color: Int) {
            val hex = color.argbHexString
            when {
                useEInkSlot() -> {
                    textAccentColorEInk = hex
                    textAccentColorIntEInk = color
                }

                AppConfig.isNightTheme -> {
                    textAccentColorNight = hex
                    textAccentColorIntNight = color
                }

                else -> {
                    textAccentColor = hex
                    textAccentColorInt = color
                }
            }
        }

        fun curTextAccentColor(): Int {
            if (!initAccentColorInt) {
                initAccentColorInt()
            }
            return when {
                useEInkSlot() -> textAccentColorIntEInk
                AppConfig.isNightTheme -> textAccentColorIntNight
                else -> textAccentColorInt
            }
        }

        fun setCurUnderlineColor(color: Int) {
            val hex = color.argbHexString
            when {
                useEInkSlot() -> underlineColor = hex
                AppConfig.isNightTheme -> underlineColorNight = hex
                else -> underlineColor = hex
            }
        }

        /**
         * 返回当前模式下划线颜色；为空或非法时回退到当前文字颜色。
         */
        fun curUnderlineColor(): Int {
            val hex = when {
                useEInkSlot() -> underlineColor
                AppConfig.isNightTheme -> underlineColorNight
                else -> underlineColor
            }
            return if (hex.isBlank()) curTextColor() else hex.toColorIntOrDefault(curTextColor())
        }

        fun setCurStatusIconDark(isDark: Boolean) {
            when {
                useEInkSlot() -> darkStatusIconEInk = isDark
                AppConfig.isNightTheme -> darkStatusIconNight = isDark
                else -> darkStatusIcon = isDark
            }
        }

        fun curStatusIconDark(): Boolean = when {
            useEInkSlot() -> darkStatusIconEInk
            AppConfig.isNightTheme -> darkStatusIconNight
            else -> darkStatusIcon
        }

        fun setCurPageAnim(@PageAnim.Anim anim: Int) {
            when {
                AppConfig.isEInkMode -> pageAnimEInk = anim
                else -> pageAnim = anim
            }
        }

        fun curPageAnim(): Int = when {
            AppConfig.isEInkMode -> pageAnimEInk
            else -> pageAnim
        }

        fun setCurBg(bgType: Int, bg: String) {
            // 背景只分日/夜两槽：墨水屏渲染沿用日/夜背景再转灰阶（见 [curBgDrawable]）
            when {
                AppConfig.isNightTheme -> {
                    bgTypeNight = bgType
                    bgStrNight = bg
                }

                else -> {
                    this.bgType = bgType
                    bgStr = bg
                }
            }
        }

        /**
         * 当前背景槽：日/夜。
         *
         * 墨水屏渲染沿用它们（在 [curBgDrawable] 里转灰阶），不再单独取 EInk 槽——
         * 旧实现在墨水屏下用一个纯色顶掉了用户配的日/夜背景（含背景图），
         * 看起来像"背景被清空了"。
         */
        fun curBgStr(): String = if (AppConfig.isNightTheme) bgStrNight else bgStr

        fun curBgType(): Int = if (AppConfig.isNightTheme) bgTypeNight else bgType

        fun curBgDrawable(width: Int, height: Int): Drawable {
            val curBgStr = curBgStr()
            isNineBgImg = curBgStr.endsWith(".9.png")
            if (width == 0 || height == 0) {
                return appCtx.getCompatColor(R.color.background).toDrawable()
            }
            var bgDrawable: Drawable? = null
            val resources = appCtx.resources
            try {
                bgDrawable = when (curBgType()) {
                    0 -> curBgStr.toColorInt().toDrawable()
                    1 -> {
                        val path = "bg" + File.separator + curBgStr
                        val bitmap = BitmapUtils.decodeAssetsBitmap(appCtx, path, width, height)
                        bitmap?.resizeAndRecycle(width, height)?.toDrawable(resources)
                    }

                    else -> {
                        val path = curBgStr.let {
                            if (it.contains(File.separator)) {
                                it
                            } else {
                                FileUtils.getPath(appCtx.externalFiles, "bg", it)
                            }
                        }
                        if (isNineBgImg) {
                            BitmapUtils.decodeNinePatchDrawable(path)
                        } else {
                            val bitmap = BitmapUtils.decodeBitmap(path, width, height)
                            bitmap?.resizeAndRecycle(width, height)?.toDrawable(resources)
                        }
                    }
                }
            } catch (e: OutOfMemoryError) {
                e.printOnDebug()
            } catch (e: Exception) {
                e.printOnDebug()
            }
            return (bgDrawable ?: appCtx.getCompatColor(R.color.background).toDrawable())
                // 墨水屏渲染：背景（颜色或图片）转为灰阶，而不是换成一块纯色；
                // 这里每次都新建 Drawable，改它自己的 colorFilter 不会污染别的读者
                .apply { colorFilter = EInkRender.androidFilterOrNull() }
        }

        fun getBgPath(bgIndex: Int): String? {
            val bgType = when (bgIndex) {
                0 -> bgType
                1 -> bgTypeNight
                else -> error("unknown bgIndex: $bgIndex")
            }
            if (bgType != 2) {
                return null
            }
            val bgStr = when (bgIndex) {
                0 -> bgStr
                1 -> bgStrNight
                else -> error("unknown bgIndex: $bgIndex")
            }
            val path = if (bgStr.contains(File.separator)) {
                bgStr
            } else {
                FileUtils.getPath(appCtx.externalFiles, "bg", bgStr)
            }
            return path
        }

        fun toMap() = mapOf(
            "name" to name,
            "bgStr" to bgStr,
            "bgStrNight" to bgStrNight,
            "bgAlpha" to bgAlpha,
            "bgType" to bgType,
            "bgTypeNight" to bgTypeNight,
            "darkStatusIcon" to darkStatusIcon,
            "darkStatusIconNight" to darkStatusIconNight,
            "darkStatusIconEInk" to darkStatusIconEInk,
            "textColor" to textColor,
            "textColorNight" to textColorNight,
            "textColorEInk" to textColorEInk,
            "textColorInt" to textColorInt,
            "textColorIntNight" to textColorIntNight,
            "textColorIntEInk" to textColorIntEInk,
            "textAccentColor" to textAccentColor,
            "textAccentColorNight" to textAccentColorNight,
            "textAccentColorEInk" to textAccentColorEInk,
            "textAccentColorInt" to textAccentColorInt,
            "textAccentColorIntNight" to textAccentColorIntNight,
            "textAccentColorIntEInk" to textAccentColorIntEInk,
            "pageAnim" to pageAnim,
            "pageAnimEInk" to pageAnimEInk,
            "textFont" to textFont,
            "textBold" to textBold,
            "titleBold" to titleBold,
            "textSize" to textSize,
            "letterSpacing" to letterSpacing,
            "lineSpacingExtra" to lineSpacingExtra,
            "paragraphSpacing" to paragraphSpacing,
            "titleMode" to titleMode,
            "titleSize" to titleSize,
            "titleTopSpacing" to titleTopSpacing,
            "titleBottomSpacing" to titleBottomSpacing,
            "titleSplitMode" to titleSplitMode,
            "titleSplitValue" to titleSplitValue,
            "titleSubScale" to (titleSubScale ?: 1f),
            "titleLineSpacingExtra" to (titleLineSpacingExtra ?: 12),
            "paragraphIndent" to paragraphIndent,
            "underlineMode" to underlineMode,
            "underlineWidth" to underlineWidth,
            "underlineColor" to underlineColor,
            "underlineColorNight" to underlineColorNight,
            "underlineOffset" to underlineOffset,
            "paddingBottom" to paddingBottom,
            "paddingLeft" to paddingLeft,
            "paddingRight" to paddingRight,
            "paddingTop" to paddingTop,
            "headerPaddingBottom" to headerPaddingBottom,
            "headerPaddingLeft" to headerPaddingLeft,
            "headerPaddingRight" to headerPaddingRight,
            "headerPaddingTop" to headerPaddingTop,
            "footerPaddingBottom" to footerPaddingBottom,
            "footerPaddingLeft" to footerPaddingLeft,
            "footerPaddingRight" to footerPaddingRight,
            "footerPaddingTop" to footerPaddingTop,
            "showHeaderLine" to showHeaderLine,
            "showFooterLine" to showFooterLine,
            "tipHeaderLeft" to tipHeaderLeft,
            "tipHeaderMiddle" to tipHeaderMiddle,
            "tipHeaderRight" to tipHeaderRight,
            "tipFooterLeft" to tipFooterLeft,
            "tipFooterMiddle" to tipFooterMiddle,
            "tipFooterRight" to tipFooterRight,
            "tipColor" to tipColor,
            "tipDividerColor" to tipDividerColor,
            "headerMode" to headerMode,
            "footerMode" to footerMode,
            "headerFontSize" to headerFontSize,
            "footerFontSize" to footerFontSize,
        )
    }
}
