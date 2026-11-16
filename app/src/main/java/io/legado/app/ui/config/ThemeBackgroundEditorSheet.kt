package io.legado.app.ui.config

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toDrawable
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgActionBarButton
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.stackBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

internal data class ThemeBackgroundEditorState(
    val dark: Boolean,
    val path: String?,
    val blur: Int
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ThemeBackgroundEditorSheet(
    state: ThemeBackgroundEditorState,
    onDismissRequest: () -> Unit,
    onSelectImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onBlurChanged: (Int) -> Unit,
    onSave: () -> Unit
) {
    val baseSnapshot = NgTheme.snapshot
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isNinePatch = state.path?.endsWith(".9.png", ignoreCase = true) == true

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
                .fillMaxHeight(0.50f),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            val snapshot = NgTheme.snapshot
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            if (state.dark) {
                                R.string.ng_theme_dark_background
                            } else {
                                R.string.ng_theme_light_background
                            }
                        ),
                        modifier = Modifier.weight(1f),
                        color = Color(snapshot.colors.onSurface),
                        fontSize = 21.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                    NgThemeSheetSaveButton(
                        onClick = onSave,
                        contentDescription = stringResource(R.string.save)
                    )
                }

                Spacer(Modifier.height(12.dp))
                BackgroundPreview(
                    path = state.path,
                    blur = if (isNinePatch) 0 else state.blur,
                    onClick = onSelectImage
                )

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.background_image_blurring),
                            color = Color(snapshot.colors.onSurface),
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = state.blur.toString(),
                        color = Color(snapshot.colors.primary),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                NgSlider(
                    value = state.blur.toFloat(),
                    onValueChange = { onBlurChanged(it.roundToInt()) },
                    valueRange = 0f..25f,
                    steps = 24,
                    variant = NgSliderVariant.DISCRETE,
                    enabled = state.path != null && !isNinePatch
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NgActionBarButton(
                        text = stringResource(
                            if (state.path == null) R.string.select_image else R.string.replace
                        ),
                        icon = Icons.Rounded.Image,
                        onClick = onSelectImage,
                        modifier = Modifier.weight(1f),
                        variant = NgButtonVariant.OUTLINE
                    )
                    if (state.path != null) {
                        NgActionBarButton(
                            text = stringResource(R.string.delete),
                            icon = Icons.Rounded.Delete,
                            onClick = onRemoveImage,
                            modifier = Modifier.weight(1f),
                            variant = NgButtonVariant.DANGER
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundPreview(
    path: String?,
    blur: Int,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val snapshot = NgTheme.snapshot
    val sourceBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, path) {
        if (!path.isNullOrBlank() && !path.startsWith("asset://")) {
            value = withContext(Dispatchers.Default) {
                runCatching { BitmapUtils.decodeBitmap(path, 720, 360) }.getOrNull()
            }
        }
    }
    var drawable by remember(path) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(sourceBitmap, blur) {
        val bitmap = sourceBitmap ?: return@LaunchedEffect
        delay(90)
        val nextDrawable = withContext(Dispatchers.Default) {
            runCatching {
                val preview = if (blur > 0 && !path.orEmpty().endsWith(".9.png", true)) {
                    bitmap.stackBlur(blur)
                } else {
                    bitmap
                }
                preview.toDrawable(context.resources)
            }.getOrNull()
        }
        if (nextDrawable != null) {
            drawable = nextDrawable
        }
    }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(138.dp)
            .clip(shape)
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .background(Color(snapshot.colors.surfaceContainer)),
        contentAlignment = Alignment.Center
    ) {
        if (drawable != null) {
            NgDrawerBackground(
                drawable = requireNotNull(drawable),
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = File(path.orEmpty()).name,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color(snapshot.colors.onSurfaceVariant)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.ng_theme_background_none),
                    color = Color(snapshot.colors.onSurfaceVariant),
                    fontSize = 14.sp
                )
            }
        }
    }
}
