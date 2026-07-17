package com.rusertech.mobile.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_locations",
    indices = [Index(value = ["isSynced", "timestamp"]), Index(value = ["timestamp"])]
)
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val heading: Float,
    val altitude: Double,
    val battery: Int,
    val timestamp: Long,
    val tripId: String? = null,
    val isSynced: Boolean = false
)
