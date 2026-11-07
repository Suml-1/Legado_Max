package io.legado.app.ui.config.theme.application.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ApplicationThemeManager
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.composeActionShape

/**
 * 应用主题的配色预览：以当前日夜对应的主题底色铺满，主色块 + 两条主色占位条。
 *
 * 只取颜色，不加载背景图——列表里的预览尺寸只有 44dp，缩略图带来的辨识度提升有限，
 * 却需要在每个条目上做一次图片解码。
 *
 * @param config 应用主题配置
 * @param modifier Modifier
 */
@Composable
fun ApplicationThemePreview(
    config: ApplicationThemeManager.Config,
    modifier: Modifier = Modifier,
) {
    val isNight = AppConfig.isNightTheme
    val theme = if (isNight) config.nightTheme else config.dayTheme
    val background = parseThemeColor(theme?.backgroundColor, MaterialTheme.colorScheme.surface)
    val primary = parseThemeColor(theme?.primaryColor, MaterialTheme.colorScheme.primary)
    Box(
        modifier = modifier
            .size(AppDimens.managePreviewBoxSize)
            .clip(composeActionShape())
            .background(background)
            .border(AppDimens.manageCardBorderWidth, MaterialTheme.colorScheme.outlineVariant, composeActionShape()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.manageBadgePaddingHorizontal),
        ) {
            Box(
                modifier = Modifier
                    .size(AppDimens.panelRowIconSize / 2)
                    .clip(composeActionShape())
                    .background(primary),
            )
            Spacer(Modifier.height(AppDimens.manageCardTitleSpacing))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppDimens.manageBadgePaddingVertical + AppDimens.manageCardTitleSpacing)
                    .clip(composeActionShape())
                    .background(primary.copy(alpha = 0.7f)),
            )
            Spacer(Modifier.height(AppDimens.manageBadgePaddingVertical + AppDimens.manageBadgePaddingVertical))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(AppDimens.manageBadgePaddingVertical + AppDimens.manageCardTitleSpacing)
                    .clip(composeActionShape())
                    .background(primary.copy(alpha = 0.5f)),
            )
        }
    }
}

private fun parseThemeColor(value: String?, fallback: Color): Color {
    val normalized = value?.takeIf { it.isNotBlank() }
        ?.let { if (it.startsWith("#")) it else "#$it" }
        ?: return fallback
    return runCatching { Color(normalized.toColorInt()) }.getOrDefault(fallback)
}
