package com.rusertech.mobile.data.repository

import com.rusertech.mobile.data.local.prefs.ActiveTrip
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.remote.api.CreateTripRequest
import com.rusertech.mobile.data.remote.api.TripApi
import com.rusertech.mobile.data.remote.api.TripResponse
import com.rusertech.mobile.domain.model.DriverState
import com.rusertech.mobile.domain.model.UserIdentity
import com.rusertech.mobile.util.NetworkUtil
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX-2 — Repositorio REAL de viajes.
 *
 * Reglas de producto (spec FIX-2):
 *  - `createTrip` REQUIERE red: solo un 200 del backend (o un 409 con el viaje
 *    ya activo, que se adopta) persiste `ActiveTrip` con el tripId REAL del
 *    servidor. Sin red → error claro, sin estado local fantasma.
 *  - `completeTrip` es OFFLINE-TOLERANTE: la UI se limpia YA (el conductor
 *    terminó); si la red falla, el tripId queda en PENDING_TRIP_CLOSE y
 *    SyncWorker reintenta el cierre al inicio de cada ciclo hasta 2xx
 *    (404/409 se tratan como "ya cerrado").
 */
@Singleton
class TripRepository @Inject constructor(
    private val api: TripApi,
    private val userPreferences: UserPreferences,
    private val networkUtil: NetworkUtil,
    private val json: Json
) {
    companion object {
        const val MSG_NEED_NETWORK =
            "Necesitás conexión para iniciar un viaje. Podés usar Tracking Libre mientras tanto."
    }

    /**
     * Crea el viaje en el backend. Devuelve el tripId REAL del servidor.
     * En 409 (ya hay un viaje en curso para esta patente) se ADOPTA el viaje
     * existente: la base es la autoridad sobre "un viaje activo por vehículo".
     */
    suspend fun createTrip(
        identity: UserIdentity,
        originAddress: String,
        destinationAddress: String,
        cargoType: String,
        notes: String,
        plannedHours: Int
    ): Result<ActiveTrip> {
        if (!networkUtil.isOnline()) {
            return Result.failure(Exception(MSG_NEED_NETWORK))
        }

        val request = CreateTripRequest(
            vehicleId = identity.plate,       // patente — el backend resuelve el UUID
            driverId = identity.documentId,   // DNI — el backend resuelve el UUID
            originAddress = originAddress.ifBlank { null },
            destinationAddress = destinationAddress.ifBlank { null },
            cargoType = cargoType.ifBlank { null },
            notes = notes.ifBlank { null },
            plannedHours = plannedHours
        )

        return try {
            val response = api.createTrip(identity.apiKey, request)
            when {
                response.isSuccessful -> {
                    val body = response.body()
                        ?: return Result.failure(Exception("Respuesta inválida del servidor, intentá de nuevo"))
                    val trip = persistActiveTrip(body.tripId, originAddress, destinationAddress, cargoType)
                    Result.success(trip)
                }
                // Ya hay un viaje en curso: adoptarlo en lugar de fallar.
                response.code() == 409 -> {
                    val existing = parseTripFromErrorBody(response.errorBody()?.string())
                        ?: return Result.failure(Exception("El vehículo ya tiene un viaje en curso"))
                    val trip = persistActiveTrip(existing.tripId, originAddress, destinationAddress, cargoType)
                    Result.success(trip)
                }
                else -> {
                    val message = parseMessageFromErrorBody(response.errorBody()?.string())
                        ?: defaultMessageForCode(response.code())
                    Result.failure(Exception(message))
                }
            }
        } catch (e: IOException) {
            Result.failure(Exception(MSG_NEED_NETWORK))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cierre offline-tolerante. SIEMPRE limpia el estado local primero:
     * para el conductor el viaje terminó, la UI lo refleja ya. La
     * confirmación contra el backend puede llegar después.
     */
    suspend fun completeTrip(identity: UserIdentity, tripId: String) {
        userPreferences.clearActiveTrip()
        userPreferences.setDriverState(null)  // FIX-10: sin viaje no hay estado operativo

        val confirmed = tryCompleteOnServer(identity, tripId)
        if (!confirmed) {
            userPreferences.setPendingTripClose(tripId)
        }
    }

    /**
     * Reintento del cierre pendiente. SyncWorker lo llama al INICIO de cada
     * ciclo. 2xx = cerrado; 404/409 = ya estaba cerrado (o no existe): en
     * ambos casos se limpia la clave.
     */
    suspend fun retryPendingTripClose(identity: UserIdentity) {
        val pending = userPreferences.pendingTripCloseSnapshot() ?: return
        if (tryCompleteOnServer(identity, pending)) {
            userPreferences.setPendingTripClose(null)
        }
    }

    /**
     * Consulta el viaje activo en el backend y reconcilia el estado local.
     * Se usa al arrancar la app (Splash): si el servidor dice que no hay
     * viaje, se limpia lo local; si hay uno con otro tripId, se adopta.
     */
    suspend fun refreshActiveTrip() {
        val identity = userPreferences.snapshot() ?: return
        if (!networkUtil.isOnline() || identity.apiKey.isBlank()) return

        try {
            val response = api.getActiveTrip(identity.apiKey, identity.plate)
            if (!response.isSuccessful) return  // sin red/error: no tocar lo local

            val serverTrip = response.body()  // null = sin viaje activo
            val localTrip = userPreferences.activeTrip.first()
            when {
                serverTrip == null && localTrip != null -> {
                    // El servidor no conoce viaje activo (p. ej. lo cerró el
                    // operador por SQL durante el piloto): limpiar lo local.
                    userPreferences.clearActiveTrip()
                    userPreferences.setDriverState(null)
                }
                serverTrip != null && localTrip == null ->
                    persistActiveTrip(serverTrip.tripId, "", "", "")
                serverTrip != null && localTrip != null && serverTrip.tripId != localTrip.tripId ->
                    persistActiveTrip(serverTrip.tripId, localTrip.origin, localTrip.destination, localTrip.cargoType)
            }
        } catch (_: Exception) {
            // Sin red o backend caído: el estado local manda hasta el próximo intento.
        }
    }

    // ------------------------------------------------------------------

    private suspend fun persistActiveTrip(
        tripId: String,
        origin: String,
        destination: String,
        cargoType: String
    ): ActiveTrip {
        val trip = ActiveTrip(
            tripId = tripId,
            origin = origin,
            destination = destination,
            cargoType = cargoType,
            startedAt = System.currentTimeMillis()
        )
        userPreferences.setActiveTrip(trip)
        userPreferences.setDriverState(DriverState.EN_ROUTE.value)  // FIX-10: default al crear
        return trip
    }

    /** true si el backend confirmó el cierre (2xx) o el viaje ya estaba cerrado (404/409). */
    private suspend fun tryCompleteOnServer(identity: UserIdentity, tripId: String): Boolean {
        return try {
            val response = api.completeTrip(identity.apiKey, tripId)
            when {
                response.isSuccessful -> true
                response.code() == 409 || response.code() == 404 -> {
                    response.errorBody()?.close()
                    true  // ya cerrado / no existe: nada que reintentar
                }
                else -> {
                    response.errorBody()?.close()
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseTripFromErrorBody(bodyString: String?): TripResponse? {
        if (bodyString.isNullOrBlank()) return null
        return try {
            json.decodeFromString(TripResponse.serializer(), bodyString)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMessageFromErrorBody(bodyString: String?): String? {
        if (bodyString.isNullOrBlank()) return null
        return try {
            val obj = json.parseToJsonElement(bodyString)
            (obj as? kotlinx.serialization.json.JsonObject)
                ?.get("message")
                ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        } catch (_: Exception) {
            null
        }
    }

    private fun defaultMessageForCode(code: Int): String = when {
        code == 401 -> "Credencial inválida. Revisá tu registro."
        code == 403 -> "El operador bloqueó este vehículo"
        code == 422 -> "El backend rechazó los datos del viaje"
        code >= 500 -> "Error del servidor, intentá de nuevo"
        else -> "No se pudo crear el viaje (error $code)"
    }
}
