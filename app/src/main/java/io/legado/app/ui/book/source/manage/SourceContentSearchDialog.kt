package io.legado.app.ui.book.source.manage

import androidx.fragment.app.viewModels
import com.google.gson.JsonObject
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.edit.BookSourceEditLauncher
import io.legado.app.ui.source.BaseContentSearchDialog
import io.legado.app.ui.source.ContentSearchEngine
import io.legado.app.ui.source.ContentSearchType
import io.legado.app.ui.source.JsonSearchItem
import io.legado.app.ui.source.SourceFieldItem
import io.legado.app.utils.GSON
import io.legado.app.utils.share
import io.legado.app.utils.startActivity

/**
 * 书源内容查询界面，用于按规则字段或完整 JSON 搜索书源配置。
 */
class SourceContentSearchDialog : BaseContentSearchDialog() {

    private val viewModel by viewModels<SourceContentSearchViewModel>()

    // 缓存的原始源数据
    private var allSources: List<Triple<String, String, JsonObject>> = emptyList()

    companion object {
        private val TAB_NAMES = mapOf(
            "base" to "基本",
            "search" to "搜索",
            "explore" to "发现",
            "info" to "详情",
            "toc" to "目录",
            "content" to "正文"
        )

        private val TAB_FIELDS = mapOf(
            "base" to listOf(
                "bookSourceUrl" to "源地址",
                "bookSourceName" to "源名称",
                "bookSourceGroup" to "源分组",
                "bookSourceComment" to "源注释",
                "loginUrl" to "登录地址",
                "loginUi" to "登录界面",
                "loginCheckJs" to "登录检查JS",
                "coverDecodeJs" to "封面解密JS",
                "bookUrlPattern" to "书籍URL正则",
                "header" to "请求头",
                "variableComment" to "变量说明",
                "concurrentRate" to "并发率",
                "jsLib" to "jsLib"
            ),
            "search" to listOf(
                "searchUrl" to "搜索地址",
                "checkKeyWord" to "校验关键字",
                "bookList" to "书籍列表",
                "name" to "书名",
                "author" to "作者",
                "kind" to "分类",
                "wordCount" to "字数",
                "lastChapter" to "最新章节",
                "intro" to "简介规则",
                "coverUrl" to "封面规则",
                "bookUrl" to "书籍URL"
            ),
            "explore" to listOf(
                "exploreUrl" to "发现地址",
                "bookList" to "书籍列表",
                "name" to "书名",
                "author" to "作者",
                "kind" to "分类",
                "wordCount" to "字数",
                "lastChapter" to "最新章节",
                "intro" to "简介规则",
                "coverUrl" to "封面规则",
                "bookUrl" to "书籍URL"
            ),
            "info" to listOf(
                "init" to "初始化",
                "name" to "书名",
                "author" to "作者",
                "kind" to "分类",
                "wordCount" to "字数",
                "lastChapter" to "最新章节",
                "intro" to "简介规则",
                "coverUrl" to "封面规则",
                "tocUrl" to "目录URL",
                "canReName" to "允许修改书名作者",
                "downloadUrls" to "下载地址"
            ),
            "toc" to listOf(
                "preUpdateJs" to "更新之前JS",
                "chapterList" to "目录列表规则",
                "chapterName" to "章节名称",
                "chapterUrl" to "章节URL",
                "formatJs" to "格式化规则",
                "isVolume" to "Volume标识",
                "updateTime" to "更新时间",
                "isVip" to "是否VIP",
                "isPay" to "购买标识",
                "nextTocUrl" to "目录下一页规则"
            ),
            "content" to listOf(
                "content" to "正文内容",
                "nextContentUrl" to "正文下一页URL规则",
                "subContent" to "字内容规则",
                "replaceRegex" to "替换正则",
                "ChapterName" to "章节名称规则",
                "sourceRegex" to "资源正则",
                "imageStyle" to "图片样式",
                "imageDecode" to "图片解密",
                "webJs" to "WebView JS",
                "payAction" to "购买操作",
                "callBackJs" to "回调操作"
            )
        )
    }

    override fun getDialogTitle() = "书源内容查询"

    override fun getSearchHint() = "输入关键词搜索选择的书源"

    override fun getContentSearchType() = ContentSearchType.BOOK_SOURCE

