package io.legado.app.help.config

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.LruCache
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils as AndroidXColorUtils
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Theme
import io.legado.app.help.DefaultData
import io.legado.app.lib.theme.EInkRender
import io.legado.app.lib.theme.ThemeStateStore
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.ThemeTransition
import io.legado.app.model.BookCover
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getFile
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.hexString
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import androidx.core.content.edit
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.stackBlur
import splitties.init.appCtx
import java.io.File
import androidx.core.graphics.drawable.toDrawable
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import io.legado.app.ui.design.theme.NgThemeGradientDrawable
import io.legado.app.utils.putPrefBoolean
import io.legado.app.ui.design.theme.NgMulticolorLightFieldDrawable

@Keep
object ThemeConfig {
    const val configFileName = "themeConfig.json"
    val configFilePath = FileUtils.getPath(appCtx.filesDir, configFileName)

    /** 单线程调度器，保证所有写操作串行执行，避免并发竞态 */
    private val ioSerialDispatcher = Dispatchers.IO.limitedParallelism(1)

    val configList: ArrayList<Config> by lazy {
        val cList = getConfigs() ?: DefaultData.themeConfigs
        ArrayList(cList)
    }

    private var needClearImg = true

    private val recreateHandler = Handler(Looper.getMainLooper())

    /** 连续编辑（颜色选择器、滑条等）的重建合并窗口：把一串修改合并成一次重建 */
    private const val recreateEditDelay = 500L

    /** [AppConfig.themeMode] 的「跟随系统」取值，见 `R.array.theme_mode_v` 与 `theme_mode` */
    private const val THEME_MODE_SYSTEM = "0"

    /** 已排队广播的延迟，-1 表示当前没有排队，见 [notifyRecreate] */
    private var recreatePendingDelay = -1L

    private val recreateRunnable = Runnable {
        recreatePendingDelay = -1L
        postEvent(EventBus.RECREATE, "")
    }

    /**
     * 请求重建界面（广播 [EventBus.RECREATE]）。
     *
     * @param immediate 一次性动作（日夜切换、跟随系统时系统翻转）传 true：下一次主线程消息即广播，
     *                  不让用户在切换后干等；连续编辑保持默认 false：尾沿防抖 [recreateEditDelay]，
     *                  把一串修改合并成一次重建，避免每拖一下颜色/滑条都重建整个页面。
     *
     * 历史（勿随手改回固定长窗口）：2026-09-10 的「重建风暴」修复曾让所有请求统一走 1500ms 尾沿防抖，
     * 结果是**整次日夜切换被推迟 1.5s 才起步**（用户侧就是"切个主题要等好几秒"），
     * 且在分批送达配置变化的 ROM 上窗口还会被持续顺延。风暴真正的根因是
     * `App.onConfigurationChanged` 又走一次 `applyDayNight` 的反馈环，它已在同一轮修复里断开；
     * 剩下的那对重复请求（App 主动切换 + 随后送来的配置变化回声）改由 [consumeNightModeEcho]
     * 在源头过滤，因此切换路径不再需要靠时间窗来防止重复广播。
     */
    fun notifyRecreate(immediate: Boolean = false) {
        val delay = if (immediate) 0L else recreateEditDelay
        // 已排队的广播只会被更早的时刻提前，不会被后来的请求推迟（合并编辑的同时不拖慢切换）
        if (recreatePendingDelay in 0 until delay) return
        recreateHandler.removeCallbacks(recreateRunnable)
        recreatePendingDelay = delay
        recreateHandler.postDelayed(recreateRunnable, delay)
    }

    /** 本 App 主动发起的日夜切换目标值（仅在实际会引发配置变化时非空），见 [consumeNightModeEcho] */
    private var requestedNightMode: Boolean? = null

    /**
     * 记下「本次日夜切换由 App 主动发起」，供随后的配置变化回调识别自身回声。
     *
     * 只在目标日夜状态与当前配置不同（即确实会收到配置变化回调）时才记录：否则会留下一个陈旧标记，
     * 把后来真正的系统翻转（跟随系统）误判成回声，漏掉那次重建。
     * 必须在 [initNightMode] 改动配置之前调用。
     */
    private fun markNightModeRequested(context: Context) {
        val configNight =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        requestedNightMode = AppConfig.isNightTheme.takeIf { it != configNight }
    }

    /**
     * 消费一次「本 App 主动切换」标记，返回这条配置变化是否为我们自己切换产生的回声。
     *
     * 回声对应的重建请求已在 [applyDayNight] 里发过，再发一次就是重复重建窗口（「重建风暴」的构成之一）。
     * 标记只消费一次，且无论结果如何都会清空：跟随系统时由系统翻转触发的回调、或标记已用掉时
     * 一律返回 false，调用方仍会正常请求重建——最坏情况只是多一次重建，绝不会漏掉重建。
     */
    fun consumeNightModeEcho(night: Boolean): Boolean {
        val requested = requestedNightMode
        requestedNightMode = null
        return requested == night
    }

    /**
     * 当前基础主题（日/夜）。
     *
     * 墨水屏渲染不再是独立主题：它只覆盖色板与交互（见 [AppConfig.isEInkMode]），
     * 日/夜的 XML 主题与 `values-night` 资源限定符仍由这里决定，
     * 因此墨水屏渲染在夜间同样走暗色资源，而不是永远当作白天。
     */
    fun getTheme() = if (AppConfig.isNightTheme) Theme.Dark else Theme.Light

    fun isDarkTheme(): Boolean = getTheme() == Theme.Dark

