package io.legado.app.ui.main.explore.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.R
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.composePanelShape
import io.legado.app.ui.theme.pageSecondaryTextColor

/**
 * 发现页的书源条目：标题行 + 展开后的分类区。
 *
 * 交互对齐原实现：点击标题行展开 / 折叠（同时会滚到该行位置）、长按弹书源操作菜单、
 * 展开期间标题行尾部显示加载指示器。菜单里的"刷新"由本组件就地转成 [ExploreKindsState.requestReload]，
 * 因为它影响的只是本行的分类区状态。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ExploreSourceRow(
    modifier: Modifier = Modifier,
    sourceItem: ExploreSourceItem,
    expanded: Boolean,
    controller: ExploreKindsController,
    actions: ExploreSourceActions,
) {
    val kindsState = remember(sourceItem.sourceUrl) { ExploreKindsState() }
    var menuExpanded by remember(sourceItem.sourceUrl) { mutableStateOf(false) }
    // 读快照状态建立重组依赖：长按菜单刷新 / 登录后的规则回调会递增它
    val refreshTick = controller.refreshTick(sourceItem.sourceUrl)

    // 分类求值会跑书源规则（@js: 源还会发请求），折叠状态下不预先求值
    LaunchedEffect(sourceItem.sourceUrl, expanded, refreshTick) {
        if (expanded) {
            kindsState.load(controller, sourceItem.sourceUrl, refreshTick)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = AppDimens.exploreRowHorizontalPadding,
                end = AppDimens.exploreRowHorizontalPadding,
                top = AppDimens.exploreRowTopPadding,
                bottom = AppDimens.exploreRowBottomPadding,
            ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(composePanelShape())
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = AppDimens.EXPLORE_TITLE_BG_ALPHA)
                    )
                    .combinedClickable(
                        onClick = { actions.onToggleExpand(sourceItem) },
                        onLongClick = { menuExpanded = true },
                    )
                    .semantics(mergeDescendants = true) { role = Role.Button }
                    .padding(
                        horizontal = AppDimens.exploreTitleHorizontalPadding,
                        vertical = AppDimens.exploreTitleVerticalPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = sourceItem.sourceName,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (kindsState.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(AppDimens.exploreTitleIconSize),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = AppDimens.exploreTitleProgressStroke,
                    )
                    Spacer(modifier = Modifier.width(AppDimens.exploreTitleIconSpacing))
                }
                // 展开状态是整行的语义，箭头只是它的视觉表达（装饰图标，不单独播报）
                Image(
                    painter = painterResource(
                        if (expanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_right
                    ),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(pageSecondaryTextColor()),
                    modifier = Modifier.size(AppDimens.exploreTitleIconSize),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                ExploreSourceMenuAction.entries.forEach { action ->
                    if (action == ExploreSourceMenuAction.Login && !sourceItem.hasLoginUrl) {
                        return@forEach
                    }
                    DropdownMenuItem(
                        text = { Text(text = stringResource(action.titleRes()), style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            menuExpanded = false
                            if (action == ExploreSourceMenuAction.Refresh) {
                                // 刷新只影响本行的分类区状态，就地处理
                                controller.requestRefresh(sourceItem.sourceUrl)
                            } else {
                                actions.onMenuAction(sourceItem, action)
                            }
                        },
                    )
                }
            }
        }
        if (expanded) {
            ExploreKindsContent(
                sourceItem = sourceItem,
                kindsState = kindsState,
                controller = controller,
                actions = actions,
            )
        }
    }
}

/** 菜单文案复用 View 版菜单（[R.menu.explore_item]）的同一批字符串资源 */
private fun ExploreSourceMenuAction.titleRes(): Int {
    return when (this) {
        ExploreSourceMenuAction.Edit -> R.string.edit
        ExploreSourceMenuAction.ToTop -> R.string.to_top
        ExploreSourceMenuAction.Query -> R.string.query
        ExploreSourceMenuAction.Login -> R.string.login
        ExploreSourceMenuAction.Search -> R.string.search
        ExploreSourceMenuAction.Refresh -> R.string.refresh
        ExploreSourceMenuAction.Delete -> R.string.delete
    }
}
