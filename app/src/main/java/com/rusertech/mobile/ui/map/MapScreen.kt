package com.rusertech.mobile.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import com.rusertech.mobile.R
import com.rusertech.mobile.domain.model.EventType
import com.rusertech.mobile.ui.theme.*
import com.rusertech.mobile.service.TrackingService
import com.rusertech.mobile.util.eventSubtype
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

// Espaciado de las flechas de sentido sobre el rastro, en metros de recorrido.
// Una flecha por punto satura el mapa; cada ~150 m se lee el sentido sin ruido.
private const val TRAIL_ARROW_SPACING_M = 150.0

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
    // Error de búsqueda/ruta visible para el conductor (nunca catch mudo).
    val mapError by viewModel.mapError.collectAsStateWithLifecycle()

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

                            // setDirectionArrow(person, arrow) está deprecado
                            // en osmdroid 6.1 — reemplazo directo por los
                            // setters separados vigentes.
                            locationOverlay.setPersonIcon(personBmp)
                            locationOverlay.setDirectionIcon(arrowBmp)

                            overlays.add(locationOverlay)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { map ->
                        val myLoc = map.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
                        map.overlays.clear()
                        if (myLoc != null) map.overlays.add(myLoc)

                        // Popup con superficie propia (tokens del proyecto) —
                        // compartido por todos los marcadores del mapa en vez
                        // del bubble blanco por defecto de osmdroid.
                        val popup = BasicInfoWindow(R.layout.map_event_popup, map)

                        // B3: rastro del recorrido desde Room + marcadores de
                        // estado/evento con la semántica de color de B1.
                        // Trazo en Deep Space: contrasta sobre los tiles claros
                        // y reserva los acentos Tech Glow para posición,
                        // eventos y flechas de sentido.
                        if (showTrail && trailPoints.size > 1) {
                            val trail = Polyline(map)
                            trail.setPoints(trailPoints)
                            trail.outlinePaint.color = DeepSpaceTop.toArgb()
                            trail.outlinePaint.strokeWidth = 8f
                            trail.infoWindow = null
                            map.overlays.add(trail)

                            // Flechas de sentido de circulación, espaciadas por
                            // distancia recorrida.
                            val arrowIcon = android.graphics.drawable.BitmapDrawable(
                                map.context.resources, trailArrowBitmap()
                            )
                            var sinceArrowM = 0.0
                            for (i in 1 until trailPoints.size) {
                                sinceArrowM += trailPoints[i - 1].distanceToAsDouble(trailPoints[i])
                                if (sinceArrowM >= TRAIL_ARROW_SPACING_M) {
                                    sinceArrowM = 0.0
                                    val arrow = Marker(map)
                                    arrow.position = trailPoints[i]
                                    arrow.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    arrow.icon = arrowIcon
                                    // bearingTo devuelve grados horarios desde el
                                    // norte; Marker.rotation gira antihorario →
                                    // signo invertido. Si en dispositivo las
                                    // flechas apuntan espejadas, es este signo.
                                    arrow.rotation = -trailPoints[i - 1].bearingTo(trailPoints[i]).toFloat()
                                    arrow.setInfoWindow(null)
                                    map.overlays.add(arrow)
                                }
                            }

                            // Inicio del rastro y última posición registrada.
                            val start = Marker(map)
                            start.position = trailPoints.first()
                            start.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            start.icon = android.graphics.drawable.BitmapDrawable(
                                map.context.resources, trailStartBitmap()
                            )
                            start.title = "Inicio del recorrido"
                            start.infoWindow = popup
                            map.overlays.add(start)

                            val last = Marker(map)
                            last.position = trailPoints.last()
                            last.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            last.icon = android.graphics.drawable.BitmapDrawable(
                                map.context.resources, trailLastBitmap()
                            )
                            last.title = "Última posición"
                            last.infoWindow = popup
                            map.overlays.add(last)
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
                                // Popup: tipo + sub-tipo de la metadata
                                // ("Checkpoint · Control policial") y fecha/hora.
                                val subtype = eventSubtype(ev.metadataJson)
                                m.title = (EventType.fromCode(ev.type)?.displayName ?: ev.type) +
                                    (subtype?.let { " · $it" } ?: "")
                                m.snippet = formatEventTime(ev.timestamp)
                                m.infoWindow = popup
                                map.overlays.add(m)
                            }
                        }

                        destination?.let { dest ->
                            val marker = Marker(map)
                            marker.position = dest
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            marker.title = "Destino"
                            marker.infoWindow = popup
                            map.overlays.add(marker)
                        }

                        if (routePoints.isNotEmpty()) {
                            val polyline = Polyline(map)
                            polyline.setPoints(routePoints)
                            // B2: ruta sugerida en azul de paleta — distinta del
                            // rastro real (verde-menta) para no confundirlos.
                            // Item 7: setters color/width deprecados → outlinePaint
                            // (igual que el rastro de B3).
                            polyline.outlinePaint.color = TechGlowBlue.toArgb()
                            polyline.outlinePaint.strokeWidth = 10f
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

            // El error de mapa no muere en un catch silencioso: banner visible.
            mapError?.let { message ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = WarningAmber.copy(alpha = 0.9f)
                ) {
                    Text(
                        message, Modifier.padding(10.dp),
                        color = DeepSpaceTop, fontSize = 12.sp, fontWeight = FontWeight.W600
                    )
                }
                Spacer(Modifier.height(6.dp))
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

            // Superficie OPACA obligatoria: SurfaceCard es blanco al 6 % de
            // alfa y sobre los tiles claros del mapa la lista queda invisible
            // (texto claro sobre fondo claro). DeepSpaceTop + borde para
            // despegarla del mapa + jerarquía nombre/detalle: legible al sol.
            AnimatedVisibility(visible = searchResults.isNotEmpty() && destination == null) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 250.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepSpaceTop),  // OPACO
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TechGlowCyan.copy(alpha = 0.4f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(searchResults) { result ->
                            // Nominatim devuelve una sola línea larga: el primer
                            // segmento es el nombre, el resto es el detalle.
                            val name = result.display_name.substringBefore(",")
                            val detail = result.display_name.substringAfter(",", "").trim()
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    viewModel.setDestination(result.lat.toDouble(), result.lon.toDouble())
                                }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = TechGlowCyan, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.W600, color = TextPrimary, maxLines = 1)
                                    if (detail.isNotBlank()) {
                                        Text(detail, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
                                    }
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = SurfaceBorder)
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

