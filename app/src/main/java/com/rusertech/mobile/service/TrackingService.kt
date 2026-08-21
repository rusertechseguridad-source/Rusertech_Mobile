package com.rusertech.mobile.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
import com.rusertech.mobile.domain.model.OperationalConfig
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
    // Scope de APLICACIÓN, independiente del serviceScope: garantiza que las
    // escrituras críticas sobrevivan a la cancelación del servicio.
    @Inject @com.rusertech.mobile.di.ApplicationScope lateinit var appScope: CoroutineScope

    private var serviceScope: CoroutineScope = newScope()
    private var identity: UserIdentity? = null
    private var collectJob: Job? = null
    private var authWatchJob: Job? = null
    private var lastBatteryAlert = 0L
    // Configuración operativa vigente: snapshot tomado al INICIAR el tracking
    // (remota del último login o defaults locales). Un cambio de configuración
    // aplica en el próximo inicio, no en caliente — mantiene una sola fuente
    // de verdad durante toda la sesión de tracking.
    private var config = OperationalConfig()
    // Detección de paradas (FIX-10): una parada = un episodio.
    private var vehicleStoppedSince = 0L
    private var stopEventSent = false
    // Auto-resume (FIX-10): movimiento continuo con parada declarada.
    private var movingSince = 0L
    // I5: ancla del episodio de parada. Movimiento = velocidad ≥ umbral O
    // haberse alejado del ancla más que el radio — cubre fixes sin velocidad
    // (hasSpeed=false → speed 0.0) sin que el jitter de GPS estacionado
    // (que oscila alrededor del ancla, no se aleja) dispare falsos positivos.
    private var stopAnchor: Location? = null
    // Último punto del episodio de movimiento EN CURSO. Sobrevive a la
    // limpieza del ancla de parada: una vez iniciado el episodio (por señal
    // fuerte), el desplazamiento contra este punto lo SOSTIENE aunque los
    // fixes lleguen sin velocidad (hasSpeed=false, más de la mitad de un
    // turno real). Null = no hay episodio de movimiento.
    private var lastMovementPoint: Location? = null
    // Filtro de persistencia (antes vivía en LocationManager como filtro del
    // OS; ahora los puntos quietos SÍ llegan para la lógica de paradas, pero
    // no se persiste cada uno).
    private var lastSaved: Location? = null
    private var lastSavedAt = 0L

    companion object {
        const val ACTION_START = "com.rusertech.mobile.ACTION_START"
        const val ACTION_STOP = "com.rusertech.mobile.ACTION_STOP"
        // Alarma exacta del heartbeat (única API que ejecuta en Doze).
        const val ACTION_HEARTBEAT = "com.rusertech.mobile.ACTION_HEARTBEAT"
        private const val HEARTBEAT_REQUEST_CODE = 2
        // I5: radio del episodio de parada. 50 m > jitter típico de GPS
        // estacionado (incluso con precisión de 50 m aceptada por el filtro),
        // y un vehículo en marcha lo cruza en segundos aun a paso de hombre.
        private const val STOP_RADIUS_M = 50f
        private const val NOTIFICATION_ID = 1001
        private const val REVOKED_NOTIFICATION_ID = 1002
        private const val CREDENTIAL_WARNING_NOTIFICATION_ID = 1003
        // Público: BootReceiver postea su notificación de reanudación en este
        // mismo canal (C1) y puede correr antes de que el servicio exista.
        const val CHANNEL_ID = "rusertech_tracking_channel"
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
        // B4: distancia acumulada de la sesión, en metros — suma de
        // distancias entre puntos consecutivos PERSISTIDOS (los mismos que
        // van a Room). Se reinicia al iniciar el tracking; el cálculo
        // definitivo por viaje vive en el backend, esto es informativo.
        private val _sessionDistanceM = MutableStateFlow(0f)
        val sessionDistanceM: StateFlow<Float> = _sessionDistanceM.asStateFlow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
            ACTION_HEARTBEAT -> onHeartbeatAlarm()
        }
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
            val startIdentity = userPreferences.snapshot()
            identity = startIdentity
            if (startIdentity == null) { stopSelf(); return@launch }
            userPreferences.setTracking(true)
            _isRunning.value = true
            // Configuración operativa: del backend (último login) o defaults.
            config = userPreferences.operationalConfigSnapshot()
            // Estado limpio de detección por arranque (el objeto Service puede
            // reutilizarse entre start/stop). lastSavedAt arranca en "ahora":
            // es la línea base del reloj de silencio del heartbeat.
            vehicleStoppedSince = 0L; stopEventSent = false; movingSince = 0L
            stopAnchor = null; lastMovementPoint = null; lastSaved = null
            lastSavedAt = System.currentTimeMillis()
            _sessionDistanceM.value = 0f  // B4: distancia por sesión
            locationManager.startUpdates(config)
            scheduleHeartbeatAlarm()

            locationManager.locations.collect { location ->
                _lastLocation.value = location
                val id = identity ?: return@collect
                val currentTrip = userPreferences.activeTrip.firstOrNull()

                // Corrección eventos 0,0: si algún evento quedó encolado sin
                // posición, este fix lo completa y lo despacha.
                eventRepository.onFixAvailable(id, location.latitude, location.longitude)

                // Filtro de persistencia (antes era el filtro de desplazamiento
                // del OS): se guarda si se movió, si va a velocidad de marcha o
                // al completarse un intervalo idle sin guardar. Un vehículo
                // parado genera ~1 punto/min, no uno por callback.
                val now = System.currentTimeMillis()
                val moved = lastSaved?.let { it.distanceTo(location) >= config.minDisplacementMeters } ?: true
                val shouldSave = moved ||
                    (location.hasSpeed() && location.speed >= LocationManager.SPEED_THRESHOLD_MS) ||
                    now - lastSavedAt >= config.intervalIdleMs
                if (shouldSave) {
                    // B4: acumular distancia entre puntos persistidos consecutivos.
                    lastSaved?.let { prev ->
                        if (moved) _sessionDistanceM.value += prev.distanceTo(location)
                    }
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
                    // Cada punto persistido corre la alarma del heartbeat:
                    // semántica de reloj de silencio, no de tick ciego.
                    scheduleHeartbeatAlarm()
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

        // I5: la velocidad solo cuenta con hasSpeed() — sin esa verificación,
        // un fix sin velocidad lee 0.0 y más de la mitad de los fixes de un
        // turno real llegan así. Tres señales de movimiento:
        //  1. velocidad VÁLIDA sobre umbral (inicia y sostiene episodio);
        //  2. salir del radio del ancla de parada (inicia episodio — el
        //     jitter estacionado oscila alrededor del ancla sin salir);
        //  3. desplazamiento contra el último punto del episodio EN CURSO
        //     (solo SOSTIENE: lastMovementPoint es null sin episodio, así
        //     que el jitter estacionado no puede iniciar movimiento ni
        //     bloquear el MOB_STOP). Sin esta tercera señal, el ancla se
        //     limpia al primer fix en movimiento y los fixes sin velocidad
        //     posteriores cortan el episodio: el contador continuo del
        //     auto-resume se reinicia y los minutos no se completan nunca.
        val fastEnough = location.hasSpeed() && location.speed >= LocationManager.SPEED_THRESHOLD_MS
        val leftStopRadius = stopAnchor?.let { it.distanceTo(location) > STOP_RADIUS_M } ?: false
        val sustainedMovement = lastMovementPoint?.let {
            it.distanceTo(location) >= config.minDisplacementMeters
        } ?: false
        val isMoving = fastEnough || leftStopRadius || sustainedMovement
        // La referencia sobrevive a la limpieza del ancla y muere con el episodio.
        lastMovementPoint = if (isMoving) location else null
        val driverState = DriverState.fromValue(userPreferences.driverStateSnapshot())

        if (!isMoving) {
            movingSince = 0L
            if (vehicleStoppedSince == 0L) {
                vehicleStoppedSince = now
                stopAnchor = location  // acá empieza el episodio de parada
            }

            // FIX-10 supresión inteligente: con una parada DECLARADA
            // (stopped_*) no hay anomalía — el MOB_STOP automático se calla.
            // MOB_STOP queda reservado para la parada NO declarada más larga
            // que el umbral configurado: esa es la señal de seguridad.
            val declared = driverState?.isDeclaredStop == true
            if (!declared && !stopEventSent && now - vehicleStoppedSince > config.stopThresholdMs) {
                eventRepository.createEvent(EventType.VEHICLE_STOP, id, location.latitude, location.longitude,
                    metadata = mapOf("stop_duration_seconds" to ((now - vehicleStoppedSince) / 1000).toString()), tripId = tripId)
                stopEventSent = true  // un solo MOB_STOP por episodio de parada
            }
        } else {
            vehicleStoppedSince = 0L
            stopEventSent = false
            stopAnchor = null  // terminó el episodio de parada

            // FIX-10 auto-resume: con parada declarada pero el vehículo
            // moviéndose a velocidad de marcha durante autoResumeMinutes
            // CONTINUOS, el conductor olvidó reanudar → MOB_RESUME automático
            // y vuelta a en_route. Evita estados zombis.
            if (driverState?.isDeclaredStop == true) {
                if (movingSince == 0L) movingSince = now
                if (now - movingSince >= config.autoResumeMs) {
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

    /**
     * Heartbeat de presencia (con el tracking activo, nunca más de
     * heartbeatIntervalMinutes sin al menos un punto persistido).
     *
     * Con fixes fluyendo, el filtro de persistencia ya guarda al menos un
     * punto por intervalo idle y la alarma se corre antes de disparar. El
     * heartbeat cubre el caso restante: ningún fix pasa el filtro de
     * precisión durante minutos (interior, garaje, cañón urbano) y la
     * plataforma dejaría de ver al vehículo sin saber si es señal o abandono.
     *
     * POR QUÉ AlarmManager y no delay(): delay() no despierta el CPU. Con el
     * teléfono quieto y la pantalla apagada, Doze suspende el procesador y
     * el timer queda congelado hasta que el dispositivo despierte por otra
     * razón — que es exactamente el escenario del heartbeat (conductor
     * esperando en un depósito). El Foreground Service mantiene vivo el
     * PROCESO, no impide la suspensión del CPU. setExactAndAllowWhileIdle es
     * la única API con ejecución garantizada en Doze.
     *
     * Límites conocidos, NO son bugs:
     *  - En Doze profundo el sistema limita las alarmas exactas a ~1 cada
     *    9 minutos por app: con el default de 5 minutos, el intervalo real
     *    puede estirarse hasta ese ritmo. Aceptable y muy superior a un
     *    timer congelado.
     *  - Sin el permiso SCHEDULE_EXACT_ALARM (revocable por el usuario en
     *    Android 12+, denegado por defecto al instalar en 14+), se cae a
     *    setAndAllowWhileIdle: inexacta pero también ejecuta en Doze, con
     *    ventanas de batching del sistema.
     *
     * NO usar un wakelock permanente como alternativa: resolvería el timer a
     * costa de la batería, y el consumo por hora medido en campo es una de
     * las mejores cualidades del producto.
     */
    private fun heartbeatPendingIntent(): PendingIntent =
        PendingIntent.getService(
            this, HEARTBEAT_REQUEST_CODE,
            Intent(this, TrackingService::class.java).apply { action = ACTION_HEARTBEAT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * (Re)programa la alarma para el próximo vencimiento del reloj de
     * silencio. Mismo PendingIntent (mismo requestCode): cada set() PISA la
     * alarma anterior — se llama después de cada punto persistido y de cada
     * heartbeat, así la alarma solo dispara tras un intervalo completo SIN
     * puntos.
     */
    private fun scheduleHeartbeatAlarm(fromMs: Long = lastSavedAt) {
        // Piso de 1 s en el futuro: una base vieja (p. ej. sin posición que
        // reportar) no puede producir una alarma que dispare en loop.
        val at = maxOf(fromMs + config.heartbeatIntervalMs, System.currentTimeMillis() + 1_000L)
        val am = getSystemService(AlarmManager::class.java)
        val pi = heartbeatPendingIntent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelHeartbeatAlarm() {
        getSystemService(AlarmManager::class.java).cancel(heartbeatPendingIntent())
    }

    /**
     * La alarma venció. Wakelock corto con timeout: la alarma despierta el
     * CPU solo para entregar el intent, y sin sostenerlo unos segundos el
     * Room insert / intento de envío podrían quedar suspendidos a mitad de
     * camino. 30 s de tope — jamás un wakelock permanente.
     */
    private fun onHeartbeatAlarm() {
        if (!_isRunning.value) {
            // Alarma rezagada tras un stop (o el sistema recreó el servicio
            // solo para entregarla): no hay nada que emitir ni reprogramar.
            stopSelf()
            return
        }
        val id = identity ?: return
        val wl = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rusertech:heartbeat")
        wl.acquire(30_000L)
        serviceScope.launch {
            try {
                val due = System.currentTimeMillis() - lastSavedAt >= config.heartbeatIntervalMs
                if (due && !emitHeartbeat(id)) {
                    // Sin ninguna posición que reportar (instalación que jamás
                    // obtuvo un fix): próxima ventana completa desde ahora.
                    scheduleHeartbeatAlarm(fromMs = System.currentTimeMillis())
                } else {
                    // emitHeartbeat corrió lastSavedAt; si no estaba vencida
                    // (un punto llegó en el medio), se reprograma desde él.
                    scheduleHeartbeatAlarm()
                }
            } finally {
                if (wl.isHeld) wl.release()
            }
        }
    }

    /** @return false si no existe ninguna posición conocida para reportar. */
    private suspend fun emitHeartbeat(id: UserIdentity): Boolean {
        val now = System.currentTimeMillis()
        val tripId = userPreferences.activeTrip.firstOrNull()?.tripId

        // Fuente: último punto persistido en memoria; si el servicio recién
        // arranca sin fix, la última posición que quedó en Room.
        val source = lastSaved ?: _lastLocation.value
        val point = if (source != null) {
            LocationPoint(
                latitude = source.latitude, longitude = source.longitude,
                accuracy = source.accuracy,
                // Sin fix nuevo no hay velocidad ni rumbo medibles.
                speed = 0f, heading = 0f,
                altitude = source.altitude,
                battery = BatteryUtil.getLevel(this),
                // timestamp = ahora: el dedupe del backend trabaja por
                // timestamp, así el heartbeat nunca colisiona con el punto
                // original del que toma las coordenadas.
                timestamp = now,
                tripId = tripId,
                isHeartbeat = true,
                fixAgeSeconds = (now - source.time) / 1000
            )
        } else {
            val lastKnown = locationRepository.lastKnownPoint() ?: return false
            LocationPoint(
                latitude = lastKnown.latitude, longitude = lastKnown.longitude,
                accuracy = lastKnown.accuracy, speed = 0f, heading = 0f,
                altitude = lastKnown.altitude,
                battery = BatteryUtil.getLevel(this),
                timestamp = now,
                tripId = tripId,
                isHeartbeat = true,
                fixAgeSeconds = (now - lastKnown.timestamp) / 1000
            )
        }
        locationRepository.saveLocation(id, point)
        lastSavedAt = now
        return true
    }

    private fun stop() {
        locationManager.stopUpdates()
        _isRunning.value = false; _lastLocation.value = null
        // I1 SIN runBlocking. La garantía de I1 es que esta escritura NO
        // muere con serviceScope.cancel() — y la da el scope de APLICACIÓN:
        // no es hijo del serviceScope, la cancelación de abajo no lo toca, y
        // el proceso sigue vivo tras stopSelf().
        //
        // ⚠️ NO volver esto a runBlocking: stop() corre en el hilo principal
        // del servicio, y bloquearlo hasta el fsync de DataStore produce ANR
        // en dispositivos con almacenamiento lento (reproducido en campo).
        // Si hace falta garantía de ejecución, la respuesta es un scope que
        // sobreviva — nunca bloquear el main.
        appScope.launch {
            userPreferences.setTracking(false)
            // Detener CIERRA la sesión de rastro: lo anterior a este instante
            // deja de dibujarse en el mapa. Solo presentación local — los
            // puntos ya enviados quedan en telemetry como histórico. Los
            // reinicios del servicio (reboot, kill del OEM) no pasan por acá:
            // el rastro de un turno sobrevive a esas muertes.
            userPreferences.setTrailClearedAt(System.currentTimeMillis())
        }
        cancelHeartbeatAlarm()
        collectJob?.cancel(); authWatchJob?.cancel()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    override fun onDestroy() {
        locationManager.stopUpdates()
        if (serviceScope.isActive) serviceScope.cancel()
        // La alarma del heartbeat NO se cancela acá a propósito: onDestroy
        // también corre cuando el sistema mata el servicio con el tracking
        // vigente, y en ese caso la alarma pendiente es inofensiva (el
        // handler la ignora y se auto-detiene si el tracking no corre).
        // stop() —la detención deliberada— sí la cancela.
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
