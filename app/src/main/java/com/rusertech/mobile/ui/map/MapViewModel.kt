package com.rusertech.mobile.ui.map

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rusertech.mobile.data.local.db.EventDao
import com.rusertech.mobile.data.local.db.EventEntity
import com.rusertech.mobile.data.local.db.LocationDao
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.data.remote.api.MapApi
import com.rusertech.mobile.data.remote.api.NominatimResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val mapApi: MapApi,
    private val locationDao: LocationDao,
    private val eventDao: EventDao,
    private val prefs: UserPreferences
) : ViewModel() {

    private companion object { const val TAG = "MapViewModel" }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Estado de error visible para el conductor. Un error que la pantalla no
    // muestra y el log no registra no existe para nadie: acá siempre se
    // loguea Y se expone, con la causa diferenciada (sin red / timeout /
    // servicio) para que la reacción del conductor sea la correcta.
    private val _mapError = MutableStateFlow<String?>(null)
    val mapError = _mapError.asStateFlow()

    /**
     * Mensaje según la causa real del fallo: sin red no tiene sentido
     * reintentar ya; un timeout sí; un error del servicio es ajeno al
     * teléfono. UnknownHost/Connect = sin salida a internet (DNS o socket
     * rechazado); SocketTimeout = red viva pero lenta o servicio saturado.
     */
    private fun mapErrorMessage(e: Exception, action: String): String = when (e) {
        is java.net.UnknownHostException,
        is java.net.ConnectException -> "Sin conexión a internet: no se pudo $action"
        is java.net.SocketTimeoutException -> "La red está lenta: no se pudo $action, reintentá"
        else -> "El servicio de mapas falló al $action, probá más tarde"
    }

    private val _searchResults = MutableStateFlow<List<NominatimResponse>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _currentLocation = MutableStateFlow<GeoPoint?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    private val _destination = MutableStateFlow<GeoPoint?>(null)
    val destination = _destination.asStateFlow()

    private val _routePoints = MutableStateFlow<List<GeoPoint>>(emptyList())
    val routePoints = _routePoints.asStateFlow()

    // ------------------------------------------------------------------
    // B3: rastro del recorrido desde Room (sin backend) + eventos sobre él.
    // ------------------------------------------------------------------
    private val _showTrail = MutableStateFlow(true)
    val showTrail = _showTrail.asStateFlow()

    private val _trailPoints = MutableStateFlow<List<TrailPoint>>(emptyList())
    val trailPoints = _trailPoints.asStateFlow()

    private val _trailEvents = MutableStateFlow<List<EventEntity>>(emptyList())
    val trailEvents = _trailEvents.asStateFlow()

    fun toggleTrail() {
        _showTrail.value = !_showTrail.value
        if (_showTrail.value) loadTrail()
    }

    /**
     * Carga el rastro: desde el inicio del viaje activo si lo hay, o las
     * últimas 12 h de puntos (Room los retiene 24 h tras sincronizar).
     * El cierre de sesión manda sobre ambos: nada anterior al último
     * "Finalizar seguimiento" se dibuja — la sesión nueva arranca limpia.
     * Solo presentación: los puntos ya enviados siguen en el servidor.
     */
    fun loadTrail() {
        viewModelScope.launch {
            val trip = prefs.activeTrip.first()
            val cleared = prefs.trailClearedAt.first()
            val base = trip?.startedAt ?: (System.currentTimeMillis() - 12 * 3_600_000L)
            val since = maxOf(base, cleared)
            _trailPoints.value = locationDao.getTrackSince(since)
                .map { TrailPoint(GeoPoint(it.latitude, it.longitude), it.timestamp) }
            _trailEvents.value = eventDao.getEventsSince(since)
        }
    }

    init {
        loadTrail()
    }

    init {
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            _searchQuery.debounce(1000).filter { it.length > 2 }.collect { query ->
                searchNominatim(query)
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
        }
    }

    fun updateCurrentLocation(loc: Location) {
        val point = GeoPoint(loc.latitude, loc.longitude)
        _currentLocation.value = point
        // B3: el rastro crece en vivo mientras la pantalla está abierta
        // (los puntos nuevos aún no están consultados de Room).
        val last = _trailPoints.value.lastOrNull()
        if (last == null || last.point.distanceToAsDouble(point) >= 10.0) {
            _trailPoints.value = _trailPoints.value + TrailPoint(point, System.currentTimeMillis())
        }
        if (_destination.value != null && _routePoints.value.isEmpty()) {
            calculateRoute()
        }
    }

    private fun searchNominatim(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _mapError.value = null
            try {
                val results = mapApi.searchNominatim(query = query)
                _searchResults.value = results
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Búsqueda de dirección falló: \"$query\"", e)
                _mapError.value = mapErrorMessage(e, "buscar la dirección")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setDestination(lat: Double, lon: Double) {
        _destination.value = GeoPoint(lat, lon)
        _searchResults.value = emptyList() // Hide search results
        _searchQuery.value = ""
        calculateRoute()
    }

    fun clearDestination() {
        _destination.value = null
        _routePoints.value = emptyList()
        _searchResults.value = emptyList()
    }

    private fun calculateRoute() {
        val start = _currentLocation.value ?: return
        val end = _destination.value ?: return
        
        viewModelScope.launch {
            try {
                // OSRM coordinates are lon,lat
                val url = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full"
                val response = mapApi.getRoute(url)
                val encodedGeometry = response.routes?.firstOrNull()?.geometry
                if (encodedGeometry != null) {
                    _routePoints.value = decodePolyline(encodedGeometry)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Cálculo de ruta falló", e)
                _mapError.value = mapErrorMessage(e, "calcular la ruta")
            }
        }
    }

    /** Punto del rastro con su instante: el mapa muestra fecha/hora por tramo. */
    data class TrailPoint(val point: GeoPoint, val timestamp: Long)

    private fun decodePolyline(encoded: String): List<GeoPoint> {
        val poly = ArrayList<GeoPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = GeoPoint((lat / 1E5), (lng / 1E5))
            poly.add(p)
        }
        return poly
    }
}
