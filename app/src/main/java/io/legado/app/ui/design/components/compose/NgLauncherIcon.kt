package io.legado.app.ui.design.components.compose

import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** 使用 Android Drawable 管线加载可能为 AdaptiveIconDrawable 的启动图标。 */
@Composable
fun NgLauncherIcon(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { imageView ->
            imageView.setImageResource(iconRes)
            imageView.contentDescription = contentDescription
        },
        modifier = modifier,
    )
}
