package io.legado.app.help.book

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ChapterTitleSplitter] 单元测试。
 *
 * 覆盖三种分段模式的切分结果，以及各类无效输入必须原样返回（最差等同于关闭）。
 */
class ChapterTitleSplitterTest {

    private val title = "第一章雪落无声夜归人"

    @Test
    fun `mode none returns original title`() {
        assertEquals(
            title,
            ChapterTitleSplitter.split(title, ChapterTitleSplitter.MODE_NONE, "5"),
        )
    }

    @Test
    fun `blank title returns original`() {
        assertEquals("", ChapterTitleSplitter.split("", ChapterTitleSplitter.MODE_POSITION, "5"))
    }

    @Test
    fun `split by position cuts once`() {
        assertEquals(
            "第一章雪落\n无声夜归人",
            ChapterTitleSplitter.split(title, ChapterTitleSplitter.MODE_POSITION, "5"),
        )
    }

    @Test
    fun `split by position ignores out of range and invalid number`() {
        // 切不出两行就不插换行
        assertEquals(
            title, ChapterTitleSplitter.split(title, ChapterTitleSplitter.MODE_POSITION, "0")
        )
        assertEquals(
            title, ChapterTitleSplitter.split(title, ChapterTitleSplitter.MODE_POSITION, "12")
        )
        assertEquals(
            title, ChapterTitleSplitter.split(title, ChapterTitleSplitter.MODE_POSITION, "abc")
        )
        assertEquals(
            title, ChapterTitleSplitter.split(title, ChapterTitleSplitter.MODE_POSITION, "")
        )
    }

    @Test
    fun `split by mark keeps the mark`() {
        assertEquals(
            "Vol.1-\n雪落-\nVol",
            ChapterTitleSplitter.split("Vol.1-雪落-Vol", ChapterTitleSplitter.MODE_MARK, "-"),
        )
    }

    @Test
    fun `split by mark splits at every match`() {
        assertEquals(
            "Vol.1-\n雪落-\nVol",
            ChapterTitleSplitter.split("Vol.1-雪落-Vol", ChapterTitleSplitter.MODE_MARK, "-"),
        )
    }

    @Test
    fun `split by mark supports space as a mark`() {
        // 空格本身就是要拿来分段的字符，不能被 trim 掉
        assertEquals(
            "第一章 \n雪落",
            ChapterTitleSplitter.split("第一章 雪落", ChapterTitleSplitter.MODE_MARK, " "),
        )
    }

    @Test
    fun `split by mark can use pipe as the mark`() {
        // | 只当普通字符，不再作为多标记的分隔符
        assertEquals(
            "第一章|\n雪落",
            ChapterTitleSplitter.split("第一章|雪落", ChapterTitleSplitter.MODE_MARK, "|"),
        )
    }

    @Test
    fun `split by mark supports multi char mark`() {
        assertEquals(
            "Vol.1 - \n雪落",
            ChapterTitleSplitter.split("Vol.1 - 雪落", ChapterTitleSplitter.MODE_MARK, " - "),
        )
    }

    @Test
    fun `split by mark returns original when mark blank or absent`() {
        assertEquals(
            title, ChapterTitleSplitter.split(title, ChapterTitleSplitter.MODE_MARK, "")
        )
        assertEquals(
            title, ChapterTitleSplitter.split(title, ChapterTitleSplitter.MODE_MARK, "：")
        )
    }

    @Test
    fun `split by regex keeps matched content`() {
        assertEquals(
            "第1章\n雪落第2章\n",
            ChapterTitleSplitter.split("第1章雪落第2章", ChapterTitleSplitter.MODE_REGEX, "第\\d章"),
        )
    }

    @Test
    fun `split by regex returns original when no match`() {
        assertEquals(
            title, ChapterTitleSplitter.split(title, ChapterTitleSplitter.MODE_REGEX, "第\\d章")
        )
    }

    @Test
    fun `split by regex returns original when pattern invalid`() {
        // 边填边试的过程中正则随时可能不合法，不能让标题崩掉
        assertEquals(
            title, ChapterTitleSplitter.split(title, ChapterTitleSplitter.MODE_REGEX, "[")
        )
        assertEquals(
            title, ChapterTitleSplitter.split(title, ChapterTitleSplitter.MODE_REGEX, "")
        )
    }

    @Test
    fun `unknown mode returns original`() {
        assertEquals(title, ChapterTitleSplitter.split(title, 99, "5"))
    }
}