    override fun loadSourceItems(allSources: Boolean, callback: (List<SourceFieldItem>) -> Unit) {
        viewModel.loadSources(!allSources) { sourceList ->
            this.allSources = sourceList
            val items = mutableListOf<SourceFieldItem>()
            for ((sourceName, sourceUrl, jsonObj) in this.allSources) {
                val sourceGroup = getFieldValue(jsonObj, "base", "bookSourceGroup")
                for ((tabKey, fields) in TAB_FIELDS) {
                    for ((fieldKey, fieldName) in fields) {
                        val value = getFieldValue(jsonObj, tabKey, fieldKey) ?: continue
                        items.add(SourceFieldItem(
                            sourceName = sourceName,
                            sourceUrl = sourceUrl,
                            tabKey = tabKey,
                            tabName = TAB_NAMES[tabKey] ?: tabKey,
                            fieldKey = fieldKey,
                            fieldName = fieldName,
                            value = value,
                            sourceGroup = sourceGroup
                        ))
                    }
                }
            }
            callback(items)
        }
    }

    override suspend fun performSearch(query: String, allItems: List<SourceFieldItem>): List<SourceFieldItem> {
        return if (searchByRuleField) {
            ContentSearchEngine.searchFields(query, allItems)
        } else {
            ContentSearchEngine.searchJson(
                query = query,
                sourceItems = allItems,
                jsonItems = allSources.map { (sourceName, sourceUrl, jsonObj) ->
                    JsonSearchItem(sourceName, sourceUrl, jsonObj.toString())
                }
            )
        }
    }

    override fun navigateToEdit(sourceUrl: String, tabKey: String?, fieldKey: String?) {
        if (BookSourceEditLauncher.isJsSource(sourceUrl)) {
            // JS 源没有规则字段，tab/字段定位无意义，直接进代码编辑页
            startActivity(BookSourceEditLauncher.jsEditIntent(requireContext(), sourceUrl))
            return
        }
        startActivity<BookSourceEditActivity> {
            putExtra("sourceUrl", sourceUrl)
            if (!tabKey.isNullOrBlank()) {
                putExtra("tabKey", tabKey)
            }
            if (!fieldKey.isNullOrBlank()) {
                putExtra("fieldKey", fieldKey)
            }
        }
    }

    override fun getTabNames(): Map<String, String> = TAB_NAMES

    override fun exportSources(sourceUrls: List<String>) {
        viewModel.exportSources(sourceUrls) { file ->
            activity?.share(file)
        }
    }

    private fun getFieldValue(jsonObj: JsonObject, tabKey: String, fieldKey: String): String? {
        return when (tabKey) {
            "base" -> {
                if (!jsonObj.has(fieldKey)) return null
                val element = jsonObj.get(fieldKey)
                when {
                    element.isJsonNull -> null
                    element.isJsonPrimitive -> element.asString
                    else -> element.toString()
                }
            }
            "search" -> {
                if (fieldKey == "searchUrl") {
                    if (!jsonObj.has("searchUrl")) return null
                    val element = jsonObj.get("searchUrl")
                    when {
                        element.isJsonNull -> null
                        element.isJsonPrimitive -> element.asString
                        else -> element.toString()
                    }
                } else {
                    val rule = jsonObj.getAsJsonObject("ruleSearch")
                    if (rule == null || !rule.has(fieldKey)) return null
                    val element = rule.get(fieldKey)
                    when {
                        element.isJsonNull -> null
                        element.isJsonPrimitive -> element.asString
                        else -> element.toString()
                    }
                }
            }
            "explore" -> {
                if (fieldKey == "exploreUrl") {
                    if (!jsonObj.has("exploreUrl")) return null
                    val element = jsonObj.get("exploreUrl")
                    when {
                        element.isJsonNull -> null
                        element.isJsonPrimitive -> element.asString
                        else -> element.toString()
                    }
                } else {
                    val rule = jsonObj.getAsJsonObject("ruleExplore")
                    if (rule == null || !rule.has(fieldKey)) return null
                    val element = rule.get(fieldKey)
                    when {
                        element.isJsonNull -> null
                        element.isJsonPrimitive -> element.asString
                        else -> element.toString()
                    }
                }
            }
            "info" -> {
                val rule = jsonObj.getAsJsonObject("ruleBookInfo")
                if (rule == null || !rule.has(fieldKey)) return null
                val element = rule.get(fieldKey)
                when {
                    element.isJsonNull -> null
                    element.isJsonPrimitive -> element.asString
                    else -> element.toString()
                }
            }
            "toc" -> {
                val rule = jsonObj.getAsJsonObject("ruleToc")
                if (rule == null || !rule.has(fieldKey)) return null
                val element = rule.get(fieldKey)
                when {
                    element.isJsonNull -> null
                    element.isJsonPrimitive -> element.asString
                    else -> element.toString()
                }
            }
            "content" -> {
                val rule = jsonObj.getAsJsonObject("ruleContent")
                if (rule == null || !rule.has(fieldKey)) return null
                val element = rule.get(fieldKey)
                when {
                    element.isJsonNull -> null
                    element.isJsonPrimitive -> element.asString
                    else -> element.toString()
                }
            }
            else -> null
        }
    }
}
