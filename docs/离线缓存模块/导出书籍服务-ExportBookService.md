# 导出书籍服务（ExportBookService）详解

主要内容 ：

- 导出流程（Txt/Epub/自定义分卷）
- ExportConfig 导出配置结构
- exportProgress / exportMsg 进度共享机制
- Epub 文件结构详解
- 模板系统（内置/自定义）
- 通知栏更新
- 与 CacheActivity 的交互
- 错误处理与性能优化


## 概述

`ExportBookService` 是一个**前台 Service**，负责将缓存的书籍导出为 `txt` 或 `epub` 格式。支持：
- 单本导出 / 批量导出
- 自定义导出（epub 分卷）
- 导出到本地存储或 WebDAV
- 实时进度通知

---

## 核心数据结构

### 1. 导出配置

```kotlin
data class ExportConfig(
    val path: String,       // 导出路径
    val type: String,       // 导出类型：txt 或 epub
    val epubSize: Int = 1, // epub 分卷大小（每卷章节数）
    val epubScope: String? = null  // epub 自定义范围，如 "1-5,8,10-18"
)
```

### 2. 进度和消息共享

```kotlin
companion object {
    // bookUrl -> 导出进度（已导出章节数）
    val exportProgress = ConcurrentHashMap<String, Int>()

    // bookUrl -> 导出状态消息（"等待中"、"导出成功"、"错误信息"）
    val exportMsg = ConcurrentHashMap<String, String>()
}
```

这两个静态变量被 `CacheActivity` 和 `CacheAdapter` 读取，用于在界面上显示进度。

### 3. 待导出队列

```kotlin
private val waitExportBooks = linkedMapOf<String, ExportConfig>()
```

采用 `linkedMapOf` 保证插入顺序，支持队列式的批量导出。

---

## 导出流程

### 整体流程图

```
┌─────────────────────────────────────────────────────────────────┐
│                      CacheActivity                              │
│  用户点击导出按钮                                                │
│  调用 export(position)                                          │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ExportBookService                             │
│  onStartCommand(IntentAction.start)                              │
│  创建 ExportConfig，加入 waitExportBooks 队列                    │
│  调用 export() 启动导出协程                                      │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                       export() 协程                              │
│  while (isActive) {                                              │
│      取出 waitExportBooks 队首                                    │
│      判断类型: type == "epub" → exportEpub()                    │
│               type == "txt" → exportTxt()                        │
│      发送 EventBus.EXPORT_BOOK 事件                               │
│  }                                                               │
└─────────────────────────────────────────────────────────────────┘
```

### 1. Txt 导出

```kotlin
private suspend fun exportTxt(fileDoc: FileDoc, book: Book) {
    val filename = book.getExportFileName("txt")
    fileDoc.find(filename)?.delete()  // 删除旧文件

    val bookDoc = fileDoc.createFileIfNotExist(filename)
    val charset = Charset.forName(AppConfig.exportCharset)

    // 图片按「书名_作者/images/章节序号_章节名」分目录存放；章节目录名由 toImageChapterDir 生成，
    // 序号取 chapterIndex（全书章节序号），同一章的图片落在同一子文件夹
    val imageBookDir = "${book.name}_${book.author}".toSafeFileName().ifBlank { "book" }
    bookDoc.openOutputStream().getOrThrow().bufferedWriter(charset).use { bw ->
        getAllContents(book) { text, srcList ->
            bw.write(text)
            // 导出图片（可选）
            srcList?.forEach {
                val vFile = BookHelp.getImage(book, it.src)
                if (vFile.exists()) {
                    fileDoc.createFileIfNotExist(
                        "${it.imageIndex}-${MD5Utils.md5Encode16(it.src)}.jpg",
                        subDirs = arrayOf(imageBookDir, "images", it.chapterTitle.toImageChapterDir(it.chapterIndex))
                    ).writeFile(vFile)
                }
            }
        }
    }

    // 可选：同步到 WebDAV
    if (AppConfig.exportToWebDav) {
        AppWebDav.exportWebDav(bookDoc.uri, filename)
    }
}
```

### 2. Epub 导出

