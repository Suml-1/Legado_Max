package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.HomepageModulePref
import kotlinx.coroutines.flow.Flow

/**
 * 首页模块用户偏好数据访问接口
 */
@Dao
interface HomepageModulePrefDao {

    @Query("SELECT * FROM homepage_module_prefs")
    fun flowAll(): Flow<List<HomepageModulePref>>

    @Query("SELECT * FROM homepage_module_prefs WHERE sourceUrl = :sourceUrl")
    fun flowBySource(sourceUrl: String): Flow<List<HomepageModulePref>>

    @Query("SELECT * FROM homepage_module_prefs WHERE sourceUrl = :sourceUrl AND moduleKey = :moduleKey")
    suspend fun get(sourceUrl: String, moduleKey: String): HomepageModulePref?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: HomepageModulePref)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(prefs: List<HomepageModulePref>)

    @Query("UPDATE homepage_module_prefs SET isEnabled = :enabled WHERE sourceUrl = :sourceUrl AND moduleKey = :moduleKey")
    suspend fun setEnabled(sourceUrl: String, moduleKey: String, enabled: Boolean)

    @Query("UPDATE homepage_module_prefs SET sortOrder = :order WHERE sourceUrl = :sourceUrl AND moduleKey = :moduleKey")
    suspend fun setSortOrder(sourceUrl: String, moduleKey: String, order: Int)

    @Query("UPDATE homepage_module_prefs SET customTitle = :title WHERE sourceUrl = :sourceUrl AND moduleKey = :moduleKey")
    suspend fun setCustomTitle(sourceUrl: String, moduleKey: String, title: String?)

    @Query("UPDATE homepage_module_prefs SET customSetId = :setId WHERE sourceUrl = :sourceUrl AND moduleKey = :moduleKey")
    suspend fun setCustomSetId(sourceUrl: String, moduleKey: String, setId: String?)

    @Query("DELETE FROM homepage_module_prefs WHERE sourceUrl = :sourceUrl AND moduleKey = :moduleKey")
    suspend fun delete(sourceUrl: String, moduleKey: String)

    @Query("DELETE FROM homepage_module_prefs WHERE sourceUrl = :sourceUrl")
    suspend fun deleteBySource(sourceUrl: String)

    @Query("DELETE FROM homepage_module_prefs")
    suspend fun deleteAll()

    @androidx.room.Transaction
    suspend fun batchSetSortOrders(sourceUrl: String, orders: Map<String, Int>) {
        orders.forEach { (moduleKey, order) -> setSortOrder(sourceUrl, moduleKey, order) }
    }
}
