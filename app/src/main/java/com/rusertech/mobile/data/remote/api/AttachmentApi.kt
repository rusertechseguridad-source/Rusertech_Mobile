package com.rusertech.mobile.data.remote.api

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Respuesta del backend. `url` es una URL firmada de corta duración (10 min)
 * y puede venir null si la firma falla — la subida igual fue exitosa.
 */
@Serializable
data class AttachmentUploadResponse(val id: String, val url: String? = null)

interface AttachmentApi {
    /**
     * Sube una foto de carga. multipart/form-data — no comparte el pipeline
     * de HubRawPayload porque transporta un binario, no JSON.
     *
     * FIX-9: nombres de parte alineados al contrato REAL del backend
     * desplegado: el archivo va en la parte `file` (antes `image`, que el
     * backend rechazaba con 422), `tripId` vincula la foto al viaje (null si
     * se sacó fuera de uno) y `driverDocument` identifica al conductor.
     * Las partes nullables se omiten del request.
     */
    @Multipart
    @POST("api/v1/trips/attachments")
    suspend fun uploadAttachment(
        @Header("X-Hub-Api-Key") apiKey: String,
        @Part("plate") plate: RequestBody,
        @Part("type") type: RequestBody,
        @Part("driverDocument") driverDocument: RequestBody,
        @Part("tripId") tripId: RequestBody?,
        @Part("notes") notes: RequestBody?,
        @Part("latitude") latitude: RequestBody?,
        @Part("longitude") longitude: RequestBody?,
        @Part file: MultipartBody.Part
    ): Response<AttachmentUploadResponse>
}
