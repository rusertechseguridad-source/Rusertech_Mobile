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

    suspend fun saveIdentity(documentId: String, plate: String, avlUserCode: String, apiKey: String) {
        prefs.saveIdentity(documentId, plate, avlUserCode, apiKey)
    }

    suspend fun login(documentId: String, plate: String): Result<Unit> = try {
        val response = authApi.login(com.rusertech.mobile.data.remote.api.LoginRequest(documentId, plate))
        saveIdentity(documentId, plate, response.avlUserCode, response.apiKey)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun setTracking(active: Boolean) = prefs.setTracking(active)
    suspend fun snapshot(): UserIdentity? = prefs.snapshot()
    suspend fun isTrackingSnapshot(): Boolean = prefs.isTrackingSnapshot()
    suspend fun logout() = prefs.clear()
}
