package io.legado.app.data.entities.rule

data class RowUi(
    val name: String = "",
    val type: String = "text",
    val action: String? = null,
    val chars: List<String?>? = null,
    val default: String? = null,
    var viewName: String? = null,
    val style: FlexChildStyle? = null,
    // 登录 UI v2（loginUi(state) 返回的 {rows:[...]}）专用字段，v1 规则忽略
    val key: String? = null,
    val hint: String? = null,
    val value: String? = null,
    val options: List<String>? = null,
    val countdown: Int? = null,
) {

    @Suppress("ConstPropertyName")
    object Type {

        const val text = "text"
        const val password = "password"
        const val button = "button"
        const val label = "label"
        const val toggle = "toggle"
        const val select = "select"
    }

    fun style(): FlexChildStyle = style ?: FlexChildStyle.defaultStyle

    /** 可选项：v2 用 `options`，v1 用 `chars` */
    fun choices(): List<String> = options?.takeIf { it.isNotEmpty() }
        ?: chars?.filterNotNull()?.takeIf { it.isNotEmpty() }
        ?: emptyList()

    /** 同 [ExploreKind]：候选列表参与判等，避免按本对象做 key 时漏掉候选变化 */
    override fun equals(other: Any?): Boolean {
        if (other is RowUi) {
            return other.name == name &&
                other.type == type &&
                other.action == action &&
                other.chars == chars &&
                other.default == default &&
                other.style == style
        }
        return false
    }

    override fun hashCode(): Int {
        var result = name.hashCode() + type.hashCode()
        result = 31 * result + (action?.hashCode() ?: 0)
        result = 31 * result + (chars?.hashCode() ?: 0)
        result = 31 * result + (default?.hashCode() ?: 0)
        result = 31 * result + (style?.hashCode() ?: 0)
        return result
    }
}
