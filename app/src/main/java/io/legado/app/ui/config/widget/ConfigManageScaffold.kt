package io.legado.app.ui.config.widget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.widget.components.AppPageTopBar
import io.legado.app.ui.widget.components.AppScaffold

/**
 * 配置管理通用 Scaffold。
 *
 * 封装了统一的 TopAppBar（含返回按钮、标题、多选/普通模式 actions）和可选的底部栏。
 * 底层复用 [AppScaffold]，与页面级界面共用同一套 Scaffold 配置，避免两处配置漂移。
 *
 * 多选模式沿用旧版管理页的行为：标题变成「已选择 N 项」，批量操作以文字形式排在顶栏右侧
 * （见 [ConfigMultiSelectTextAction]），底栏让位给列表，不再压一条图标操作栏。
 *
 * @param title 标题文本
 * @param isMultiSelectMode 是否处于多选模式
 * @param onBackClick 返回按钮回调
 * @param onExitMultiSelect 退出多选模式回调
 * @param modifier Modifier
 * @param selectedCount 多选模式下的已选数量，用于标题
 * @param actions 普通模式下的 TopAppBar actions 插槽
 * @param multiSelectActions 多选模式下的 TopAppBar actions 插槽
 * @param bottomBar 底部栏插槽（多选模式下不显示）
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
    selectedCount: Int = 0,
    actions: @Composable RowScope.() -> Unit = {},
    multiSelectActions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // 多选模式下系统返回键先退出多选，而不是直接退出页面（与旧版气泡页行为一致）
    BackHandler(enabled = isMultiSelectMode) {
        onExitMultiSelect()
    }
    AppScaffold(
        modifier = modifier,
        topBar = {
            AppPageTopBar(
                // 不能复用 R.string.selected：该名字被依赖库占用，且库里有更具体的
                // values-zh-rCN 版本（"已选择"，没有 %1$d），在中文环境下会把数量吃掉
                title = if (isMultiSelectMode) {
                    stringResource(R.string.multi_select_count, selectedCount)
                } else {
                    title
                },
                onBackClick = {
                    if (isMultiSelectMode) {
                        onExitMultiSelect()
                    } else {
                        onBackClick()
                    }
                },
                actions = if (isMultiSelectMode) multiSelectActions else actions,
            )
        },
        bottomBar = {
            if (!isMultiSelectMode) {
                bottomBar()
            }
        },
        content = content,
    )
}
