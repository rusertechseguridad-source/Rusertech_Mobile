package com.rusertech.mobile.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rusertech.mobile.ui.theme.*
import com.rusertech.mobile.service.TrackingService
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(onBack: () -> Unit, viewModel: MapViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val routePoints by viewModel.routePoints.collectAsStateWithLifecycle()

    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Subscribe to location updates
    LaunchedEffect(Unit) {
        TrackingService.lastLocation.collect { loc ->
            if (loc != null) viewModel.updateCurrentLocation(loc)
        }
    }

    var isMapCentered by remember { mutableStateOf(false) }
    var mapInstance by remember { mutableStateOf<MapView?>(null) }
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()

    LaunchedEffect(currentLocation) {
        if (!isMapCentered && currentLocation != null) {
            mapInstance?.controller?.animateTo(currentLocation, 16.0, 1000L)
            isMapCentered = true
        }
    }

    Box(Modifier.fillMaxSize().background(DeepSpaceTop).systemBarsPadding()) {
        // Map View (Bottom Layer)
        Box(Modifier.fillMaxSize()) {
            if (hasLocationPermission) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            mapInstance = this
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(15.0)
                            
                            val locationOverlay = MyLocationNewOverlay(this)
                            locationOverlay.enableMyLocation()
                            locationOverlay.enableFollowLocation()
                            
                            // Custom TechGlowCyan icons
                            val cyan = android.graphics.Color.parseColor("#00E5FF")
                            
                            val personBmp = android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888)
                            android.graphics.Canvas(personBmp).apply {
                                drawCircle(20f, 20f, 15f, android.graphics.Paint().apply { color = cyan; isAntiAlias = true; style = android.graphics.Paint.Style.FILL })
                                drawCircle(20f, 20f, 15f, android.graphics.Paint().apply { color = android.graphics.Color.BLACK; isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeWidth = 2f })
                            }
                            
                            val arrowBmp = android.graphics.Bitmap.createBitmap(60, 60, android.graphics.Bitmap.Config.ARGB_8888)
                            android.graphics.Canvas(arrowBmp).apply {
                                val path = android.graphics.Path().apply { moveTo(30f, 0f); lineTo(60f, 60f); lineTo(30f, 45f); lineTo(0f, 60f); close() }
                                drawPath(path, android.graphics.Paint().apply { color = cyan; isAntiAlias = true; style = android.graphics.Paint.Style.FILL })
                                drawPath(path, android.graphics.Paint().apply { color = android.graphics.Color.BLACK; isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeWidth = 2f })
                            }
                            
                            locationOverlay.setPersonIcon(personBmp)
                            locationOverlay.setDirectionArrow(personBmp, arrowBmp)
                            
                            overlays.add(locationOverlay)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { map ->
                        val myLoc = map.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
                        map.overlays.clear()
                        if (myLoc != null) map.overlays.add(myLoc)

                        destination?.let { dest ->
                            val marker = Marker(map)
                            marker.position = dest
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            marker.title = "Destino"
                            map.overlays.add(marker)
                        }

                        if (routePoints.isNotEmpty()) {
                            val polyline = Polyline(map)
                            polyline.setPoints(routePoints)
                            polyline.color = android.graphics.Color.parseColor("#00E5FF") // TechGlowCyan
                            polyline.width = 10f
                            map.overlays.add(polyline)

                            if (routePoints.size > 1) {
                                val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(routePoints)
                                map.zoomToBoundingBox(boundingBox, true, 100)
                            }
                        } else if (destination != null) {
                            map.controller.animateTo(destination, 16.0, 1000L)
                        }

                        map.invalidate()
                    }
                )

                // FAB "Ubicarme"
                FloatingActionButton(
                    onClick = {
                        currentLocation?.let { mapInstance?.controller?.animateTo(it, 16.0, 1000L) }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = 32.dp),
                    containerColor = TechGlowCyan
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.LocationOn, "Ubicarme", tint = Color.Black)
                }

            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Se requiere permiso de ubicación para ver el mapa", color = TextMuted)
                }
            }
        }

        // Header / Search Bar & Dropdown (Top Layer)
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp).background(DeepSpaceTop.copy(alpha = 0.8f), RoundedCornerShape(12.dp)), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TechGlowCyan) }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    placeholder = { Text("Buscar destino...", color = TextMuted) },
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceInput,
                        unfocusedContainerColor = SurfaceInput,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedBorderColor = TechGlowCyan,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty() || destination != null) {
                            IconButton(onClick = { viewModel.clearDestination() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = TextMuted)
                            }
                        }
                    }
                )
            }

            AnimatedVisibility(visible = searchResults.isNotEmpty() && destination == null) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 250.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(searchResults) { result ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    viewModel.setDestination(result.lat.toDouble(), result.lon.toDouble())
                                }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = InfoBlue, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(result.display_name, fontSize = 14.sp, color = TextPrimary, maxLines = 2)
                            }
                            HorizontalDivider(color = SurfaceBorder)
                        }
                    }
                }
            }
        }
    }
}
