package io.legado.app.ui.config

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class NgColorPickerMode {
    GRID,
    SPECTRUM,
    RGB,
}

internal data class NgColorPickerHsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

/** Keeps the exact, unpremultiplied ARGB value separate from editing coordinates and drafts. */
@Stable
internal class NgColorPickerState(initialColor: Int, private val forceOpaque: Boolean = false) {

    private var selectedColor by mutableIntStateOf(enforceAlpha(initialColor))
    val color: Int get() = selectedColor

    private var hsv by mutableStateOf(NgColorPickerColors.rgbToHsv(color))

    private var selectedMode by mutableStateOf(NgColorPickerMode.GRID)
    val mode: NgColorPickerMode get() = selectedMode

    var hexInput by mutableStateOf(NgColorPickerColors.format(color))
        private set

    var rgbInputs by mutableStateOf(channelInputs(color))
        private set

    val alpha: Int get() = color ushr 24
    val red: Int get() = color ushr 16 and 0xFF
    val green: Int get() = color ushr 8 and 0xFF
    val blue: Int get() = color and 0xFF
    val hue: Float get() = hsv.hue
    val saturation: Float get() = hsv.saturation
    val value: Float get() = hsv.value
    val isHexInputError: Boolean get() = NgColorPickerColors.parse(hexInput) == null
    val rgbInputErrors: List<Boolean> get() = rgbInputs.map { parseChannel(it) == null }
    val isInputValid: Boolean get() = !isHexInputError && rgbInputs.all { parseChannel(it) != null }

    /** A callback echo must not erase partially typed text or the hue of gray/black. */
    fun syncColor(color: Int) {
        if (enforceAlpha(color) != this.color) setColor(color)
    }

    /** Explicit selections also discard unfinished input when the selected color is unchanged. */
    fun setColor(color: Int) {
        val next = enforceAlpha(color)
        selectedColor = next
        hsv = NgColorPickerColors.rgbToHsv(next, hsv)
        refreshInputs()
    }

    /** Returns whether an incomplete RGB draft was discarded when leaving its mode. */
    fun setMode(mode: NgColorPickerMode): Boolean {
        val discarded = this.mode != mode && rgbInputs.any { parseChannel(it) == null }
        if (discarded) rgbInputs = channelInputs(color)
        selectedMode = mode
        return discarded
    }

    fun setHue(hue: Float) {
        applyHsv(hsv.copy(hue = hue.finiteIn(360f)))
    }

    fun setSaturationValue(saturation: Float, value: Float) {
        applyHsv(hsv.copy(saturation = saturation.finiteIn(1f), value = value.finiteIn(1f)))
    }

    fun setRgbChannel(channel: Int, byte: Int) {
        require(channel in 0..2)
        val shift = (2 - channel) * 8
        setColor((color and (0xFF shl shift).inv()) or (byte.coerceIn(0, 255) shl shift))
    }

    fun editRgbChannel(channel: Int, text: String) {
        require(channel in 0..2)
        rgbInputs = rgbInputs.toMutableList().also { it[channel] = text }
        val channels = rgbInputs.map { parseChannel(it) }
        if (channels.any { it == null }) return
        selectedColor = enforceAlpha(
            (alpha shl 24) or (requireNotNull(channels[0]) shl 16) or
                (requireNotNull(channels[1]) shl 8) or requireNotNull(channels[2])
        )
        hsv = NgColorPickerColors.rgbToHsv(color, hsv)
        hexInput = NgColorPickerColors.format(color)
    }

    fun editHex(text: String) {
        hexInput = text.trim().uppercase()
        val parsed = NgColorPickerColors.parse(hexInput) ?: return
        selectedColor = enforceAlpha(parsed)
        hsv = NgColorPickerColors.rgbToHsv(color, hsv)
        rgbInputs = channelInputs(color)
        if (color != parsed) hexInput = NgColorPickerColors.format(color)
    }

    fun setAlpha(alpha: Int) {
        selectedColor = enforceAlpha((color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24))
        refreshInputs()
    }

    fun reset(color: Int) = setColor(color)

    /** A screen pixel supplies RGB only; the editing session continues to own opacity. */
    fun sampleRgb(color: Int) = setColor((color and 0x00FFFFFF) or (alpha shl 24))

