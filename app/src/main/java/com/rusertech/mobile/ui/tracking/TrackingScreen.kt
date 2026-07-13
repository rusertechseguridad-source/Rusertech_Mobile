package com.rusertech.mobile.ui.tracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.rusertech.mobile.ui.theme.*
import com.rusertech.mobile.util.BatteryUtil

@Composable
fun TrackingScreen(
    onLogout: () -> Unit, onNavigateToEvents: () -> Unit,
    onNavigateToAttachments: () -> Unit,  // Sección 29
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val identity by viewModel.userIdentity.collectAsStateWithLifecycle()
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val lastLocation by viewModel.lastLocation.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val accessRevoked by viewModel.accessRevoked.collectAsStateWithLifecycle()
    val credentialWarning by viewModel.credentialWarning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val battery = remember { BatteryUtil.getLevel(context) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locGranted = permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                         permissions.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false)
        // Se requiere location sí o sí para iniciar el servicio, las notificaciones en Tiramisu+
        if (locGranted) {
            viewModel.startTracking()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(deepSpaceGradient()).padding(20.dp).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Sección 10.1 — 403: banner rojo, tracking detenido, botón bloqueado
        if (accessRevoked) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(10.dp),
                color = SOSRed.copy(alpha = 0.15f)
            ) {
                Text(
                    "Tu acceso fue desactivado por el operador. Contactalo si es un error.",
                    modifier = Modifier.padding(12.dp),
                    color = SOSRed, fontSize = 12.sp
                )
            }
        }
        // Sección 10.1 — 401: banner ámbar, tracking SIGUE activo, solo advierte
        if (credentialWarning && !accessRevoked) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(10.dp),
                color = WarningAmber.copy(alpha = 0.15f)
            ) {
                Text(
                    "Tu API Key no es válida. El tracking sigue activo y guardando localmente — pedile al operador que la revise.",
                    modifier = Modifier.padding(12.dp),
                    color = WarningAmber, fontSize = 12.sp
                )
            }
        }
        // Header
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), fontSize = 17.sp, fontWeight = FontWeight.W500, color = TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(if (isTracking) stringResource(R.string.tracking_active) else stringResource(R.string.tracking_stopped),
                    if (isTracking) SuccessGreen else TextMuted)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Salir", tint = TextSecondary) }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Identidad
        identity?.let {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IdentityChip("Documento", it.documentId, Modifier.weight(1f))
                IdentityChip("Patente", it.plate, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
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
            Text("${"%.6f".format(loc.latitude)}, ${"%.6f".format(loc.longitude)}",
                fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextMuted)
        }
        Spacer(Modifier.weight(1f))
        // Botón eventos
        OutlinedButton(onClick = onNavigateToEvents, modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, SurfaceBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)) {
            Icon(Icons.Default.Notifications, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.events_title), fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        // Botón fotos de carga (Sección 29)
        OutlinedButton(onClick = onNavigateToAttachments, modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, SurfaceBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)) {
            Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text("Fotos de carga", fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        // Botón principal — bloqueado si el operador revocó el acceso (Sección 10.1, 403)
        Box(modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    accessRevoked -> androidx.compose.ui.graphics.SolidColor(SurfaceCard)
                    !isTracking -> techGlowGradient()
                    else -> androidx.compose.ui.graphics.SolidColor(SOSRed)
                }
            )
            .clickable(enabled = !accessRevoked) {
                if (isTracking) {
                    viewModel.stopTracking()
                } else {
                    val permissions = mutableListOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                }
            },
            contentAlignment = Alignment.Center) {
            Text(
                when {
                    accessRevoked -> "Acceso desactivado"
                    isTracking -> stringResource(R.string.tracking_stop)
                    else -> stringResource(R.string.tracking_start)
                },
                fontSize = 17.sp, fontWeight = FontWeight.W500,
                color = if (accessRevoked) TextMuted else if (!isTracking) IconOnGlow else Color.White
            )
        }
    }
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