```kotlin
private suspend fun exportEpub(fileDoc: FileDoc, book: Book) {
    val filename = book.getExportFileName("epub")
    fileDoc.find(filename)?.delete()

    val epubBook = EpubBook()
    epubBook.version = "2.0"

    // 设置元数据（书名、作者、简介、语言、出版者等）
    setEpubMetadata(book, epubBook)

    // 设置封面
    setCover(book, epubBook)

    // 设置 CSS 和模板
    val contentModel = setAssets(fileDoc, book, epubBook)

    // 设置正文内容
    setEpubContent(contentModel, book, epubBook)

    // 写入文件
    val bookDoc = fileDoc.createFileIfNotExist(filename)
    bookDoc.openOutputStream().buffered().use { bookOs ->
        EpubWriter().write(epubBook, bookOs)
    }

    // 可选：同步到 WebDAV
    if (AppConfig.exportToWebDav) {
        AppWebDav.exportWebDav(bookDoc.uri, filename)
    }
}
```

**Epub 文件结构**：

```
book_name.epub
├── META-INF/
│   └── container.xml
├── OEBPS/
│   ├── Text/
│   │   ├── cover.html       # 封面页
│   │   ├── intro.html       # 简介页
│   │   └── chapter_0.html   # 章节1
│   │   └── chapter_1.html   # 章节2
│   ├── Images/
│   │   ├── cover.jpg        # 封面图片
│   │   └── xxx.jpg          # 正文图片
│   └── Styles/
│       ├── main.css         # 主样式
│       └── fonts.css        # 字体样式
└── mimetype
```

### 3. 自定义导出（分卷 Epub）

当用户选择"自定义导出"时，会进入 `CustomExporter` 类：

```kotlin
inner class CustomExporter(scopeStr: String, private val size: Int) {

    suspend fun export(path: String, book: Book) {
        // 解析范围字符串，如 "1-5,8,10-18"
        scope = parseScope(scopeStr)

        // 创建多个 epub 对象
        val (contentModel, epubList) = createEpubs(book, fileDoc)

        epubList.forEachIndexed { index, ep ->
            val (filename, epubBook) = ep
            // 设置正文
            setEpubContent(contentModel, book, epubBook, index) { _, _ ->
                // 更新进度
                exportProgress[book.bookUrl] = progressBar.toInt()
            }
            // 保存到磁盘
            save2Drive(filename, epubBook, fileDoc) { total, _ ->
                exportProgress[book.bookUrl] = progressBar.toInt()
            }
        }
    }
}
```

**范围解析逻辑**：

```kotlin
private fun parseScope(scope: String): Set<Int> {
    // 输入: "1-5,8,10-18"
    // 输出: {0, 1, 2, 3, 4, 7, 9, 10, ..., 17}（0-based 索引）

    val split = scope.split(",")
    val result = linkedSetOf<Int>()

    for (s in split) {
        val v = s.split("-")
        if (v.size != 2) {
            // 单个数字，如 "8"
            result.add(s.toInt() - 1)
            continue
        }
        // 范围，如 "1-5"
        val left = v[0].toInt()
        val right = v[1].toInt()
        for (i in left..right) {
            result.add(i - 1)
        }
    }
    return result
}
```

---

## 核心方法解析

### getAllContents — 获取所有章节内容

```kotlin
private suspend fun getAllContents(
    book: Book,
    append: (text: String, srcList: ArrayList<SrcData>?) -> Unit
) = coroutineScope {
    // 是否启用替换规则
    val useReplace = AppConfig.exportUseReplace && book.getUseReplaceRule()
    val contentProcessor = ContentProcessor.get(book.name, book.origin)

    // 书名、作者、简介头部
    val qy = "${book.name}\n${getString(R.string.author_show, book.getRealAuthor())}\n..."
    append(qy, null)

    // 并行线程数
    val threads = if (AppConfig.parallelExportBook) {
        AppConst.MAX_THREAD
    } else {
        1
    }

    // Flow 并行处理章节
    flow {
        appDb.bookChapterDao.getChapterList(book.bookUrl).forEach { chapter ->
            emit(chapter)
        }
    }.mapAsync(threads) { chapter ->
        getExportData(book, chapter, contentProcessor, useReplace)
    }.collectIndexed { index, result ->
        exportProgress[book.bookUrl] = index
        append.invoke(result.first, result.second)
    }
}
```

**关键点**：
- 使用 `flow` + `mapAsync` 实现并发获取章节内容
- `collectIndexed` 实时更新进度
- `contentProcessor` 应用替换规则格式化内容

### getExportData — 处理单个章节数据

