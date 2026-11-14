package io.legado.app.data.entities

import android.text.TextUtils
import androidx.room.DatabaseView
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.utils.splitNotBlank

@DatabaseView(
    """select bookSourceUrl, bookSourceName, bookSourceGroup, customOrder, enabled, enabledExplore,
    ((loginUrl is not null and trim(loginUrl) <> '')
        or (instr(ifnull(loginUi, ''), '"version":2') > 0)
        or (instr(ifnull(mainJs, ''), 'loginUi') > 0 and instr(ifnull(mainJs, ''), 'loginAction') > 0))
        hasLoginUrl,
    lastUpdateTime, respondTime, weight,
    (exploreUrl is not null and trim(exploreUrl) <> '') hasExploreUrl, eventListener, bookSourceType
    from book_sources""",
    viewName = "book_sources_part"
)
data class BookSourcePart(
    // 地址，包括 http/https
    var bookSourceUrl: String = "",
    // 名称
    var bookSourceName: String = "",
    // 分组
    var bookSourceGroup: String? = null,
    // 手动排序编号
    var customOrder: Int = 0,
    // 是否启用
    var enabled: Boolean = true,
    // 是否启用发现
    var enabledExplore: Boolean = true,
    // 是否有登录地址
    var hasLoginUrl: Boolean = false,
    // 最后更新时间，用于排序
    var lastUpdateTime: Long = 0,
    // 响应时间，用于排序
    var respondTime: Long = 180000L,
    // 智能排序权重
    var weight: Int = 0,
    // 是否有发现 url
    var hasExploreUrl: Boolean = false,
    // 是否启用事件监听
    var eventListener: Boolean = false,
    // 书源类型
    var bookSourceType: Int = 0
) {

    override fun hashCode(): Int {
        return bookSourceUrl.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return if (other is BookSourcePart) other.bookSourceUrl == bookSourceUrl else false
    }

    /**
     * RecyclerView item identity.
     * Only checks whether two rows point to the same source URL.
     */
    fun isSameSource(other: BookSourcePart): Boolean {
        return bookSourceUrl == other.bookSourceUrl
    }

    /**
     * Fields that affect the Explore page list content or ordering.
     * Keep this separate from equals(); equals only models source identity.
     */
    fun hasSameExploreListContent(other: BookSourcePart): Boolean {
        return bookSourceName == other.bookSourceName
            && bookSourceGroup == other.bookSourceGroup
            && customOrder == other.customOrder
            && enabled == other.enabled
            && enabledExplore == other.enabledExplore
            && hasLoginUrl == other.hasLoginUrl
            && lastUpdateTime == other.lastUpdateTime
            && respondTime == other.respondTime
            && weight == other.weight
            && hasExploreUrl == other.hasExploreUrl
            && eventListener == other.eventListener
            && bookSourceType == other.bookSourceType
    }

    fun getDisPlayNameGroup(): String {
        return if (bookSourceGroup.isNullOrBlank()) {
            bookSourceName
        } else {
            String.format("%s (%s)", bookSourceName, bookSourceGroup)
        }
    }

    fun getBookSource(): BookSource? {
        return appDb.bookSourceDao.getBookSource(bookSourceUrl)
    }

    fun addGroup(groups: String) {
        bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)?.toHashSet()?.let {
            it.addAll(groups.splitNotBlank(AppPattern.splitGroupRegex))
            bookSourceGroup = TextUtils.join(",", it)
        }
        if (bookSourceGroup.isNullOrBlank()) bookSourceGroup = groups
    }

    fun removeGroup(groups: String) {
        bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)?.toHashSet()?.let {
            it.removeAll(groups.splitNotBlank(AppPattern.splitGroupRegex).toSet())
            bookSourceGroup = TextUtils.join(",", it)
        }
    }
}

fun List<BookSourcePart>.toBookSource(): List<BookSource> {
    return mapNotNull { it.getBookSource() }
}
