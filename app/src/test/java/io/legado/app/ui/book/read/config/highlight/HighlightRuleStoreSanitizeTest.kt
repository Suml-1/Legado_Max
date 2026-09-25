package io.legado.app.ui.book.read.config.highlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HighlightRuleStore.sanitizeRule] 九宫格分割比例兜底逻辑单元测试。
 *
 * GSON 用 Unsafe 实例化 data class 时不调用构造函数，
 * 老规则 JSON 缺失 np 字段时反序列化得到 0（合法值，表示无边框分割），
 * 只有越界值才应回落到默认比例。
 */
class HighlightRuleStoreSanitizeTest {

    private fun rule(
        npLeft: Float = HighlightRuleStore.DEFAULT_NP_RATIO,
        npTop: Float = HighlightRuleStore.DEFAULT_NP_RATIO,
        npRight: Float = HighlightRuleStore.DEFAULT_NP_RATIO,
        npBottom: Float = HighlightRuleStore.DEFAULT_NP_RATIO,
    ) = HighlightRule(
        name = "测试",
        pattern = ".*",
        npLeft = npLeft,
        npTop = npTop,
        npRight = npRight,
        npBottom = npBottom,
    )

    @Test
    fun `合法比例保持不变`() {
        val sanitized = HighlightRuleStore.sanitizeRule(
            rule(npLeft = 0f, npTop = 0.9f, npRight = 0.5f, npBottom = 0.1f),
        )
        assertEquals(0f, sanitized.npLeft)
        assertEquals(0.9f, sanitized.npTop)
        assertEquals(0.5f, sanitized.npRight)
        assertEquals(0.1f, sanitized.npBottom)
    }

    @Test
    fun `越界比例回落到默认值`() {
        val sanitized = HighlightRuleStore.sanitizeRule(
            rule(npLeft = -0.3f, npTop = 1.2f, npRight = Float.NaN, npBottom = 12f),
        )
        assertEquals(HighlightRuleStore.DEFAULT_NP_RATIO, sanitized.npLeft)
        assertEquals(HighlightRuleStore.DEFAULT_NP_RATIO, sanitized.npTop)
        assertEquals(HighlightRuleStore.DEFAULT_NP_RATIO, sanitized.npRight)
        assertEquals(HighlightRuleStore.DEFAULT_NP_RATIO, sanitized.npBottom)
    }

    @Test
    fun `styleSummary 标注九宫格适配方式`() {
        val summary = rule().copy(bgImage = "assets://bg/a.png", bgImageFit = 3).styleSummary()
        assertTrue(summary.contains("背景图(九宫格)"))
    }

    @Test
    fun `旧版左右上下间距迁移到四边并清空旧字段`() {
        val sanitized = HighlightRuleStore.sanitizeRule(
            rule().copy(bgSpacingH = 0.3f, bgSpacingV = -0.2f),
        )
        assertEquals(0.3f, sanitized.bgSpacingLeft)
        assertEquals(0.3f, sanitized.bgSpacingRight)
        assertEquals(-0.2f, sanitized.bgSpacingTop)
        assertEquals(-0.2f, sanitized.bgSpacingBottom)
        assertEquals(0f, sanitized.bgSpacingH)
        assertEquals(0f, sanitized.bgSpacingV)
    }

    @Test
    fun `四边间距已设置时不被旧字段覆盖`() {
        val sanitized = HighlightRuleStore.sanitizeRule(
            rule().copy(bgSpacingH = 0.5f, bgSpacingV = 0.4f, bgSpacingBottom = 0.25f),
        )
        assertEquals(0f, sanitized.bgSpacingLeft)
        assertEquals(0f, sanitized.bgSpacingRight)
        assertEquals(0f, sanitized.bgSpacingTop)
        assertEquals(0.25f, sanitized.bgSpacingBottom)
        assertEquals(0f, sanitized.bgSpacingH)
        assertEquals(0f, sanitized.bgSpacingV)
    }

    @Test
    fun `上下间距正向可到 1em 负向仍限 -0_5em`() {
        val sanitized = HighlightRuleStore.sanitizeRule(
            rule().copy(bgSpacingTop = 1f, bgSpacingBottom = -0.5f),
        )
        assertEquals(1f, sanitized.bgSpacingTop)
        assertEquals(-0.5f, sanitized.bgSpacingBottom)
        val outOfRange = HighlightRuleStore.sanitizeRule(
            rule().copy(bgSpacingTop = 1.2f, bgSpacingBottom = -0.6f),
        )
        assertEquals(0f, outOfRange.bgSpacingTop)
        assertEquals(0f, outOfRange.bgSpacingBottom)
    }

    @Test
    fun `命中字距与命中行行距越界回落到 0`() {
        val sanitized = HighlightRuleStore.sanitizeRule(
            rule().copy(
                letterSpacingBefore = 24f,
                letterSpacingAfter = -4f,
                lineSpacingEnabled = true,
                lineSpacingTop = HighlightRuleStore.MAX_MATCH_SPACING + 1f,
                lineSpacingBottom = Float.NaN,
            ),
        )
        assertEquals(24f, sanitized.letterSpacingBefore)
        assertEquals(0f, sanitized.letterSpacingAfter)
        assertTrue(sanitized.lineSpacingEnabled)
        assertEquals(0f, sanitized.lineSpacingTop)
        assertEquals(0f, sanitized.lineSpacingBottom)
    }

    @Test
    fun `styleSummary 标注命中字距与命中行行距`() {
        val summary = rule().copy(
            letterSpacingBefore = 8f,
            letterSpacingAfter = 8f,
            lineSpacingEnabled = true,
            lineSpacingTop = 12f,
            lineSpacingBottom = 12f,
        ).styleSummary()
        assertTrue(summary.contains("命中字距 8 / 8px"))
        assertTrue(summary.contains("命中行行距 12 / 12px"))
    }
}
