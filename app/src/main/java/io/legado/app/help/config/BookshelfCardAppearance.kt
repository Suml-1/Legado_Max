package io.legado.app.help.config

import androidx.core.content.edit
import io.legado.app.utils.defaultSharedPreferences
import splitties.init.appCtx

enum class BookshelfCardMaterial(val value: Int) {
    SOLID(0), TRANSPARENT(1), LIQUID(2);

    companion object {
        fun fromValue(value: Int) = entries.first { it.value == value }
    }
}

/** Immutable UI settings; persisted as individual primitive preferences, not serialized. */
data class BookshelfCardStyle(
    val material: BookshelfCardMaterial,
    val transparentPercent: Int = 40,
    val liquidPercent: Int = 40,
) {
    val transparency: Int
        get() = if (material == BookshelfCardMaterial.LIQUID) liquidPercent else transparentPercent

    fun withTransparency(value: Int): BookshelfCardStyle =
        if (material == BookshelfCardMaterial.LIQUID) copy(liquidPercent = value.coerceIn(0, 100))
        else copy(transparentPercent = value.coerceIn(0, 100))
}

data class BookshelfCardAppearance(
    val day: BookshelfCardStyle = BookshelfCardStyle(BookshelfCardMaterial.LIQUID),
    val night: BookshelfCardStyle = BookshelfCardStyle(BookshelfCardMaterial.LIQUID),
) {
    fun forNight(isNight: Boolean) = if (isNight) night else day
    fun updated(isNight: Boolean, style: BookshelfCardStyle) =
        if (isNight) copy(night = style) else copy(day = style)
}

/** Shared by LIST and COMPACT. Day/night and both glass transparency values are independent. */
object BookshelfCardAppearanceStore {
    fun read(): BookshelfCardAppearance {
        val prefs = appCtx.defaultSharedPreferences
        fun readStyle(mode: String, default: BookshelfCardMaterial) = BookshelfCardStyle(
            material = BookshelfCardMaterial.fromValue(prefs.getInt("bookshelfCard_${mode}_material", default.value)),
            transparentPercent = prefs.getInt("bookshelfCard_${mode}_transparent", 40).coerceIn(0, 100),
            liquidPercent = prefs.getInt("bookshelfCard_${mode}_liquid", 40).coerceIn(0, 100),
        )
        return BookshelfCardAppearance(
            readStyle("day", BookshelfCardMaterial.LIQUID),
            readStyle("night", BookshelfCardMaterial.LIQUID),
        )
    }

    fun save(value: BookshelfCardAppearance) {
        appCtx.defaultSharedPreferences.edit {
            listOf("day" to value.day, "night" to value.night).forEach { (mode, style) ->
                putInt("bookshelfCard_${mode}_material", style.material.value)
                putInt("bookshelfCard_${mode}_transparent", style.transparentPercent.coerceIn(0, 100))
                putInt("bookshelfCard_${mode}_liquid", style.liquidPercent.coerceIn(0, 100))
            }
        }
    }
}
