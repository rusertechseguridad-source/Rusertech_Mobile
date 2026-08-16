package com.rusertech.mobile.ui.mode

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateTripViewModel @Inject constructor(
    private val tripRepository: TripRepository,
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
            // FIX-2: crear viaje REQUIERE red. Solo un tripId real del servidor
            // persiste ActiveTrip; si falla, no queda ningún estado fantasma.
            val result = tripRepository.createTrip(
                identity = identity,
                originAddress = origin,
                destinationAddress = destination,
                cargoType = cargoType,
                notes = notes,
                plannedHours = plannedHours
            )
            isLoading = false
            if (result.isSuccess) {
                onSuccess()
            } else {
                networkError = result.exceptionOrNull()?.message ?: "Error al crear el viaje"
            }
        }
    }
}
