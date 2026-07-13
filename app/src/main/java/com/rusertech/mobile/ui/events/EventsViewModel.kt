package com.rusertech.mobile.ui.events

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rusertech.mobile.data.local.db.EventEntity
import com.rusertech.mobile.data.repository.EventRepository
import com.rusertech.mobile.data.repository.UserRepository
import com.rusertech.mobile.domain.model.EventType
import com.rusertech.mobile.domain.model.UserIdentity
import com.rusertech.mobile.service.TrackingService
import com.rusertech.mobile.util.BatteryUtil
import com.rusertech.mobile.util.NetworkUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val networkUtil: NetworkUtil,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val userIdentity: StateFlow<UserIdentity?> = userRepository.userIdentity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val isOnline: StateFlow<Boolean> = networkUtil.isOnlineFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()
    val recentEvents: StateFlow<List<EventEntity>> = eventRepository.getRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pendingCount: StateFlow<Int> = eventRepository.getUnsyncedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun fireEvent(type: EventType) {
        viewModelScope.launch {
            val identity = userRepository.userIdentity.firstOrNull() ?: return@launch
            val location = TrackingService.lastLocation.value
            eventRepository.createEvent(type, identity, location?.latitude ?: 0.0, location?.longitude ?: 0.0)
            _feedback.value = type.displayName; delay(3000); _feedback.value = null
        }
    }

    fun fireSOS() {
        triggerVibration()
        viewModelScope.launch {
            val identity = userRepository.userIdentity.firstOrNull() ?: return@launch
            val location = TrackingService.lastLocation.value
            eventRepository.createEvent(EventType.SOS, identity, location?.latitude ?: 0.0, location?.longitude ?: 0.0,
                metadata = mapOf("battery" to BatteryUtil.getLevel(context).toString(),
                    "network" to if (isOnline.value) "online" else "offline"))
            _feedback.value = "SOS enviado"; delay(3000); _feedback.value = null
        }
    }

    private fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                    .vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }
}
