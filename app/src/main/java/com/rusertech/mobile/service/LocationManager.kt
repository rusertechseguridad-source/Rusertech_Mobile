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
    private var lastEmitted: Location? = null

    @SuppressLint("MissingPermission")
    fun startUpdates() {
        if (callback != null) return
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                if (shouldEmit(loc)) {
                    lastEmitted = loc
                    _locations.tryEmit(loc)
                    adaptInterval(loc.speed)
                }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, currentInterval)
            .setMinUpdateDistanceMeters(SMALLEST_DISPLACEMENT_M)
            .setMaxUpdateDelayMillis(currentInterval * 2)
            .setWaitForAccurateLocation(false)
            .build()
        fusedClient.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
    }

    fun stopUpdates() {
        callback?.let { fusedClient.removeLocationUpdates(it); callback = null }
        lastEmitted = null
    }

    private fun shouldEmit(loc: Location): Boolean {
        if (loc.accuracy <= 0f || loc.accuracy > MAX_ACCURACY_METERS) return false
        val last = lastEmitted ?: return true
        return !(last.distanceTo(loc) < SMALLEST_DISPLACEMENT_M && loc.speed < SPEED_THRESHOLD_MS)
    }

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
