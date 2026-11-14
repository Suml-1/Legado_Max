package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.model.AutoTaskRule

@Dao
interface AutoTaskRuleDao {

    @Query("select * from auto_task_rules order by sortOrder, id")
    fun all(): List<AutoTaskRule>

    @get:Query("select ifnull(max(sortOrder), -1) from auto_task_rules")
    val maxOrder: Int

    @Query("select * from auto_task_rules where id = :id")
    fun getById(id: String): AutoTaskRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg rule: AutoTaskRule)

    @Update
    fun update(vararg rule: AutoTaskRule)

    @Query("delete from auto_task_rules where id = :id")
    fun delete(id: String)

    @Query("update auto_task_rules set sortOrder = :sortOrder where id = :id")
    fun updateOrder(id: String, sortOrder: Int)

    @Transaction
    fun resetOrder(rules: List<AutoTaskRule>) {
        rules.forEachIndexed { index, rule -> updateOrder(rule.id, index) }
    }
}
