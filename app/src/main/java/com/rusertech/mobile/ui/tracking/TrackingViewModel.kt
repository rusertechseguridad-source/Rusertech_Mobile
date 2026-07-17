package com.rusertech.mobile.ui.tracking

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rusertech.mobile.data.repository.LocationRepository
import com.rusertech.mobile.data.repository.UserRepository
import com.rusertech.mobile.service.TrackingService
import com.rusertech.mobile.util.NetworkUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.rusertech.mobile.data.local.prefs.ActiveTrip
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.repository.TripRepository

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val locationRepository: LocationRepository,
    private val tripRepository: TripRepository,
    private val prefs: UserPreferences,
    private val networkUtil: NetworkUtil,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val userIdentity = userRepository.userIdentity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        
    val activeTrip = prefs.activeTrip
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        
    val isTracking = TrackingService.isRunning
    val lastLocation = TrackingService.lastLocation
    val accessRevoked = TrackingService.accessRevoked  // Sección 10.1 — 403
    val credentialWarning = TrackingService.credentialWarning  // Sección 10.1 — 401
    val isOnline = networkUtil.isOnlineFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val pendingCount = locationRepository.getUnsyncedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun startTracking() {
        context.startForegroundService(
            Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_START }
        )
    }

    // Fix #5: stopService en vez de startService
    fun stopTracking() {
        context.startService(
            Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_STOP }
        )
        context.stopService(Intent(context, TrackingService::class.java))
    }
    
    fun completeTrip(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val identity = userIdentity.value ?: return@launch
            val trip = activeTrip.value ?: return@launch
            
            // Sync final (en V1 enviamos todo usando el SyncWorker forzado o esperamos que el TripRepository maneje el estado)
            // Aqui el tracking service se detiene
            stopTracking()
            
            try {
                tripRepository.completeTrip(identity.apiKey, trip.tripId)
                // Forzar un flush the telemetría si fuera necesario, para Mock no hace falta, 
                // ya que TrackingService al detenerse llama a locationRepository y SyncWorker
            } catch (e: Exception) {
                // Para V1 ignoramos el error y limpiamos local de todos modos
                prefs.clearActiveTrip()
            }
            onSuccess()
        }
    }

    fun logout() {
        stopTracking()
        viewModelScope.launch { userRepository.logout() }
    }
}
