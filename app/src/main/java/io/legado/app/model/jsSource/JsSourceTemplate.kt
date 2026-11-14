package io.legado.app.model.jsSource

import splitties.init.appCtx

/**
 * 「新建 JS 书源」编辑器里的初始模板。
 *
 * 模板正文维护在 `assets/js_source_template.js`（唯一来源，改动只动 asset），
 * 内容覆盖：绑定与 java.* 清单、引擎已知坑、config 常用字段、五个入口函数、
 * 登录界面 v2 与空作用域沙箱的说明。
 */
object JsSourceTemplate {

    val NEW_SOURCE: String by lazy {
        runCatching {
            appCtx.assets.open("js_source_template.js").use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrDefault(MINIMAL_FALLBACK)
    }

    /** 资源读取异常时的最小可运行骨架，保证「新建」流程不中断 */
    private const val MINIMAL_FALLBACK = """var config = {
    bookSourceUrl: "https://example.com/js",
    bookSourceName: "新的JS书源",
    bookSourceGroup: "JS",
    bookSourceType: 0,
};

function search(key, page) {
    return [];
}

function getBookInfo(book) {
    return {};
}

function getChapters(book) {
    return [];
}

function getContent(chapter, book, nextChapterUrl) {
    return java.ajax(chapter.url);
}"""
}