    fun applyDayNight(context: Context) {
        // 过渡起点（上一轮显示的颜色/背景图）只能在主题改动前快照，且必须早于任何表面按新主题落地
        ThemeTransition.notifyThemeChanged()
        applyTheme(context)
        // 必须在 initNightMode 之前判断：之后配置就已变成新值，无从区分"会否真的收到配置变化回调"
        markNightModeRequested(context)
        initNightMode()
        BookCover.upDefaultCover()
        // 日夜切换是一次性动作：立即请求重建，别让用户在切换后干等（旧版 1500ms 尾沿防抖的代价）
        notifyRecreate(immediate = true)
    }

    fun applyDayNightInit(context: Context) {
        applyTheme(context)
        // 启动时按用户设置设定模式，随后送来的配置变化回调同样是自家回声：
        // 若不标记，启动瞬间就会请求一次重建（还会被 LiveEventBus 粘住，让刚订阅的主界面白重建一次）
        markNightModeRequested(context)
        initNightMode()
    }

    /**
     * 把夜间模式设置同步给 AppCompat。
     *
     * 「跟随系统」必须真的交给 AppCompat 跟随（[AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM]），
     * 不能按"此刻系统的昼夜"固化成 YES/NO：调色板走 [AppConfig.isNightTheme] 的实时系统值，
     * 而 XML/主题资源（`values-night` 限定符、`android.R.attr.textColorPrimary` 等）走 AppCompat 的
     * 默认夜间模式。一旦固化，系统昼夜翻转后就会出现"背景/调色板跟随了、字体颜色没跟随"
     * （浅底浅字、黑底黑字），且重建窗口也救不回来——新窗口仍按固化模式取资源。
     */
    private fun initNightMode() {
        val targetMode = when {
            AppConfig.themeMode == THEME_MODE_SYSTEM ->
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

            AppConfig.isNightTheme -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(targetMode)
    }

    /**
     * 获取链接获取图片文件名
     */
    fun getUrlToFile(url: String): String {
        val suffix = when {
            url.contains(".9.png", ignoreCase = true) -> ".9.png"
            url.contains(".png", ignoreCase = true) -> ".png"
            url.contains(".gif", ignoreCase = true) -> ".gif"
            url.contains("webp", ignoreCase = true) -> ".webp"
            else -> ".jpg"
        }
        return MD5Utils.md5Encode16(url) + suffix
    }

    fun getBgImage(context: Context, metrics: DisplayMetrics): Drawable? {
        val themeMode = getTheme()
        val preferenceKey = when (themeMode) {
            Theme.Light -> PreferKey.bgImage
            Theme.Dark -> PreferKey.bgImageN
            else -> return null
        }
        var path = context.getPrefString(preferenceKey)
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http")) {
            val name = getUrlToFile(path)
            val fileRoot = context.externalFiles
            val filePath = FileUtils.getPath(fileRoot, preferenceKey, name)
            if (!FileUtils.exist(filePath)) {
                appCtx.toastOnUi("未缓存在线背景图\n请重新应用主题")
                return null
            }
            path = filePath
        } else if (!path.contains(File.separator)) {
            // 只有文件名，拼接完整路径
            val filePath = FileUtils.getPath(context.externalFiles, preferenceKey, path)
            if (FileUtils.exist(filePath)) {
                path = filePath
            } else {
                return null
            }
        }
        if (path.endsWith(".9.png")) {
            return BitmapUtils.decodeNinePatchDrawable(path)?.withEInkFilter()
        }
        val bgImgBlu = when (themeMode) {
            Theme.Light -> context.getPrefInt(PreferKey.bgImageBlurring, 0)
            Theme.Dark -> context.getPrefInt(PreferKey.bgImageNBlurring, 0)
            else -> 0
        }
        // 当 Activity 在后台重建时 windowSize 可能为 0，使用屏幕真实尺寸作为兜底
        val safeWidth = if (metrics.widthPixels > 0) metrics.widthPixels else context.resources.displayMetrics.widthPixels
        val safeHeight = if (metrics.heightPixels > 0) metrics.heightPixels else context.resources.displayMetrics.heightPixels
        val bgImage = BitmapUtils
            .decodeBitmap(path, safeWidth, safeHeight)
        if (bgImgBlu == 0) {
            return bgImage?.toDrawable(context.resources)?.withEInkFilter()
        }
        return bgImage?.stackBlur(bgImgBlu)?.toDrawable(context.resources)?.withEInkFilter()
    }

    /**
     * 墨水屏渲染下的背景图滤镜（不开启时返回原 Drawable，并清掉可能残留的旧滤镜）。
     *
     * 滤镜挂在 Drawable 实例上而不是把位图重画一遍：`BitmapDrawable.setColorFilter` 存的是
     * state 里的 Paint，`constantState?.newDrawable()?.mutate()` 会连着 Paint 一起复制，
     * 所以 [getCachedBgImage] 取出的副本、以及主界面 content_container 的玻璃采样副本都带着滤镜，
     * 每帧不需要再加一次离屏合成。签名里带了墨水屏开关（见 [getBackgroundSignature]），
     * 缓存不会在开关两态之间混用。
     */
    private fun Drawable.withEInkFilter(): Drawable = apply {
        colorFilter = EInkRender.androidFilterOrNull()
    }

    // ==================== 背景图进程级缓存（消除重建时的纯色闪烁） ====================

    /** 背景图签名 → 解码结果缓存。整屏 ARGB 位图约 10MB/张，最多保留 2 张（当前 + 上一张切换占位） */
    private val bgDrawableCache = object : LruCache<String, Drawable>(2) {}

    /** 最近一次成功解码的背景图（签名 + Drawable），未命中缓存时用作解码期间的占位 */
    @Volatile
    private var lastBgImage: Pair<String, Drawable>? = null

