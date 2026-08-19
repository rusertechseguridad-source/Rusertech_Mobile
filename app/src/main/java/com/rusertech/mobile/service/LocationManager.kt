package com.rusertech.mobile.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import com.rusertech.mobile.domain.model.OperationalConfig
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
        // Umbral "clásico" — lo siguen usando TrackingService (persistencia
        // y detección de paradas por ancla). No cambiar sin revisar ambos.
        const val SPEED_THRESHOLD_MS = 2.0f

        // A2 + M7: HISTÉRESIS del intervalo. Subir a modo movimiento exige
        // más señal que quedarse en él — así el tráfico denso (velocidad
        // oscilando alrededor del umbral) no re-registra el request de
        // ubicación constantemente. Estos umbrales son de calibración fina
        // del detector y quedan fijos: no forman parte de la configuración
        // operativa remota.
        const val SPEED_ENTER_MOVING_MS = 2.5f   // subir a movimiento
        const val SPEED_EXIT_MOVING_MS = 1.5f    // bajar de movimiento
        const val DISPLACEMENT_ENTER_MOVING_M = 25f  // por desplazamiento
        // Bajar a idle exige este número de fixes consecutivos quietos:
        // el jitter de GPS estacionado (picos de 10–20 m) no alcanza a
        // sostener "movimiento", y un semáforo largo tampoco derriba el
        // modo movimiento al primer fix lento.
        const val STILL_FIXES_TO_IDLE = 3
    }

    // Configuración operativa vigente (intervalos, precisión máxima,
    // desplazamiento mínimo). TrackingService la inyecta en startUpdates():
    // llega del backend en el login o son los defaults locales. Un cambio de
    // configuración aplica en el próximo inicio del tracking.
    private var config = OperationalConfig()

    private val _locations = MutableSharedFlow<Location>(
        replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val locations: SharedFlow<Location> = _locations.asSharedFlow()
    private var callback: LocationCallback? = null
    private var currentInterval = config.intervalMovingMs

    // A2: estado del detector de movimiento para el intervalo adaptativo.
    private var lastFix: Location? = null
    private var inMovingMode = true      // arrancar rápido: primer fix ya
    private var stillStreak = 0          // fixes consecutivos sin movimiento

    fun startUpdates(config: OperationalConfig) {
        if (callback != null) return
        this.config = config
        currentInterval = config.intervalMovingMs
        registerUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun registerUpdates() {
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
        // SIN setMinUpdateDistanceMeters: con el filtro de desplazamiento del
        // OS, un vehículo quieto no recibía callbacks y la detección de
        // paradas quedaba ciega. El ritmo lo gobierna el intervalo
        // adaptativo; el filtro de PERSISTENCIA vive en TrackingService.
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
        currentInterval = config.intervalMovingMs
    }

    /** Solo filtra por precisión: cada punto preciso llega al servicio,
     *  aunque el vehículo esté quieto (lo necesita la lógica de paradas). */
    private fun shouldEmit(loc: Location): Boolean =
        loc.accuracy > 0f && loc.accuracy <= config.maxAccuracyMeters

    /**
     * A2 — intervalo adaptativo. Decidir movimiento SOLO por `loc.speed` era
     * el bug raíz del sub-muestreo: speed vale 0.0 cuando el fix no trae
     * velocidad (`hasSpeed() == false`, habitual entre edificios o dentro de
     * un vehículo), y el intervalo se iba a idle con el vehículo circulando.
     * Es el mismo defecto que I5 corrigió en checkAutoEvents.
     *
     * Criterio: movimiento = velocidad VÁLIDA sobre umbral O desplazamiento
     * franco respecto del fix anterior. Con histéresis: subir a movimiento es
     * inmediato (no perder puntos de un arranque); bajar a idle exige
     * STILL_FIXES_TO_IDLE fixes quietos consecutivos.
     */
    @SuppressLint("MissingPermission")
    private fun adaptInterval(loc: Location) {
        val speedValid = loc.hasSpeed()
        val displacement = lastFix?.distanceTo(loc)

        val movementSignal = if (inMovingMode) {
            // Para SOSTENER movimiento alcanza una señal débil...
            (speedValid && loc.speed >= SPEED_EXIT_MOVING_MS) ||
                (displacement != null && displacement >= config.minDisplacementMeters)
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

        val target = if (inMovingMode) config.intervalMovingMs else config.intervalIdleMs
        if (target != currentInterval) {
            currentInterval = target
            callback?.let { fusedClient.removeLocationUpdates(it) }
            callback = null
            registerUpdates()
        }
    }
}
