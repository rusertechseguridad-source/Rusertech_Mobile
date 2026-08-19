package com.rusertech.mobile.ui.tracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
// rememberSaveable vive en el subpaquete .saveable: el comodín
// androidx.compose.runtime.* NO lo cubre y sin este import no compila.
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rusertech.mobile.R
import com.rusertech.mobile.domain.model.DriverState
import com.rusertech.mobile.ui.theme.*
import com.rusertech.mobile.util.BatteryUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    onLogout: () -> Unit,
    onTripFinished: () -> Unit,  // I2: completar viaje NO es desloguear
    onNavigateToEvents: () -> Unit,
    onNavigateToAttachments: () -> Unit,  // Sección 29
    onNavigateToMap: () -> Unit,
    onNavigateToCreateTrip: () -> Unit,  // B7: cambiar de modo sin desloguear
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val identity by viewModel.userIdentity.collectAsStateWithLifecycle()
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val trackingIntended by viewModel.trackingIntended.collectAsStateWithLifecycle()
    val lastLocation by viewModel.lastLocation.collectAsStateWithLifecycle()
    val currentAddress by viewModel.currentAddress.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val accessRevoked by viewModel.accessRevoked.collectAsStateWithLifecycle()
    val credentialWarning by viewModel.credentialWarning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val battery = remember { BatteryUtil.getLevel(context) }

    val activeTrip by viewModel.activeTrip.collectAsStateWithLifecycle()
    var showEndTripDialog by remember { mutableStateOf(false) }
    // B7: cerrar sesión con confirmación de texto (el ícono solo no se lee
    // como "salir") — y llamando de verdad a viewModel.logout(): el ícono
    // anterior SOLO navegaba, la purga de FIX-7 nunca corría desde la UI.
    var showLogoutDialog by remember { mutableStateOf(false) }
    // B6: al declarar parada sanitaria se pide el lugar (metadata).
    var showSanitaryOptions by remember { mutableStateOf(false) }
    // B4: distancia acumulada de la sesión.
    val sessionDistanceM by viewModel.sessionDistanceM.collectAsStateWithLifecycle()

    // FIX-10: estado operativo del conductor + bottom sheet para declararlo.
    val driverState by viewModel.driverState.collectAsStateWithLifecycle()
    var showStateSheet by remember { mutableStateOf(false) }

    // ------------------------------------------------------------------
    // C1: permiso de ubicación en segundo plano.
    // Sin "Permitir todo el tiempo", el tracking NO se reanuda tras reboot
    // (el FGS arrancado desde background no recibe fixes en Android 11+).
    // No es bloqueante para el uso en primer plano, pero la advertencia
    // queda siempre visible y el diálogo manda a Settings (en Android 11+
    // el permiso no se concede por diálogo directo).
    // ------------------------------------------------------------------
    var hasBgPermission by remember {
        mutableStateOf(com.rusertech.mobile.ui.common.PermissionHandler.hasBackgroundLocation(context))
    }
    // A1: con Compose UI 1.7, LocalLifecycleOwner ya no vive en
    // androidx.compose.ui.platform — se movió a androidx.lifecycle.compose
    // (lifecycle 2.8+, que este proyecto ya usa). Referenciar el viejo
    // rompería la compilación con el BOM nuevo.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Re-chequear al volver de Settings (ON_RESUME).
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasBgPermission = com.rusertech.mobile.ui.common.PermissionHandler.hasBackgroundLocation(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showBgDialog by remember { mutableStateOf(false) }
    var bgDialogShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isTracking, hasBgPermission) {
        // Una vez por entrada a la pantalla, al arrancar el tracking sin permiso.
        if (isTracking && !hasBgPermission && !bgDialogShown) {
            showBgDialog = true
            bgDialogShown = true
        }
    }
    val openAppSettings = {
        context.startActivity(
            android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)
            )
        )
    }

    // C1: si el conductor llegó desde la notificación de reanudación del
    // BootReceiver (tracking pendiente en DataStore, servicio muerto), al
    // pasar a foreground se reanuda solo.
    //
    // UN SOLO intento por entrada a la pantalla: sin este guard, al tocar
    // "Detener" el flow de DataStore emite false unos milisegundos después
    // de que el servicio muere, y el efecto vería (intended=true, running=
    // false) y RELANZARÍA el tracking recién detenido.
    var autoResumeDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(trackingIntended) {
        if (autoResumeDone) return@LaunchedEffect
        when {
            trackingIntended && !isTracking &&
                com.rusertech.mobile.ui.common.PermissionHandler.hasFineLocation(context) -> {
                autoResumeDone = true
                viewModel.startTracking()
            }
            // Tracking ya corriendo, o intención sin permiso de ubicación:
            // no hay nada que reanudar automáticamente en esta entrada.
            trackingIntended || isTracking -> autoResumeDone = true
        }
    }

    if (showBgDialog) {
        AlertDialog(
            onDismissRequest = { showBgDialog = false },
            title = { Text(stringResource(R.string.permission_background_title), color = TextPrimary) },
            text = { Text(stringResource(R.string.permission_background_message), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showBgDialog = false; openAppSettings() }) {
                    Text(stringResource(R.string.permission_open_settings), color = TechGlowCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBgDialog = false }) {
                    Text(stringResource(R.string.permission_background_later), color = TextMuted)
                }
            },
            containerColor = DeepSpaceTop
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locGranted = permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                         permissions.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false)
        if (locGranted) {
            viewModel.startTracking()
        }
    }
    // Lista de permisos de arranque, compartida por el botón y el auto-inicio
    // del Modo Viaje.
    val startPermissions = remember {
        val list = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            list.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    // ------------------------------------------------------------------
    // Crear un viaje ES viajar. Al entrar a esta pantalla
    // con un viaje activo y el tracking detenido, el tracking arranca solo
    // (pidiendo permisos si es la primera vez). Antes de este fix, el botón
    // principal con viaje activo SOLO ofrecía finalizar: el Modo Viaje no
    // registraba un solo punto.
    //
    // Guard de UN intento por entrada a la pantalla: al finalizar el viaje,
    // completeTrip detiene el servicio unos milisegundos ANTES de limpiar
    // activeTrip de DataStore — sin el guard, este efecto vería (viaje
    // activo, tracking detenido) en esa ventana y relanzaría el tracking
    // en plena finalización.
    // ------------------------------------------------------------------
    var tripAutoStartDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(activeTrip, isTracking) {
        if (tripAutoStartDone) return@LaunchedEffect
        if (activeTrip != null) {
            tripAutoStartDone = true
            if (!isTracking) {
                if (com.rusertech.mobile.ui.common.PermissionHandler.hasFineLocation(context)) {
                    viewModel.startTracking()
                } else {
                    permissionLauncher.launch(startPermissions)
                }
            }
        }
    }
    
    // FIX-10: bottom sheet de estados operativos — táctiles grandes, texto
    // claro, ícono por estado. Disponible en Modo Viaje y en Tracking Libre.
    if (showStateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStateSheet = false },
            containerColor = DeepSpaceTop
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text("¿Cuál es tu situación?", fontSize = 17.sp, fontWeight = FontWeight.W600, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Declarar una parada evita la alerta de seguridad por detención no avisada.",
                    fontSize = 12.sp, color = TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                // Filas COMPACTAS — ícono + título en una
                // línea; la descripción solo en la opción seleccionada. El
                // área táctil se mantiene ≥48 dp (guantes): el padding
                // vertical de 14 dp + la línea de texto lo garantizan.
                DriverState.entries.forEach { state ->
                    val selected = state == driverState
                    // B1: verde = circulando; azul = parada declarada (legítima).
                    val accent = driverStateColor(state)
                    Surface(
                        onClick = {
                            showStateSheet = false
                            // B6: la parada sanitaria pide el lugar antes de declararse.
                            if (state == DriverState.STOPPED_SANITARY) {
                                showSanitaryOptions = true
                            } else {
                                viewModel.declareState(state)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) accent.copy(alpha = 0.15f) else SurfaceCard,
                        border = BorderStroke(0.5.dp, if (selected) accent else SurfaceBorder)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(driverStateIcon(state), fontSize = 18.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    state.displayName, fontSize = 15.sp,
                                    fontWeight = if (selected) FontWeight.W600 else FontWeight.W500,
                                    color = if (selected) accent else TextPrimary
                                )
                            }
                            if (selected) {
                                Text(
                                    driverStateHint(state), fontSize = 12.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(start = 30.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // B6: lugar de la parada sanitaria — viaja en metadata, sin código nuevo.
    if (showSanitaryOptions) {
        AlertDialog(
            onDismissRequest = { showSanitaryOptions = false },
            title = { Text("¿Dónde es la parada?", color = TextPrimary, fontSize = 17.sp) },
            text = {
                Column {
                    listOf(
                        "baño público" to "Baño público",
                        "estación de servicio" to "Estación de servicio",
                        "otro" to "Otro"
                    ).forEach { (value, label) ->
                        TextButton(
                            onClick = {
                                showSanitaryOptions = false
                                viewModel.declareState(
                                    DriverState.STOPPED_SANITARY,
                                    metadata = mapOf("lugar" to value)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label, color = TextPrimary, fontSize = 15.sp) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSanitaryOptions = false }) {
                    Text("Cancelar", color = TextMuted)
                }
            },
            containerColor = DeepSpaceTop
        )
    }

    // B7: confirmación de cierre de sesión CON texto — y ejecutando el logout
    // real (purga FIX-7) antes de navegar.
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Cerrar sesión", color = TextPrimary) },
            text = {
                Text(
                    "Se cerrará la sesión de este dispositivo. Lo pendiente sin " +
                        "sincronizar se intenta enviar y después se borra del teléfono.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                    onLogout()
                }) { Text("Cerrar sesión", color = SOSRed) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar", color = TextPrimary)
                }
            },
            containerColor = DeepSpaceTop
        )
    }

    if (showEndTripDialog) {
        AlertDialog(
            onDismissRequest = { showEndTripDialog = false },
            title = { Text("Finalizar Viaje", color = TextPrimary) },
            text = { Text("¿Confirmás que finalizás el viaje? Esta acción no se puede deshacer.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showEndTripDialog = false
                    // I2: completar viaje NO toca la identidad — vuelve a la
                    // selección de modo con la sesión intacta.
                    viewModel.completeTrip(onSuccess = { onTripFinished() })
                }) {
                    Text("Confirmar", color = SOSRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndTripDialog = false }) {
                    Text("Cancelar", color = TextPrimary)
                }
            },
            containerColor = DeepSpaceTop
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(deepSpaceGradient()).padding(20.dp).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ... (Error Banners)
        if (accessRevoked) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(10.dp),
                color = SOSRed.copy(alpha = 0.15f)
            ) {
                Text("Tu acceso fue desactivado por el operador. Contactalo si es un error.", modifier = Modifier.padding(12.dp), color = SOSRed, fontSize = 12.sp)
            }
        }
        if (credentialWarning && !accessRevoked) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(10.dp),
                color = WarningAmber.copy(alpha = 0.15f)
            ) {
                Text("Tu API Key no es válida. El tracking sigue activo y guardando localmente.", modifier = Modifier.padding(12.dp), color = WarningAmber, fontSize = 12.sp)
            }
        }
        // C1: advertencia SIEMPRE visible mientras falte "Permitir todo el
        // tiempo". Tap → Settings de la app.
        if (!hasBgPermission) {
            Surface(
                onClick = openAppSettings,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(10.dp),
                color = WarningAmber.copy(alpha = 0.15f)
            ) {
                Text(
                    stringResource(R.string.permission_background_banner),
                    modifier = Modifier.padding(12.dp), color = WarningAmber, fontSize = 12.sp
                )
            }
        }
        // Header
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(if (activeTrip != null) "VIAJE ACTIVO" else "SEGUIMIENTO LIBRE", fontSize = 17.sp, fontWeight = FontWeight.W500, color = if (activeTrip != null) TechGlowCyan else TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(if (isTracking) stringResource(R.string.tracking_active) else stringResource(R.string.tracking_stopped),
                    if (isTracking) SuccessGreen else TextMuted)
                Spacer(Modifier.width(8.dp))
                // Solo permitimos logout si no hay viaje activo. Si hay viaje activo, obligamos a cerrarlo.
                // B7: abre la confirmación con texto (antes navegaba directo
                // SIN ejecutar el logout real — la purga nunca corría).
                if (activeTrip == null) {
                    IconButton(onClick = { showLogoutDialog = true }) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Cerrar sesión", tint = TextSecondary) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        
        // Información del Viaje (si existe)
        activeTrip?.let { trip ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(14.dp), border = BorderStroke(0.5.dp, TechGlowCyan.copy(alpha=0.5f))) {
                Column(Modifier.padding(14.dp)) {
                    Text("Origen: ${trip.origin}", fontSize = 13.sp, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("Destino: ${trip.destination}", fontSize = 13.sp, color = TextPrimary)
                    if (trip.cargoType.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Carga: ${trip.cargoType}", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // FIX-10: estado operativo — siempre visible; tap abre el selector.
        // Disponible con tracking activo, en Modo Viaje y en Tracking Libre.
        if (isTracking) {
            // B1: parada DECLARADA = azul (legítima); el ámbar queda reservado
            // para la señal de seguridad (MOB_STOP, parada NO declarada).
            val stateAccent = driverStateColor(driverState)
            Surface(
                onClick = { showStateSheet = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (driverState.isDeclaredStop) stateAccent.copy(alpha = 0.12f) else SurfaceCard,
                border = BorderStroke(0.5.dp, if (driverState.isDeclaredStop) stateAccent else SurfaceBorder)
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(driverStateIcon(driverState), fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Estado", fontSize = 11.sp, color = TextMuted)
                                Text(
                                    driverState.displayName, fontSize = 14.sp, fontWeight = FontWeight.W600,
                                    color = if (driverState.isDeclaredStop) stateAccent else TextPrimary
                                )
                            }
                        }
                        Text("Cambiar", fontSize = 12.sp, color = TechGlowCyan, fontWeight = FontWeight.W500)
                    }
                    // A4: con una parada declarada, volver a "En
                    // viaje" es UN toque — sin abrir el selector. El conductor
                    // se olvida siempre, y mientras tanto el MOB_STOP
                    // automático queda suprimido (se pierde la señal de
                    // seguridad). El auto-resume por movimiento sigue como red.
                    if (driverState.isDeclaredStop) {
                        HorizontalDivider(thickness = 0.5.dp, color = SurfaceBorder)
                        TextButton(
                            onClick = { viewModel.declareState(DriverState.EN_ROUTE) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "▶  Reanudar viaje",
                                fontSize = 14.sp, fontWeight = FontWeight.W600, color = TechGlowCyan
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Velocímetro
        Box(modifier = Modifier.size(140.dp).border(3.dp,
            if (isTracking) techGlowGradient() else Brush.linearGradient(listOf(SurfaceBorder, SurfaceBorder)), CircleShape),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val kmh = lastLocation?.let { (it.speed * 3.6f).toInt() } ?: 0
                Text("$kmh", fontSize = 40.sp, fontWeight = FontWeight.W500, color = TextPrimary)
                Text("km/h", fontSize = 13.sp, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(20.dp))
        // Card de estado
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(14.dp), border = BorderStroke(0.5.dp, SurfaceBorder)) {
            Column(Modifier.padding(14.dp)) {
                StatusRow("GPS", lastLocation != null, "Precisión ${lastLocation?.accuracy?.toInt() ?: 0}m", "Buscando señal")
                StatusRow("Red", isOnline, "Conectado", "Sin conexión")
                StatusRow("Tracking", isTracking, "Activo", "Detenido")
                StatusRow("Batería", battery > 20, "$battery%", "$battery% (baja)")
                // B4: distancia recorrida desde que arrancó el seguimiento.
                if (isTracking || sessionDistanceM > 0f) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), Arrangement.SpaceBetween) {
                        Text("Distancia", fontSize = 13.sp, color = TextSecondary)
                        Text(
                            if (sessionDistanceM >= 1000f) "%.1f km".format(sessionDistanceM / 1000f)
                            else "${sessionDistanceM.toInt()} m",
                            fontSize = 13.sp, color = TextPrimary
                        )
                    }
                }
                if (pendingCount > 0) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), Arrangement.SpaceBetween) {
                        Text("Pendientes", fontSize = 13.sp, color = TextSecondary)
                        Text("$pendingCount", fontSize = 13.sp, color = WarningAmber)
                    }
                }
            }
        }
        lastLocation?.let { loc ->
            Spacer(Modifier.height(8.dp))
            Text("Ubicación actual", fontSize = 11.sp, color = TextMuted)
            // Dirección legible si el geocoding la resolvió; si no,
            // coordenadas — el dato nunca desaparece por un fallo de red.
            val address = currentAddress
            if (address != null) {
                Text(
                    address, fontSize = 12.sp, color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            } else {
                Text("${"%.6f".format(loc.latitude)}, ${"%.6f".format(loc.longitude)}",
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextMuted)
            }
        }
        Spacer(Modifier.weight(1f))
        
        // Botones de acción
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onNavigateToEvents, modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, SurfaceBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)) {
                Icon(Icons.Default.Notifications, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                Text("Eventos", fontSize = 13.sp)
            }
            OutlinedButton(onClick = onNavigateToMap, modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, SurfaceBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)) {
                Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                Text("Mapa", fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onNavigateToAttachments, modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, SurfaceBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)) {
                Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("Fotos de carga", fontSize = 13.sp)
            }
            // B7: cambiar de modo sin desloguear — desde Tracking Libre se
            // puede generar un viaje directamente (el camino inverso es
            // Finalizar Viaje, que ya vuelve a la selección de modo).
            if (activeTrip == null) {
                OutlinedButton(onClick = onNavigateToCreateTrip, modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, TechGlowCyan.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TechGlowCyan)) {
                    Text("Generar Viaje", fontSize = 13.sp, fontWeight = FontWeight.W600)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        
        // Botón principal: tres estados con viaje.
        //   viaje + trackeando        → "Finalizar Viaje" (rojo)
        //   viaje + NO trackeando     → "Reanudar seguimiento" (glow): el
        //     servicio murió (kill OEM, etc.) — reinicia el tracking SIN
        //     tocar el viaje. Para finalizar en este estado: reanudar y
        //     después finalizar (decisión del fix: nunca finalizar a ciegas).
        //   sin viaje                 → Tracking Libre como siempre
        Box(modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    accessRevoked -> androidx.compose.ui.graphics.SolidColor(SurfaceCard)
                    !isTracking -> techGlowGradient()
                    else -> androidx.compose.ui.graphics.SolidColor(SOSRed)
                }
            )
            .clickable(enabled = !accessRevoked) {
                when {
                    activeTrip != null && isTracking -> showEndTripDialog = true
                    activeTrip != null -> {
                        // Reanudar sin tocar el viaje
                        if (com.rusertech.mobile.ui.common.PermissionHandler.hasFineLocation(context)) {
                            viewModel.startTracking()
                        } else {
                            permissionLauncher.launch(startPermissions)
                        }
                    }
                    isTracking -> viewModel.stopTracking()
                    else -> permissionLauncher.launch(startPermissions)
                }
            },
            contentAlignment = Alignment.Center) {
            Text(
                when {
                    accessRevoked -> "Acceso desactivado"
                    activeTrip != null && isTracking -> "Finalizar Viaje"
                    activeTrip != null -> "Reanudar seguimiento"
                    isTracking -> "Detener Seguimiento Libre"
                    else -> "Iniciar Seguimiento Libre"
                },
                fontSize = 17.sp, fontWeight = FontWeight.W500,
                color = if (accessRevoked) TextMuted else if (!isTracking) IconOnGlow else Color.White
            )
        }
    }
}

// FIX-10: ícono y ayuda por estado operativo (emoji: sin assets nuevos).
private fun driverStateIcon(state: DriverState): String = when (state) {
    DriverState.EN_ROUTE -> "🚚"
    DriverState.STOPPED_WAYPOINT -> "📍"
    DriverState.STOPPED_AUTHORIZED -> "🅿️"
    DriverState.STOPPED_SANITARY -> "🚻"
}

private fun driverStateHint(state: DriverState): String = when (state) {
    DriverState.EN_ROUTE -> "Circulando hacia el destino"
    DriverState.STOPPED_WAYPOINT -> "Llegué a un destino intermedio"
    DriverState.STOPPED_AUTHORIZED -> "Parada autorizada por el operador"
    DriverState.STOPPED_SANITARY -> "Parada breve por necesidad"
}

@Composable private fun IdentityChip(label: String, value: String, modifier: Modifier) {
    Surface(modifier, RoundedCornerShape(8.dp), color = SurfaceInput) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 11.sp, color = TextMuted)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.W500, color = TextPrimary)
        }
    }
}

@Composable private fun StatusRow(label: String, ok: Boolean, okText: String, badText: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (ok) SuccessGreen else SOSRed, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(if (ok) okText else badText, fontSize = 13.sp, color = if (ok) SuccessGreen else SOSRed)
        }
    }
}

@Composable private fun StatusBadge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.15f)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp, color = color, fontWeight = FontWeight.W500)
    }
}
