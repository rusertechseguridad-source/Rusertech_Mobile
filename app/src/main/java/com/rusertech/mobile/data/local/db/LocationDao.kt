package com.rusertech.mobile.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LocationEntity): Long

    @Query("SELECT * FROM pending_locations WHERE isSynced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsynced(limit: Int = 50): List<LocationEntity>

    @Query("UPDATE pending_locations SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM pending_locations WHERE isSynced = 1 AND timestamp < :before")
    suspend fun purgeSynced(before: Long)

    @Query("SELECT COUNT(*) FROM pending_locations WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}
