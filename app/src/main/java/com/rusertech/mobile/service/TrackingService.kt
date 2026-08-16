package com.rusertech.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.rusertech.mobile.MainActivity
import com.rusertech.mobile.R
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.remote.api.AuthEventBus
import com.rusertech.mobile.data.remote.sync.AttachmentSyncWorker
import com.rusertech.mobile.data.remote.sync.SyncWorker
import com.rusertech.mobile.data.repository.EventRepository
import com.rusertech.mobile.data.repository.LocationRepository
import com.rusertech.mobile.domain.model.DriverState
import com.rusertech.mobile.domain.model.EventType
import com.rusertech.mobile.domain.model.LocationPoint
import com.rusertech.mobile.domain.model.UserIdentity
import com.rusertech.mobile.util.BatteryUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {
    @Inject lateinit var locationManager: LocationManager
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var eventRepository: EventRepository
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var authEventBus: AuthEventBus

    private var serviceScope: CoroutineScope = newScope()
    private var identity: UserIdentity? = null
    private var collectJob: Job? = null
    private var authWatchJob: Job? = null
    private var lastBatteryAlert = 0L
    // Detección de paradas (FIX-10): una parada = un episodio.
    private var vehicleStoppedSince = 0L
    private var stopEventSent = false
    // Auto-resume (FIX-10): movimiento continuo con parada declarada.
    private var movingSince = 0L
    // Filtro de persistencia (antes vivía en LocationManager como filtro del
    // OS; ahora los puntos quietos SÍ llegan para la lógica de paradas, pero
    // no se persiste cada uno).
    private var lastSaved: Location? = null
    private var lastSavedAt = 0L

    companion object {
        const val ACTION_START = "com.rusertech.mobile.ACTION_START"
        const val ACTION_STOP = "com.rusertech.mobile.ACTION_STOP"
        private const val NOTIFICATION_ID = 1001
        private const val REVOKED_NOTIFICATION_ID = 1002
        private const val CREDENTIAL_WARNING_NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "rusertech_tracking_channel"
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
        private val _lastLocation = MutableStateFlow<Location?>(null)
        val lastLocation: StateFlow<Location?> = _lastLocation.asStateFlow()
        // Sección 10.1: true cuando el backend respondió 403 (acceso revocado a propósito)
        private val _accessRevoked = MutableStateFlow(false)
        val accessRevoked: StateFlow<Boolean> = _accessRevoked.asStateFlow()
        // Sección 10.1: true cuando el backend respondió 401 (API Key mal formada, NO detiene tracking)
        private val _credentialWarning = MutableStateFlow(false)
        val credentialWarning: StateFlow<Boolean> = _credentialWarning.asStateFlow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) { ACTION_START -> start(); ACTION_STOP -> stop() }
        return START_STICKY
    }

    private fun start() {
        if (!serviceScope.isActive) serviceScope = newScope()
        _accessRevoked.value = false
        _credentialWarning.value = false
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.service_notification_text_active)))

        // Sección 10.1: escucha revocación (403) y advertencia de credenciales (401)
        // durante toda la vida del servicio — son dos reacciones distintas.
        authWatchJob?.cancel()
        authWatchJob = serviceScope.launch {
            launch { authEventBus.accessRevoked.collect { onAccessRevoked() } }
            launch { authEventBus.credentialWarning.collect { onCredentialWarning() } }
        }

        collectJob?.cancel()
        collectJob = serviceScope.launch {
            identity = userPreferences.snapshot()
            if (identity == null) { stopSelf(); return@launch }
            userPreferences.setTracking(true)
            _isRunning.value = true
            // Estado limpio de detección por arranque (el objeto Service puede
            // reutilizarse entre start/stop).
            vehicleStoppedSince = 0L; stopEventSent = false; movingSince = 0L
            lastSaved = null; lastSavedAt = 0L
            locationManager.startUpdates()

            locationManager.locations.collect { location ->
                _lastLocation.value = location
                val id = identity ?: return@collect
                val currentTrip = userPreferences.activeTrip.firstOrNull()

                // Corrección eventos 0,0: si algún evento quedó encolado sin
                // posición, este fix lo completa y lo despacha.
                eventRepository.onFixAvailable(id, location.latitude, location.longitude)

                // Filtro de persistencia (antes era el filtro de desplazamiento
                // del OS): se guarda si se movió, si va a velocidad de marcha o
                // como heartbeat cada intervalo idle completo. Un vehículo
                // parado genera ~1 punto/min, no uno por callback.
                val now = System.currentTimeMillis()
                val moved = lastSaved?.let { it.distanceTo(location) >= LocationManager.SMALLEST_DISPLACEMENT_M } ?: true
                val shouldSave = moved ||
                    location.speed >= LocationManager.SPEED_THRESHOLD_MS ||
                    now - lastSavedAt >= LocationManager.INTERVAL_IDLE_MS
                if (shouldSave) {
                    val point = LocationPoint(
                        latitude = location.latitude, longitude = location.longitude,
                        accuracy = location.accuracy, speed = location.speed,
                        heading = if (location.hasBearing()) location.bearing else 0f,
                        altitude = location.altitude,
                        battery = BatteryUtil.getLevel(this@TrackingService),
                        timestamp = now,
                        tripId = currentTrip?.tripId
                    )
                    locationRepository.saveLocation(id, point)
                    lastSaved = location
                    lastSavedAt = now
                    updateNotification(point.speedKmh().toInt())
                }
                checkAutoEvents(location, id, currentTrip?.tripId)
            }
        }
        scheduleSyncWork()
    }

    /**
     * Sección 10.1 — 403: el operador desactivó el avl_user o revocó la API Key
     * a propósito. Detiene el tracking y deja una notificación explicando por qué,
     * en vez de simplemente morir en silencio (lo cual confundiría al conductor).
     */
    private fun onAccessRevoked() {
        _accessRevoked.value = true
        stop()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(REVOKED_NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Acceso desactivado")
            .setContentText("Tu operador desactivó el seguimiento. Contactalo si es un error.")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build())
    }

    /**
     * Sección 10.1 — 401: la API Key está mal formada, típicamente un error de
     * carga durante el registro. NO se detiene el tracking — los puntos se
     * siguen guardando en Room con el mismo mecanismo offline-first de siempre.
     * Solo se avisa para que alguien corrija la credencial.
     */
    private fun onCredentialWarning() {
        _credentialWarning.value = true
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(CREDENTIAL_WARNING_NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Revisá tus credenciales")
            .setContentText("La API Key no es válida. El tracking sigue activo y guardando localmente.")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build())
    }

    private suspend fun checkAutoEvents(location: Location, id: UserIdentity, tripId: String?) {
        val now = System.currentTimeMillis()
        val battery = BatteryUtil.getLevel(this)
        if (battery in 0..15 && now - lastBatteryAlert > 30 * 60_000L) {
            lastBatteryAlert = now
            eventRepository.createEvent(EventType.LOW_BATTERY, id, location.latitude, location.longitude,
                metadata = mapOf("battery_level" to battery.toString()), tripId = tripId)
        }

        val isMoving = location.speed >= LocationManager.SPEED_THRESHOLD_MS
        val driverState = DriverState.fromValue(userPreferences.driverStateSnapshot())

        if (!isMoving) {
            movingSince = 0L
            if (vehicleStoppedSince == 0L) vehicleStoppedSince = now

            // FIX-10 supresión inteligente: con una parada DECLARADA
            // (stopped_*) no hay anomalía — el MOB_STOP automático se calla.
            // MOB_STOP queda reservado para la parada NO declarada >5 min:
            // esa es la señal de seguridad.
            val declared = driverState?.isDeclaredStop == true
            if (!declared && !stopEventSent && now - vehicleStoppedSince > 5 * 60_000L) {
                eventRepository.createEvent(EventType.VEHICLE_STOP, id, location.latitude, location.longitude,
                    metadata = mapOf("stop_duration_seconds" to ((now - vehicleStoppedSince) / 1000).toString()), tripId = tripId)
                stopEventSent = true  // un solo MOB_STOP por episodio de parada
            }
        } else {
            vehicleStoppedSince = 0L
            stopEventSent = false

            // FIX-10 auto-resume: con parada declarada pero el vehículo
            // moviéndose a velocidad de marcha durante 3 minutos CONTINUOS,
            // el conductor olvidó reanudar → MOB_RESUME automático y vuelta
            // a en_route. Evita estados zombis.
            if (driverState?.isDeclaredStop == true) {
                if (movingSince == 0L) movingSince = now
                if (now - movingSince >= 3 * 60_000L) {
                    userPreferences.setDriverState(DriverState.EN_ROUTE.value)
                    eventRepository.createEvent(EventType.RESUME, id, location.latitude, location.longitude,
                        metadata = mapOf("auto" to "true"), tripId = tripId)
                    movingSince = 0L
                }
            } else {
                movingSince = 0L
            }
        }
    }

    private fun stop() {
        locationManager.stopUpdates()
        _isRunning.value = false; _lastLocation.value = null
        serviceScope.launch { userPreferences.setTracking(false) }
        collectJob?.cancel(); authWatchJob?.cancel(); serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    override fun onDestroy() {
        locationManager.stopUpdates()
        if (serviceScope.isActive) serviceScope.cancel()
        _isRunning.value = false; _lastLocation.value = null
        super.onDestroy()
    }

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun scheduleSyncWork() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("rusertech_sync", ExistingPeriodicWorkPolicy.KEEP, request)

        // Sección 29: sube fotos de carga pendientes en el mismo ciclo, worker separado
        // porque multipart no comparte el pipeline JSON de HubRawPayload.
        val attachmentRequest = PeriodicWorkRequestBuilder<AttachmentSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "rusertech_attachment_sync", ExistingPeriodicWorkPolicy.KEEP, attachmentRequest
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.service_channel_name), NotificationManager.IMPORTANCE_LOW)
            .apply { description = getString(R.string.service_channel_description); setShowBadge(false); enableLights(false); enableVibration(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(content: String): Notification {
        val openPending = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopPending = PendingIntent.getService(this, 1,
            Intent(this, TrackingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title)).setContentText(content)
            .setSmallIcon(R.drawable.ic_notification).setOngoing(true).setSilent(true)
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_notification, getString(R.string.service_notification_action_stop), stopPending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build()
    }

    private fun updateNotification(speedKmh: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification("${getString(R.string.service_notification_text_active)} · $speedKmh km/h"))
    }
}
