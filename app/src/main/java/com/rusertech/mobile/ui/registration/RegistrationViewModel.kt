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
    var activationCode by mutableStateOf(""); private set
    var documentError by mutableStateOf<String?>(null); private set
    var plateError by mutableStateOf<String?>(null); private set
    var activationError by mutableStateOf<String?>(null); private set
    var networkError by mutableStateOf<String?>(null); private set
    var isLoading by mutableStateOf(false); private set

    val isValid: Boolean get() = IdentityValidator.isValid(documentId) && PlateValidator.isValid(plate) && activationCode.length >= 4

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
    fun onActivationCodeChange(input: String) {
        // Sin uppercase forzado (decisión de Gustavo): el backend normaliza a
        // mayúsculas al validar; acá se respeta lo que el conductor tipea.
        activationCode = input.take(128)
        activationError = if (activationCode.length < 4) "Código inválido" else null
        networkError = null
    }

    /**
     * Item 7 (tanda 5): los códigos se dictan por teléfono y se tipean con
     * guantes — O/0, I/l/1 y S/5 se confunden (pasó en la prueba de campo
     * con PILOTO01). Se normalizan los caracteres ambiguos a su dígito para
     * que AMBAS grafías funcionen. Solo en la app: el backend ya normaliza
     * mayúsculas al validar, y los códigos reales usan dígitos en esas
     * posiciones.
     */
    private fun normalizeActivationCode(raw: String): String =
        raw.trim().map { c ->
            when (c) {
                'O', 'o' -> '0'
                'I', 'i', 'l' -> '1'
                'S', 's' -> '5'
                else -> c
            }
        }.joinToString("")

    fun save(onDone: () -> Unit) {
        documentError = IdentityValidator.errorOrNull(documentId)
        plateError = PlateValidator.errorOrNull(plate)
        activationError = if (activationCode.length < 4) "Requerido" else null
        if (documentError != null || plateError != null || activationError != null) return
        viewModelScope.launch {
            isLoading = true
            networkError = null
            val doc = IdentityValidator.normalize(documentId)
            val plateNorm = PlateValidator.normalize(plate)
            val rawCode = activationCode.trim()
            val normalizedCode = normalizeActivationCode(rawCode)

            // Primer intento: código normalizado (cubre "tipeé O donde era 0").
            var result = userRepository.login(doc, plateNorm, normalizedCode)

            // Fallback: si el backend dice "código inválido" y lo tipeado
            // difiere de lo normalizado, UN reintento con el código tal cual.
            // Cubre el caso inverso: el código canónico usa la LETRA (como la
            // O de PILOTO01) y la normalización lo habría roto. Así "ambas
            // grafías funcionan" sea cual sea la forma canónica, sin tocar el
            // backend. Costo: un intento extra del rate limit solo cuando el
            // primero falló por código inválido.
            if (result.isFailure && normalizedCode != rawCode &&
                result.exceptionOrNull()?.message == "Código de activación inválido o expirado"
            ) {
                result = userRepository.login(doc, plateNorm, rawCode)
            }
            isLoading = false
            if (result.isSuccess) {
                onDone()
            } else {
                networkError = result.exceptionOrNull()?.message ?: "Error desconocido"
            }
        }
    }
}
