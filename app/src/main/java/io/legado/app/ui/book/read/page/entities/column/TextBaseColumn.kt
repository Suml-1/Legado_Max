package io.legado.app.ui.book.read.page.entities.column

/**
 * 文字基列
 */
interface TextBaseColumn : BaseColumn {
    override var start: Float
    override var end: Float
    val charData: String
    val textColor: Int?
    val underlineMode: Int
    val underlineColor: Int?
    val underlineWidth: Float
    val underlineOffset: Float
    val underlineSvgPath: String
    val bgColor: Int?
    val bgImage: String
    val bgImageFit: Int
    val bgImageScale: Float

    /** 九宫格分割比例（0-1，左右相加、上下相加不超过 1），bgImageFit=3 时生效 */
    val npLeft: Float get() = 0.1f
    val npTop: Float get() = 0.1f
    val npRight: Float get() = 0.1f
    val npBottom: Float get() = 0.1f

    /** 九宫格外扩策略，默认值与 HighlightRule.BLEED_SMART 保持一致 */
    val bgBleedMode: Int get() = 1

    /** 背景图左间距（em），与 HighlightRule.bgSpacingLeft 口径一致 */
    val bgSpacingLeft: Float get() = 0f

    /** 背景图右间距（em），与 HighlightRule.bgSpacingRight 口径一致 */
    val bgSpacingRight: Float get() = 0f

    /** 背景图上间距（em），与 HighlightRule.bgSpacingTop 口径一致 */
    val bgSpacingTop: Float get() = 0f

    /** 背景图下间距（em），与 HighlightRule.bgSpacingBottom 口径一致 */
    val bgSpacingBottom: Float get() = 0f

    /** 高亮规则指定字体路径，空串表示跟随阅读字体 */
    val fontPath: String get() = ""
    var selected: Boolean
    var isSearchResult: Boolean
    var isCurrentSearchResult: Boolean
}
