package io.legado.app.help.config

import kotlin.math.roundToInt

object BookshelfFloatingDockConfig {

    const val AUTOMATIC_TOP_DISTANCE_PX = -1
    const val DEFAULT_TOP_DISTANCE_PX = 40
    const val MIN_TOP_DISTANCE_PX = 0
    const val MAX_TOP_DISTANCE_PX = 500
    const val TOP_DISTANCE_STEP_PX = 5
    const val TOP_DISTANCE_SLIDER_STEPS =
        (MAX_TOP_DISTANCE_PX - MIN_TOP_DISTANCE_PX) / TOP_DISTANCE_STEP_PX - 1
    const val DEFAULT_TRANSPARENCY_PERCENT = 40
    const val MIN_TRANSPARENCY_PERCENT = 0
    const val MAX_TRANSPARENCY_PERCENT = 100

    private const val HERO_HEIGHT_WIDTH_RATIO = 0.38f
    private const val HERO_HEIGHT_STEP_DP = 5
    private const val MIN_HERO_HEIGHT_DP = 130
    private const val MAX_HERO_HEIGHT_DP = 195
    private const val DOCK_TOP_GAP_DP = 8

    /** 返回状态栏下方的可见间距，单位为设备物理像素。 */
    fun resolveTopDistancePx(
        storedDistancePx: Int,
        screenWidthPx: Int,
        density: Float,
        @Suppress("UNUSED_PARAMETER") statusBarHeightPx: Int
    ): Int {
        if (storedDistancePx != AUTOMATIC_TOP_DISTANCE_PX) {
            return normalizeTopDistancePx(storedDistancePx)
        }
        val safeDensity = density.takeIf { it > 0f } ?: 1f
        val widthDp = screenWidthPx / safeDensity
        val heroHeightDp = (
            widthDp * HERO_HEIGHT_WIDTH_RATIO / HERO_HEIGHT_STEP_DP
        ).roundToInt()
            .times(HERO_HEIGHT_STEP_DP)
            .coerceIn(MIN_HERO_HEIGHT_DP, MAX_HERO_HEIGHT_DP)
        val automaticDistancePx = (
            (heroHeightDp + DOCK_TOP_GAP_DP) * safeDensity
        ).roundToInt()
        return normalizeTopDistancePx(automaticDistancePx)
    }

    fun normalizeTopDistancePx(value: Int): Int {
        val clamped = value.coerceIn(MIN_TOP_DISTANCE_PX, MAX_TOP_DISTANCE_PX)
        return (
            (clamped - MIN_TOP_DISTANCE_PX).toFloat() / TOP_DISTANCE_STEP_PX
        ).roundToInt() * TOP_DISTANCE_STEP_PX + MIN_TOP_DISTANCE_PX
    }

    fun screenTopDistancePx(topGapPx: Int, statusBarHeightPx: Int): Int {
        return statusBarHeightPx.coerceAtLeast(0) + normalizeTopDistancePx(topGapPx)
    }

    fun normalizeTransparencyPercent(value: Int): Int {
        return value.coerceIn(MIN_TRANSPARENCY_PERCENT, MAX_TRANSPARENCY_PERCENT)
    }

    fun surfaceAlpha(transparencyPercent: Int): Float {
        return 1f - normalizeTransparencyPercent(transparencyPercent) / 100f
    }
}
