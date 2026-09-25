package io.legado.app.ui.main.homepage.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.legado.app.help.config.AppConfig
import io.legado.app.model.BookCover
import io.legado.app.ui.widget.components.BookCoverTextOverlay

/**
 * 首页书籍封面组件
 *
 * 受封面设置（CoverConfig）控制：
 * - 当 useDefaultCover 开启时，强制使用默认封面
 * - 当无封面 URL 时，回退到默认封面
 * - 根据封面设置决定是否在封面上绘制书名和作者（绘制逻辑见 [BookCoverTextOverlay]）
 * - 当启用封面图集时，根据 identity 参数随机选择封面图片
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HomepageBookCover(
    name: String,
    author: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    identity: String? = null,
) {
    val galleryDefaultCover = BookCover.getGalleryDefaultCover(identity, coverUrl)
    val useDefaultCover = AppConfig.useDefaultCover
    val coverShadowEnabled = AppConfig.bookCoverShadow
    // 图集默认封面优先于 useDefaultCover，使每本书获得随机图集封面
    val displayCover = galleryDefaultCover ?: if (useDefaultCover) null else coverUrl
    val shouldDrawName = (galleryDefaultCover == null && useDefaultCover || coverUrl == null) && BookCover.drawBookName

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                if (coverShadowEnabled) Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                else Modifier
            )
    ) {
        if (displayCover != null) {
            GlideImage(
                model = displayCover,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            GlideImage(
                model = BookCover.defaultDrawable,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (shouldDrawName && name.isNotBlank()) {
            BookCoverTextOverlay(
                name = name,
                author = author,
                drawAuthor = BookCover.drawBookAuthor,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
