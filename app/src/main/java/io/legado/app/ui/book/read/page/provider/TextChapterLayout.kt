package io.legado.app.ui.book.read.page.provider

import android.graphics.Paint
import android.text.Layout
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import android.text.style.URLSpan
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.PageAnim
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookContent
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.getBookSource
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.ImageProvider
import io.legado.app.model.ParagraphBubbleRenderer
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.column.ImageColumn
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fastSum
import io.legado.app.utils.getTextWidthsCompat
import io.legado.app.utils.splitNotBlank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.LinkedList
import kotlin.math.roundToInt
import android.util.Size
import android.util.Base64
import android.net.Uri
import androidx.core.text.HtmlCompat
import io.legado.app.constant.AppPattern.noWordCountRegex
import io.legado.app.data.appDb
import io.legado.app.ui.book.read.page.entities.TextLine.Companion.atLeastApi28
import io.legado.app.ui.book.read.page.entities.column.TextHtmlColumn
import io.legado.app.ui.book.read.page.provider.ChapterProvider.reviewStr
import io.legado.app.ui.book.read.page.provider.ChapterProvider.srcReplaceStr
import io.legado.app.ui.book.read.page.provider.ChapterProvider.srcReplaceChar
import io.legado.app.ui.book.read.page.provider.ChapterProvider.srcReplacementChar
import io.legado.app.utils.StringUtils
import androidx.core.text.parseAsHtml
import androidx.core.util.component1
import androidx.core.util.component2
import io.legado.app.help.TextViewTagHandler
import io.legado.app.help.TextViewTagHandler.Companion.HR_PLACE_CHAR
import io.legado.app.help.TextViewTagHandler.Companion.HR_PLACE_STR
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.paramPattern
import io.legado.app.ui.book.read.page.entities.column.BaseColumn
import io.legado.app.ui.book.read.page.entities.column.TextBaseColumn
import io.legado.app.ui.book.read.config.highlight.HighlightRule
import io.legado.app.ui.book.read.config.highlight.HighlightRuleRepository
import io.legado.app.ui.book.read.config.highlight.HighlightRuleStyle
import io.legado.app.ui.book.read.page.provider.ChapterProvider.reviewChar
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import splitties.init.appCtx
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext

/**
 * 章节文本排版器。
 *
 * 负责把章节内容、HTML 样式、链接、搜索状态和高亮规则转换成 TextPage/TextLine；
 * 高亮规则在这里完成正则匹配和 Span 标记，最终样式绘制交给 TextLine。
 */
