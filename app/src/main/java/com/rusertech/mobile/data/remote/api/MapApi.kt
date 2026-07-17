package com.rusertech.mobile.data.remote.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface MapApi {
    @GET
    suspend fun searchNominatim(
        @Url url: String = "https://nominatim.openstreetmap.org/search",
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 10
    ): List<NominatimResponse>

    @GET
    suspend fun getRoute(
        @Url url: String, // e.g. "https://router.project-osrm.org/route/v1/driving/{lon1},{lat1};{lon2},{lat2}"
        @Query("overview") overview: String = "full"
    ): OsrmResponse
}

@Serializable
data class NominatimResponse(
    val place_id: Long = 0,
    val lat: String,
    val lon: String,
    val display_name: String
)

@Serializable
data class OsrmResponse(
    val code: String,
    val routes: List<OsrmRoute>? = null
)

@Serializable
data class OsrmRoute(
    val geometry: String, // Encoded polyline
    val duration: Double, // in seconds
    val distance: Double  // in meters
)
