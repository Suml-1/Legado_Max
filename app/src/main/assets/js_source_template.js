/**
 * 纯 JavaScript 单文件书源模板。
 *
 * 一个文件就是完整书源：`config` 提供书源信息，函数提供抓取逻辑。
 * 必需函数：search / getChapters / getContent；可选：explore（与 config.exploreUrl 成对）、
 * getBookInfo、登录（loginUi v2）。保存时会做校验：config 的 bookSourceUrl / bookSourceName
 * 必须非空、必备函数必须齐全；规则字段（ruleSearch/ruleToc 等）写进来也会被自动剥离，不用管。
 *
 * ── 可用的绑定 ──────────────────────────────────────────────
 * java     宿主能力，常用清单见下
 * source   本书源实体：put(key, value) / get(key)、setVariable(json) / getVariable()、
 *          getLoginInfo() / putLoginInfo(json) / removeLoginInfo()、getLoginInfoMap()、
 *          getLoginHeader() / putLoginHeader(json) / removeLoginHeader()、getKey()、getHeaderMap()
 * cookie   CookieStore：getCookie(url) / setCookie(url, cookie) / replaceCookie(url, cookie) /
 *          removeCookie(url) / cookieToMap(cookie) / mapToCookie(map)
 * cache    CacheManager：put(key, value, saveTime) / get(key) / getInt / getLong / … / delete(key)
 * baseUrl  本书源地址
 * CryptoJS 内置加密库（MD5 / SHA / AES / enc.Base64…），无需联网引入
 *
 * ── java.* 常用能力（完整清单见应用内帮助「js 变量和函数」）──────────
 * 网络（同步）：ajax(url[, timeoutMs])、ajaxAll([url, …])、connect(url, header?, timeout?)、
 *              get / post / head(url, headers, timeout?)、
 *              webView(html, url, js)（后台 WebView，须在非主线程调用）、webViewGetSource(…)
 * JS 源专属：  postBase64Body(url, bodyBase64, headersJson)（仅 https，用于原始二进制请求）、
 *              gzipUtf8ToBase64(text)、decompressBase64ToUtf8(base64)、getJsLibResource(name)、
 *              getQuickJsSandbox().evalString(script)（空作用域沙箱，见文末）
 * 工具：       htmlFormat(html)、base64Decode / base64Encode / hexDecodeToByteArray / hexEncodeToString、
 *              strToBytes / bytesToStr、encodeURI、timeFormat、t2s / s2t（简繁）、toNumChapter、toURL、
 *              readTxtFile(path)、importScript(path)、getZipStringContent(url, path) 等压缩包读取
 * 加解密：     md5Encode / md5Encode16、aes* / des* / tripleDES*、createSymmetricCrypto、
 *              createAsymmetricCrypto、createSign、digestHex、HMacHex / HMacBase64
 * 反馈：       toast(msg)、longToast(msg)、log(msg)（调试日志）、openVideoPlayer(url, title)、
 *              startBrowser(url, title) / startBrowserAwait(url, title)（会弹界面做验证）
 * ──────────────────────────────────────────────────────
 *
 * ── 引擎要点（踩过的坑都在这）───────────────────────────────
 * 1. Java 侧返回的字符串（Jsoup / okhttp / book / chapter 的属性）先 String(x) 再调
 *    .trim() / .match() / .startsWith() 等字符串方法。
 * 2. ES6+ 可用：可选链 ?.、空值合并 ??、模板字符串、Array.includes、BigInt、Promise；
 *    两个已知坑——const 允许重赋值、let 不实现暂时性死区
 *    （详见应用内帮助「Rhino JavaScript 引擎缺陷记录」）。
 * 3. 每次函数调用都在新作用域执行；跨请求状态放 cache.put / get 或 source.put / get、
 *    source.setVariable / getVariable。
 * 4. 返回字段名必须与实体字段完全一致，拼错的字段会被静默丢弃：
 *    章节小字（更新时间/字数）是 tag，不是 updateTime；音频/视频直链是 resourceUrl；
 *    本章字数是 wordCount。
 * 5. 章节 url 会被 getAbsoluteURL(book.tocUrl, url) 绝对化：存完整 http(s) 地址最稳，
 *    自造 scheme（如 chapter://xxx）会被拼接破坏。
 * 6. bookSourceType：0 文本 / 1 音频 / 2 图片 / 3 文件下载 / 4 视频。
 * 7. 网络请求默认带 config.header（JSON 请求头）；并发限制用 config.concurrentRate（如 "1/600"）。
 * ──────────────────────────────────────────────────────
 */

var config = {
    bookSourceUrl: "https://example.com", // 必填；主键，修改后视为新源
    bookSourceName: "新的JS书源", // 必填
    bookSourceType: 0,
    bookSourceGroup: "JS",
    bookSourceComment: "源说明写这里",
    // 请求头 JSON：{"User-Agent": "...", "Referer": "..."}
    header: "",
    // 并发限制："1/600" 表示每 600ms 最多 1 次请求
    concurrentRate: "",
    enabledCookieJar: false,
    // jsLib 共享库（可选）：普通 JS 文本，或 legado.js.library/1 结构化包。
    // 库内顶层函数/变量对主脚本可见（父作用域链）；库内函数默认拿不到 java/source，
    // 需要时按参考实现写法：const { java, source } = this;（以实际测试为准）。
    // 库内数据资源（不参与求值）用 java.getJsLibResource("名字") 读取。
    jsLib: "",
    // 发现分类：由 explore 抓取，与 explore 函数成对使用。数组每项 {title, url, style?}；
    // 不带 url 的项渲染为分区标题。也接受 "名称::url" 每行一个（或 && 分隔）的字符串。
    // style 是布局描述（不是 CSS）：
    //   layout_flexBasisPercent 控制一行几个（1 整行 / 0.5 两个 / 0.25 四个），
    //   layout_flexGrow = 1 拉伸填满，layout_wrapBefore = true 强制另起一行，
    //   layout_justifySelf = "center" 居中。分区标题建议整行，条目建议 0.25 / 0.5。
    exploreUrl: [
        // { title: "—— 分类 ——", style: { layout_flexBasisPercent: 1, layout_justifySelf: "center" } },
        // { title: "玄幻", url: "https://example.com/sort/1", style: { layout_flexGrow: 1, layout_flexBasisPercent: 0.25 } },
    ],
    lastUpdateTime: 0, // 版本时间戳（毫秒）；导入值较新时提示更新
};

