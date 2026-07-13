package com.rusertech.mobile.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rusertech.mobile.data.local.prefs.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var userPreferences: UserPreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (userPreferences.isTrackingSnapshot() && userPreferences.snapshot() != null) {
                    context.startForegroundService(
                        Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_START }
                    )
                    Log.i("BootReceiver", "Tracking auto-reanudado")
                }
            } catch (e: Exception) { Log.e("BootReceiver", "Fallo al reanudar", e) }
            finally { pendingResult.finish() }
        }
    }
}
