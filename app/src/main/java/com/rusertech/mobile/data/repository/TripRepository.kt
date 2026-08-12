package com.rusertech.mobile.data.repository

import com.rusertech.mobile.data.local.prefs.ActiveTrip
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.remote.api.*
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ⚠️ PENDIENTE FIX-2 — este repositorio TODAVÍA NO habla con el backend.
 *
 * FIX-4 sacó de acá los delays simulados y toda referencia a la infraestructura
 * de simulación, pero el `tripId` se sigue generando localmente y NO existe en
 * el servidor. Hasta que se aplique FIX-2:
 *   - los viajes no aparecen en la tabla `trips` del backend,
 *   - la telemetría que se envíe con ese `tripId` se persiste igual (el backend
 *     ignora los TripId desconocidos), pero sin fila en `trip_events`.
 * No usar Modo Viaje en producción antes de FIX-2.
 */
@Singleton
class TripRepository @Inject constructor(
    private val userPreferences: UserPreferences
) : TripApi {

    override suspend fun createTrip(apiKey: String, request: CreateTripRequest): TripResponse {
        val tripId = "TRIP-${UUID.randomUUID().toString().take(8).uppercase()}"
        
        val activeTrip = ActiveTrip(
            tripId = tripId,
            origin = request.origin.address,
            destination = request.destination.address,
            cargoType = request.cargoType,
            startedAt = System.currentTimeMillis()
        )
        userPreferences.setActiveTrip(activeTrip)
        
        return TripResponse(tripId = tripId, status = "active", createdAt = System.currentTimeMillis().toString())
    }

    override suspend fun completeTrip(apiKey: String, tripId: String): TripResponse {
        userPreferences.clearActiveTrip()
        return TripResponse(tripId = tripId, status = "completed")
    }

    override suspend fun getActiveTrip(apiKey: String, vehicleId: String): TripResponse? {
        // Pendiente FIX-2: devuelve el viaje persistido localmente, no el del servidor.
        val currentTrip = userPreferences.activeTrip.first()
        return currentTrip?.let {
            TripResponse(
                tripId = it.tripId, 
                status = "active", 
                origin = LocationPayload(it.origin, 0.0, 0.0),
                destination = LocationPayload(it.destination, 0.0, 0.0)
            )
        }
    }
    
    suspend fun refreshActiveTrip() {
        val identity = userPreferences.snapshot() ?: return
        val serverActiveTrip = getActiveTrip(identity.apiKey, identity.plate)
        if (serverActiveTrip == null) {
            userPreferences.clearActiveTrip()
        }
    }
}
