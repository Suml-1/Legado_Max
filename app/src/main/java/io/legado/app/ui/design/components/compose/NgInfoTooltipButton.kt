package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.launch

/** 点击显示的说明气泡；长说明保留滚动和文字选择，不触发表单提交。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NgInfoTooltipButton(
    text: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    if (text.isBlank()) return
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                SelectionContainer {
                    Text(
                        text = text,
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState()),
                        fontFamily = NgTheme.fontFamily,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        },
        state = state,
        focusable = true,
        enableUserInput = false,
    ) {
        NgIconButton(
            onClick = {
                if (state.isVisible) state.dismiss()
                else scope.launch { state.show() }
            },
            modifier = modifier.size(44.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cfg_about),
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = Color(NgTheme.colors.onSurfaceVariant),
            )
        }
    }
}
