package io.legado.app.help.config

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.utils.GSON
import java.security.MessageDigest

@Keep
internal data class NgEffectThemeOption(
    @SerializedName("value") val value: String = "",
    @SerializedName("label") val label: String = "",
)

@Keep
internal data class NgEffectThemeParameter(
    @SerializedName("key") val key: String = "",
    @SerializedName("label") val label: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("defaultValue") val defaultValue: String = "",
    @SerializedName("min") val min: Float? = null,
    @SerializedName("max") val max: Float? = null,
    @SerializedName("step") val step: Float? = null,
    @SerializedName("options") val options: List<NgEffectThemeOption> = emptyList(),
) {
    fun accepts(value: String): Boolean = value.length <= 256 && when (type) {
        "range" -> value.toFloatOrNull()?.let { it.isFinite() && it >= (min ?: 0f) && it <= (max ?: 100f) } == true
        "choice" -> options.any { it.value == value }
        "color" -> Regex("#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})").matches(value)
        "boolean" -> value == "true" || value == "false"
        else -> false
    }
}

@Keep
internal data class NgEffectThemeDescriptor(
    @SerializedName("apiVersion") val apiVersion: Int = 1,
    @SerializedName("entryClass") val entryClass: String = "",
    @SerializedName("targets") val targets: List<String> = emptyList(),
    @SerializedName("minGlesVersion") val minGlesVersion: Int = 0x30000,
    @SerializedName("lowRamAllowed") val lowRamAllowed: Boolean = true,
    @SerializedName("preview") val preview: String? = null,
    @SerializedName("playerPalette") val playerPalette: String = "cover",
    @SerializedName("parameterSchema") val parameterSchema: List<NgEffectThemeParameter> = emptyList(),
) {
    fun validate() {
        require(apiVersion == 1) { "特效主题接口不兼容" }
        require(Regex("(?:[A-Za-z_$][A-Za-z0-9_$]*\\.)+[A-Za-z_$][A-Za-z0-9_$]*").matches(entryClass)) { "特效主题入口无效" }
        require(targets.size == 1 && targets.single() in setOf("app", "player")) {
            "主题与听书特效必须分别打包，不支持双用途包"
        }
        require(playerPalette == "theme" || playerPalette == "cover") { "特效主题播放器配色无效" }
        require(minGlesVersion == 0 || minGlesVersion in 0x20000..0x30002) { "特效主题图形要求无效" }
        require(preview == null || isSafeThemeAssetPath(preview)) { "特效主题预览路径无效" }
        require(parameterSchema.size <= 32 && parameterSchema.map { it.key }.distinct().size == parameterSchema.size) { "特效主题参数重复或过多" }
        parameterSchema.forEach { parameter ->
            require(Regex("[a-zA-Z][a-zA-Z0-9_.-]{0,63}").matches(parameter.key) &&
                parameter.label.length in 1..80) { "特效主题参数名称无效" }
            if (parameter.type == "range") {
                require(parameter.min?.isFinite() == true && parameter.max?.isFinite() == true &&
                    parameter.min <= parameter.max && (parameter.step == null || parameter.step.isFinite() && parameter.step > 0f)) { "特效主题参数范围无效" }
            }
            require(parameter.options.size <= 32 && parameter.options.map { it.value }.distinct().size == parameter.options.size &&
                parameter.options.all { it.value.length in 1..80 && it.label.length in 1..80 }) { "特效主题选项无效" }
            require(parameter.accepts(parameter.defaultValue)) { "特效主题默认参数无效" }
        }
    }
}

@Keep
internal data class NgThemePublication(
    @SerializedName("versionCode") val versionCode: Long = 1,
    @SerializedName("versionName") val versionName: String = "1.0",
    @SerializedName("author") val author: String = "",
    @SerializedName("sourceId") val sourceId: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("sha256") val sha256: String? = null,
    @SerializedName("installedFingerprint") val installedFingerprint: String? = null,
    @SerializedName("derived") val derived: Boolean = false,
)

internal val NgManagedTheme.isEffect: Boolean get() = effect != null
internal val NgManagedTheme.isCustomized: Boolean
    get() = publication?.derived == true || publication?.installedFingerprint?.let { it != customizationFingerprint() } == true

internal fun NgManagedTheme.customizationFingerprint(): String {
    val json = GSON.toJson(copy(publication = null))
    return MessageDigest.getInstance("SHA-256").digest(json.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

internal enum class NgEffectThemeStatus(val label: String) {
    READY("可用"), MISSING("主题文件缺失"), INCOMPATIBLE("接口不兼容"),
    UNSUPPORTED("此设备或位置不支持"), CORRUPT("主题文件损坏"), LOAD_ERROR("运行失败");
    val canRun: Boolean get() = this == READY
    val message: String? get() = label.takeUnless { canRun }
}

internal enum class NgThemeImportAction { INSTALL, UPDATE, REPLACE, DOWNGRADE, NO_OP }
