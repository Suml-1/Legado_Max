package io.legado.app.ui.config.bubble.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.composeActionShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/**
 * 气泡包预览图。
 *
 * SVG 渲染在 IO 线程完成，键取「目录 + 更新时间 + 模板 + 强调色」，
 * 只有包内容真的变了才重新渲染（旧版靠 ImageView tag 做同样的失效判断）。
 *
 * @param config 气泡配置
 * @param modifier Modifier
 */
@Composable
fun BubblePreview(
    config: BubblePackageManager.Config,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        config.dirName,
        config.updatedAt,
        config.svgTemplate,
        config.dayEmphasisColor,
    ) {
        value = withContext(Dispatchers.IO) { renderPreview(config)?.asImageBitmap() }
    }
    Box(
        modifier = modifier
            .size(AppDimens.managePreviewBoxSize)
            .clip(composeActionShape())
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(AppDimens.managePreviewBoxSize)
                    .padding(AppDimens.manageCardTitleSpacing),
            )
        }
    }
}

private fun renderPreview(config: BubblePackageManager.Config): android.graphics.Bitmap? = runCatching {
    val color = config.dayEmphasisColor?.takeIf { it.isNotBlank() }
        ?: BubblePackageManager.DEFAULT_EMPHASIS_COLOR
    val svg = config.svgTemplate
        .replace("\${color}", color)
        .replace("\${num}", "12")
    io.legado.app.utils.SvgUtils.createBitmap(ByteArrayInputStream(svg.toByteArray()), 128, 128)
}.getOrNull()
