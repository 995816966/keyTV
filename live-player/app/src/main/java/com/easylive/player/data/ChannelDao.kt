package com.easylive.player.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    /** 全量频道（按 orderIndex 排序），用 Flow 让 UI 自动刷新 */
    @Query("SELECT * FROM channels ORDER BY orderIndex ASC")
    fun observeAll(): Flow<List<Channel>>

    @Query("SELECT * FROM channels ORDER BY orderIndex ASC")
    suspend fun getAll(): List<Channel>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Channel?

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun count(): Int

    /** 替换整张表：先清空再批量写入，保证来源更新后列表干净 */
    @Query("DELETE FROM channels")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Channel>)
}
