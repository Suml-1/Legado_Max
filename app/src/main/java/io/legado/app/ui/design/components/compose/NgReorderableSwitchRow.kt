package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

enum class NgManagementDrawerPanelVariant {
    DEFAULT,
    COMPACT,
}

/** NG 管理抽屉中的不透明连续列表承载面。 */
@Composable
fun NgManagementDrawerPanel(
    modifier: Modifier = Modifier,
    variant: NgManagementDrawerPanelVariant = NgManagementDrawerPanelVariant.DEFAULT,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cornerRadius = when (variant) {
        NgManagementDrawerPanelVariant.DEFAULT -> NgTheme.shapes.largeDp.dp
        NgManagementDrawerPanelVariant.COMPACT -> NgTheme.shapes.mediumDp.dp
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(ngDrawerContentCardColor()),
        content = content,
    )
}

object NgReorderableSwitchRowDefaults {
    val rowHeight = 62.dp
    val dividerThickness = 0.6.dp

    fun contentHeight(itemCount: Int) = if (itemCount <= 0) {
        0.dp
    } else {
        rowHeight * itemCount.toFloat() +
            dividerThickness * (itemCount - 1).toFloat()
    }
}

/**
 * NG 管理抽屉中的连续列表行。
 *
 * 拖动、显示开关和进入详情是三个独立点击区；业务页面只传状态与事件。
 */
@Composable
fun NgReorderableSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onNavigate: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val colors = NgTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NgReorderableSwitchRowDefaults.rowHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = dragHandleModifier
                    .width(42.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(colors.onSurfaceVariant),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClick = onNavigate)
                    .padding(vertical = 9.dp),
            ) {
                Text(
                    text = title,
                    color = Color(colors.onSurface),
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    modifier = Modifier.padding(top = 2.dp),
                    color = Color(colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NgSwitchControl(
                checked = checked,
                onCheckedChange = onCheckedChange,
                variant = NgSwitchControlVariant.COMPACT,
            )
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .fillMaxHeight()
                    .clickable(role = Role.Button, onClick = onNavigate),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right_20),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(colors.onSurfaceVariant),
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 42.dp, end = 12.dp),
                thickness = NgReorderableSwitchRowDefaults.dividerThickness,
                color = Color(colors.outlineVariant).copy(alpha = 0.24f),
            )
        }
    }
}
