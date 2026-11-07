package io.legado.app.ui.config.widget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.legado.app.ui.widget.components.AppPageTopBar
import io.legado.app.ui.widget.components.AppScaffold

/**
 * 配置管理通用 Scaffold。
 *
 * 封装了统一的 TopAppBar（含返回按钮、标题、多选/普通模式 actions）和可选的底部多选操作栏。
 * 底层复用 [AppScaffold]，与页面级界面共用同一套 Scaffold 配置，避免两处配置漂移。
 *
 * @param title 标题文本
 * @param isMultiSelectMode 是否处于多选模式
 * @param onBackClick 返回按钮回调
 * @param onExitMultiSelect 退出多选模式回调
 * @param actions 普通模式下的 TopAppBar actions 插槽
 * @param bottomBar 底部栏插槽（通常为多选模式下的操作栏）
 * @param content 主内容区域
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigManageScaffold(
    title: String,
    isMultiSelectMode: Boolean,
    onBackClick: () -> Unit,
    onExitMultiSelect: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    // 多选模式下系统返回键先退出多选，而不是直接退出页面（与旧版气泡页行为一致）
    BackHandler(enabled = isMultiSelectMode) {
        onExitMultiSelect()
    }
    AppScaffold(
        modifier = modifier,
        topBar = {
            AppPageTopBar(
                title = title,
                onBackClick = {
                    if (isMultiSelectMode) {
                        onExitMultiSelect()
                    } else {
                        onBackClick()
                    }
                },
                actions = actions
            )
        },
        bottomBar = bottomBar,
        content = content
    )
}
