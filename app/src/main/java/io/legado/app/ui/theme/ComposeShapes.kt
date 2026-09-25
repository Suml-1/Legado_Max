package io.legado.app.ui.theme

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.lib.theme.UiCorner

/**
 * Compose 侧圆角令牌（theme-styles.md §7.2）。
 *
 * 圆角值统一取自 [UiCorner]，与 XML 侧的 `UiCorner.panelRounded` / `actionRadius` 同源，
 * 保证同一主题配置下 Compose 页面与 View 页面的圆角观感一致——这是"Compose 化后界面不像同一个 App"
 * 的主要来源，所以圆角不在 Compose 侧另设一套常量。
 *
 * 注意圆角随用户配置变化（`ui_panel_radius` / `ui_action_radius` 是主题资源），
 * 因此每次进入 composition 都重新换算，不做进程级缓存。
 */
@Composable
fun composePanelRadius(): Dp = rememberUiCornerDp { UiCorner.panelRadius(this) }

/** 按钮、标签、小控件的圆角，语义同 [UiCorner.actionRadius] */
@Composable
fun composeActionRadius(): Dp = rememberUiCornerDp { UiCorner.actionRadius(this) }

/** 分组面板 / 卡片容器形状 */
@Composable
fun composePanelShape(): RoundedCornerShape = RoundedCornerShape(composePanelRadius())

/** 按钮 / 标签形状 */
@Composable
fun composeActionShape(): RoundedCornerShape = RoundedCornerShape(composeActionRadius())

/**
 * 把 XML 侧的像素圆角换算成 Compose 的 [Dp]。
 *
 * @param radiusPx 以 Context 为接收者的取圆角函数，内部直接读主题资源
 */
@Composable
private fun rememberUiCornerDp(radiusPx: Context.() -> Float): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        with(density) { radiusPx(context).toDp() }
    }
}
