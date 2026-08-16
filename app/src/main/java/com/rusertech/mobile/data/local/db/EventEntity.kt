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
    val isSynced: Boolean = false,
    // true = el evento se creó SIN ninguna posición disponible y espera el
    // primer fix de GPS para completar sus coordenadas. Mientras esté en true,
    // el sync NO lo envía (jamás mandar un evento con lat/lng 0,0).
    val awaitingFix: Boolean = false
)
