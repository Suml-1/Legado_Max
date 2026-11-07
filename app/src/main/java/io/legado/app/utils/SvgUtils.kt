package io.legado.app.utils

import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.drawable.PictureDrawable
import android.util.Size
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream
import com.caverock.androidsvg.SVG
import kotlin.math.max

@Suppress("WeakerAccess", "MemberVisibilityCanBePrivate")
object SvgUtils {

    private val xmlDeclarationRegex = Regex("^\\s*<\\?xml[^>]*\\?>", RegexOption.IGNORE_CASE)
    private val svgDoctypeRegex = Regex(
        "<!DOCTYPE\\s+svg\\b[^>]*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /**
     * 从Svg中解码bitmap
     */

    fun createBitmap(filePath: String, width: Int, height: Int? = null): Bitmap? = kotlin.runCatching {
        val inputStream = FileInputStream(filePath)
        createBitmap(inputStream, width, height)
    }.getOrNull()

    fun createBitmap(inputStream: InputStream, width: Int, height: Int? = null): Bitmap? = kotlin.runCatching {
        val svg = SVG.getFromInputStream(inputStream)
        createBitmap(svg, width, height)
    }.getOrNull()

    /**
     * 从文件路径创建 Bitmap，支持带 XML 声明/DOCTYPE 的 SVG。
     *
     * 先尝试直接解析；失败后移除 XML 声明和 DOCTYPE 再重试。
     * 某些 SVG 文件（如 Reeden 主题包中的底栏图标）带有 DOCTYPE 声明，
     * androidsvg 库解析时会尝试加载外部 DTD 导致失败。
     */
    fun createBitmapFromFile(filePath: String, width: Int, height: Int? = null): Bitmap? {
        // 读不到文件（不存在 / 无权限，如备份恢复后残留的其它包名外部目录路径）时按"没有图标"处理，
        // 不能把异常抛给调用方：底栏图标是在 Activity resume 时同步加载的，抛出去会直接崩掉主界面
        val bytes = kotlin.runCatching { java.io.File(filePath).readBytes() }.getOrNull()
            ?: return null
        // 先尝试直接解析
        createBitmap(ByteArrayInputStream(bytes), width, height)?.let { return it }
        // 失败后移除 XML 声明和 DOCTYPE 再重试
        val text = bytes.toString(Charsets.UTF_8)
        val sanitized = svgDoctypeRegex
            .replace(xmlDeclarationRegex.replace(text.trimStart('\uFEFF'), ""), "")
            .trimStart()
        if (sanitized.isNotBlank() && sanitized != text) {
            return createBitmap(ByteArrayInputStream(sanitized.toByteArray(Charsets.UTF_8)), width, height)
        }
        return null
    }

    fun createDrawable(inputStream: InputStream): Pair<PictureDrawable, Size>? = kotlin.runCatching {
        val svg = SVG.getFromInputStream(inputStream)
        val size = getSize(svg)
        val picture = svg.renderToPicture()
        Pair(PictureDrawable(picture), size)
    }.getOrNull()

    // 获取svg图片大小
    fun getSize(filePath: String): Size? = kotlin.runCatching {
        val inputStream = FileInputStream(filePath)
        getSize(inputStream)
    }.getOrNull()

    fun getSize(inputStream: InputStream): Size? = kotlin.runCatching {
        val svg = SVG.getFromInputStream(inputStream)
        getSize(svg)
    }.getOrNull()

    // ///// private method
    private fun createBitmap(svg: SVG, width: Int? = null, height: Int? = null): Bitmap {
        val size = getSize(svg)
        val wRatio = width?.let { size.width / it } ?: -1
        val hRatio = height?.let { size.height / it } ?: -1
        // 如果超出指定大小，则缩小相应的比例
        val ratio = when {
            wRatio > 1 && hRatio > 1 -> max(wRatio, hRatio)
            wRatio > 1 -> wRatio
            hRatio > 1 -> hRatio
            else -> 1
        }

        val viewBox: RectF? = svg.documentViewBox
        if (viewBox == null && size.width > 0 && size.height > 0) {
            svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
        }

        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")

        val bitmapWidth = size.width / ratio
        val bitmapHeight = size.height / ratio
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)

        svg.renderToCanvas(Canvas(bitmap))
        return bitmap
    }

    private fun getSize(svg: SVG): Size {
        val width = svg.documentWidth.toInt().takeIf { it > 0 }
            ?: (svg.documentViewBox.right - svg.documentViewBox.left).toInt()
        val height = svg.documentHeight.toInt().takeIf { it > 0 }
            ?: (svg.documentViewBox.bottom - svg.documentViewBox.top).toInt()
        return Size(width, height)
    }
}
