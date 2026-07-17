package com.rusertech.mobile.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rusertech.mobile.domain.model.UserIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "rusertech_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DOCUMENT_ID = stringPreferencesKey("document_id")
        val PLATE = stringPreferencesKey("plate")
        val ACTIVATION_CODE = stringPreferencesKey("activation_code")
        val AVL_USER_CODE = stringPreferencesKey("avl_user_code")
        val API_KEY = stringPreferencesKey("api_key")
        val IS_TRACKING = booleanPreferencesKey("is_tracking")
        val TRIP_ID = stringPreferencesKey("trip_id")
        val TRIP_ORIGIN = stringPreferencesKey("trip_origin")
        val TRIP_DESTINATION = stringPreferencesKey("trip_destination")
        val TRIP_CARGO = stringPreferencesKey("trip_cargo")
        val TRIP_STARTED_AT = androidx.datastore.preferences.core.longPreferencesKey("trip_started_at")
    }

    val userIdentity: Flow<UserIdentity?> = context.dataStore.data.map { prefs ->
        val doc = prefs[Keys.DOCUMENT_ID]
        val plate = prefs[Keys.PLATE]
        val actCode = prefs[Keys.ACTIVATION_CODE] ?: ""
        val avlCode = prefs[Keys.AVL_USER_CODE] ?: ""
        val apiKey = prefs[Keys.API_KEY] ?: ""
        if (!doc.isNullOrBlank() && !plate.isNullOrBlank())
            UserIdentity(doc, plate, actCode, avlCode, apiKey) else null
    }

    val isTracking: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.IS_TRACKING] ?: false
    }

    val activeTrip: Flow<ActiveTrip?> = context.dataStore.data.map { prefs ->
        val tripId = prefs[Keys.TRIP_ID]
        if (!tripId.isNullOrBlank()) {
            ActiveTrip(
                tripId = tripId,
                origin = prefs[Keys.TRIP_ORIGIN] ?: "",
                destination = prefs[Keys.TRIP_DESTINATION] ?: "",
                cargoType = prefs[Keys.TRIP_CARGO] ?: "",
                startedAt = prefs[Keys.TRIP_STARTED_AT] ?: 0L
            )
        } else null
    }

    // DEBUG MOCK MODE
    val mockAuthMode: Flow<MockAuthMode> = context.dataStore.data.map { prefs ->
        try {
            MockAuthMode.valueOf(prefs[stringPreferencesKey("mock_auth_mode")] ?: MockAuthMode.SUCCESS.name)
        } catch(e: Exception) { MockAuthMode.SUCCESS }
    }

    suspend fun setMockAuthMode(mode: MockAuthMode) {
        context.dataStore.edit { it[stringPreferencesKey("mock_auth_mode")] = mode.name }
    }
    // END DEBUG MOCK MODE

    suspend fun saveIdentity(documentId: String, plate: String, activationCode: String, avlUserCode: String, apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOCUMENT_ID] = documentId.trim()
            prefs[Keys.PLATE] = plate.trim().uppercase()
            prefs[Keys.ACTIVATION_CODE] = activationCode.trim().uppercase()
            prefs[Keys.AVL_USER_CODE] = avlUserCode.trim()
            prefs[Keys.API_KEY] = apiKey.trim()
        }
    }

    suspend fun setTracking(active: Boolean) {
        context.dataStore.edit { it[Keys.IS_TRACKING] = active }
    }

    suspend fun setActiveTrip(trip: ActiveTrip?) {
        context.dataStore.edit { prefs ->
            if (trip != null) {
                prefs[Keys.TRIP_ID] = trip.tripId
                prefs[Keys.TRIP_ORIGIN] = trip.origin
                prefs[Keys.TRIP_DESTINATION] = trip.destination
                prefs[Keys.TRIP_CARGO] = trip.cargoType
                prefs[Keys.TRIP_STARTED_AT] = trip.startedAt
            } else {
                prefs.remove(Keys.TRIP_ID)
                prefs.remove(Keys.TRIP_ORIGIN)
                prefs.remove(Keys.TRIP_DESTINATION)
                prefs.remove(Keys.TRIP_CARGO)
                prefs.remove(Keys.TRIP_STARTED_AT)
            }
        }
    }

    suspend fun clearActiveTrip() = setActiveTrip(null)

    suspend fun snapshot(): UserIdentity? = userIdentity.first()
    suspend fun isTrackingSnapshot(): Boolean = isTracking.first()

    suspend fun clear() { context.dataStore.edit { it.clear() } }
}

data class ActiveTrip(
    val tripId: String,
    val origin: String,
    val destination: String,
    val cargoType: String,
    val startedAt: Long
)

enum class MockAuthMode {
    SUCCESS,
    UNAUTHORIZED_401,
    FORBIDDEN_403,
    NOT_FOUND_404,
    RATE_LIMITED_429,
    SERVER_ERROR_500
}
