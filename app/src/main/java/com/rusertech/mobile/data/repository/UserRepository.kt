package com.rusertech.mobile.data.repository

import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.domain.model.UserIdentity
import kotlinx.coroutines.flow.Flow
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
        // TODO: Revertir esto cuando el equipo Web implemente el endpoint POST /api/v1/mobile/login
        // val response = authApi.login(com.rusertech.mobile.data.remote.api.LoginRequest(documentId, plate, activationCode))
        // saveIdentity(documentId, plate, activationCode, response.avlUserCode, response.apiKey)
        
        // --- MOCK PARA TESTING EN EMULADOR ---
        kotlinx.coroutines.delay(1000) // Simular latencia de red
        val mockAvlUser = "Rusertech_Mobile"
        val mockApiKey = "mock_api_key_12345"
        prefs.saveIdentity(documentId, plate, activationCode, mockAvlUser, mockApiKey)
        // -------------------------------------
        
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun setTracking(active: Boolean) = prefs.setTracking(active)
    suspend fun snapshot(): UserIdentity? = prefs.snapshot()
    suspend fun isTrackingSnapshot(): Boolean = prefs.isTrackingSnapshot()
    suspend fun logout() = prefs.clear()
}
