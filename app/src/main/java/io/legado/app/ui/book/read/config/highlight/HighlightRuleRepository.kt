package io.legado.app.ui.book.read.config.highlight

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

/**
 * 高亮规则的数据仓库入口。
 *
 * 为 UI、ViewModel 和阅读排版层提供规则、分组、当前分组的统一访问接口，
 * 隔离底层 Store、SharedPreferences 和 JSON 编码细节。
 */
object HighlightRuleRepository {

    fun loadRules(context: Context): MutableList<HighlightRule> {
        return HighlightRuleStore.load(context)
    }

    fun loadEnabledRules(context: Context): List<HighlightRule> {
        return HighlightRuleStore.loadEnabled(context)
    }

    fun saveRules(context: Context, rules: List<HighlightRule>) {
        HighlightRuleStore.save(context, rules)
    }

    fun resetRules(context: Context): MutableList<HighlightRule> {
        return HighlightRuleStore.reset(context)
    }

    fun loadGroups(context: Context): MutableList<String> {
        return HighlightRuleGroupStore.load(context)
    }

    fun saveGroups(context: Context, groups: List<String>) {
        HighlightRuleGroupStore.save(context, groups)
    }

    fun saveCurrentGroup(context: Context, group: String?) {
        context.putPrefString(PreferKey.highlightRuleCurrentGroup, group.orEmpty())
    }

    fun loadCurrentGroup(context: Context): String? {
        val saved = context.getPrefString(PreferKey.highlightRuleCurrentGroup)
        if (saved.isNullOrBlank()) return null
        return saved.takeIf { loadGroups(context).contains(it) }
    }

    fun sanitizeRule(
        rule: HighlightRule,
        fallbackGroup: String = HighlightRuleGroupStore.DEFAULT_GROUP,
    ): HighlightRule {
        return HighlightRuleStore.sanitizeRule(rule, fallbackGroup)
    }

    fun encodeRules(rules: List<HighlightRule>): String {
        return GSON.toJson(rules)
    }

    /**
     * 按拖动后的列表顺序重排完整规则表（纯函数，便于单测）。
     *
     * 分组筛选（[group] 非空）下列表只包含该分组的规则，此时只把这些规则回填到它们在原表中
     * 占用的槽位，其他分组的规则位置保持不动；槽位数量与列表长度不一致说明两份数据不同步，
     * 返回 null 表示放弃本次重排。
     *
     * @return 重排后的完整列表；[ordered] 为空或槽位不匹配时返回 null
     */
    fun reorderInGroup(
        rules: List<HighlightRule>,
        group: String?,
        ordered: List<HighlightRule>,
    ): List<HighlightRule>? {
        if (ordered.isEmpty()) return null
        if (group == null) return ordered.toList()
        val slots = rules.withIndex().filter { it.value.group == group }.map { it.index }
        if (slots.size != ordered.size) return null
        return ArrayList(rules).also { result ->
            slots.forEachIndexed { index, slot -> result[slot] = ordered[index] }
        }
    }
}