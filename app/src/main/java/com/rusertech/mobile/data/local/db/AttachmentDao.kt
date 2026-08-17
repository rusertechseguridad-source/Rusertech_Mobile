package com.rusertech.mobile.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AttachmentEntity): Long

    @Query("SELECT * FROM pending_attachments WHERE isUploaded = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnuploaded(limit: Int = 10): List<AttachmentEntity>

    @Query("UPDATE pending_attachments SET isUploaded = 1, remoteUrl = :url WHERE id = :id")
    suspend fun markUploaded(id: Long, url: String)

    @Query("SELECT * FROM pending_attachments ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 20): Flow<List<AttachmentEntity>>

    @Query("SELECT COUNT(*) FROM pending_attachments WHERE isUploaded = 0")
    fun getPendingCount(): Flow<Int>

    /** FIX-7: rutas locales de TODAS las fotos (para borrar los archivos). */
    @Query("SELECT localPath FROM pending_attachments")
    suspend fun getAllLocalPaths(): List<String>

    /** FIX-7: purga total en logout — las fotos no llevan identidad propia. */
    @Query("DELETE FROM pending_attachments")
    suspend fun deleteAll()
}
