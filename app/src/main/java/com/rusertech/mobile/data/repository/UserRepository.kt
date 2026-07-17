package com.rusertech.mobile.data.repository

import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.domain.model.UserIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val prefs: UserPreferences,
    private val authApi: com.rusertech.mobile.data.remote.api.AuthApi
) {
    val userIdentity: Flow<UserIdentity?> = prefs.userIdentity
    val isTracking: Flow<Boolean> = prefs.isTracking

    suspend fun saveIdentity(documentId: String, plate: String, activationCode: String, avlUserCode: String, apiKey: String) {
        prefs.saveIdentity(documentId, plate, activationCode, avlUserCode, apiKey)
    }

    suspend fun login(documentId: String, plate: String, activationCode: String): Result<Unit> = try {
        kotlinx.coroutines.delay(1000) // Simular latencia de red
        
        val mode = prefs.mockAuthMode.first()
        when (mode) {
            com.rusertech.mobile.data.local.prefs.MockAuthMode.SUCCESS -> {
                val mockAvlUser = "Rusertech_Mobile"
                val mockApiKey = "mock_api_key_12345"
                prefs.saveIdentity(documentId, plate, activationCode, mockAvlUser, mockApiKey)
                Result.success(Unit)
            }
            com.rusertech.mobile.data.local.prefs.MockAuthMode.UNAUTHORIZED_401 -> 
                throw Exception("Código de activación inválido o expirado")
            com.rusertech.mobile.data.local.prefs.MockAuthMode.FORBIDDEN_403 -> 
                throw Exception("Conductor o vehículo no asociado a este operador")
            com.rusertech.mobile.data.local.prefs.MockAuthMode.NOT_FOUND_404 -> 
                throw Exception("Documento o patente no encontrados")
            com.rusertech.mobile.data.local.prefs.MockAuthMode.RATE_LIMITED_429 -> 
                throw Exception("Demasiados intentos, esperá unos minutos")
            com.rusertech.mobile.data.local.prefs.MockAuthMode.SERVER_ERROR_500 -> 
                throw Exception("Error del servidor")
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun setTracking(active: Boolean) = prefs.setTracking(active)
    suspend fun snapshot(): UserIdentity? = prefs.snapshot()
    suspend fun isTrackingSnapshot(): Boolean = prefs.isTrackingSnapshot()
    suspend fun logout() = prefs.clear()
}