```kotlin
private fun getExportData(
    book: Book,
    chapter: BookChapter,
    contentProcessor: ContentProcessor,
    useReplace: Boolean
): Pair<String, ArrayList<SrcData>?> {
    // 获取缓存的正文
    val content = BookHelp.getContent(book, chapter)

    // 应用替换规则、去重标题等处理
    val content1 = contentProcessor.getContent(
        book,
        chapter.apply { isVip = false },  // 不导出 VIP 标识
        content ?: if (chapter.isVolume) "" else "null",
        includeTitle = !AppConfig.exportNoChapterName,
        useReplace = useReplace,
        chineseConvert = false,
        reSegment = false
    ).toString()

    // 如果启用导出图片，提取图片 URL
    if (AppConfig.exportPictureFile) {
        val srcList = arrayListOf<SrcData>()
        content?.split("\n")?.forEachIndexed { index, text ->
            val matcher = AppPattern.imgPattern.matcher(text)
            while (matcher.find()) {
                matcher.group(1)?.let {
                    val src = NetworkUtils.getAbsoluteURL(chapter.url, it)
                    // imageIndex 为图片所在行号，仅用于同章内文件命名；目录归属用 chapterIndex
                    srcList.add(SrcData(chapter.title, chapter.index, index, src))
                }
            }
        }
        return Pair("\n\n$content1", srcList)
    }

    return Pair("\n\n$content1", null)
}
```

### fixPic — 修复 Epub 中的图片路径

```kotlin
private fun fixPic(
    book: Book,
    content: String,
    chapter: BookChapter
): Pair<String, ArrayList<Resource>> {
    val data = StringBuilder("")
    val resources = arrayListOf<Resource>()

    content.split("\n").forEach { text ->
        var text1 = text
        val matcher = AppPattern.imgPattern.matcher(text)

        while (matcher.find()) {
            matcher.group(1)?.let {
                val src = NetworkUtils.getAbsoluteURL(chapter.url, it)
                val href = "Images/${MD5Utils.md5Encode16(src)}.${BookHelp.getImageSuffix(src)}"

                // 获取本地缓存的图片文件
                val vFile = BookHelp.getImage(book, src)
                val fp = FileResourceProvider(vFile.parent)

                if (vFile.exists()) {
                    val img = LazyResource(fp, href, src)  // 保持原始 URL 作为 originalHref
                    resources.add(img)
                }

                // 将原文中的图片 URL 替换为相对路径
                text1 = text1.replace(src, "../${href}")
            }
        }
        data.append(text1).append("\n")
    }

    return data.toString() to resources
}
```

---

## 通知栏更新

```kotlin
private fun upExportNotification(finish: Boolean = false) {
    val notification = NotificationCompat.Builder(this, AppConst.channelIdDownload)
        .setSmallIcon(R.drawable.ic_export)
        .setSubText(getString(R.string.export_book))
        .setContentIntent(activityPendingIntent<CacheActivity>("cacheActivity"))
        .setContentText(notificationContentText)
        .setDeleteIntent(servicePendingIntent<ExportBookService>(IntentAction.stop))
        .setGroup(groupKey)
        .setOnlyAlertOnce(true)

    if (!finish) {
        notification.setOngoing(true)
        notification.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.cancel),
            servicePendingIntent<ExportBookService>(IntentAction.stop)
        )
    }

    notificationManager.notify(NotificationId.ExportBook, notification.build())
}
```

**通知栏效果**：

```
┌─────────────────────────────────────────┐
│ 📤 导出书籍                    [取消]   │
├─────────────────────────────────────────┤
│ 《书名》正在导出... 剩余 3 本           │
└─────────────────────────────────────────┘
```

---

## 与 CacheActivity 的交互

### 1. Activity 调用 Service

```kotlin
// CacheActivity.kt
private fun startExport(path: String, exportPosition: Int) {
    val exportType = when (AppConfig.exportType) {
        1 -> "epub"
        else -> "txt"
    }

    if (exportPosition == -10) {
        // 批量导出
        adapter.getItems().forEach { book ->
            startService<ExportBookService> {
                action = IntentAction.start
                putExtra("bookUrl", book.bookUrl)
                putExtra("exportType", exportType)
                putExtra("exportPath", path)
            }
        }
    } else if (exportPosition >= 0) {
        // 单本导出
        adapter.getItem(exportPosition)?.let { book ->
            startService<ExportBookService> {
                action = IntentAction.start
                putExtra("bookUrl", book.bookUrl)
                putExtra("exportType", exportType)
                putExtra("exportPath", path)
            }
        }
    }
}
```

### 2. Service 回调 Activity

