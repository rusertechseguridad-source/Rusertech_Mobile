package com.rusertech.mobile.data.repository

import com.rusertech.mobile.data.local.db.EventDao
import com.rusertech.mobile.data.local.db.EventEntity
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
 * El EventEngine del backend los procesa vía el diccionario del avl_user.
 */
@Singleton
class EventRepository @Inject constructor(
    private val dao: EventDao,
    private val api: TrackingApi,
    private val networkUtil: NetworkUtil
) {
    suspend fun createEvent(
        type: EventType,
        identity: UserIdentity,
        latitude: Double,
        longitude: Double,
        notes: String = "",
        metadata: Map<String, String> = emptyMap()
    ): Long {
        val entity = EventEntity(
            type = type.code,
            latitude = latitude,
            longitude = longitude,
            timestamp = System.currentTimeMillis(),
            notes = notes,
            metadataJson = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.serializer(), metadata
            )
        )
        val id = dao.insert(entity)

        if (networkUtil.isOnline() && identity.apiKey.isNotBlank()) {
            tryImmediateSend(identity, entity.copy(id = id))
        }
        return id
    }

    suspend fun syncPending(identity: UserIdentity): Result<Int> = runCatching {
        if (identity.apiKey.isBlank()) return@runCatching 0
        val pending = dao.getUnsynced(30)
        if (pending.isEmpty()) return@runCatching 0

        // Enviar cada evento como un punto de telemetría con Code
        val payloads = pending.map { it.toHubPayload(identity) }
        val response = api.ingestBatch(identity.apiKey, payloads)
        if (response.isSuccessful) {
            dao.markSynced(pending.map { it.id })
            dao.purgeSynced(System.currentTimeMillis() - 7 * 86_400_000L)
        }
        pending.size
    }

    fun getRecent(): Flow<List<EventEntity>> = dao.getRecent()
    fun getUnsyncedCount(): Flow<Int> = dao.getUnsyncedCount()

    private suspend fun tryImmediateSend(identity: UserIdentity, entity: EventEntity) {
        try {
            val resp = api.ingest(identity.apiKey, entity.toHubPayload(identity))
            if (resp.isSuccessful) dao.markSynced(listOf(entity.id))
        } catch (_: Exception) { /* WorkManager reintentará */ }
    }

    /** Convierte un EventEntity al formato HubRawPayload con el Code del evento. */
    private fun EventEntity.toHubPayload(identity: UserIdentity) = HubRawPayload(
        asset = identity.plate,
        userAvl = identity.avlUserCode,
        date = Instant.ofEpochMilli(timestamp)
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_INSTANT),
        latitude = latitude.toString(),
        longitude = longitude.toString(),
        speed = "0",
        code = type,  // MOB_SOS, MOB_CHKPT, MOB_COMM, MOB_INCIDENT, etc.
        shipment = if (notes.isNotBlank()) notes else null,  // Notas van en Shipment
        sourceTag = "mobile_app"
    )
}
