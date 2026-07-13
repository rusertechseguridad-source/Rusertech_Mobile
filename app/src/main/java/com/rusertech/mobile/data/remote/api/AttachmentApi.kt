package com.rusertech.mobile.data.remote.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import kotlinx.serialization.Serializable

@Serializable
data class AttachmentUploadResponse(val id: String, val url: String)

interface AttachmentApi {
    /**
     * Sube una foto de carga. multipart/form-data — no comparte el pipeline
     * de HubRawPayload porque transporta un binario, no JSON.
     * El backend resuelve vehicle_id desde plate + avlUserCode, igual que telemetría.
     */
    @Multipart
    @POST("api/v1/trips/attachments")
    suspend fun uploadAttachment(
        @Header("X-Hub-Api-Key") apiKey: String,
        @Part("plate") plate: RequestBody,
        @Part("avlUserCode") avlUserCode: RequestBody,
        @Part("type") type: RequestBody,
        @Part("notes") notes: RequestBody,
        @Part("latitude") latitude: RequestBody,
        @Part("longitude") longitude: RequestBody,
        @Part("timestamp") timestamp: RequestBody,
        @Part image: MultipartBody.Part
    ): Response<AttachmentUploadResponse>
}
