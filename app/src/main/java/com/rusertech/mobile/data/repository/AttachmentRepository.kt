package com.rusertech.mobile.data.repository

import android.content.Context
import android.net.Uri
import com.rusertech.mobile.data.local.db.AttachmentDao
import com.rusertech.mobile.data.local.db.AttachmentEntity
import com.rusertech.mobile.data.local.db.LocationDao
import com.rusertech.mobile.data.remote.api.AttachmentApi
import com.rusertech.mobile.domain.model.AttachmentType
import com.rusertech.mobile.domain.model.UserIdentity
import com.rusertech.mobile.util.ImageCompressor
import com.rusertech.mobile.util.NetworkUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX-9 — Fotos de carga end-to-end contra el backend real.
 *
 * Offline-first: la foto SIEMPRE se comprime y persiste localmente primero;
 * la subida inmediata es un intento oportunista y AttachmentSyncWorker
 * reintenta las pendientes en cada ciclo.
 *
 * Política de posición (coherente con EventRepository): sin fix de GPS se usa
 * la última posición conocida de Room; sin ninguna, la foto queda con 0,0
 * LOCAL como marca de "desconocida" y esas coordenadas NO se envían al
 * backend (van null → el operador ve la foto sin posición, no una posición
 * falsa en el Golfo de Guinea).
 */
@Singleton
class AttachmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AttachmentDao,
    private val locationDao: LocationDao,
    private val api: AttachmentApi,
    private val networkUtil: NetworkUtil
) {
    private val storageDir: File
        get() = File(context.filesDir, "cargo_photos").apply { mkdirs() }

    // TODA la cadena (leer el JPEG original → comprimir →
    // escribir en filesDir → borrar el original) corre en Dispatchers.IO.
    // El llamador (viewModelScope) vive en Main: sin este withContext, la
    // compresión de una foto de 8 MP congela la UI varios segundos y llega
    // al umbral de ANR en dispositivos de gama media (reproducido en campo).
    suspend fun saveAttachment(
        identity: UserIdentity,
        sourceUri: Uri,
        type: AttachmentType,
        notes: String,
        latitude: Double?,
        longitude: Double?,
        tripId: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val targetFile = File(storageDir, "cargo_${System.currentTimeMillis()}.jpg")
        // Compresión SIEMPRE antes de persistir/subir (objetivo ≤ 500 KB).
        val compressed = ImageCompressor.compressToFile(context, sourceUri, targetFile)
        // M2: el original de cámara (3–8 MB en cacheDir) ya cumplió su función
        // — la copia comprimida es la que se persiste y sube. Borrarlo vía el
        // ContentResolver (el Uri es de nuestro FileProvider, que soporta
        // delete y borra el archivo subyacente). Se borra también si la
        // compresión falló: un original sin fila en Room es un huérfano.
        runCatching { context.contentResolver.delete(sourceUri, null, null) }
        if (!compressed) return@withContext false

        // Resolución de posición: fix actual → última conocida → desconocida (0,0 local).
        val hasFix = latitude != null && longitude != null && !(latitude == 0.0 && longitude == 0.0)
        val resolved = if (hasFix) {
            latitude!! to longitude!!
        } else {
            val lastKnown = locationDao.getMostRecent()
            if (lastKnown != null) lastKnown.latitude to lastKnown.longitude else 0.0 to 0.0
        }

        val entity = AttachmentEntity(
            localPath = targetFile.absolutePath,
            type = type.code, notes = notes,
            latitude = resolved.first, longitude = resolved.second,
            timestamp = System.currentTimeMillis(),
            tripId = tripId
        )
        val id = dao.insert(entity)

        if (networkUtil.isOnline() && identity.apiKey.isNotBlank()) {
            tryUpload(identity, entity.copy(id = id))
        }
        true
    }

    /**
     * El conductor descartó la foto en el preview — borrar
     * el original de cámara sin dejar rastro. En IO, como todo lo de disco.
     */
    suspend fun discardCapture(sourceUri: Uri) = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.delete(sourceUri, null, null) }
        Unit
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
        if (!file.exists()) {
            // El archivo local desapareció (limpieza del sistema, logout a
            // medias): la fila jamás va a poder subirse — marcarla para que
            // no trabe el resto de la cola en cada ciclo.
            dao.markUploaded(entity.id, "")
            return false
        }
        return try {
            // Contrato real del backend: el binario va en la parte `file`.
            val filePart = MultipartBody.Part.createFormData(
                "file", file.name, file.asRequestBody("image/jpeg".toMediaType())
            )
            // 0,0 local significa "posición desconocida": no se envía.
            val positionKnown = !(entity.latitude == 0.0 && entity.longitude == 0.0)
            val resp = api.uploadAttachment(
                apiKey = identity.apiKey,
                plate = identity.plate.toPlainRequestBody(),
                type = entity.type.toPlainRequestBody(),
                driverDocument = identity.documentId.toPlainRequestBody(),
                tripId = entity.tripId?.toPlainRequestBody(),
                notes = entity.notes.ifBlank { null }?.toPlainRequestBody(),
                latitude = if (positionKnown) entity.latitude.toString().toPlainRequestBody() else null,
                longitude = if (positionKnown) entity.longitude.toString().toPlainRequestBody() else null,
                file = filePart
            )
            if (resp.isSuccessful) {
                dao.markUploaded(entity.id, resp.body()?.url ?: "")
                // La copia local ya cumplió su función de respaldo offline.
                file.delete()
                true
            } else {
                if (resp.code() == 422) {
                    // Payload rechazado: reintentar eternamente no lo va a
                    // arreglar (regla §3.1: la app NO reintenta un 422).
                    resp.errorBody()?.close()
                    dao.markUploaded(entity.id, "")
                } else {
                    resp.errorBody()?.close()
                }
                false
            }
        } catch (_: Exception) { false }
    }

    private fun String.toPlainRequestBody(): RequestBody =
        this.toRequestBody("text/plain".toMediaType())
}
