package io.legado.app.ui.config.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.legado.app.R
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.composeActionShape

/**
 * 配置管理页底部「新建/添加」按钮。
 *
 * 统一了主题列表、应用主题、底栏、顶栏、气泡五处各写一遍的添加按钮，
 * 并把圆角/配色收回到主题令牌（此前主题列表版本直接取 `R.color.background_add_button`、
 * 硬编码 8dp 圆角，是规范 `migration-review.md` §17 的示例违规）。
 *
 * @param text 按钮文案，必须已资源化
 * @param onClick 点击回调
 * @param modifier Modifier
 */
@Composable
fun ConfigAddBottomBar(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = AppDimens.screenPadding,
                end = AppDimens.screenPadding,
                bottom = AppDimens.screenPadding,
            )
            .clickable(onClick = onClick),
        shape = composeActionShape(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = AppDimens.dividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppDimens.panelRowVerticalPadding),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfigAddBottomBarPreview() {
    MaterialTheme {
        ConfigAddBottomBar(
            text = stringResource(R.string.add_theme),
            onClick = {},
        )
    }
}
