package com.rusertech.mobile.data.repository

import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.domain.model.UserIdentity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(private val prefs: UserPreferences) {
    val userIdentity: Flow<UserIdentity?> = prefs.userIdentity
    val isTracking: Flow<Boolean> = prefs.isTracking

    suspend fun saveIdentity(documentId: String, plate: String, avlUserCode: String, apiKey: String) {
        prefs.saveIdentity(documentId, plate, avlUserCode, apiKey)
    }

    suspend fun setTracking(active: Boolean) = prefs.setTracking(active)
    suspend fun snapshot(): UserIdentity? = prefs.snapshot()
    suspend fun isTrackingSnapshot(): Boolean = prefs.isTrackingSnapshot()
    suspend fun logout() = prefs.clear()
}
