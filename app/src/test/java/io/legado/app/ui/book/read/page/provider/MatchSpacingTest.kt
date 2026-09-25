package io.legado.app.ui.book.read.page.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 命中字距的宽度测量单元测试。
 *
 * 这两个函数是「留白是真实排版宽度」的唯一口径：断行测量按字符取额外宽度，
 * 两端对齐/标题对齐按可视行取留白总和。算错就会表现为高亮文字右侧溢出或整行被压扁。
 */
class MatchSpacingTest {

    /** "abcd"：命中段为 "bc"，段首 b 带左侧留白 8，段尾 c 带右侧留白 6 */
    private val styles: Array<CharStyle?> = arrayOf(
        null,
        CharStyle(letterSpacingBefore = 8f),
        CharStyle(letterSpacingAfter = 6f),
        null,
    )

    @Test
    fun `断行额外宽度按字符给出命中段首尾留白`() {
        val widths = styles.matchSpacingWidths(4)
        assertEquals(0f, widths?.get(0))
        assertEquals(8f, widths?.get(1))
        assertEquals(6f, widths?.get(2))
        assertEquals(0f, widths?.get(3))
        assertEquals(4, widths?.size)
    }

    @Test
    fun `没有命中字距时不拷贝数组`() {
        assertNull(arrayOf<CharStyle?>(null, CharStyle(), null).matchSpacingWidths(3))
        assertNull((null as Array<CharStyle?>?).matchSpacingWidths(3))
    }

    @Test
    fun `可视行留白只统计落在行范围内的字符`() {
        assertEquals(14f, styles.measureLineMatchSpacing(0, 4))
        assertEquals(8f, styles.measureLineMatchSpacing(0, 2))
        assertEquals(6f, styles.measureLineMatchSpacing(2, 4))
        assertEquals(0f, styles.measureLineMatchSpacing(3, 4))
        assertEquals(0f, styles.measureLineMatchSpacing(3, 3))
    }

    @Test
    fun `单字命中时同一字符的左右留白都要算上`() {
        val single = arrayOf<CharStyle?>(
            CharStyle(letterSpacingBefore = 5f, letterSpacingAfter = 7f),
            null,
        )
        assertEquals(12f, single.matchSpacingWidths(2)?.get(0))
        assertEquals(12f, single.measureLineMatchSpacing(0, 1))
        assertEquals(12f, single.measureLineMatchSpacing(0, 2))
    }
}
