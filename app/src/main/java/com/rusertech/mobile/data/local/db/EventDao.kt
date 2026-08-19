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

    // awaitingFix = 0: un evento que espera su primer fix de GPS NUNCA se envía
    // (sus coordenadas todavía son el placeholder 0,0).
    @Query("SELECT * FROM tracking_events WHERE isSynced = 0 AND awaitingFix = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsynced(limit: Int = 30): List<EventEntity>

    @Query("UPDATE tracking_events SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM tracking_events WHERE awaitingFix = 1")
    suspend fun countAwaitingFix(): Int

    /**
     * Completa con el primer fix real las coordenadas de los eventos que se
     * crearon sin ninguna posición disponible, y los libera para el sync.
     */
    @Query("UPDATE tracking_events SET latitude = :lat, longitude = :lng, awaitingFix = 0 WHERE awaitingFix = 1")
    suspend fun resolveAwaitingFix(lat: Double, lng: Double): Int

    @Query("SELECT * FROM tracking_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 30): Flow<List<EventEntity>>

    @Query("SELECT COUNT(*) FROM tracking_events WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>

    @Query("DELETE FROM tracking_events WHERE isSynced = 1 AND timestamp < :before")
    suspend fun purgeSynced(before: Long)

    /** FIX-7: purga total en logout — los eventos no llevan identidad propia. */
    @Query("DELETE FROM tracking_events")
    suspend fun deleteAll()

    /** B3: eventos con posición real para marcar sobre el rastro del mapa. */
    @Query("SELECT * FROM tracking_events WHERE timestamp >= :since AND awaitingFix = 0 ORDER BY timestamp ASC")
    suspend fun getEventsSince(since: Long): List<EventEntity>
}
