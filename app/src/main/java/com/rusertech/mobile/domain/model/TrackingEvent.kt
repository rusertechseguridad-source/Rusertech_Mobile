package com.rusertech.mobile.domain.model

/**
 * Tipos de evento que la app puede producir.
 * El `code` es lo que se envía en el campo Code del HubRawPayload.
 * El operador debe mapear estos códigos en el diccionario del avl_user mobile
 * dentro del dashboard Rusertech Web.
 */
enum class EventType(val code: String, val displayName: String) {
    SOS("MOB_SOS", "Pedido de ayuda (SOS)"),
    COMMUNICATION_REQUEST("MOB_COMM", "Solicitud de contacto"),
    CHECKPOINT("MOB_CHKPT", "Checkpoint"),
    INCIDENT("MOB_INCIDENT", "Incidente"),
    VEHICLE_STOP("MOB_STOP", "Parada del vehículo"),
    LOW_BATTERY("MOB_LOWBAT", "Batería baja");

    companion object {
        fun fromCode(code: String): EventType? = entries.firstOrNull { it.code == code }
    }
}

data class TrackingEvent(
    val id: Long = 0,
    val type: EventType,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val tripId: String? = null,
    val notes: String = "",
    val metadata: Map<String, String> = emptyMap()
)
