package io.legado.app.ui.book.read.config.highlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [HighlightRuleRepository.reorderInGroup] 拖动排序后的顺序重排单元测试。
 *
 * 分组筛选下列表只包含当前分组的规则，重排必须只回填该分组占用的槽位，
 * 其他分组的规则位置保持不动。
 */
class HighlightRuleReorderTest {

    private fun rule(id: String, group: String) =
        HighlightRule(id = id, name = id, pattern = ".*", group = group)

    @Test
    fun `未筛选时按列表顺序整体重排`() {
        val rules = listOf(rule("a", "默认"), rule("b", "默认"), rule("c", "默认"))
        val reordered = HighlightRuleRepository.reorderInGroup(
            rules,
            group = null,
            ordered = listOf(rules[2], rules[0], rules[1]),
        )
        assertEquals(listOf("c", "a", "b"), reordered?.map { it.id })
    }

    @Test
    fun `分组筛选下只重排该分组的槽位`() {
        val rules = listOf(
            rule("a", "默认"),
            rule("x", "对话"),
            rule("b", "默认"),
            rule("y", "对话"),
            rule("c", "默认"),
        )
        // 该分组在整表中的槽位是 1、3，列表顺序为 x、y 交换后回填
        val reordered = HighlightRuleRepository.reorderInGroup(
            rules,
            group = "对话",
            ordered = listOf(rules[3], rules[1]),
        )
        assertEquals(listOf("a", "y", "b", "x", "c"), reordered?.map { it.id })
    }

    @Test
    fun `空列表与槽位不匹配时放弃重排`() {
        val rules = listOf(rule("a", "默认"), rule("x", "对话"))
        assertNull(HighlightRuleRepository.reorderInGroup(rules, null, emptyList()))
        assertNull(
            HighlightRuleRepository.reorderInGroup(
                rules,
                group = "对话",
                ordered = listOf(rules[0], rules[1]),
            ),
        )
    }
}
