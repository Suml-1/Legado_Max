package io.legado.app.ui.main.bookshelf.compose

import android.content.Context
import androidx.compose.runtime.Immutable
import io.legado.app.data.dao.BookShelfDisplay
import io.legado.app.help.book.BookTagMatcher
import io.legado.app.help.book.SmartTag
import io.legado.app.help.book.toSmartTagSnapshot
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.toTimeAgo

/**
 * 书架条目底部的信息胶囊（标签 / 字数 / 分类）。
 *
 * @param text 文案
 * @param bookTag 是否为书籍标签：书籍标签带菱形前缀，用于与字数、分类区分
 */
@Immutable
data class BookshelfChip(
    val text: String,
    val bookTag: Boolean,
)

/**
 * 书架条目的 UI 模型。
 *
 * 只保留渲染需要的字段：书名/作者/章节来自 [display]，未读角标、更新中转圈、标签与简介
 * 都在构建时算好，避免在 Composable 里做计算；`isUpdating` 会随更新事件变化，所以单独建模。
 */
@Immutable
data class BookshelfBookItem(
    val display: BookShelfDisplay,
    val key: String,
    val isUpdating: Boolean,
    val unreadCount: Int,
    val hasNewChapter: Boolean,
    val lastUpdateText: String?,
    val readProgress: Float?,
    val chips: List<BookshelfChip>,
    val intro: String?,
)

/**
 * 构建书架条目列表。
 *
 * 只在该开关开启时才计算对应内容（标签要读智能标签规则、简介要处理富文本前缀），
 * 避免每个条目都做一遍无用功。
 */
fun buildBookshelfBookItems(
    context: Context,
    displays: List<BookShelfDisplay>,
    config: BookshelfDisplayConfig,
    isUpdating: (String) -> Boolean,
): List<BookshelfBookItem> {
    // 智能标签规则在一次构建里只取一次（内部带缓存），所有条目复用
    val smartRules = if (config.showTags) BookTagMatcher.enabledRules(context) else emptyList()
    return displays.map { display ->
        BookshelfBookItem(
            display = display,
            key = display.bookUrl,
            isUpdating = !display.isLocal && isUpdating(display.bookUrl),
            unreadCount = display.getUnreadChapterNum(),
            hasNewChapter = display.lastCheckCount > 0,
            lastUpdateText = if (config.showLastUpdateTime && !display.isLocal) {
                display.latestChapterTime.toTimeAgo()
            } else {
                null
            },
            readProgress = if (config.showReadProgress) display.readProgress() else null,
            chips = if (config.showTags) display.buildChips(smartRules) else emptyList(),
            intro = if (config.showIntro) display.getDisplayIntroPlainText() else null,
        )
    }
}

/**
 * 更新单个书籍条目的"更新中"状态（[io.legado.app.constant.EventBus.UP_BOOKSHELF] 事件）。
 *
 * 只在状态真的变化时替换该条目，其余条目保持同一实例，让 Compose 跳过它们的重组。
 */
fun updateBookshelfBookUpdating(
    items: List<BookshelfBookItem>,
    bookUrl: String,
    isUpdating: (String) -> Boolean,
): List<BookshelfBookItem> {
    var changed = false
    val updated = items.map { item ->
        if (item.key != bookUrl) return@map item
        val next = !item.display.isLocal && isUpdating(bookUrl)
        if (next == item.isUpdating) return@map item
        changed = true
        item.copy(isUpdating = next)
    }
    return if (changed) updated else items
}

/**
 * 条目底部信息顺序固定为「书籍标签 → 字数 → 分类」：书籍标签在前并带菱形前缀，
 * 其余信息照旧全部展示（与 View 版列表条目一致）。
 */
private fun BookShelfDisplay.buildChips(smartRules: List<SmartTag.ResolvedRule>): List<BookshelfChip> {
    val chips = arrayListOf<BookshelfChip>()
    BookTagMatcher.bookTagNames(customTag, toSmartTagSnapshot(), smartRules).forEach { name ->
        chips.add(BookshelfChip(text = name, bookTag = true))
    }
    wordCount?.takeIf { it.isNotBlank() }?.let {
        chips.add(BookshelfChip(text = it, bookTag = false))
    }
    kind?.splitNotBlank(",", "\n")?.forEach {
        chips.add(BookshelfChip(text = it, bookTag = false))
    }
    return chips
}
