package io.legado.app.help.config

import io.legado.app.ui.design.theme.NgColorGenerationMode
import io.legado.app.ui.design.theme.NgColorSpec
import io.legado.app.ui.design.theme.NgColorSystem
import io.legado.app.ui.design.theme.NgContrastLevel
import io.legado.app.ui.design.theme.NgManualColorSet
import io.legado.app.ui.design.theme.NgPaletteStyle
import io.legado.app.ui.design.theme.NgTopBarTextMode

/** Asset-free built-in appearance. Content themes and their saved colors remain independent. */
internal object NgMulticolorTheme {

    private val light = NgManualColorSet(
        primary = 0xFF008F78.toInt(),
        secondary = 0xFFFFFFFF.toInt(),
        primaryText = 0xFF18282D.toInt(),
        secondaryText = 0xFF435C60.toInt(),
        background = 0xFFE4F5F2.toInt(),
        labelContainer = 0xFFF2FAF8.toInt(),
    )

    val colors = NgColorSystem(
        mode = NgColorGenerationMode.MANUAL,
        lightSeed = light.primary,
        darkSeed = light.primary,
        paletteStyle = NgPaletteStyle.TONAL_SPOT,
        contrast = NgContrastLevel.DEFAULT,
        colorSpec = NgColorSpec.MATERIAL_3_2021,
        manualLight = light,
        manualDark = light,
        lightTopBarTextMode = NgTopBarTextMode.DARK,
        darkTopBarTextMode = NgTopBarTextMode.DARK,
    )
}
