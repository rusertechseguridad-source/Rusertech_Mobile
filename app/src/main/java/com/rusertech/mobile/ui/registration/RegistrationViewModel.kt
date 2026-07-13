package com.rusertech.mobile.ui.registration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rusertech.mobile.data.repository.UserRepository
import com.rusertech.mobile.util.IdentityValidator
import com.rusertech.mobile.util.PlateValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    var documentId by mutableStateOf(""); private set
    var plate by mutableStateOf(""); private set
    var documentError by mutableStateOf<String?>(null); private set
    var plateError by mutableStateOf<String?>(null); private set
    var networkError by mutableStateOf<String?>(null); private set
    var isLoading by mutableStateOf(false); private set

    val isValid: Boolean get() = IdentityValidator.isValid(documentId) && PlateValidator.isValid(plate)

    fun onDocumentChange(input: String) {
        documentId = input.take(20)
        documentError = if (documentId.isNotEmpty()) IdentityValidator.errorOrNull(documentId) else null
        networkError = null
    }
    fun onPlateChange(input: String) {
        plate = input.uppercase().take(10)
        plateError = if (plate.isNotEmpty()) PlateValidator.errorOrNull(plate) else null
        networkError = null
    }

    fun save(onDone: () -> Unit) {
        documentError = IdentityValidator.errorOrNull(documentId)
        plateError = PlateValidator.errorOrNull(plate)
        if (documentError != null || plateError != null) return
        viewModelScope.launch {
            isLoading = true
            networkError = null
            val result = userRepository.login(
                IdentityValidator.normalize(documentId), PlateValidator.normalize(plate)
            )
            isLoading = false
            if (result.isSuccess) {
                onDone()
            } else {
                networkError = "Error de conexión o datos incorrectos"
            }
        }
    }
}
