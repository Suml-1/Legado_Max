package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** 紧凑列表中单个辅助动作与开关的组合外壳，两个控件保留独立事件。 */
@Composable
fun NgSwitchActionGroup(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    actionIcon: Painter,
    actionDescription: String,
    onAction: () -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(contentColor.copy(alpha = 0.01f))
            .border(0.7.dp, contentColor.copy(alpha = 0.10f), shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = actionEnabled, role = Role.Button, onClick = onAction),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = actionIcon,
                contentDescription = actionDescription,
                modifier = Modifier.size(22.dp),
                tint = contentColor.copy(alpha = if (actionEnabled) 0.85f else 0.38f),
            )
        }
        Spacer(
            Modifier.size(width = 0.7.dp, height = 16.dp)
                .background(contentColor.copy(alpha = 0.16f)),
        )
        NgSwitchControl(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(width = 52.dp, height = 36.dp),
        )
    }
}
