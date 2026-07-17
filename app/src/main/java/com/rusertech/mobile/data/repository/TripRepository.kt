package com.rusertech.mobile.data.repository

import com.rusertech.mobile.data.local.prefs.ActiveTrip
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.remote.api.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val userPreferences: UserPreferences
) : TripApi {

    override suspend fun createTrip(apiKey: String, request: CreateTripRequest): TripResponse {
        delay(1500) // Mock delay
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
        delay(1000) // Mock delay
        userPreferences.clearActiveTrip()
        return TripResponse(tripId = tripId, status = "completed")
    }

    override suspend fun getActiveTrip(apiKey: String, vehicleId: String): TripResponse? {
        delay(800) // Mock delay
        // El mock devuelve el viaje que ya tenemos persistido localmente, o null
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
