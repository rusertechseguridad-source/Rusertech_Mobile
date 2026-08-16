package com.rusertech.mobile.domain.model

/**
 * Tipos de evento que la app puede producir.
 * El `code` es lo que se envía en el campo Code del HubRawPayload.
 * El operador debe mapear estos códigos en el diccionario del avl_user mobile
 * dentro del dashboard Rusertech Web.
 *
 * FIX-10: MOB_STOP queda RESIGNIFICADO como "Parada no declarada" — es la señal
 * de seguridad (vehículo detenido >5 min sin que el conductor declare nada).
 * Las paradas declaradas van con sus propios códigos (MOB_WAYPOINT,
 * MOB_STOP_AUTH, MOB_STOP_SANIT) y la reanudación con MOB_RESUME.
 */
enum class EventType(val code: String, val displayName: String) {
    SOS("MOB_SOS", "Pedido de ayuda (SOS)"),
    COMMUNICATION_REQUEST("MOB_COMM", "Solicitud de contacto"),
    CHECKPOINT("MOB_CHKPT", "Checkpoint"),
    INCIDENT("MOB_INCIDENT", "Incidente"),
    VEHICLE_STOP("MOB_STOP", "Parada no declarada"),
    LOW_BATTERY("MOB_LOWBAT", "Batería baja"),

    // FIX-10 — transiciones de estado operativo del conductor
    WAYPOINT("MOB_WAYPOINT", "Destino intermedio"),
    STOP_AUTHORIZED("MOB_STOP_AUTH", "Parada autorizada"),
    STOP_SANITARY("MOB_STOP_SANIT", "Parada sanitaria"),
    RESUME("MOB_RESUME", "Reanudación de viaje");

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
