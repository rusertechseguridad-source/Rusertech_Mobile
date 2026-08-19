package com.rusertech.mobile.ui.tracking

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.repository.EventRepository
import com.rusertech.mobile.data.repository.LocationRepository
import com.rusertech.mobile.data.repository.TripRepository
import com.rusertech.mobile.data.repository.UserRepository
import com.rusertech.mobile.domain.model.DriverState
import com.rusertech.mobile.service.TrackingService
import com.rusertech.mobile.util.NetworkUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val locationRepository: LocationRepository,
    private val tripRepository: TripRepository,
    private val eventRepository: EventRepository,
    private val prefs: UserPreferences,
    private val networkUtil: NetworkUtil,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val userIdentity = userRepository.userIdentity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeTrip = prefs.activeTrip
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // FIX-10: estado operativo del conductor, siempre visible en la pantalla.
    // null (sin declarar) se muestra como "En viaje".
    val driverState = prefs.driverState
        .map { DriverState.fromValue(it) ?: DriverState.EN_ROUTE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DriverState.EN_ROUTE)

    val isTracking = TrackingService.isRunning
    // C1: intención de trackear persistida en DataStore. Si es true pero el
    // servicio no corre (reboot sin permiso de background → notificación),
    // la pantalla reanuda sola al pasar a foreground.
    val trackingIntended = userRepository.isTracking
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
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

    /**
     * FIX-10: el conductor declara su estado operativo desde el bottom sheet.
     * Cada transición persiste el estado en DataStore y emite el evento MOB_
     * correspondiente con la posición actual y el tripId si hay viaje.
     * Disponible también en Tracking Libre (los eventos van sin tripId).
     */
    fun declareState(newState: DriverState, metadata: Map<String, String> = emptyMap()) {
        viewModelScope.launch {
            val current = driverState.value
            if (newState == current) return@launch

            val identity = userIdentity.value ?: return@launch
            prefs.setDriverState(newState.value)

            // Sin fix de GPS se pasa null: EventRepository usa la última
            // posición conocida (staleLocation) o encola hasta el primer fix.
            // B6: el detalle (p. ej. lugar de la parada sanitaria) viaja en
            // metadata — el diccionario de códigos MOB_ no crece.
            val location = lastLocation.value
            eventRepository.createEvent(
                type = newState.transitionEvent,
                identity = identity,
                latitude = location?.latitude,
                longitude = location?.longitude,
                metadata = metadata,
                tripId = activeTrip.value?.tripId
            )
        }
    }

    // B4: distancia acumulada de la sesión (metros), desde el servicio.
    val sessionDistanceM = TrackingService.sessionDistanceM

    fun completeTrip(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val identity = userIdentity.value ?: return@launch
            val trip = activeTrip.value ?: return@launch

            stopTracking()

            // FIX-2: cierre offline-tolerante. El repositorio limpia el estado
            // local YA (ActiveTrip + driver_state) y, si la red falla, deja el
            // cierre en PENDING_TRIP_CLOSE para que SyncWorker lo reintente.
            tripRepository.completeTrip(identity, trip.tripId)
            onSuccess()
        }
    }

    fun logout() {
        stopTracking()
        viewModelScope.launch { userRepository.logout() }
    }
}
