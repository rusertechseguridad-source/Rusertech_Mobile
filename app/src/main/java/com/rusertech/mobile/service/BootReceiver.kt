package com.rusertech.mobile.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rusertech.mobile.MainActivity
import com.rusertech.mobile.R
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.ui.common.PermissionHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var userPreferences: UserPreferences

    companion object {
        private const val TAG = "BootReceiver"
        private const val RESUME_NOTIFICATION_ID = 1004
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (userPreferences.isTrackingSnapshot() && userPreferences.snapshot() != null) {
                    // C1: un FGS de ubicación arrancado desde background SIN el
                    // permiso "Permitir todo el tiempo" no recibe NINGÚN fix en
                    // Android 11+ — aparenta trackear con cero datos, que es
                    // peor que no arrancar. En ese caso, en vez del servicio
                    // ciego se postea una notificación: al tocarla la app pasa
                    // a foreground y el tracking recupera acceso a ubicación.
                    if (PermissionHandler.hasBackgroundLocation(context)) {
                        context.startForegroundService(
                            Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_START }
                        )
                        Log.i(TAG, "Tracking auto-reanudado")
                    } else {
                        postResumeNotification(context)
                        Log.w(TAG, "Sin ACCESS_BACKGROUND_LOCATION: no se arranca un tracking ciego; se pide reanudar a mano")
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Fallo al reanudar", e) }
            finally { pendingResult.finish() }
        }
    }

    private fun postResumeNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // El receiver puede correr antes de que el servicio haya creado el
        // canal (primer arranque tras reboot): crearlo acá es idempotente.
        nm.createNotificationChannel(
            NotificationChannel(
                TrackingService.CHANNEL_ID,
                context.getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val openApp = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            RESUME_NOTIFICATION_ID,
            NotificationCompat.Builder(context, TrackingService.CHANNEL_ID)
                .setContentTitle(context.getString(R.string.resume_notification_title))
                .setContentText(context.getString(R.string.resume_notification_text))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }
}
