package io.legado.app.help.book

/**
 * 正文标题分段。
 *
 * 只服务于「正文标题渲染」这一个显示环节，不改变数据库里的章节名，也不改变
 * [io.legado.app.data.entities.BookChapter.getDisplayTitle] 的返回值——后者同时被
 * ContentProcessor 用来判断并删除正文首行的重复标题，一旦带上分段结果会让去重判断失配。
 *
 * 因此页眉页脚（取 book.durChapterTitle）、书架章节名、目录、朗读标题、替换规则的匹配对象
 * 全部还是未分段的原标题，分段只影响正文里那一块标题的换行显示。
 */
object ChapterTitleSplitter {

    /** 关闭：不分段 */
    const val MODE_NONE = 0

    /** 按位置：只在填写的数字位置后切一刀 */
    const val MODE_POSITION = 1

    /** 按标记：在填写的字符处换行，标记按字面使用（可用 | 等多个字符的组合） */
    const val MODE_MARK = 2

    /** 按正则：在每个正则匹配项的后面插入换行，匹配内容保留 */
    const val MODE_REGEX = 3

    /**
     * 按 [mode] 与 [value] 给标题插入换行。
     *
     * 任何填不合法、切了也没意义的输入都原样返回标题，保证最差情况等同于「关闭」。
     *
     * @param title 已应用替换规则等处理的显示标题
     * @param mode [MODE_NONE] / [MODE_POSITION] / [MODE_MARK] / [MODE_REGEX]
     * @param value 分段依据：位置填数字、标记填字符（按字面使用）、正则填表达式
     */
    fun split(title: String, mode: Int, value: String): String {
        if (title.isEmpty() || mode == MODE_NONE) return title
        return when (mode) {
            MODE_POSITION -> splitByPosition(title, value)
            MODE_MARK -> splitByMarks(title, value)
            MODE_REGEX -> splitByRegex(title, value)
            else -> title
        }
    }

    /**
     * 按位置分段：在第 count 个字符之后换一次行。
     *
     * count 落在标题长度之外时切不出两行，直接原样返回，而不是插一个没用的换行。
     */
    private fun splitByPosition(title: String, value: String): String {
        val count = value.trim().toIntOrNull() ?: return title
        if (count <= 0 || count >= title.length) return title
        return title.substring(0, count) + "\n" + title.substring(count)
    }

    /**
     * 按标记分段：整串内容作为一个标记，标题里每个该标记出现处的后面换行，标记本身保留。
     *
     * 标记按字面使用，既不 trim 也不拆成多个——空格、「|」本身都可能是要拿来分段的字符，
     * 需要按多种标记分段时用正则模式表达即可。
     *
     * 例如标题「第一章 雪落无声」填「 」得到「第一章 \n雪落无声」，而不是把空格吞掉——
     * 吞掉属于替换行为，会让标题丢字。
     */
    private fun splitByMarks(title: String, value: String): String {
        if (value.isEmpty() || value !in title) return title
        return title.replace(value, "$value\n")
    }

    /**
     * 按正则分段：在每个匹配项结束的位置插入换行，匹配到的内容保留。
     *
     * 正则非法时原样返回，避免用户边填边试的过程中崩到阅读页。
     * 空匹配（如 `a*`）不插入换行，否则会在同一位置反复插入、把标题切碎。
     */
    private fun splitByRegex(title: String, value: String): String {
        val pattern = value.trim()
        if (pattern.isEmpty()) return title
        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            return title
        }
        val output = StringBuilder()
        var lastEnd = 0
        regex.findAll(title).forEach { match ->
            val end = match.range.last + 1
            if (match.value.isEmpty() || end <= lastEnd) return@forEach
            output.append(title, lastEnd, end).append('\n')
            lastEnd = end
        }
        if (lastEnd == 0) return title
        output.append(title, lastEnd, title.length)
        return output.toString()
    }
}
