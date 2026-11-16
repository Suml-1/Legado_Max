package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.BookshelfCardStyle
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.theme.NgTheme

enum class NgBookshelfManageCardVariant {
    CONTAINED_CARD,
    FLAT_ROW,
    COVER_CARD,
}

/**
 * 书架管理专用的紧凑双行卡片。
 *
 * 与通用 [NgManagementListCard] 分离，避免书架的选中描边、长按详情和拖动手柄
 * 改变供应商、朗读引擎等既有管理列表的圆角与结构。
 * 封面卡显式传入 [appearance] 时共用书架材质；未传入时保留原白卡。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NgBookshelfManageCard(
    title: String,
    supportingText: String,
    metadataText: String,
    modifier: Modifier = Modifier,
    variant: NgBookshelfManageCardVariant = NgBookshelfManageCardVariant.CONTAINED_CARD,
    appearance: BookshelfCardStyle? = null,
    cacheText: String? = null,
    groupTag: NgStatusTagSpec? = null,
    updateTag: NgStatusTagSpec? = null,
    selected: Boolean = false,
    coverContent: (@Composable () -> Unit)? = null,
    dragHandleContentDescription: String,
    dragHandleModifier: Modifier = Modifier,
    dragHandleEnabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    require(groupTag == null || groupTag.style == NgStatusTagStyle.INLINE) {
        "Bookshelf management card group tag must use INLINE style"
    }
    require(updateTag == null || updateTag.style == NgStatusTagStyle.COMPACT) {
        "Bookshelf management card update tag must use COMPACT style"
    }

    if (variant == NgBookshelfManageCardVariant.COVER_CARD) {
        NgBookshelfManageCoverCard(
            title = title,
            supportingText = supportingText,
            metadataText = metadataText,
            cacheText = cacheText,
            groupTag = groupTag,
            updateTag = updateTag,
            selected = selected,
            appearance = appearance,
            coverContent = requireNotNull(coverContent) {
                "COVER_CARD variant requires coverContent"
            },
            dragHandleContentDescription = dragHandleContentDescription,
            dragHandleModifier = dragHandleModifier,
            dragHandleEnabled = dragHandleEnabled,
            modifier = modifier,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        return
    }

    val isFlatRow = variant == NgBookshelfManageCardVariant.FLAT_ROW
    val shape = RoundedCornerShape(
        if (isFlatRow) 0.dp else NgTheme.shapes.mediumDp.dp
    )
    val selectedOutline = Color(NgTheme.colors.primary)
    val metadataColor = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.68f)
    val supportingMetadata = listOf(supportingText, metadataText)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isFlatRow) 68.dp else 54.dp)
            .clip(shape)
            .background(
                if (isFlatRow) {
                    if (selected) {
                        Color(NgTheme.colors.selectedContainer).copy(
                            alpha = if (NgTheme.snapshot.isEInk) 1f else 0.28f
                        )
                    } else {
                        Color.Transparent
                    }
                } else {
                    colorResource(R.color.ng_surface_card)
                }
            )
            .then(
                if (selected && !isFlatRow) {
                    Modifier
                        .border(1.dp, selectedOutline, shape)
                } else {
                    Modifier
                }
            )
            .semantics { this.selected = selected }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isFlatRow) {
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .then(
                                if (selected) {
                                    Modifier.background(selectedOutline)
                                } else {
                                    Modifier.border(
                                        width = 1.5.dp,
                                        color = metadataColor.copy(alpha = 0.72f),
                                        shape = CircleShape
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (isFlatRow) 0.dp else 16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = NgTheme.typography.summarySp.sp,
                    lineHeight = (NgTheme.typography.summarySp + 3).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = supportingMetadata,
                    modifier = Modifier.fillMaxWidth(),
                    color = metadataColor,
                    fontSize = (NgTheme.typography.labelSp - 1).sp,
                    lineHeight = (NgTheme.typography.labelSp + 1).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(
                modifier = Modifier.width(82.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!cacheText.isNullOrBlank()) {
                        Text(
                            text = cacheText,
                            color = metadataColor,
                            fontSize = (NgTheme.typography.labelSp - 1).sp,
                            lineHeight = (NgTheme.typography.labelSp + 1).sp,
                            maxLines = 1
                        )
                    }
                    if (updateTag != null) {
                        Spacer(Modifier.width(4.dp))
                        NgStatusDot(
                            spec = updateTag,
                            modifier = Modifier.size(6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                if (groupTag != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NgStatusTag(
                            spec = groupTag,
                            modifier = Modifier.widthIn(max = 72.dp)
                        )
                        if (updateTag != null) {
                            Spacer(Modifier.width(10.dp))
                        }
                    }
                }
            }
            Box(
                modifier = dragHandleModifier
                    .width(28.dp)
                    .fillMaxHeight()
                    .semantics { contentDescription = dragHandleContentDescription },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (selected) {
                        selectedOutline
                    } else {
                        metadataColor.copy(alpha = 0.52f)
                    }
                )
            }
        }
        if (isFlatRow) {
            HorizontalDivider(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 52.dp, end = 14.dp),
                color = Color(NgTheme.colors.outlineVariant).copy(
                    alpha = if (NgTheme.snapshot.isEInk) 1f else 0.24f
                )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NgBookshelfManageCoverCard(
    title: String,
    supportingText: String,
    metadataText: String,
    cacheText: String?,
    groupTag: NgStatusTagSpec?,
    updateTag: NgStatusTagSpec?,
    selected: Boolean,
    appearance: BookshelfCardStyle?,
    coverContent: @Composable () -> Unit,
    dragHandleContentDescription: String,
    dragHandleModifier: Modifier,
    dragHandleEnabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val radius = dimensionResource(R.dimen.ng_radius_s)
    val shape = RoundedCornerShape(radius)
    val coverShape = RoundedCornerShape(4.dp)
    val selectedOutline = Color(NgTheme.colors.primary)
    val metadataColor = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.68f)
    val supportingMetadata = listOf(supportingText, metadataText)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    val cardColor = colorResource(R.color.ng_surface_card).copy(alpha = 1f)

    val cardModifier = modifier
        .fillMaxWidth()
        .height(76.dp)
        .clip(shape)
        .then(
            if (selected) {
                Modifier.border(2.dp, selectedOutline, shape)
            } else {
                Modifier
            }
        )
        .semantics { this.selected = selected }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    val cardContent: @Composable BoxScope.() -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .height(60.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(coverShape),
                ) {
                    coverContent()
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 12.dp, top = 1.dp, bottom = 1.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = supportingMetadata,
                    modifier = Modifier.fillMaxWidth(),
                    color = metadataColor,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!cacheText.isNullOrBlank()) {
                            Text(
                                text = cacheText,
                                modifier = Modifier.weight(1f, fill = false),
                                color = metadataColor,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (updateTag != null) {
                            Spacer(Modifier.width(4.dp))
                            NgStatusDot(
                                spec = updateTag,
                                modifier = Modifier.size(6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    if (groupTag != null) {
                        NgStatusTag(
                            spec = groupTag,
                            modifier = Modifier.widthIn(max = 72.dp),
                        )
                    }
                }
            }
            Box(
                modifier = dragHandleModifier
                    .width(32.dp)
                    .fillMaxHeight()
                    .semantics {
                        if (dragHandleEnabled) contentDescription = dragHandleContentDescription
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = metadataColor.copy(alpha = if (dragHandleEnabled) 0.62f else 0.24f),
                )
            }
        }
    }
    if (appearance == null) {
        Box(modifier = cardModifier.background(cardColor), content = cardContent)
    } else {
        NgBookshelfCardSurface(
            appearance = appearance,
            pressed = isPressed,
            cornerRadius = radius,
            modifier = cardModifier,
            content = cardContent,
        )
    }
}
