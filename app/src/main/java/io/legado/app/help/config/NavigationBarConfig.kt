package io.legado.app.help.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.util.LruCache
import android.view.Menu
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toDrawable
import com.google.gson.JsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.lib.theme.EInkRender
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.SvgUtils
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getFile
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import splitties.init.appCtx
import java.io.File

/**
 * 底栏导航栏配置数据类。
 *
 * 定义了底部导航栏的外观和行为属性，包括布局模式、视觉效果、
 * 背景色/透明度、边框样式以及自定义图标。每个配置项可以是
 * 内置（默认日间/夜间）或用户自定义的。
 *
 * @property id 唯一标识符，内置配置以 "builtin_" 前缀开头
 * @property name 配置名称，用于 UI 展示
 * @property isNight 是否为夜间模式配置
 * @property isBuiltin 是否为内置（不可删除）配置
 * @property layoutMode 布局模式：[LAYOUT_FLOATING] / [LAYOUT_STANDARD] / [LAYOUT_SIDEBAR]
 * @property effectMode 视觉效果：[EFFECT_SOLID] / [EFFECT_GLASS] / [EFFECT_FROSTED]
 * @property backgroundColor 自定义背景色，null 表示跟随主题
 * @property opacity 背景透明度（0-100）
 * @property borderColor 边框颜色
 * @property borderAlpha 边框透明度（0-100）
 * @property icons 自定义图标映射，key 格式为 "{itemKey}_{state}"
 * @property updatedAt 最后更新时间戳
 */
