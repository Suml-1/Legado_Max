package io.legado.app.ui.design.components.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.NgManagementListCardVariant
import io.legado.app.ui.design.components.NgManagementTrailing
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.theme.NgTheme

/**
 * 与已验收 View 版相同的管理列表卡片骨架。
 *
 * 业务页面负责 UiModel、排序和菜单；组件只维护信息层级与点击区域。
 */
enum class NgManagementListCardSize { STANDARD, COMPACT_SINGLE_LINE }

@Composable
fun NgManagementListCard(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    headerTags: List<NgStatusTagSpec> = emptyList(),
    detailTags: List<NgStatusTagSpec> = emptyList(),
    variant: NgManagementListCardVariant = NgManagementListCardVariant.DEFAULT,
    trailing: NgManagementTrailing = NgManagementTrailing.NONE,
    trailingContentDescription: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLeadingClick: (() -> Unit)? = null,
    onTrailingClick: (() -> Unit)? = null,
    trailingModifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
    containerColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 0.dp,
    titleColor: Color? = null,
    titleFontFamily: FontFamily? = null,
    size: NgManagementListCardSize = NgManagementListCardSize.STANDARD,
    summaryTag: NgStatusTagSpec? = null,
    titleFontWeight: FontWeight? = null,
    leading: @Composable () -> Unit
) {
    require(headerTags.size <= 2) { "Management card supports at most 2 header tags" }
    require(detailTags.size <= 3) { "Management card supports at most 3 detail tags" }
    val isCompactGrid = variant == NgManagementListCardVariant.COMPACT_GRID
    val isMultilineSummary = variant == NgManagementListCardVariant.MULTILINE_SUMMARY
    val isProviderTwoRow = variant == NgManagementListCardVariant.PROVIDER_TWO_ROW
    val compactLine = size == NgManagementListCardSize.COMPACT_SINGLE_LINE
    require(!compactLine || (summary.isNullOrEmpty() && headerTags.isEmpty() && detailTags.isEmpty() && summaryTag == null))
    val shape = RoundedCornerShape(
        if (compactLine) 12.dp else if (isCompactGrid) NgTheme.shapes.smallDp.dp else NgTheme.shapes.largeDp.dp
    )
    val resolvedContainerColor = containerColor ?: ngDrawerContentCardColor()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = if (compactLine) 48.dp else if (isCompactGrid) 54.dp else 70.dp)
            .clip(shape)
            .background(resolvedContainerColor)
            .then(
                if (borderColor != null && borderWidth > 0.dp) {
                    Modifier.border(borderWidth, borderColor, shape)
                } else {
                    Modifier
                }
            )
            .then(
                if (selected) {
                    Modifier.semantics { this.selected = true }
                } else {
                    Modifier
                }
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .width(if (compactLine || isCompactGrid) 4.dp else 6.dp)
                    .fillMaxHeight()
                    .background(Color(NgTheme.colors.primary))
            )
        }
        Box(
            modifier = Modifier
                .width(if (compactLine || isCompactGrid) 44.dp else 58.dp)
                .fillMaxHeight()
                .then(
                    if (onLeadingClick != null) {
                        Modifier.clickable(onClick = onLeadingClick)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            leading()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = if (isCompactGrid) 0.dp else 4.dp,
                    top = if (compactLine || isCompactGrid) 6.dp else 10.dp,
                    bottom = if (compactLine || isCompactGrid) 6.dp else 10.dp,
                ),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = if (isProviderTwoRow) Modifier.heightIn(min = 24.dp) else Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    // 提供商名称先按自然宽度排版，状态 Tag 使用剩余宽度。
                    modifier = if (isProviderTwoRow) Modifier else Modifier.weight(1f),
                    color = titleColor ?: colorResource(R.color.ng_on_surface),
                    fontFamily = titleFontFamily,
                    fontSize = if (compactLine) 16.sp else if (isCompactGrid) {
                        NgTheme.typography.denseItemTitleSp.sp
                    } else {
                        NgTheme.typography.itemTitleSp.sp
                    },
                    lineHeight = if (isCompactGrid) 14.sp else 19.sp,
                    letterSpacing = 0.sp,
                    fontWeight = titleFontWeight ?: if (compactLine) FontWeight.Normal else if (isCompactGrid) FontWeight.Medium else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (headerTags.isNotEmpty()) {
                    Spacer(Modifier.width(if (isProviderTwoRow) 4.dp else 8.dp))
                    NgStatusTagRow(
                        tags = headerTags,
                        header = true,
                        modifier = if (isProviderTwoRow) Modifier.weight(1f, fill = false) else Modifier,
                        providerRow = isProviderTwoRow,
                    )
                }
            }
            if (detailTags.isNotEmpty()) {
                Spacer(Modifier.height(if (isCompactGrid) 3.dp else 6.dp))
                NgStatusTagRow(tags = detailTags, header = false, providerRow = isProviderTwoRow)
            }
            if (summaryTag != null) {
                Spacer(Modifier.height(if (isProviderTwoRow) 6.dp else 4.dp))
                NgStatusTag(spec = summaryTag)
            }
            if (!summary.isNullOrBlank()) {
                Spacer(Modifier.height(if (isCompactGrid) 1.dp else 4.dp))
                Text(
                    text = summary,
                    color = colorResource(R.color.ng_on_surface_variant),
                    fontSize = if (isCompactGrid) {
                        NgTheme.typography.denseItemSummarySp.sp
                    } else {
                        NgTheme.typography.summarySp.sp
                    },
                    lineHeight = if (isCompactGrid) 12.sp else 16.sp,
                    letterSpacing = 0.sp,
                    maxLines = if (isMultilineSummary) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailingContent != null) {
            Box(
                modifier = Modifier
                    .width(if (isCompactGrid) 30.dp else 36.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                trailingContent()
            }
        } else if (trailing != NgManagementTrailing.NONE && onTrailingClick != null) {
            NgManagementTrailingIcon(
                trailing = trailing,
                contentDescription = trailingContentDescription,
                modifier = trailingModifier,
                onClick = onTrailingClick
            )
        } else {
            Spacer(Modifier.width(if (isCompactGrid) 6.dp else 14.dp))
        }
    }
}

/** 36dp 视觉槽，不引入 Material IconButton 的 48dp 最小布局约束。 */
@Composable
fun NgManagementTrailingIcon(
    trailing: NgManagementTrailing,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    require(trailing != NgManagementTrailing.NONE) {
        "NgManagementTrailingIcon requires DRAG or MORE"
    }
    Box(
        modifier = modifier
            .width(36.dp)
            .fillMaxHeight()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier.semantics {
                        contentDescription?.let { this.contentDescription = it }
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(
                when (trailing) {
                    NgManagementTrailing.DRAG -> R.drawable.ic_drag_handle
                    NgManagementTrailing.MORE -> R.drawable.ic_more_vert
                    NgManagementTrailing.NONE -> error("Handled above")
                }
            ),
            contentDescription = if (onClick != null) contentDescription else null,
            modifier = Modifier.size(
                if (trailing == NgManagementTrailing.DRAG) 20.dp else 24.dp
            ),
            tint = colorResource(R.color.ng_on_surface)
        )
    }
}

@Composable
fun NgManagementLeadingIcon(
    painter: Painter,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = Color.Unspecified,
    containerColor: Color? = null
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(containerColor ?: colorResource(R.color.ng_icon_container))
            .padding(7.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = tint
        )
    }
}

