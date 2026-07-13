package com.rusertech.mobile.data.remote.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface TrackingApi {

    /**
     * Endpoint ÚNICO de ingesta — mismo que usan los HUB GPS.
     * La autenticación es por header X-Hub-Api-Key (API Key del avl_user mobile).
     */
    @POST("api/v1/telemetry/ingest")
    suspend fun ingest(
        @Header("X-Hub-Api-Key") apiKey: String,
        @Body payload: HubRawPayload
    ): Response<Unit>

    /**
     * Envío en lote — múltiples puntos en un solo request.
     */
    @POST("api/v1/telemetry/ingest/batch")
    suspend fun ingestBatch(
        @Header("X-Hub-Api-Key") apiKey: String,
        @Body payload: List<HubRawPayload>
    ): Response<Unit>
}
