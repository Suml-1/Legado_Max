package io.legado.app.ui.config

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import io.legado.app.help.config.NgThemePresentationMode

/** Small appearance samples have their own composition, independent of full-screen geometry. */
@Composable
internal fun ThemeAppearancePreview(
    mode: NgThemePresentationMode,
    softColors: List<Int>,
    modifier: Modifier = Modifier,
) {
    Box(modifier.drawWithCache {
        val w = size.width
        val h = size.height
        when (mode) {
            NgThemePresentationMode.MULTICOLOR -> {
                val base = Brush.linearGradient(
                    listOf(Color(0xFF43CFF1), Color(0xFFAAF2E6)),
                    start = Offset.Zero, end = Offset(w * 0.35f, h),
                )
                val mint = Path().apply {
                    moveTo(-w * 0.1f, h * 0.34f)
                    cubicTo(w * 0.16f, h * 0.46f, w * 0.34f, h * 0.58f, w * 0.50f, h * 0.91f)
                    lineTo(w * 0.68f, h * 1.1f)
                    lineTo(-w * 0.1f, h * 1.1f)
                    close()
                }
                val mintColor = Brush.linearGradient(
                    listOf(Color(0xFFADEED7), Color(0xFF77DDC0)),
                    start = Offset.Zero, end = Offset(w * 0.3f, h),
                )
                val colorField = Path().apply {
                    moveTo(w * 0.56f, -h * 0.1f)
                    cubicTo(w * 0.56f, h * 0.15f, w * 0.94f, h * 0.30f, w * 0.82f, h * 0.42f)
                    cubicTo(w * 0.68f, h * 0.55f, w * 0.40f, h * 0.63f, w * 0.42f, h * 0.79f)
                    cubicTo(w * 0.47f, h * 0.99f, w * 0.90f, h * 1.05f, w * 1.1f, h * 1.1f)
                    lineTo(w * 1.1f, -h * 0.1f)
                    close()
                }
                val colors = Brush.linearGradient(
                    0f to Color(0xFF559AF5), 0.30f to Color(0xFFACB5EF),
                    0.57f to Color(0xFFF4A9C7), 0.80f to Color(0xFFFFD4AA), 1f to Color(0xFFF9B49D),
                    start = Offset(w * 0.82f, 0f), end = Offset(w * 0.58f, h),
                )
                onDrawBehind {
                    drawRect(base)
                    drawPath(mint, mintColor)
                    drawPath(colorField, colors)
                }
            }
            NgThemePresentationMode.SOFT_GRADIENT -> {
                // Full-screen radial spotlights become blotches in a small sample.
                val gradient = Brush.linearGradient(
                    colors = softColors.take(4).map(::Color),
                    start = Offset.Zero, end = Offset(w * 0.7f, h),
                )
                onDrawBehind { drawRect(gradient) }
            }
            else -> {
                val paper = Color(0xFFFAF8F4)
                val ink = Color(0xFF282828)
                val split = w * 0.5f
                val circleCenter = Offset(split, h * 0.5f)
                val circleRadius = h * 0.35f
                onDrawBehind {
                    drawRect(paper)
                    drawRect(ink, topLeft = Offset(split, 0f), size = Size(w - split, h))
                    clipRect(right = split) {
                        drawCircle(ink, radius = circleRadius, center = circleCenter)
                    }
                    clipRect(left = split) {
                        drawCircle(paper, radius = circleRadius, center = circleCenter)
                    }
                }
            }
        }
    })
}
