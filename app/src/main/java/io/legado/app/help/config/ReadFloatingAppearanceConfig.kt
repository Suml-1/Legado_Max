package io.legado.app.help.config

import com.google.gson.annotations.SerializedName
import kotlin.math.pow

enum class ReadFloatingColorStyle(val storageValue: String) {
    @SerializedName("vibrant")
    VIBRANT("vibrant"),

    @SerializedName("expressive")
    EXPRESSIVE("expressive"),

    @SerializedName("rainbow")
    RAINBOW("rainbow"),

    @SerializedName("fruitSalad")
    FRUIT_SALAD("fruitSalad");

    companion object {
        fun fromStorage(value: String?): ReadFloatingColorStyle =
            entries.firstOrNull { it.storageValue == value } ?: VIBRANT
    }
}

internal data class EffectiveReadFloatingColor(
    val seed: Int,
    val followsApplication: Boolean,
    val colorStyle: ReadFloatingColorStyle,
)

internal fun resolveEffectiveReadFloatingColor(
    isEInk: Boolean,
    globallyFollowsApplication: Boolean,
    globalColorStyle: ReadFloatingColorStyle,
    presetSeed: Int,
    presetColorStyle: ReadFloatingColorStyle,
): EffectiveReadFloatingColor = when {
    isEInk -> EffectiveReadFloatingColor(
        seed = 0,
        followsApplication = false,
        colorStyle = presetColorStyle,
    )

    globallyFollowsApplication -> EffectiveReadFloatingColor(
        seed = 0,
        followsApplication = true,
        colorStyle = globalColorStyle,
    )

    else -> EffectiveReadFloatingColor(
        seed = presetSeed,
        followsApplication = false,
        colorStyle = presetColorStyle,
    )
}

object ReadFloatingAppearanceConfig {

    const val DEFAULT_TRANSPARENCY_PERCENT = 0
    const val DEFAULT_PRIMARY_STRENGTH_PERCENT = 50
    const val MINI_PLAYER_TRANSPARENCY_PERCENT = 20
    const val MIN_PERCENT = 0
    const val MAX_PERCENT = 100
    internal const val TRANSPARENCY_CURVE_PIVOT_PERCENT = 20

    fun normalizePercent(value: Int): Int = value.coerceIn(MIN_PERCENT, MAX_PERCENT)

    /** 0% 不透明，100% 完全透明；20% 之前使用缓入以降低低刻度的视觉突变。 */
    fun surfaceAlpha(transparencyPercent: Int): Float {
        val transparency = normalizePercent(transparencyPercent)
        val transparencyFraction = if (transparency <= TRANSPARENCY_CURVE_PIVOT_PERCENT) {
            val progress = transparency / TRANSPARENCY_CURVE_PIVOT_PERCENT.toFloat()
            TRANSPARENCY_CURVE_PIVOT_PERCENT / 100f * progress * progress
        } else {
            transparency / 100f
        }
        return 1f - transparencyFraction
    }

    /**
     * 保留 20% 透明度下既有玻璃渐变，同时保证 0% 时渐变两端都严格不透明。
     * [defaultAlpha] 是该渐变端点在 20% 透明度下的历史 alpha。
     */
    fun floatingSurfaceAlpha(
        transparencyPercent: Int,
        defaultAlpha: Float
    ): Float {
        val transparency = normalizePercent(transparencyPercent)
        val baseline = defaultAlpha.coerceIn(0f, 1f)
        return if (transparency <= TRANSPARENCY_CURVE_PIVOT_PERCENT) {
            val linearProgress = transparency / TRANSPARENCY_CURVE_PIVOT_PERCENT.toFloat()
            val progress = linearProgress * linearProgress
            1f + (baseline - 1f) * progress
        } else {
            val remaining = (MAX_PERCENT - transparency).toFloat()
            val range = (MAX_PERCENT - TRANSPARENCY_CURVE_PIVOT_PERCENT).toFloat()
            baseline * remaining / range
        }.coerceIn(0f, 1f)
    }

    /**
     * 听书气泡原本使用更浓的主题色薄膜，需要更陡的感知曲线才能让默认值保持不变，
     * 同时让 0% 和 100% 仍分别对应清晰与完全透明。
     */
    fun miniPlayerSurfaceAlpha(transparencyPercent: Int): Float {
        return surfaceAlpha(transparencyPercent).pow(4.58f)
    }

    /** 听书胶囊比其它阅读浮窗更显色，按预设浓度的一半派生自身语义色。 */
    fun miniPlayerPrimaryStrengthPercent(primaryStrengthPercent: Int): Int =
        normalizePercent(primaryStrengthPercent) / 2

    /** 滑块在完整 0%～100% 区间内的位置，仅用于需要全程连续变化的参数。 */
    fun primaryStrengthFraction(primaryStrengthPercent: Int): Float =
        normalizePercent(primaryStrengthPercent) / MAX_PERCENT.toFloat()

    /**
     * 主色浓度以 50% 为原色：0% 完全弱化，50% 保持原色，100% 继续增强。
     * 增强上限使用 1.35 倍色度，避免高色度种子在 sRGB 色域中大面积失真。
     */
    fun primaryStrengthChromaScale(primaryStrengthPercent: Int): Float {
        val strength = normalizePercent(primaryStrengthPercent)
        return if (strength <= DEFAULT_PRIMARY_STRENGTH_PERCENT) {
            strength / DEFAULT_PRIMARY_STRENGTH_PERCENT.toFloat()
        } else {
            val progress = (strength - DEFAULT_PRIMARY_STRENGTH_PERCENT) /
                (MAX_PERCENT - DEFAULT_PRIMARY_STRENGTH_PERCENT).toFloat()
            1f + 0.35f * progress
        }
    }

    /** 0%～50% 的弱化进度；50% 及以上保持 1。 */
    fun primaryStrengthBaseProgress(primaryStrengthPercent: Int): Float =
        (normalizePercent(primaryStrengthPercent) /
            DEFAULT_PRIMARY_STRENGTH_PERCENT.toFloat()).coerceAtMost(1f)

    /** 50%～100% 的增强进度；50% 及以下保持 0。 */
    fun primaryStrengthEnhanceProgress(primaryStrengthPercent: Int): Float =
        ((normalizePercent(primaryStrengthPercent) - DEFAULT_PRIMARY_STRENGTH_PERCENT) /
            (MAX_PERCENT - DEFAULT_PRIMARY_STRENGTH_PERCENT).toFloat()).coerceIn(0f, 1f)
}
