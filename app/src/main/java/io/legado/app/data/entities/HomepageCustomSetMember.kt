package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 首页自定义集成员关系表
 *
 * 存储书源模块与自定义集之间的分配关系。
 * 用户将书源同步模块"分配到自定义集"时，在此表添加一条记录，
 * 而不复制模块数据本身（模块定义从书源 JSON 实时解析）。
 *
 * 外键关联 book_sources(bookSourceUrl)，书源删除时自动 CASCADE 清理成员关系。
 *
 * @property customSetId 自定义集 ID
 * @property sourceUrl 书源 URL（外键）
 * @property moduleKey 模块键
 * @property sortOrder 在自定义集内的排序
 */
@Entity(
    tableName = "homepage_custom_set_members",
    primaryKeys = ["customSetId", "sourceUrl", "moduleKey"],
    foreignKeys = [
        ForeignKey(
            entity = BookSource::class,
            parentColumns = ["bookSourceUrl"],
            childColumns = ["sourceUrl"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sourceUrl"), Index("customSetId")]
)
data class HomepageCustomSetMember(
    val customSetId: String = "",
    val sourceUrl: String = "",
    val moduleKey: String = "",
    val sortOrder: Int = 0,
)
