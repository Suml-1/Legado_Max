package io.legado.app.data.entities

/**
 * 书源轻量 DTO，专用于首页模块实时解析。
 *
 * 仅包含首页所需的字段：URL、名称、分组、发现URL、首页模块 JSON。
 * 普通 data class，非 @Entity 非 @DatabaseView，不会触发 Room schema 校验。
 *
 * 与 [BookSourceExploreLite] 的区别：
 * - 查询条件不同：homepage 查询关注 homepageModules 字段非空
 * - explore 查询关注 exploreUrl 字段非空
 */
data class BookSourceHomepageLite(
    val bookSourceUrl: String = "",
    val bookSourceName: String = "",
    val bookSourceGroup: String? = null,
    val exploreUrl: String? = null,
    val homepageModules: String? = null,
)
