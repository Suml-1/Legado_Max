package io.legado.app.help.book

import android.os.Build
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.AppPattern.spaceRegex
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.exception.RegexTimeoutException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.escapeRegex
import io.legado.app.utils.replace
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import splitties.init.appCtx
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Matcher
import java.util.regex.Pattern

class ContentProcessor private constructor(
    private val bookName: String,
    private val bookOrigin: String
) {

    companion object {
        private val processors = ConcurrentHashMap<String, WeakReference<ContentProcessor>>()
        private val isAndroid8 = Build.VERSION.SDK_INT in 26..27

        fun get(book: Book) = get(book.name, book.origin)

        fun get(bookName: String, bookOrigin: String): ContentProcessor {
            val processorWr = processors[bookName + bookOrigin]
            var processor: ContentProcessor? = processorWr?.get()
            if (processor == null) {
                processor = ContentProcessor(bookName, bookOrigin)
                processors[bookName + bookOrigin] = WeakReference(processor)
            }
            return processor
        }

        fun upReplaceRules() {
            processors.forEach {
                it.value.get()?.upReplaceRules()
            }
        }

    }

    private val titleReplaceRules = CopyOnWriteArrayList<ReplaceRule>()
    private val contentReplaceRules = CopyOnWriteArrayList<ReplaceRule>()
    val removeSameTitleCache = hashSetOf<String>()

    init {
        upReplaceRules()
    }

    fun upReplaceRules() {
        titleReplaceRules.run {
            clear()
            addAll(appDb.replaceRuleDao.findEnabledByTitleScope(bookName, bookOrigin))
        }
        contentReplaceRules.run {
            clear()
            addAll(appDb.replaceRuleDao.findEnabledByContentScope(bookName, bookOrigin))
        }
    }

    /**
     * 重新扫描整本书缓存目录以填充 removeSameTitleCache。
     *
     * 注意：此方法会扫描整本书的缓存目录，开销较大，不再在 init 中自动调用。
     * removeSameTitleCache 现在仅在 [setRemoveSameTitle] 被用户调用时增量更新，
     * [getContent] 中的去除重复标题判断已改为按单章按需检查 .nr 文件，
     * 避免每次进入正文都扫描整本书的缓存目录。
     */
    private fun upRemoveSameTitle() {
        val book = appDb.bookDao.getBookByOrigin(bookName, bookOrigin) ?: return
        removeSameTitleCache.clear()
        val files = BookHelp.getChapterFiles(book).filter {
            it.endsWith("nr")
        }
        removeSameTitleCache.addAll(files)
    }

    fun getTitleReplaceRules(): List<ReplaceRule> {
        return titleReplaceRules
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getContentReplaceRules(): List<ReplaceRule> {
        return contentReplaceRules
    }

    fun getContent(
        book: Book,
        chapter: BookChapter,
        content: String,
        includeTitle: Boolean = true,
        useReplace: Boolean = true,
        chineseConvert: Boolean = true,
        reSegment: Boolean = true,
        forceRemoveTitle: Boolean = false
    ): BookContent {
        var mContent = content
        var sameTitleRemoved = false
        var effectiveReplaceRules: ArrayList<ReplaceRule>? = null
        val replaceBook by lazy { book.toReplaceBook() }
        if (content != "null") {
            //去除重复标题
            // 按单章按需检查 .nr 文件，避免在 init 中扫描整本书缓存目录
            if (BookHelp.removeSameTitle(book, chapter)) try {
                val name = Pattern.quote(book.name)
                var title = chapter.title.escapeRegex().replace(spaceRegex, "\\\\s*")
                var matcher = Pattern.compile("^(\\s|\\p{P}|${name})*${title}(\\s)*")
                    .matcher(mContent)
                if (matcher.find()) {
                    mContent = mContent.substring(matcher.end())
                    sameTitleRemoved = true
                } else if (useReplace && book.getUseReplaceRule()) {
                    title = Pattern.quote(
                        chapter.getDisplayTitle(
                            titleReplaceRules,
                            chineseConvert = false,
                            replaceBook = replaceBook
                        )
                    )
                    matcher = Pattern.compile("^(\\s|\\p{P}|${name})*${title}(\\s)*")
                        .matcher(mContent)
                    if (matcher.find()) {
                        mContent = mContent.substring(matcher.end())
                        sameTitleRemoved = true
                    }
                }
                // 如果正则匹配失败且强制移除标题，尝试移除第一行
                if (!sameTitleRemoved && forceRemoveTitle) {
                    val firstLine = mContent.lines().firstOrNull()?.trim() ?: ""
                    val chapterTitle = chapter.title.trim()
                    val displayTitle = chapter.getDisplayTitle(
                        titleReplaceRules,
                        useReplace = useReplace && book.getUseReplaceRule(),
                        chineseConvert = false,
                        replaceBook = replaceBook
                    ).trim()
                    // 如果第一行与标题相同或相似，移除第一行
                    if (firstLine.isNotEmpty() && 
                        (firstLine == chapterTitle || firstLine == displayTitle ||
                         firstLine.contains(chapterTitle) || chapterTitle.contains(firstLine) ||
                         firstLine.contains(displayTitle) || displayTitle.contains(firstLine))) {
                        mContent = mContent.removePrefix(firstLine).trimStart()
                        sameTitleRemoved = true
                    }
                }
            } catch (e: Exception) {
                AppLog.put("去除重复标题出错\n${e.localizedMessage}", e)
            }
            if (reSegment && book.getReSegment()) {
                //重新分段
                // 先保护 img 标签，避免被分段算法破坏引号和空格
                val imgMap = mutableMapOf<String, String>()
                val imgMatcher = AppPattern.imgPattern.matcher(mContent)
                val imgSb = StringBuffer()  //Matcher.appendTail(StringBuilder) 和 Matcher.appendReplacement(StringBuilder, String) 这两个方法是 Android API 26（Android 8.0）才新增的。你的设备系统低于 Android 8.0，所以运行时报 NoSuchMethodError。修复：将 StringBuilder 改为 StringBuffer。StringBuffer 版本的 appendTail 和 appendReplacement 从 API 1 就存在，兼容所有 Android 版本。两者功能完全一致，唯一区别是 StringBuffer 是线程安全的（在这个局部变量场景下没有性能影响）。
                while (imgMatcher.find()) {
                    val placeholder = "图片占位符${imgMap.size}号"
                    imgMap[placeholder] = imgMatcher.group()
                    imgMatcher.appendReplacement(imgSb, Matcher.quoteReplacement(placeholder))
                }
                imgMatcher.appendTail(imgSb)
                mContent = imgSb.toString()
                mContent = ContentHelp.reSegment(mContent, chapter.title)
                // 还原 img 标签
                imgMap.forEach { (placeholder, original) ->
                    mContent = mContent.replace(placeholder, original)
                }
            }
            if (chineseConvert) {
                //简繁转换
                try {
                    when (AppConfig.chineseConverterType) {
                        1 -> mContent = ChineseUtils.t2s(mContent)
                        2 -> mContent = ChineseUtils.s2t(mContent)
                    }
                } catch (_: Exception) {
                    appCtx.toastOnUi("简繁转换出错")
                }
            }
            val useHtmlMap = mutableMapOf<String, String>()
            if (AppConfig.adaptSpecialStyle) { //html处理
                mContent = AppPattern.useHtmlRegex.replace(mContent) { matchResult ->
                    val placeholder = "特殊格式的占位不应该被看见${useHtmlMap.size}。"
                    useHtmlMap[placeholder] = "\n${matchResult.value.replace("\n","")}\n"
                    placeholder
                }
            }
            if (useReplace && book.getUseReplaceRule()) {
                //替换
                effectiveReplaceRules = arrayListOf()
                mContent = mContent.lines().joinToString("\n") { it.trim() }
                getContentReplaceRules().forEach { item ->
                    if (item.pattern.isEmpty()) {
                        return@forEach
                    }
                    try {
                        val tmp = if (item.isRegex) {
                            mContent.replace(
                                item.name,
                                item.regex,
                                item.replacement,
                                item.getValidTimeoutMillisecond(),
                                chapter,
                                replaceBook
                            )
                        } else {
                            mContent.replace(item.pattern, item.replacement)
                        }
                        if (mContent != tmp) {
                            effectiveReplaceRules.add(item)
                            mContent = tmp
                        }
                    } catch (e: RegexTimeoutException) {
                        item.isEnabled = false
                        appDb.replaceRuleDao.update(item)
                        mContent = item.name + e.stackTraceStr
                    } catch (_: CancellationException) {
                    } catch (e: Exception) {
                        AppLog.put("替换净化: 规则 ${item.name}替换出错.\n${mContent}", e)
                        appCtx.toastOnUi("替换净化: 规则 ${item.name}替换出错")
                    }
                }
            }
            useHtmlMap.forEach { (placeholder, originalContent) ->
                mContent = mContent.replace(placeholder, originalContent)
            }
        }
        if (includeTitle) {
            //重新添加标题
            mContent = chapter.getDisplayTitle(
                getTitleReplaceRules(),
                useReplace = useReplace && book.getUseReplaceRule(),
                replaceBook = replaceBook
            ) + "\n" + mContent
        }
        if (isAndroid8) {
            mContent = mContent.replace('\u00A0', ' ')
        }
        val contents = arrayListOf<String>()
        // 保留空行：原文的空行以空段落的形式进 textList，排版时按一行高度留白
        val keepBlankLine = AppConfig.keepBlankLine
        mContent.split("\n").forEach { str ->
            val paragraph = str.trim {
                it.code <= 0x20 || it == '　'
            }
            if (paragraph.isNotEmpty()) {
                if (contents.isEmpty() && includeTitle) {
                    contents.add(paragraph)
                } else {
                    contents.add("${ReadBookConfig.paragraphIndent}$paragraph")
                }
            } else if (keepBlankLine && contents.isNotEmpty()) {
                // 章节开头的空行仍然丢掉（标题前/正文首行前不留空白）
                contents.add("")
            }
        }
        return BookContent(sameTitleRemoved, contents, effectiveReplaceRules)
    }

}
