package io.legado.app.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.legado.app.R
import io.legado.app.help.config.NgThemeDrawerProfile
import io.legado.app.ui.design.components.compose.NgChoiceCard
import io.legado.app.ui.design.components.compose.NgChoiceCardVariant
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormNavigationRow
import io.legado.app.ui.design.components.compose.NgFormNavigationRowVariant
import io.legado.app.ui.design.theme.NgColorSystem
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.design.theme.formatNgColor

/** The owner decides whether changes persist now or remain in a theme draft. */
@Composable
internal fun NgDrawerBackgroundSettingsContent(
    profile: NgThemeDrawerProfile?,
    colors: NgColorSystem,
    onProfileChanged: (NgThemeDrawerProfile) -> Unit,
    onProfileChangeFinished: () -> Unit,
    onSelectImage: (Boolean) -> Unit,
) {
    val value = profile ?: NgThemeDrawerProfile()
    var colorNight by remember { mutableStateOf<Boolean?>(null) }
    val sources = listOf("theme_color", "custom_color", "theme_image", "custom_image")
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sources.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { source ->
                    NgChoiceCard(
                        title = ngDrawerBackgroundSourceName(source),
                        selected = value.source == source,
                        onClick = {
                            if (value.source != source) {
                                colorNight = null
                                onProfileChanged(value.copy(source = source))
                                onProfileChangeFinished()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        variant = NgChoiceCardVariant.TEXT,
                    )
                }
            }
        }
        if (value.source == "custom_color" || value.source == "custom_image") {
            NgFormGroupDivider()
            Column {
                listOf(false, true).forEachIndexed { index, night ->
                    if (index > 0) NgFormGroupDivider()
                    if (value.source == "custom_color") {
                        DrawerBackgroundColorRow(
                            night = night,
                            color = value.forNight(night).backgroundColor,
                            onClick = { colorNight = night },
                        )
                    } else {
                        DrawerBackgroundImageRow(
                            night = night,
                            imagePath = value.forNight(night).imagePath,
                            onClick = { onSelectImage(night) },
                        )
                    }
                }
            }
        }
    }
    colorNight?.let { night ->
        val context = LocalContext.current
        val initialColor = value.forNight(night).backgroundColor ?: remember(context, colors, night) {
            NgThemeResolver.resolve(context, colors, night).colors.background
        }
        NgColorPickerSheet(
            show = true,
            initialColor = initialColor,
            resetColor = null,
            showAlphaSlider = false,
            onDismissRequest = { colorNight = null },
            onSelectionConfirmed = { color, _ ->
                colorNight = null
                onProfileChanged(value.updated(
                    night, value.forNight(night).copy(backgroundColor = color),
                ).copy(source = "custom_color"))
                onProfileChangeFinished()
            },
        )
    }
}

@Composable
internal fun ngDrawerBackgroundSourceName(source: String): String = stringResource(
    when (source) {
        "custom_color" -> R.string.ng_drawer_background_custom_color
        "theme_image" -> R.string.ng_drawer_background_theme_image
        "custom_image" -> R.string.ng_drawer_background_custom_image
        else -> R.string.ng_drawer_background_theme_color
    }
)

@Composable
private fun DrawerBackgroundColorRow(night: Boolean, color: Int?, onClick: () -> Unit) {
    NgFormNavigationRow(
        title = stringResource(
            if (night) R.string.ng_drawer_background_night_color else R.string.ng_drawer_background_day_color
        ),
        value = color?.let(::formatNgColor) ?: stringResource(R.string.ng_drawer_background_choose),
        onClick = onClick,
        arrowIcon = painterResource(R.drawable.ic_chevron_right_20),
        valueContent = if (color == null) null else {
            {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color(color), RoundedCornerShape(5.dp))
                        .border(
                            0.6.dp,
                            Color(NgTheme.colors.outlineVariant).copy(alpha = 0.5f),
                            RoundedCornerShape(5.dp),
                        ),
                )
            }
        },
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun DrawerBackgroundImageRow(night: Boolean, imagePath: String?, onClick: () -> Unit) {
    val hasImage = !imagePath.isNullOrBlank()
    NgFormNavigationRow(
        title = stringResource(
            if (night) R.string.ng_drawer_background_night_image else R.string.ng_drawer_background_day_image
        ),
        value = stringResource(
            if (hasImage) R.string.ng_drawer_background_change else R.string.ng_drawer_background_choose
        ),
        onClick = onClick,
        arrowIcon = painterResource(R.drawable.ic_chevron_right_20),
        variant = NgFormNavigationRowVariant.MEDIA,
        valueContent = if (!hasImage) null else {
            {
                val path = requireNotNull(imagePath)
                val model = if (path.startsWith("asset://")) {
                    "file:///android_asset/" + path.removePrefix("asset://").removePrefix("/")
                } else path
                GlideImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 88.dp, height = 60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(NgTheme.colors.surfaceContainerHigh)),
                )
            }
        },
    )
}
