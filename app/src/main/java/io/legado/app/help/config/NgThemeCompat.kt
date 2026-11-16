package io.legado.app.help.config

/**
 * NG 主题移植兼容层：主题日夜模式解析与内置背景资源路径映射。
 * 从 legado_NG 的 AppConfig.kt / ThemeConfig.kt 抽出，供 NG 主题组件引用。
 */

/**
 * 按主题模式字符串解析是否为夜间。
 * "0" 跟随系统；"1" 日间；"2" 夜间；"3" 墨水屏（非夜间渲染）。
 */
internal fun resolveThemeNightMode(themeMode: String, systemNightMode: Boolean): Boolean = when (themeMode) {
    "2" -> true
    "1", "3" -> false
    else -> systemNightMode
}

/**
 * 主题模式取值归一化："0"跟随/"1"日间/"2"夜间/"3"墨水；旧值 4/5/6 归到日间。
 */
internal fun normalizeThemeMode(value: String?): String {
    return when (value) {
        "0", "1", "2", "3" -> value
        "4", "5", "6" -> "1"
        else -> "0"
    }
}

/**
 * 内置主题背景资源路径归一化：旧 png 路径映射到打包的 webp 资源。
 */
internal fun resolveBundledBackgroundAssetPath(assetPath: String): String = when (assetPath) {
    "defaultData/theme/reading_ng_warm.png",
    "bg/暖色渐变.png" -> "bg/暖色渐变.webp"
    "defaultData/theme/reading_ng_bamboo.png",
    "bg/竹影之韵.png" -> "bg/竹影之韵.webp"
    "defaultData/theme/reading_ng_mist.png",
    "bg/灰色雾霭.png" -> "bg/灰色雾霭.webp"
    else -> assetPath
}
