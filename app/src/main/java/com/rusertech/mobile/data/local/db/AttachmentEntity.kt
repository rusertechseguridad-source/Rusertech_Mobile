package com.rusertech.mobile.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_attachments",
    indices = [Index(value = ["isUploaded", "timestamp"])]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val localPath: String,       // ruta al JPEG comprimido en almacenamiento privado
    val type: String,            // AttachmentType.code
    val notes: String = "",
    // 0.0/0.0 = posición desconocida al sacar la foto (sin fix ni historial).
    // Al subir, esas coordenadas NO se envían (el backend las guarda null).
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    // FIX-9: viaje activo al momento de la foto (null = fuera de un viaje).
    val tripId: String? = null,
    val isUploaded: Boolean = false,
    val remoteUrl: String? = null
)
