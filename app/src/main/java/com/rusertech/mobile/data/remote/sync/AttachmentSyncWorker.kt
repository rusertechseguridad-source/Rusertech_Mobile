package com.rusertech.mobile.data.remote.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.repository.AttachmentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AttachmentSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val attachmentRepository: AttachmentRepository,
    private val userPreferences: UserPreferences
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val identity = userPreferences.snapshot() ?: return Result.failure()
        return try {
            val uploaded = attachmentRepository.syncPending(identity)
            Log.i("AttachmentSyncWorker", "Subidas ${uploaded.getOrDefault(0)} fotos")
            if (uploaded.isFailure) {
                if (runAttemptCount < 5) Result.retry() else Result.failure()
            } else Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
