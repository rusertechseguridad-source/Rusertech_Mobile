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
    @SerialName("Asset") val asset: String,                     // = plate
    @SerialName("User_avl") val userAvl: String,                // = avlUserCode
    @SerialName("Date") val date: String,                       // ISO 8601
    @SerialName("Latitude") val latitude: String,
    @SerialName("Longitude") val longitude: String,
    @SerialName("Speed") val speed: String,
    @SerialName("Course") val course: String? = null,           // heading en grados
    @SerialName("Code") val code: String? = null,               // evento mobile o null
    @SerialName("Ignition") val ignition: String? = null,
    @SerialName("Altitude") val altitude: String? = null,
    @SerialName("Odometer") val odometer: String? = null,
    @SerialName("Battery") val battery: String? = null,
    @SerialName("Temperature") val temperature: String? = null,
    @SerialName("Humidity") val humidity: String? = null,
    @SerialName("Direction") val direction: String? = null,
    @SerialName("SerialNumber") val serialNumber: String? = null,
    @SerialName("Shipment") val shipment: String? = null,       // metadata JSON del evento
    @SerialName("SourceTag") val sourceTag: String? = "mobile_app",
    @SerialName("Alert") val alert: String? = null
)
