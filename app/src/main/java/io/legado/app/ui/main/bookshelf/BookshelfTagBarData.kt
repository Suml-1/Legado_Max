package io.legado.app.ui.main.bookshelf

import android.content.Context
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.dao.BookTagInfo
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.book.BookTagHelper
import io.legado.app.help.book.BookTagManagement
import io.legado.app.help.book.BookTagMatcher
import io.legado.app.help.book.toSmartTagSnapshot
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 书架二级标签栏的数据计算（style1 与 style2 共用同一份实现）。
 *
 * 标签来源为「当前分组内实际使用/命中的标签」与「用户手动配置的标签」的并集，
 * 减去被隐藏的标签；智能标签只保留本分组内有书籍命中的规则。
 *
 * @return 标签名列表，以及每个标签的命中数量；空字符串 key 代表"全部"标签，
 * 其数量即分组内书籍总数，供 "标签名·数量" 展示
 */
internal suspend fun loadBookshelfTagBarData(
    context: Context,
    groupId: Long,
): Pair<List<String>, Map<String, Int>> = withContext(Dispatchers.IO) {
    val configured = AppConfig.bookshelfGroupTags[groupId].orEmpty()
    val hidden = AppConfig.bookshelfHiddenTags[groupId].orEmpty()
    val groupBooks = filterBookshelfTagInfosByGroup(appDb.bookDao.allTagInfos, groupId)
    // 每本书的标签只解析一次，后续合并标签与统计数量复用
    val parsedTags = groupBooks.map { BookTagHelper.parseSet(it.customTag) }
    val existing = parsedTags.flatten()
    val merged = BookTagManagement.mergeTags(configured, existing)
        .filter { tag -> hidden.none { it.equals(tag, ignoreCase = true) } }
    val smartRules = BookTagMatcher.enabledRules(context)
    val snapshots = groupBooks.map { it.toSmartTagSnapshot() }
    val smartNames = BookTagMatcher.matchingNames(snapshots, smartRules)
    val mergedTags = BookTagManagement.mergeTags(merged, smartNames)
    val counts = BookTagMatcher.countMatches(
        mergedTags,
        parsedTags,
        snapshots,
        smartRules,
    ) + ("" to groupBooks.size)
    mergedTags to counts
}

/**
 * 按分组筛选书籍标签信息，逻辑与 [BookshelfTagManageViewModel.booksInGroup] 一致。
 * 默认分组（负数 ID）基于 [BookType] 筛选，用户分组（正数 ID）基于 group 位掩码筛选。
 */
internal fun filterBookshelfTagInfosByGroup(
    books: List<BookTagInfo>,
    groupId: Long,
): List<BookTagInfo> = when (groupId) {
    BookGroup.IdAll -> books
    BookGroup.IdLocal -> books.filter { it.type and BookType.local > 0 }
    BookGroup.IdAudio -> books.filter { it.type and BookType.audio > 0 }
    BookGroup.IdVideo -> books.filter { it.type and BookType.video > 0 }
    BookGroup.IdError -> books.filter { it.type and BookType.updateError > 0 }
    else -> {
        val userGroupMask = appDb.bookGroupDao.all
            .filter { it.groupId > 0 }
            .fold(0L) { acc, group -> acc or group.groupId }
        when (groupId) {
            BookGroup.IdNetNone -> books.filter {
                it.type and BookType.audio == 0 &&
                    it.type and BookType.video == 0 &&
                    it.type and BookType.local == 0 &&
                    (it.group and userGroupMask) == 0L
            }

            BookGroup.IdLocalNone -> books.filter {
                it.type and BookType.audio == 0 &&
                    it.type and BookType.video == 0 &&
                    it.type and BookType.local > 0 &&
                    (it.group and userGroupMask) == 0L
            }

            else -> if (groupId > 0) {
                books.filter { it.group and groupId > 0 }
            } else {
                emptyList()
            }
        }
    }
}
