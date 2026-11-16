package io.legado.app.ui.design.components.compose

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.NgThemeDrawerProfile
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.config.resolveBundledBackgroundAssetPath
import io.legado.app.ui.design.theme.NgDrawerPalette
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.defaultSharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class NgDrawerImageSource(val path: String, val cacheKey: String)

/** Resolve files off Main; only theme-image drawers observe global wallpaper changes. */
@Composable
internal fun rememberNgDrawerImageSource(profile: NgThemeDrawerProfile?): NgDrawerImageSource? {
    val snapshot = NgTheme.snapshot
    val source = profile?.source
    if (snapshot.isEInk || source !in listOf("theme_image", "custom_image")) return null
    val context = LocalContext.current.applicationContext
    val customPath = profile?.forNight(snapshot.isDark)?.imagePath
    var revision by remember(context) { mutableIntStateOf(0) }
    DisposableEffect(context, source) {
        val prefs = context.defaultSharedPreferences
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in WallpaperPreferenceKeys) revision++
        }
        if (source == "theme_image") prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { if (source == "theme_image") prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val resolved by produceState<NgDrawerImageSource?>(
        null, context, source, customPath, snapshot.isDark, revision,
    ) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                val path = if (source == "theme_image") {
                    ThemeConfig.drawerBackgroundImagePath(context, snapshot.isDark)
                } else customPath
                resolveDrawerImageSource(context, path)
            }.getOrNull()
        }
    }
    return resolved
}

private val WallpaperPreferenceKeys = setOf(
    PreferKey.bgImage,
    PreferKey.bgImageN,
    PreferKey.themeMode,
    PreferKey.ngThemePresentationMode,
    PreferKey.ngCustomAppearanceEdited,
    "ngActiveManagedThemeId.v1",
)

/** Called only from IO; Glide owns the later bounded decoding and bitmap cache. */
private fun resolveDrawerImageSource(context: Context, path: String?): NgDrawerImageSource? {
    if (path.isNullOrBlank()) return null
    val source = if (path.startsWith("asset://")) {
        "file:///android_asset/" + resolveBundledBackgroundAssetPath(path.removePrefix("asset://"))
    } else path
    if (source.startsWith("file:///android_asset/")) {
        context.assets.open(source.removePrefix("file:///android_asset/")).use { }
        return NgDrawerImageSource(source, "$source:${File(context.applicationInfo.sourceDir).lastModified()}")
    }
    if (source.startsWith("content:")) return NgDrawerImageSource(source, source)
    // Theme URL backgrounds are resolved to the app's existing local cache, never fetched here.
    if (source.startsWith("http")) return null
    val file = File(if (source.startsWith("file:")) Uri.parse(source).path ?: return null else source)
    if (!file.isFile) return null
    return NgDrawerImageSource(file.absolutePath, "${file.absolutePath}:${file.lastModified()}:${file.length()}")
}

/** The same fixed, non-interactive crop is rendered after every material backend. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun NgDrawerBackdrop(
    imagePath: String,
    cacheKey: String,
    modifier: Modifier = Modifier,
    onImageReadyChanged: (Boolean) -> Unit = {},
) {
    val ready = remember(cacheKey) { mutableStateOf(false) }
    val currentOnImageReadyChanged by rememberUpdatedState(onImageReadyChanged)
    LaunchedEffect(cacheKey, ready.value) {
        currentOnImageReadyChanged(ready.value)
    }
    val listener = remember(cacheKey) {
        object : RequestListener<Drawable> {
            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>,
                isFirstResource: Boolean): Boolean {
                ready.value = false
                return false
            }

            override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?,
                dataSource: DataSource, isFirstResource: Boolean): Boolean {
                ready.value = true
                return false
            }
        }
    }
    val snapshot = NgTheme.snapshot
    val mask = remember(snapshot) { NgDrawerPalette.resolveImageMask(snapshot) }
    Box(modifier) {
        GlideImage(
            model = imagePath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(if (ready.value) 1f else 0f),
        ) { request -> request.signature(ObjectKey(cacheKey)).dontAnimate().listener(listener) }
        if (ready.value) {
            // A neutral veil retains the image without tinting it with the primary/HCT hue.
            Box(Modifier.matchParentSize().background(
                Color(mask.color).copy(alpha = mask.alpha)
            ))
        }
    }
}
