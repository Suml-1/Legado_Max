package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 首页用户手动创建模块表
 *
 * 存储用户手动添加的首页模块（如从发现分类创建按钮组、排行榜，或手动填写URL的自定义模块）。
 * 这些模块不在书源的 homepageModules JSON 中，必须独立存储。
 *
 * 注意：不使用数据库外键约束，因为 sourceUrl 可能引用 book_sources 或 rssSources 表，
 * Room 外键只能引用单张表。改为在应用层（SourceHelp）手动级联清理：
 * - 删除书源时 → SourceHelp.deleteBookSourceInternal 调用 homepageUserModuleDao.deleteBySource
 * - 删除订阅源时 → SourceHelp.deleteRssSourceInternal 调用 homepageUserModuleDao.deleteBySource
 *
 * @property id 模块全局唯一标识
 * @property sourceUrl 书源/订阅源 URL
 * @property moduleKey 模块键
 * @property type 模块类型
 * @property title 模块标题
 * @property args 模块参数（JSON）
 * @property layoutConfig 布局配置（JSON）
 * @property url 模块 URL
 * @property isEnabled 是否在首页显示
 * @property customSetId 所属自定义集 ID（null 表示在源集中）
 * @property sortOrder 排序顺序
 * @property sourceType 模块来源类型："book"=书源模块, "rss"=订阅源模块
 */
@Entity(
    tableName = "homepage_user_modules",
    indices = [Index("sourceUrl"), Index("customSetId")]
)
data class HomepageUserModule(
    @PrimaryKey
    var id: String = "",
    var sourceUrl: String = "",
    var moduleKey: String = "",
    var type: String = "",
    var title: String = "",
    var args: String? = null,
    var layoutConfig: String? = null,
    var url: String? = null,
    var isEnabled: Boolean = true,
    var customSetId: String? = null,
    var sortOrder: Int = 0,
    var sourceType: String = "book",
)
