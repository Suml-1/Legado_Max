package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.HomepageUserModule
import kotlinx.coroutines.flow.Flow

/**
 * 首页用户手动创建模块数据访问接口
 */
@Dao
interface HomepageUserModuleDao {

    @Query("SELECT * FROM homepage_user_modules WHERE isEnabled = 1 ORDER BY sortOrder ASC")
    fun flowEnabled(): Flow<List<HomepageUserModule>>

    @Query("SELECT * FROM homepage_user_modules ORDER BY sortOrder ASC")
    fun flowAll(): Flow<List<HomepageUserModule>>

    @Query("SELECT * FROM homepage_user_modules WHERE sourceUrl = :sourceUrl ORDER BY sortOrder ASC")
    fun flowBySource(sourceUrl: String): Flow<List<HomepageUserModule>>

    @Query("SELECT * FROM homepage_user_modules WHERE customSetId = :setId ORDER BY sortOrder ASC")
    fun flowByCustomSet(setId: String): Flow<List<HomepageUserModule>>

    @Query("SELECT * FROM homepage_user_modules WHERE id = :id")
    suspend fun getById(id: String): HomepageUserModule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(modules: List<HomepageUserModule>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(module: HomepageUserModule)

    @Query("UPDATE homepage_user_modules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE homepage_user_modules SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: String, order: Int)

    @Query("UPDATE homepage_user_modules SET customSetId = :setId WHERE id = :id")
    suspend fun setCustomSetId(id: String, setId: String?)

    @Query("UPDATE homepage_user_modules SET title = :title WHERE id = :id")
    suspend fun setTitle(id: String, title: String)

    @Query("DELETE FROM homepage_user_modules WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM homepage_user_modules WHERE sourceUrl = :sourceUrl AND moduleKey = :moduleKey")
    suspend fun deleteBySourceAndKey(sourceUrl: String, moduleKey: String)

    @Query("DELETE FROM homepage_user_modules WHERE sourceUrl = :sourceUrl")
    suspend fun deleteBySource(sourceUrl: String)

    @Query("DELETE FROM homepage_user_modules WHERE customSetId = :setId")
    suspend fun deleteByCustomSet(setId: String)

    @Query("DELETE FROM homepage_user_modules")
    suspend fun deleteAll()

    @androidx.room.Transaction
    suspend fun batchSetSortOrders(orders: Map<String, Int>) {
        orders.forEach { (id, order) -> setSortOrder(id, order) }
    }
}
