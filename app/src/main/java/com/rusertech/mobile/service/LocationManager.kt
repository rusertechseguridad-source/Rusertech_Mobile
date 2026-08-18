package com.rusertech.mobile.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationManager @Inject constructor(
    private val fusedClient: FusedLocationProviderClient,
    @ApplicationContext private val context: Context
) {
    companion object {
        // A2 (tanda 6): 10 s → 5 s y 60 s → 30 s. El turno real midió
        // 0,66 %/h de batería con los intervalos viejos: hay margen de sobra
        // (objetivo < 5 %/h) y el muestreo era el cuello (10–44 pts/hora).
        const val INTERVAL_MOVING_MS = 5_000L
        const val INTERVAL_IDLE_MS = 30_000L

        // Umbral "clásico" — lo siguen usando TrackingService (persistencia
        // y detección de paradas por ancla). No cambiar sin revisar ambos.
        const val SPEED_THRESHOLD_MS = 2.0f

        // A2 + M7: HISTÉRESIS del intervalo. Subir a modo movimiento exige
        // más señal que quedarse en él — así el tráfico denso (velocidad
        // oscilando alrededor del umbral) no re-registra el request de
        // ubicación constantemente.
        const val SPEED_ENTER_MOVING_MS = 2.5f   // subir a movimiento
        const val SPEED_EXIT_MOVING_MS = 1.5f    // bajar de movimiento
        const val DISPLACEMENT_ENTER_MOVING_M = 25f  // por desplazamiento
        // Bajar a idle exige este número de fixes consecutivos quietos:
        // el jitter de GPS estacionado (picos de 10–20 m) no alcanza a
        // sostener "movimiento", y un semáforo largo tampoco derriba el
        // modo movimiento al primer fix lento.
        const val STILL_FIXES_TO_IDLE = 3

        const val SMALLEST_DISPLACEMENT_M = 10f
        const val MAX_ACCURACY_METERS = 50f
    }

    private val _locations = MutableSharedFlow<Location>(
        replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val locations: SharedFlow<Location> = _locations.asSharedFlow()
    private var callback: LocationCallback? = null
    private var currentInterval = INTERVAL_MOVING_MS

    // A2: estado del detector de movimiento para el intervalo adaptativo.
    private var lastFix: Location? = null
    private var inMovingMode = true      // arrancar rápido: primer fix ya
    private var stillStreak = 0          // fixes consecutivos sin movimiento

    @SuppressLint("MissingPermission")
    fun startUpdates() {
        if (callback != null) return
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                if (shouldEmit(loc)) {
                    _locations.tryEmit(loc)
                    adaptInterval(loc)
                    lastFix = loc
                }
            }
        }
        // SIN setMinUpdateDistanceMeters: ver tanda 2 — con el filtro de
        // desplazamiento del OS, un vehículo quieto no recibía callbacks y la
        // detección de paradas quedaba ciega. El ritmo lo gobierna el
        // intervalo adaptativo; el filtro de PERSISTENCIA vive en TrackingService.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, currentInterval)
            .setMaxUpdateDelayMillis(currentInterval * 2)
            .setWaitForAccurateLocation(false)
            .build()
        fusedClient.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
    }

    fun stopUpdates() {
        callback?.let { fusedClient.removeLocationUpdates(it); callback = null }
        lastFix = null
        inMovingMode = true
        stillStreak = 0
        currentInterval = INTERVAL_MOVING_MS
    }

    /** Solo filtra por precisión: cada punto preciso llega al servicio,
     *  aunque el vehículo esté quieto (lo necesita la lógica de paradas). */
    private fun shouldEmit(loc: Location): Boolean =
        loc.accuracy > 0f && loc.accuracy <= MAX_ACCURACY_METERS

    /**
     * A2 (tanda 6) — el bug raíz del muestreo: la versión anterior decidía
     * SOLO por `loc.speed`, que vale 0.0 cuando el fix no trae velocidad
     * (`hasSpeed() == false`, lo habitual entre edificios o dentro de un
     * vehículo). El intervalo se iba a 60 s con el vehículo circulando:
     * 10–44 puntos/hora medidos en el turno real. Es el mismo bug que I5
     * corrigió en checkAutoEvents y quedó sin corregir acá.
     *
     * Criterio nuevo: movimiento = velocidad VÁLIDA sobre umbral O
     * desplazamiento franco respecto del fix anterior. Con histéresis:
     * subir a movimiento es inmediato (no perder puntos de un arranque);
     * bajar a idle exige STILL_FIXES_TO_IDLE fixes quietos consecutivos.
     */
    @SuppressLint("MissingPermission")
    private fun adaptInterval(loc: Location) {
        val speedValid = loc.hasSpeed()
        val displacement = lastFix?.distanceTo(loc)

        val movementSignal = if (inMovingMode) {
            // Para SOSTENER movimiento alcanza una señal débil...
            (speedValid && loc.speed >= SPEED_EXIT_MOVING_MS) ||
                (displacement != null && displacement >= SMALLEST_DISPLACEMENT_M)
        } else {
            // ...para ENTRAR hace falta una fuerte (histéresis).
            (speedValid && loc.speed >= SPEED_ENTER_MOVING_MS) ||
                (displacement != null && displacement >= DISPLACEMENT_ENTER_MOVING_M)
        }

        if (movementSignal) {
            stillStreak = 0
            if (!inMovingMode) inMovingMode = true
        } else if (inMovingMode) {
            stillStreak++
            if (stillStreak >= STILL_FIXES_TO_IDLE) {
                inMovingMode = false
                stillStreak = 0
            }
        }

        val target = if (inMovingMode) INTERVAL_MOVING_MS else INTERVAL_IDLE_MS
        if (target != currentInterval) {
            currentInterval = target
            callback?.let { fusedClient.removeLocationUpdates(it) }
            callback = null
            startUpdates()
        }
    }
}