var Jsoup = org.jsoup.Jsoup;

/**
 * 搜索书籍。
 * @param key 搜索关键词
 * @param page 页码，从 1 开始
 * @return 书籍数组或 JSON 字符串；name、bookUrl 必填（绝对地址），
 *         可选 author / kind / coverUrl / intro / wordCount / latestChapterTitle / tocUrl / type
 */
function search(key, page) {
    var url = config.bookSourceUrl + "/search?q=" + encodeURIComponent(key) + "&p=" + page;
    var html = java.ajax(url);
    var list = [];
    // JSON 接口直接 JSON.parse(html) 逐条 push；
    // HTML 用 Jsoup（Java 对象记得 String() 转换）：
    // var doc = Jsoup.parse(html);
    // var items = doc.select("div.book-item");
    // for (var i = 0; i < items.size(); i++) {
    //     var a = items.get(i).selectFirst("a");
    //     list.push({
    //         name: String(a.text()),
    //         bookUrl: String(a.attr("abs:href")),
    //         author: String(items.get(i).selectFirst(".author").text()),
    //     });
    // }
    return list;
}

/**
 * 发现页书籍；config.exploreUrl 非空时必须实现。
 * @param url exploreUrl 里的分类地址，原样传入
 * @param page 页码，从 1 开始
 * @return 结构同 search
 */
function explore(url, page) {
    var html = java.ajax(url);
    return [];
}

/**
 * 书籍详情（可选）：只写需要覆盖的字段，没写的字段保留搜索结果原值。
 * tocUrl 缺省用 bookUrl；variable 可返回 {..} 更新书籍变量。
 */
function getBookInfo(book) {
    var html = java.ajax(String(book.bookUrl));
    return {
        intro: "",
        coverUrl: "",
        kind: "",
        wordCount: "",
        latestChapterTitle: "",
        tocUrl: String(book.bookUrl),
    };
}

/**
 * 目录。title / url 必填，数组顺序即目录顺序。
 * 卷名行：{ title: "第一卷", url: "https://…/v1", isVolume: true }（url 随意，会与 title 一起被跳过）。
 * 章节小字（更新时间/字数）放 tag；本章字数放 wordCount；
 * 音频/视频源把媒体直链放 resourceUrl。
 */
function getChapters(book) {
    var html = java.ajax(String(book.tocUrl));
    var chapters = [];
    // chapters.push({ title: "第一卷", url: "https://example.com/v1", isVolume: true });
    // chapters.push({ title: "第1章", url: "https://example.com/read/1", tag: "2026-01-01", wordCount: "3120" });
    return chapters;
}

/**
 * 正文：返回文本，空字符串视为获取失败；段落用 \n 分隔，
 * 带排版的 HTML 可以用 java.htmlFormat(html) 转成阅读器文本并保留插图。
 * 正文内插图：在文本里嵌入 <img src="图片地址,{"style":"full"}">，
 * style 取 "full"（整宽）或 "text"（随行高）。
 * @param chapter 章节对象；@param book 书籍对象
 * @param nextChapterUrl 下一章地址，可能为 null（预加载用）
 */
function getContent(chapter, book, nextChapterUrl) {
    var html = java.ajax(String(chapter.url));
    // return java.htmlFormat(Jsoup.parse(html).select("div.content").html());
    return html;
}

/**
 * ── 登录界面 v2（可选）────────────────────────────────────
 * 在脚本里成对声明 loginUi(state) / loginAction(action, state, form) 即启用；
 * 不要再填 config.loginUi，也不需要 config.loginUrl。无 loginUrl 也能显示登录入口。
 * 行类型：text / password / select / toggle / button / label；输入行用 key 做表单键，
 * select / toggle 的候选项用 options（或 chars），按钮 countdown 给冷却秒数。
 * loginAction 返回值（可选字段）：{state:{…}} 回传后重新渲染、{error:{字段:提示}}、
 * {login:{…}} 保存为登录信息、{close:true} 关闭对话框。
 * 示例（保持注释状态即可，取消注释并保存就会启用）：
 *
 * function loginUi(state) {
 *     return { rows: [
 *         { name: "刷新设备", type: "button", action: "refreshDevice" },
 *         { name: (state.reviewEnabled ? "✓ " : "") + "开启段评", type: "button", action: "enableReview" },
 *     ] };
 * }
 * function loginAction(action, state, form) {
 *     if (action === "enableReview") {
 *         source.put("review", "on");
 *         return { state: { reviewEnabled: true } };
 *     }
 *     throw "未知动作";
 * }
 *
 * ── 空作用域沙箱（可选）────────────────────────────────────
 * java.getQuickJsSandbox().evalString(script)：全新空作用域执行纯计算脚本，
 * 看不到 java / source / cache，也不会污染主脚本；BigInt / 64 位位运算可用，
 * 适合放签名核心。带数据入口：evalStringWithData(script, data)，脚本里用形参 __data 接收。
 */
