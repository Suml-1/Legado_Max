package io.legado.app.help.config

/** Product identity is independent of renderer implementation and asset reuse. */
internal enum class NgEffectPackageKind(
    val format: String,
    val target: String,
    val directory: String,
) {
    THEME("reading-ng-theme", "app", "ng_theme_packages"),
    LISTENING_EFFECT("reading-ng-listening-effect", "player", "ng_listening_effect_packages");

    fun acceptsTargets(targets: List<String>): Boolean = targets == listOf(target)

    fun requireContent(theme: NgManagedTheme) {
        val effect = theme.effect
        require(effect != null || this == THEME) { "请选择听书特效 ZIP，普通主题不能导入听书特效库" }
        require(effect == null || acceptsTargets(effect.targets)) {
            "主题与听书特效必须分别打包，请使用对应的独立 ZIP"
        }
    }

    companion object {
        fun fromFormat(format: String): NgEffectPackageKind = entries.firstOrNull { it.format == format }
            ?: error("不支持的资源包格式")

        fun of(theme: NgManagedTheme): NgEffectPackageKind = when (theme.effect?.targets) {
            null, listOf("app") -> THEME
            listOf("player") -> LISTENING_EFFECT
            else -> error("旧双用途包不再支持，请重新导入独立的主题或听书特效 ZIP")
        }

        fun runtimeKey(id: String, target: String): String {
            require(entries.any { it.target == target }) { "资源包用途无效" }
            return "$target:$id"
        }
    }
}
