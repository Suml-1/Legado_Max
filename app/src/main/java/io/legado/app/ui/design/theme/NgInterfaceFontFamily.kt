package io.legado.app.ui.design.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** System families retain all platform weights; files use Compose's single-font synthesis. */
internal fun interfaceFontFamily(typeface: Typeface): FontFamily = when (typeface) {
    Typeface.DEFAULT -> FontFamily.Default
    Typeface.SERIF -> FontFamily.Serif
    Typeface.MONOSPACE -> FontFamily.Monospace
    else -> FontFamily(NgPreloadedInterfaceFont(typeface))
}

private class NgPreloadedInterfaceFont(val typeface: Typeface) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = PreloadedTypefaceLoader,
    variationSettings = FontVariation.Settings(),
) {
    override val weight: FontWeight = FontWeight.Normal
    override val style: FontStyle = FontStyle.Normal

    override fun equals(other: Any?): Boolean =
        other is NgPreloadedInterfaceFont && typeface == other.typeface

    override fun hashCode(): Int = typeface.hashCode()
}

private object PreloadedTypefaceLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface =
        (font as NgPreloadedInterfaceFont).typeface

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
        loadBlocking(context, font)
}