@Composable
fun NgManagementLeadingIcon(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = Color.Unspecified,
    containerColor: Color? = null
) {
    NgManagementLeadingIcon(
        painter = painterResource(iconRes),
        modifier = modifier,
        contentDescription = contentDescription,
        tint = tint,
        containerColor = containerColor
    )
}

@Composable
fun NgManagementLeadingText(
    text: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    textColor: Color = Color(NgTheme.colors.primary),
    variant: NgManagementListCardVariant = NgManagementListCardVariant.DEFAULT,
) {
    val isCompactGrid = variant == NgManagementListCardVariant.COMPACT_GRID
    Box(
        modifier = modifier
            .size(if (isCompactGrid) 30.dp else 38.dp)
            .clip(CircleShape)
            .background(colorResource(R.color.ng_icon_container))
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = if (isCompactGrid) NgTheme.typography.denseBadgeSp.sp else 15.sp,
            lineHeight = if (isCompactGrid) 14.sp else 18.sp,
            letterSpacing = 0.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontFamily = NgTheme.fontFamily,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            modifier = Modifier.defaultMinSize(minWidth = 24.dp)
        )
    }
}

@Composable
private fun NgStatusTagRow(
    tags: List<NgStatusTagSpec>,
    header: Boolean,
    modifier: Modifier = Modifier,
    providerRow: Boolean = false,
) {
    if (header) {
        require(tags.all { it.style == NgStatusTagStyle.COMPACT }) {
            "Header tags must use COMPACT style"
        }
    }
    Row(
        modifier = modifier
            .then(
                if (!header) Modifier.fillMaxWidth()
                else if (providerRow) Modifier
                else Modifier.widthIn(max = 198.dp)
            )
            .clipToBounds(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tags.forEachIndexed { index, tag ->
            if (index > 0) {
                Spacer(
                    Modifier.width(
                        if (providerRow) 4.dp else when (tag.style) {
                            NgStatusTagStyle.INLINE -> 4.dp
                            NgStatusTagStyle.COMPACT -> 6.dp
                            NgStatusTagStyle.REGULAR,
                            NgStatusTagStyle.TTS_ROLE -> 8.dp
                        }
                    )
                )
            }
            val tagModifier = Modifier.widthIn(
                max = if (providerRow) 120.dp else when (tag.style) {
                    NgStatusTagStyle.INLINE -> 72.dp
                    NgStatusTagStyle.COMPACT -> 96.dp
                    NgStatusTagStyle.REGULAR,
                    NgStatusTagStyle.TTS_ROLE -> 120.dp
                }
            )
            NgStatusTag(spec = tag, modifier = tagModifier)
        }
    }
}
