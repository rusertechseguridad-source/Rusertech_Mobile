package com.rusertech.mobile.ui.tracking

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.remote.api.MapApi
import com.rusertech.mobile.data.repository.EventRepository
import com.rusertech.mobile.data.repository.LocationRepository
import com.rusertech.mobile.data.repository.TripRepository
import com.rusertech.mobile.data.repository.UserRepository
import com.rusertech.mobile.domain.model.DriverState
import com.rusertech.mobile.service.TrackingService
import com.rusertech.mobile.util.NetworkUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val locationRepository: LocationRepository,
    private val tripRepository: TripRepository,
    private val eventRepository: EventRepository,
    private val prefs: UserPreferences,
    private val networkUtil: NetworkUtil,
    private val mapApi: MapApi,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val userIdentity = userRepository.userIdentity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeTrip = prefs.activeTrip
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // FIX-10: estado operativo del conductor, siempre visible en la pantalla.
    // null (sin declarar) se muestra como "En viaje".
    val driverState = prefs.driverState
        .map { DriverState.fromValue(it) ?: DriverState.EN_ROUTE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DriverState.EN_ROUTE)

    val isTracking = TrackingService.isRunning
    // C1: intención de trackear persistida en DataStore. Si es true pero el
    // servicio no corre (reboot sin permiso de background → notificación),
    // la pantalla reanuda sola al pasar a foreground.
    val trackingIntended = userRepository.isTracking
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val lastLocation = TrackingService.lastLocation
    val accessRevoked = TrackingService.accessRevoked  // Sección 10.1 — 403
    val credentialWarning = TrackingService.credentialWarning  // Sección 10.1 — 401
    val isOnline = networkUtil.isOnlineFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val pendingCount = locationRepository.getUnsyncedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun startTracking() {
        context.startForegroundService(
            Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_START }
        )
    }

    // Fix #5: stopService en vez de startService
    fun stopTracking() {
        context.startService(
            Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_STOP }
        )
        context.stopService(Intent(context, TrackingService::class.java))
    }

    /**
     * FIX-10: el conductor declara su estado operativo desde el bottom sheet.
     * Cada transición persiste el estado en DataStore y emite el evento MOB_
     * correspondiente con la posición actual y el tripId si hay viaje.
     * Disponible también en Tracking Libre (los eventos van sin tripId).
     */
    fun declareState(newState: DriverState, metadata: Map<String, String> = emptyMap()) {
        viewModelScope.launch {
            val current = driverState.value
            if (newState == current) return@launch

            val identity = userIdentity.value ?: return@launch
            prefs.setDriverState(newState.value)

            // Sin fix de GPS se pasa null: EventRepository usa la última
            // posición conocida (staleLocation) o encola hasta el primer fix.
            // B6: el detalle (p. ej. lugar de la parada sanitaria) viaja en
            // metadata — el diccionario de códigos MOB_ no crece.
            val location = lastLocation.value
            eventRepository.createEvent(
                type = newState.transitionEvent,
                identity = identity,
                latitude = location?.latitude,
                longitude = location?.longitude,
                metadata = metadata,
                tripId = activeTrip.value?.tripId
            )
        }
    }

    // B4: distancia acumulada de la sesión (metros), desde el servicio.
    val sessionDistanceM = TrackingService.sessionDistanceM

    // ------------------------------------------------------------------
    // Dirección legible de la posición actual ("Ubicación actual").
    // ------------------------------------------------------------------

    private companion object {
        const val TAG = "TrackingViewModel"
        // Re-resolver solo si el vehículo se movió más que esto: la dirección
        // no cambia cada 10 m y cada resolución cuesta red o CPU.
        const val ADDRESS_REFRESH_DISPLACEMENT_M = 200f
        // Separación mínima entre INTENTOS, haya éxito o fallo. Con el
        // Geocoder nativo ausente, cada intento puede terminar en Nominatim:
        // sin este techo, un vehículo en ruta resolvería cada ~8 s (200 m a
        // 90 km/h) y ese volumen sostenido termina en ban de IP. Techo
        // resultante: ≤60 llamadas/hora.
        const val ADDRESS_MIN_ATTEMPT_SPACING_MS = 60_000L
        // Nominatim banea IPs que superan ~1 req/s: separación mínima dura
        // entre llamadas al fallback.
        const val NOMINATIM_MIN_SPACING_MS = 1_100L
    }

    private val _currentAddress = MutableStateFlow<String?>(null)
    /** Dirección legible de la última posición, o null → la UI muestra coordenadas. */
    val currentAddress = _currentAddress.asStateFlow()

    private var addressResolvedFor: Location? = null
    private var addressAttemptAt = 0L
    private var addressResolving = false
    private var lastNominatimAt = 0L

    init {
        viewModelScope.launch {
            TrackingService.lastLocation.collect { loc ->
                if (loc != null) maybeResolveAddress(loc)
            }
        }
    }

    /**
     * Resuelve la dirección con el Geocoder NATIVO de Android (sin costo de
     * cuota) y usa Nominatim /reverse solo como fallback espaciado — el
     * volumen de un tracking (un fix cada 5 s) contra Nominatim directo
     * termina en ban de IP. Cache por desplazamiento: se re-resuelve recién
     * al moverse ADDRESS_REFRESH_DISPLACEMENT_M. Todo fuera del hilo
     * principal; el fallo es silencioso (la UI cae a coordenadas).
     */
    private fun maybeResolveAddress(loc: Location) {
        if (addressResolving) return
        val now = System.currentTimeMillis()
        val resolvedFor = addressResolvedFor
        val movedEnough = resolvedFor == null ||
            resolvedFor.distanceTo(loc) >= ADDRESS_REFRESH_DISPLACEMENT_M
        if (!movedEnough) return
        if (now - addressAttemptAt < ADDRESS_MIN_ATTEMPT_SPACING_MS) return

        addressResolving = true
        addressAttemptAt = now
        viewModelScope.launch {
            val address = resolveAddress(loc)
            if (address != null) {
                _currentAddress.value = address
                addressResolvedFor = loc
            }
            addressResolving = false
        }
    }

    private suspend fun resolveAddress(loc: Location): String? = withContext(Dispatchers.IO) {
        // 1) Geocoder nativo. La variante síncrona está deprecada en API 33 a
        //    favor del listener, pero sigue funcionando en todas las versiones
        //    y acá ya corre en IO — el motivo de la deprecación (bloquear el
        //    main) no aplica.
        val native = runCatching {
            if (Geocoder.isPresent()) {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault())
                    .getFromLocation(loc.latitude, loc.longitude, 1)
                    ?.firstOrNull()
                    ?.let { a ->
                        val street = listOfNotNull(a.thoroughfare, a.subThoroughfare)
                            .joinToString(" ").ifBlank { null }
                        // País solo en cruce de frontera: en operación
                        // doméstica es ruido permanente; fuera del país del
                        // tenant (bloque MARCA de strings.xml) es el dato
                        // más importante de la dirección.
                        val tenantCountry = context
                            .getString(com.rusertech.mobile.R.string.tenant_country_code)
                            .trim()
                        val country = a.countryCode
                            ?.takeIf { tenantCountry.isNotBlank() && !it.equals(tenantCountry, ignoreCase = true) }
                            ?.let { a.countryName ?: it }
                        listOfNotNull(street, a.subLocality, a.locality, country)
                            .distinct().joinToString(", ").ifBlank { null }
                            ?: a.getAddressLine(0)
                    }
            } else null
        }.getOrNull()
        if (native != null) return@withContext native

        // 2) Fallback Nominatim, con separación mínima dura entre llamadas.
        val now = System.currentTimeMillis()
        if (now - lastNominatimAt < NOMINATIM_MIN_SPACING_MS) return@withContext null
        lastNominatimAt = now
        runCatching {
            mapApi.reverseNominatim(lat = loc.latitude, lon = loc.longitude)
                .display_name
                // display_name viene kilométrico (hasta país y CP): con los
                // tres primeros segmentos alcanza para orientar al conductor.
                .split(",").take(3).joinToString(",").trim().ifBlank { null }
        }.onFailure {
            android.util.Log.w(TAG, "Reverse geocoding falló (fallback Nominatim)", it)
        }.getOrNull()
    }

    fun completeTrip(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val identity = userIdentity.value ?: return@launch
            val trip = activeTrip.value ?: return@launch

            stopTracking()

            // FIX-2: cierre offline-tolerante. El repositorio limpia el estado
            // local YA (ActiveTrip + driver_state) y, si la red falla, deja el
            // cierre en PENDING_TRIP_CLOSE para que SyncWorker lo reintente.
            tripRepository.completeTrip(identity, trip.tripId)
            onSuccess()
        }
    }

    fun logout() {
        stopTracking()
        viewModelScope.launch { userRepository.logout() }
    }
}
