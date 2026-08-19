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
import androidx.compose.ui.graphics.toArgb
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
    // B3: rastro del recorrido + eventos sobre él
    val showTrail by viewModel.showTrail.collectAsStateWithLifecycle()
    val trailPoints by viewModel.trailPoints.collectAsStateWithLifecycle()
    val trailEvents by viewModel.trailEvents.collectAsStateWithLifecycle()

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

                            // B2: marcador de posición con el gradiente Tech
                            // Glow de la marca (verde→menta→azul) + pin, en
                            // vez del cyan hardcodeado fuera de paleta.
                            val glowShader = android.graphics.LinearGradient(
                                4f, 4f, 44f, 44f,
                                intArrayOf(TechGlowGreen.toArgb(), TechGlowCyan.toArgb(), TechGlowBlue.toArgb()),
                                null, android.graphics.Shader.TileMode.CLAMP
                            )
                            val personBmp = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
                            android.graphics.Canvas(personBmp).apply {
                                drawCircle(24f, 24f, 20f, android.graphics.Paint().apply { shader = glowShader; isAntiAlias = true; style = android.graphics.Paint.Style.FILL })
                                drawCircle(24f, 24f, 20f, android.graphics.Paint().apply { color = DeepSpaceTop.toArgb(); isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeWidth = 3f })
                                // Pin interior: cabeza + punta, en Deep Space para contraste
                                drawCircle(24f, 19f, 6f, android.graphics.Paint().apply { color = DeepSpaceTop.toArgb(); isAntiAlias = true; style = android.graphics.Paint.Style.FILL })
                                val pinPath = android.graphics.Path().apply { moveTo(18f, 22f); lineTo(30f, 22f); lineTo(24f, 35f); close() }
                                drawPath(pinPath, android.graphics.Paint().apply { color = DeepSpaceTop.toArgb(); isAntiAlias = true; style = android.graphics.Paint.Style.FILL })
                            }

                            val arrowBmp = android.graphics.Bitmap.createBitmap(60, 60, android.graphics.Bitmap.Config.ARGB_8888)
                            android.graphics.Canvas(arrowBmp).apply {
                                val path = android.graphics.Path().apply { moveTo(30f, 0f); lineTo(60f, 60f); lineTo(30f, 45f); lineTo(0f, 60f); close() }
                                drawPath(path, android.graphics.Paint().apply {
                                    shader = android.graphics.LinearGradient(0f, 0f, 60f, 60f,
                                        intArrayOf(TechGlowGreen.toArgb(), TechGlowCyan.toArgb(), TechGlowBlue.toArgb()),
                                        null, android.graphics.Shader.TileMode.CLAMP)
                                    isAntiAlias = true; style = android.graphics.Paint.Style.FILL
                                })
                                drawPath(path, android.graphics.Paint().apply { color = DeepSpaceTop.toArgb(); isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeWidth = 3f })
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

                        // B3: rastro del recorrido desde Room + marcadores de
                        // estado/evento con la semántica de color de B1.
                        if (showTrail && trailPoints.size > 1) {
                            val trail = Polyline(map)
                            trail.setPoints(trailPoints)
                            trail.outlinePaint.color = TechGlowCyan.toArgb()
                            trail.outlinePaint.strokeWidth = 8f
                            map.overlays.add(trail)
                        }
                        if (showTrail) {
                            trailEvents.forEach { ev ->
                                val m = Marker(map)
                                m.position = GeoPoint(ev.latitude, ev.longitude)
                                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                m.icon = android.graphics.drawable.BitmapDrawable(
                                    map.context.resources,
                                    eventDotBitmap(eventColor(ev.type).toArgb())
                                )
                                m.title = com.rusertech.mobile.domain.model.EventType
                                    .fromCode(ev.type)?.displayName ?: ev.type
                                map.overlays.add(m)
                            }
                        }

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
                            // B2: ruta sugerida en azul de paleta — distinta del
                            // rastro real (verde-menta) para no confundirlos.
                            polyline.color = TechGlowBlue.toArgb()
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

                // FAB "Ubicarme" — B2: antes dependía SOLO de la posición que
                // llega por TrackingService (null con el tracking detenido →
                // el botón "no funcionaba"). Fallback al fix propio del
                // overlay de osmdroid, que vive aunque el servicio no corra.
                FloatingActionButton(
                    onClick = {
                        val overlayFix = mapInstance?.overlays
                            ?.filterIsInstance<MyLocationNewOverlay>()
                            ?.firstOrNull()?.myLocation
                        val target = currentLocation ?: overlayFix
                        target?.let { mapInstance?.controller?.animateTo(it, 16.0, 1000L) }
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

            // B3: switch para mostrar/ocultar el rastro del recorrido.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = DeepSpaceTop.copy(alpha = 0.8f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rastro", fontSize = 12.sp, color = TextSecondary)
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = showTrail,
                            onCheckedChange = { viewModel.toggleTrail() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TechGlowCyan,
                                checkedTrackColor = TechGlowCyan.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
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

/** B3: punto coloreado para marcar eventos/estados sobre el rastro. */
private fun eventDotBitmap(colorInt: Int): android.graphics.Bitmap {
    val bmp = android.graphics.Bitmap.createBitmap(28, 28, android.graphics.Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(bmp).apply {
        drawCircle(14f, 14f, 11f, android.graphics.Paint().apply {
            color = colorInt; isAntiAlias = true; style = android.graphics.Paint.Style.FILL
        })
        drawCircle(14f, 14f, 11f, android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE; isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE; strokeWidth = 3f
        })
    }
    return bmp
}
