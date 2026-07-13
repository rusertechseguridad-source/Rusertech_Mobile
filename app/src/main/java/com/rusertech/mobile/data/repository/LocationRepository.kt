package com.rusertech.mobile.data.repository

import com.rusertech.mobile.data.local.db.LocationDao
import com.rusertech.mobile.data.local.db.LocationEntity
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
    private val networkUtil: NetworkUtil
) {
    /** Siempre persiste localmente primero. Intento inmediato si hay red. */
    suspend fun saveLocation(identity: UserIdentity, point: LocationPoint) {
        val entity = LocationEntity(
            latitude = point.latitude, longitude = point.longitude,
            accuracy = point.accuracy, speed = point.speed,
            heading = point.heading, altitude = point.altitude,
            battery = point.battery, timestamp = point.timestamp
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

        val payloads = pending.map { it.toHubPayload(identity) }
        val response = api.ingestBatch(identity.apiKey, payloads)
        if (!response.isSuccessful) {
            throw IllegalStateException("Sync falló con HTTP ${response.code()}")
        }

        dao.markSynced(pending.map { it.id })
        dao.purgeSynced(System.currentTimeMillis() - 86_400_000L)
        pending.size
    }

    fun getUnsyncedCount(): Flow<Int> = dao.getUnsyncedCount()

    private suspend fun tryImmediateSend(identity: UserIdentity, entity: LocationEntity) {
        try {
            val resp = api.ingest(identity.apiKey, entity.toHubPayload(identity))
            if (resp.isSuccessful) dao.markSynced(listOf(entity.id))
        } catch (_: Exception) { /* WorkManager reintentará */ }
    }

    /** Convierte una LocationEntity al formato HubRawPayload del backend web. */
    private fun LocationEntity.toHubPayload(identity: UserIdentity) = HubRawPayload(
        asset = identity.plate,
        userAvl = identity.avlUserCode,
        date = Instant.ofEpochMilli(timestamp)
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_INSTANT),
        latitude = latitude.toString(),
        longitude = longitude.toString(),
        speed = (speed * 3.6f).toString(),  // m/s → km/h para el backend
        course = heading.toInt().toString(),
        code = null,  // Sin evento — es telemetría pura
        battery = battery.toString(),
        altitude = altitude.toString(),
        sourceTag = "mobile_app"
    )
}
