package io.legado.app.ui.book.read.config.highlight

/**
 * 高亮规则的统一样式快照。样式模型
 *
 * 从 `HighlightRule` 中抽取预览和阅读渲染共同需要的样式字段，
 * 避免配置页预览与阅读页实际绘制各自解析一套样式。
 */
data class HighlightRuleStyle(
    val textColor: Int?,
    val underlineMode: Int,
    val underlineColor: Int?,
    val underlineWidth: Float,
    val underlineOffset: Float,
    val underlineSvgPath: String,
    val bgColor: Int?,
    val bgImage: String,
    val bgImageFit: Int,
    val bgImageScale: Float,
    /** 九宫格分割比例，适配方式为九宫格(bgImageFit=3)时生效 */
    val npLeft: Float = 0.1f,
    val npTop: Float = 0.1f,
    val npRight: Float = 0.1f,
    val npBottom: Float = 0.1f,
    /** 九宫格外扩策略，取值 HighlightRule.BLEED_*，已在 [from] 中解析为确定值 */
    val bgBleedMode: Int = HighlightRule.BLEED_SMART,
    /** 背景图左间距（em），正数向外撑大、负数向内收 */
    val bgSpacingLeft: Float = 0f,
    /** 背景图右间距（em），正数向外撑大、负数向内收 */
    val bgSpacingRight: Float = 0f,
    /** 背景图上间距（em），正数向外撑大、负数向内收 */
    val bgSpacingTop: Float = 0f,
    /** 背景图下间距（em），正数向外撑大、负数向内收 */
    val bgSpacingBottom: Float = 0f,
    /** 高亮字体路径，空串表示跟随阅读字体 */
    val font: String = "",
) {

    val resolvedTextColor: Int
        get() = textColor ?: 0xFF111111.toInt()

    val resolvedAccentColor: Int
        get() = underlineColor ?: textColor ?: 0xFF63C37D.toInt()

    val hasDecoration: Boolean
        get() = underlineMode != 0 || bgImage.isNotBlank() || bgColor != null

    companion object {
        fun from(rule: HighlightRule): HighlightRuleStyle = HighlightRuleStyle(
            textColor = rule.textColor,
            underlineMode = rule.underlineMode,
            underlineColor = rule.underlineColor,
            underlineWidth = rule.underlineWidth,
            underlineOffset = rule.underlineOffset,
            underlineSvgPath = rule.underlineSvgPath.orEmpty(),
            bgColor = rule.bgColor,
            bgImage = rule.bgImage.orEmpty(),
            bgImageFit = rule.bgImageFit,
            bgImageScale = rule.bgImageScale,
            npLeft = rule.npLeft,
            npTop = rule.npTop,
            npRight = rule.npRight,
            npBottom = rule.npBottom,
            bgBleedMode = HighlightRule.resolvedBleedMode(rule.bgBleedMode),
            bgSpacingLeft = rule.bgSpacingLeft,
            bgSpacingRight = rule.bgSpacingRight,
            bgSpacingTop = rule.bgSpacingTop,
            bgSpacingBottom = rule.bgSpacingBottom,
            font = rule.font.orEmpty(),
        )
    }
}
