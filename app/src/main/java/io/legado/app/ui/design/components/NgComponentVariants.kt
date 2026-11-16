package io.legado.app.ui.design.components

enum class NgSurfaceVariant {
    CANVAS,
    CARD,
    PANEL,
    OVERLAY,
    BORDERLESS_CARD,
}

enum class NgButtonVariant {
    PRIMARY,
    PRIMARY_LIGHT_CONTENT,
    TONAL,
    NEUTRAL,
    OUTLINE,
    DANGER,
    ON_IMAGE
}

enum class NgButtonShapeVariant {
    PILL,
    ROUNDED,
    SMALL_ROUNDED,
    COMPACT_ROUNDED,
}

enum class NgSettingsTrailing {
    NONE,
    CHEVRON,
    SWITCH,
    VALUE,
    CUSTOM
}

enum class NgDialogVariant {
    STANDARD,
    CONFIRMATION,
    COMPACT_CONFIRMATION,
    CLASSIC_CONFIRMATION,
    EDITOR,
    FORM_EDITOR,
    LONG_CONTENT,
    SIMPLE_CONFIRMATION,
}

enum class NgStatusTagVariant {
    PRIMARY,
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    NEUTRAL
}

enum class NgStatusTagStyle {
    REGULAR,
    COMPACT,
    TTS_ROLE,
    INLINE
}

enum class NgManagementTrailing {
    NONE,
    DRAG,
    MORE
}

enum class NgManagementListCardVariant {
    DEFAULT,
    COMPACT_GRID,
    MULTILINE_SUMMARY,
    PROVIDER_TWO_ROW,
}

enum class NgFilterChipGroupVariant {
    WRAP,
    EVEN_ROW,
    TWO_ROW_RAIL
}

data class NgStatusTagSpec(
    val text: CharSequence,
    val variant: NgStatusTagVariant,
    val style: NgStatusTagStyle = NgStatusTagStyle.REGULAR
)
