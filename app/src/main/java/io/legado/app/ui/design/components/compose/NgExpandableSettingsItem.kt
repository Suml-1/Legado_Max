package io.legado.app.ui.design.components.compose

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.design.theme.NgTheme

enum class NgExpandableSettingsItemVariant {
    REGULAR,
    REGULAR_LEADING,
    COMPACT,
    COMPACT_LEADING,
}

/** NG 设置页中可向下展开的单体卡片。 */
@Composable
fun NgExpandableSettingsItem(
    title: String,
    summary: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    variant: NgExpandableSettingsItemVariant = NgExpandableSettingsItemVariant.REGULAR,
    @DrawableRes leadingIconRes: Int? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val compact = variant == NgExpandableSettingsItemVariant.COMPACT ||
        variant == NgExpandableSettingsItemVariant.COMPACT_LEADING
    val withLeading = variant == NgExpandableSettingsItemVariant.REGULAR_LEADING ||
        variant == NgExpandableSettingsItemVariant.COMPACT_LEADING
    val cornerRadius = if (compact) 16.dp else 18.dp
    val shape = RoundedCornerShape(cornerRadius)
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "NgExpandableSettingsArrow"
    )
    NgSettingsCardSurface(
        modifier = modifier
            .fillMaxWidth(),
        cornerRadius = cornerRadius,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .semantics { role = Role.Button }
                .heightIn(min = if (compact) 50.dp else 64.dp)
                .padding(
                    start = 16.dp,
                    top = if (compact) 8.dp else 10.dp,
                    end = 14.dp,
                    bottom = if (compact) 8.dp else 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (withLeading && leadingIconRes != null) {
                if (compact) {
                    Icon(
                        painter = painterResource(leadingIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color(NgTheme.colors.onSurfaceVariant),
                    )
                } else {
                    NgSettingsIcon(
                        painter = painterResource(leadingIconRes),
                        contentDescription = null,
                    )
                }
                Spacer(Modifier.width(if (compact) 12.dp else 14.dp))
            }
            if (compact) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = Color(NgTheme.colors.onSurface),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = NgTheme.typography.itemTitleSp.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!summary.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = summary,
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = NgTheme.typography.summarySp.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        color = Color(NgTheme.colors.onSurface),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = NgTheme.typography.itemTitleSp.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!summary.isNullOrBlank()) {
                        Text(
                            text = summary,
                            color = Color(NgTheme.colors.onSurfaceVariant),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = NgTheme.typography.summarySp.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(arrowRotation),
                tint = Color(NgTheme.colors.onSurfaceVariant)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = if (compact) 12.dp else 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(if (withLeading) 9.dp else 12.dp),
                content = content
            )
        }
    }
}
