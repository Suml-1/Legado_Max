package io.legado.app.ui.design.components.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

/** 短抽屉中的单行选择数据；业务页面只提供内容与点击事件。 */
@Immutable
data class NgCompactDrawerSelectionItem(
    @DrawableRes val iconRes: Int,
    val title: String,
    val value: String? = null,
    val key: Long? = null,
    val indicator: NgCompactDrawerSelectionIndicator = NgCompactDrawerSelectionIndicator.NONE,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val muted: Boolean = false,
)

enum class NgCompactDrawerSelectionIndicator { NONE, CHECKBOX, NAVIGATION }

/**
 * NG 短抽屉的居中标题栏。
 */
@Composable
fun NgCompactDrawerHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = Color(NgTheme.colors.onSurface),
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * NG 短抽屉共用的亮色卡面。
 *
 * 单本书操作与分组选择共用同一背景、圆角和滚动实现，避免业务抽屉各自取主题 surface。
 */
@Composable
fun NgCompactDrawerPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollState: ScrollState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(NgTheme.shapes.largeDp.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .ngDrawerImageContentCardShadow(shape)
            .clip(shape)
            .background(ngDrawerContentCardColor())
            .then(
                if (scrollState != null) {
                    Modifier.verticalScroll(scrollState)
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        content = content,
    )
}

/**
 * NG 短抽屉的紧凑选择面板。
 *
 * 所有选项共享一个中性卡面；行间只靠固定节奏区分，不绘制分隔线或独立卡片。
 */
@Composable
fun NgCompactDrawerSelectionPanel(
    items: List<NgCompactDrawerSelectionItem>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NgCompactDrawerPanel(
        modifier = modifier
            .fillMaxWidth(),
        scrollState = rememberScrollState(),
    ) {
        items.forEachIndexed { index, item ->
            key(item.key ?: index) {
                val contentColor = Color(when {
                    item.muted -> NgTheme.colors.onSurfaceVariant
                    item.selected -> NgTheme.colors.primary
                    else -> NgTheme.colors.onSurface
                })
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .then(
                            if (item.selected && !item.muted) {
                                Modifier.background(Color(NgTheme.colors.primary).copy(alpha = 0.06f))
                            } else Modifier
                        )
                        .then(
                            if (item.indicator == NgCompactDrawerSelectionIndicator.CHECKBOX) {
                                Modifier.toggleable(
                                    value = item.selected,
                                    enabled = item.enabled,
                                    role = Role.Checkbox,
                                    onValueChange = { onItemClick(index) },
                                )
                            } else {
                                Modifier.clickable(enabled = item.enabled, role = Role.Button) {
                                    onItemClick(index)
                                }
                            }
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = contentColor,
                    )
                    Text(
                        text = item.title,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        color = contentColor,
                        fontSize = NgTheme.typography.itemTitleSp.sp,
                        lineHeight = (NgTheme.typography.itemTitleSp + 4).sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.value?.let { value ->
                        Text(
                            text = value,
                            modifier = Modifier.padding(start = 12.dp),
                            color = Color(NgTheme.colors.onSurfaceVariant),
                            fontSize = NgTheme.typography.summarySp.sp,
                            lineHeight = (NgTheme.typography.summarySp + 3).sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                    when (item.indicator) {
                        NgCompactDrawerSelectionIndicator.CHECKBOX -> NgFileSelectionCheckbox(
                            checked = item.selected,
                            onCheckedChange = null,
                            modifier = Modifier.padding(start = 8.dp),
                            enabled = item.enabled,
                            variant = NgFileSelectionCheckboxVariant.COMPACT,
                        )
                        NgCompactDrawerSelectionIndicator.NAVIGATION -> Icon(
                            painter = painterResource(R.drawable.ic_arrow_right),
                            contentDescription = null,
                            modifier = Modifier.padding(start = 8.dp).size(20.dp),
                            tint = Color(NgTheme.colors.onSurfaceVariant),
                        )
                        NgCompactDrawerSelectionIndicator.NONE -> Unit
                    }
                }
            }
        }
    }
}
