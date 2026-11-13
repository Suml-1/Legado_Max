package io.legado.app.ui.config.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.R
import io.legado.app.lib.theme.eInkGrayscale
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.VerticalScrollbar

/**
 * 配置编辑弹窗的统一外框：标题 + 内容（超高才滚动）+ 取消/保存。
 *
 * 弹窗高度按内容收缩，最多到屏幕 85%；内容区挂滚动块。
 * 旧版用 `alert { customView { ScrollView } }`，长表单（顶栏 12 项、底栏 5 项 +
 * 10 个图标行）既没有滚动块，也没有显式的高度上限，在矮屏上会被对话框裁切。
 *
 * @param title 弹窗标题
 * @param onDismissRequest 取消/点击外部回调
 * @param onConfirm 保存回调
 * @param modifier Modifier
 * @param confirmEnabled 保存按钮是否可点（如名称非空）
 * @param content 表单内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditDialogFrame(
    title: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 弹窗总高必须能被屏幕装下：标题 + 表单 + 按钮加起来可能超过屏幕（矮屏 / 放大字号），
    // 超出的部分是按钮行，会被直接裁掉
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        // 弹层是独立窗口，页面那层灰阶罩不到，这里再贴一次
        modifier = modifier.eInkGrayscale(),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            // 弹窗高度按内容收缩，最多到屏幕 85%。
            //
            // 不能用 `Column { 标题; Box(Modifier.weight(1f, fill = false)) { 表单 }; 按钮 }`：
            // weight 子项无论 fill 与否都会占满分配给它的高度，所以表单只有半屏高时，
            // 弹窗仍被撑到 85% 屏高、表单下方留出一整块空白、按钮被顶到最底部
            // （主题/底栏编辑弹窗"一大片空白挡住了，还得划一下"就是这么来的）。
            // 这里改成先量标题与按钮，把余下的高度作为表单的上限：表单短则弹窗跟着变矮，
            // 表单长才滚动。
            Layout(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight),
                content = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            start = AppDimens.panelRowHorizontalPadding,
                            top = AppDimens.panelRowHorizontalPadding,
                            end = AppDimens.panelRowHorizontalPadding,
                            bottom = AppDimens.panelTitleBottomPadding,
                        ),
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .verticalScroll(scrollState)
                                .padding(
                                    start = AppDimens.panelRowHorizontalPadding,
                                    end = AppDimens.panelRowHorizontalPadding,
                                    bottom = AppDimens.panelRowTitleSpacing,
                                ),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.manageRowSpacing),
                            content = content,
                        )
                        VerticalScrollbar(
                            state = scrollState,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                end = AppDimens.panelRowTitleSpacing,
                                bottom = AppDimens.panelRowTitleSpacing,
                            ),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismissRequest) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                },
            ) { measurables, constraints ->
                val fixedConstraints = constraints.copy(minHeight = 0)
                val titlePlaceable = measurables[0].measure(fixedConstraints)
                val buttonPlaceable = measurables[2].measure(fixedConstraints)
                val contentMaxHeight = (
                    constraints.maxHeight - titlePlaceable.height - buttonPlaceable.height
                    ).coerceAtLeast(0)
                val contentPlaceable = measurables[1].measure(
                    Constraints(
                        minWidth = 0,
                        maxWidth = constraints.maxWidth,
                        minHeight = 0,
                        maxHeight = contentMaxHeight,
                    ),
                )
                val height =
                    titlePlaceable.height + contentPlaceable.height + buttonPlaceable.height
                val width = if (constraints.hasBoundedWidth) {
                    constraints.maxWidth
                } else {
                    maxOf(titlePlaceable.width, contentPlaceable.width, buttonPlaceable.width)
                }
                layout(width, height) {
                    titlePlaceable.place(0, 0)
                    contentPlaceable.place(0, titlePlaceable.height)
                    buttonPlaceable.place(0, titlePlaceable.height + contentPlaceable.height)
                }
            }
        }
    }
}
