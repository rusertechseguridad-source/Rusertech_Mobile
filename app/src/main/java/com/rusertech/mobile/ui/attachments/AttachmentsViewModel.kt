package com.rusertech.mobile.ui.attachments

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rusertech.mobile.data.local.db.AttachmentEntity
import com.rusertech.mobile.data.repository.AttachmentRepository
import com.rusertech.mobile.data.repository.UserRepository
import com.rusertech.mobile.domain.model.AttachmentType
import com.rusertech.mobile.service.TrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AttachmentsViewModel @Inject constructor(
    private val attachmentRepository: AttachmentRepository,
    private val userRepository: UserRepository,
    private val prefs: com.rusertech.mobile.data.local.prefs.UserPreferences
) : ViewModel() {
    var selectedType by mutableStateOf(AttachmentType.CARGO_START); private set
    var notes by mutableStateOf(""); private set
    var lastSaveOk by mutableStateOf<Boolean?>(null); private set

    val recent: StateFlow<List<AttachmentEntity>> = attachmentRepository.getRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pendingCount: StateFlow<Int> = attachmentRepository.getPendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun onTypeSelected(type: AttachmentType) { selectedType = type }
    fun onNotesChange(value: String) { notes = value.take(200) }

    fun onPhotoCaptured(uri: Uri) {
        viewModelScope.launch {
            val identity = userRepository.snapshot() ?: return@launch
            // Posición null-safe (misma política que los eventos: nada de 0,0)
            // y vínculo con el viaje activo si lo hay (FIX-9).
            val location = TrackingService.lastLocation.value
            val activeTrip = prefs.activeTrip.first()
            val ok = attachmentRepository.saveAttachment(
                identity = identity, sourceUri = uri, type = selectedType, notes = notes,
                latitude = location?.latitude, longitude = location?.longitude,
                tripId = activeTrip?.tripId
            )
            lastSaveOk = ok
            notes = ""
        }
    }
}
