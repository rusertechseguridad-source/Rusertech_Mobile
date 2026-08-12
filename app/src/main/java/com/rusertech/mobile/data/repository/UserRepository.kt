package com.rusertech.mobile.data.repository

import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.remote.api.AuthApi
import com.rusertech.mobile.data.remote.api.LoginRequest
import com.rusertech.mobile.domain.model.UserIdentity
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val prefs: UserPreferences,
    private val authApi: AuthApi
) {
    val userIdentity: Flow<UserIdentity?> = prefs.userIdentity
    val isTracking: Flow<Boolean> = prefs.isTracking

    suspend fun saveIdentity(documentId: String, plate: String, activationCode: String, avlUserCode: String, apiKey: String) {
        prefs.saveIdentity(documentId, plate, activationCode, avlUserCode, apiKey)
    }

    /**
     * Login real contra el backend (FIX-1).
     *
     * Solo un 200 con API Key no vacía persiste la identidad: si el servidor
     * responde cualquier otra cosa, en DataStore no queda nada y el conductor
     * vuelve a ver la pantalla de registro.
     *
     * Los códigos HTTP se mapean a mensajes de usuario según §3.1 del spec.
     * El 401/403 de ESTE endpoint no llega a AuthEventBus: el AuthInterceptor
     * ignora el path de login (FIX-3). Un código mal tipeado muestra el error
     * en pantalla, no una notificación de "credenciales revocadas".
     */
    suspend fun login(documentId: String, plate: String, activationCode: String): Result<Unit> = try {
        val response = authApi.login(LoginRequest(documentId, plate, activationCode))

        if (response.isSuccessful) {
            val body = response.body()
            if (body == null || body.apiKey.isBlank()) {
                Result.failure(Exception("Respuesta inválida del servidor, intentá de nuevo"))
            } else {
                prefs.saveIdentity(documentId, plate, activationCode, body.avlUserCode, body.apiKey)
                Result.success(Unit)
            }
        } else {
            // El errorBody retiene la conexión del pool de OkHttp: cerrarlo o
            // se filtra una conexión por cada login fallido.
            response.errorBody()?.close()
            Result.failure(Exception(messageForCode(response.code())))
        }
    } catch (e: IOException) {
        // Sin red, DNS caído, timeout: nunca es culpa de las credenciales.
        Result.failure(Exception("Sin conexión. Verificá tu internet e intentá de nuevo"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun messageForCode(code: Int): String = when {
        code == 401 -> "Código de activación inválido o expirado"
        code == 403 -> "Conductor o vehículo no asociado a este operador"
        code == 404 -> "Documento o patente no encontrados"
        code == 429 -> "Demasiados intentos, esperá unos minutos"
        code >= 500 -> "Error del servidor, intentá de nuevo"
        // 422 y cualquier otro 4xx: el backend rechazó los datos enviados.
        // No debería pasar con la validación de la pantalla, pero si pasa el
        // conductor tiene que ver algo accionable y no una pantalla muda.
        else -> "No se pudo validar el registro (error $code)"
    }

    suspend fun setTracking(active: Boolean) = prefs.setTracking(active)
    suspend fun snapshot(): UserIdentity? = prefs.snapshot()
    suspend fun isTrackingSnapshot(): Boolean = prefs.isTrackingSnapshot()
    suspend fun logout() = prefs.clear()
}
