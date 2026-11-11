# 漫画卷轴模式向后预下载

> 适用范围：`model/ReadManga.kt` 的页级预下载、`ui/book/manga/ReadMangaActivity.kt` 的触发时机与缓存目录口径。**有效**（代码已落地）。
> 相关配置：`AppConfig.mangaPreDownloadNum`（向后预下载页数）。

## 一、问题背景

漫画卷轴模式原来用 Glide 的 `RecyclerViewPreloader` 做预取。预取确实执行了，但**翻页时仍会重新下载**，表现为"预下载不生效"。

根因是两套缓存 key 完全不匹配：

| 路径 | 缓存位置 | key |
| --- | --- | --- |
| Glide 预取 | Glide 自己的磁盘缓存 | 图片 URL |
| 漫画页实际显示（`MangaVH.loadImageWithRetry`） | `BookHelp.getImage()` 的图片文件缓存 | URL 的 MD5 文件名 |

`MangaVH` 判断"是否已缓存"只看 `BookHelp.getImage(book, url)` 是否存在。Glide 缓存写在另一个地方，它完全看不见，因此无论预取多少页，翻到时都判定为未缓存并重新下载。

## 二、设计

### 2.1 直接写显示路径的缓存

预下载不再经过 Glide，改用与显示路径**完全相同**的写入函数：

```kotlin
if (!BookHelp.isImageExist(book, url)) {
    BookHelp.saveImage(bookSource, book, url)
}
```

`BookHelp.saveImage()` 是 suspend 函数，内部带 `isImageExist` 短路和每 URL 的 `Mutex`，与漫画页加载共用同一把锁，避免同一张图被并发写坏。

落盘位置：`externalFiles/book_cache/<bookFolder>/images/<MD5>.jpg`。

### 2.2 按当前位置取页，不只跟滚动方向

`preDownloadNextPages()` 从 `durChapterPos` 之后取 `mangaPreDownloadNum` 页：

```kotlin
for (page in curPages) {
    if (wanted.size >= count) break
    if (page is MangaPage && page.index > durChapterPos && page.mImageUrl.isNotBlank()) {
        wanted.add(page.mImageUrl)
    }
}
```

当前章页数不够时越过章尾取下一章，凑满设定页数（卷轴模式下会自然跨章）。

### 2.3 单 worker + 窗口替换

三个集合各司其职（都需 `synchronized(pagePreDownloadWanted)` 保护）：

| 字段 | 作用 |
| --- | --- |
| `pagePreDownloadWanted` | 当前预下载窗口，每次翻页整体替换 |
| `pagePreDownloadScheduled` | 已入队但未下完的地址，避免重复入队 |
| `pagePreDownloadFailed` | 明确失败的地址，不再重试 |

单 worker 协程从 `Channel<String>(UNLIMITED)` 取地址串行处理：

```kotlin
while (true) {
    val url = pagePreDownloadChannel.receive()
    val obsolete = synchronized(pagePreDownloadWanted) { url !in pagePreDownloadWanted }
    if (obsolete) {
        synchronized(pagePreDownloadWanted) { pagePreDownloadScheduled.remove(url) }
        continue
    }
    // ...下载
}
```

**窗口替换时不清 `scheduled`**：已排队的地址仍然有效，清掉会导致重复入队。

### 2.4 并发限制

下载走 `preDownloadSemaphore`（`ReadConstants.PRE_DOWNLOAD_CONCURRENCY`）限流，与整章下载共用 `downloadScope`。

### 2.5 生命周期

| 时机 | 动作 |
| --- | --- |
| 翻页（`curPageChanged`） | `preDownloadNextPages()` 重开窗口 |
| 调整预下载页数设置 | 立即重开窗口，无需退出重进 |
| `onResume` | 按当前位置重新开窗口（`onPause` 取消过） |
| `onDestroy` | `cancelPagePreDownload()` |

`cancelPagePreDownload()` 会取消 Job、清空三个集合，并**丢弃 channel 中尚未取走的残留任务**——否则下次进入阅读页会先跑旧书的地址。

## 三、配置项

| 项 | 位置 | 含义 |
| --- | --- | --- |
| `AppConfig.mangaPreDownloadNum` | `help/config/AppConfig.kt` | 向后预下载页数，0 或负数表示关闭 |

设置为 0 时 `preDownloadNextPages()` 直接 return，不做任何事。

## 四、验证

### 4.1 缓存目录核对

真机 / 模拟器上进入漫画阅读页后检查：

```bash
adb shell "ls -lat '/sdcard/Android/data/io.legado.app.yuedu.release/files/book_cache/<书名目录>/images' | head -12"
```

正常表现：一次运行后新增多条同一时刻写入的 `*.jpg`，数量等于 `mangaPreDownloadNum`。

### 4.2 常见误判

- 用旧版 APK 测试会测不出效果——确认安装的包包含本次改动
- `updateLog` 只是应用内文案，不影响功能
- 缓存命中要看 `images/` 目录，不要看 Glide 的缓存目录

## 五、涉及文件

| 文件 | 职责 |
| --- | --- |
| `model/ReadManga.kt` | `preDownloadNextPages()` / `startPagePreDownload()` / `cancelPagePreDownload()` |
| `ui/book/manga/ReadMangaActivity.kt` | 触发时机（翻页 / onResume / onDestroy）、设置变更后刷新 |
| `help/book/BookHelp.kt` | `getImage()` / `saveImage()` / `isImageExist()`，与显示路径共用 |

`ReadMangaActivity.setRecyclerViewPreloader()` 现在只是无参包装，内部调 `ReadManga.preDownloadNextPages()`，Glide 的 `RecyclerViewPreloader`、`mSizeProvider`、`mRecyclerViewPreloader` 已全部移除。
