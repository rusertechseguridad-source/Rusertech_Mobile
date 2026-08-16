package com.rusertech.mobile.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

// version = 2: incluye AttachmentEntity (fotos de carga, Sección 29) desde el inicio.
// version = 3: tripId en pending_locations y tracking_events (Modo Viaje).
// version = 4: awaitingFix en tracking_events (eventos encolados hasta el
//              primer fix de GPS — nunca enviar un evento con lat/lng 0,0).
@Database(
    entities = [LocationEntity::class, EventEntity::class, AttachmentEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun eventDao(): EventDao
    abstract fun attachmentDao(): AttachmentDao
}
