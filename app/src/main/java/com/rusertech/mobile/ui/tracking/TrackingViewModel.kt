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

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val locationRepository: LocationRepository,
    private val networkUtil: NetworkUtil,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val userIdentity = userRepository.userIdentity
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

    fun logout() {
        stopTracking()
        viewModelScope.launch { userRepository.logout() }
    }
}
