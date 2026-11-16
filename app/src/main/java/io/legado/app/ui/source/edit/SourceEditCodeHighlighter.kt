package io.legado.app.ui.source.edit

import io.legado.app.ui.widget.code.CodeView
import io.legado.app.ui.widget.code.addCssPattern
import io.legado.app.ui.widget.code.addHtmlPattern
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addLegadoPattern

object SourceEditCodeHighlighter {

    private const val JS_LANGUAGE = "source.js"
    private const val HTML_LANGUAGE = "text.html.basic"
    private const val EDITOR_MAX_HIGHLIGHT_LENGTH = 64 * 1024

    private val ruleKeys = setOf(
        "bookUrlPattern",
        "searchUrl",
        "exploreUrl",
        "sortUrl",
        "bookList",
        "name",
        "author",
        "kind",
        "wordCount",
        "lastChapter",
        "intro",
        "coverUrl",
        "bookUrl",
        "init",
        "tocUrl",
        "canReName",
        "downloadUrls",
        "chapterList",
        "chapterName",
        "chapterUrl",
        "isVolume",
        "updateTime",
        "isVip",
        "isPay",
        "nextTocUrl",
        "content",
        "nextContentUrl",
        "subContent",
        "replaceRegex",
        "title",
        "sourceRegex",
        "ruleArticles",
        "ruleNextPage",
        "ruleTitle",
        "rulePubDate",
        "ruleDescription",
        "ruleImage",
        "ruleLink",
        "ruleContent",
        "contentWhitelist",
        "contentBlacklist",
        "url",
        "voicesUrl",
        "contentType",
        "concurrentRate"
    )

    private val jsKeys = setOf(
        "loginCheckJs",
        "coverDecodeJs",
        "loginUrl",
        "jsLib",
        "preUpdateJs",
        "formatJs",
        "imageDecode",
        "webJs",
        "payAction",
        "callBackJs",
        "startJs",
        "preloadJs",
        "injectJs",
        "shouldOverrideUrlLoading"
    )

    private val jsonKeys = setOf(
        "header",
        "variableComment"
    )

    private val jsJsonKeys = setOf(
        "loginUi"
    )

    private val htmlKeys = setOf(
        "startHtml"
    )

    private val cssKeys = setOf(
        "imageStyle",
        "startStyle",
        "style"
    )

    enum class Group {
        LEGADO,
        JS,
        JSON,
        HTML,
        CSS,
    }

    fun groupsOf(key: String?): Set<Group> {
        return when (key ?: return emptySet()) {
            in jsKeys -> setOf(Group.LEGADO, Group.JS)
            in jsJsonKeys -> setOf(Group.LEGADO, Group.JS, Group.JSON)
            in jsonKeys -> setOf(Group.JSON)
            in htmlKeys -> setOf(Group.HTML)
            in cssKeys -> setOf(Group.CSS)
            in ruleKeys -> setOf(Group.LEGADO)
            else -> emptySet()
        }
    }

    fun applyTo(codeView: CodeView, key: String) {
        codeView.setMaxHighlightLength(EDITOR_MAX_HIGHLIGHT_LENGTH)
        codeView.resetSyntaxPatternList()
        groupsOf(key).forEach { group ->
            when (group) {
                Group.LEGADO -> codeView.addLegadoPattern()
                Group.JS -> codeView.addJsPattern()
                Group.JSON -> codeView.addJsonPattern()
                Group.HTML -> codeView.addHtmlPattern()
                Group.CSS -> codeView.addCssPattern()
            }
        }
    }

    fun languageNameOf(key: String?): String? {
        return when (key ?: return null) {
            in jsKeys -> JS_LANGUAGE
            in htmlKeys -> HTML_LANGUAGE
            else -> null
        }
    }

}
