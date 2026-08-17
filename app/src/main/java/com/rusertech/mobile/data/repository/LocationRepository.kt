package com.rusertech.mobile.data.repository

import com.rusertech.mobile.data.local.db.LocationDao
import com.rusertech.mobile.data.local.db.LocationEntity
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.remote.api.HubRawPayload
import com.rusertech.mobile.data.remote.api.TrackingApi
import com.rusertech.mobile.domain.model.LocationPoint
import com.rusertech.mobile.domain.model.UserIdentity
import com.rusertech.mobile.util.NetworkUtil
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val dao: LocationDao,
    private val api: TrackingApi,
    private val networkUtil: NetworkUtil,
    private val prefs: UserPreferences
) {
    /** Siempre persiste localmente primero. Intento inmediato si hay red. */
    suspend fun saveLocation(identity: UserIdentity, point: LocationPoint) {
        val entity = LocationEntity(
            latitude = point.latitude, longitude = point.longitude,
            accuracy = point.accuracy, speed = point.speed,
            heading = point.heading, altitude = point.altitude,
            battery = point.battery, timestamp = point.timestamp,
            tripId = point.tripId
        )
        val id = dao.insert(entity)

        if (networkUtil.isOnline() && identity.apiKey.isNotBlank()) {
            tryImmediateSend(identity, entity.copy(id = id))
        }
    }

    /** Sincroniza lote de ubicaciones pendientes como HubRawPayload. */
    suspend fun syncPending(identity: UserIdentity): Result<Int> = runCatching {
        if (identity.apiKey.isBlank()) return@runCatching 0
        val pending = dao.getUnsynced(50)
        if (pending.isEmpty()) return@runCatching 0

        val driverState = prefs.driverStateSnapshot()
        val payloads = pending.map { it.toHubPayload(identity, driverState) }
        val response = api.ingestBatch(identity.apiKey, payloads)

        // TODO (Track 2): Manejar el cierre remoto de viaje desde el dashboard web.
        // Si el operador finaliza el viaje (el TripId enviado ya está cerrado), el backend
        // debería devolver un HTTP 409 Conflict o un 200 OK con { trip_closed: true }.
        // Al recibir esta respuesta, la app debe limpiar 'UserPreferences.activeTrip'
        // y detener el TrackingService, informando al conductor "Viaje finalizado por operador".

        when {
            response.isSuccessful -> {
                dao.markSynced(pending.map { it.id })
            }
            // I4 — regla §3.1: un 422 NO se reintenta. Sin esta rama, un lote
            // inválido (ej.: el operador renombró la patente en la base) se
            // reintentaría cada 15 min PARA SIEMPRE y, como la cola es FIFO,
            // taparía todos los puntos nuevos detrás de él.
            response.code() == 422 -> {
                response.errorBody()?.close()
                dao.markSynced(pending.map { it.id })  // cuarentena: no se reenvían
                android.util.Log.e(
                    "LocationRepository",
                    "Lote de ${pending.size} puntos DESCARTADO por 422 (payload inválido para el backend). No se reintenta (§3.1)."
                )
            }
            else -> {
                response.errorBody()?.close()
                throw IllegalStateException("Sync falló con HTTP ${response.code()}")
            }
        }

        dao.purgeSynced(System.currentTimeMillis() - 86_400_000L)
        pending.size
    }

    fun getUnsyncedCount(): Flow<Int> = dao.getUnsyncedCount()

    private suspend fun tryImmediateSend(identity: UserIdentity, entity: LocationEntity) {
        try {
            val driverState = prefs.driverStateSnapshot()
            val resp = api.ingest(identity.apiKey, entity.toHubPayload(identity, driverState))
            if (resp.isSuccessful) dao.markSynced(listOf(entity.id))
        } catch (_: Exception) { /* WorkManager reintentará */ }
    }

    /** Convierte una LocationEntity al formato HubRawPayload del backend web. */
    private fun LocationEntity.toHubPayload(identity: UserIdentity, driverState: String?) = HubRawPayload(
        userAvl = identity.avlUserCode,
        asset = identity.plate,
        // Unificación MobileCode: SIEMPRE el avlUserCode que devolvió el login
        // (antes iba el código de activación y el backend usaba otro valor).
        mobileCode = identity.avlUserCode,
        driverDni = identity.documentId,
        latitude = latitude,
        longitude = longitude,
        date = Instant.ofEpochMilli(timestamp)
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_INSTANT),
        speed = (speed * 3.6f).toDouble(),  // m/s → km/h para el backend
        course = heading.toDouble(),
        ignition = if (speed > 0) 1 else 0,
        battery = battery,
        code = null,  // Sin evento — es telemetría pura
        shipment = null,
        tripId = tripId,
        driverState = driverState
    )
}
