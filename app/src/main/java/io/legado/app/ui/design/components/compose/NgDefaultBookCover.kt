package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

/** 听书播放器与紧凑书籍列表共用的无封面回退。 */
@Composable
fun NgDefaultBookCover(
    title: String,
    author: String,
    modifier: Modifier = Modifier,
    baseArtwork: ImageBitmap? = null,
    compact: Boolean = false,
    coverContentDescription: String? = null,
) {
    val semanticModifier = if (coverContentDescription == null) {
        modifier
    } else {
        modifier.semantics {
            contentDescription = coverContentDescription
        }
    }
    Box(
        modifier = semanticModifier
            .fillMaxSize()
            .background(Color(0xFFEADCC2)),
        contentAlignment = Alignment.Center,
    ) {
        if (baseArtwork != null) {
            Image(
                bitmap = baseArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.82f),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.image_cover_default),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.82f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFD8B98A).copy(alpha = 0.16f)),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 4.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = Color(0xFF2B251F),
                fontSize = if (compact) 6.sp else 17.sp,
                lineHeight = if (compact) 7.sp else 23.sp,
                fontFamily = NgTheme.fontFamily,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 3 else 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact && author.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .padding(top = 14.dp, bottom = 10.dp)
                        .width(42.dp)
                        .height(1.dp)
                        .background(Color(0xFF6F6253).copy(alpha = 0.42f)),
                )
                Text(
                    text = author,
                    color = Color(0xFF4A4036),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontFamily = NgTheme.fontFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
