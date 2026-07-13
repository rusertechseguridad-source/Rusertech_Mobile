package com.rusertech.mobile.data.remote.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.repository.EventRepository
import com.rusertech.mobile.data.repository.LocationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val locationRepository: LocationRepository,
    private val eventRepository: EventRepository,
    private val userPreferences: UserPreferences
) : CoroutineWorker(appContext, params) {
    companion object { private const val TAG = "SyncWorker" }

    override suspend fun doWork(): Result {
        val identity = userPreferences.snapshot() ?: return Result.failure()
        return try {
            val events = eventRepository.syncPending(identity)
            val locations = locationRepository.syncPending(identity)
            val total = events.getOrDefault(0) + locations.getOrDefault(0)
            Log.i(TAG, "Sync ok — $total items")
            if (events.isFailure || locations.isFailure) {
                if (runAttemptCount < 5) Result.retry() else Result.failure()
            } else Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
