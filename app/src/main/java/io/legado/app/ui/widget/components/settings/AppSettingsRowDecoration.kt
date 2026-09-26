package io.legado.app.ui.widget.components.settings

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import io.legado.app.ui.theme.AppDimens

/**
 * 面板行的统一装扮：圆角裁剪 + 行底分隔线。
 *
 * 按压反馈不在这里画：按下态要经重组才能画出来，快速点击时按下与抬起在一帧内完成，
 * 用户完全看不到反馈（真机上「轻点没反应、按住一会儿才有」就是这个成因）。
 * 反馈交给 `combinedClickable(indication = ripple(...))`——ripple 在绘制层生效，不依赖重组。
 *
 * 分隔线用 [drawWithContent] 画而不是插一个 `HorizontalDivider`：分隔线必须被面板圆角裁掉，
 * 单独插入的 Divider 是面板 Column 的兄弟节点、不受行的圆角约束，在首/末行会戳出圆角外。
 * 行本身被裁剪成同一个面板圆角，首行/末行的圆角自然由裁剪结果决定，无需再区分四个角。
 *
 * @param shape 面板圆角形状，需与所在 [AppSettingsPanel] 保持一致
 * @param dividerColor 行底分隔线颜色
 * @param showDivider 是否绘制分隔线；分组最后一行传 `false`
 */
fun Modifier.appSettingsRowDecoration(
    shape: RoundedCornerShape,
    dividerColor: Color,
    showDivider: Boolean
): Modifier = this
    .clip(shape)
    .then(
        if (showDivider) {
            Modifier.drawWithContent {
                drawContent()
                val thickness = AppDimens.dividerThickness.toPx()
                val centerY = size.height - thickness / 2f
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = thickness
                )
            }
        } else {
            Modifier
        }
    )
