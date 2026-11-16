package io.legado.app.ui.design.components.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import io.legado.app.R
import io.legado.app.ui.source.edit.SourceEditCodeHighlighter
import io.legado.app.ui.widget.code.codeStringPattern
import io.legado.app.ui.widget.code.cssNumberPattern
import io.legado.app.ui.widget.code.cssPropertyPattern
import io.legado.app.ui.widget.code.cssStringPattern
import io.legado.app.ui.widget.code.debugHeaderPattern
import io.legado.app.ui.widget.code.debugSectionPattern
import io.legado.app.ui.widget.code.debugTimestampPattern
import io.legado.app.ui.widget.code.debugUrlPattern
import io.legado.app.ui.widget.code.htmlAttributePattern
import io.legado.app.ui.widget.code.htmlEndTagNamePattern
import io.legado.app.ui.widget.code.htmlStartTagNamePattern
import io.legado.app.ui.widget.code.jsCommentPattern
import io.legado.app.ui.widget.code.jsKeywordPattern
import io.legado.app.ui.widget.code.jsLiteralPattern
import io.legado.app.ui.widget.code.jsNumberPattern
import io.legado.app.ui.widget.code.jsStringPattern
import io.legado.app.ui.widget.code.jsonKeyPattern
import io.legado.app.ui.widget.code.jsonLiteralPattern
import io.legado.app.ui.widget.code.jsonNumberPattern
import io.legado.app.ui.widget.code.legadoPattern
import io.legado.app.ui.widget.code.wrapPattern
import java.util.regex.Pattern

enum class NgCodeHighlightMode {
    SOURCE,
    DEFAULT,
    DEBUG_LOG,
}

private data class NgCodePalette(
    val legado: Color,
    val comment: Color,
    val string: Color,
    val number: Color,
    val keyword: Color,
    val key: Color,
    val tag: Color,
)

@Composable
fun rememberNgCodeVisualTransformation(
    mode: NgCodeHighlightMode,
    sourceKey: String? = null,
): VisualTransformation {
    val palette = rememberNgCodePalette()
    return remember(mode, sourceKey, palette) {
        VisualTransformation { source ->
            TransformedText(
                highlightCode(source.text, mode, sourceKey, palette),
                OffsetMapping.Identity,
            )
        }
    }
}

@Composable
fun rememberNgHighlightedCode(
    text: String,
    mode: NgCodeHighlightMode,
    sourceKey: String? = null,
): AnnotatedString {
    val palette = rememberNgCodePalette()
    return remember(text, mode, sourceKey, palette) {
        highlightCode(text, mode, sourceKey, palette)
    }
}

@Composable
private fun rememberNgCodePalette(): NgCodePalette {
    return NgCodePalette(
        legado = colorResource(R.color.md_orange_900),
        comment = colorResource(R.color.md_blue_grey_500),
        string = colorResource(R.color.md_green_800),
        number = colorResource(R.color.md_purple_700),
        keyword = colorResource(R.color.md_light_blue_600),
        key = colorResource(R.color.md_blue_800),
        tag = colorResource(R.color.md_blue_700),
    )
}

private fun highlightCode(
    text: String,
    mode: NgCodeHighlightMode,
    sourceKey: String?,
    palette: NgCodePalette,
): AnnotatedString {
    if (text.length > MAX_HIGHLIGHT_LENGTH) return AnnotatedString(text)
    val builder = AnnotatedString.Builder(text)
    val groups = when (mode) {
        NgCodeHighlightMode.SOURCE -> SourceEditCodeHighlighter.groupsOf(sourceKey)
        NgCodeHighlightMode.DEFAULT -> setOf(
            SourceEditCodeHighlighter.Group.LEGADO,
            SourceEditCodeHighlighter.Group.JSON,
            SourceEditCodeHighlighter.Group.JS,
        )
        NgCodeHighlightMode.DEBUG_LOG -> emptySet()
    }
    groups.forEach { group ->
        when (group) {
            SourceEditCodeHighlighter.Group.LEGADO ->
                builder.addPattern(text, legadoPattern, palette.legado)
            SourceEditCodeHighlighter.Group.JS -> {
                builder.addPattern(text, wrapPattern, palette.comment)
                builder.addPattern(text, jsCommentPattern, palette.comment)
                builder.addPattern(text, jsStringPattern, palette.string)
                builder.addPattern(text, jsNumberPattern, palette.number)
                builder.addPattern(text, jsLiteralPattern, palette.number)
                builder.addPattern(text, jsKeywordPattern, palette.keyword)
            }
            SourceEditCodeHighlighter.Group.JSON -> {
                builder.addPattern(text, codeStringPattern, palette.string)
                builder.addPattern(text, jsonNumberPattern, palette.number)
                builder.addPattern(text, jsonLiteralPattern, palette.number)
                builder.addPattern(text, jsonKeyPattern, palette.key)
            }
            SourceEditCodeHighlighter.Group.HTML -> {
                builder.addPattern(text, htmlStartTagNamePattern, palette.key)
                builder.addPattern(text, htmlEndTagNamePattern, palette.key)
                builder.addPattern(text, htmlAttributePattern, palette.number)
                builder.addPattern(text, codeStringPattern, palette.string)
            }
            SourceEditCodeHighlighter.Group.CSS -> {
                builder.addPattern(text, cssPropertyPattern, palette.key)
                builder.addPattern(text, cssStringPattern, palette.string)
                builder.addPattern(text, cssNumberPattern, palette.number)
            }
        }
    }
    if (mode == NgCodeHighlightMode.DEBUG_LOG) {
        builder.addPattern(text, debugSectionPattern, palette.comment)
        builder.addPattern(text, debugTimestampPattern, palette.tag)
        builder.addPattern(text, debugUrlPattern, palette.number)
        builder.addPattern(text, debugHeaderPattern, palette.key)
        builder.addPattern(text, codeStringPattern, palette.string)
        builder.addPattern(text, jsonNumberPattern, palette.number)
        builder.addPattern(text, jsonLiteralPattern, palette.number)
        builder.addPattern(text, jsonKeyPattern, palette.key)
        builder.addPattern(text, htmlStartTagNamePattern, palette.key)
        builder.addPattern(text, htmlEndTagNamePattern, palette.key)
        builder.addPattern(text, htmlAttributePattern, palette.number)
    }
    return builder.toAnnotatedString()
}

private fun AnnotatedString.Builder.addPattern(source: String, pattern: Pattern, color: Color) {
    val matcher = pattern.matcher(source)
    while (matcher.find()) {
        if (matcher.start() < matcher.end()) {
            addStyle(SpanStyle(color = color), matcher.start(), matcher.end())
        }
    }
}

private const val MAX_HIGHLIGHT_LENGTH = 64 * 1024
