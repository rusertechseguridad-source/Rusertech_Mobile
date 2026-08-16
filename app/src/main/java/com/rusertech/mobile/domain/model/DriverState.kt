package com.rusertech.mobile.domain.model

/**
 * FIX-10 — Estado operativo del conductor (protocolo de paradas declaradas).
 *
 * NO confundir con el ciclo de vida del viaje (`trips.status`,
 * in_progress/completed): esto es lo que el CONDUCTOR declara DENTRO de un
 * viaje en curso, y va y vuelve durante horas. Son dos dimensiones distintas.
 *
 * `value` son los strings EXACTOS del CHECK de `trips.driver_state` en la base
 * y del campo `DriverState` del payload. No cambiarlos.
 */
enum class DriverState(val value: String, val displayName: String) {
    EN_ROUTE("en_route", "En viaje"),
    STOPPED_WAYPOINT("stopped_waypoint", "Destino intermedio"),
    STOPPED_AUTHORIZED("stopped_authorized", "Parada autorizada"),
    STOPPED_SANITARY("stopped_sanitary", "Parada sanitaria");

    /** true si es una parada declarada (suprime el MOB_STOP automático). */
    val isDeclaredStop: Boolean get() = this != EN_ROUTE

    /** Evento de transición que se emite al pasar A este estado. */
    val transitionEvent: EventType
        get() = when (this) {
            EN_ROUTE -> EventType.RESUME
            STOPPED_WAYPOINT -> EventType.WAYPOINT
            STOPPED_AUTHORIZED -> EventType.STOP_AUTHORIZED
            STOPPED_SANITARY -> EventType.STOP_SANITARY
        }

    companion object {
        fun fromValue(value: String?): DriverState? =
            entries.firstOrNull { it.value == value }
    }
}
