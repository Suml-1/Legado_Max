package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.runtime.Immutable
import io.legado.app.help.config.AppConfig

/**
 * 书架列表的显示配置。
 *
 * 与 View 版书架一样，条目内容受一大批开关控制；这些开关都不是 Compose 状态，
 * 因此由 Fragment 在 `BOOKSHELF_REFRESH` 时重建配置对象后下发，条目只做纯渲染。
 *
 * @param bookLayout 书籍布局：0 标准列表 / 1 紧凑列表 / ≥2 网格列数
 * @param folderLayout 文件夹布局（style2），取值同 [bookLayout]
 * @param showBookBorder 列表条目显示外边框（仅列表/紧凑列表生效）
 * @param showUnread 显示未读章节角标
 * @param showLastUpdateTime 显示最后更新时间
 * @param showReadProgress 显示阅读进度条
 * @param showTags 列表条目显示标签（"显示更多信息"+"显示分类信息"）
 * @param showIntro 列表条目显示简介（"显示更多信息"+"显示简介"）
 * @param introLines 简介最大行数
 * @param showBookName 网格条目书名位置：0 封面下方 / 1 不显示 / 2 叠在封面上
 * @param marginPx 条目外边距（用户可调）
 * @param fastScrollerEnabled 显示快速滚动条
 */
@Immutable
data class BookshelfDisplayConfig(
    val bookLayout: Int,
    val folderLayout: Int,
    val showBookBorder: Boolean,
    val showUnread: Boolean,
    val showLastUpdateTime: Boolean,
    val showReadProgress: Boolean,
    val showTags: Boolean,
    val showIntro: Boolean,
    val introLines: Int,
    val showBookName: Int,
    val marginPx: Int,
    val fastScrollerEnabled: Boolean,
) {

    /** 书籍是否网格布局（列数 ≥ 2） */
    val isGrid: Boolean get() = bookLayout >= 2

    /** 是否紧凑列表 */
    val isCompactList: Boolean get() = bookLayout == 1

    private val bookSpan: Int get() = if (bookLayout >= 2) bookLayout else 1

    private val folderSpan: Int get() = if (folderLayout >= 2) folderLayout else 1

    /**
     * style2 的网格总列数：书籍与文件夹列数取最小公倍数，两者可以各自独立选择列数
     * （与原 GridLayoutManager 的 spanCount 口径一致）。为 1 表示走单列列表。
     */
    val spanCount: Int get() = if (bookSpan > 1 || folderSpan > 1) lcm(bookSpan, folderSpan) else 1

    /** style2 是否使用网格布局 */
    val useSpanGrid: Boolean get() = spanCount > 1

    /** 书籍条目在网格中占几列 */
    fun bookGridSpan(): Int = if (bookLayout >= 2) spanCount / bookSpan else spanCount

    /** 文件夹条目在网格中占几列 */
    fun folderGridSpan(): Int = if (folderLayout >= 2) spanCount / folderSpan else spanCount

    private fun lcm(a: Int, b: Int): Int = a * b / gcd(a, b)

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    companion object {

        /** 从 [AppConfig] 读取当前配置，非 Compose 状态，需在刷新事件里重新读取 */
        fun fromAppConfig(): BookshelfDisplayConfig = BookshelfDisplayConfig(
            bookLayout = AppConfig.bookLayout,
            folderLayout = AppConfig.folderLayout,
            showBookBorder = AppConfig.showBookBorder,
            showUnread = AppConfig.showUnread,
            showLastUpdateTime = AppConfig.showLastUpdateTime,
            showReadProgress = AppConfig.showBookshelfReadProgress,
            showTags = AppConfig.showMoreInfoInList && AppConfig.showCategoryInfoInList,
            showIntro = AppConfig.showMoreInfoInList && AppConfig.showIntroInList,
            introLines = AppConfig.introLinesInList,
            showBookName = AppConfig.showBookname,
            marginPx = AppConfig.bookshelfMargin,
            fastScrollerEnabled = AppConfig.showBookshelfFastScroller,
        )
    }
}
