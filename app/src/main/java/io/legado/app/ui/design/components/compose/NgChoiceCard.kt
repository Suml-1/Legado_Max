package io.legado.app.ui.design.components.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

enum class NgChoiceCardVariant {
    HORIZONTAL,
    FORMAT,
    TEXT,
}

/** NG 并列单选卡，适用于格式、模式等少量互斥选项。 */
@Composable
fun NgChoiceCard(
    title: String,
    summary: String = "",
    @DrawableRes iconRes: Int? = null,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NgChoiceCardVariant = NgChoiceCardVariant.HORIZONTAL,
) {
    val colors = NgTheme.colors
    val isFormatCard = variant == NgChoiceCardVariant.FORMAT
    val isTextCard = variant == NgChoiceCardVariant.TEXT
    val shape = RoundedCornerShape(
        if (isFormatCard) NgTheme.shapes.largeDp.dp else NgTheme.shapes.mediumDp.dp
    )
    val containerColor = when {
        isTextCard && selected -> Color(colors.selectedContainer)
        isTextCard -> ngDrawerContentCardColor()
        !isFormatCard -> Color(colors.cardContainer).copy(
            alpha = NgTheme.effects.containerAlpha
        )
        else -> ngDrawerContentCardColor()
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = if (isTextCard) 60.dp else if (isFormatCard) 88.dp else 82.dp)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            },
        shape = shape,
        color = containerColor,
        contentColor = Color(colors.onSurface),
        border = BorderStroke(
            if (selected) 1.5.dp else if (isFormatCard) 0.6.dp else 0.7.dp,
            Color(if (selected) colors.primary else colors.outlineVariant).copy(
                alpha = if (selected) 0.9f else 0.22f
            )
        ),
        shadowElevation = if (isTextCard) 0.dp else NgTheme.effects.cardElevationDp.dp,
    ) {
        if (isTextCard) {
            NgTextChoiceCardContent(title = title, selected = selected)
        } else if (isFormatCard) {
            NgFormatChoiceCardContent(
                title = title,
                summary = summary,
                iconRes = requireNotNull(iconRes),
                selected = selected,
            )
        } else {
            NgHorizontalChoiceCardContent(
                title = title,
                summary = summary,
                iconRes = requireNotNull(iconRes),
                selected = selected,
            )
        }
    }
}

@Composable
private fun NgHorizontalChoiceCardContent(
    title: String,
    summary: String,
    @DrawableRes iconRes: Int,
    selected: Boolean,
) {
    val colors = NgTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(
                if (selected) colors.selectedContainer else colors.surfaceContainerHigh
            ),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.padding(8.dp),
                tint = Color(if (selected) colors.primary else colors.onSurfaceVariant),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                color = Color(colors.onSurfaceVariant),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_check_circle_outline),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(colors.primary),
            )
        }
    }
}

@Composable
private fun NgFormatChoiceCardContent(
    title: String,
    summary: String,
    @DrawableRes iconRes: Int,
    selected: Boolean,
) {
    val colors = NgTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(18.dp),
            shape = CircleShape,
            color = if (selected) Color(colors.primary) else Color.Transparent,
            border = BorderStroke(
                width = if (selected) 0.dp else 1.4.dp,
                color = Color(if (selected) colors.primary else colors.outlineVariant).copy(
                    alpha = if (selected) 1f else 0.72f
                ),
            ),
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.padding(3.dp),
                    tint = Color(colors.onPrimary),
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = Color(if (selected) colors.primary else colors.onSurfaceVariant).copy(
                    alpha = if (selected) 1f else 0.68f
                ),
            )
            Spacer(Modifier.size(3.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                color = Color(colors.onSurfaceVariant),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NgTextChoiceCardContent(title: String, selected: Boolean) {
    val colors = NgTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(vertical = 20.dp),
            color = Color(if (selected) colors.primary else colors.onSurface),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        if (selected) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp).size(16.dp),
                shape = CircleShape,
                color = Color(colors.primary),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.padding(3.dp),
                    tint = Color.White,
                )
            }
        }
    }
}
