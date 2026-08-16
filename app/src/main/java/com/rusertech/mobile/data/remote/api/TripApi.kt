package com.rusertech.mobile.data.remote.api

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Contrato REAL de viajes contra el backend desplegado (FIX-2).
 *
 * ⚠️ ESTRUCTURA PLANA: el backend en producción espera los campos de
 * origen/destino sueltos (originAddress, originLat, ...), no anidados en un
 * objeto. El DTO anterior anidaba origen y destino en un objeto propio, pero
 * nunca viajó por la red (el repositorio era simulado): este es el primer
 * contrato real.
 *
 * vehicleId = PATENTE y driverId = DNI (decisión del spec §4.4): el backend
 * resuelve internamente los UUID reales de vehículo y conductor.
 */
@Serializable
data class CreateTripRequest(
    val vehicleId: String,                  // patente (el backend resuelve el UUID)
    val driverId: String,                   // DNI (el backend resuelve el UUID)
    val originAddress: String? = null,
    val originLat: Double? = null,          // null = sin geocodificar (la app no geocodifica)
    val originLng: Double? = null,
    val destinationAddress: String? = null,
    val destinationLat: Double? = null,
    val destinationLng: Double? = null,
    val cargoType: String? = null,
    val notes: String? = null,
    // Duración planificada. Opciones cerradas del selector: 2/4/6/10/12 horas.
    // El backend calcula planned_end = now() + plannedHours.
    val plannedHours: Int = 12
)

/**
 * Respuesta del backend. `status` ∈ {"active", "completed"} (vocabulario del
 * contrato §3.2, NO el de la base). El 409 de "ya hay un viaje en curso"
 * también trae tripId/status, por eso este mismo DTO sirve para parsearlo.
 */
@Serializable
data class TripResponse(
    val tripId: String,
    val status: String
)

interface TripApi {
    @POST("api/v1/trips")
    suspend fun createTrip(
        @Header("X-Hub-Api-Key") apiKey: String,
        @Body request: CreateTripRequest
    ): Response<TripResponse>

    @POST("api/v1/trips/{tripId}/complete")
    suspend fun completeTrip(
        @Header("X-Hub-Api-Key") apiKey: String,
        @Path("tripId") tripId: String
    ): Response<TripResponse>

    @GET("api/v1/trips/active")
    suspend fun getActiveTrip(
        @Header("X-Hub-Api-Key") apiKey: String,
        @Query("plate") plate: String
    ): Response<TripResponse?>
}
