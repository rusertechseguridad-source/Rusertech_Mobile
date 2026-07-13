package com.rusertech.mobile.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

// version = 2: incluye AttachmentEntity (fotos de carga, Sección 29) desde el inicio.
// Si el proyecto ya tiene usuarios en producción con version = 1, agregar una
// Migration real en vez de subir el número directamente (ver Sección 25).
@Database(
    entities = [LocationEntity::class, EventEntity::class, AttachmentEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun eventDao(): EventDao
    abstract fun attachmentDao(): AttachmentDao
}
