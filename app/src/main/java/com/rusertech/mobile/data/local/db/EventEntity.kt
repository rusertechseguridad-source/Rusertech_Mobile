package com.rusertech.mobile.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracking_events",
    indices = [
        Index(value = ["isSynced", "timestamp"]),
        Index(value = ["timestamp"])
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,              // EventType.code (MOB_SOS, MOB_CHKPT, etc.)
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val notes: String = "",
    val metadataJson: String = "{}",
    val tripId: String? = null,
    val isSynced: Boolean = false
)