class TextChapterLayout(
    val scope: CoroutineScope,
    private val textChapter: TextChapter,
    private val textPages: ArrayList<TextPage>,
    private val book: Book,
    private val bookContent: BookContent,
) {

    @Volatile
    private var listener: LayoutProgressListener? = textChapter

    private val paddingLeft = ChapterProvider.paddingLeft
    private val paddingRight = ChapterProvider.paddingRight
    private val paddingTop = ChapterProvider.paddingTop

    private val titlePaint = ChapterProvider.titlePaint
    private val titlePaintTextHeight = ChapterProvider.titlePaintTextHeight
    private val titlePaintFontMetrics = ChapterProvider.titlePaintFontMetrics

    private val contentPaint = ChapterProvider.contentPaint
    private val reviewCharWidth by lazy { contentPaint.measureText(srcReplaceStr) * 1.5556f }

    // 高亮规则字体测宽专用画笔，参数在每次使用前按当前排版画笔设置
    private val highlightFontPaint = TextPaint().apply { isAntiAlias = true }
    private val contentPaintTextHeight = ChapterProvider.contentPaintTextHeight
    private val contentPaintFontMetrics = ChapterProvider.contentPaintFontMetrics

    private val titleTopSpacing = ChapterProvider.titleTopSpacing
    private val titleBottomSpacing = ChapterProvider.titleBottomSpacing
    private val lineSpacingExtra = ChapterProvider.lineSpacingExtra
    private val paragraphSpacing = ChapterProvider.paragraphSpacing

    /** 九宫格适配方式的取值（与 HighlightRule.bgImageFit / TextLine.drawNineSlice 口径一致） */
    private val bgImageFitNine = 3

    /** 九宫格"强制"策略需要从列末尾扣掉的宽度（见 [computeNeighborPush]），按段落排版时写入 */
    private var columnTrimEnd: FloatArray? = null

    /**
     * 九宫格"强制"策略在**段首带缩进**时给缩进额外让出的宽度（见 [computeNeighborPush]）。
     *
     * 两端对齐时首行缩进是用固定宽度的占位列重建的（见 [addCharsToLineFirst]），读不到宽度数组里
     * 那份加宽，所以单独带一份过来补在最后一个缩进列上。
     */
    private var columnIndentExtra: Float = 0f

    private val visibleHeight = ChapterProvider.visibleHeight
    private val visibleWidth = ChapterProvider.visibleWidth

    private val viewWidth = ChapterProvider.viewWidth
    private val doublePage = ChapterProvider.doublePage
    private val indentCharWidth = ChapterProvider.indentCharWidth
    private val stringBuilder = StringBuilder()

    private val paragraphIndent = ReadBookConfig.paragraphIndent
    private val titleMode = ReadBookConfig.titleMode
    private val useZhLayout = ReadBookConfig.useZhLayout
    private val isMiddleTitle = ReadBookConfig.isMiddleTitle
    private val isRightTitle = ReadBookConfig.isRightTitle
    private val textFullJustify = ReadBookConfig.textFullJustify
    private val adaptSpecialStyle = AppConfig.adaptSpecialStyle
    private val pageAnim = book.getPageAnim()
    private val compiledHighlightRules by lazy {
        HighlightRuleRepository.loadEnabledRules(appCtx).mapNotNull { rule ->
            kotlin.runCatching {
                CompiledHighlightRule(
                    rule = rule,
                    regex = rule.toRegex(),
                )
            }.getOrNull()
        }
    }

    private var pendingTextPage = TextPage()

    private val bookChapter inline get() = textChapter.chapter
    private val displayTitle inline get() = textChapter.title
    private val chaptersSize inline get() = textChapter.chaptersSize

    private var durY = 0f
    private var absStartX = paddingLeft
    private var floatArray = FloatArray(128)
    private val appendMutex = Mutex()
    private val pendingLazyContents = ArrayList<String>()

    private var isCompleted = false
    private val job: Coroutine<*>

    var exception: Throwable? = null

    var channel = Channel<TextPage>(Channel.UNLIMITED)

    init {
        job = Coroutine.async(
            scope,
            start = CoroutineStart.LAZY,
            executeContext = IO,
        ) {
            launch {
                val bookSource = book.getBookSource() ?: return@launch
                BookHelp.saveImages(bookSource, book, bookChapter, bookContent.toString())
            }
            getTextChapter(book, bookChapter, displayTitle, bookContent)
        }.onError {
            exception = it
            onException(it)
        }.onCancel {
            channel.cancel()
        }.onFinally {
            isCompleted = true
        }
        job.start()
    }

    fun cancel() {
        job.cancel()
        listener = null
    }

    fun appendContent(newContents: List<String>) {
        if (newContents.isEmpty()) return

        kotlinx.coroutines.GlobalScope.launch(IO) {
            try {
                AppLog.putReaderDebug("懒加载排版: 请求追加内容，共${newContents.size}段，排版完成状态=$isCompleted")
                appendMutex.withLock {
                    if (!isCompleted) {
                        AppLog.putReaderDebug("懒加载排版: 初始排版未完成，内容入队等待")
                        pendingLazyContents.addAll(newContents)
                        return@withLock
                    }
                    appendContentInternal(newContents)
                }
                AppLog.putReaderDebug("懒加载排版: 追加内容完成")
            } catch (e: Exception) {
                AppLog.put("追加内容失败: ${e.localizedMessage}", e)
            }
        }
    }

    private suspend fun appendContentInternal(newContents: List<String>) {
        val processedContents = preprocessBubbleJs(newContents)
        val imageStyle = book.getImageStyle()
        val isTextImageStyle = imageStyle.equals(Book.imgStyleText, true)
        val bodyHighlightStyles = buildBodyHighlightStyles(processedContents)

        // 续排逻辑：如果最后一页没排满，摘回来继续排
        if (textPages.isNotEmpty()) {
            val lastPage = textPages.last()
            val lastLine = lastPage.lines.lastOrNull()
            if (lastLine != null && !lastLine.isParagraphEnd) {
                // 最后一行不是段落结尾，说明页面没排满，摘回来续排
                AppLog.putReaderDebug("懒加载排版: 最后一页未排满，摘回续排，当前页行数=${lastPage.lines.size}")
                pendingTextPage = lastPage
                pendingTextPage.isResumed = true
                textPages.removeAt(textPages.lastIndex)
                // 恢复排版状态
                durY = lastLine.lineBottom
                stringBuilder.clear()
                // 重建 stringBuilder 内容（从该页第一行开始）
                for (line in lastPage.lines) {
                    stringBuilder.append(line.text)
                    if (line.isParagraphEnd) stringBuilder.append("\n")
                }
            } else if (lastLine != null && lastLine.isParagraphEnd && lastPage.height < visibleHeight) {
                // 段落结束了但页面还有空间，也摘回来续排
                AppLog.putReaderDebug("懒加载排版: 最后一页有剩余空间，摘回续排，当前高度=${lastPage.height}, 可视高度=$visibleHeight")
                pendingTextPage = lastPage
                textPages.removeAt(textPages.lastIndex)
                durY = lastPage.height
                stringBuilder.clear()
                for (line in lastPage.lines) {
                    stringBuilder.append(line.text)
                    if (line.isParagraphEnd) stringBuilder.append("\n")
                }
            }
        }

        val sb = StringBuffer()
        var isSetTypedImage = false
        var wordCount = 0

        for ((contentIndex, content) in processedContents.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (adaptSpecialStyle) {
                val text = content.trim()
                if (text == "[newpage]") {
                    prepareNextPageIfNeed()
                    continue
                } else if (text.startsWith("<usehtml>")) {
                    val endInt = text.lastIndexOf("<")
                    if (endInt > 9) {
                        setTypeHtml(imageStyle, book, text.substring(9, endInt))
                        continue
                    }
                }
            }
            var text = content.replace(srcReplaceChar, srcReplacementChar)
            if (isTextImageStyle) {
                val srcList = LinkedList<String>()
                val clickList = LinkedList<String?>()
                sb.setLength(0)
                val matcher = AppPattern.imgPattern.matcher(text)
                while (matcher.find()) {
                    matcher.group(1)?.let { src ->
                        val bubbleResult = tryParseForcedBubbleSrcWithClick(src)
                        srcList.add(bubbleResult.renderSrc)
                        clickList.add(bubbleResult.click)
                        matcher.appendReplacement(sb, srcReplaceStr)
                    }
                }
                matcher.appendTail(sb)
                text = sb.toString()
                wordCount += text.replace(noWordCountRegex, "").length
                setTypeText(
                    book,
                    text,
                    contentPaint,
                    contentPaintTextHeight,
                    contentPaintFontMetrics,
                    imageStyle,
                    srcList = srcList,
                    clickList = clickList,
                    bodyHighlightStyles = bodyHighlightStyles.takeIf { !content.contains("<img") },
                    bodyHighlightStart = bodyHighlightStyles.startAt(contentIndex),
                )
            } else {
                if (isSetTypedImage) {
                    isSetTypedImage = false
                    prepareNextPageIfNeed()
                }
                var start = 0
                val srcList = LinkedList<String>()
                val clickList = LinkedList<String?>()
                sb.setLength(0)
                var isFirstLine = true
                if (content.contains("<img")) {
                    val matcher = AppPattern.imgPattern.matcher(text)
                    while (matcher.find()) {
                        currentCoroutineContext().ensureActive()
                        val bubbleResult = tryParseForcedBubbleSrcWithClick(matcher.group(1)!!)
                        val imgSrc = bubbleResult.renderSrc
                        val isBubble = ParagraphBubbleRenderer.isBubbleSrc(imgSrc)
                        var style: String? = if (isBubble) "TEXT" else null
                        var click: String? = if (isBubble) bubbleResult.click else null
                        var imgSize = ImageProvider.getImageSize(book, imgSrc, ReadBook.bookSource)
                        val isAnimated = if (isBubble) false else ImageProvider.isGif(book, imgSrc, ReadBook.bookSource)
                        val urlMatcher = paramPattern.matcher(imgSrc)
                        if (urlMatcher.find()) {
                            var width: String? = null
                            val urlOptionStr = imgSrc.substring(urlMatcher.end())
                            GSON.fromJsonObject<Map<String, String>>(urlOptionStr).getOrNull()?.let { map ->
                                map.forEach { (key, value) ->
                                    when (key) {
                                        "style" -> style = value
                                        "width" -> width = value
                                        "click" -> click = value
                                    }
                                }
                            }
                            width?.let {
                                if (width.endsWith("%")) {
                                    width.dropLast(1).toIntOrNull()?.let { percentage ->
                                        val imgWidth = visibleWidth * percentage / 100
                                        val (sizeHeight, sizeWidth) = imgSize
                                        imgSize = Size(imgWidth, sizeHeight * imgWidth / sizeWidth)
                                    }
                                } else {
                                    width.toIntOrNull()?.let { width ->
                                        val (sizeHeight, sizeWidth) = imgSize
                                        imgSize = Size(width, sizeHeight * width / sizeWidth)
                                    }
                                }
                            }
                        }
                        if (style == null) {
                            style = if (imgSize.width < 80 && imgSize.height < 80) {
                                "text"
                            } else {
                                imageStyle
                            }
                        }
                        if (start < matcher.start()) {
                            sb.append(text.subSequence(start, matcher.start()))
                        }
                        when (style) {
                            "TEXT" -> {
                                sb.append(reviewChar)
                                srcList.add(imgSrc)
                                clickList.add(click)
                            }
                            "text" -> {
                                sb.append(srcReplaceChar)
                                srcList.add(imgSrc)
                                clickList.add(click)
                            }
                            else -> {
                                val textBefore = sb.toString()
                                if (textBefore.isNotBlank()) {
                                    wordCount += textBefore.replace(noWordCountRegex, "").length
                                    setTypeText(
                                        book,
                                        sb.toString(),
                                        contentPaint,
                                        contentPaintTextHeight,
                                        contentPaintFontMetrics,
                                        "TEXT",
                                        isFirstLine = isFirstLine,
                                        srcList = srcList,
                                        clickList = clickList,
                                    )
                                    sb.setLength(0)
                                    isFirstLine = false
                                }
                                setTypeImage(
                                    book,
                                    imgSrc,
                                    contentPaintTextHeight,
                                    style,
                                    imgSize,
                                    click,
                                    isAnimated,
                                )
                                isSetTypedImage = true
                            }
                        }
                        start = matcher.end()
                    }
                }
                if (start < content.length) {
                    if (isSetTypedImage) {
                        isSetTypedImage = false
                        prepareNextPageIfNeed()
                    }
                    val textAfter = content.subSequence(start, content.length)
                    sb.append(textAfter)
                }
                text = sb.toString()
                if (text.isNotBlank()) {
                    wordCount += text.replace(noWordCountRegex, "").length
                    setTypeText(
                        book,
                        text,
                        contentPaint,
                        contentPaintTextHeight,
                        contentPaintFontMetrics,
                        "TEXT",
                        isFirstLine = isFirstLine,
                        srcList = srcList,
                        clickList = clickList,
                        bodyHighlightStyles = bodyHighlightStyles.takeIf { !content.contains("<img") },
                        bodyHighlightStart = bodyHighlightStyles.startAt(contentIndex),
                    )
                }
            }
            pendingTextPage.lines.lastOrNull()?.isParagraphEnd = true
            stringBuilder.append("\n")
        }

        val textPage = pendingTextPage
        val endPadding = 20.dpToPx()
        val durYPadding = durY + endPadding
        if (textPage.height < durYPadding) {
            textPage.height = durYPadding
        } else {
            textPage.height += endPadding
        }
        textPage.text = stringBuilder.toString()
        currentCoroutineContext().ensureActive()
        onPageCompleted()

        pendingTextPage = TextPage()
        stringBuilder.clear()
        durY = 0f
        absStartX = paddingLeft
    }

    private fun onPageCompleted() {
        val textPage = pendingTextPage
        textPage.index = textPages.size
        textPage.chapterIndex = bookChapter.index
        textPage.chapterSize = chaptersSize
        textPage.title = displayTitle
        textPage.doublePage = doublePage
        textPage.paddingTop = paddingTop
        textPage.isCompleted = true
        textPage.textChapter = textChapter
        textPage.upLinesPosition()
        textPage.upRenderHeight()
        textPages.add(textPage)
        if (!textPage.isResumed) {
            channel.trySend(textPage)
        }
        try {
            listener?.onLayoutPageCompleted(textPages.lastIndex, textPage)
        } catch (e: Exception) {
            e.printStackTrace()
            AppLog.put("调用布局进度监听回调出错\n${e.localizedMessage}", e)
        }
    }

    private fun onCompleted() {
        channel.close()
        try {
            listener?.onLayoutCompleted()
        } catch (e: Exception) {
            e.printStackTrace()
            AppLog.put("调用布局进度监听回调出错\n${e.localizedMessage}", e)
        } finally {
            listener = null
        }

        // 初始排版完成后，处理排队等待的懒加载内容
        if (pendingLazyContents.isNotEmpty()) {
            kotlinx.coroutines.GlobalScope.launch(IO) {
                try {
                    appendMutex.withLock {
                        if (pendingLazyContents.isNotEmpty()) {
                            AppLog.putReaderDebug("懒加载排版: 初始排版完成，处理排队内容${pendingLazyContents.size}段")
                            appendContentInternal(pendingLazyContents)
                            pendingLazyContents.clear()
                        }
                    }
                } catch (e: Exception) {
                    AppLog.put("处理排队懒加载内容失败: ${e.localizedMessage}", e)
                }
            }
        }
    }

    private fun onException(e: Throwable) {
        channel.close(e)
        if (e is CancellationException) {
            listener = null
            return
        }
        try {
            listener?.onLayoutException(e)
        } catch (e: Exception) {
            e.printStackTrace()
            AppLog.put("调用布局进度监听回调出错\n${e.localizedMessage}", e)
        } finally {
            listener = null
        }
    }

    /**
     * 获取拆分完的章节数据
     */
    private suspend fun getTextChapter(
        book: Book,
        bookChapter: BookChapter,
        displayTitle: String,
        bookContent: BookContent,
    ) {
        val contents = preprocessBubbleJs(bookContent.textList)
        val imageStyle = book.getImageStyle()
        val isSingleImageStyle = imageStyle.equals(Book.imgStyleSingle, true)

        if (titleMode != 2 || bookChapter.isVolume || contents.isEmpty()) {
            var firstLine = true
            // 标题非隐藏
            displayTitle.splitNotBlank("\n").forEach { text ->
                val srcList = LinkedList<String>()
                val clickList = LinkedList<String?>()
                val titleImg = if (firstLine) {
                    firstLine = false
                    bookChapter.imgUrl
                } else {
                    null
                }
                val imgText = if (titleImg.isNullOrEmpty()) {
                    null
                } else {
                    val urlMatcher = paramPattern.matcher(titleImg)
                    var click: String? = null
                    var style: String? = if (ParagraphBubbleRenderer.isBubbleSrc(titleImg)) "TEXT" else null
                    var imgSize = ImageProvider.getImageSize(book, titleImg, ReadBook.bookSource)
                    val isAnimated = ImageProvider.isGif(book, titleImg, ReadBook.bookSource)
                    if (urlMatcher.find()) {
                        var width: String? = null
                        val urlOptionStr = titleImg.substring(urlMatcher.end())
                        GSON.fromJsonObject<Map<String, String>>(urlOptionStr).getOrNull()
                            ?.let { map ->
                                map.forEach { (key, value) ->
                                    when (key) {
                                        "style" -> style = value
                                        "width" -> width = value
                                        "click" -> click = value
                                    }
                                }
                            }
                        width?.let {
                            if (width.endsWith("%")) {
                                width.dropLast(1).toIntOrNull()?.let { percentage ->
                                    val imgWidth = visibleWidth * percentage / 100
                                    val (sizeHeight, sizeWidth) = imgSize
                                    imgSize = Size(imgWidth, sizeHeight * imgWidth / sizeWidth)
                                }
                            } else {
                                width.toIntOrNull()?.let { width ->
                                    val (sizeHeight, sizeWidth) = imgSize
                                    imgSize = Size(width, sizeHeight * width / sizeWidth)
                                }
                            }
                        }
                    }
                    if (style == null) {
                        style = if (imgSize.width < 80 && imgSize.height < 80) {
                            "text"
                        } else {
                            imageStyle
                        }
                    }
                    when (style) {
                        "text" -> {
                            srcList.add(titleImg)
                            clickList.add(click)
                            srcReplaceChar
                        }
                        "TEXT" -> {
                            srcList.add(titleImg)
                            clickList.add(click)
                            reviewChar
                        }
                        else -> {
                            setTypeImage(
                                book,
                                titleImg,
                                contentPaintTextHeight,
                                style,
                                imgSize,
                                click,
                                isAnimated,
                            )
                            null
                        }
                    }
                }
                setTypeText(
                    book,
                    if (imgText != null) text + imgText else text,
                    titlePaint,
                    titlePaintTextHeight,
                    titlePaintFontMetrics,
                    imageStyle,
                    srcList = srcList,
                    clickList = clickList,
                    isTitle = true,
                    emptyContent = contents.isEmpty(),
                    isVolumeTitle = bookChapter.isVolume,
                )
                pendingTextPage.lines.last().isParagraphEnd = true
                stringBuilder.append("\n")
            }
            durY += titleBottomSpacing

            // 如果是单图模式且当前页有内容，强制分页
            if (isSingleImageStyle && pendingTextPage.lines.isNotEmpty() && contents.isNotEmpty()) {
                prepareNextPageIfNeed()
            }
        }

        val isTextImageStyle = imageStyle.equals(Book.imgStyleText, true)
        val bodyHighlightStyles = buildBodyHighlightStyles(contents)

        val sb = StringBuffer()
        var isSetTypedImage = false
        var wordCount = 0
        contents.forEachIndexed { contentIndex, content ->
            currentCoroutineContext().ensureActive()
            if (adaptSpecialStyle) {
                val text = content.trim()
                if (text == "[newpage]") {
                    prepareNextPageIfNeed()
                    return@forEachIndexed
                } else if (text.startsWith("<usehtml>")) {
                    val endInt = text.lastIndexOf("<")
                    if (endInt > 9) {
                        setTypeHtml(imageStyle, book, text.substring(9, endInt))
                        return@forEachIndexed
                    }
                }
            }
            var text = content.replace(srcReplaceChar, srcReplacementChar)
            if (isTextImageStyle) {
                // 图片样式为文字嵌入类型
                val srcList = LinkedList<String>()
                val clickList = LinkedList<String?>()
                sb.setLength(0)
                val matcher = AppPattern.imgPattern.matcher(text)
                while (matcher.find()) {
                    matcher.group(1)?.let { src ->
                        val bubbleResult = tryParseForcedBubbleSrcWithClick(src)
                        srcList.add(bubbleResult.renderSrc)
                        clickList.add(bubbleResult.click)
                        matcher.appendReplacement(sb, srcReplaceStr)
                    }
                }
                matcher.appendTail(sb)
                text = sb.toString()
                wordCount += text.replace(noWordCountRegex, "").length
                setTypeText(
                    book,
                    text,
                    contentPaint,
                    contentPaintTextHeight,
                    contentPaintFontMetrics,
                    imageStyle,
                    srcList = srcList,
                    clickList = clickList,
                    bodyHighlightStyles = bodyHighlightStyles.takeIf { !content.contains("<img") },
                    bodyHighlightStart = bodyHighlightStyles.startAt(contentIndex),
                )
            } else {
                if (isSingleImageStyle && isSetTypedImage) {
                    isSetTypedImage = false
                    prepareNextPageIfNeed()
                }
                var start = 0
                val srcList = LinkedList<String>()
                val clickList = LinkedList<String?>()
                sb.setLength(0)
                var isFirstLine = true
                if (content.contains("<img")) {
                    val matcher = AppPattern.imgPattern.matcher(text)
                    while (matcher.find()) {
                        currentCoroutineContext().ensureActive()
                        val bubbleResult = tryParseForcedBubbleSrcWithClick(matcher.group(1)!!)
                        val imgSrc = bubbleResult.renderSrc
                        val isBubble = ParagraphBubbleRenderer.isBubbleSrc(imgSrc)
                        var style: String? = if (isBubble) "TEXT" else null
                        var click: String? = if (isBubble) bubbleResult.click else null
                        var imgSize = ImageProvider.getImageSize(book, imgSrc, ReadBook.bookSource)
                        val isAnimated = if (isBubble) false else ImageProvider.isGif(book, imgSrc, ReadBook.bookSource)
                        val urlMatcher = paramPattern.matcher(imgSrc)
                        if (urlMatcher.find()) {
                            var width: String? = null
                            val urlOptionStr = imgSrc.substring(urlMatcher.end())
                            GSON.fromJsonObject<Map<String, String>>(urlOptionStr).getOrNull()?.let { map ->
                                map.forEach { (key, value) ->
                                    when (key) {
                                        "style" -> style = value
                                        "width" -> width = value
                                        "click" -> click = value
                                    }
                                }
                            }
                            width?.let {
                                if (width.endsWith("%")) {
                                    width.dropLast(1).toIntOrNull()?.let { percentage ->
                                        val imgWidth = visibleWidth * percentage / 100
                                        val (sizeHeight, sizeWidth) = imgSize
                                        imgSize = Size(imgWidth, sizeHeight * imgWidth / sizeWidth)
                                    }
                                } else {
                                    width.toIntOrNull()?.let { width ->
                                        val (sizeHeight, sizeWidth) = imgSize
                                        imgSize = Size(width, sizeHeight * width / sizeWidth)
                                    }
                                }
                            }
                        }
                        if (style == null) {
                            style = if (imgSize.width < 80 && imgSize.height < 80) {
                                "text"
                            } else {
                                imageStyle
                            }
                        }
                        if (start < matcher.start()) {
                            sb.append(text.subSequence(start, matcher.start()))
                        }
                        when (style) {
                            "TEXT" -> {
                                sb.append(reviewChar)
                                srcList.add(imgSrc)
                                clickList.add(click)
                            }
                            "text" -> {
                                sb.append(srcReplaceChar)
                                srcList.add(imgSrc)
                                clickList.add(click)
                            }
                            else -> {
                                val textBefore = sb.toString()
                                if (textBefore.isNotBlank()) {
                                    wordCount += textBefore.replace(noWordCountRegex, "").length
                                    setTypeText(
                                        book,
                                        sb.toString(),
                                        contentPaint,
                                        contentPaintTextHeight,
                                        contentPaintFontMetrics,
                                        "TEXT",
                                        isFirstLine = isFirstLine,
                                        srcList = srcList,
                                        clickList = clickList,
                                    )
                                    sb.setLength(0)
                                    isFirstLine = false
                                }
                                setTypeImage(
                                    book,
                                    imgSrc,
                                    contentPaintTextHeight,
                                    style,
                                    imgSize,
                                    click,
                                    isAnimated,
                                )
                                isSetTypedImage = true
                            }
                        }
                        start = matcher.end()
                    }
                }
                if (start < content.length) {
                    if (isSingleImageStyle && isSetTypedImage) {
                        isSetTypedImage = false
                        prepareNextPageIfNeed()
                    }
                    val textAfter = content.subSequence(start, content.length)
                    sb.append(textAfter)
                }
                text = sb.toString()
                if (text.isNotBlank()) {
                    wordCount += text.replace(noWordCountRegex, "").length
                    setTypeText(
                        book,
                        text,
                        contentPaint,
                        contentPaintTextHeight,
                        contentPaintFontMetrics,
                        "TEXT",
                        isFirstLine = isFirstLine,
                        srcList = srcList,
                        clickList = clickList,
                        bodyHighlightStyles = bodyHighlightStyles.takeIf { !content.contains("<img") },
                        bodyHighlightStart = bodyHighlightStyles.startAt(contentIndex),
                    )
                }
            }
            pendingTextPage.lines.last().isParagraphEnd = true
            stringBuilder.append("\n")
        }
        val chapterWordCount = StringUtils.wordCountFormat(wordCount.toString())
        bookChapter.wordCount = chapterWordCount
        appDb.bookChapterDao.upWordCount(bookChapter.bookUrl, bookChapter.url, chapterWordCount)
        val textPage = pendingTextPage
        val endPadding = 20.dpToPx()
        val durYPadding = durY + endPadding
        if (textPage.height < durYPadding) {
            textPage.height = durYPadding
        } else {
            textPage.height += endPadding
        }
        textPage.text = stringBuilder.toString()
        currentCoroutineContext().ensureActive()
        onPageCompleted()
        pendingTextPage = TextPage()
        stringBuilder.clear()
        durY = 0f
        absStartX = paddingLeft
        onCompleted()
    }

    /**
     * 排版图片
     */
    private suspend fun setTypeImage(
        book: Book,
        src: String,
        textHeight: Float,
        imageStyle: String?,
        size: Size,
        click: String?,
        isAnimated: Boolean = false,
    ) {
        if (size.width > 0 && size.height > 0) {
            prepareNextPageIfNeed(durY)
            var height = size.height
            var width = size.width
            when (imageStyle?.uppercase()) {
                Book.imgStyleFull -> {
                    width = visibleWidth
                    height = size.height * visibleWidth / size.width
                    if (pageAnim != PageAnim.scrollPageAnim && height > visibleHeight - durY) {
                        if (height > visibleHeight) {
                            width = width * visibleHeight / height
                            height = visibleHeight
                        }
                        prepareNextPageIfNeed(durY + height)
                    }
                }

                Book.imgStyleSingle -> {
                    width = visibleWidth
                    height = size.height * visibleWidth / size.width
                    if (height > visibleHeight) {
                        width = width * visibleHeight / height
                        height = visibleHeight
                    }
                    if (durY > 0f) {
                        prepareNextPageIfNeed()
                    }

                    // 图片竖直方向居中：调整 Y 坐标
                    if (height < visibleHeight) {
                        val adjustHeight = (visibleHeight - height) / 2f
                        durY = adjustHeight // 将 Y 坐标设置为居中位置
                    }
                }

                else -> {
                    if (size.width > visibleWidth) {
                        height = size.height * visibleWidth / size.width
                        width = visibleWidth
                    }
                    if (height > visibleHeight) {
                        width = width * visibleHeight / height
                        height = visibleHeight
                    }
                    prepareNextPageIfNeed(durY + height)
                }
            }
            val textLine = TextLine(isImage = true)
            textLine.text = " "
            textLine.lineTop = durY + paddingTop
            durY += height
            textLine.lineBottom = durY + paddingTop
            val (start, end) = if (visibleWidth > width) {
                when (imageStyle?.uppercase()) {
                    "RIGHT" -> Pair(visibleWidth - width, visibleWidth)
                    "LEFT" -> Pair(0f, width)
                    else -> {
                        val adjustWidth = (visibleWidth - width) / 2f
                        Pair(adjustWidth, adjustWidth + width)
                    }
                }
            } else {
                Pair(0f, width)
            }
            textLine.addColumn(
                ImageColumn(
                    start = absStartX + start.toFloat(),
                    end = absStartX + end.toFloat(),
                    src = src,
                    click = click,
                    isAnimated = isAnimated,
                ),
            )
            calcTextLinePosition(textPages, textLine, stringBuilder.length)
            stringBuilder.append(" ") // 确保翻页时索引计算正确
            pendingTextPage.addLine(textLine)
        }
        durY += textHeight * paragraphSpacing / 10f
    }

    /**
     * 排版html样式
     */
    private suspend fun setTypeHtml(
        imageStyle: String?,
        book: Book,
        htmlContent: String,
    ) {
        val textViewTagHandler = TextViewTagHandler()
        val spanned = applyHighlightRules(
            SpannableStringBuilder(
                htmlContent.parseAsHtml(
                    HtmlCompat.FROM_HTML_MODE_COMPACT,
                    tagHandler = textViewTagHandler,
                ),
            ),
        )
        val width = visibleWidth
        val textPaint = contentPaint
        // 九宫格"强制"策略：HTML 走 StaticLayout，只有把推开的宽度挂到邻字身上（Span），
        // 断行与两端对齐才会一起生效
        val neighborPush = computeNeighborPush(
            spanned,
            collectForcedBleedSegments(spanned),
            textPaint,
        ) { index -> textPaint.measureText(spanned, index, index + 1) }
        columnTrimEnd = neighborPush?.trimEnd
        columnIndentExtra = neighborPush?.indentAdd ?: 0f
        neighborPush?.let { push ->
            for (i in push.widthAdd.indices) {
                if (push.widthAdd[i] > 0f) {
                    spanned.setSpan(
                        HighlightSpacingSpan(push.widthAdd[i]),
                        i,
                        i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
        }
        val textColor = ReadBookConfig.textColor
        if (textPaint.color != textColor) {
            textPaint.color = textColor
        }
        val staticLayout = if (atLeastApi28) {
            StaticLayout.Builder.obtain(spanned, 0, spanned.length, textPaint, width)
                .setIncludePad(true)
                .setUseLineSpacingFromFallbacks(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                spanned,
                textPaint,
                width,
                Layout.Alignment.ALIGN_NORMAL,
                1f,
                0f,
                true,
            )
        }
        val tempPaint = TextPaint(textPaint)
        for (lineIndex in 0 until staticLayout.lineCount) {
            val lineStart = staticLayout.getLineStart(lineIndex)
            val lineEnd = staticLayout.getLineEnd(lineIndex)
            if (lineStart == lineEnd) { // 这一行没有内容，跳过
                continue
            }
            val textLine = TextLine(isHtml = true)
            val lineText = StringBuilder()
            val lineLeft = staticLayout.getLineLeft(lineIndex)
            textLine.startX = absStartX + lineLeft // x坐标
            val mLineTop = staticLayout.getLineTop(lineIndex).toFloat()
            val mLineBottom = staticLayout.getLineBottom(lineIndex).toFloat()
            val lineHeight = mLineBottom - mLineTop
            prepareNextPageIfNeed(durY + lineHeight)
            textLine.upTopBottom(durY, lineHeight, textPaint.fontMetrics) // y坐标

            val columns = mutableListOf<BaseColumn>()
            var charIndex = lineStart
            while (charIndex < lineEnd) {
                val char = spanned[charIndex].toString()
                lineText.append(char)
                if (char == "\n") {
                    textLine.isParagraphEnd = true
                    durY += lineHeight * paragraphSpacing / 10f // 段距
                    charIndex++
                    continue
                }
                val charX = staticLayout.getPrimaryHorizontal(charIndex)
                val textSize = extractTextSize(spanned, charIndex, textPaint.textSize)
                val textColor = extractTextColor(spanned, charIndex)
                val linkUrl = extractLinkUrl(spanned, charIndex)
                val highlightStyle = extractHighlightStyle(spanned, charIndex)
                val underlineMode = highlightStyle?.underlineMode ?: 0
                val underlineColor = highlightStyle?.underlineColor
                val bgColor = highlightStyle?.bgColor
                val bgImage = highlightStyle?.bgImage ?: ""
                val bgImageFit = highlightStyle?.bgImageFit ?: 0
                val bgImageScale = highlightStyle?.bgImageScale ?: 1f
                val npLeft = highlightStyle?.npLeft ?: 0.1f
                val npTop = highlightStyle?.npTop ?: 0.1f
                val npRight = highlightStyle?.npRight ?: 0.1f
                val npBottom = highlightStyle?.npBottom ?: 0.1f
                val bgBleedMode = highlightStyle?.bgBleedMode ?: HighlightRule.BLEED_SMART
                val bgSpacingLeft = highlightStyle?.bgSpacingLeft ?: 0f
                val bgSpacingRight = highlightStyle?.bgSpacingRight ?: 0f
                val bgSpacingTop = highlightStyle?.bgSpacingTop ?: 0f
                val bgSpacingBottom = highlightStyle?.bgSpacingBottom ?: 0f
                val highlightFontPath = extractFontPath(spanned, charIndex)
                val charRight = if (charIndex + 1 < lineEnd) {
                    staticLayout.getPrimaryHorizontal(charIndex + 1)
                } else {
                    tempPaint.textSize = textSize
                    // 行尾字符无下一列可取坐标，需按该字符实际字体测宽，保持与绘制一致
                    tempPaint.typeface = HighlightFontCache.getTypefaceFor(
                        highlightFontPath,
                        textPaint.typeface,
                    ) ?: textPaint.typeface
                    val charWidth = tempPaint.measureText(char)
                    charX + charWidth
                }
                var needAddText = true
                spanned.getSpans(charIndex, charIndex + 1, ImageSpan::class.java).firstOrNull()?.let { span ->
                    // 处理图片
                    val source = span.source ?: return@let
                    val urlMatcher = paramPattern.matcher(source)
                    if (urlMatcher.find()) {
                        val urlOptionStr = source.substring(urlMatcher.end())
                        val urlOption = GSON.fromJsonObject<Map<String, String>>(urlOptionStr).getOrNull() ?: return@let
                        var iStyle = urlOption["style"]
                        val width = urlOption["width"]
                        // 强制软件气泡检测（提前到 getImageSize 之前，避免对气泡 URL 执行无意义的网络请求）
                        val bubbleResult = tryParseForcedBubbleSrcWithClick(source)
                        val forcedBubbleSrc = bubbleResult.renderSrc
                        val isForcedBubble = ParagraphBubbleRenderer.isBubbleSrc(forcedBubbleSrc) &&
                            !ParagraphBubbleRenderer.isBubbleSrc(source)
                        val click = if (isForcedBubble) {
                            bubbleResult.click
                        } else {
                            listOfNotNull(urlOption["pclick"]?.takeIf { it.isNotBlank() }, urlOption["click"]?.takeIf { it.isNotBlank() }).firstOrNull()
                        }
                        val effectiveSrc = if (isForcedBubble) forcedBubbleSrc else source
                        var imgSize = ImageProvider.getImageSize(book, effectiveSrc, ReadBook.bookSource)
                        val isAnimated = if (isForcedBubble) false else ImageProvider.isGif(book, source, ReadBook.bookSource)
                        width?.let {
                            if (width.endsWith("%")) {
                                width.dropLast(1).toIntOrNull()?.let { percentage ->
                                    val imgWidth = visibleWidth * percentage / 100
                                    val (sizeHeight, sizeWidth) = imgSize
                                    imgSize = Size(imgWidth, sizeHeight * imgWidth / sizeWidth)
                                }
                            } else {
                                width.toIntOrNull()?.let { width ->
                                    val (sizeHeight, sizeWidth) = imgSize
                                    imgSize = Size(width, sizeHeight * width / sizeWidth)
                                }
                            }
                        }
                        if (isForcedBubble) {
                            iStyle = "TEXT"
                        } else if (iStyle == null) {
                            iStyle = if (imgSize.width < 80 && imgSize.height < 80) {
                                "text"
                            } else {
                                imageStyle
                            }
                        }
                        when (iStyle?.uppercase()) {
                            "TEXT" -> {
                                val renderSrc = if (isForcedBubble) forcedBubbleSrc else source
                                if (!ParagraphBubbleRenderer.isBubbleSrc(renderSrc)) {
                                    ImageProvider.cacheImage(book, renderSrc, ReadBook.bookSource)
                                }
                                columns.add(
                                    ImageColumn(
                                        start = absStartX + charX,
                                        end = absStartX + charRight,
                                        src = renderSrc,
                                        click = click,
                                        isAnimated = if (ParagraphBubbleRenderer.isBubbleSrc(renderSrc)) false else isAnimated,
                                    ),
                                )
                            }
                            else -> {
                                setTypeImage(
                                    book,
                                    source,
                                    contentPaintTextHeight,
                                    iStyle,
                                    imgSize,
                                    click,
                                    isAnimated,
                                )
                            }
                        }
                    } else {
                        val bubbleResult = tryParseForcedBubbleSrcWithClick(source)
                        val forcedSrc = bubbleResult.renderSrc
                        val isBubble = ParagraphBubbleRenderer.isBubbleSrc(forcedSrc)
                        if (isBubble) {
                            columns.add(
                                ImageColumn(
                                    start = absStartX + charX,
                                    end = absStartX + charRight,
                                    src = forcedSrc,
                                    click = bubbleResult.click,
                                    isAnimated = false,
                                ),
                            )
                        } else {
                            val imgSize = ImageProvider.getImageSize(book, source, ReadBook.bookSource)
                            val isAnimated = ImageProvider.isGif(book, source, ReadBook.bookSource)
                            setTypeImage(
                                book,
                                source,
                                contentPaintTextHeight,
                                imageStyle,
                                imgSize,
                                null,
                                isAnimated,
                            )
                        }
                    }
                    needAddText = false
                }
                // 只认自定义标签用的 ReplacementSpan：邻字外推挂的 HighlightSpacingSpan 不算
                spanned.getSpans(charIndex, charIndex + 1, ReplacementSpan::class.java)
                    .firstOrNull { it !is HighlightSpacingSpan }?.let { _ ->
                    // 自定义标签
                    if (char == HR_PLACE_CHAR) {
                        columns.add(
                            TextHtmlColumn(
                                absStartX.toFloat(),
                                (absStartX + width - paddingRight).toFloat(),
                                HR_PLACE_STR,
                                textSize,
                                textColor,
                                linkUrl,
                                underlineMode,
                                underlineColor,
                                bgColor = bgColor,
                                bgImage = bgImage,
                                bgImageFit = bgImageFit,
                                bgImageScale = bgImageScale,
                                npLeft = npLeft,
                                npTop = npTop,
                                npRight = npRight,
                                npBottom = npBottom,
                                bgBleedMode = bgBleedMode,
                                bgSpacingLeft = bgSpacingLeft,
                                bgSpacingRight = bgSpacingRight,
                                bgSpacingTop = bgSpacingTop,
                                bgSpacingBottom = bgSpacingBottom,
                                fontPath = highlightFontPath,
                            ),
                        )
                        needAddText = false
                    }
                }
                if (needAddText) {
                    columns.add(
                        TextHtmlColumn(
                            absStartX + charX,
                            // 九宫格"强制"策略给最后一个字加的宽度要扣掉，否则背景会跟着一起变宽
                            absStartX + charRight - (columnTrimEnd?.getOrNull(charIndex) ?: 0f),
                            char,
                            textSize,
                            textColor,
                            linkUrl,
                            underlineMode,
                            underlineColor,
                            bgColor = bgColor,
                            bgImage = bgImage,
                            bgImageFit = bgImageFit,
                            bgImageScale = bgImageScale,
                            npLeft = npLeft,
                            npTop = npTop,
                            npRight = npRight,
                            npBottom = npBottom,
                            bgBleedMode = bgBleedMode,
                            bgSpacingLeft = bgSpacingLeft,
                            bgSpacingRight = bgSpacingRight,
                            bgSpacingTop = bgSpacingTop,
                            bgSpacingBottom = bgSpacingBottom,
                            fontPath = highlightFontPath,
                        ),
                    )
                }
                charIndex++
                if (charIndex == lineEnd && lineIndex == staticLayout.lineCount - 1) {
                    textLine.isParagraphEnd = true
                    durY += lineHeight * paragraphSpacing / 10f // 段距
                }
            }
            textLine.text = lineText.toString()
            if (textFullJustify && !textLine.isParagraphEnd) {
                justifyHtmlLine(columns, textLine, visibleWidth)
            } else {
                textLine.addColumns(columns)
            }
            calcTextLinePosition(textPages, textLine, stringBuilder.length)
            stringBuilder.append(lineText)
            val textPage = pendingTextPage
            textPage.addLine(textLine)
            durY += lineHeight * lineSpacingExtra // 行距
            if (textPage.height < durY) {
                textPage.height = durY
            }
        }
    }

    /**
     * 对HTML行进行两端对齐
     */
    private fun justifyHtmlLine(
        columns: MutableList<BaseColumn>,
        textLine: TextLine,
        lineWidth: Int,
    ) {
        if (columns.isEmpty()) return
        // 计算当前行的总宽度
        val firstCol = columns.first()
        val lastCol = columns.last()
        val currentWidth = lastCol.end - firstCol.start
        // 计算剩余空间
        val residualWidth = lineWidth - currentWidth

        if (residualWidth <= 0) {
            textLine.addColumns(columns)
            return
        }

        // 统计空格数量
        val spaceCount = columns.count {
            (it as? TextBaseColumn)?.charData == " "
        }

        if (spaceCount > 1) {
            // 多个空格：调整空格间距
            val spaceIncrement = residualWidth / spaceCount
            textLine.wordSpacing = spaceIncrement

            // 重新计算字符位置
            var currentX = firstCol.start
            for (i in columns.indices) {
                val col = columns[i]
                val width = col.end - col.start

                if ((col as? TextBaseColumn)?.charData == " " && i != columns.lastIndex) {
                    // 空格，增加额外的间距
                    col.start = currentX
                    col.end = currentX + width + spaceIncrement
                    currentX = col.end
                } else {
                    // 非空格或最后一个字符
                    col.start = currentX
                    col.end = currentX + width
                    currentX = col.end
                }

                textLine.addColumn(col)
            }
        } else {
            // 没有或只有一个空格：调整字符间距
            val gapCount = columns.lastIndex
            if (gapCount > 0) {
                val charIncrement = residualWidth / gapCount
                var currentX = firstCol.start
                for (i in columns.indices) {
                    val col = columns[i]
                    val width = col.end - col.start

                    if (i != columns.lastIndex) {
                        // 非最后一个字符，增加额外的间距
                        col.start = currentX
                        col.end = currentX + width + charIncrement
                        currentX = col.end
                    } else {
                        // 最后一个字符，不增加额外间距
                        col.start = currentX
                        col.end = currentX + width
                    }

                    textLine.addColumn(col)
                }
            } else {
                // 只有一个字符，不需要调整
                textLine.addColumns(columns)
            }
        }
    }

    private fun extractTextSize(spanned: Spanned, index: Int, defaultSize: Float): Float {
        val relativeSpans = spanned.getSpans(index, index + 1, RelativeSizeSpan::class.java)
        // 如果有 RelativeSizeSpan，基于基准大小计算
        relativeSpans.firstOrNull()?.let { span ->
            return defaultSize * span.sizeChange
        }
//        val sizeSpans = spanned.getSpans(index, index + 1, AbsoluteSizeSpan::class.java)
//        sizeSpans.firstOrNull()?.let { span ->
//            return span.size.toFloat()
//        }
        return defaultSize
    }

    private fun extractTextColor(spanned: Spanned, index: Int): Int? {
        val foregroundSpans = spanned.getSpans(index, index + 1, ForegroundColorSpan::class.java)
        return foregroundSpans.lastOrNull()?.foregroundColor
    }

    private fun extractFontPath(spanned: Spanned, index: Int): String {
        val spans = spanned.getSpans(index, index + 1, HighlightTypefaceSpan::class.java)
        return spans.lastOrNull()?.fontPath.orEmpty()
    }

    /**
     * 高亮规则指定字体时，按高亮字体重新测量对应字符宽度，
     * 保证排版宽度与最终绘制一致。
     */
    private fun adjustWidthsForHighlightFont(
        text: String,
        widthsArray: FloatArray,
        charStyles: Array<CharStyle?>?,
        textPaint: TextPaint,
    ) {
        if (charStyles == null) return
        for (i in text.indices) {
            val fontPath = charStyles[i]?.font
            if (fontPath.isNullOrEmpty()) continue
            // 原 width 为 0 的下标属于代理对/组合字符簇的延续，重测会破坏聚类
            if (widthsArray[i] <= 0f) continue
            val typeface = HighlightFontCache.getTypefaceFor(fontPath, textPaint.typeface) ?: continue
            highlightFontPaint.textSize = textPaint.textSize
            highlightFontPaint.letterSpacing = textPaint.letterSpacing
            highlightFontPaint.textScaleX = textPaint.textScaleX
            highlightFontPaint.textSkewX = textPaint.textSkewX
            highlightFontPaint.typeface = typeface
            widthsArray[i] = highlightFontPaint.measureText(text, i, i + 1)
        }
    }

    /**
     * 存在高亮字体时，把命中区间包成带 HighlightTypefaceSpan 的 SpannableString，
     * 让 StaticLayout 断行测量与逐字符列宽使用同一套字体，避免行宽错位。
     */
    private fun buildFontAwareLayoutText(
        text: String,
        charStyles: Array<CharStyle?>?,
        widthAdd: FloatArray? = null,
    ): CharSequence {
        if (charStyles == null) return text
        var spanStart = -1
        var spanFont = ""
        var spannable: SpannableString? = null
        for (i in 0..text.length) {
            val font = if (i < text.length) charStyles[i]?.font.orEmpty() else ""
            if (font != spanFont) {
                if (spanFont.isNotEmpty() && spanStart in 0 until i) {
                    val sb = spannable ?: SpannableString(text).also { spannable = it }
                    sb.setSpan(
                        HighlightTypefaceSpan(spanFont),
                        spanStart,
                        i,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                spanStart = i
                spanFont = font
            }
        }
        // 邻字外推：把多占的宽度挂到邻字第身上，StaticLayout 断行与两端对齐才会按真实宽度计算
        if (widthAdd != null) {
            for (i in widthAdd.indices) {
                if (widthAdd[i] > 0f) {
                    val sb = spannable ?: SpannableString(text).also { spannable = it }
                    sb.setSpan(
                        HighlightSpacingSpan(widthAdd[i]),
                        i,
                        i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
        }
        return spannable ?: text
    }

    private fun extractHighlightStyle(spanned: CharSequence, index: Int): HighlightStyleSpan? {
        val spans = (spanned as? Spanned)?.getSpans(
            index,
            index + 1,
            HighlightStyleSpan::class.java,
        ) ?: return null
        if (spans.isEmpty()) return null
        var underlineMode = 0
        var underlineColor = 0xFF63C37D.toInt()
        var underlineWidth = 1f
        var underlineOffset = 2f
        var underlineSvgPath = ""
        var bgColor: Int? = null
        var bgImage = ""
        var bgImageFit = 0
        var bgImageScale = 1f
        var npLeft = 0.1f
        var npTop = 0.1f
        var npRight = 0.1f
        var npBottom = 0.1f
        var bgBleedMode = HighlightRule.BLEED_SMART
        var bgSpacingLeft = 0f
        var bgSpacingRight = 0f
        var bgSpacingTop = 0f
        var bgSpacingBottom = 0f
        var hasUnderline = false
        var hasBgImage = false
        var hasBgColor = false
        spans.forEach { span ->
            if (span.underlineMode != 0) {
                underlineMode = span.underlineMode
                underlineColor = span.underlineColor
                underlineWidth = span.underlineWidth
                underlineOffset = span.underlineOffset
                underlineSvgPath = span.underlineSvgPath
                hasUnderline = true
            }
            if (span.bgImage.isNotEmpty()) {
                bgImage = span.bgImage
                bgImageFit = span.bgImageFit
                bgImageScale = span.bgImageScale
                npLeft = span.npLeft
                npTop = span.npTop
                npRight = span.npRight
                npBottom = span.npBottom
                bgBleedMode = span.bgBleedMode
                bgSpacingLeft = span.bgSpacingLeft
                bgSpacingRight = span.bgSpacingRight
                bgSpacingTop = span.bgSpacingTop
                bgSpacingBottom = span.bgSpacingBottom
                hasBgImage = true
            }
            if (span.bgColor != null) {
                bgColor = span.bgColor
                hasBgColor = true
            }
        }
        if (!hasUnderline && !hasBgImage && !hasBgColor) return null
        return HighlightStyleSpan(
            underlineMode = if (hasUnderline) underlineMode else 0,
            underlineColor = underlineColor,
            underlineWidth = underlineWidth,
            underlineOffset = underlineOffset,
            underlineSvgPath = if (hasUnderline) underlineSvgPath else "",
            bgColor = if (hasBgColor) bgColor else null,
            bgImage = if (hasBgImage) bgImage else "",
            bgImageFit = if (hasBgImage) bgImageFit else 0,
            bgImageScale = if (hasBgImage) bgImageScale else 1f,
            npLeft = if (hasBgImage) npLeft else 0.1f,
            npTop = if (hasBgImage) npTop else 0.1f,
            npRight = if (hasBgImage) npRight else 0.1f,
            npBottom = if (hasBgImage) npBottom else 0.1f,
            bgBleedMode = bgBleedMode,
            bgSpacingLeft = bgSpacingLeft,
            bgSpacingRight = bgSpacingRight,
            bgSpacingTop = bgSpacingTop,
            bgSpacingBottom = bgSpacingBottom,
        )
    }

    private fun extractLinkUrl(spanned: Spanned, index: Int): String? {
        // 检查URLSpan（超链接）
        val urlSpans = spanned.getSpans(index, index + 1, URLSpan::class.java)
        urlSpans.firstOrNull()?.let { span ->
            return span.url
        }
        return null
    }

    /**
     * 对整章正文做一次高亮匹配，构建每字符样式数组。
     *
     * 与逐段建 SpannableStringBuilder 再 setSpan 的旧方案等价，
     * 但每条规则只产生一个共享样式对象，命中只做数组写入，
     * 排版期逐字符消费改为数组下标访问。
     */
    private fun buildBodyHighlightStyles(contents: List<String>): BodyHighlightStyles {
        val starts = ArrayList<Int>(contents.size)
        val fullText = StringBuilder()
        contents.forEachIndexed { index, content ->
            starts.add(fullText.length)
            fullText.append(content.replace(srcReplaceChar, srcReplacementChar))
            if (index != contents.lastIndex) {
                fullText.append('\n')
            }
        }
        if (fullText.isEmpty()) {
            return BodyHighlightStyles(null, starts)
        }
        return BodyHighlightStyles(
            createHighlightStyles(fullText.toString(), isTitle = false),
            starts,
        )
    }

    /**
     * 对指定文本按启用的高亮规则生成每字符样式数组，无命中时返回 null。
     * 重叠规则按字段级合并，与旧 Span 方案的合并语义一致。
     */
    private fun createHighlightStyles(text: String, isTitle: Boolean): Array<CharStyle?>? {
        if (text.isEmpty()) return null
        var styles: Array<CharStyle?>? = null
        compiledHighlightRules.forEach { compiled ->
            if (!compiled.rule.appliesTo(isTitle, book.name, book.origin)) return@forEach
            compiled.regex.findAll(text).forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1
                if (start >= end) return@forEach
                val style = compiled.charStyle
                val active = styles ?: arrayOfNulls<CharStyle>(text.length).also { styles = it }
                for (i in start until end) {
                    active[i] = when (val existing = active[i]) {
                        null -> style
                        else -> existing.mergedWith(style)
                    }
                }
            }
        }
        return styles
    }

    private fun applyHighlightRules(
        spannable: SpannableStringBuilder,
        isTitle: Boolean = false,
    ): SpannableStringBuilder {
        compiledHighlightRules.forEach { compiled ->
            // 按标题/正文作用域和书籍作用域过滤
            if (!compiled.rule.appliesTo(isTitle, book.name, book.origin)) return@forEach
            applyRuleSpans(spannable, compiled.rule, compiled.regex)
        }
        return spannable
    }

    private fun applyRuleSpans(
        spannable: SpannableStringBuilder,
        rule: HighlightRule,
        regex: Regex,
    ) {
        regex.findAll(spannable).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (start >= end) return@forEach
            applyRuleSpan(spannable, rule, start, end)
        }
    }

    private fun applyRuleSpan(
        spannable: SpannableStringBuilder,
        rule: HighlightRule,
        start: Int,
        end: Int,
    ) {
        val style = HighlightRuleStyle.from(rule)
        style.textColor?.let { color ->
            spannable.setSpan(
                ForegroundColorSpan(color),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        if (style.font.isNotBlank()) {
            // 字体属于影响测量的样式，StaticLayout 排版需要 MetricAffectingSpan
            spannable.setSpan(
                HighlightTypefaceSpan(style.font),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        if (style.hasDecoration) {
            spannable.setSpan(
                HighlightStyleSpan(style),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun setTypeText(
        book: Book,
        text: String,
        textPaint: TextPaint,
        textHeight: Float,
        fontMetrics: Paint.FontMetrics,
        imageStyle: String?,
        isTitle: Boolean = false,
        isFirstLine: Boolean = true,
        emptyContent: Boolean = false,
        isVolumeTitle: Boolean = false,
        srcList: LinkedList<String>? = null,
        clickList: LinkedList<String?>?,
        bodyHighlightStyles: BodyHighlightStyles? = null,
        bodyHighlightStart: Int? = null,
    ) {
        // 整章样式数组按段落切片；无整章上下文时（标题、被图片分割的残段）就地扫描
        val charStyles = if (!isTitle && bodyHighlightStyles != null && bodyHighlightStart != null) {
            bodyHighlightStyles.stylesAt(bodyHighlightStart, text.length)
        } else {
            createHighlightStyles(text, isTitle)
        }
        val widthsArray = allocateFloatArray(text.length)
        textPaint.getTextWidthsCompat(text, widthsArray, reviewCharWidth)
        adjustWidthsForHighlightFont(text, widthsArray, charStyles, textPaint)
        // 调整气泡内联宽度
        if (srcList != null && srcList.isNotEmpty()) {
            var imageIndex = 0
            text.forEachIndexed { index, char ->
                if (char == srcReplaceChar || char == reviewChar) {
                    val src = srcList.getOrNull(imageIndex)
                    if (src != null && ParagraphBubbleRenderer.isBubbleSrc(src)) {
                        widthsArray[index] = ParagraphBubbleRenderer.inlineWidth(widthsArray[index])
                    }
                    imageIndex++
                }
            }
        }
        // 九宫格"强制"策略：把左右邻字向外推开一个正文字距。加宽写进 widthsArray 参与断行与两端
        // 对齐，列末尾再按 trimEnd 扣回来，保证背景本身不被撑宽（见 computeNeighborPush）
        // 标题左对齐（且没有被"居中"分支接管）时行首就是正文列左边界，行首匹配要靠整行右移让出背景
        val titleStartAligned = isTitle && !isMiddleTitle && !isRightTitle &&
            !emptyContent && !isVolumeTitle &&
            imageStyle?.uppercase() != Book.imgStyleSingle
        // 标题右对齐时行末（最后一行/单行）就是正文列右边界，行末匹配要靠整行左移让出背景
        val titleEndAligned = isTitle && isRightTitle &&
            !emptyContent && !isVolumeTitle &&
            imageStyle?.uppercase() != Book.imgStyleSingle
        // 正文段首无缩进时行首同样是正文列左边界，行首匹配也要整行右移让出背景；
        // 正文段末匹配落在末行，末行是左对齐的、没有可整体左移的余量，改为按需压缩行宽
        val indentLength = paragraphIndentLength(text, isTitle)
        val bodyStartAligned = !isTitle && isFirstLine && indentLength == 0
        val neighborPush = computeNeighborPush(
            text,
            collectForcedBleedSegments(charStyles),
            textPaint,
            indentLength,
            titleStartAligned || bodyStartAligned,
            titleEndAligned || !isTitle,
        ) { index -> widthsArray.getOrElse(index) { 0f } }
        columnTrimEnd = neighborPush?.trimEnd
        columnIndentExtra = neighborPush?.indentAdd ?: 0f
        neighborPush?.let { push ->
            for (i in push.widthAdd.indices) {
                if (push.widthAdd[i] != 0f) widthsArray[i] += push.widthAdd[i]
            }
        }
        val layout = if (useZhLayout) {
            val (words, widths) = measureTextSplit(text, widthsArray)
            val indentSize = if (isFirstLine) paragraphIndent.length else 0
            ZhLayout(text, textPaint, visibleWidth, words, widths, indentSize)
        } else {
            StaticLayout(
                buildFontAwareLayoutText(text, charStyles, neighborPush?.widthAdd),
                textPaint,
                visibleWidth,
                Layout.Alignment.ALIGN_NORMAL,
                0f,
                0f,
                true,
            )
        }
        durY = when {
            // 标题y轴居中
            emptyContent && textPages.isEmpty() -> {
                val textPage = pendingTextPage
                if (textPage.lineSize == 0) {
                    val ty = (visibleHeight - layout.lineCount * textHeight) / 2
                    if (ty > titleTopSpacing) ty else titleTopSpacing.toFloat()
                } else {
                    var textLayoutHeight = layout.lineCount * textHeight
                    val fistLine = textPage.getLine(0)
                    if (fistLine.lineTop < textLayoutHeight + titleTopSpacing) {
                        textLayoutHeight = fistLine.lineTop - titleTopSpacing
                    }
                    textPage.lines.forEach {
                        it.lineTop -= textLayoutHeight
                        it.lineBase -= textLayoutHeight
                        it.lineBottom -= textLayoutHeight
                    }
                    durY - textLayoutHeight
                }
            }

            isTitle && textPages.isEmpty() && pendingTextPage.lines.isEmpty() -> {
                when (imageStyle?.uppercase()) {
                    Book.imgStyleSingle -> {
                        val ty = (visibleHeight - layout.lineCount * textHeight) / 2
                        if (ty > titleTopSpacing) ty else titleTopSpacing.toFloat()
                    }

                    else -> durY + titleTopSpacing
                }
            }

            else -> durY
        }
        for (lineIndex in 0 until layout.lineCount) {
            val textLine = TextLine(isTitle = isTitle)
            prepareNextPageIfNeed(durY + textHeight)
            val lineStart = layout.getLineStart(lineIndex)
            val lineEnd = layout.getLineEnd(lineIndex)
            val lineText = text.substring(lineStart, lineEnd)
            val (words, widths) = measureTextSplit(lineText, widthsArray, lineStart)
            val desiredWidth = widths.fastSum()
            textLine.text = lineText
            // 行首匹配让出的外扩量加在整行起始偏移上（标题首行；正文无缩进的段首同理，
            // 只有首行会被行首匹配影响）
            val lineStartExtra = if (lineIndex == 0) {
                neighborPush?.lineStartAdd ?: 0f
            } else {
                0f
            }
            // 标题右对齐时段末匹配让出的外扩量从整行起始偏移里扣掉（只有最后一行/单行会被段末匹配影响）
            val titleEndExtra = if (isTitle && lineIndex == layout.lineCount - 1) {
                neighborPush?.lineEndSub ?: 0f
            } else {
                0f
            }
            // 正文段末匹配的外扩量：末行右侧空白不够时按两端对齐的方式压缩行宽让出（标题右对齐
            // 用"整行左移"，正文末行左对齐、没有可左移的余量）；空白足够则无需处理
            val endSqueeze = if (!isTitle && lineIndex == layout.lineCount - 1) {
                val endBleed = neighborPush?.lineEndSub ?: 0f
                if (endBleed > 0f) {
                    val trailingBlank =
                        (visibleWidth - lineStartExtra - desiredWidth).coerceAtLeast(0f)
                    (endBleed - trailingBlank).coerceIn(0f, endBleed)
                } else {
                    0f
                }
            } else {
                0f
            }
            when (lineIndex) {
                0 if layout.lineCount > 1 && !isTitle && isFirstLine -> {
                    // 多行的第一行 非标题
                    addCharsToLineFirst(
                        book, absStartX, textLine, words, textPaint,
                        desiredWidth, widths, srcList, clickList, charStyles, lineStart,
                        lineStartExtra,
                    )
                }
                layout.lineCount - 1 -> {
                    // 最后一行、单行
                    // 标题x轴对齐
                    val startX = if (isTitle) {
                        when {
                            isMiddleTitle ||
                                emptyContent ||
                                isVolumeTitle ||
                                imageStyle?.uppercase() == Book.imgStyleSingle -> {
                                (visibleWidth - desiredWidth) / 2
                            }
                            // 右对齐：扣掉段末匹配让出的外扩量，背景右侧边缘正好落在正文列右边界；
                            // 行满时扣成负数会把左侧文字挤进页边距，改为保住文字、外扩量部分溢出
                            isRightTitle -> (visibleWidth - desiredWidth - titleEndExtra).coerceAtLeast(0f)
                            else -> lineStartExtra
                        }
                    } else {
                        // 单行正文段落同样可能吃到行首匹配的整行右移
                        if (lineIndex == 0) lineStartExtra else 0f
                    }
                    if (!isTitle && textFullJustify && endSqueeze > 0f) {
                        // 末行右端放不下段末外扩：走两端对齐的压缩分布，desiredWidth 补上压缩量后
                        // residual 相应变小，行尾正好落在让出外扩之后的位置
                        addCharsToLineMiddle(
                            book, absStartX, textLine, words, textPaint,
                            desiredWidth + startX + endSqueeze, startX,
                            widths, srcList, clickList, charStyles, lineStart,
                        )
                    } else {
                        addCharsToLineNatural(
                            book, absStartX, textLine, words,
                            startX, !isTitle && lineIndex == 0, widths, srcList, clickList, charStyles, lineStart,
                        )
                    }
                }
                else -> {
                    if (isTitle) {
                        // 标题对齐
                        val startX = when {
                            isMiddleTitle ||
                                emptyContent ||
                                isVolumeTitle ||
                                imageStyle?.uppercase() == Book.imgStyleSingle -> {
                                (visibleWidth - desiredWidth) / 2
                            }
                            isRightTitle -> visibleWidth - desiredWidth
                            else -> lineStartExtra
                        }
                        addCharsToLineNatural(
                            book, absStartX, textLine, words,
                            startX, false, widths, srcList, clickList, charStyles, lineStart,
                        )
                    } else {
                        // 中间行
                        addCharsToLineMiddle(
                            book, absStartX, textLine, words, textPaint,
                            desiredWidth, 0f, widths, srcList, clickList, charStyles, lineStart,
                        )
                    }
                }
            }
            if (doublePage) {
                textLine.isLeftLine = absStartX < viewWidth / 2
            }
            calcTextLinePosition(textPages, textLine, stringBuilder.length)
            stringBuilder.append(lineText)
            textLine.upTopBottom(durY, textHeight, fontMetrics)
            val textPage = pendingTextPage
            textPage.addLine(textLine)
            durY += textHeight * lineSpacingExtra
            if (textPage.height < durY) {
                textPage.height = durY
            }
        }
        durY += textHeight * paragraphSpacing / 10f
    }

    private fun calcTextLinePosition(
        textPages: ArrayList<TextPage>,
        textLine: TextLine,
        sbLength: Int,
    ) {
        val lastLine = pendingTextPage.lines.lastOrNull { it.paragraphNum > 0 }
            ?: textPages.lastOrNull()?.lines?.lastOrNull { it.paragraphNum > 0 }
        val paragraphNum = when {
            lastLine == null -> 1
            lastLine.isParagraphEnd -> lastLine.paragraphNum + 1
            else -> lastLine.paragraphNum
        }
        textLine.paragraphNum = paragraphNum
        textLine.chapterPosition =
            (
                textPages.lastOrNull()?.lines?.lastOrNull()?.run {
                    chapterPosition + charSize + if (isParagraphEnd) 1 else 0
                } ?: 0
                ) + sbLength
        textLine.pagePosition = sbLength
    }

    /**
     * 有缩进,两端对齐
     */
    private suspend fun addCharsToLineFirst(
        book: Book,
        absStartX: Int,
        textLine: TextLine,
        words: List<String>,
        textPaint: TextPaint,
        /**自然排版长度**/
        desiredWidth: Float,
        textWidths: List<Float>,
        srcList: LinkedList<String>?,
        clickList: LinkedList<String?>?,
        charStyles: Array<CharStyle?>?,
        lineStart: Int,
        /** 行首匹配让出的外扩量：整行右移让出背景（与标题左对齐同理，正文仅无缩进的段首会出现） **/
        startExtra: Float = 0f,
    ) {
        var x = startExtra
        if (!textFullJustify) {
            addCharsToLineNatural(
                book, absStartX, textLine, words,
                x, true, textWidths, srcList, clickList, charStyles, lineStart,
            )
            return
        }
        val bodyIndent = paragraphIndent
        val indentExtra = columnIndentExtra
        repeat(bodyIndent.length) { index ->
            // 段首有"强制"外扩时，最后一个缩进列要多让出外扩量，缩进后的文字随之整体右移
            val x1 = x + indentCharWidth +
                if (index == bodyIndent.lastIndex) indentExtra else 0f
            textLine.addColumn(
                TextColumn(
                    charData = ChapterProvider.indentChar,
                    start = absStartX + x,
                    end = absStartX + x1,
                ),
            )
            x = x1
            textLine.indentWidth = x
        }
        textLine.indentSize = bodyIndent.length
        if (words.size > bodyIndent.length) {
            val text1 = words.subList(bodyIndent.length, words.size)
            val textWidths1 = textWidths.subList(bodyIndent.length, textWidths.size)
            addCharsToLineMiddle(
                book, absStartX, textLine, text1, textPaint,
                // 整行右移让出背景后，两端对齐的终点要扣回同样的量，行尾仍落在正文列右边界
                //（有缩进时 startExtra 恒为 0，缩进路径不受影响）
                desiredWidth + startExtra, x, textWidths1, srcList, clickList, charStyles, lineStart + bodyIndent.length,
            )
        }
    }

    /**
     * 无缩进,两端对齐
     */
    private suspend fun addCharsToLineMiddle(
        book: Book,
        absStartX: Int,
        textLine: TextLine,
        words: List<String>,
        textPaint: TextPaint,
        /**自然排版长度**/
        desiredWidth: Float,
        /**起始x坐标**/
        startX: Float,
        textWidths: List<Float>,
        srcList: LinkedList<String>?,
        clickList: LinkedList<String?>?,
        charStyles: Array<CharStyle?>?,
        lineStart: Int,
    ) {
        if (!textFullJustify) {
            addCharsToLineNatural(
                book, absStartX, textLine, words,
                startX, false, textWidths, srcList,
                clickList, charStyles, lineStart,
            )
            return
        }
        val residualWidth = visibleWidth - desiredWidth
        val spaceSize = words.count { it == " " }
        textLine.startX = absStartX + startX
        if (spaceSize > 1) {
            val d = residualWidth / spaceSize
            textLine.wordSpacing = d
            var x = startX
            for (index in words.indices) {
                val char = words[index]
                val cw = textWidths[index]
                val x1 = if (char == " ") {
                    if (index != words.lastIndex) (x + cw + d) else (x + cw)
                } else {
                    (x + cw)
                }
                addCharToLine(
                    book, absStartX, textLine, char,
                    x, x1, index + 1 == words.size, srcList,
                    clickList, charStyles, lineStart + index,
                )
                x = x1
            }
        } else {
            val gapCount: Int = words.lastIndex
            val d = if (gapCount > 0) residualWidth / gapCount else 0f
            textLine.extraLetterSpacingOffsetX = -d / 2
            textLine.extraLetterSpacing = d / textPaint.textSize
            var x = startX
            for (index in words.indices) {
                val char = words[index]
                val cw = textWidths[index]
                val x1 = if (index != words.lastIndex) (x + cw + d) else (x + cw)
                addCharToLine(
                    book, absStartX, textLine, char,
                    x, x1, index + 1 == words.size, srcList,
                    clickList, charStyles, lineStart + index,
                )
                x = x1
            }
        }
        exceed(absStartX, textLine, words)
    }

    /**
     * 自然排列
     */
    private suspend fun addCharsToLineNatural(
        book: Book,
        absStartX: Int,
        textLine: TextLine,
        words: List<String>,
        startX: Float,
        hasIndent: Boolean,
        textWidths: List<Float>,
        srcList: LinkedList<String>?,
        clickList: LinkedList<String?>?,
        charStyles: Array<CharStyle?>?,
        lineStart: Int,
    ) {
        val indentLength = paragraphIndent.length
        var x = startX
        textLine.startX = absStartX + startX
        for (index in words.indices) {
            val char = words[index]
            val cw = textWidths[index]
            val x1 = x + cw
            addCharToLine(
                book,
                absStartX,
                textLine,
                char,
                x,
                x1,
                index + 1 == words.size,
                srcList,
                clickList,
                charStyles,
                lineStart + index,
            )
            x = x1
            if (hasIndent && index == indentLength - 1) {
                textLine.indentWidth = x
            }
        }
        exceed(absStartX, textLine, words)
    }

    /**
     * 添加字符
     */
    private suspend fun addCharToLine(
        book: Book,
        absStartX: Int,
        textLine: TextLine,
        char: String,
        xStart: Float,
        xEnd: Float,
        isLineEnd: Boolean,
        srcList: LinkedList<String>?,
        clickList: LinkedList<String?>?,
        charStyles: Array<CharStyle?>?,
        textIndex: Int,
    ) {
        val style = charStyles?.getOrNull(textIndex)
        val textColor = style?.textColor
        val underlineMode = style?.underlineMode ?: 0
        val underlineColor = style?.underlineColor
        val underlineWidth = style?.underlineWidth ?: 1f
        val underlineOffset = style?.underlineOffset ?: 2f
        val underlineSvgPath = style?.underlineSvgPath ?: ""
        val bgColor = style?.bgColor
        val bgImage = style?.bgImage ?: ""
        val bgImageFit = style?.bgImageFit ?: 0
        val bgImageScale = style?.bgImageScale ?: 1f
        val npLeft = style?.npLeft ?: 0.1f
        val npTop = style?.npTop ?: 0.1f
        val npRight = style?.npRight ?: 0.1f
        val npBottom = style?.npBottom ?: 0.1f
        val bgBleedMode = style?.bgBleedMode ?: HighlightRule.BLEED_SMART
        val bgSpacingLeft = style?.bgSpacingLeft ?: 0f
        val bgSpacingRight = style?.bgSpacingRight ?: 0f
        val bgSpacingTop = style?.bgSpacingTop ?: 0f
        val bgSpacingBottom = style?.bgSpacingBottom ?: 0f
        val fontPath = style?.font.orEmpty()
        val column = when {
            !srcList.isNullOrEmpty() && (char == srcReplaceStr || char == reviewStr) -> {
                val src = srcList.removeFirst()
                val click = clickList?.removeFirst()
                val isBubble = ParagraphBubbleRenderer.isBubbleSrc(src)
                if (!isBubble) {
                    ImageProvider.cacheImage(book, src, ReadBook.bookSource)
                }
                val isAnimated = if (isBubble) false else ImageProvider.isGif(book, src, ReadBook.bookSource)
                ImageColumn(
                    start = absStartX + xStart,
                    end = absStartX + xEnd,
                    src = src,
                    click = click,
                    isAnimated = isAnimated,
                )
            }
//            isLineEnd && char == ChapterProvider.reviewChar -> {
//                ReviewColumn(
//                    start = absStartX + xStart,
//                    end = absStartX + xEnd,
//                    count = 10
//                )
//            }

            else -> {
                TextColumn(
                    start = absStartX + xStart,
                    // 九宫格"强制"策略给最后一个字加的宽度要扣掉，否则背景会跟着一起变宽
                    end = absStartX + xEnd - (columnTrimEnd?.getOrNull(textIndex) ?: 0f),
                    charData = char,
                    textColor = textColor,
                    underlineMode = underlineMode,
                    underlineColor = underlineColor,
                    underlineWidth = underlineWidth,
                    underlineOffset = underlineOffset,
                    underlineSvgPath = underlineSvgPath,
                    bgColor = bgColor,
                    bgImage = bgImage,
                    bgImageFit = bgImageFit,
                    bgImageScale = bgImageScale,
                    npLeft = npLeft,
                    npTop = npTop,
                    npRight = npRight,
                    npBottom = npBottom,
                    bgBleedMode = bgBleedMode,
                    bgSpacingLeft = bgSpacingLeft,
                    bgSpacingRight = bgSpacingRight,
                    bgSpacingTop = bgSpacingTop,
                    bgSpacingBottom = bgSpacingBottom,
                    fontPath = fontPath,
                )
            }
        }
        textLine.addColumn(column)
    }

    /**
     * 超出边界处理
     */
    private fun exceed(absStartX: Int, textLine: TextLine, words: List<String>) {
        var size = words.size
        if (size < 2) return
        val visibleEnd = absStartX + visibleWidth
        val columns = textLine.columns
        var offset = 0
        val endColumn = if (words.last() == " ") {
            size--
            offset++
            columns[columns.lastIndex - 1]
        } else {
            columns.last()
        }
        val endX = endColumn.end.roundToInt()
        if (endX > visibleEnd) {
            textLine.exceed = true
            val cc = (endX - visibleEnd) / size
            for (i in 0..<size) {
                textLine.getColumnReverseAt(i, offset).let {
                    val py = cc * (size - i)
                    it.start -= py
                    it.end -= py
                }
            }
        }
    }

    private suspend fun prepareNextPageIfNeed(requestHeight: Float = -1f) {
        if (requestHeight > visibleHeight || requestHeight == -1f) {
            val textPage = pendingTextPage
            // 双页的 durY 不正确，可能会小于实际高度
            if (textPage.height < durY) {
                textPage.height = durY
            }
            if (doublePage && absStartX < viewWidth / 2) {
                // 当前页面左列结束
                textPage.leftLineSize = textPage.lineSize
                absStartX = viewWidth / 2 + paddingLeft
            } else {
                // 当前页面结束,设置各种值
                if (textPage.leftLineSize == 0) {
                    textPage.leftLineSize = textPage.lineSize
                }
                textPage.text = stringBuilder.toString()
                currentCoroutineContext().ensureActive()
                onPageCompleted()
                // 新建页面
                pendingTextPage = TextPage()
                stringBuilder.clear()
                absStartX = paddingLeft
            }
            durY = 0f
        }
    }

    private fun allocateFloatArray(size: Int): FloatArray {
        if (size > floatArray.size) {
            floatArray = FloatArray(size)
        }
        return floatArray
    }

    /**
     * "邻字外推"的结果。
     *
     * [widthAdd] 参与断行与两端对齐：加在**邻字自己**的推进量上，所以剩下的文字仍然整齐；
     * [trimEnd] 记录加在"匹配区最后一个字"上的那份，建列时要从列末尾扣掉——否则背景会跟着
     * 一起变宽，等于没把邻字推开。
     */
    private class NeighborPush(val widthAdd: FloatArray, val trimEnd: FloatArray) {

        /**
         * 段首缩进额外让出的宽度：加在**最后一个缩进字**上，缩进后的文字整体右移。
         * 两端对齐的缩进列是按固定宽度重建的，读不到 [widthAdd]，所以额外带一份。
         */
        var indentAdd: Float = 0f

        /**
         * 行首额外右移量：匹配从**本行第一列**开始（行首没有邻字可推）时，把外扩量加到
         * 整行的起始偏移上，背景的左侧边缘就落在文字起始位置（见 [computeNeighborPush]）。
         */
        var lineStartAdd: Float = 0f

        /**
         * 行末额外左移量：匹配到**本行最后一列**（行末没有邻字可推）时，把外扩量从整行的
         * 起始偏移里扣掉，背景的右侧边缘就落在文字结束位置（见 [computeNeighborPush]）。
         */
        var lineEndSub: Float = 0f
    }

    /** 参与"邻字外推"计算的一段九宫格强制高亮（只保留与背景图外扩相关的字段） */
    private class BleedSegment(
        val start: Int,
        val end: Int,
        val bgImage: String,
        val spacingLeft: Float,
        val spacingRight: Float,
        val npLeft: Float,
        val npRight: Float,
    )

    private fun CharStyle.isForcedBleed(): Boolean =
        bgImage.isNotEmpty() && bgImageFit == bgImageFitNine &&
            bgBleedMode == HighlightRule.BLEED_FORCE

    private fun CharStyle?.sameBleedAs(other: CharStyle): Boolean =
        this != null && bgImage == other.bgImage && bgImageFit == other.bgImageFit &&
            bgBleedMode == other.bgBleedMode && npLeft == other.npLeft && npRight == other.npRight &&
            bgSpacingLeft == other.bgSpacingLeft && bgSpacingRight == other.bgSpacingRight

    private fun HighlightStyleSpan.isForcedBleed(): Boolean =
        bgImage.isNotEmpty() && bgImageFit == bgImageFitNine &&
            bgBleedMode == HighlightRule.BLEED_FORCE

    /** 从整章字符样式数组里收集需要"推开邻字"的九宫格强制段 */
    private fun collectForcedBleedSegments(charStyles: Array<CharStyle?>?): List<BleedSegment> {
        if (charStyles == null) return emptyList()
        val segments = ArrayList<BleedSegment>()
        var index = 0
        while (index < charStyles.size) {
            val style = charStyles[index]
            if (style == null || !style.isForcedBleed()) {
                index++
                continue
            }
            var end = index + 1
            while (end < charStyles.size && charStyles[end].sameBleedAs(style)) end++
            segments.add(
                BleedSegment(
                    index, end, style.bgImage,
                    style.bgSpacingLeft, style.bgSpacingRight, style.npLeft, style.npRight,
                ),
            )
            index = end
        }
        return segments
    }

    /** 从 HTML 的高亮 Span 里收集需要"推开邻字"的九宫格强制段 */
    private fun collectForcedBleedSegments(spanned: Spanned): List<BleedSegment> {
        val segments = ArrayList<BleedSegment>()
        spanned.getSpans(0, spanned.length, HighlightStyleSpan::class.java).forEach { span ->
            if (!span.isForcedBleed()) return@forEach
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            if (start < end) {
                segments.add(
                    BleedSegment(
                        start, end, span.bgImage,
                        span.bgSpacingLeft, span.bgSpacingRight, span.npLeft, span.npRight,
                    ),
                )
            }
        }
        return segments
    }

    /**
     * 段落首行的缩进长度；没有缩进（标题、用户把缩进设为 0、非段落开头）时返回 0。
     */
    private fun paragraphIndentLength(text: CharSequence, isTitle: Boolean): Int {
        if (isTitle) return 0
        val indent = paragraphIndent
        if (indent.isEmpty() || text.length <= indent.length) return 0
        return if (text.startsWith(indent)) indent.length else 0
    }

    /**
     * 九宫格"强制"策略：把左右邻字向外推开的宽度。
     *
     * 目标：背景照旧向外包裹（外扩量 = 四角厚度 + 间距），但邻字与背景边缘之间保留一个**正文字距**，
     * 于是邻字要向外让出的量 = 外扩量 + 正文字距 − 邻字与匹配区之间本来已有的空隙。
     * 加宽加在邻字自己的推进量上，因此断行与两端对齐都会按真实宽度处理，剩下的文字仍然整齐。
     *
     * 让出的距离由**背景元素自身**决定（四角厚度 + 间距），不再固定为一个字宽：间距调大时邻字会被
     * 推得更远，背景始终完整地包住匹配文字。
     *
     * 段首缩进是例外：此时左邻字就是段落自己的缩进。缩进是段落必需的排版空间，不能按"邻字已有空隙"
     * 抵扣（抵扣后背景的左侧边缘会压进缩进里，这一段看上去缩进比别的段落小）。改为**按外扩量把缩进
     * 后的文字整体右移**：缩进的让出量写进 [NeighborPush.indentAdd]，背景边缘正好落在缩进后的文字
     * 起始位置，且与匹配文字的距离保持不变。
     *
     * 另一种"行首没有邻字"的情况同理：匹配从本行第一列开始（标题左对齐时最常见，行首右边就是正文列
     * 左边界），左侧没有列可以加宽。此时若该行确实顶着正文列左边界（[lineStartAligned]），把外扩量
     * 记进 [NeighborPush.lineStartAdd]，由调用方加到整行的起始偏移上——背景左侧边缘落在文字起始位置，
     * 不会溢出到页边距里被裁掉。居中的行不需要（外扩量落在行首外的空白里，视觉上本来就是完整的）。
     *
     * 行末是镜像情况：匹配到本行最后一列（标题右对齐时最常见，行末左边就是正文列右边界），右侧没有
     * 列可以加宽。此时若该行确实顶着正文列右边界（[lineEndAligned]），把外扩量记进
     * [NeighborPush.lineEndSub]，由调用方从整行的起始偏移里扣掉——背景右侧边缘落在文字结束位置。
     */
    private fun computeNeighborPush(
        text: CharSequence,
        segments: List<BleedSegment>,
        textPaint: TextPaint,
        /** 段落首行的缩进长度（0 = 无缩进），左邻字落在缩进里时改用"整段右移" */
        indentLength: Int = 0,
        /** 该行文字是否紧贴正文列左边界（行首匹配时要把整行右移，见 [NeighborPush.lineStartAdd]） */
        lineStartAligned: Boolean = false,
        /** 该行文字是否紧贴正文列右边界（行末匹配时要把整行左移，见 [NeighborPush.lineEndSub]） */
        lineEndAligned: Boolean = false,
        advance: (Int) -> Float,
    ): NeighborPush? {
        if (segments.isEmpty()) return null
        val textSize = textPaint.textSize
        val bodySpacing = textPaint.letterSpacing * textSize
        val push = NeighborPush(FloatArray(text.length), FloatArray(text.length))
        val inkBounds = android.graphics.Rect()

        /** 邻字与匹配区之间已有的空隙：左侧看邻字的右侧空，右侧看邻字的左侧空 */
        fun bearing(index: Int, isLeftNeighbor: Boolean): Float {
            val char = text[index].toString()
            if (char.isBlank()) return advance(index)
            textPaint.getTextBounds(char, 0, char.length, inkBounds)
            return if (isLeftNeighbor) {
                (advance(index) - inkBounds.right).coerceAtLeast(0f)
            } else {
                (-inkBounds.left).toFloat().coerceAtLeast(0f)
            }
        }

        var applied = false
        segments.forEach { segment ->
            val bitmap = TextLine.getBgBitmap(segment.bgImage) ?: return@forEach
            val sides = TextLine.nineSliceSideWidth(
                bitmap, segment.npLeft, segment.npRight, textSize,
            )
            val spacingLeft = segment.spacingLeft * textSize
            val spacingRight = segment.spacingRight * textSize
            // 左侧：把匹配区连同背景一起往右挪，邻字不动
            if (segment.start > 0) {
                val index = segment.start - 1
                if (indentLength > 0 && index < indentLength) {
                    // 段首缩进：外扩量加在最后一个缩进字上，缩进后的文字整体右移，背景的左侧边缘
                    // 就落在缩进后的文字起始位置（与匹配文字的距离 = 外扩量，保持不变）
                    val extra = (sides[0] + spacingLeft).coerceAtLeast(0f)
                    if (extra > 0f) {
                        val target = indentLength - 1
                        push.widthAdd[target] = maxOf(push.widthAdd[target], extra)
                        push.indentAdd = maxOf(push.indentAdd, extra)
                        applied = true
                    }
                } else {
                    val size = (sides[0] + spacingLeft + bodySpacing - bearing(index, true))
                        .coerceAtLeast(0f)
                    if (size > 0f) {
                        push.widthAdd[index] = maxOf(push.widthAdd[index], size)
                        applied = true
                    }
                }
            } else if (lineStartAligned) {
                // 匹配从本行第一列开始（标题左对齐）：左侧没有列可以加宽，外扩量交给整行起始偏移，
                // 背景的左侧边缘就落在文字起始位置（与匹配文字的距离 = 外扩量，保持不变）
                val extra = (sides[0] + spacingLeft).coerceAtLeast(0f)
                if (extra > 0f) {
                    push.lineStartAdd = maxOf(push.lineStartAdd, extra)
                    applied = true
                }
            }
            // 右侧：加在匹配区最后一个字上（它后面的字才会被推开），因此记下要扣回背景的量
            if (segment.end < text.length) {
                val index = segment.end - 1
                val size = (sides[1] + spacingRight + bodySpacing - bearing(segment.end, false))
                    .coerceAtLeast(0f)
                if (size > 0f) {
                    push.widthAdd[index] = maxOf(push.widthAdd[index], size)
                    push.trimEnd[index] = maxOf(push.trimEnd[index], size)
                    applied = true
                }
            } else if (lineEndAligned) {
                // 匹配到本行最后一列（标题右对齐最常见）：右侧没有列可以加宽，外扩量从整行起始
                // 偏移里扣掉，背景的右侧边缘就落在文字结束位置（与匹配文字的距离 = 外扩量，保持不变）
                val extra = (sides[1] + spacingRight).coerceAtLeast(0f)
                if (extra > 0f) {
                    push.lineEndSub = maxOf(push.lineEndSub, extra)
                    applied = true
                }
            }
        }
        return push.takeIf { applied }
    }

    private fun measureTextSplit(
        text: String,
        widthsArray: FloatArray,
        start: Int = 0,
    ): Pair<ArrayList<String>, ArrayList<Float>> {
        val length = text.length
        var clusterCount = 0
        for (i in start..<start + length) {
            if (widthsArray[i] > 0) clusterCount++
        }
        val widths = ArrayList<Float>(clusterCount)
        val stringList = ArrayList<String>(clusterCount)
        var i = 0
        while (i < length) {
            val clusterBaseIndex = i++
            widths.add(widthsArray[start + clusterBaseIndex])
            while (i < length && widthsArray[start + i] == 0f && !isZeroWidthChar(text[i])) {
                i++
            }
            stringList.add(text.substring(clusterBaseIndex, i))
        }
        return stringList to widths
    }

    private fun isZeroWidthChar(char: Char): Boolean {
        val code = char.code
        return code == 8203 || code == 8204 || code == 8205 || code == 8288
    }

    /**
     * 已预编译正则的高亮规则，避免排版时重复编译 pattern。
     */
    private data class CompiledHighlightRule(
        val rule: HighlightRule,
        val regex: Regex,
    ) {
        /** 该规则的渲染样式快照，整章共享一个对象，命中只写数组引用 */
        val charStyle: CharStyle by lazy {
            val style = HighlightRuleStyle.from(rule)
            CharStyle(
                textColor = style.textColor,
                underlineMode = style.underlineMode,
                underlineColor = style.resolvedAccentColor,
                underlineWidth = style.underlineWidth,
                underlineOffset = style.underlineOffset,
                underlineSvgPath = style.underlineSvgPath,
                bgColor = style.bgColor,
                bgImage = style.bgImage,
                bgImageFit = style.bgImageFit,
                bgImageScale = style.bgImageScale,
                npLeft = style.npLeft,
                npTop = style.npTop,
                npRight = style.npRight,
                npBottom = style.npBottom,
                bgBleedMode = style.bgBleedMode,
                bgSpacingLeft = style.bgSpacingLeft,
                bgSpacingRight = style.bgSpacingRight,
                bgSpacingTop = style.bgSpacingTop,
                bgSpacingBottom = style.bgSpacingBottom,
                font = style.font,
            )
        }
    }

    /**
     * 正文高亮匹配结果。
     *
     * [starts] 保存每段 content 拼接后在全文中的起始偏移；
     * [styles] 为整章每字符样式数组，无任何命中时为 null；
     * [startAt] 返回指定 content 的全局起始偏移，供 setTypeText 定位切片。
     */
    private class BodyHighlightStyles(
        val styles: Array<CharStyle?>?,
        val starts: List<Int>,
    ) {
        fun startAt(contentIndex: Int): Int = starts.getOrElse(contentIndex) { 0 }

        /**
         * 取指定偏移起、指定长度的样式切片，该范围内无任何样式时返回 null。
         */
        fun stylesAt(offset: Int, length: Int): Array<CharStyle?>? {
            val source = styles ?: return null
            if (length <= 0 || offset >= source.size) return null
            val copyLength = minOf(length, source.size - offset)
            var hasStyle = false
            for (i in 0 until copyLength) {
                if (source[offset + i] != null) {
                    hasStyle = true
                    break
                }
            }
            if (!hasStyle) return null
            val result = arrayOfNulls<CharStyle>(length)
            for (i in 0 until copyLength) {
                source[offset + i]?.let { result[i] = it }
            }
            return result
        }
    }

    /** 判断规则是否对当前文本生效，同时检查书籍作用域、排版作用域、主题作用域和标题/正文作用域 */
    private fun HighlightRule.appliesTo(isTitle: Boolean, bookName: String, bookOrigin: String): Boolean {
        if (!matchesScope(bookName, bookOrigin)) return false
        if (!matchesLayout(ReadBookConfig.durConfig.name)) return false
        if (!matchesTheme(AppConfig.isNightTheme)) return false
        return when (targetScope) {
            HighlightRule.TARGET_TITLE -> isTitle
            HighlightRule.TARGET_BODY -> !isTitle
            else -> true
        }
    }

    //region 强制软件气泡

    /** 强制气泡解析结果：renderSrc 为气泡 URL 或原始 src，click 为从原始 option 中提取的点击脚本 */
    private data class ForcedBubbleResult(val renderSrc: String, val click: String?)

    /**
     * 尝试将非气泡图片源转换为 bubble://paragraph URL（仅返回 renderSrc）。
     * 当需要同时保留 click 时请使用 [tryParseForcedBubbleSrcWithClick]。
     */
    private fun tryParseForcedBubbleSrc(src: String): String = tryParseForcedBubbleSrcWithClick(src).renderSrc

    /**
     * 尝试将非气泡图片源转换为 bubble://paragraph URL，同时保留原始 click 脚本。
     *
     * 当 [AppConfig.forceSoftwareParagraphBubble] 开启时，检测图片源是否为段评入口
     * （如内联 SVG、特定 type/click 等），如果是则转换为软件气泡 URL，
     * 并将原始 option 中的 click/pclick 脚本保留到结果中。
     *
     * 已是气泡 URL 或开关关闭时返回原始 src 和 null click。
     */
    private fun tryParseForcedBubbleSrcWithClick(src: String): ForcedBubbleResult {
        if (!AppConfig.forceSoftwareParagraphBubble) return ForcedBubbleResult(src, null)
        if (ParagraphBubbleRenderer.isBubbleSrc(src)) return ForcedBubbleResult(src, null)
        // dp: 协议快速解析（如 dp:12,{"pclick":"...","status":"normal"}）
        if (src.startsWith(PARAGRAPH_BUBBLE_PREFIX, ignoreCase = true)) {
            return parseParagraphBubble(src)
        }
        val urlMatcher = paramPattern.matcher(src)
        if (!urlMatcher.find()) return ForcedBubbleResult(src, null)
        val renderSrc = src.substring(0, urlMatcher.start())
        val optionStr = src.substring(urlMatcher.end())
        val option = GSON.fromJsonObject<Map<String, String>>(optionStr).getOrNull()
            ?: return ForcedBubbleResult(src, null)
        if (!isForcedBubbleCandidate(src, option)) return ForcedBubbleResult(src, null)
        val displayText = extractForcedBubbleDisplayText(src, renderSrc, option)
            ?: return ForcedBubbleResult(src, null)
        val status = option.valueIgnoreCase("status")?.takeIf { it.isNotBlank() } ?: "normal"
        val displayColor = extractForcedBubbleColor(src, renderSrc, option)
        val colorQuery = displayColor?.let { "&displayColor=${Uri.encode(it)}" }.orEmpty()
        val encodedText = Uri.encode(displayText)
        val encodedStatus = Uri.encode(status)
        val pclick = option.valueIgnoreCase("pclick")?.takeIf { it.isNotBlank() }
        val click = option.valueIgnoreCase("click")?.takeIf { it.isNotBlank() }
        val bubbleUrl = "bubble://paragraph?displayText=$encodedText&num=$encodedText&status=$encodedStatus$colorQuery"
        return ForcedBubbleResult(bubbleUrl, pclick ?: click)
    }

    /**
     * 解析 dp: 协议的段评气泡 src。
     *
     * 格式：dp:<count>,{"pclick":"...","status":"normal","displayColor":"..."}
     * - count 部分作为气泡显示文本的 fallback
     * - option JSON 中的 displayText/num/count 等优先作为显示文本
     * - pclick/click 保留为点击脚本
     */
    private fun parseParagraphBubble(src: String): ForcedBubbleResult {
        val payload = src.substring(PARAGRAPH_BUBBLE_PREFIX.length).trim()
        val optionIndex = payload.indexOf(",{")
        val count = if (optionIndex >= 0) {
            payload.substring(0, optionIndex)
        } else {
            payload
        }.trim()
        val option = if (optionIndex >= 0) {
            GSON.fromJsonObject<Map<String, String>>(payload.substring(optionIndex + 1))
                .getOrNull()
                .orEmpty()
        } else {
            emptyMap()
        }
        val displayText = extractForcedBubbleDisplayText(src, src, option) ?: count
        val status = option.valueIgnoreCase("status")?.takeIf { it.isNotBlank() } ?: "normal"
        val displayColor = extractForcedBubbleColor(src, src, option)
        val colorQuery = displayColor?.let { "&displayColor=${Uri.encode(it)}" }.orEmpty()
        val encodedText = Uri.encode(displayText)
        val encodedStatus = Uri.encode(status)
        val pclick = option.valueIgnoreCase("pclick")?.takeIf { it.isNotBlank() }
        val click = option.valueIgnoreCase("click")?.takeIf { it.isNotBlank() }
        val bubbleUrl = "bubble://paragraph?displayText=$encodedText&num=$encodedText&status=$encodedStatus$colorQuery"
        return ForcedBubbleResult(bubbleUrl, pclick ?: click)
    }

    /** 判断图片源是否像段评气泡入口 */
    private fun isForcedBubbleCandidate(src: String, option: Map<String, String>): Boolean {
        val style = option.valueIgnoreCase("style")
        val styleText = style.equals("TEXT", ignoreCase = true)
        if (!style.isNullOrBlank() && !styleText) return false
        val type = option.valueIgnoreCase("type").orEmpty().lowercase()
        val knownType = type in FORCED_BUBBLE_TYPES
        val click = listOfNotNull(option.valueIgnoreCase("click"), option.valueIgnoreCase("pclick"))
            .joinToString(separator = "\n")
            .lowercase()
        val clickLike = click.contains("showcmt(") ||
            click.contains("showcomment(") ||
            click.contains("showreview(") ||
            click.contains("paragraph")
        val dataSvg = src.trimStart().startsWith("data:image/svg+xml", ignoreCase = true)
        return knownType || clickLike || (styleText && dataSvg)
    }

    /** 从图片源中提取气泡显示文本 */
    private fun extractForcedBubbleDisplayText(
        src: String,
        renderSrc: String,
        option: Map<String, String>,
    ): String? {
        listOf("displayText", "num", "\$num", "\${num}", "{{num}}", "count", "text", "label").forEach { key ->
            normalizeForcedBubbleText(option.valueIgnoreCase(key).orEmpty())?.let { return it }
        }
        listOf("click", "pclick", "js").forEach { key ->
            extractForcedBubbleTextFromScript(option.valueIgnoreCase(key).orEmpty())?.let { return it }
        }
        listOf(src, renderSrc).distinct().forEach { source ->
            FORCED_BUBBLE_DISPLAY_PARAM_REGEX.find(source)?.groupValues?.getOrNull(1)?.let {
                normalizeForcedBubbleText(Uri.decode(it))?.let { value -> return value }
            }
            val optionMatcher = paramPattern.matcher(source)
            val sourcePart = if (optionMatcher.find()) {
                source.substring(0, optionMatcher.start())
            } else {
                source
            }
            decodeDataSvg(sourcePart)
                ?.let { extractForcedBubbleTextFromSvg(it) }
                ?.let { return it }
        }
        return null
    }

    /** 从脚本中提取 createSvg 调用的 count 参数 */
    private fun extractForcedBubbleTextFromScript(script: String): String? {
        if (script.isBlank()) return null
        return FORCED_BUBBLE_CREATE_SVG_COUNT_REGEX.find(script)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { normalizeForcedBubbleText(it) }
    }

    /** 从 SVG 中提取 <text> 标签内容 */
    private fun extractForcedBubbleTextFromSvg(svg: String): String? {
        val texts = FORCED_BUBBLE_TEXT_REGEX.findAll(svg)
            .mapNotNull { normalizeForcedBubbleText(it.groupValues[1]) }
            .toList()
        return texts.firstOrNull { text -> text.any { it.isDigit() } }
            ?: texts.singleOrNull()
            ?: texts.firstOrNull()
    }

    /** 从图片源中提取气泡颜色 */
    private fun extractForcedBubbleColor(
        src: String,
        renderSrc: String,
        option: Map<String, String>,
    ): String? {
        listOf("displayColor", "color", "\$color", "\${color}", "{{color}}").forEach { key ->
            normalizeForcedBubbleText(option.valueIgnoreCase(key).orEmpty())?.let { return it }
        }
        listOf(src, renderSrc).distinct().forEach { source ->
            FORCED_BUBBLE_COLOR_PARAM_REGEX.find(source)?.groupValues?.getOrNull(1)?.let {
                normalizeForcedBubbleText(Uri.decode(it))?.let { value -> return value }
            }
        }
        return null
    }

    /** 规范化气泡文本：去除 HTML 标签、trim、限制长度 */
    private fun normalizeForcedBubbleText(raw: String): String? = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .trim()
        .takeIf { it.isNotBlank() }
        ?.take(24)

    /** 解码 data:image/svg+xml URL */
    private fun decodeDataSvg(sourcePart: String): String? {
        if (!sourcePart.startsWith("data:image/svg+xml", ignoreCase = true)) return null
        return runCatching {
            if (sourcePart.contains(";base64,", ignoreCase = true)) {
                val base64 = sourcePart.substringAfter(";base64,")
                String(Base64.decode(base64, Base64.DEFAULT), Charsets.UTF_8)
            } else {
                Uri.decode(sourcePart.substringAfter(",", ""))
            }
        }.getOrNull()
    }

    private fun Map<String, String>.valueIgnoreCase(key: String): String? = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    //endregion

    /**
     * 预处理段评气泡的 js 字段：扫描所有段落的 <img> 标签，
     * 对含 js 字段且可能是段评入口的 src，异步执行 JS 并用返回值替换原始 src。
     *
     * 执行失败时保持原 src 不变（走原有逻辑），不重试不缓存。
     */
    private suspend fun preprocessBubbleJs(contents: List<String>): List<String> {
        if (!AppConfig.forceSoftwareParagraphBubble) return contents
        val source = ReadBook.bookSource ?: return contents

        // 限制气泡 js 执行并发：对话生成气泡场景下几乎每段正文都是含 js 字段的气泡，
        // 全量并行执行会在瞬间向书源服务器发起大量请求（js 内含 java.ajax 等网络调用），
        // 触发服务器限流/过载返回 HTML 错误页，连带正文的 /content 请求也拿到 HTML，
        // 导致书源规则里的 JSON.parse 抛出
        // "SyntaxError: Unexpected token: <"（表现为获取正文失败，刷新后可恢复）。
        val jsSemaphore = Semaphore(BUBBLE_JS_MAX_CONCURRENCY)

        // 并行处理每个段落
        return contents.map { content ->
            scope.async { preprocessBubbleJsInText(content, source, jsSemaphore) }
        }.awaitAll()
    }

    /**
     * 处理单个段落：找出所有含 js 字段的 img src，异步执行 JS，替换为 JS 返回值
     */
    private suspend fun preprocessBubbleJsInText(
        text: String,
        source: BaseSource,
        jsSemaphore: Semaphore,
    ): String {
        val matcher = AppPattern.imgPattern.matcher(text)
        // 收集需要处理的 (start, end, originalSrc)
        val tasks = mutableListOf<Triple<Int, Int, String>>()
        while (matcher.find()) {
            val originalSrc = matcher.group(1) ?: continue
            if (isJsBubbleSrc(originalSrc)) {
                tasks.add(Triple(matcher.start(1), matcher.end(1), originalSrc))
            }
        }
        if (tasks.isEmpty()) return text

        // 段落内多个 img 的 JS 执行同样受全局信号量限制，避免并发请求轰炸书源服务器
        val results = tasks.map { (_, _, src) ->
            scope.async {
                currentCoroutineContext().ensureActive()
                jsSemaphore.withPermit {
                    executeBubbleJs(src, source)
                }
            }
        }.awaitAll()

        // 按位置倒序替换，避免偏移问题
        var result = text
        for (i in tasks.indices.reversed()) {
            val (start, end, originalSrc) = tasks[i]
            val newRenderSrc = results[i]
            if (newRenderSrc != null && newRenderSrc != originalSrc) {
                // 从 originalSrc 中分离出 option 部分，拼装成 "newRenderSrc,option"
                val urlMatcher = paramPattern.matcher(originalSrc)
                if (urlMatcher.find()) {
                    val optionStr = originalSrc.substring(urlMatcher.end())
                    val replaced = "$newRenderSrc,$optionStr"
                    result = result.substring(0, start) + replaced + result.substring(end)
                }
            }
        }
        return result
    }

    /**
     * 判断 img src 是否需要执行 js 字段：
     * - 已含 js 字段
     * - style=TEXT 或没有 style（参考强制气泡候选判断）
     */
    private fun isJsBubbleSrc(src: String): Boolean {
        val urlMatcher = paramPattern.matcher(src)
        if (!urlMatcher.find()) return false
        val optionStr = src.substring(urlMatcher.end())
        val option = GSON.fromJsonObject<Map<String, String>>(optionStr).getOrNull() ?: return false
        val js = option.valueIgnoreCase("js")?.takeIf { it.isNotBlank() } ?: return false
        // 必须是可能的段评入口：style=TEXT 或其他强信号
        val style = option.valueIgnoreCase("style")
        val styleText = style.equals("TEXT", ignoreCase = true)
        val type = option.valueIgnoreCase("type").orEmpty().lowercase()
        val knownType = type in FORCED_BUBBLE_TYPES
        val click = listOfNotNull(option.valueIgnoreCase("click"), option.valueIgnoreCase("pclick"))
            .joinToString(separator = "\n").lowercase()
        val clickLike = click.contains("showcmt(") ||
            click.contains("showcomment(") ||
            click.contains("showreview(") ||
            click.contains("paragraph")
        return styleText || knownType || clickLike
    }

    /**
     * 执行 js 字段，返回 JS 的执行结果（字符串）。
     * 执行失败或返回无效值时返回 null（保持原 src 不变）。
     */
    private suspend fun executeBubbleJs(
        src: String,
        source: BaseSource,
    ): String? {
        val urlMatcher = paramPattern.matcher(src)
        if (!urlMatcher.find()) return null
        val urlNoOption = src.substring(0, urlMatcher.start())
        val optionStr = src.substring(urlMatcher.end())
        val option = GSON.fromJsonObject<Map<String, String>>(optionStr).getOrNull() ?: return null
        val js = option.valueIgnoreCase("js")?.takeIf { it.isNotBlank() } ?: return null

        return try {
            val result = AnalyzeRule(book, source).apply {
                setCoroutineContext(currentCoroutineContext())
                setBaseUrl(bookChapter.url)
                setChapter(bookChapter)
            }.evalJS(js, urlNoOption)?.toString()
            // 过滤无效返回值，避免把 "undefined"/空串写进 src
            if (result.isNullOrBlank() || result == "undefined" || result == "null") null else result
        } catch (e: Exception) {
            AppLog.put("强制气泡JS执行失败: ${e.localizedMessage}", e)
            null
        }
    }

    private companion object {
        /** 气泡 js 执行的最大并发数，防止瞬时请求压垮书源服务器（见 preprocessBubbleJs 注释） */
        private const val BUBBLE_JS_MAX_CONCURRENCY = 2
        val FORCED_BUBBLE_TEXT_REGEX = Regex(
            """<text\b[^>]*>(.*?)</text>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val FORCED_BUBBLE_DISPLAY_PARAM_REGEX = Regex(
            """(?:^|[?&,])(?:displayText|num|\${'$'}num|\${'$'}\{num\}|\{\{num\}\}|count|text|label)=([^&,\s]{1,48})""",
            RegexOption.IGNORE_CASE,
        )
        val FORCED_BUBBLE_COLOR_PARAM_REGEX = Regex(
            """(?:^|[?&,])(?:displayColor|color|\${'$'}color|\${'$'}\{color\}|\{\{color\}\})=([^&,\s]{1,32})""",
            RegexOption.IGNORE_CASE,
        )
        val FORCED_BUBBLE_CREATE_SVG_COUNT_REGEX = Regex(
            """createSvg2?\s*\((?:[^,)]*,){3}\s*([0-9]{1,8})""",
            RegexOption.IGNORE_CASE,
        )
        const val PARAGRAPH_BUBBLE_PREFIX = "dp:"
        val FORCED_BUBBLE_TYPES = setOf(
            "qd",
            "fqpl",
            "fanqie",
            "cmt",
            "comment",
            "comments",
            "review",
            "paragraph",
            "paragraphcomment",
        )
    }
}
