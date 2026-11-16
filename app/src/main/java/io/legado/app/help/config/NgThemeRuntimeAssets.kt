package io.legado.app.help.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.TextView
import java.io.File

internal data class NgThemeNavigationIcons(
    private val bookshelfBitmap: Bitmap,
    private val exploreBitmap: Bitmap,
    private val rssBitmap: Bitmap,
    private val myBitmap: Bitmap,
    private val homeBitmap: Bitmap?,
) {
    fun home(context: Context): Drawable? = homeBitmap?.asDrawable(context)
    fun bookshelf(context: Context): Drawable = bookshelfBitmap.asDrawable(context)
    fun explore(context: Context): Drawable = exploreBitmap.asDrawable(context)
    fun rss(context: Context): Drawable = rssBitmap.asDrawable(context)
    fun my(context: Context): Drawable = myBitmap.asDrawable(context)

    private fun Bitmap.asDrawable(context: Context): Drawable =
        BitmapDrawable(context.resources, this).apply { isFilterBitmap = true }
}

/**
 * 运行时只消费 NG 已经定义清楚的主题包资源。
 *
 * 原有四项导航图标必须齐全且都能解码才整体启用；首页图标独立可选。
 */
internal object NgThemeRuntimeAssets {

    private const val CACHE_SIZE_KB = 4 * 1024
    private const val NAVIGATION_DECODE_SIZE_DP = 48
    private val bitmapCache = object : LruCache<String, Bitmap>(CACHE_SIZE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val typefaceCache = LruCache<String, Typeface>(4)
    private val invalidTypefaceKeys = hashSetOf<String>()

    fun navigationIcons(context: Context): NgThemeNavigationIcons? {
        val theme = NgThemeLibraryStore.activeTheme(context) ?: return null
        val navigation = theme.resourceProfile?.navigation ?: return null
        val targetSize = (NAVIGATION_DECODE_SIZE_DP * context.resources.displayMetrics.density)
            .toInt()
            .coerceAtLeast(NAVIGATION_DECODE_SIZE_DP)
        val bookshelf = loadBitmap(theme.resolvePackageAsset(navigation.bookshelf), targetSize)
            ?: return null
        val explore = loadBitmap(theme.resolvePackageAsset(navigation.explore), targetSize)
            ?: return null
        val rss = loadBitmap(theme.resolvePackageAsset(navigation.rss), targetSize)
            ?: return null
        val my = loadBitmap(theme.resolvePackageAsset(navigation.my), targetSize)
            ?: return null
        val home = loadBitmap(theme.resolvePackageAsset(navigation.home), targetSize)
        return NgThemeNavigationIcons(bookshelf, explore, rss, my, home)
    }

    fun appTypeface(context: Context): Typeface? {
        if (NgInterfaceFontStore.hasOverride(context)) return NgInterfaceFontStore.typeface(context)
        val theme = NgThemeLibraryStore.activeTheme(context) ?: return null
        val file = theme.resolvePackageAsset(theme.resourceProfile?.appFont) ?: return null
        val cacheKey = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
        synchronized(typefaceCache) {
            typefaceCache.get(cacheKey)?.let { return it }
            if (cacheKey in invalidTypefaceKeys) return null
            return runCatching { Typeface.createFromFile(file) }
                .getOrNull()
                ?.also { typefaceCache.put(cacheKey, it) }
                ?: run {
                    invalidTypefaceKeys += cacheKey
                    null
                }
        }
    }

    fun applyAppTypeface(context: Context, view: TextView) {
        val style = view.typeface?.style ?: Typeface.NORMAL
        val typeface = appTypeface(context) ?: return
        view.setTypeface(typeface, style)
    }

    /** Toolbar creates its title views internally, outside the XML inflater. */
    fun applyToolbarTypeface(toolbar: androidx.appcompat.widget.Toolbar) {
        for (index in 0 until toolbar.childCount) {
            (toolbar.getChildAt(index) as? TextView)?.let {
                applyAppTypeface(toolbar.context, it)
            }
        }
    }

    private fun loadBitmap(file: File?, targetSize: Int): Bitmap? {
        file ?: return null
        val cacheKey = "${file.absolutePath}:${file.lastModified()}:${file.length()}:$targetSize"
        bitmapCache.get(cacheKey)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (
            bounds.outWidth / (sampleSize * 2) >= targetSize &&
            bounds.outHeight / (sampleSize * 2) >= targetSize
        ) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inScaled = false
            },
        ) ?: return null
        bitmapCache.put(cacheKey, bitmap)
        return bitmap
    }
}