/**
 * Flecha de sentido del rastro. Dibujada apuntando al NORTE: el marcador la
 * rota al rumbo real del tramo. Cyan sobre el trazo Deep Space.
 */
private fun trailArrowBitmap(): android.graphics.Bitmap {
    val bmp = android.graphics.Bitmap.createBitmap(30, 30, android.graphics.Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(bmp).apply {
        val path = android.graphics.Path().apply {
            moveTo(15f, 2f); lineTo(27f, 26f); lineTo(15f, 20f); lineTo(3f, 26f); close()
        }
        drawPath(path, android.graphics.Paint().apply {
            color = TechGlowCyan.toArgb(); isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        })
        drawPath(path, android.graphics.Paint().apply {
            color = DeepSpaceTop.toArgb(); isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE; strokeWidth = 2f
        })
    }
    return bmp
}

/** Inicio del rastro: anillo blanco con centro verde — distinto de los puntos
 *  de evento (llenos) y del marcador de posición actual (gradiente). */
private fun trailStartBitmap(): android.graphics.Bitmap {
    val bmp = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(bmp).apply {
        drawCircle(16f, 16f, 13f, android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE; isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        })
        drawCircle(16f, 16f, 13f, android.graphics.Paint().apply {
            color = DeepSpaceTop.toArgb(); isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE; strokeWidth = 3f
        })
        drawCircle(16f, 16f, 6f, android.graphics.Paint().apply {
            color = TechGlowGreen.toArgb(); isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        })
    }
    return bmp
}

/** Última posición registrada del rastro: disco cyan con borde blanco, más
 *  grande que los puntos de evento. Con el tracking vivo coincide con el
 *  marcador de posición del overlay — misma información, sin conflicto. */
private fun trailLastBitmap(): android.graphics.Bitmap {
    val bmp = android.graphics.Bitmap.createBitmap(36, 36, android.graphics.Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(bmp).apply {
        drawCircle(18f, 18f, 14f, android.graphics.Paint().apply {
            color = TechGlowCyan.toArgb(); isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        })
        drawCircle(18f, 18f, 14f, android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE; isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE; strokeWidth = 4f
        })
    }
    return bmp
}

/** Fecha/hora local del evento para el popup del mapa. */
private fun formatEventTime(timestamp: Long): String =
    java.time.Instant.ofEpochMilli(timestamp)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm"))
