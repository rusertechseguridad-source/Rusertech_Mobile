package com.rusertech.mobile.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rusertech.mobile.data.repository.TripRepository
import com.rusertech.mobile.data.repository.UserRepository
import com.rusertech.mobile.ui.theme.deepSpaceGradient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val tripRepository: TripRepository
) : ViewModel() {
    
    // 0 = Loading, 1 = Tracking (Active Trip or Free), 2 = ModeSelection, 3 = Registration
    var nextDestination by mutableStateOf(0); private set

    init {
        viewModelScope.launch {
            val identity = userRepository.snapshot()
            if (identity == null) {
                nextDestination = 3
            } else {
                tripRepository.refreshActiveTrip() // sync
                val isTracking = userRepository.isTrackingSnapshot()
                if (isTracking) {
                    nextDestination = 1
                } else {
                    nextDestination = 2
                }
            }
        }
    }
}

@Composable
fun SplashRoute(
    // Se eliminó el parámetro `onRegistered`, que nunca se
    // usó. Verificado antes de borrar (a diferencia del logout: parámetro
    // sin uso = posible funcionalidad desconectada): acá NO había
    // funcionalidad perdida — el ruteo del Splash cubre los tres destinos con
    // los callbacks restantes, y el registro navega por SU propio
    // `onRegistered` en RegistrationScreen, que sí está cableado.
    onNavigateToTracking: () -> Unit,
    onNavigateToModeSelection: () -> Unit,
    onNeedsRegistration: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    LaunchedEffect(viewModel.nextDestination) {
        when (viewModel.nextDestination) {
            1 -> onNavigateToTracking()
            2 -> onNavigateToModeSelection()
            3 -> onNeedsRegistration()
        }
    }
    Box(Modifier.fillMaxSize().background(deepSpaceGradient()))
}
