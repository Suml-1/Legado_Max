package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.design.theme.NgTheme

enum class NgCoverMosaicVariant {
    LARGE,
    MEDIUM,
    COMPACT,
}

enum class NgCoverMosaicPresentationVariant {
    OVERLAY,
    FOLDER,
}

/**
 * NG media collection preview. [NgCoverMosaicPresentationVariant.OVERLAY] keeps the legacy
 * four-slot grid and gradient label; [NgCoverMosaicPresentationVariant.FOLDER] uses one
 * continuous folder surface, hides unused slots, and places the label below it.
 */
@Composable
fun NgCoverMosaic(
    label: String,
    itemCount: Int,
    modifier: Modifier = Modifier,
    variant: NgCoverMosaicVariant = NgCoverMosaicVariant.MEDIUM,
    presentationVariant: NgCoverMosaicPresentationVariant =
        NgCoverMosaicPresentationVariant.OVERLAY,
    itemContent: @Composable (index: Int) -> Unit,
) {
    val metrics = coverMosaicMetrics(variant)
    val visibleItemCount = itemCount.coerceIn(0, MaxVisibleItems)
    val labelSize = when (variant) {
        NgCoverMosaicVariant.LARGE -> NgTheme.typography.itemTitleSp
        NgCoverMosaicVariant.MEDIUM -> NgTheme.typography.compactItemTitleSp
        NgCoverMosaicVariant.COMPACT -> NgTheme.typography.labelSp
    }

    when (presentationVariant) {
        NgCoverMosaicPresentationVariant.OVERLAY -> Box(modifier = modifier) {
            CoverMosaicFrame(
                visibleItemCount = visibleItemCount,
                metrics = metrics,
                containerCorner = metrics.containerCorner,
                showEmptySlots = true,
                itemContent = itemContent,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(metrics.labelHeight)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.68f),
                            ),
                        ),
                    ),
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = labelSize.sp,
                    lineHeight = (labelSize + 3).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = metrics.labelHorizontalPadding,
                            end = metrics.labelHorizontalPadding,
                            bottom = metrics.labelBottomPadding,
                        ),
                )
            }
        }

        NgCoverMosaicPresentationVariant.FOLDER -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CoverMosaicFrame(
                visibleItemCount = visibleItemCount,
                metrics = metrics,
                containerCorner = metrics.folderContainerCorner,
                containerAlpha = when {
                    NgTheme.snapshot.isEInk -> 1f
                    NgTheme.snapshot.isDark -> 0.48f
                    else -> 0.32f
                },
                borderAlpha = when {
                    NgTheme.snapshot.isEInk -> 1f
                    NgTheme.snapshot.isDark -> 0.30f
                    else -> 0.34f
                },
                showEmptySlots = false,
                itemContent = itemContent,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Spacer(modifier = Modifier.height(metrics.folderLabelGap))
            Text(
                text = label,
                color = Color(NgTheme.colors.onSurface),
                fontSize = labelSize.sp,
                lineHeight = (labelSize + 3).sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = metrics.labelHorizontalPadding),
            )
        }
    }
}

@Composable
private fun CoverMosaicFrame(
    visibleItemCount: Int,
    metrics: NgCoverMosaicMetrics,
    containerCorner: Dp,
    containerAlpha: Float = 0.88f,
    borderAlpha: Float = 0.26f,
    showEmptySlots: Boolean,
    itemContent: @Composable (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerShape = RoundedCornerShape(containerCorner)
    val coverShape = RoundedCornerShape(metrics.coverCorner)
    Box(
        modifier = modifier
            .clip(containerShape)
            .background(Color(NgTheme.colors.cardContainer).copy(alpha = containerAlpha))
            .border(
                width = 0.8.dp,
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = borderAlpha),
                shape = containerShape,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(metrics.contentPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.coverGap),
        ) {
            repeat(2) { row ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(metrics.coverGap),
                ) {
                    repeat(2) { column ->
                        val index = row * 2 + column
                        val slotModifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(coverShape)
                        Box(
                            modifier = if (showEmptySlots || index < visibleItemCount) {
                                slotModifier.background(
                                    Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.07f),
                                )
                            } else {
                                slotModifier
                            },
                        ) {
                            if (index < visibleItemCount) itemContent(index)
                        }
                    }
                }
            }
        }
    }
}

private const val MaxVisibleItems = 4

private data class NgCoverMosaicMetrics(
    val contentPadding: Dp,
    val coverGap: Dp,
    val containerCorner: Dp,
    val folderContainerCorner: Dp,
    val coverCorner: Dp,
    val labelHeight: Dp,
    val labelHorizontalPadding: Dp,
    val labelBottomPadding: Dp,
    val folderLabelGap: Dp,
)

private fun coverMosaicMetrics(variant: NgCoverMosaicVariant): NgCoverMosaicMetrics {
    return when (variant) {
        NgCoverMosaicVariant.LARGE -> NgCoverMosaicMetrics(
            contentPadding = 6.dp,
            coverGap = 6.dp,
            containerCorner = 20.dp,
            folderContainerCorner = 10.dp,
            coverCorner = 8.dp,
            labelHeight = 48.dp,
            labelHorizontalPadding = 10.dp,
            labelBottomPadding = 8.dp,
            folderLabelGap = 6.dp,
        )

        NgCoverMosaicVariant.MEDIUM -> NgCoverMosaicMetrics(
            contentPadding = 5.dp,
            coverGap = 5.dp,
            containerCorner = 16.dp,
            folderContainerCorner = 8.dp,
            coverCorner = 7.dp,
            labelHeight = 40.dp,
            labelHorizontalPadding = 8.dp,
            labelBottomPadding = 6.dp,
            folderLabelGap = 5.dp,
        )

        NgCoverMosaicVariant.COMPACT -> NgCoverMosaicMetrics(
            contentPadding = 4.dp,
            coverGap = 4.dp,
            containerCorner = 13.dp,
            folderContainerCorner = 7.dp,
            coverCorner = 5.dp,
            labelHeight = 34.dp,
            labelHorizontalPadding = 6.dp,
            labelBottomPadding = 5.dp,
            folderLabelGap = 4.dp,
        )
    }
}
