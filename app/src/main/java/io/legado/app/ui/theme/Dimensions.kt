package io.legado.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局 Compose 尺寸令牌（theme-styles.md §7.2）。
 *
 * 这里是 Compose 侧所有 dp 值的唯一定义处：Composable 体内禁止再裸写 `12.dp` / `16.dp`。
 * 原先分散在各 Feature 的 `PageDimens`（精准管理、下载管理等页面就近定义）已并入此处，
 * 新增尺寸一律追加到本对象，不要再另起一个平行对象。
 *
 * 字号不在此定义：文本一律走 `MaterialTheme.typography` 语义样式（theme-styles.md §7.4）。
 */
object AppDimens {

    // ── 通用整页列表 ──

    /** 整页列表（精准管理、下载管理等）的四周内边距 */
    val screenPadding: Dp = 16.dp

    /** 整页列表中卡片之间的纵向间距 */
    val cardSpacing: Dp = 12.dp

    // ── 内嵌 Tab 页（主界面书架 / 发现 / 我的）──

    /** Tab 页分组面板的左右外边距，比整页列表窄，让面板更贴近屏幕边缘 */
    val panelHorizontalPadding: Dp = 12.dp

    /** 相邻分组面板之间的纵向间距 */
    val panelSpacing: Dp = 10.dp

    /** 分组面板首个面板距列表顶部的间距 */
    val panelTopPadding: Dp = 8.dp

    /** 面板行的最小高度，保证命中区不小于无障碍要求的 48dp */
    val panelRowMinHeight: Dp = 60.dp

    /** 面板行内容的水平内边距 */
    val panelRowHorizontalPadding: Dp = 16.dp

    /** 面板行内容的垂直内边距 */
    val panelRowVerticalPadding: Dp = 10.dp

    /** 行内图标尺寸 */
    val panelRowIconSize: Dp = 22.dp

    /** 行内图标与文本之间的间距 */
    val panelRowIconSpacing: Dp = 14.dp

    /** 行内标题与副标题之间的间距 */
    val panelRowTitleSpacing: Dp = 8.dp

    /** 行内尾部控件与文本之间的间距（如开关） */
    val panelRowTrailingSpacing: Dp = 8.dp

    /** 分组标题的上内边距 */
    val panelTitleTopPadding: Dp = 12.dp

    /** 分组标题的下内边距 */
    val panelTitleBottomPadding: Dp = 6.dp

    /** 面板描边与行分隔线的粗细 */
    val dividerThickness: Dp = 1.dp

    // ── 书籍封面（AppBookCover）──

    /** 封面圆角，对齐 View 版 CoverImageView 的 12px 描边圆角 */
    val bookCoverCornerRadius: Dp = 4.dp

    // ── 弹窗 ──

    /** 单选列表弹窗的选项区最大高度，超出后滚动，避免弹窗顶穿屏幕 */
    val dialogOptionsMaxHeight: Dp = 320.dp
}
