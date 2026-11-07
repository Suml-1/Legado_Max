package io.legado.app.ui.book.read.page.provider

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.FontMetrics
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.os.postDelayed
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookContent
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.RealPathUtil

import io.legado.app.utils.dpToPx
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isPad
import io.legado.app.utils.postEvent
import io.legado.app.utils.spToPx
import io.legado.app.utils.textHeight
import kotlinx.coroutines.CoroutineScope
import splitties.init.appCtx
import androidx.core.net.toUri
import kotlin.math.abs

/**
 * 解析内容生成章节和页面
 */
@Suppress("DEPRECATION", "ConstPropertyName")
object ChapterProvider {
    //用于图片字的替换
    const val srcReplaceStr = "袮" //▩▣ //这是不应该存在的汉字,会替换为祢，这个字符用来标记
    const val srcReplaceChar = '袮'
    const val srcReplacementChar = '祢'
    //用于评论按钮的替换
    const val reviewStr = "꧁"
    const val reviewChar = '꧁'
    const val indentChar = "　"

    @JvmStatic
    var viewWidth = 0
        private set

    @JvmStatic
    var viewHeight = 0
        private set

    @JvmStatic
    var paddingLeft = 0
        private set

    @JvmStatic
    var paddingTop = 0
        private set

    @JvmStatic
    var paddingRight = 0
        private set

    @JvmStatic
    var paddingBottom = 0
        private set

    @JvmStatic
    var visibleWidth = 0
        private set

    @JvmStatic
    var visibleHeight = 0
        private set

    @JvmStatic
    var visibleRight = 0
        private set

    @JvmStatic
    var visibleBottom = 0
        private set

    @JvmStatic
    var lineSpacingExtra = 0f

    /**
     * 正文标题分段后的标题行距：与 [lineSpacingExtra] 同单位，但只作用于分段后的标题各行，
     * 不跟随正文行距与段距（正文标题设置里单独调节）
     */
    @JvmStatic
    var titleLineSpacing = 0f
        private set

    @JvmStatic
    var paragraphSpacing = 0
        private set

    @JvmStatic
    var titleTopSpacing = 0
        private set

    @JvmStatic
    var titleBottomSpacing = 0
        private set

    @JvmStatic
    var indentCharWidth = 0f
        private set

    @JvmStatic
    var titlePaintTextHeight = 0f
        private set

    @JvmStatic
    var titleSubPaintTextHeight = 0f
        private set

    @JvmStatic
    var contentPaintTextHeight = 0f
        private set

    @JvmStatic
    var titlePaintFontMetrics = FontMetrics()

    @JvmStatic
    var titleSubPaintFontMetrics = FontMetrics()

    @JvmStatic
    var contentPaintFontMetrics = FontMetrics()

    @JvmStatic
    var typeface: Typeface? = Typeface.DEFAULT
        private set

    @JvmStatic
    var titlePaint: TextPaint = TextPaint()

    /**
     * 正文标题分段后次行（第二段起）用的画笔。
     *
     * 字体、字重、颜色、字间距全部沿用 [titlePaint]，只换字号，因此这里不重复走
     * applyStrokeWeight——它会写 [titleThinStrokeWidth]，重复调用会把首行的擦除宽度覆盖掉。
     */
    @JvmStatic
    var titleSubPaint: TextPaint = TextPaint()

    @JvmStatic
    var contentPaint: TextPaint = TextPaint()

    /**
     * 标题/正文字重比所用字体实际能渲染的字面更轻时的擦除宽度
     * （&gt;0 表示绘制阶段需用背景色描边擦掉字心边缘）。由 [getPaints] 按字号算出，
     * 供 TextLine/TextColumn 的绘制路径消费。
     */
    @JvmStatic
    var titleThinStrokeWidth: Float = 0f
        private set

    @JvmStatic
    var contentThinStrokeWidth: Float = 0f
        private set

    @JvmStatic
    var reviewPaint: TextPaint = TextPaint()

    @JvmStatic
    var doublePage = false
        private set

    @JvmStatic
    var visibleRect = RectF()

    init {
        upStyle()
    }

