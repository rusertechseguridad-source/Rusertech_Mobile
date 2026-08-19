package com.rusertech.mobile.ui.mode

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.repository.EventRepository
import com.rusertech.mobile.data.repository.TripRepository
import com.rusertech.mobile.domain.model.DriverState
import com.rusertech.mobile.domain.model.EventType
import com.rusertech.mobile.service.TrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateTripViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val eventRepository: EventRepository,
    private val prefs: UserPreferences
) : ViewModel() {

    companion object {
        // Opciones cerradas del selector de duración (decisión de producto,
        // spec FIX-2 punto 5). Sin campo de texto libre.
        val PLANNED_HOURS_OPTIONS = listOf(2, 4, 6, 10, 12)
        const val PLANNED_HOURS_DEFAULT = 12
    }

    var origin by mutableStateOf(""); private set
    var originError by mutableStateOf<String?>(null); private set

    var destination by mutableStateOf(""); private set
    var destinationError by mutableStateOf<String?>(null); private set

    var cargoType by mutableStateOf(""); private set
    var notes by mutableStateOf(""); private set
    var plannedHours by mutableStateOf(PLANNED_HOURS_DEFAULT); private set

    var isLoading by mutableStateOf(false); private set
    var networkError by mutableStateOf<String?>(null); private set

    val isValid: Boolean get() = origin.isNotBlank() && destination.isNotBlank()

    fun onOriginChange(input: String) {
        origin = input.take(100)
        originError = if (origin.isBlank()) "Requerido" else null
    }

    fun onDestinationChange(input: String) {
        destination = input.take(100)
        destinationError = if (destination.isBlank()) "Requerido" else null
    }

    fun onCargoTypeChange(input: String) {
        cargoType = input.take(50)
    }

    fun onNotesChange(input: String) {
        notes = input.take(500)
    }

    fun onPlannedHoursChange(hours: Int) {
        if (hours in PLANNED_HOURS_OPTIONS) plannedHours = hours
    }

    fun createTrip(onSuccess: () -> Unit) {
        if (!isValid) return
        viewModelScope.launch {
            isLoading = true
            networkError = null
            val identity = prefs.snapshot()
            if (identity == null) {
                networkError = "Sesión no válida. Vuelva a iniciar sesión."
                isLoading = false
                return@launch
            }
            // A5 (tanda 6): capturar el estado operativo PREVIO — persistir el
            // viaje lo pisa con en_route.
            val previousState = DriverState.fromValue(prefs.driverStateSnapshot())

            // FIX-2: crear viaje REQUIERE red. Solo un tripId real del servidor
            // persiste ActiveTrip; si falla, no queda ningún estado fantasma.
            // B7 (tanda 6): origen, destino y tipo de carga normalizados a
            // MAYÚSCULAS al enviar — consistencia en el dashboard sin forzar
            // el teclado del conductor.
            val result = tripRepository.createTrip(
                identity = identity,
                originAddress = origin.trim().uppercase(),
                destinationAddress = destination.trim().uppercase(),
                cargoType = cargoType.trim().uppercase(),
                notes = notes,
                plannedHours = plannedHours
            )
            isLoading = false
            if (result.isSuccess) {
                // A5: crear el viaje deja el estado en EN_ROUTE (lo hace
                // persistActiveTrip, también en la base vía el INSERT). Si el
                // conductor venía de una parada declarada en Tracking Libre,
                // la transición se hace visible con su evento MOB_RESUME —
                // sin evento, el dashboard vería el salto de estado sin causa.
                if (previousState?.isDeclaredStop == true) {
                    val location = TrackingService.lastLocation.value
                    eventRepository.createEvent(
                        type = EventType.RESUME,
                        identity = identity,
                        latitude = location?.latitude,
                        longitude = location?.longitude,
                        metadata = mapOf("origen" to "creacion_de_viaje"),
                        tripId = result.getOrNull()?.tripId
                    )
                }
                onSuccess()
            } else {
                networkError = result.exceptionOrNull()?.message ?: "Error al crear el viaje"
            }
        }
    }
}
