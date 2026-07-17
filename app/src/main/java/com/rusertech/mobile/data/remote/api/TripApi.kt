package com.rusertech.mobile.data.remote.api

import kotlinx.serialization.Serializable

@Serializable
data class CreateTripRequest(
    val vehicleId: String,
    val driverId: String,
    val origin: LocationPayload,
    val destination: LocationPayload,
    val cargoType: String,
    val notes: String
)

@Serializable
data class LocationPayload(
    val address: String,
    val lat: Double,
    val lng: Double
)

@Serializable
data class TripResponse(
    val tripId: String,
    val status: String,
    val createdAt: String? = null,
    val origin: LocationPayload? = null,
    val destination: LocationPayload? = null
)

interface TripApi {
    suspend fun createTrip(apiKey: String, request: CreateTripRequest): TripResponse
    suspend fun completeTrip(apiKey: String, tripId: String): TripResponse
    suspend fun getActiveTrip(apiKey: String, vehicleId: String): TripResponse?
}
