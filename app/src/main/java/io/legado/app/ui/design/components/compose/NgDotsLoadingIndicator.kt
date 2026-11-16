package io.legado.app.ui.design.components.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.theme.NgTheme

/**
 * BGM 已使用的三点渐隐加载动画，供内容页面共用。
 * svg-spinners/3-dots-fade by Utkarsh Verma (MIT).
 * Original: https://github.com/n3r4zzurr0/svg-spinners/blob/main/svg-css/3-dots-fade.svg
 * License: assets/licenses/svg-spinners-MIT.txt
 */
@Composable
fun NgDotsLoadingIndicator(modifier: Modifier = Modifier) {
    val color = Color(NgTheme.colors.primary)
    val phase = if (NgTheme.snapshot.isEInk) null else {
        rememberInfiniteTransition(label = "ng_dots_loading").animateFloat(
            initialValue = 0f,
            targetValue = 800f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "ng_dots_loading_phase",
        )
    }
    Box(
        modifier.semantics {
            contentDescription = "加载中"
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(32.dp)) {
            // 只在绘制阶段读取动画，列表不会逐帧重组；墨水屏使用静态三点。
            val elapsed = phase?.value
            val unit = size.minDimension / 24f
            repeat(3) { index ->
                val opacity = if (elapsed == null) 1f else {
                    val time = (elapsed + 800f - index * 150f) % 800f
                    (1f - .8f * (time / 750f)).coerceAtLeast(.2f)
                }
                drawCircle(
                    color = color.copy(alpha = opacity),
                    radius = 3f * unit,
                    center = Offset(size.width / 2f + (index - 1) * 8f * unit, size.height / 2f),
                )
            }
        }
    }
}

