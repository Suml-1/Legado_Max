package io.legado.app.model.jsSource

/**
 * 「新建 JS 书源」编辑器里的初始模板。
 *
 * 只放最小可运行骨架：`config` 必填字段 + 五个入口函数签名，
 * 让用户直接在一个能通过导入校验的脚本上改，而不是从空白文件猜格式。
 */
object JsSourceTemplate {

    val NEW_SOURCE = """
        // 单文件 JS 书源：config 提供书源信息，函数提供抓取逻辑
        // 内置 CryptoJS（MD5/SHA/AES 等），可直接使用 java.ajax 发起请求
        var config = {
            bookSourceUrl: "https://example.com/js",
            bookSourceName: "新的JS书源",
            bookSourceGroup: "JS",
            bookSourceType: 0,
            enabled: true,
            enabledExplore: false
        };

        function search(key, page) {
            var body = java.ajax("https://example.com/search?q=" + encodeURIComponent(key));
            // 解析 body 后返回书籍数组：name/author/bookUrl/kind/intro
            return [];
        }

        function getBookInfo(book) {
            return {};
        }

        function getChapters(book) {
            // 返回章节数组：title/url
            return [];
        }

        function getContent(chapter, book, nextChapterUrl) {
            return java.ajax(chapter.url);
        }
    """.trimIndent()
}
