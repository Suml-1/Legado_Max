package io.legado.app.ui.book.read.config.highlight

import io.legado.app.utils.RegexCache

// 数据模型
data class HighlightRule(
    var id: String = System.currentTimeMillis().toString(),
    var name: String = "",
    var pattern: String = "",
    var isRegex: Boolean = true,
    var sampleText: String = "",
    var group: String = HighlightRuleGroupStore.DEFAULT_GROUP,
    var targetScope: Int = TARGET_ALL,
    var enabled: Boolean = true,
    var textColor: Int? = null,
    var underlineMode: Int = 0,
    var underlineColor: Int? = null,
    var underlineWidth: Float = 1f,
    var underlineOffset: Float = 2f,
    var underlineSvgPath: String? = null,
    /** 高亮字体路径（FileDoc 字符串），为空时跟随阅读字体 */
    var font: String? = null,
    var bgColor: Int? = null,
    var bgImage: String? = null,
    var bgImageFit: Int = 0,
    var bgImageScale: Float = 1f,
    /** 九宫格分割比例（0-1，占图片宽/高的百分比；左右相加、上下相加不超过 1），适配方式为九宫格时生效 */
    var npLeft: Float = 0.1f,
    var npTop: Float = 0.1f,
    var npRight: Float = 0.1f,
    var npBottom: Float = 0.1f,
    /**
     * 九宫格外扩策略，取值 [BLEED_STRICT] / [BLEED_SMART] / [BLEED_FORCE]。
     * 为空表示 [BLEED_SMART]（智能）：可空是为了区分"用户显式选了严格"与"老规则没有这个字段"。
     */
    var bgBleedMode: Int? = null,
    /**
     * 旧版"左右间距"（em）：间距已按四边拆分，本字段只为兼容老规则保留，
     * 由 [HighlightRuleStore.sanitizeRule] 迁移到 [bgSpacingLeft]/[bgSpacingRight] 后清零。
     */
    var bgSpacingH: Float = 0f,

    /**
     * 旧版"上下间距"（em）：同上，迁移到 [bgSpacingTop]/[bgSpacingBottom] 后清零。
     */
    var bgSpacingV: Float = 0f,
    /** 背景图左间距（em，随字号缩放）：正数把背景向外撑大、离文字更远，负数向内收 */
    var bgSpacingLeft: Float = 0f,
    /** 背景图右间距（em）：正数向外撑大，负数向内收 */
    var bgSpacingRight: Float = 0f,
    /** 背景图上间距（em）：正数向外撑大，负数向内收 */
    var bgSpacingTop: Float = 0f,
    /** 背景图下间距（em）：正数向外撑大，负数向内收 */
    var bgSpacingBottom: Float = 0f,
    /** 命中字距（px）：命中段左侧邻字之间额外留出的空白，0 表示不留白 */
    var letterSpacingBefore: Float = 0f,
    /** 命中字距（px）：命中段右侧邻字之间额外留出的空白，0 表示不留白 */
    var letterSpacingAfter: Float = 0f,
    /** 作用范围，书名或书源URL，分号分隔，为空则对所有书籍生效 */
    var scope: String? = null,
    /** 排除范围，书名或书源URL，分号分隔，匹配的书籍不应用该规则 */
    var excludeScope: String? = null,
    /** 作用的阅读排版名称，分号分隔，为空则对所有排版生效 */
    var layoutScope: String? = null,
    /** 主题作用范围：位标志，1=亮色，2=暗色，3=全部（默认） */
    var themeScope: Int = THEME_ALL,
) {

    fun styleSummary(): String {
        val parts = ArrayList<String>(4)
        parts.add(targetScopeLabel())
        if (!scope.isNullOrBlank()) {
            parts.add("仅: ${scope!!.replace(";", "; ").trim()}")
        }
        if (!excludeScope.isNullOrBlank()) {
            parts.add("排除: ${excludeScope!!.replace(";", "; ").trim()}")
        }
        if (!layoutScope.isNullOrBlank()) {
            parts.add("排版: ${layoutScope!!.replace(";", "; ").trim()}")
        }
        if (themeScope != THEME_ALL) {
            parts.add(themeScopeLabel())
        }
        textColor?.let {
            parts.add("字色 ${it.toHexColor()}")
        }
        if (underlineMode != 0) {
            parts.add(
                when (underlineMode) {
                    1 -> "实线下划线"
                    2 -> "虚线下划线"
                    3 -> "波浪下划线"
                    4 -> "双下划线"
                    5 -> "自定义SVG"
                    6 -> "删除线"
                    7 -> "斜体"
                    8 -> "方框"
                    else -> "下划线"
                } + underlineColor?.let { " ${it.toHexColor()}" }.orEmpty(),
            )
        }
        if (!font.isNullOrBlank()) {
            parts.add("字体 ${fontDisplayName()}")
        }
        if (!bgImage.isNullOrBlank()) {
            parts.add(
                when (bgImageFit) {
                    1 -> "背景图(拉伸)"
                    2 -> "背景图(裁剪)"
                    3 -> "背景图(九宫格)"
                    else -> "背景图(平铺)"
                },
            )
        } else if (bgColor != null) {
            parts.add("背景色 ${bgColor!!.toHexColor()}")
        }
        if (letterSpacingBefore > 0f || letterSpacingAfter > 0f) {
            parts.add("命中字距 ${letterSpacingBefore.formatDistance()} / ${letterSpacingAfter.formatDistance()}px")
        }
        if (parts.isEmpty()) {
            parts.add("无样式")
        }
        return parts.joinToString(" / ")
    }

    /**
     * 从字体路径中提取展示用的文件名。
     * content:// 形式的 FileDoc 路径经过 URL 编码（如 %20），展示前需解码。
     */
    fun fontDisplayName(): String {
        val fontPath = font ?: return ""
        if (fontPath.isBlank()) return ""
        val decoded = runCatching {
            java.net.URLDecoder.decode(fontPath, "utf-8")
        }.getOrNull() ?: fontPath
        return decoded.substringAfterLast('/').substringAfterLast('\\').ifBlank { fontPath }
    }

    fun targetScopeLabel(): String = when (targetScope) {
        TARGET_TITLE -> "作用于标题"
        TARGET_BODY -> "作用于正文"
        else -> "作用于全部"
    }

    fun themeScopeLabel(): String = when (themeScope) {
        THEME_LIGHT -> "仅亮色"
        THEME_DARK -> "仅暗色"
        else -> "亮暗色"
    }

    fun displayPattern(): String = pattern.ifBlank { ".*" }

    // 转换为正则表达式（使用全局缓存，避免重复编译）
    fun toRegex(): Regex = if (isRegex) {
        RegexCache.getOrCompile(pattern)
    } else {
        // 非正则模式：转义后作为字面量匹配，缓存键加前缀避免与正则模式冲突
        RegexCache.getOrCompile("LITERAL:" + pattern) { Regex(Regex.escape(pattern)) }
    }

    // 格式化样本文本，确保在显示时正确换行
    fun normalizedSampleText(): String = sampleText.ifBlank {
        "她轻声说：\"今晚就出发。\"\n他说：“明天见。”\n最近在重读《百年孤独》（纪念版），节奏依然很稳。"
    }

    fun copyWithNewId(): HighlightRule = copy(id = "${System.currentTimeMillis()}_${name.hashCode()}")

    /**
     * 判断规则是否对指定书籍生效
     * - scope 为空时，默认对所有书籍生效（仍会检查 excludeScope）
     * - scope 非空时，仅对匹配书名或书源URL的书籍生效
     * - excludeScope 非空时，匹配的书籍会被排除
     */
    fun matchesScope(bookName: String, bookOrigin: String): Boolean {
        // 检查作用范围：scope 非空时，书名或书源URL必须匹配其中一项
        val scopeVal = scope
        if (!scopeVal.isNullOrBlank()) {
            val scopeItems = scopeVal.split(";").map { it.trim() }.filter { it.isNotBlank() }
            val inScope = scopeItems.any { item ->
                bookName.contains(item) || bookOrigin.contains(item)
            }
            if (!inScope) return false
        }
        // 检查排除范围：书名或书源URL匹配排除项则不生效
        val excludeVal = excludeScope
        if (!excludeVal.isNullOrBlank()) {
            val excludeItems = excludeVal.split(";").map { it.trim() }.filter { it.isNotBlank() }
            val excluded = excludeItems.any { item ->
                bookName.contains(item) || bookOrigin.contains(item)
            }
            if (excluded) return false
        }
        return true
    }

    /**
     * 判断规则是否对指定排版生效
     * - layoutScope 为空时，默认对所有排版生效
     * - layoutScope 非空时，仅对匹配名称的排版生效
     */
    fun matchesLayout(layoutName: String): Boolean {
        val layoutVal = layoutScope ?: return true
        if (layoutVal.isBlank()) return true
        val layoutItems = layoutVal.split(";").map { it.trim() }.filter { it.isNotBlank() }
        return layoutItems.any { item -> layoutName == item }
    }

    /**
     * 判断规则是否对当前主题生效
     * - themeScope 包含对应主题位标志时生效
     */
    fun matchesTheme(isNightTheme: Boolean): Boolean {
        val flag = if (isNightTheme) THEME_DARK else THEME_LIGHT
        return (themeScope and flag) != 0
    }

    companion object {
        const val TARGET_ALL = 0
        const val TARGET_TITLE = 1
        const val TARGET_BODY = 2

        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        const val THEME_ALL = 3

        /** 严格：不自动外扩，背景只覆盖匹配到的文字 */
        const val BLEED_STRICT = 0

        /** 智能（默认）：只占用邻接的空白——水平借邻接空白字符的宽度，垂直吃掉一半行距 */
        const val BLEED_SMART = 1

        /** 强制：按四角厚度向外扩展，即原来的"向外包裹文字"行为，可能压到相邻文字 */
        const val BLEED_FORCE = 2

        fun resolvedBleedMode(mode: Int?): Int = when (mode) {
            BLEED_STRICT, BLEED_SMART, BLEED_FORCE -> mode
            else -> BLEED_SMART
        }

        fun Int.toHexColor(): String = String.format("#%08X", this)
    }
}

/** 规则摘要里的 px 数值展示：整数不带小数，其余保留两位 */
private fun Float.formatDistance(): String {
    return if (this % 1f == 0f) toInt().toString() else String.format(java.util.Locale.US, "%.2f", this)
}
