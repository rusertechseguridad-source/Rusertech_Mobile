package com.rusertech.mobile.data.repository

import android.content.Context
import androidx.work.WorkManager
import com.rusertech.mobile.data.local.db.AttachmentDao
import com.rusertech.mobile.data.local.db.EventDao
import com.rusertech.mobile.data.local.db.LocationDao
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.remote.api.AuthApi
import com.rusertech.mobile.data.remote.api.LoginRequest
import com.rusertech.mobile.domain.model.UserIdentity
import com.rusertech.mobile.util.NetworkUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences,
    private val authApi: AuthApi,
    private val locationDao: LocationDao,
    private val eventDao: EventDao,
    private val attachmentDao: AttachmentDao,
    private val locationRepository: LocationRepository,
    private val eventRepository: EventRepository,
    private val attachmentRepository: AttachmentRepository,
    private val networkUtil: NetworkUtil
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

    /**
     * FIX-7 — Logout con purga de pendientes.
     *
     * Los puntos/eventos/fotos pendientes NO llevan identidad propia: el sync
     * usa la identidad vigente de DataStore. Si un conductor cierra sesión con
     * pendientes y otro se registra en el mismo teléfono, esos datos se
     * atribuirían al vehículo del nuevo. Por eso, ANTES de limpiar DataStore:
     *
     *  1. Último intento de sync si hay red — best effort, máximo ~10 s.
     *  2. Borrado de TODO lo no sincronizado (lo que no llegó, se pierde:
     *     preferible a atribuírselo al conductor equivocado).
     *  3. Cancelación de los works periódicos (ya no hay identidad que usar).
     */
    // NonCancellable: el logout lo lanza un viewModelScope que muere al
    // navegar a la pantalla de registro. Sin esto, la purga podría quedar por
    // la mitad (identidad borrada pero pendientes vivos, o al revés).
    suspend fun logout() = withContext(NonCancellable) {
        val identity = prefs.snapshot()

        // 1) Best effort: empujar lo pendiente antes de perder la identidad.
        if (identity != null && identity.apiKey.isNotBlank() && networkUtil.isOnline()) {
            withTimeoutOrNull(10_000L) {
                runCatching { eventRepository.syncPending(identity) }
                runCatching { locationRepository.syncPending(identity) }
                runCatching { attachmentRepository.syncPending(identity) }
            }
        }

        // 2) Purga total: filas + archivos de foto locales.
        runCatching {
            attachmentDao.getAllLocalPaths().forEach { path ->
                runCatching { File(path).delete() }
            }
        }
        locationDao.deleteAll()
        eventDao.deleteAll()
        attachmentDao.deleteAll()

        // 3) Sin identidad no hay nada que sincronizar.
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("rusertech_sync")
        wm.cancelUniqueWork("rusertech_attachment_sync")

        prefs.clear()
    }
}
