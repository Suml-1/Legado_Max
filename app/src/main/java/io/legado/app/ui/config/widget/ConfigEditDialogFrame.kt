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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.R
import io.legado.app.lib.theme.eInkGrayscale
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.VerticalScrollbar

/**
 * 配置编辑弹窗的统一外框：标题 + 限高可滚动内容 + 取消/保存。
 *
 * 内容区限高并挂滚动块——旧版用 `alert { customView { ScrollView } }`，
 * 长表单（顶栏 12 项、底栏 5 项 + 10 个图标行）既没有滚动块，也没有显式的
 * 高度上限，在矮屏上会被对话框裁切。
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
    // 弹窗总高必须能被屏幕装下：只给内容区限高是不够的，标题 + 480dp 表单 + 按钮
    // 加起来仍可能超过屏幕（矮屏 / 放大字号），超出的部分是按钮行，会被直接裁掉
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight),
            ) {
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
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .heightIn(max = AppDimens.dialogEditMaxHeight)
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
            }
        }
    }
}
