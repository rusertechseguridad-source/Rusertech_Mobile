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
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val isUploaded: Boolean = false,
    val remoteUrl: String? = null
)
