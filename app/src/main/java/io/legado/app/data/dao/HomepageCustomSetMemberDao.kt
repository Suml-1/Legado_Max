package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.HomepageCustomSetMember
import kotlinx.coroutines.flow.Flow

/**
 * 首页自定义集成员关系数据访问接口
 */
@Dao
interface HomepageCustomSetMemberDao {

    @Query("SELECT * FROM homepage_custom_set_members ORDER BY sortOrder ASC")
    fun flowAll(): Flow<List<HomepageCustomSetMember>>

    @Query("SELECT * FROM homepage_custom_set_members WHERE customSetId = :setId ORDER BY sortOrder ASC")
    fun flowByCustomSet(setId: String): Flow<List<HomepageCustomSetMember>>

    @Query("SELECT * FROM homepage_custom_set_members WHERE sourceUrl = :sourceUrl")
    fun flowBySource(sourceUrl: String): Flow<List<HomepageCustomSetMember>>

    @Query("SELECT * FROM homepage_custom_set_members WHERE customSetId = :setId AND sourceUrl = :sourceUrl AND moduleKey = :moduleKey")
    suspend fun get(setId: String, sourceUrl: String, moduleKey: String): HomepageCustomSetMember?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: HomepageCustomSetMember)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(members: List<HomepageCustomSetMember>)

    @Query("DELETE FROM homepage_custom_set_members WHERE customSetId = :setId AND sourceUrl = :sourceUrl AND moduleKey = :moduleKey")
    suspend fun delete(setId: String, sourceUrl: String, moduleKey: String)

    @Query("DELETE FROM homepage_custom_set_members WHERE customSetId = :setId")
    suspend fun deleteByCustomSet(setId: String)

    @Query("DELETE FROM homepage_custom_set_members WHERE sourceUrl = :sourceUrl")
    suspend fun deleteBySource(sourceUrl: String)

    @Query("DELETE FROM homepage_custom_set_members")
    suspend fun deleteAll()

    @Query("UPDATE homepage_custom_set_members SET sortOrder = :order WHERE customSetId = :customSetId AND sourceUrl = :sourceUrl AND moduleKey = :moduleKey")
    suspend fun setSortOrder(customSetId: String, sourceUrl: String, moduleKey: String, order: Int)

    @androidx.room.Transaction
    suspend fun batchSetSortOrders(customSetId: String, sourceUrl: String, orders: Map<String, Int>) {
        // orders: moduleKey -> sortOrder
        orders.forEach { (moduleKey, order) ->
            setSortOrder(customSetId, sourceUrl, moduleKey, order)
        }
    }
}
