package io.legado.app.ui.main.explore.compose

import androidx.compose.runtime.Stable

/**
 * 发现页列表需要向宿主（Fragment）抛出的动作集合。
 *
 * 集中成一个契约对象，避免每个条目透传五六个 lambda；这些动作都会开 Activity、弹 View 对话框
 * 或写剪贴板，属于平台操作，统一留在 Fragment 执行（state-events.md §4.1）。
 *
 * 由 Fragment 在 `by lazy` 里构造一次，属性不再变化，因此标注 [Stable] 是成立的。
 */
@Stable
internal class ExploreSourceActions(
    /** 点击条目标题：展开 / 折叠该源 */
    val onToggleExpand: (ExploreSourceItem) -> Unit,
    /** 长按菜单选中某项 */
    val onMenuAction: (ExploreSourceItem, ExploreSourceMenuAction) -> Unit,
    /** 打开发现页（url 类分类项的点击） */
    val onOpenExplore: (sourceUrl: String, title: String, exploreUrl: String) -> Unit,
    /** 弹出错误详情（书源规则求值失败时会生成 `ERROR:` 分类） */
    val onShowError: (message: String) -> Unit,
    /** usehtml 内容里的图片长按查看 */
    val onShowPhoto: (url: String, sourceUrl: String) -> Unit,
)
