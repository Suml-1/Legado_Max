package io.legado.app.ui.book.read.config.highlight

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.EventBus
import io.legado.app.help.source.SourceRecycleBinHelp
import io.legado.app.utils.postEvent

/**
 * 高亮规则配置页的状态管理层。
 *
 * 管理规则列表、当前分组、增删改、导入、启用状态切换和保存同步，
 * 让配置弹窗只负责界面渲染与用户交互。
 */
class HighlightRuleConfigViewModel(application: Application) : BaseViewModel(application) {

    val rules = ArrayList<HighlightRule>()
    var currentGroup: String? = null
        private set

    fun loadRules() {
        rules.clear()
        rules.addAll(HighlightRuleRepository.loadRules(context))
        currentGroup = HighlightRuleRepository.loadCurrentGroup(context)
    }

    fun filteredRules(): List<HighlightRule> {
        return currentGroup?.let { group ->
            rules.filter { it.group == group }
        } ?: rules.toList()
    }

    fun switchToGroup(group: String?) {
        currentGroup = group
        HighlightRuleRepository.saveCurrentGroup(context, currentGroup)
    }

    /**
     * 拖动排序后把列表顺序写回规则表。
     *
     * 分组筛选下只重排该分组占据的那些槽位，其他分组的规则位置保持不动；
     * 顺序没变化时返回 false，避免无意义地写库与刷新阅读页。
     */
    fun reorderRules(ordered: List<HighlightRule>): Boolean {
        if (ordered.isEmpty()) return false
        val group = currentGroup
        val newRules = if (group == null) {
            ArrayList(ordered)
        } else {
            val slots = rules.withIndex()
                .filter { it.value.group == group }
                .map { it.index }
            if (slots.size != ordered.size) return false
            ArrayList(rules).also { result ->
                slots.forEachIndexed { index, slot -> result[slot] = ordered[index] }
            }
        }
        if (newRules.map { it.id } == rules.map { it.id }) return false
        rules.clear()
        rules.addAll(newRules)
        syncRules()
        return true
    }

    fun resetRules() {
        rules.clear()
        rules.addAll(HighlightRuleRepository.resetRules(context))
        syncRules()
    }

    fun upsertRule(rule: HighlightRule) {
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            rules[index] = rule
        } else {
            rules.add(rule)
        }
        syncRules()
    }

    fun addRule(rule: HighlightRule) {
        rules.add(rule)
        syncRules()
    }

    fun deleteRule(rule: HighlightRule) {
        SourceRecycleBinHelp.recycleHighlightRules(listOf(rule))
        rules.removeAll { it.id == rule.id }
        syncRules()
    }

    fun deleteRules(ids: Set<String>) {
        val deleteRules = rules.filter { it.id in ids }
        SourceRecycleBinHelp.recycleHighlightRules(deleteRules)
        rules.removeAll { it.id in ids }
        syncRules()
    }

    fun setRuleEnabled(rule: HighlightRule, enabled: Boolean) {
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index < 0 || rules[index].enabled == enabled) return
        rules[index] = rules[index].copy(enabled = enabled)
        syncRules()
    }

    fun importRules(imported: List<HighlightRule>) {
        val targetGroup = currentGroup ?: HighlightRuleGroupStore.DEFAULT_GROUP
        imported.forEach { rule ->
            var normalized = HighlightRuleRepository.sanitizeRule(rule, targetGroup)
            if (rules.any { it.id == normalized.id }) {
                normalized = normalized.copyWithNewId()
            }
            rules.add(normalized)
        }
        syncRules()
    }

    fun syncRules() {
        HighlightRuleRepository.saveRules(context, rules)
        HighlightRuleRepository.saveCurrentGroup(context, currentGroup)
        postEvent(EventBus.UP_CONFIG, arrayListOf(5))
    }
}