```kotlin
// ExportBookService.kt
postEvent(EventBus.EXPORT_BOOK, bookUrl)

// CacheActivity.kt
observeEvent<String>(EventBus.EXPORT_BOOK) {
    notifyItemChanged(it)  // 刷新对应书籍的导出状态
}
```

### 3. Adapter 读取进度

```kotlin
// CacheAdapter.kt
override fun exportProgress(bookUrl: String): Int? {
    return ExportBookService.exportProgress[bookUrl]
}

override fun exportMsg(bookUrl: String): String? {
    return ExportBookService.exportMsg[bookUrl]
}
```

---

## 配置选项

| 配置项 | 说明 |
|--------|------|
| `exportUseReplace` | 导出时启用正文替换规则 |
| `exportNoChapterName` | 导出时不包含章节标题 |
| `exportPictureFile` | Txt 导出时包含图片文件 |
| `exportToWebDav` | 导出后同步到 WebDAV |
| `parallelExportBook` | 并行导出章节 |
| `exportCharset` | Txt 导出编码（默认 UTF-8） |
| `exportType` | 默认导出类型（0=txt, 1=epub） |
| `enableCustomExport` | 启用自定义导出（分卷） |
| `episodeExportFileName` | Epub 分卷文件名模板 |

---

## Epub 模板系统

### 内置模板

`setAssets()` 方法使用 `assets/epub/` 目录下的内置模板：

```
assets/epub/
├── cover.html    # 封面模板
├── intro.html    # 简介模板
├── chapter.html  # 章节模板
├── main.css      # 主样式
├── logo.png      # Logo 图片
└── fonts.css     # 字体样式
```

### 自定义模板

`setAssetsExternal()` 方法支持用户自定义模板：

```
导出目录/
└── Asset/
    ├── Text/
    │   ├── cover.html    # 自定义封面
    │   ├── intro.html    # 自定义简介
    │   └── chapter.html  # 自定义章节模板
    ├── Styles/
    │   ├── main.css
    │   └── fonts.css
    └── Images/
        └── logo.png
```

---

## 错误处理

```kotlin
try {
    book ?: throw NoStackTraceException("获取${bookUrl}书籍出错")
    refreshChapterList(book)

    if (exportConfig.type == "epub") {
        if (exportConfig.epubScope.isNullOrBlank()) {
            exportEpub(exportConfig.path, book)
        } else {
            CustomExporter(exportConfig.epubScope, exportConfig.epubSize)
                .export(exportConfig.path, book)
        }
    } else {
        exportTxt(exportConfig.path, book)
    }

    exportMsg[book.bookUrl] = getString(R.string.export_success)

} catch (e: Throwable) {
    exportMsg[bookUrl] = e.localizedMessage ?: "ERROR"
    AppLog.put("导出书籍<${book?.name ?: bookUrl}>出错", e)
} finally {
    exportProgress.remove(bookUrl)
    postEvent(EventBus.EXPORT_BOOK, bookUrl)
}
```

---

## 性能优化

### 1. 并行导出

```kotlin
val threads = if (AppConfig.parallelExportBook) {
    AppConst.MAX_THREAD
} else {
    1
}

flow { ... }
    .mapAsync(threads) { chapter ->
        getExportData(book, chapter, contentProcessor, useReplace)
    }
    .collectIndexed { index, result ->
        exportProgress[book.bookUrl] = index
        append.invoke(result.first, result.second)
    }
```

### 2. LazyResource 延迟加载

```kotlin
val provider = LazyResourceProvider { _ ->
    file.inputStream()
}
epubBook.coverImage = LazyResource(provider, "Images/cover.jpg")
```

图片内容在写入 Epub 文件时才读取，节省内存。

### 3. 分卷导出进度计算

```kotlin
progressBar += book.totalChapterNum.toDouble() / scope.size / 2  // 设置正文时
progressBar += book.totalChapterNum.toDouble() / epubList.size / total / 2  // 写入文件时
```

---

## 常见问题

**Q1: 导出失败怎么办？**
A: 查看通知栏错误消息，或点击菜单"日志"查看详细错误信息。

**Q2: Epub 封面是空白？**
A: 确保书籍有有效的封面 URL，或检查 `setCover()` 中的 Glide 加载是否成功。

**Q3: 导出到 WebDAV 失败？**
A: 检查 WebDAV 配置是否正确，网络是否可达。

**Q4: 自定义模板不生效？**
A: 确保 `Asset` 文件夹放在导出目录下，且结构符合要求。
