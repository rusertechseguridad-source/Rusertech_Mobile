package com.rusertech.mobile.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rusertech.mobile.data.local.db.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE pending_locations ADD COLUMN tripId TEXT")
        database.execSQL("ALTER TABLE tracking_events ADD COLUMN tripId TEXT")
    }
}

// Eventos encolados hasta el primer fix de GPS (corrección de eventos 0,0).
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE tracking_events ADD COLUMN awaitingFix INTEGER NOT NULL DEFAULT 0")
    }
}

// FIX-9: la foto de carga se vincula al viaje activo al momento de sacarla.
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE pending_attachments ADD COLUMN tripId TEXT")
    }
}

@Module @InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "rusertech_db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

    @Provides fun provideLocationDao(db: AppDatabase): LocationDao = db.locationDao()
    @Provides fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()
    @Provides fun provideAttachmentDao(db: AppDatabase): AttachmentDao = db.attachmentDao()
}
