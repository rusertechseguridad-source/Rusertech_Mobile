package com.rusertech.mobile.ui.mode

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.remote.api.CreateTripRequest
import com.rusertech.mobile.data.remote.api.LocationPayload
import com.rusertech.mobile.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateTripViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val prefs: UserPreferences
) : ViewModel() {

    var origin by mutableStateOf(""); private set
    var originError by mutableStateOf<String?>(null); private set

    var destination by mutableStateOf(""); private set
    var destinationError by mutableStateOf<String?>(null); private set

    var cargoType by mutableStateOf(""); private set
    var notes by mutableStateOf(""); private set
    
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

    fun createTrip(onSuccess: () -> Unit) {
        if (!isValid) return
        viewModelScope.launch {
            isLoading = true
            networkError = null
            try {
                val identity = prefs.snapshot()
                if (identity != null) {
                    val req = CreateTripRequest(
                        vehicleId = identity.plate,
                        driverId = identity.documentId,
                        origin = LocationPayload(origin, 0.0, 0.0),
                        destination = LocationPayload(destination, 0.0, 0.0),
                        cargoType = cargoType,
                        notes = notes
                    )
                    tripRepository.createTrip(identity.apiKey, req)
                    onSuccess()
                } else {
                    networkError = "Sesión no válida. Vuelva a iniciar sesión."
                }
            } catch (e: Exception) {
                networkError = e.message ?: "Error al crear el viaje"
            } finally {
                isLoading = false
            }
        }
    }
}