    /**
     * 计算当前主题背景的签名，取图逻辑与 [getBgImage] 保持一致。
     * 纳入主题模式（日/夜）、背景路径、文件最后修改时间与大小、模糊强度与**墨水屏渲染开关**，
     * 任一变化都会使签名不同而触发重新解码。
     * 未配置背景图、或配置的图片文件不存在时返回 null（此时应显示纯色底，
     * 不得使用占位图）。
     */
    fun getBackgroundSignature(context: Context): String? {
        val night = AppConfig.isNightTheme
        val prefKey = if (night) PreferKey.bgImageN else PreferKey.bgImage
        val path = resolveBgImagePath(context) ?: return null
        val blurring = context.getPrefInt(
            if (night) PreferKey.bgImageNBlurring else PreferKey.bgImageBlurring,
            0,
        )
        val file = File(path)
        return "bg:$prefKey:${file.absolutePath}:${file.lastModified()}:${file.length()}:$blurring:${AppConfig.isEInkMode}"
    }

    /**
     * 解析当前日/夜主题下窗口背景图（主界面壁纸）的真实文件路径。
     * 与 [getBgImage] 同一套规则：在线背景需先落到缓存文件，仅文件名的需拼接完整路径，
     * 绝对路径须为真实存在的文件，否则视为无背景图。
     */
    fun resolveBgImagePath(context: Context): String? {
        val prefKey = if (AppConfig.isNightTheme) PreferKey.bgImageN else PreferKey.bgImage
        val rawPath = context.getPrefString(prefKey).orEmpty()
        if (rawPath.isBlank()) return null
        return when {
            rawPath.startsWith("http") ->
                FileUtils
                    .getPath(context.externalFiles, prefKey, getUrlToFile(rawPath))
                    .takeIf { FileUtils.exist(it) }
            !rawPath.contains(File.separator) ->
                FileUtils
                    .getPath(context.externalFiles, prefKey, rawPath)
                    .takeIf { FileUtils.exist(it) }
            else -> rawPath.takeIf { File(it).isFile }
        }
    }

    /** 背景图亮度采样尺寸：只需判断整体偏亮还是偏暗，24x24 足够而解码开销可忽略 */
    private const val bgLuminanceSampleSize = 24

    /**
     * 居中裁切采样时的高度比例下限。
     *
     * 顶部/底栏这类"宽条"容器上，窄高图按宽高比算出来的可见条带可能只剩一两行采样像素，
     * 平均值会随图片内容抖动；取个下限保证采样面足够大。
     */
    private const val cropBandMinFraction = 0.2f

    /**
     * 图片采样结果：平均亮度与平均色。
     *
     * 同一趟解码同时算出两者——"图是深是浅"与"图是什么颜色"是同一个采样，
     * 分成两次开流既浪费也容易让两处结论不一致。
     */
    private class ImageSample(val luminance: Float, val averageColor: Int)

    /** 采样失败（图片损坏/解不出来）的占位：亮度取负值，[getImageLuminance] 据此返回 null */
    private val failedImageSample = ImageSample(-1f, 0)

    /** 图片签名（路径 + 修改时间 + 大小 + 裁切比例）→ 采样结果。容量按日/夜与前后两张图留余量 */
    private val bgSampleCache = object : LruCache<String, ImageSample>(8) {}

    /**
     * 解码缩略图并采样平均亮度与平均色；结果按路径 + 修改时间 + 大小 + 裁切比例缓存，失败也缓存。
     *
     * 这类图会在顶栏重组热路径上被反复问到，不缓存就会每次重新开流解码。
     *
     * @param cropAspectRatio 非空且为正时，只统计**居中裁切**成该宽高比的那条横向区域
     *   （见 [getImageAverageColor] 的说明）；null 表示按整图统计。
     */
    private fun sampleImage(path: String, cropAspectRatio: Float?): ImageSample {
        val file = File(path)
        val key = "${file.absolutePath}|${file.lastModified()}|${file.length()}|${cropAspectRatio ?: 0f}"
        bgSampleCache.get(key)?.let { return it }
        val sample = kotlin.runCatching {
            val bitmap = BitmapUtils
                .decodeBitmap(path, bgLuminanceSampleSize, bgLuminanceSampleSize)
                ?: return@runCatching failedImageSample
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return@runCatching failedImageSample
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            bitmap.recycle()
            // center-crop 显示时纵向露出的比例：可见高度 = 图片宽 / 目标宽高比
            val bandFraction = cropAspectRatio
                ?.takeIf { it > 0f }
                ?.let { (width / it) / height }
                ?.coerceIn(cropBandMinFraction, 1f)
                ?: 1f
            val bandHeight = (height * bandFraction + 0.5f).toInt().coerceIn(1, height)
            val top = (height - bandHeight) / 2
            var luminanceSum = 0.0
            var red = 0L
            var green = 0L
            var blue = 0L
            var count = 0
            for (y in top until top + bandHeight) {
                val rowStart = y * width
                for (x in 0 until width) {
                    val pixel = pixels[rowStart + x]
                    luminanceSum += AndroidXColorUtils.calculateLuminance(pixel)
                    red += Color.red(pixel)
                    green += Color.green(pixel)
                    blue += Color.blue(pixel)
                    count++
                }
            }
            ImageSample(
                luminance = (luminanceSum / count).toFloat(),
                averageColor = Color.rgb(
                    (red / count).toInt(),
                    (green / count).toInt(),
                    (blue / count).toInt(),
                ),
            )
        }.getOrDefault(failedImageSample)
        bgSampleCache.put(key, sample)
        return sample
    }

    /**
     * 图片文件的平均亮度（0..1），解不出来时返回 null。
     *
     * 用于"压在图片上的文字该用深色还是浅色"（顶栏透明时压在主界面壁纸上）。
     */
    fun getImageLuminance(path: String): Float? = sampleImage(path, cropAspectRatio = null).luminance.takeIf { it >= 0f }

