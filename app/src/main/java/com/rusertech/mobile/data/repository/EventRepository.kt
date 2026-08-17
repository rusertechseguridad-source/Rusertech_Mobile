package com.rusertech.mobile.data.repository

import com.rusertech.mobile.data.local.db.EventDao
import com.rusertech.mobile.data.local.db.EventEntity
import com.rusertech.mobile.data.local.db.LocationDao
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.remote.api.HubRawPayload
import com.rusertech.mobile.data.remote.api.TrackingApi
import com.rusertech.mobile.domain.model.EventType
import com.rusertech.mobile.domain.model.UserIdentity
import com.rusertech.mobile.util.NetworkUtil
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Los eventos mobile se envían como puntos de telemetría con el campo Code
 * seteado al código del evento (MOB_SOS, MOB_CHKPT, etc.).
 *
 * Política de posición (corrección de eventos 0,0):
 *  1. Hay fix de GPS → se usa tal cual.
 *  2. No hay fix pero Room tiene una posición previa → se usa esa, con
 *     `staleLocation: true` (y su antigüedad) en el metadata.
 *  3. No hay NINGUNA posición → el evento se persiste con `awaitingFix = true`
 *     y queda ENCOLADO: el sync no lo envía hasta que el primer fix real
 *     complete sus coordenadas. Jamás se manda un evento con lat/lng 0,0
 *     (un SOS en el Golfo de Guinea no le sirve a nadie).
 */
@Singleton
class EventRepository @Inject constructor(
    private val dao: EventDao,
    private val locationDao: LocationDao,
    private val api: TrackingApi,
    private val networkUtil: NetworkUtil,
    private val prefs: UserPreferences
) {
    // Evita consultar Room en cada punto de GPS: solo hay que resolver algo
    // si de verdad se encoló un evento. null = desconocido (primer uso tras
    // reiniciar el proceso: hay que mirar la base una vez).
    @Volatile
    private var hasAwaitingFix: Boolean? = null

    suspend fun createEvent(
        type: EventType,
        identity: UserIdentity,
        latitude: Double?,
        longitude: Double?,
        notes: String = "",
        metadata: Map<String, String> = emptyMap(),
        tripId: String? = null
    ): Long {
        // 0,0 exacto se trata como "sin fix": es el valor centinela que
        // producía el bug y no es una posición real de ningún vehículo de la
        // flota (0°N 0°E está en el océano).
        val hasFix = latitude != null && longitude != null &&
            !(latitude == 0.0 && longitude == 0.0)

        val entity: EventEntity
        if (hasFix) {
            entity = EventEntity(
                type = type.code,
                latitude = latitude!!,
                longitude = longitude!!,
                timestamp = System.currentTimeMillis(),
                notes = notes,
                metadataJson = encodeMetadata(metadata),
                tripId = tripId
            )
        } else {
            val lastKnown = locationDao.getMostRecent()
            if (lastKnown != null) {
                // Última posición conocida + marca de posición vieja.
                val ageSeconds = (System.currentTimeMillis() - lastKnown.timestamp) / 1000
                entity = EventEntity(
                    type = type.code,
                    latitude = lastKnown.latitude,
                    longitude = lastKnown.longitude,
                    timestamp = System.currentTimeMillis(),
                    notes = notes,
                    metadataJson = encodeMetadata(
                        metadata + mapOf(
                            "staleLocation" to "true",
                            "staleAgeSeconds" to ageSeconds.toString()
                        )
                    ),
                    tripId = tripId
                )
            } else {
                // Sin ninguna posición previa: encolar hasta el primer fix.
                val id = dao.insert(
                    EventEntity(
                        type = type.code,
                        latitude = 0.0,   // placeholder — NUNCA se envía así
                        longitude = 0.0,
                        timestamp = System.currentTimeMillis(),
                        notes = notes,
                        metadataJson = encodeMetadata(metadata + ("queuedUntilFix" to "true")),
                        tripId = tripId,
                        awaitingFix = true
                    )
                )
                hasAwaitingFix = true
                return id
            }
        }

        val id = dao.insert(entity)
        if (networkUtil.isOnline() && identity.apiKey.isNotBlank()) {
            tryImmediateSend(identity, entity.copy(id = id))
        }
        return id
    }

    /**
     * Llamar en cada fix de GPS (TrackingService ya lo hace): si hay eventos
     * encolados sin posición, los completa con este fix y los despacha.
     * Con el cache en memoria, el costo en el camino caliente es cero.
     */
    suspend fun onFixAvailable(identity: UserIdentity, latitude: Double, longitude: Double) {
        if (hasAwaitingFix == null) {
            hasAwaitingFix = dao.countAwaitingFix() > 0
        }
        if (hasAwaitingFix != true) return

        val resolved = dao.resolveAwaitingFix(latitude, longitude)
        hasAwaitingFix = false
        if (resolved > 0 && networkUtil.isOnline() && identity.apiKey.isNotBlank()) {
            syncPending(identity)
        }
    }

    suspend fun syncPending(identity: UserIdentity): Result<Int> = runCatching {
        if (identity.apiKey.isBlank()) return@runCatching 0
        val pending = dao.getUnsynced(30)
        if (pending.isEmpty()) return@runCatching 0

        // Enviar cada evento como un punto de telemetría con Code
        val driverState = prefs.driverStateSnapshot()
        val payloads = pending.map { it.toHubPayload(identity, driverState) }
        val response = api.ingestBatch(identity.apiKey, payloads)
        when {
            response.isSuccessful -> dao.markSynced(pending.map { it.id })
            // I4 — regla §3.1: un 422 NO se reintenta (mismo criterio que
            // telemetría y fotos): cuarentena del lote para no trabar la cola.
            response.code() == 422 -> {
                response.errorBody()?.close()
                dao.markSynced(pending.map { it.id })
                android.util.Log.e(
                    "EventRepository",
                    "Lote de ${pending.size} eventos DESCARTADO por 422 (payload inválido para el backend). No se reintenta (§3.1)."
                )
            }
            else -> response.errorBody()?.close()
        }
        dao.purgeSynced(System.currentTimeMillis() - 7 * 86_400_000L)
        pending.size
    }

    fun getRecent(): Flow<List<EventEntity>> = dao.getRecent()
    fun getUnsyncedCount(): Flow<Int> = dao.getUnsyncedCount()

    private suspend fun tryImmediateSend(identity: UserIdentity, entity: EventEntity) {
        try {
            val driverState = prefs.driverStateSnapshot()
            val resp = api.ingest(identity.apiKey, entity.toHubPayload(identity, driverState))
            if (resp.isSuccessful) dao.markSynced(listOf(entity.id))
        } catch (_: Exception) { /* WorkManager reintentará */ }
    }

    private fun encodeMetadata(metadata: Map<String, String>): String =
        kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.serializer(), metadata
        )

    /** Convierte un EventEntity al formato HubRawPayload con el Code del evento. */
    private fun EventEntity.toHubPayload(identity: UserIdentity, driverState: String?) = HubRawPayload(
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
        speed = 0.0,
        course = 0.0,
        ignition = 1,
        battery = null,
        code = type,  // MOB_SOS, MOB_CHKPT, MOB_COMM, MOB_INCIDENT, etc.
        shipment = if (notes.isNotBlank()) notes else null,  // Notas van en Shipment
        tripId = tripId,
        driverState = driverState
    )
}