    fun getTextChapterAsync(
        scope: CoroutineScope,
        book: Book,
        bookChapter: BookChapter,
        displayTitle: String,
        bookContent: BookContent,
        chapterSize: Int,
    ): TextChapter {

        val textChapter = TextChapter(
            bookChapter,
            bookChapter.index, displayTitle,
            chapterSize,
            bookContent.sameTitleRemoved,
            bookChapter.isVip,
            bookChapter.isPay,
            bookContent.effectiveReplaceRules
        ).apply {
            createLayout(scope, book, bookContent)
        }

        return textChapter
    }

    /**
     * 更新样式
     */
    fun upStyle() {
        typeface = getTypeface(ReadBookConfig.textFont)
        getPaints(typeface).let {
            titlePaint = it.first
            contentPaint = it.second
//            reviewPaint.color = contentPaint.color
//            reviewPaint.textSize = contentPaint.textSize * 0.45f
//            reviewPaint.textAlign = Paint.Align.CENTER
        }
        // 次行只在标题分段后出现，字号 = 首行字号 × 倍数（<1 比首行小，>1 比首行大）。
        // 下限 1px：倍数可以拖到 0，字号为 0 会让文字测量与绘制失去意义
        titleSubPaint = TextPaint(titlePaint).apply {
            textSize = (titlePaint.textSize * ReadBookConfig.titleSubScale).coerceAtLeast(1f)
        }
        //间距
        lineSpacingExtra = ReadBookConfig.lineSpacingExtra / 10f
        titleLineSpacing = ReadBookConfig.titleLineSpacingExtra / 10f
        paragraphSpacing = ReadBookConfig.paragraphSpacing
        titleTopSpacing = ReadBookConfig.titleTopSpacing.dpToPx()
        titleBottomSpacing = ReadBookConfig.titleBottomSpacing.dpToPx()
        val bodyIndent = ReadBookConfig.paragraphIndent
        indentCharWidth = if (bodyIndent.isNotEmpty()) {
            var indentWidth = StaticLayout.getDesiredWidth(bodyIndent, contentPaint)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                indentWidth += contentPaint.letterSpacing * contentPaint.textSize
            }
            indentWidth / bodyIndent.length
        } else {
            0f
        }
        titlePaintTextHeight = titlePaint.textHeight
        contentPaintTextHeight = contentPaint.textHeight
        titleSubPaintTextHeight = titleSubPaint.textHeight
        titlePaintFontMetrics = titlePaint.fontMetrics
        contentPaintFontMetrics = contentPaint.fontMetrics
        titleSubPaintFontMetrics = titleSubPaint.fontMetrics
        upLayout()
    }

    private fun getTypeface(fontPath: String): Typeface? {
        return kotlin.runCatching {
            when {
                fontPath.isContentScheme() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    appCtx.contentResolver
                        .openFileDescriptor(fontPath.toUri(), "r")!!
                        .use {
                            Typeface.Builder(it.fileDescriptor).build()
                        }
                }

                fontPath.isContentScheme() -> {
                    Typeface.createFromFile(RealPathUtil.getPath(appCtx, fontPath.toUri()))
                }

                fontPath.isNotEmpty() -> Typeface.createFromFile(fontPath)
                else -> when (AppConfig.systemTypefaces) {
                    1 -> Typeface.SERIF
                    2 -> Typeface.MONOSPACE
                    else -> Typeface.SANS_SERIF
                }
            }
        }.getOrElse {
            ReadBookConfig.textFont = ""
            ReadBookConfig.save()
            Typeface.SANS_SERIF
        } ?: Typeface.DEFAULT
    }

    /**
     * 创建标题和正文的画笔
     *
     * 两种字重模式都归到一个"有效字重"上（[ReadBookConfig.getTitleBoldWeight] / [ReadBookConfig.getTextBoldWeight]）：
     * Android 9+ 先交给 `Typeface.create(weight)` 选静态字面，可变字体再补 `wght` 变体轴；
     * 字体自身给不出的那部分字重（第三方字体多是单字面、系统字体的中文回退只有常规/粗体两档）
     * 一律由 [applyStrokeWeight] 补上——比基准重就描边外扩加粗，比基准轻就记录擦除宽度、
     * 绘制时用背景色再描一遍字擦掉字心边缘
     *
     * @param typeface 基础字体
     * @return Pair<标题画笔, 正文画笔>
     */
    private fun getPaints(typeface: Typeface?): Pair<TextPaint, TextPaint> {
        val isCustomFont = ReadBookConfig.textFont.isNotEmpty()
        val isFineMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && AppConfig.textBoldMode == 1
        // 描边系数＝"500 个字重单位"折算成多少字高。参考实现给的是 0.10，
        // 但按实测校准（中文常规→粗体的墨迹差 ≈ 3.4% 字高 / 300 单位）0.05 才贴合真实字面，
        // 0.10 在中文 900 档会把字腔糊成实心块；粗略模式三档间隔更大，再收一档
        val strokeCoefficient = if (isFineMode) 0.05f else 0.04f
        // 第三方字体的粗略模式统一回落到 NORMAL，三档差异全部交给描边/擦除，
        // 否则 Typeface.create 的合成粗体会和描边叠加，粗体档明显过粗
        val useWeightAxis = !(isCustomFont && !isFineMode)

        val titleWeight = ReadBookConfig.getTitleBoldWeight()
        val textWeight = ReadBookConfig.getTextBoldWeight()

        val titleFont = createWeightedTypeface(typeface, titleWeight, useWeightAxis)
        val textFont = createWeightedTypeface(typeface, textWeight, useWeightAxis)
        // 第三方字体是单一字面，基准恒为 400；系统字体按中文回退阶梯估算实际落到的字面
        val titleBase = if (isCustomFont) 400 else estimateSystemRenderedWeight(titleWeight)
        val textBase = if (isCustomFont) 400 else estimateSystemRenderedWeight(textWeight)

        //标题
        val tPaint = TextPaint()
        tPaint.color = ReadBookConfig.textColor
        tPaint.letterSpacing = ReadBookConfig.letterSpacing
        tPaint.typeface = titleFont
        tPaint.textSize = with(ReadBookConfig) { textSize + titleSize }.toFloat().spToPx()
        tPaint.isAntiAlias = true
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && AppConfig.optimizeRender) {
            tPaint.isLinearText = true
        }
        applyWeightAxis(tPaint, titleWeight, useWeightAxis)
        titleThinStrokeWidth = applyStrokeWeight(tPaint, titleWeight, titleBase, strokeCoefficient)
        //正文
        val cPaint = TextPaint()
        cPaint.color = ReadBookConfig.textColor
        cPaint.letterSpacing = ReadBookConfig.letterSpacing
        cPaint.typeface = textFont
        cPaint.textSize = ReadBookConfig.textSize.toFloat().spToPx()
        cPaint.isAntiAlias = true
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && AppConfig.optimizeRender) {
            cPaint.isLinearText = true
        }
        applyWeightAxis(cPaint, textWeight, useWeightAxis)
        contentThinStrokeWidth = applyStrokeWeight(cPaint, textWeight, textBase, strokeCoefficient)
        return Pair(tPaint, cPaint)
    }

    /**
     * 按字重取字体：Android 9+ 走三参数 weight，更低版本回退到两参数 style
     */
    private fun createWeightedTypeface(typeface: Typeface?, weight: Int, useWeightAxis: Boolean): Typeface {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return Typeface.create(typeface, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        }
        return if (useWeightAxis) {
            Typeface.create(typeface, weight, false)
        } else {
            Typeface.create(typeface, Typeface.NORMAL)
        }
    }

    /**
     * 补 `wght` 变体轴：可变字体（含系统可变字体）由此拿到真正的连续字重，静态字面会忽略该设置
     */
    private fun applyWeightAxis(paint: TextPaint, weight: Int, useWeightAxis: Boolean) {
        if (useWeightAxis && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            paint.setFontVariationSettings("'wght' $weight")
        }
    }

    /**
     * 变细：用"字后面实际的颜色"把刚画过的字再描一遍，擦掉字心边缘
     * （目标字重比字体能渲染的字面更轻时生效）。
     * 描边宽度为 0（不需要变细）、或推不出擦除色时静默跳过。
     * 会临时改 paint 的 style/color/strokeWidth，返回前还原，因此可以安全作用于共享画笔。
     *
     * @param localBg 字后面实际的颜色，来自 [io.legado.app.ui.book.read.page.entities.column.TextBaseColumn.eraseBgColor]；
     *   null 表示字后面就是页面背景，用背景均色
     */
    @JvmStatic
    fun drawThinStroke(
        canvas: Canvas,
        paint: Paint,
        isTitle: Boolean,
        text: String,
        start: Int,
        end: Int,
        x: Float,
        y: Float,
        localBg: Int? = null
    ) {
        val thinStrokeWidth = if (isTitle) {
            // 标题分段后的次行字号被缩放过，擦除宽度按本行画笔的实际字号同比换算，
            // 否则次行会按首行字号擦除，表现为次行字心边缘擦多/擦少
            val baseSize = titlePaint.textSize
            if (paint.textSize > 0f && baseSize > 0f && paint.textSize != baseSize) {
                titleThinStrokeWidth * (paint.textSize / baseSize)
            } else {
                titleThinStrokeWidth
            }
        } else {
            contentThinStrokeWidth
        }
        if (thinStrokeWidth <= 0f) return
        // 系统字体的擦除量是按汉字回退阶梯估算的，而拉丁字形本来就有真实字面（Roboto 有 100~900 六级），
        // 按汉字基准擦会在极细档把纯拉丁文本擦没，所以系统字体只对含汉字的文本生效；
        // 第三方字体是单字面，拉丁字形同样只能靠擦除变细，不做这个限制
        if (ReadBookConfig.textFont.isEmpty() && !hasHan(text, start, end)) return
        // 擦除色必须等于字后面实际的颜色，否则会在字形周围留一圈错色的包边：
        // 色块不透明直接用；半透明块色先和页面背景均色合成；没有块色用页面背景均色
        val eraseColor = when {
            localBg == null || Color.alpha(localBg) == 0 -> ReadBookConfig.bgMeanColor
            Color.alpha(localBg) == 0xFF -> localBg
            ReadBookConfig.bgMeanColor == 0 -> return
            else -> ColorUtils.blendColors(ReadBookConfig.bgMeanColor, localBg, Color.alpha(localBg) / 255f)
        }
        if (eraseColor == 0) return
        val oldStyle = paint.style
        val oldColor = paint.color
        val oldStrokeWidth = paint.strokeWidth
        paint.style = Paint.Style.STROKE
        paint.color = eraseColor
        paint.strokeWidth = thinStrokeWidth
        canvas.drawText(text, start, end, x, y, paint)
        paint.style = oldStyle
        paint.color = oldColor
        paint.strokeWidth = oldStrokeWidth
    }

    /** [start, end) 区间内是否含汉字（含扩展区与兼容区） */
    private fun hasHan(text: String, start: Int, end: Int): Boolean {
        var i = start
        while (i < end) {
            val codePoint = text.codePointAt(i)
            if (isHan(codePoint)) return true
            i += Character.charCount(codePoint)
        }
        return false
    }

    private fun isHan(codePoint: Int): Boolean =
        codePoint in 0x3400..0x4DBF ||      // 扩展 A
            codePoint in 0x4E00..0x9FFF ||  // 基本区
            codePoint in 0xF900..0xFAFF ||  // 兼容汉字
            codePoint in 0x20000..0x3FFFF   // 扩展 B 及以后

    /**
     * 估算系统字体把某个字重实际渲染成多少，作为补差额的基准。
     *
     * 系统字体的中文来自回退族。本机（Android 12 / AOSP 字体集）探针实测 `sans-serif`：
     * 中文只有 NotoSansSC-Regular(400) 与 NotoSansSC-Bold(700) 两个字面 —— 请求 ≤550 落 400、
     * 600~800 落 700、900 落更重的一档；而拉丁字形另有 100/300/400/500/700/900 六级。
     * 按中文这一级取基准，差额由描边/擦除补上，中文因此也连续；
     * 混排时拉丁一侧最多被多补/少补 2% 字高（它本来有真实字面，只是补得不够准）。
     */
    private fun estimateSystemRenderedWeight(weight: Int): Int = when {
        weight <= 550 -> 400
        weight <= 800 -> 700
        else -> 900
    }

    /**
     * 给画笔补上"字体自身给不出"的字重差：比基准重就用描边外扩加粗，比基准轻则返回擦除宽度
     *
     * 两个方向用同一条线性响应：中文回退的字面间隔是 300 单位（400→700），
     * 若变细一侧用更陡的曲线，跨过"常规→粗体"切换点（如 550→600）时会出现
     * "往右拖反而变细"的反向抖动，所以粗细两端共用 [strokeCoefficient]。
     *
     * @param weight 目标字重（100~900）
     * @param baseWeight 该字体实际能渲染到的字重（第三方字体恒为 400，见 [estimateSystemRenderedWeight]）
     * @return 擦除宽度，0f 表示不需要擦除
     */
    private fun applyStrokeWeight(
        paint: TextPaint,
        weight: Int,
        baseWeight: Int,
        strokeCoefficient: Float
    ): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return 0f
        val delta = weight - baseWeight
        if (delta == 0) return 0f
        val strokeWidth = abs(delta) / 500f * paint.textSize * strokeCoefficient
        if (delta > 0) {
            paint.style = Paint.Style.FILL_AND_STROKE
            paint.strokeWidth = strokeWidth
            return 0f
        }
        // 变细靠"用背景色把字心边缘描掉"；上限压到字号的 2%，避免重=100 时擦除过度导致文字几乎消失
        return strokeWidth.coerceAtMost(paint.textSize * 0.02f)
    }

    /**
     * 更新View尺寸
     */
    fun upViewSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            return
        }
        if (width != viewWidth || height != viewHeight) {
            notifyViewSizeChange(width, height)
        }
    }

    private fun notifyViewSizeChange(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        upLayout()
        // 容器尺寸变更后，清除预加载的章节缓存
        // prevTextChapter/nextTextChapter 中的页面按旧尺寸排版，
        // 若不清除，后续 moveToNextChapter/moveToPrevChapter 会直接使用旧排版数据，
        // 导致内容渲染不全（横竖屏切换/小窗全屏切换场景复现）
        ReadBook.prevTextChapter = null
        ReadBook.nextTextChapter = null
        postEvent(EventBus.UP_CONFIG, arrayListOf(5))
    }

    /**
     * 更新绘制尺寸
     */
    fun upLayout() {
        when (AppConfig.doublePageHorizontal) {
            "0" -> doublePage = false
            "1" -> doublePage = true
            "2" -> {
                doublePage = (viewWidth > viewHeight)
                        && ReadBook.pageAnim() != 3
            }

            "3" -> {
                doublePage = (viewWidth > viewHeight || appCtx.isPad)
                        && ReadBook.pageAnim() != 3
            }
        }

        if (viewWidth <= 0 || viewHeight <= 0) {
            return
        }

        paddingLeft = ReadBookConfig.paddingLeft.dpToPx()
        paddingTop = ReadBookConfig.paddingTop.dpToPx()
        paddingRight = ReadBookConfig.paddingRight.dpToPx()
        paddingBottom = ReadBookConfig.paddingBottom.dpToPx()
        visibleWidth = if (doublePage) {
            viewWidth / 2 - paddingLeft - paddingRight
        } else {
            viewWidth - paddingLeft - paddingRight
        }
        //留1dp画最后一行下划线
        visibleHeight = viewHeight - paddingTop - paddingBottom
        visibleRight = viewWidth - paddingRight
        visibleBottom = paddingTop + visibleHeight

        if (paddingLeft >= visibleRight || paddingTop >= visibleBottom) {
            AppLog.put("边距设置过大，请重新设置", toast = true)
            setFallbackLayout()
        }

        visibleRect.set( //留余，让溢出时也显示
            paddingLeft.toFloat() - 10,
            paddingTop.toFloat() - 10,
            visibleRight.toFloat() + 10,
            visibleBottom.toFloat() + 10f.dpToPx() //下划线最远10dp
        )

    }

    private fun setFallbackLayout() {
        paddingLeft = 20.dpToPx()
        paddingTop = 5.dpToPx()
        paddingRight = 20.dpToPx()
        paddingBottom = 5.dpToPx()
        visibleWidth = if (doublePage) {
            viewWidth / 2 - paddingLeft - paddingRight
        } else {
            viewWidth - paddingLeft - paddingRight
        }
        //留1dp画最后一行下划线
        visibleHeight = viewHeight - paddingTop - paddingBottom
        visibleRight = viewWidth - paddingRight
        visibleBottom = paddingTop + visibleHeight
    }

}
