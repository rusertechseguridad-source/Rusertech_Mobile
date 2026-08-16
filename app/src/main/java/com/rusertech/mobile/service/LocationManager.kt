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
        const val INTERVAL_MOVING_MS = 10_000L
        const val INTERVAL_IDLE_MS = 60_000L
        const val SPEED_THRESHOLD_MS = 2.0f
        const val SMALLEST_DISPLACEMENT_M = 10f
        const val MAX_ACCURACY_METERS = 50f
    }

    private val _locations = MutableSharedFlow<Location>(
        replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val locations: SharedFlow<Location> = _locations.asSharedFlow()
    private var callback: LocationCallback? = null
    private var currentInterval = INTERVAL_MOVING_MS

    @SuppressLint("MissingPermission")
    fun startUpdates() {
        if (callback != null) return
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                if (shouldEmit(loc)) {
                    _locations.tryEmit(loc)
                    adaptInterval(loc.speed)
                }
            }
        }
        // SIN setMinUpdateDistanceMeters: con el filtro de desplazamiento a
        // nivel del OS, un vehículo quieto no recibía NINGÚN callback, y la
        // detección de parada (MOB_STOP) y el auto-resume de FIX-10 quedaban
        // ciegos justo cuando más se los necesita. El ritmo lo gobierna el
        // intervalo adaptativo (10 s en movimiento / 60 s quieto) — que es lo
        // que manda en el consumo de batería; el filtro de desplazamiento
        // para PERSISTIR ahora vive en TrackingService.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, currentInterval)
            .setMaxUpdateDelayMillis(currentInterval * 2)
            .setWaitForAccurateLocation(false)
            .build()
        fusedClient.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
    }

    fun stopUpdates() {
        callback?.let { fusedClient.removeLocationUpdates(it); callback = null }
    }

    /** Solo filtra por precisión: cada punto preciso llega al servicio,
     *  aunque el vehículo esté quieto (lo necesita la lógica de paradas). */
    private fun shouldEmit(loc: Location): Boolean =
        loc.accuracy > 0f && loc.accuracy <= MAX_ACCURACY_METERS

    @SuppressLint("MissingPermission")
    private fun adaptInterval(speed: Float) {
        val target = if (speed < SPEED_THRESHOLD_MS) INTERVAL_IDLE_MS else INTERVAL_MOVING_MS
        if (target != currentInterval) {
            currentInterval = target
            callback?.let { fusedClient.removeLocationUpdates(it) }
            callback = null
            startUpdates()
        }
    }
}
