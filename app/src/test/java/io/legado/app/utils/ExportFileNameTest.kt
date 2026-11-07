package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 导出文件名净化 [toSafeFileName] 的单元测试。
 *
 * 覆盖：非法字符换成全角同形字符、正常名字原样返回、控制字符换成空格、
 * 以及"替换成 `_` 会撞名"的那类书名在映射后仍然互不相同。
 */
class ExportFileNameTest {

    @Test
    fun `illegal characters map to full width lookalikes`() {
        assertEquals(
            "A／B：C＊D？E＂F＜G＞H｜I＼J",
            "A/B:C*D?E\"F<G>H|I\\J".toSafeFileName(),
        )
    }

    @Test
    fun `normal file name stays unchanged`() {
        val name = "没钱修什么仙 作者：熊狼狗 [2].txt"
        assertEquals(name, name.toSafeFileName())
    }

    @Test
    fun `control characters become space`() {
        assertEquals("第1章 试读", "第1章\n试读".toSafeFileName())
        assertEquals("第2章 试读", "第2章\t试读".toSafeFileName())
    }

    @Test
    fun `slash and underscore names do not collide`() {
        assertNotEquals("a/b".toSafeFileName(), "a_b".toSafeFileName())
    }
}
