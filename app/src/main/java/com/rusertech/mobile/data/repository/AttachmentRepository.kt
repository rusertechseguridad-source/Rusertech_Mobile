package com.rusertech.mobile.data.repository

import android.content.Context
import android.net.Uri
import com.rusertech.mobile.data.local.db.AttachmentDao
import com.rusertech.mobile.data.local.db.AttachmentEntity
import com.rusertech.mobile.data.remote.api.AttachmentApi
import com.rusertech.mobile.domain.model.AttachmentType
import com.rusertech.mobile.domain.model.UserIdentity
import com.rusertech.mobile.util.ImageCompressor
import com.rusertech.mobile.util.NetworkUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AttachmentDao,
    private val api: AttachmentApi,
    private val networkUtil: NetworkUtil
) {
    private val storageDir: File
        get() = File(context.filesDir, "cargo_photos").apply { mkdirs() }

    /**
     * Comprime la foto tomada, la persiste localmente, e intenta subirla
     * de inmediato si hay red. Offline-first: la fila queda en Room
     * aunque falle la subida, y AttachmentSyncWorker reintenta después.
     */
    suspend fun saveAttachment(
        identity: UserIdentity,
        sourceUri: Uri,
        type: AttachmentType,
        notes: String,
        latitude: Double,
        longitude: Double
    ): Boolean {
        val targetFile = File(storageDir, "cargo_${System.currentTimeMillis()}.jpg")
        val compressed = ImageCompressor.compressToFile(context, sourceUri, targetFile)
        if (!compressed) return false

        val entity = AttachmentEntity(
            localPath = targetFile.absolutePath,
            type = type.code, notes = notes,
            latitude = latitude, longitude = longitude,
            timestamp = System.currentTimeMillis()
        )
        val id = dao.insert(entity)

        if (networkUtil.isOnline() && identity.apiKey.isNotBlank()) {
            tryUpload(identity, entity.copy(id = id))
        }
        return true
    }

    /** Llamado por AttachmentSyncWorker — sube de a una (multipart no soporta batch). */
    suspend fun syncPending(identity: UserIdentity): Result<Int> = runCatching {
        if (identity.apiKey.isBlank()) return@runCatching 0
        val pending = dao.getUnuploaded(10)
        var uploaded = 0
        for (attachment in pending) {
            if (tryUpload(identity, attachment)) uploaded++
        }
        uploaded
    }

    fun getRecent(): Flow<List<AttachmentEntity>> = dao.getRecent()
    fun getPendingCount(): Flow<Int> = dao.getPendingCount()

    private suspend fun tryUpload(identity: UserIdentity, entity: AttachmentEntity): Boolean {
        val file = File(entity.localPath)
        if (!file.exists()) return false
        return try {
            val imagePart = MultipartBody.Part.createFormData(
                "image", file.name, file.asRequestBody("image/jpeg".toMediaType())
            )
            val resp = api.uploadAttachment(
                apiKey = identity.apiKey,
                plate = identity.plate.toPlainRequestBody(),
                avlUserCode = identity.avlUserCode.toPlainRequestBody(),
                type = entity.type.toPlainRequestBody(),
                notes = entity.notes.toPlainRequestBody(),
                latitude = entity.latitude.toString().toPlainRequestBody(),
                longitude = entity.longitude.toString().toPlainRequestBody(),
                timestamp = entity.timestamp.toString().toPlainRequestBody(),
                image = imagePart
            )
            if (resp.isSuccessful) {
                dao.markUploaded(entity.id, resp.body()?.url ?: "")
                true
            } else false
        } catch (_: Exception) { false }
    }

    private fun String.toPlainRequestBody() = this.toRequestBody("text/plain".toMediaType())
}
