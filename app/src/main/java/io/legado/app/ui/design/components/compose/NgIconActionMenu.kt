package io.legado.app.ui.design.components.compose

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.NgActionPopupItem
import kotlin.math.roundToInt

/**
 * 与 NgIconActionPopup 相同几何的纯 Compose 横向图标菜单。
 * 放在触发控件的 Box 内容末尾，以该 Box 作为定位锚点。
 */
@Composable
internal fun NgIconActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<NgActionPopupItem>,
    style: NgGlassStyle,
    role: NgMaterialRole,
    onItemClick: (NgActionPopupItem) -> Unit,
) {
    if (!expanded || items.isEmpty()) return

    val marginPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val positionProvider = remember(marginPx) { NgIconActionMenuPositionProvider(marginPx) }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        NgGlassSurface(
            shape = RoundedCornerShape(12.dp),
            style = style,
            role = role,
            liquidCornerRadius = 12.dp,
        ) {
            Row(Modifier.padding(horizontal = 4.dp)) {
                items.forEach { item ->
                    key(item.itemId) {
                        NgIconActionMenuItem(item) {
                            onDismissRequest()
                            onItemClick(item)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NgIconActionMenuItem(item: NgActionPopupItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val title = item.title?.toString()
        ?: item.titleRes.takeIf { it != 0 }?.let(context::getString).orEmpty()
    val colors = NgTheme.colors
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(title) } },
        state = rememberTooltipState(),
        focusable = false,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (item.checked) Modifier.background(Color(colors.selectedContainer))
                    else Modifier
                )
                .semantics {
                    contentDescription = title
                    selected = item.checked
                }
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val drawable = item.iconDrawable
            val painter = if (drawable != null) {
                remember(drawable) {
                    NgIconActionDrawablePainter(
                        drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
                    )
                }
            } else {
                item.iconRes.takeIf { it != 0 }?.let { painterResource(it) }
            }
            if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = Color(if (item.checked) colors.primary else colors.onSurface),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private class NgIconActionMenuPositionProvider(private val marginPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(marginPx, maxX)
        val maxY = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx)
        val belowY = anchorBounds.bottom + marginPx
        val desiredY = if (belowY + popupContentSize.height <= windowSize.height - marginPx) {
            belowY
        } else {
            anchorBounds.top - popupContentSize.height - marginPx
        }
        return IntOffset(x, desiredY.coerceIn(marginPx, maxY))
    }
}

private class NgIconActionDrawablePainter(private val drawable: Drawable) : Painter() {
    override val intrinsicSize: Size = if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
        Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
    } else {
        Size.Unspecified
    }

    override fun DrawScope.onDraw() {
        drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
        drawIntoCanvas { drawable.draw(it.nativeCanvas) }
    }
}
