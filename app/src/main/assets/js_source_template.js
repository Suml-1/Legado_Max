/**
 * JavaScript 单文件书源模板。
 * search、getChapters、getContent 为必需函数，getBookInfo 和 explore 为可选函数。
 * loginUi 与 loginAction 成对声明即可启用动态登录界面。
 * config 保存脚本配置；source 是运行时书源实体，sourceApi 是兼容旧脚本的别名。
 * 可使用 java、source、sourceApi、cookie、cache、baseUrl 与内置 CryptoJS。
 * Android 6.0 及以上可按需调用 java.getQuickJsSandbox().evalString(script)，
 * 在受限 QuickJS 隔离进程中执行纯字符串脚本；失败时不会回退到 Rhino。
 */

var config = {
    bookSourceUrl: "https://example.com",
    bookSourceName: "示例 JS 书源",
    bookSourceType: 0,
    bookSourceGroup: "",
    bookSourceComment: "",
    // 旧版表单登录示例: [{ name: "账号", type: "text" }, { name: "密码", type: "password" }]
    loginUi: [],
    // 发现分类支持 JSON 数组，或“名称::url”文本（换行或 && 分隔）。
    exploreUrl: [],
    lastUpdateTime: 0
};

var Jsoup = org.jsoup.Jsoup;

// config.loginUi 非空时必须提供。
function login() {
    var loginInfo = JSON.parse(source.getLoginInfo() || "{}");
    // 执行登录请求；失败时 throw "错误信息"。
}

/**
 * 动态登录界面（可选）由 loginUi(state) 与 loginAction(action, state, form) 成对启用，
 * 不要同时填写 config.loginUi。支持 text、password、label、select、toggle 和 button 行。
 */

function search(key, page) {
    var html = java.ajax(
        config.bookSourceUrl + "/search?q=" + encodeURIComponent(key) + "&p=" + page
    );
    var books = [];
    // books.push({ name: "书名", bookUrl: "https://example.com/book/1", author: "作者" });
    return books;
}

// config.exploreUrl 非空时必须提供。url 原样传入，翻页请使用 page。
function explore(url, page) {
    var html = java.ajax(url);
    return [];
}

// 可选。返回字段会合并到搜索结果，tocUrl 为空时默认使用 bookUrl。
function getBookInfo(book) {
    var html = java.ajax(book.bookUrl);
    return {
        intro: "",
        coverUrl: "",
        latestChapterTitle: "",
        tocUrl: book.bookUrl
    };
}

// title 和 url 为必填字段，数组顺序即目录顺序。
function getChapters(book) {
    var html = java.ajax(book.tocUrl);
    var chapters = [];
    // chapters.push({ title: "第 1 章", url: "https://example.com/read/1" });
    return chapters;
}

// 返回正文文本，空字符串视为失败。
function getContent(chapter, book, nextChapterUrl) {
    var html = java.ajax(chapter.url);
    return html;
}
