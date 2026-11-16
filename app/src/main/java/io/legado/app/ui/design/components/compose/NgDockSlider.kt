package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.design.theme.NgTheme

/** NG 设置卡内用于距离和透明度的标题、轨道与边界标签组合。 */
@Composable
fun NgDockSlider(
    title: String,
    valueText: String,
    minimumText: String,
    maximumText: String,
    showBoundLabels: Boolean = true,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    variant: NgSliderVariant = NgSliderVariant.CONTINUOUS,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color(NgTheme.colors.onSurface),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Normal
                )
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = valueText,
                color = Color(NgTheme.colors.primary),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        NgSlider(
            value = value.coerceIn(valueRange),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            variant = variant,
            onValueChangeFinished = onValueChangeFinished
        )
        if (showBoundLabels) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BoundLabel(minimumText)
                BoundLabel(maximumText)
            }
        }
    }
}

@Composable
private fun BoundLabel(text: String) {
    Text(
        text = text,
        color = Color(NgTheme.colors.onSurfaceVariant),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Normal
        )
    )
}
