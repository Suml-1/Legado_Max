package io.legado.app.ui.widget.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.composePanelShape
import io.legado.app.ui.theme.pageCardElevatedContainerColor
import io.legado.app.ui.theme.pageSecondaryTextColor

/**
 * 设置页分组面板：一块圆角卡片，内部按顺序摆放 [AppSettingsActionRow]。
 *
 * 面板整体裁剪成圆角并加一圈描边——描边是分组边界的主要视觉信号（行与行之间只靠细分隔线区分），
 * 去掉描边后多块面板贴在壁纸上会糊成一片，因此这里不提供"无描边"开关。
 *
 * @param modifier 外部修饰符，调用方负责面板之间的间距
 * @param content 面板内容，通常是 [AppSettingsSectionTitle] + 若干行
 */
@Composable
fun AppSettingsPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = composePanelShape()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(pageCardElevatedContainerColor())
            .border(AppDimens.dividerThickness, MaterialTheme.colorScheme.outlineVariant, shape),
        content = content
    )
}

/**
 * 分组标题，位于 [AppSettingsPanel] 内部的第一行。
 *
 * @param title 分组名，调用方从 string resource 取
 */
@Composable
fun AppSettingsSectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = pageSecondaryTextColor(),
        modifier = modifier.padding(
            start = AppDimens.panelRowHorizontalPadding,
            top = AppDimens.panelTitleTopPadding,
            end = AppDimens.panelRowHorizontalPadding,
            bottom = AppDimens.panelTitleBottomPadding
        )
    )
}
