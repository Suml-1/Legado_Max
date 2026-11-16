package io.legado.app.help.config

enum class BookshelfFloatingDockSearchPosition(val value: Int) {
    LEFT(0),
    RIGHT(1);

    companion object {
        fun fromValue(value: Int): BookshelfFloatingDockSearchPosition {
            return entries.firstOrNull { it.value == value } ?: LEFT
        }
    }
}
