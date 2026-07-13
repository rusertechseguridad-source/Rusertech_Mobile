package com.rusertech.mobile.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EventEntity): Long

    @Query("SELECT * FROM tracking_events WHERE isSynced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsynced(limit: Int = 30): List<EventEntity>

    @Query("UPDATE tracking_events SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT * FROM tracking_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 30): Flow<List<EventEntity>>

    @Query("SELECT COUNT(*) FROM tracking_events WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>

    @Query("DELETE FROM tracking_events WHERE isSynced = 1 AND timestamp < :before")
    suspend fun purgeSynced(before: Long)
}
