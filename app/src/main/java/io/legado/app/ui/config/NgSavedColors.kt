package io.legado.app.ui.config

import android.content.Context
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

/** Ordered, exact ARGB favorites shared by all NG color picker hosts. */
internal object NgSavedColors {

    private const val preferenceKey = "ngColorPickerSavedColors.v1"
    private const val limit = 7

    enum class AddResult {
        ADDED,
        ALREADY_SAVED,
        FULL,
    }

    fun load(context: Context): List<Int> = decode(context.getPrefString(preferenceKey))

    @Synchronized
    fun add(context: Context, color: Int): AddResult {
        val colors = load(context)
        val result = addResult(colors, color)
        if (result == AddResult.ADDED) {
            context.putPrefString(preferenceKey, encode(colors + color))
        }
        return result
    }

    @Synchronized
    fun remove(context: Context, color: Int): List<Int> {
        val colors = load(context)
        val remaining = colors.filterNot { it == color }
        if (remaining.size != colors.size) {
            context.putPrefString(preferenceKey, encode(remaining))
        }
        return remaining
    }

    internal fun addResult(colors: List<Int>, color: Int): AddResult = when {
        color in colors -> AddResult.ALREADY_SAVED
        colors.size >= limit -> AddResult.FULL
        else -> AddResult.ADDED
    }

    // A string list preserves insertion order; StringSet does not promise an order after restart.
    internal fun encode(colors: List<Int>): String = colors.distinct().take(limit).joinToString(",")

    internal fun decode(encoded: String?): List<Int> = encoded.orEmpty().split(',')
        .mapNotNull { it.toIntOrNull() }
        .distinct()
        .take(limit)
}
