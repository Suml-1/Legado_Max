package io.legado.app.help.config

enum class BookshelfTopBarStyle(val value: Int) {
    COMPACT_TOOLBAR(0),
    GROUP_NAVIGATION(1);

    companion object {
        val DEFAULT = GROUP_NAVIGATION

        fun fromValue(value: Int): BookshelfTopBarStyle {
            return entries.firstOrNull { it.value == value } ?: COMPACT_TOOLBAR
        }

        fun resolveForLayout(
            configuredStyle: BookshelfTopBarStyle,
            groupGridMode: Boolean,
        ): BookshelfTopBarStyle {
            return if (groupGridMode) COMPACT_TOOLBAR else configuredStyle
        }
    }
}
