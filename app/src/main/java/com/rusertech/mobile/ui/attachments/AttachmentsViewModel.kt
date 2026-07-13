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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AttachmentsViewModel @Inject constructor(
    private val attachmentRepository: AttachmentRepository,
    private val userRepository: UserRepository
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
            val location = TrackingService.lastLocation.value
            val ok = attachmentRepository.saveAttachment(
                identity = identity, sourceUri = uri, type = selectedType, notes = notes,
                latitude = location?.latitude ?: 0.0, longitude = location?.longitude ?: 0.0
            )
            lastSaveOk = ok
            notes = ""
        }
    }
}
