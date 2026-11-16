@file:Suppress("unused")

package io.legado.app.ui.widget.code

import android.content.Context
import android.widget.ArrayAdapter
import io.legado.app.R
import splitties.init.appCtx
import splitties.resources.color
import java.util.regex.Pattern

/**
 * 阅读规则语法模式
 * 匹配 ||、&&、%%、@js:、@Json:、@css:、@@、@XPath:、@webjs: 等阅读规则关键字
 */
val legadoPattern: Pattern = Pattern.compile("\\|\\||&&|%%|@js:|@Json:|@css:|@@|@XPath:|@webjs:")

/**
 * JSON语法模式
 * 匹配JSON中的键名、引号、花括号、方括号
 */
val jsonPattern: Pattern = Pattern.compile("\"[A-Za-z0-9]*?\"\\:|\"|\\{|\\}|\\[|\\]")

/**
 * 换行符模式
 * 匹配转义的换行符 \n
 */
val wrapPattern: Pattern = Pattern.compile("\\\\n")

// NG 移植：CSS 语法模式
val cssPropertyPattern: Pattern = Pattern.compile("\\b[-A-Za-z]+(?=\\s*:)")
val cssStringPattern: Pattern = Pattern.compile("'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"")
val cssNumberPattern: Pattern = Pattern.compile("(?<![\\w.])-?\\d+(?:\\.\\d+)?(?:px|em|rem|vh|vw|%|s|ms)?(?![\\w.])")

// NG 移植：JS 语法模式
val jsCommentPattern: Pattern = Pattern.compile("//.*$|/\\*[\\s\\S]*?\\*/", Pattern.MULTILINE)
val jsStringPattern: Pattern = Pattern.compile("'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"|`(?:\\\\.|[^`\\\\])*`")
val jsNumberPattern: Pattern = Pattern.compile("(?<![\\w.])-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?(?![\\w.])")
val jsKeywordPattern: Pattern = Pattern.compile(
    "\\b(?:async|await|break|case|catch|class|const|continue|default|delete|do|else|export|extends|finally|for|function|if|import|in|instanceof|let|new|return|switch|this|throw|try|typeof|var|void|while|yield)\\b"
)
val jsLiteralPattern: Pattern = Pattern.compile("\\b(?:false|null|true|undefined|NaN|Infinity)\\b")

// NG 移植：调试日志模式
val debugSectionPattern: Pattern = Pattern.compile("^// ===== .+ =====$", Pattern.MULTILINE)
val debugTimestampPattern: Pattern = Pattern.compile("\\[\\d{2}:\\d{2}\\.\\d{3}]")
val debugUrlPattern: Pattern = Pattern.compile("https?://[^\\s\"'<>]+|(?<=获取成功:)/[^\\s\"'<>]+")
val debugHeaderPattern: Pattern = Pattern.compile("^[A-Za-z0-9_-]+(?=:)", Pattern.MULTILINE)

// NG 移植：代码 / JSON / HTML 细分模式
val codeStringPattern: Pattern = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"")
val jsonKeyPattern: Pattern = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"(?=\\s*:)")
val jsonLiteralPattern: Pattern = Pattern.compile("\\b(?:true|false|null)\\b")
val jsonNumberPattern: Pattern = Pattern.compile("(?<![\\w.])-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?(?![\\w.])")
val htmlStartTagNamePattern: Pattern = Pattern.compile("(?<=<)[A-Za-z][\\w:-]*")
val htmlEndTagNamePattern: Pattern = Pattern.compile("(?<=</)[A-Za-z][\\w:-]*")
val htmlAttributePattern: Pattern = Pattern.compile("\\b[A-Za-z_:][\\w:.-]*(?=\\s*=\\s*\")")

/**
 * 运算符模式
 * 匹配各种运算符符号
 */
val operationPattern: Pattern =
    Pattern.compile(":|==|>|<|!=|>=|<=|->|=|%|-|-=|%=|\\+|\\-|\\-=|\\+=|\\^|\\&|\\|::|\\?|\\*")

/**
 * JavaScript关键字模式
 * 匹配 var、let、const 关键字
 */
val jsPattern: Pattern = Pattern.compile("\\b(?:var|let|const)\\b")

/**
 * HTML标签模式
 * 匹配HTML标签，如 <div>、</div>、<img src="..."/>
 */
val htmlTagPattern: Pattern = Pattern.compile("</?[a-zA-Z][a-zA-Z0-9]*[^>]*>")

/**
 * HTML属性模式
 * 匹配HTML属性名，如 class=、id=、style=
 */
val htmlAttrPattern: Pattern = Pattern.compile("\\s[a-zA-Z-]+=")

/**
 * HTML注释模式
 * 匹配HTML注释 <!-- ... -->
 */
val htmlCommentPattern: Pattern = Pattern.compile("<!--.*?-->")

/**
 * 模板变量模式
 * 匹配模板变量 {{variableName}}，用于封面HTML模板中的变量替换
 */
val htmlVariablePattern: Pattern = Pattern.compile("\\{\\{[a-zA-Z]+\\}\\}")

/**
 * 为CodeView添加阅读规则语法高亮
 */
fun CodeView.addLegadoPattern() {
    addSyntaxPattern(legadoPattern, appCtx.color(R.color.md_orange_900))
}

/**
 * 为CodeView添加JSON语法高亮
 */
fun CodeView.addJsonPattern() {
    addSyntaxPattern(jsonPattern, appCtx.color(R.color.md_blue_800))
}

/**
 * 为CodeView添加JavaScript语法高亮
 */
fun CodeView.addJsPattern() {
    addSyntaxPattern(wrapPattern, appCtx.color(R.color.md_blue_grey_500))
    addSyntaxPattern(operationPattern, appCtx.color(R.color.md_orange_900))
    addSyntaxPattern(jsPattern, appCtx.color(R.color.md_light_blue_600))
}

// NG 移植：为CodeView添加CSS语法高亮
fun CodeView.addCssPattern() {
    addSyntaxPattern(cssPropertyPattern, appCtx.color(R.color.md_blue_800))
    addSyntaxPattern(cssStringPattern, appCtx.color(R.color.md_green_800))
    addSyntaxPattern(cssNumberPattern, appCtx.color(R.color.md_purple_700))
}

/**
 * 为CodeView添加HTML语法高亮
 * 
 * 包含以下高亮规则：
 * - HTML标签：红色
 * - HTML属性：橙色
 * - HTML注释：灰色
 * - 模板变量（如{{bookName}}）：绿色
 */
fun CodeView.addHtmlPattern() {
    addSyntaxPattern(htmlTagPattern, appCtx.color(R.color.md_red_700))
    addSyntaxPattern(htmlAttrPattern, appCtx.color(R.color.md_orange_800))
    addSyntaxPattern(htmlCommentPattern, appCtx.color(R.color.md_grey_500))
    addSyntaxPattern(htmlVariablePattern, appCtx.color(R.color.md_green_700))
}

/**
 * 创建关键词自动补全适配器
 * 
 * @param keywords 关键词数组
 * @return ArrayAdapter 适配器
 */
fun Context.arrayAdapter(keywords: Array<String>): ArrayAdapter<String> {
    return ArrayAdapter(this, R.layout.item_1line_text_and_del, R.id.text_view, keywords)
}
