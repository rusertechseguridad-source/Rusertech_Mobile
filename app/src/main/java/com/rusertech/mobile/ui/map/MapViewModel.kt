package com.rusertech.mobile.ui.map

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val mapApi: MapApi
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

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
        _currentLocation.value = GeoPoint(loc.latitude, loc.longitude)
        if (_destination.value != null && _routePoints.value.isEmpty()) {
            calculateRoute()
        }
    }

    private fun searchNominatim(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val results = mapApi.searchNominatim(query = query)
                _searchResults.value = results
            } catch (e: Exception) {
                // Ignore or log error
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
                // Ignore or log error
            }
        }
    }

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
