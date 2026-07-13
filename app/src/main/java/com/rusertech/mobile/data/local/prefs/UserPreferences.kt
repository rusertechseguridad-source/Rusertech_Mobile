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
        val AVL_USER_CODE = stringPreferencesKey("avl_user_code")
        val API_KEY = stringPreferencesKey("api_key")
        val IS_TRACKING = booleanPreferencesKey("is_tracking")
    }

    val userIdentity: Flow<UserIdentity?> = context.dataStore.data.map { prefs ->
        val doc = prefs[Keys.DOCUMENT_ID]
        val plate = prefs[Keys.PLATE]
        val avlCode = prefs[Keys.AVL_USER_CODE] ?: ""
        val apiKey = prefs[Keys.API_KEY] ?: ""
        if (!doc.isNullOrBlank() && !plate.isNullOrBlank())
            UserIdentity(doc, plate, avlCode, apiKey) else null
    }

    val isTracking: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.IS_TRACKING] ?: false
    }

    suspend fun saveIdentity(documentId: String, plate: String, avlUserCode: String, apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOCUMENT_ID] = documentId.trim()
            prefs[Keys.PLATE] = plate.trim().uppercase()
            prefs[Keys.AVL_USER_CODE] = avlUserCode.trim()
            prefs[Keys.API_KEY] = apiKey.trim()
        }
    }

    suspend fun setTracking(active: Boolean) {
        context.dataStore.edit { it[Keys.IS_TRACKING] = active }
    }

    suspend fun snapshot(): UserIdentity? = userIdentity.first()
    suspend fun isTrackingSnapshot(): Boolean = isTracking.first()

    suspend fun clear() { context.dataStore.edit { it.clear() } }
}