@Keep
data class NavigationBarConfig(
    var id: String,
    var name: String,
    var isNight: Boolean,
    var isBuiltin: Boolean = false,
    var layoutMode: String = LAYOUT_FLOATING,
    var effectMode: String = EFFECT_GLASS,
    var backgroundColor: Int? = null,
    var opacity: Int = 76,
    var borderColor: Int? = null,
    var borderAlpha: Int = 100,
    var icons: Map<String, String> = emptyMap(),
    var updatedAt: Long = System.currentTimeMillis(),
) {

    /** 底栏导航项元数据：key 用于图标存储，menuId 对应菜单资源 ID */
    data class NavItem(
        val key: String,
        @StringRes val titleRes: Int,
        @IdRes val menuId: Int,
        @DrawableRes val defaultIconRes: Int,
    )

    fun toJson(): String = GSON.toJson(this)

    fun copySelf(): NavigationBarConfig = copy(icons = icons.toMap())

    /** 获取布局模式的本地化显示文本 */
    fun getLayoutModeText(context: Context = appCtx): String = when (layoutMode) {
        LAYOUT_STANDARD -> context.getString(R.string.nav_bar_layout_standard)
        LAYOUT_SIDEBAR -> context.getString(R.string.nav_bar_layout_sidebar)
        else -> context.getString(R.string.nav_bar_layout_floating)
    }

    /** 获取效果模式的本地化显示文本 */
    fun getEffectModeText(context: Context = appCtx): String = when (effectMode) {
        EFFECT_SOLID -> context.getString(R.string.nav_bar_effect_solid)
        EFFECT_FROSTED -> context.getString(R.string.nav_bar_effect_frosted)
        else -> context.getString(R.string.nav_bar_effect_glass)
    }

    companion object {
        /** 布局模式：浮动底栏 */
        const val LAYOUT_FLOATING = "floating"

        /** 布局模式：标准底栏 */
        const val LAYOUT_STANDARD = "standard"

        /** 布局模式：侧边栏 */
        const val LAYOUT_SIDEBAR = "sidebar"

        /** 效果模式：纯色 */
        const val EFFECT_SOLID = "solid"

        /** 效果模式：玻璃质感 */
        const val EFFECT_GLASS = "glass"

        /** 效果模式：磨砂 */
        const val EFFECT_FROSTED = "frosted"

        /** 图标状态：常规 */
        const val STATE_NORMAL = "normal"

        /** 图标状态：选中 */
        const val STATE_SELECTED = "selected"

        private const val PREF_KEY_ACTIVE_DAY = "activeDayNavBarId"
        private const val PREF_KEY_ACTIVE_NIGHT = "activeNightNavBarId"
        private const val PREF_KEY_CUSTOM_CONFIGS = "customNavBarConfigs"

        /** 图标缓存容量上限：按解码后的位图字节数计，避免用户直接选相册大图把缓存撑爆 */
        private const val ICON_CACHE_MAX_BYTES = 12 * 1024 * 1024

        /**
         * 图标解码结果缓存（SVG 位图与位图图标都进这里）。
         *
         * 存的是 [Drawable.createFromPath] **原样返回的 Drawable 实例**，而不是"等价重建"的产物：
         * 底栏图标在首次进入主界面时会被连续应用两次（`upBottomMenu` 一次、`initView` 再确认一次），
         * 每次都要重新解码 10 张图（设备实测单次约 0.5s 主线程阻塞）。
         * 换成 `BitmapFactory` + `BitmapDrawable` 的等价重建会改变渲染（实测底栏区 884 个像素不同），
         * 原样复用则逐像素一致。按字节数计容量：图标过大（用户直接选相册大图）时不入缓存，
         * 行为与改动前一致，也不会把缓存撑爆。
         */
        private val iconDrawableCache = object : LruCache<String, Drawable>(ICON_CACHE_MAX_BYTES) {
            override fun sizeOf(key: String, value: Drawable): Int = when (value) {
                is BitmapDrawable -> value.bitmap?.byteCount ?: 0
                else -> 1024
            }
        }

        /** 图标位图缓存（按字节计容量），与 [iconDrawableCache] 共用同一份解码结果 */
        private val iconBitmapCache = object : LruCache<String, Bitmap>(ICON_CACHE_MAX_BYTES) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }

        /**
         * 位图包成 Drawable 并缓存。
         *
         * 包一层本身很便宜，但启动期底栏图标会被应用两次以上，复用同一实例可以少一次分配；
         * 统一用 appCtx 的资源构造，避免静态缓存间接持有 Activity。
         */
        private fun drawableOf(cacheKey: String, bitmap: Bitmap): Drawable = synchronized(iconDrawableCache) {
            iconDrawableCache[cacheKey]
        } ?: bitmap.toDrawable(appCtx.resources).also {
            synchronized(iconDrawableCache) { iconDrawableCache.put(cacheKey, it) }
        }

        /** 清空图标缓存（配置变更时调用） */
        fun clearIconCache() {
            iconDrawableCache.evictAll()
            iconBitmapCache.evictAll()
        }

        /**
         * 预解码当前激活配置的自定义图标（供 App 启动时后台调用）。
         *
         * 底栏图标首次应用时必须同步解码（实测 10 张合计约 0.6s 主线程阻塞），
         * 启动时先在后台解好，主线程应用底栏时直接命中缓存。
         * 多张图并行解码：源图往往是用户相册里的大图，单张 PNG 解压就要上百毫秒，
         * 串行解完窗口太长容易赶不上。未配置自定义图标时直接返回。
         */
        suspend fun preloadActiveIcons(context: Context) {
            val config = runCatching { activeConfig(context, AppConfig.isNightTheme) }.getOrNull()
                ?: return
            if (config.icons.isEmpty()) return
            coroutineScope {
                config.icons.values.forEach { path ->
                    launch(Dispatchers.IO) { runCatching { loadIconDrawable(context, path) } }
                }
            }
        }

        /** 生成图标缓存 key：基于文件路径、最后修改时间和大小 */
        private fun iconCacheKey(path: String): String {
            val file = java.io.File(path)
            return "${file.absolutePath}|${file.lastModified()}|${file.length()}"
        }

        /** 底栏导航项列表：首页、书架、发现、RSS、我的 */
        val items = listOf(
            NavItem("homepage", R.string.homepage, R.id.menu_homepage, R.drawable.ic_bottom_home),
            NavItem("bookshelf", R.string.bookshelf, R.id.menu_bookshelf, R.drawable.ic_bottom_books),
            NavItem("discovery", R.string.discovery, R.id.menu_discovery, R.drawable.ic_bottom_explore),
            NavItem("rss", R.string.rss, R.id.menu_rss, R.drawable.ic_bottom_rss_feed),
            NavItem("my", R.string.my, R.id.menu_my_config, R.drawable.ic_bottom_person),
        )

        fun fromJson(json: String): NavigationBarConfig = GSON.fromJsonObject<NavigationBarConfig>(json).getOrThrow()

        fun createDefaultDay(): NavigationBarConfig = NavigationBarConfig(
            id = "builtin_default_day",
            name = appCtx.getString(R.string.nav_bar_default_day_name),
            isNight = false,
            isBuiltin = true,
            layoutMode = LAYOUT_STANDARD,
            effectMode = EFFECT_SOLID,
            opacity = 30,
            updatedAt = 0L,
        )

        fun createDefaultNight(): NavigationBarConfig = NavigationBarConfig(
            id = "builtin_default_night",
            name = appCtx.getString(R.string.nav_bar_default_night_name),
            isNight = true,
            isBuiltin = true,
            layoutMode = LAYOUT_STANDARD,
            effectMode = EFFECT_SOLID,
            opacity = 30,
            updatedAt = 0L,
        )

        /**
         * [loadConfigs] 的解析结果缓存。
         *
         * 主界面每次 onResume、底栏配置签名比对（[currentSignature]）、菜单图标应用
         * （[applyToMenu]）都会走到 [activeConfig] → 这里，而它要 `getPrefString` +
         * 逐个 `GSON.fromJson` 解析用户的每份自定义底栏——进出主界面/切页时这份解析完全是重复劳动。
         * 缓存键直接用原始 JSON 字符串（比字符串比解析便宜得多），任何写入
         * （[saveConfigs]、旧格式迁移、主题导入）都会让键失效。
         */
        @Volatile
        private var cachedConfigs: Pair<String, List<NavigationBarConfig>>? = null

        /**
         * 加载所有底栏配置：内置配置（日间/夜间）+ 用户自定义配置。
         * 支持从旧版 JSON 对象格式自动迁移到数组格式。
         */
        fun loadConfigs(context: Context): MutableList<NavigationBarConfig> {
            val stored = context.getPrefString(PREF_KEY_CUSTOM_CONFIGS).orEmpty()
            cachedConfigs?.takeIf { it.first == stored }?.let { cached ->
                // 返回副本：调用方（配置管理页、主题导入）会直接改这些实例
                return cached.second.mapTo(mutableListOf()) { it.copySelf() }
            }
            val configs = mutableListOf(createDefaultDay(), createDefaultNight())
            var shouldMigrate = false
            when {
                stored.isNullOrBlank() -> Unit
                stored.trimStart().startsWith("[") -> {
                    configs.addAll(parseConfigArray(stored))
                }
                else -> {
                    parseLegacyConfigObjects(stored)
                        .mapNotNull { json -> runCatching { fromJson(json) }.getOrNull() }
                        .also {
                            configs.addAll(it)
                            shouldMigrate = it.isNotEmpty()
                        }
                }
            }
            if (shouldMigrate) {
                saveConfigs(context, configs)
            }
            // 只缓存"未被迁移改写"的形态：迁移会重写偏好，下一次调用键不同、自然重新解析
            if (!shouldMigrate) {
                cachedConfigs = stored to configs.map { it.copySelf() }
            }
            return configs
        }

        /** 保存用户自定义配置到 SharedPreferences（内置配置不入库） */
        fun saveConfigs(context: Context, configs: List<NavigationBarConfig>) {
            context.defaultSharedPreferences.edit(commit = true) {
                putString(
                    PREF_KEY_CUSTOM_CONFIGS,
                    GSON.toJson(configs.filter { !it.isBuiltin }),
                )
            }
            // 写盘后键本就变了，这里再显式清一次，兜住失败/时序异常
            cachedConfigs = null
        }

        private fun parseConfigArray(stored: String): List<NavigationBarConfig> {
            val array = runCatching {
                GSON.fromJson(stored, JsonArray::class.java)
            }.getOrNull() ?: return emptyList()
            return array.mapNotNull { element ->
                runCatching {
                    GSON.fromJson(element, NavigationBarConfig::class.java)
                }.getOrNull()
            }
        }

        /**
         * 解析旧版 JSON 对象格式（多个独立的 JSON 对象拼接成的字符串）。
         * 通过手动字符扫描提取每个 `{}` 块，兼容历史数据。
         */
        private fun parseLegacyConfigObjects(stored: String): List<String> {
            val result = mutableListOf<String>()
            var depth = 0
            var start = -1
            var inString = false
            var escaped = false
            stored.forEachIndexed { index, char ->
                if (escaped) {
                    escaped = false
                    return@forEachIndexed
                }
                when {
                    char == '\\' && inString -> escaped = true
                    char == '"' -> inString = !inString
                    !inString && char == '{' -> {
                        if (depth == 0) start = index
                        depth++
                    }
                    !inString && char == '}' -> {
                        depth--
                        if (depth == 0 && start >= 0) {
                            result.add(stored.substring(start, index + 1))
                            start = -1
                        }
                    }
                }
            }
            return result
        }

        /** 获取当前激活的底栏配置 ID */
        fun activeId(context: Context, isNight: Boolean): String? = context.getPrefString(if (isNight) PREF_KEY_ACTIVE_NIGHT else PREF_KEY_ACTIVE_DAY)

        /** 设置当前激活的底栏配置 ID */
        fun setActiveId(context: Context, isNight: Boolean, id: String?) {
            context.defaultSharedPreferences.edit(commit = true) {
                putString(if (isNight) PREF_KEY_ACTIVE_NIGHT else PREF_KEY_ACTIVE_DAY, id.orEmpty())
            }
        }

        /** 获取当前激活的底栏配置，若未设置则返回该模式下的第一个配置 */
        fun activeConfig(context: Context, isNight: Boolean): NavigationBarConfig {
            val configs = loadConfigs(context)
            val activeId = activeId(context, isNight)
            return configs.firstOrNull { it.isNight == isNight && it.id == activeId }
                ?: configs.first { it.isNight == isNight }
        }

        /** 生成当前配置的签名摘要，用于缓存失效判断 */
        fun currentSignature(context: Context, isNight: Boolean): String {
            val config = activeConfig(context, isNight)
            val iconSignature = config.icons.entries
                .sortedBy { it.key }
                .joinToString("|") { "${it.key}:${it.value}" }
            // 内置配置使用固定 updatedAt（0L），避免每次 loadConfigs 重新创建实例
            // 导致时间戳变化、签名不一致，进而触发重复重建主题/图标/背景。
            val stableUpdatedAt = if (config.isBuiltin) 0L else config.updatedAt
            return listOf(
                isNight,
                config.id,
                config.layoutMode,
                config.effectMode,
                config.backgroundColor,
                config.opacity,
                config.borderColor,
                config.borderAlpha,
                stableUpdatedAt,
                iconSignature,
            ).joinToString("|")
        }

        /** 应用指定配置：设置激活 ID、应用主题、发送事件总线通知 */
        fun applyConfig(context: Context, config: NavigationBarConfig, recreate: Boolean = false) {
            setActiveId(context, config.isNight, config.id)
            ThemeConfig.applyTheme(context)
            postEvent(EventBus.NAVIGATION_BAR_CHANGED, config.isNight)
            if (recreate) postEvent(EventBus.RECREATE, "")
        }

        /** 根据配置的透明度计算最终底栏背景色（内置与自定义配置统一处理） */
        fun resolveBottomColor(baseColor: Int, config: NavigationBarConfig): Int {
            val alpha = config.opacity.coerceIn(0, 100) / 100f
            return ColorUtils.withAlpha(config.backgroundColor ?: baseColor, alpha)
        }

        /**
         * 将自定义图标应用到菜单项。
         * @return true 表示至少有一项使用了自定义图标
         */
        fun applyToMenu(menu: Menu, context: Context, isNight: Boolean, bgColor: Int? = null): Boolean {
            val config = activeConfig(context, isNight)
            var hasCustom = false
            items.forEach { item ->
                val normal = loadIconDrawable(context, config.icons[iconKey(item.key, STATE_NORMAL)])
                val selected = loadIconDrawable(context, config.icons[iconKey(item.key, STATE_SELECTED)])
                if (normal != null || selected != null) hasCustom = true
                menu.findItem(item.menuId)?.icon = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_checked), (selected ?: normal ?: defaultDrawable(context, item.defaultIconRes, true, bgColor)).withEInkGrayscale())
                    addState(intArrayOf(android.R.attr.state_selected), (selected ?: normal ?: defaultDrawable(context, item.defaultIconRes, true, bgColor)).withEInkGrayscale())
                    addState(intArrayOf(), (normal ?: defaultDrawable(context, item.defaultIconRes, false, bgColor)).withEInkGrayscale())
                }
            }
            return hasCustom
        }

        /** 获取指定导航项的预览 Drawable（优先自定义图标，回退到默认） */
        fun previewDrawable(context: Context, config: NavigationBarConfig, item: NavItem, selected: Boolean, bgColor: Int? = null): Drawable? {
            val state = if (selected) STATE_SELECTED else STATE_NORMAL
            val icon = loadIconDrawable(context, config.icons[iconKey(item.key, state)])
                ?: loadIconDrawable(context, config.icons[iconKey(item.key, STATE_NORMAL)])
                ?: defaultDrawable(context, item.defaultIconRes, selected, bgColor)
            // 预览与底栏实际效果保持一致（墨水屏渲染下同样是灰阶）
            return icon.withEInkGrayscale()
        }

        /**
         * 墨水屏渲染：让自定义图标也走灰阶。
         *
         * 包一层共享同一张底图的 [BitmapDrawable]，而不是直接改传入实例的 `colorFilter`——
         * 自定义图标来自 [iconDrawableCache]，改它会污染缓存（关掉开关后图标仍是灰的）。
         * 包一层不重新解码，代价只有一次对象分配；非位图（内置图标由主题色染色，本身已无彩）
         * 原样返回。
         */
        private fun Drawable.withEInkGrayscale(): Drawable {
            val filter = EInkRender.androidFilterOrNull() ?: return this
            val bitmap = (this as? BitmapDrawable)?.bitmap ?: return this
            return BitmapDrawable(appCtx.resources, bitmap).apply { colorFilter = filter }
        }

        /** 生成图标存储 key："{itemKey}_{state}" */
        fun iconKey(itemKey: String, state: String): String = "${itemKey}_$state"

        /**
         * 读取自定义图标，结果进 [iconDrawableCache]（键含文件修改时间与大小，换图即失效）。
         *
         * SVG 用 [SvgUtils] 解析（[Drawable.createFromPath] 不支持 SVG）；
         * 位图图标用 [Drawable.createFromPath] 原样解码后缓存。
         *
         * 图标路径是配置文件里的绝对路径，备份恢复（尤其跨包名/跨设备）后可能指向
         * 其它应用的外部目录或已删掉的文件，读取会抛 EACCES/ENOENT。这里整体兜住：
         * 读不到就当没有自定义图标，由调用方回退到内置图标——不能让异常冒到
         * Activity resume（曾经因为一张 svg 直接崩主界面）。
         */
        private fun loadIconDrawable(context: Context, path: String?): Drawable? {
            if (path.isNullOrBlank()) return null
            return runCatching { decodeIconDrawable(context, path) }.getOrNull()
        }

        private fun decodeIconDrawable(context: Context, path: String): Drawable? {
            val cacheKey = iconCacheKey(path)
            synchronized(iconDrawableCache) {
                iconDrawableCache[cacheKey]?.let { return it }
            }
            val file = java.io.File(path)
            if (file.extension.equals("svg", ignoreCase = true)) {
                val targetSize = (context.resources.displayMetrics.density * 48).toInt()
                return SvgUtils.createBitmapFromFile(path, targetSize, targetSize)
                    ?.let { drawableOf(cacheKey, it) }
            }
            val bitmap = synchronized(iconBitmapCache) {
                iconBitmapCache[cacheKey]?.takeIf { !it.isRecycled }
                    ?: decodeIconBitmap(path, iconDecodeTarget(context)).also {
                        if (it != null) iconBitmapCache.put(cacheKey, it)
                    }
            }
            return bitmap?.let { drawableOf(cacheKey, it) }
        }

        /**
         * 修正自定义图标的保存路径（备份恢复后调用）。
         *
         * 图标在配置里存的是绝对路径，跨包名恢复后它仍指向原包名的
         * `Android/data/<旧包名>/files/navigationBarIcons/...`，当前进程读不了（EACCES）。
         * 这里按"当前应用图标目录下有没有同名文件"重新定位：
         * 能找到就改成当前路径，找不到就丢掉这条图标（底栏回退内置图标），
         * 避免留下一条永远读不了的坏路径。
         *
         * @return 是否有路径被改写
         */
        fun repairIconPaths(context: Context): Boolean {
            val configs = loadConfigs(context)
            var changed = false
            configs.forEach { config ->
                if (config.icons.isEmpty()) return@forEach
                val iconDir = context.externalFiles.getFile("navigationBarIcons", config.id)
                val repaired = config.icons.mapNotNull { (key, path) ->
                    val localFile = File(iconDir, File(path).name)
                    when {
                        localFile.canRead() -> key to localFile.absolutePath
                        File(path).canRead() -> key to path
                        else -> null
                    }
                }.toMap()
                if (repaired != config.icons) {
                    config.icons = repaired
                    changed = true
                }
            }
            if (changed) {
                saveConfigs(context, configs)
                clearIconCache()
            }
            return changed
        }

        /**
         * 图标解码目标尺寸（px）：底栏图标按 22-23dp 绘制，取 96dp 留 4 倍余量。
         *
         * 用户常直接从相册选图，动辄一两千像素、十几 MB。底栏只显示几十像素，
         * 整张解码既慢（实测单张 80-140ms，10 张近 1 秒、首屏还会做两遍）
         * 又会因为单张超出缓存上限而根本留不住；先按目标尺寸降采样再交给 ImageView，
         * 时间与内存都成倍下降。
         */
        private fun iconDecodeTarget(context: Context): Int = (context.resources.displayMetrics.density * 96).toInt().coerceAtLeast(1)

        /** 按目标尺寸降采样解码图标位图 */
        private fun decodeIconBitmap(path: String, targetSize: Int): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= targetSize &&
                bounds.outHeight / (sampleSize * 2) >= targetSize
            ) {
                sampleSize *= 2
            }
            BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            )
        }.getOrNull()

        private fun defaultDrawable(context: Context, @DrawableRes resId: Int, selected: Boolean, bgColor: Int? = null): Drawable {
            val drawable = ContextCompat.getDrawable(context, resId)!!.mutate()
            val bg = bgColor ?: ThemeStore.bottomBackground(context)
            val textIsDark = ColorUtils.isColorLight(bg)
            val color = if (selected) ThemeStore.accentColor(context) else context.getSecondaryTextColor(textIsDark)
            DrawableCompat.setTint(drawable, color)
            return drawable
        }
    }
}