    /**
     * 图片文件的平均色（不含 alpha），解不出来时返回 null。
     *
     * 与 [getImageLuminance] 同一份采样。亮度只能回答"图是深是浅"；当这张图是**半透明压在半透明
     * 底色上**时（顶栏壁纸就是这样），必须先按同一套透明度把两侧合成成一块颜色，才能判深浅。
     *
     * @param cropAspectRatio 非空时只统计居中裁切成该宽高比的区域。图片按 center-crop 铺开时四边
     *   根本显示不出来，而宽图上下常带留白/黑边——按整图平均会把这些从不显示的区域算进去，
     *   把"实际很亮"的图判成暗图（2026-10-12 的顶栏白字压浅底就是这个原因）。
     *   传 null 表示按整图统计。
     */
    fun getImageAverageColor(path: String, cropAspectRatio: Float? = null): Int? = sampleImage(path, cropAspectRatio)
        .takeIf { it.luminance >= 0f }
        ?.averageColor

    /**
     * 窗口背景图（主界面壁纸）的平均亮度（0..1）；未配置背景图或解不出来时返回 null。
     *
     * 顶栏透明时文字与图标实际压在壁纸上，只按主题背景色判断深浅会在"浅色底 + 深色壁纸"
     * 这类组合里取反（深色文字压深色壁纸），表现为顶栏文字/图标与底色撞色。
     */
    fun getBgImageLuminance(context: Context): Float? {
        val path = resolveBgImagePath(context) ?: return null
        return getImageLuminance(path)
    }

    /** 命中缓存返回独立副本（mutate），避免多窗口共享同一 Drawable 实例导致状态冲突 */
    fun getCachedBgImage(signature: String): Drawable? = bgDrawableCache.get(signature)?.constantState?.newDrawable()?.mutate()

    /** 缓存解码结果 */
    fun cacheBgImage(signature: String, drawable: Drawable) {
        bgDrawableCache.put(signature, drawable)
        lastBgImage = signature to drawable
    }

    /** 最近一次应用的背景图（排除指定签名），供未命中缓存时作占位，返回独立副本 */
    fun getLastBgImage(excludeSignature: String): Drawable? = lastBgImage?.takeIf { it.first != excludeSignature }
        ?.second?.constantState?.newDrawable()?.mutate()

    suspend fun upConfig() {
        addConfigs(DefaultData.themeConfigs)
    }

    /**
     * 升级默认主题配置
     * 检查用户是否使用旧的默认主题，如果是则更新为新的默认主题
     */
    fun upDefaultThemeConfig() {
        val context = appCtx

        val dayThemeName = context.getPrefString(PreferKey.dThemeName)
        val nightThemeName = context.getPrefString(PreferKey.dNThemeName)

        val newDayPrimary = context.getCompatColor(R.color.default_primary)
        val newDayAccent = context.getCompatColor(R.color.default_accent)
        val newDayBackground = context.getCompatColor(R.color.default_background)
        val newDayBottomBackground = context.getCompatColor(R.color.default_bottom_background)
        val newNightPrimary = context.getCompatColor(R.color.default_night_primary)
        val newNightAccent = context.getCompatColor(R.color.default_night_accent)
        val newNightBackground = context.getCompatColor(R.color.default_night_background)
        val newNightBottomBackground = context.getCompatColor(R.color.default_night_bottom_background)

        if (dayThemeName == "默认") {
            context.putPrefInt(PreferKey.cPrimary, newDayPrimary)
            context.putPrefInt(PreferKey.cAccent, newDayAccent)
            context.putPrefInt(PreferKey.cBackground, newDayBackground)
            context.putPrefInt(PreferKey.cBBackground, newDayBottomBackground)
            context.putPrefString(PreferKey.dThemeName, "红柚白")
        }

        if (nightThemeName == "默认") {
            context.putPrefInt(PreferKey.cNPrimary, newNightPrimary)
            context.putPrefInt(PreferKey.cNAccent, newNightAccent)
            context.putPrefInt(PreferKey.cNBackground, newNightBackground)
            context.putPrefInt(PreferKey.cNBBackground, newNightBottomBackground)
            context.putPrefString(PreferKey.dNThemeName, "A屏黑")
        }
    }

    suspend fun replaceConfigs(newConfigs: List<Config>?) {
        val validConfigs = newConfigs?.filter { validateConfig(it) } ?: emptyList()
        configList.clear()
        configList.addAll(validConfigs)
        save()
    }

    suspend fun save() {
        val json = GSON.toJson(configList)
        withContext(ioSerialDispatcher) {
            FileUtils.delete(configFilePath)
            FileUtils.createFileIfNotExist(configFilePath).writeText(json)
        }
    }

    suspend fun delConfig(index: Int) {
        configList.removeAt(index)
        save()
    }

    suspend fun toTopConfigs(positions: List<Int>) {
        val configs = positions.map { configList[it] }
        positions.sortedDescending().forEach { configList.removeAt(it) }
        configList.addAll(0, configs)
        save()
    }

    suspend fun addConfig(json: String): Int {
        val trimmedJson = json.trim { it < ' ' }
        var count = 0
        GSON.fromJsonArray<Config>(trimmedJson).getOrNull()?.let { configs ->
            configs.forEach { config ->
                if (validateConfig(config)) {
                    addConfig(config)
                    count++
                }
            }
            return count
        }
        trimmedJson.lines().forEach { line ->
            val lineTrimmed = line.trim()
            if (lineTrimmed.isNotEmpty() && lineTrimmed.startsWith("{")) {
                GSON.fromJsonObject<Config>(lineTrimmed).getOrNull()?.let { config ->
                    if (validateConfig(config)) {
                        addConfig(config)
                        count++
                    }
                }
            }
        }
        if (count == 0) {
            GSON.fromJsonObject<Config>(trimmedJson).getOrNull()?.let {
                if (validateConfig(it)) {
                    addConfig(it)
                    count = 1
                }
            }
        }
        return count
    }

