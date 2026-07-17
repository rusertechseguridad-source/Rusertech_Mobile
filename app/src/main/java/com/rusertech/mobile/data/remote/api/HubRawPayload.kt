package com.rusertech.mobile.data.remote.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload compatible con el endpoint POST /api/v1/telemetry/ingest
 * del backend Rusertech Web. Formato idéntico al que envían los HUB GPS.
 *
 * La app mobile actúa como un HUB más. El campo Asset = patente del vehículo.
 * El campo User_avl = código del avl_user mobile asignado por el operador.
 * El campo Code = código de evento mobile (MOB_SOS, MOB_CHKPT, etc.)
 *               o null para puntos de telemetría normales.
 */
@Serializable
data class HubRawPayload(
    @SerialName("User_avl") val userAvl: String,
    @SerialName("Asset") val asset: String,
    @SerialName("MobileCode") val mobileCode: String,
    @SerialName("DriverDNI") val driverDni: String,
    @SerialName("Latitude") val latitude: Double,
    @SerialName("Longitude") val longitude: Double,
    @SerialName("Date") val date: String,
    @SerialName("Speed") val speed: Double,
    @SerialName("Course") val course: Double? = null,
    @SerialName("Ignition") val ignition: Int? = null,
    @SerialName("Battery") val battery: Int? = null,
    // Campos extra para que el backend reconozca eventos mobile (opcionales)
    @SerialName("Code") val code: String? = null,
    @SerialName("Shipment") val shipment: String? = null,
    // Campo para vincular con un viaje en Modo 1 (null en Modo 2)
    @SerialName("TripId") val tripId: String? = null
)
