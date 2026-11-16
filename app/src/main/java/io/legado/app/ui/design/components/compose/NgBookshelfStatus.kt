package io.legado.app.ui.design.components.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.getCompatColor

/**
 * 等效复刻书架旧 RotateLoading：2dp 双圆弧、2dp 右下阴影和原动画节奏。
 */
@Composable
fun NgBookshelfUpdateIndicator(
    modifier: Modifier = Modifier,
) {
    val loadingColor = Color(LocalContext.current.accentColor)
    val transition = rememberInfiniteTransition(label = "bookshelf_update_indicator")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "bookshelf_update_rotation",
    )
    val sweep by transition.animateFloat(
        initialValue = 10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1_500
                10f at 0 using LinearEasing
                160f at 1_000 using LinearEasing
                10f at 1_500
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "bookshelf_update_sweep",
    )
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        val inset = strokeWidth * 2f
        val shadowOffset = 2.dp.toPx()
        val arcSize = Size(
            width = (size.width - inset * 2f).coerceAtLeast(0f),
            height = (size.height - inset * 2f).coerceAtLeast(0f),
        )
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        val topStartAngle = rotation + 10f
        val bottomStartAngle = topStartAngle + 180f
        val shadowTopLeft = Offset(inset + shadowOffset, inset + shadowOffset)
        val arcTopLeft = Offset(inset, inset)

        drawArc(
            color = Color(0x1A000000),
            startAngle = topStartAngle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = shadowTopLeft,
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = Color(0x1A000000),
            startAngle = bottomStartAngle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = shadowTopLeft,
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = loadingColor,
            startAngle = topStartAngle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = loadingColor,
            startAngle = bottomStartAngle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = stroke,
        )
    }
}

/**
 * 等效复刻书架旧 BadgeView 的尺寸、圆角、色值与零值隐藏行为。
 */
@Composable
fun NgBookshelfUnreadBadge(
    count: Int,
    highlight: Boolean,
    modifier: Modifier = Modifier,
) {
    if (count == 0) return

    val context = LocalContext.current
    val containerColorInt = if (highlight) {
        context.accentColor
    } else {
        context.getCompatColor(R.color.darker_gray)
    }
    val contentColor = if (ColorUtils.isColorLight(containerColorInt)) {
        Color.Black
    } else {
        Color.White
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
            .background(
                color = Color(containerColorInt),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            style = TextStyle(
                fontFamily = NgTheme.fontFamily,
                fontSize = 11.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}