    suspend fun addConfig(newConfig: Config) {
        if (!validateConfig(newConfig)) {
            return
        }
        var hasTheme = false
        configList.forEachIndexed { index, config ->
            if (newConfig.themeName == config.themeName && newConfig.isNightTheme == config.isNightTheme) {
                configList[index] = newConfig
                hasTheme = true
                return@forEachIndexed
            }
        }
        if (!hasTheme) {
            configList.add(newConfig)
        }
        save()
    }

    suspend fun addConfigs(newConfigs: List<Config>?) {
        val newConfigs = newConfigs?.filter {
            validateConfig(it)
        }
        if (newConfigs.isNullOrEmpty()) {
            return
        }
        newConfigs.forEach { newConfig ->
            val existingIndex = configList.indexOfFirst {
                it.themeName == newConfig.themeName && it.isNightTheme == newConfig.isNightTheme
            }
            if (existingIndex != -1) {
                configList[existingIndex] = newConfig
            } else {
                configList.add(newConfig)
            }
        }
        save()
    }

    private fun validateConfig(config: Config): Boolean {
        try {
            config.primaryColor.toColorInt()
            config.accentColor.toColorInt()
            config.backgroundColor.toColorInt()
            config.bottomBackground.toColorInt()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun getConfigs(): List<Config>? {
        val configFile = File(configFilePath)
        if (configFile.exists()) {
            kotlin.runCatching {
                val json = configFile.readText()
                return GSON.fromJsonArray<Config>(json).getOrThrow()
            }.onFailure {
                it.printOnDebug()
            }
        }
        return null
    }

    fun applyConfig(context: Context, config: Config, applyNow: Boolean = true) {
        try {
            if (needClearImg) {
                needClearImg = false
                clearBg(context)
            }
            val primary = config.primaryColor.toColorInt()
            val accent = config.accentColor.toColorInt()
            val background = config.backgroundColor.toColorInt()
            val bBackground = config.bottomBackground.toColorInt()
            val isNightTheme = config.isNightTheme
            val transparentNavBar = config.transparentNavBar
            val backgroundPath = config.backgroundImgPath
            if (backgroundPath != null && backgroundPath.startsWith("http")) {
                val fileRoot = context.externalFiles
                val preferenceKey = if (isNightTheme) {
                    PreferKey.bgImageN
                } else {
                    PreferKey.bgImage
                }
                val name = getUrlToFile(backgroundPath)
                val fileFold = File(fileRoot, preferenceKey)
                if (!fileFold.exists()) {
                    fileFold.mkdirs()
                }
                val fileImg = File(fileFold, name)
                if (!fileImg.exists()) {
                    appCtx.toastOnUi("下载背景图片中...")
                    Coroutine.async {
                        kotlin.runCatching {
                            val res = okHttpClient.newCallResponse(0) {
                                url(backgroundPath)
                            }
                            res.body.byteStream().use { inputStream ->
                                FileOutputStream(fileImg).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }.onSuccess {
                            appCtx.toastOnUi("背景图下载成功\n请重新应用主题")
                        }.onFailure {
                            appCtx.toastOnUi(it.localizedMessage)
                        }
                    }
                    return
                }
            }
            val backgroundBlur = config.backgroundImgBlur
            // 批量写入：合并为单次 edit()，减少 8 次 Editor 创建 + 8 次磁盘调度为 1 次
            context.defaultSharedPreferences.edit {
                if (isNightTheme) {
                    putString(PreferKey.dNThemeName, config.themeName)
                    putInt(PreferKey.cNPrimary, primary)
                    putInt(PreferKey.cNAccent, accent)
                    putInt(PreferKey.cNBackground, background)
                    putInt(PreferKey.cNBBackground, bBackground)
                    putBoolean(PreferKey.tNavBarN, transparentNavBar)
                    putString(PreferKey.bgImageN, backgroundPath)
                    putInt(PreferKey.bgImageNBlurring, backgroundBlur)
                } else {
                    putString(PreferKey.dThemeName, config.themeName)
                    putInt(PreferKey.cPrimary, primary)
                    putInt(PreferKey.cAccent, accent)
                    putInt(PreferKey.cBackground, background)
                    putInt(PreferKey.cBBackground, bBackground)
                    putBoolean(PreferKey.tNavBar, transparentNavBar)
                    putString(PreferKey.bgImage, backgroundPath)
                    putInt(PreferKey.bgImageBlurring, backgroundBlur)
                }
            }
            if (applyNow) {
                AppConfig.isNightTheme = isNightTheme
                applyDayNight(context)
            }
        } catch (e: Exception) {
            AppLog.put("设置主题出错\n$e", e, true)
        }
    }

    fun getDurConfig(context: Context): Config {
        val isNight = AppConfig.isNightTheme
        val name = if (isNight) {
            context.getPrefString(PreferKey.dNThemeName) ?: ""
        } else {
            context.getPrefString(PreferKey.dThemeName) ?: ""
        }
        return if (isNight) {
            getNightTheme(context, name)
        } else {
            getDayTheme(context, name)
        }
    }

    private fun getDayTheme(context: Context, name: String): Config {
        val primary =
            context.getPrefInt(PreferKey.cPrimary, context.getCompatColor(R.color.default_primary))
        val accent =
            context.getPrefInt(PreferKey.cAccent, context.getCompatColor(R.color.default_accent))
        val background =
            context.getPrefInt(PreferKey.cBackground, context.getCompatColor(R.color.default_background))
        val bBackground =
            context.getPrefInt(PreferKey.cBBackground, context.getCompatColor(R.color.default_bottom_background))
        val transparentNavBar =
            context.getPrefBoolean(PreferKey.tNavBar, false)
        val bgImgPath =
            context.getPrefString(PreferKey.bgImage)
        val bgImgBlur =
            context.getPrefInt(PreferKey.bgImageBlurring, 0)

        return Config(
            themeName = name,
            isNightTheme = false,
            primaryColor = "#${primary.hexString}",
            accentColor = "#${accent.hexString}",
            backgroundColor = "#${background.hexString}",
            bottomBackground = "#${bBackground.hexString}",
            transparentNavBar = transparentNavBar,
            backgroundImgPath = bgImgPath,
            backgroundImgBlur = bgImgBlur,
        )
    }

    suspend fun saveDayTheme(context: Context, name: String) {
        val config = getDayTheme(context, name)
        addConfig(config)
    }

    private fun getNightTheme(context: Context, name: String): Config {
        val primary =
            context.getPrefInt(
                PreferKey.cNPrimary,
                context.getCompatColor(R.color.default_night_primary),
            )
        val accent =
            context.getPrefInt(
                PreferKey.cNAccent,
                context.getCompatColor(R.color.default_night_accent),
            )
        val background =
            context.getPrefInt(PreferKey.cNBackground, context.getCompatColor(R.color.default_night_background))
        val bBackground =
            context.getPrefInt(PreferKey.cNBBackground, context.getCompatColor(R.color.default_night_bottom_background))
        val transparentNavBar =
            context.getPrefBoolean(PreferKey.tNavBarN, false)
        val bgImgPath =
            context.getPrefString(PreferKey.bgImageN)
        val bgImgBlur =
            context.getPrefInt(PreferKey.bgImageNBlurring, 0)
        return Config(
            themeName = name,
            isNightTheme = true,
            primaryColor = "#${primary.hexString}",
            accentColor = "#${accent.hexString}",
            backgroundColor = "#${background.hexString}",
            bottomBackground = "#${bBackground.hexString}",
            transparentNavBar = transparentNavBar,
            backgroundImgPath = bgImgPath,
            backgroundImgBlur = bgImgBlur,
        )
    }

    suspend fun saveNightTheme(context: Context, name: String) {
        val config = getNightTheme(context, name)
        addConfig(config)
    }

    /**
     * 更新主题
     */
    fun applyTheme(context: Context) = with(context) {
        when {
            AppConfig.isEInkMode -> {
                // 墨水屏渲染：与日夜模式解耦，日间"纸白 + 墨黑"、夜间"墨黑底 + 浅灰字"，
                // 对应主流墨水屏设备在自然光与前光（夜间）下的观感；纯色平面、无透明底栏，
                // 不引入任何彩色。primary 取纸色（供 XML 主题按明度判定日/暗），accent 即"墨水"色。
                val night = AppConfig.isNightTheme
                val paper = if (night) 0xFF000000.toInt() else Color.WHITE
                val ink = if (night) 0xFFC8C8C8.toInt() else Color.BLACK
                ThemeStore.editTheme(this)
                    .primaryColor(paper)
                    .accentColor(ink)
                    .backgroundColor(paper)
                    .bottomBackground(paper)
                    .transparentNavBar(false)
                    .apply()
            }

            AppConfig.isNightTheme -> {
                val primary =
                    getPrefInt(PreferKey.cNPrimary, getCompatColor(R.color.default_night_primary))
                val accent =
                    getPrefInt(PreferKey.cNAccent, getCompatColor(R.color.default_night_accent))
                var background =
                    getPrefInt(PreferKey.cNBackground, getCompatColor(R.color.default_night_background))
                if (ColorUtils.isColorLight(background)) {
                    background = getCompatColor(R.color.default_night_background)
                    putPrefInt(PreferKey.cNBackground, background)
                }
                val bBackground =
                    getPrefInt(PreferKey.cNBBackground, getCompatColor(R.color.default_night_bottom_background))
                val transparentNavBar =
                    getPrefBoolean(PreferKey.tNavBarN, false)
                ThemeStore.editTheme(this)
                    .primaryColor(ColorUtils.withAlpha(primary, 1f))
                    .accentColor(ColorUtils.withAlpha(accent, 1f))
                    .backgroundColor(ColorUtils.withAlpha(background, 1f))
                    .bottomBackground(ColorUtils.withAlpha(bBackground, 1f))
                    .transparentNavBar(transparentNavBar)
                    .apply()
            }

            else -> {
                val primary =
                    getPrefInt(PreferKey.cPrimary, getCompatColor(R.color.default_primary))
                val accent =
                    getPrefInt(PreferKey.cAccent, getCompatColor(R.color.default_accent))
                var background =
                    getPrefInt(PreferKey.cBackground, getCompatColor(R.color.default_background))
                if (!ColorUtils.isColorLight(background)) {
                    background = getCompatColor(R.color.default_background)
                    putPrefInt(PreferKey.cBackground, background)
                }
                val bBackground =
                    getPrefInt(PreferKey.cBBackground, getCompatColor(R.color.default_bottom_background))
                val transparentNavBar =
                    getPrefBoolean(PreferKey.tNavBar, false)
                ThemeStore.editTheme(this)
                    .primaryColor(ColorUtils.withAlpha(primary, 1f))
                    .accentColor(ColorUtils.withAlpha(accent, 1f))
                    .backgroundColor(ColorUtils.withAlpha(background, 1f))
                    .bottomBackground(ColorUtils.withAlpha(bBackground, 1f))
                    .transparentNavBar(transparentNavBar)
                    .apply()
            }
        }
        // 色板已更新：让 Compose 侧的读色（LegadoTheme/CommonPageColors）立刻重组，
        // 不依赖随后的重建窗口（跟随系统翻转、重建被判定为回声而跳过等场景）
        ThemeStateStore.notifyThemeChanged()
    }

    fun clearBg(context: Context) {
        val (nightConfigs, dayConfigs) = configList.partition { it.isNightTheme }
        val fileRoot = context.externalFiles
        val nightBackgroundImgPaths = nightConfigs.mapNotNull {
            val path = it.backgroundImgPath ?: return@mapNotNull null
            if (path.startsWith("http")) {
                val name = getUrlToFile(path)
                FileUtils.getPath(fileRoot, PreferKey.bgImageN, name)
            } else {
                path
            }
        }
        val dayBackgroundImgPaths = dayConfigs.mapNotNull {
            val path = it.backgroundImgPath ?: return@mapNotNull null
            if (path.startsWith("http")) {
                val name = getUrlToFile(path)
                FileUtils.getPath(fileRoot, PreferKey.bgImage, name)
            } else {
                path
            }
        }
        appCtx.externalFiles.getFile(PreferKey.bgImage).listFiles()?.forEach {
            if (!dayBackgroundImgPaths.contains(it.absolutePath)) {
                it.delete()
            }
        }
        appCtx.externalFiles.getFile(PreferKey.bgImageN).listFiles()?.forEach {
            if (!nightBackgroundImgPaths.contains(it.absolutePath)) {
                it.delete()
            }
        }
    }

    @Keep
    data class Config(
        var themeName: String,
        var isNightTheme: Boolean,
        var primaryColor: String,
        var accentColor: String,
        var backgroundColor: String,
        var bottomBackground: String,
        var transparentNavBar: Boolean,
        var backgroundImgPath: String?,
        var backgroundImgBlur: Int,
    ) {

        override fun hashCode(): Int = GSON.toJson(this).hashCode()

        override fun equals(other: Any?): Boolean {
            other ?: return false
            if (other is Config) {
                return other.themeName == themeName &&
                    other.isNightTheme == isNightTheme &&
                    other.primaryColor == primaryColor &&
                    other.accentColor == accentColor &&
                    other.backgroundColor == backgroundColor &&
                    other.bottomBackground == bottomBackground &&
                    other.transparentNavBar == transparentNavBar &&
                    other.backgroundImgPath == backgroundImgPath &&
                    other.backgroundImgBlur == backgroundImgBlur
            }
            return false
        }

        fun toMap() = mapOf(
            "themeName" to themeName,
            "isNightTheme" to isNightTheme,
            "primaryColor" to primaryColor,
            "accentColor" to accentColor,
            "backgroundColor" to backgroundColor,
            "bottomBackground" to bottomBackground,
            "transparentNavBar" to transparentNavBar,
            "backgroundImgPath" to backgroundImgPath,
            "backgroundImgBlur" to backgroundImgBlur,
        )
    }


    // ==================== NG 主题移植：托管主题应用 / 渐变与抽屉背景 ====================
    private const val ASSET_BACKGROUND_PREFIX = "asset://"
    private const val THEME_MODE_EINK = "3"

    private fun getAssetToFile(assetPath: String): String {
        val suffix = when {
            assetPath.contains(".9.png", ignoreCase = true) -> ".9.png"
            assetPath.endsWith(".png", ignoreCase = true) -> ".png"
            assetPath.endsWith(".gif", ignoreCase = true) -> ".gif"
            assetPath.endsWith(".webp", ignoreCase = true) -> ".webp"
            assetPath.endsWith(".jpg", ignoreCase = true) -> ".jpg"
            assetPath.endsWith(".jpeg", ignoreCase = true) -> ".jpg"
            else -> ".png"
        }
        return MD5Utils.md5Encode16(assetPath) + suffix
    }

    private fun cachedBackgroundPath(context: Context, path: String, preferenceKey: String): String {
        val fileRoot = context.externalFiles
        return when {
            path.startsWith("http") -> FileUtils.getPath(fileRoot, preferenceKey, getUrlToFile(path))
            path.startsWith(ASSET_BACKGROUND_PREFIX) ->
                FileUtils.getPath(fileRoot, preferenceKey, getAssetToFile(path.removePrefix(ASSET_BACKGROUND_PREFIX)))
            else -> path
        }
    }

    private fun copyAssetBackgroundIfNeed(
        context: Context,
        preferenceKey: String,
        backgroundPath: String,
        forceRefresh: Boolean = false,
    ): String {
        val assetPath = resolveBundledBackgroundAssetPath(backgroundPath.removePrefix(ASSET_BACKGROUND_PREFIX))
        val canonicalBackgroundPath = "$ASSET_BACKGROUND_PREFIX$assetPath"
        val filePath = cachedBackgroundPath(context, canonicalBackgroundPath, preferenceKey)
        val file = File(filePath)
        if (forceRefresh || !file.exists() || file.length() == 0L) {
            FileUtils.createFileIfNotExist(filePath)
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(file).use { outputStream -> inputStream.copyTo(outputStream) }
            }
        }
        return file.absolutePath
    }

    internal fun drawerBackgroundImagePath(context: Context, isDark: Boolean): String? {
        val mode = NgThemeModeStore.current(context)
        if (mode == NgThemePresentationMode.SOFT_GRADIENT ||
            mode == NgThemePresentationMode.MULTICOLOR ||
            mode == NgThemePresentationMode.EINK
        ) return null
        val preferenceKey = if (isDark) PreferKey.bgImageN else PreferKey.bgImage
        val path = if (mode == NgThemePresentationMode.EFFECT_THEME) {
            val theme = NgEffectThemeSelection.current(context)
            (if (isDark) theme?.darkBackground else theme?.lightBackground)?.path
        } else context.getPrefString(preferenceKey)
        if (path.isNullOrBlank()) return null
        return when {
            path.startsWith(ASSET_BACKGROUND_PREFIX) ->
                "file:///android_asset/" + resolveBundledBackgroundAssetPath(path.removePrefix(ASSET_BACKGROUND_PREFIX))
            path.startsWith("http") -> cachedBackgroundPath(context, path, preferenceKey).takeIf { File(it).isFile }
            else -> path
        }
    }

    fun getGradientBgImage(context: Context): Drawable? = when (NgThemeModeStore.current(context)) {
        NgThemePresentationMode.SOFT_GRADIENT -> NgThemeGradientDrawable(NgSoftGradientTheme.gradient(context))
        NgThemePresentationMode.MULTICOLOR -> NgMulticolorLightFieldDrawable()
        else -> null
    }

    fun applyThemeMode(context: Context, themeMode: String) {
        val normalizedMode = normalizeThemeMode(themeMode)
        AppConfig.themeMode = normalizedMode
        AppConfig.isEInkMode = normalizedMode == THEME_MODE_EINK
        context.putPrefString(PreferKey.themeMode, normalizedMode)
        applyDayNight(context)
    }

    internal fun applyManagedTheme(
        context: Context,
        theme: NgManagedTheme,
        preparedDrawer: NgPreparedDrawerProfile? = null,
        renderEffect: Boolean = true,
    ): Boolean {
        var drawerSettings = preparedDrawer
        return runCatching {
            if (drawerSettings == null) {
                drawerSettings = theme.drawerProfile?.let { NgDrawerProfileStore.prepare(context, it) }
            }
            fun materialize(background: NgThemeBackground, preferenceKey: String): String? {
                val path = background.path?.takeIf(String::isNotBlank) ?: return null
                return if (path.startsWith(ASSET_BACKGROUND_PREFIX)) {
                    copyAssetBackgroundIfNeed(context, preferenceKey, path, forceRefresh = true)
                } else path
            }
            val lightBackground = materialize(theme.lightBackground, PreferKey.bgImage)
            val darkBackground = materialize(theme.darkBackground, PreferKey.bgImageN)
            theme.coverProfile?.takeIf { it.applyAlbumSelection }?.albumId?.let { albumId ->
                require(NgCoverAlbumStore.current(context).albums.any { it.id == albumId }) {
                    "主题关联的封面图集不存在"
                }
            }
            NgThemeModeStore.selectManagedTheme(context, renderEffect && theme.effect != null)
            context.putPrefString(PreferKey.dThemeName, theme.name)
            context.putPrefString(PreferKey.dNThemeName, theme.name)
            context.putPrefString(PreferKey.bgImage, lightBackground)
            context.putPrefString(PreferKey.bgImageN, darkBackground)
            context.putPrefInt(PreferKey.bgImageBlurring, theme.lightBackground.blur)
            context.putPrefInt(PreferKey.bgImageNBlurring, theme.darkBackground.blur)
            context.putPrefBoolean(PreferKey.tNavBar, true)
            theme.barProfile?.normalized()?.let { bars ->
                bars.useFloatingBottomBar?.let { context.putPrefBoolean(PreferKey.useFloatingBottomBar, it) }
                bars.floatingBottomBarBottomDistancePx?.let { AppConfig.floatingBottomBarBottomDistancePx = it }
                bars.floatingBottomBarTransparency?.let { AppConfig.floatingBottomBarTransparency = it }
                bars.bookshelfTopBarStyle?.let { AppConfig.bookshelfTopBarStyle = BookshelfTopBarStyle.fromValue(it) }
                bars.bookshelfFloatingDockTopDistancePx?.let { AppConfig.bookshelfFloatingDockTopDistancePx = it }
                bars.bookshelfFloatingDockTransparency?.let { AppConfig.bookshelfFloatingDockTransparency = it }
                bars.bookshelfFloatingDockSearchPosition?.let {
                    AppConfig.bookshelfFloatingDockSearchPosition = BookshelfFloatingDockSearchPosition.fromValue(it)
                }
            }
            NgColorConfigStore.update(context, theme.colors)
            theme.coverProfile?.let { cover ->
                if (cover.applyAlbumSelection) {
                    check(NgCoverAlbumStore.select(context, cover.albumId)) { "无法应用主题封面图集" }
                }
                cover.loadOnlyWifi?.let { context.putPrefBoolean(PreferKey.loadCoverOnlyWifi, it) }
                cover.useDefault?.let { context.putPrefBoolean(PreferKey.useDefaultCover, it) }
                cover.showName?.let { context.putPrefBoolean(PreferKey.coverShowName, it) }
                cover.showAuthor?.let { context.putPrefBoolean(PreferKey.coverShowAuthor, it) }
                cover.showNameDark?.let { context.putPrefBoolean(PreferKey.coverShowNameN, it) }
                cover.showAuthorDark?.let { context.putPrefBoolean(PreferKey.coverShowAuthorN, it) }
            }
            drawerSettings?.let { NgDrawerProfileStore.commit(context, it) }
            val mode = NgThemeModeStore.standardThemeMode(context)
            if (AppConfig.isEInkMode || AppConfig.themeMode != mode) applyThemeMode(context, mode)
            BookCover.upDefaultCover()
            postEvent(EventBus.RECREATE, "")
            true
        }.getOrElse { error ->
            AppLog.put("设置主题出错\n$error", error, true)
            false
        }.also { drawerSettings?.discard() }
    }
}
