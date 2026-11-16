package io.legado.app.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.theme.NgTheme
import java.util.Locale

internal data class ThemeFontScaleEditorState(
    val scale: Float,
    val followSystem: Boolean
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ThemeFontScaleEditorSheet(
    state: ThemeFontScaleEditorState,
    onDismissRequest: () -> Unit,
    onScaleChanged: (Float) -> Unit,
    onFollowSystem: () -> Unit,
    onSave: () -> Unit
) {
    val baseSnapshot = NgTheme.snapshot
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        contentColor = Color(baseSnapshot.colors.onSurface),
        shape = RectangleShape
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.30f),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            val snapshot = NgTheme.snapshot
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NgThemeSheetActionButton(
                        onClick = onFollowSystem,
                        contentDescription = stringResource(R.string.ng_font_scale_follow_system)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color(
                                if (state.followSystem) {
                                    snapshot.colors.primary
                                } else {
                                    snapshot.colors.onSurface
                                }
                            )
                        )
                    }
                    Text(
                        text = stringResource(R.string.font_scale),
                        modifier = Modifier.weight(1f),
                        color = Color(snapshot.colors.onSurface),
                        fontSize = 21.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    NgThemeSheetSaveButton(
                        onClick = onSave,
                        contentDescription = stringResource(R.string.save)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.ng_font_scale_multiplier),
                        modifier = Modifier.weight(1f),
                        color = Color(snapshot.colors.onSurfaceVariant),
                        fontSize = 15.sp
                    )
                    Text(
                        text = String.format(Locale.ROOT, "%.1f", state.scale),
                        color = Color(snapshot.colors.primary),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                NgSlider(
                    value = state.scale,
                    onValueChange = onScaleChanged,
                    valueRange = 0.8f..1.6f,
                    steps = 7,
                    variant = NgSliderVariant.DISCRETE
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "0.8",
                        color = Color(snapshot.colors.onSurfaceVariant),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "1.6",
                        color = Color(snapshot.colors.onSurfaceVariant),
                        fontSize = 12.sp
                    )
                }

            }
        }
    }
}