    private fun applyHsv(hsv: NgColorPickerHsv) {
        this.hsv = hsv
        selectedColor = enforceAlpha(
            NgColorPickerColors.hsvToColor(hsv.hue, hsv.saturation, hsv.value, alpha)
        )
        refreshInputs()
    }

    private fun refreshInputs() {
        hexInput = NgColorPickerColors.format(color)
        rgbInputs = channelInputs(color)
    }

    private fun enforceAlpha(color: Int): Int = if (forceOpaque) color or (0xFF shl 24) else color

    private fun channelInputs(color: Int): List<String> = listOf(
        (color ushr 16 and 0xFF).toString(),
        (color ushr 8 and 0xFF).toString(),
        (color and 0xFF).toString(),
    )

    private fun parseChannel(text: String): Int? = text.takeIf {
        it.length in 1..3 && it.all { char -> char in '0'..'9' }
    }?.toIntOrNull()?.takeIf { it <= 255 }
}

/** Color arithmetic deliberately avoids android.graphics.Color so it can be checked on the JVM. */
internal object NgColorPickerColors {

    fun format(color: Int): String = "#" + color.toUInt().toString(16).padStart(8, '0').uppercase()

    fun parse(text: String): Int? {
        val raw = text.trim().removePrefix("#")
        if (raw.length != 6 && raw.length != 8) return null
        if (raw.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        val argb = if (raw.length == 6) "FF$raw" else raw
        return argb.toLongOrNull(16)?.toInt()
    }

    fun rgbToHsv(color: Int, previous: NgColorPickerHsv? = null): NgColorPickerHsv {
        val red = (color ushr 16 and 0xFF) / 255f
        val green = (color ushr 8 and 0xFF) / 255f
        val blue = (color and 0xFF) / 255f
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        val delta = max - min
        val previousHue = (previous?.hue ?: 0f).finiteIn(360f)
        if (max == 0f) {
            return NgColorPickerHsv(previousHue, (previous?.saturation ?: 0f).finiteIn(1f), 0f)
        }
        if (delta == 0f) return NgColorPickerHsv(previousHue, 0f, max)
        val sector = when (max) {
            red -> (green - blue) / delta
            green -> (blue - red) / delta + 2f
            else -> (red - green) / delta + 4f
        }
        return NgColorPickerHsv((sector * 60f + 360f) % 360f, delta / max, max)
    }

    fun hsvToColor(hue: Float, saturation: Float, value: Float, alpha: Int = 255): Int {
        val h = hue.finiteIn(360f).toDouble() / 60.0
        val s = saturation.finiteIn(1f).toDouble()
        val v = value.finiteIn(1f).toDouble()
        val chroma = v * s
        val x = chroma * (1.0 - abs(h % 2.0 - 1.0))
        val m = v - chroma
        val (r, g, b) = when {
            h < 1.0 -> Triple(chroma, x, 0.0)
            h < 2.0 -> Triple(x, chroma, 0.0)
            h < 3.0 -> Triple(0.0, chroma, x)
            h < 4.0 -> Triple(0.0, x, chroma)
            h < 5.0 -> Triple(x, 0.0, chroma)
            else -> Triple(chroma, 0.0, x)
        }
        fun byte(channel: Double) = ((channel + m) * 255).roundToInt().coerceIn(0, 255)
        return (alpha.coerceIn(0, 255) shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
    }

    /** Row-major 12 x 10 grid, matching the approved prototype. */
    fun gridColors(): List<Int> = grid

    private val grid: List<Int> by lazy {
        val hues = listOf(190f, 215f, 250f, 275f, 310f, 350f, 15f, 35f, 50f, 65f, 90f, 120f)
        val rows = listOf(
            1f to 0.28f, 1f to 0.46f, 1f to 0.64f, 1f to 0.82f, 1f to 1f,
            0.8f to 1f, 0.6f to 1f, 0.4f to 1f, 0.2f to 1f,
        )
        buildList {
            repeat(12) { index ->
                val gray = (255.0 * (1.0 - index / 11.0)).roundToInt()
                add((0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray)
            }
            rows.forEach { (saturation, value) ->
                hues.forEach { hue -> add(hsvToColor(hue, saturation, value)) }
            }
        }
    }
}

private fun Float.finiteIn(max: Float): Float = if (isFinite()) coerceIn(0f, max) else 0f
