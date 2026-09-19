package io.legado.app.ui.book.read.page.provider

/**
 * 每个字符的高亮样式，由高亮规则整章匹配后填充到字符样式数组。
 *
 * 参考 MD3-main 的实现：排版期用数组下标直接取样式，
 * 取代旧的 SpannableStringBuilder + 逐字符 getSpans 方案，
 * 避免规则较多时大量 Span 分配与查询拖慢章节打开速度。
 */
data class CharStyle(
    val textColor: Int? = null,
    val underlineMode: Int = 0,
    val underlineColor: Int = 0xFF63C37D.toInt(),
    val underlineWidth: Float = 1f,
    val underlineOffset: Float = 2f,
    val underlineSvgPath: String = "",
    val bgColor: Int? = null,
    val bgImage: String = "",
    val bgImageFit: Int = 0,
    val bgImageScale: Float = 1f,
    /** 九宫格分割比例，适配方式为九宫格(bgImageFit=3)时生效 */
    val npLeft: Float = 0.1f,
    val npTop: Float = 0.1f,
    val npRight: Float = 0.1f,
    val npBottom: Float = 0.1f,
    /** 九宫格外扩策略，取值 HighlightRule.BLEED_* */
    val bgBleedMode: Int = 1,
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

    /**
     * 字段级合并重叠规则的样式，与旧 Span 实现中 extractHighlightStyle
     * 对多个重叠 Span 各取所需的行为保持一致：
     * 下划线字段取最后一条带下划线的规则，背景取最后一条带背景的规则，
     * 字色取最后一条指定了字色的规则。
     */
    fun mergedWith(later: CharStyle): CharStyle {
        if (later.underlineMode != 0 &&
            later.bgImage.isNotEmpty() &&
            later.bgColor != null &&
            later.textColor != null
        ) {
            return later
        }
        return CharStyle(
            textColor = later.textColor ?: textColor,
            underlineMode = if (later.underlineMode != 0) later.underlineMode else underlineMode,
            underlineColor = if (later.underlineMode != 0) later.underlineColor else underlineColor,
            underlineWidth = if (later.underlineMode != 0) later.underlineWidth else underlineWidth,
            underlineOffset = if (later.underlineMode != 0) later.underlineOffset else underlineOffset,
            underlineSvgPath = if (later.underlineMode != 0) later.underlineSvgPath else underlineSvgPath,
            bgColor = later.bgColor ?: bgColor,
            bgImage = if (later.bgImage.isNotEmpty()) later.bgImage else bgImage,
            bgImageFit = if (later.bgImage.isNotEmpty()) later.bgImageFit else bgImageFit,
            bgImageScale = if (later.bgImage.isNotEmpty()) later.bgImageScale else bgImageScale,
            npLeft = if (later.bgImage.isNotEmpty()) later.npLeft else npLeft,
            npTop = if (later.bgImage.isNotEmpty()) later.npTop else npTop,
            npRight = if (later.bgImage.isNotEmpty()) later.npRight else npRight,
            npBottom = if (later.bgImage.isNotEmpty()) later.npBottom else npBottom,
            bgBleedMode = if (later.bgImage.isNotEmpty()) later.bgBleedMode else bgBleedMode,
            bgSpacingLeft = if (later.bgImage.isNotEmpty()) later.bgSpacingLeft else bgSpacingLeft,
            bgSpacingRight = if (later.bgImage.isNotEmpty()) later.bgSpacingRight else bgSpacingRight,
            bgSpacingTop = if (later.bgImage.isNotEmpty()) later.bgSpacingTop else bgSpacingTop,
            bgSpacingBottom = if (later.bgImage.isNotEmpty()) later.bgSpacingBottom else bgSpacingBottom,
            font = if (later.font.isNotEmpty()) later.font else font,
        )
    }
}
