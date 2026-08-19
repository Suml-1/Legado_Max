package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 首页模块用户偏好表
 *
 * 存储用户对书源同步模块的个性化配置（显隐、排序、自定义标题、自定义集归属）。
 * 模块定义本身从书源 homepageModules JSON 实时解析，不在此表存储。
 *
 * 外键关联 book_sources(bookSourceUrl)，书源删除时自动 CASCADE 清理偏好记录。
 *
 * @property sourceUrl 书源 URL（外键）
 * @property moduleKey 模块键（与书源 JSON 中的 key 对应）
 * @property isEnabled 是否在首页显示
 * @property customTitle 用户自定义标题（覆盖书源 JSON 中的 title）
 * @property sortOrder 用户自定义排序
 * @property customSetId 所属自定义集 ID（null 表示在书源集中）
 */
@Entity(
    tableName = "homepage_module_prefs",
    primaryKeys = ["sourceUrl", "moduleKey"],
    foreignKeys = [
        ForeignKey(
            entity = BookSource::class,
            parentColumns = ["bookSourceUrl"],
            childColumns = ["sourceUrl"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sourceUrl")]
)
data class HomepageModulePref(
    val sourceUrl: String = "",
    val moduleKey: String = "",
    val isEnabled: Boolean = true,
    val customTitle: String? = null,
    val sortOrder: Int = 0,
    val customSetId: String? = null,
)
