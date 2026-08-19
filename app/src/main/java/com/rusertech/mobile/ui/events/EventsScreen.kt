package com.rusertech.mobile.ui.events

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rusertech.mobile.R
import com.rusertech.mobile.data.local.db.EventEntity
import com.rusertech.mobile.domain.model.EventType
import com.rusertech.mobile.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EventsScreen(onBack: () -> Unit, viewModel: EventsViewModel = hiltViewModel()) {
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val recentEvents by viewModel.recentEvents.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    // B6: evento pendiente de detalle (incidente/checkpoint) antes de disparar.
    var pendingMetaEvent by remember { mutableStateOf<EventType?>(null) }

    // B6: el detalle viaja en metadata — un diálogo liviano por tipo.
    pendingMetaEvent?.let { evType ->
        val options = when (evType) {
            EventType.INCIDENT -> listOf(
                "propio" to "Incidente propio", "tercero" to "Incidente de un tercero"
            )
            EventType.CHECKPOINT -> listOf(
                "control policial" to "Control policial", "peaje" to "Peaje",
                "báscula" to "Báscula", "otro" to "Otro"
            )
            else -> emptyList()
        }
        val metaKey = if (evType == EventType.INCIDENT) "categoria" else "tipo"
        AlertDialog(
            onDismissRequest = { pendingMetaEvent = null },
            title = { Text(evType.displayName, color = TextPrimary, fontSize = 17.sp) },
            text = {
                Column {
                    options.forEach { (value, label) ->
                        TextButton(
                            onClick = {
                                pendingMetaEvent = null
                                viewModel.fireEvent(evType, metadata = mapOf(metaKey to value))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label, color = TextPrimary, fontSize = 15.sp) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingMetaEvent = null }) { Text("Cancelar", color = TextMuted) }
            },
            containerColor = DeepSpaceTop
        )
    }

    Column(Modifier.fillMaxSize().background(deepSpaceGradient()).padding(20.dp).systemBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextPrimary) }
                Text(stringResource(R.string.events_title), fontSize = 20.sp, fontWeight = FontWeight.W500, color = TextPrimary)
            }
            Surface(shape = RoundedCornerShape(20.dp), color = (if (isOnline) SuccessGreen else WarningAmber).copy(alpha = 0.15f)) {
                Text(if (isOnline) "En línea" else "Offline", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 11.sp, color = if (isOnline) SuccessGreen else WarningAmber, fontWeight = FontWeight.W500)
            }
        }
        // SOS — I8: long-press de 1,5 s con progreso visible. Un botón de
        // pánico no puede dispararse por un roce en el bolsillo: cada SOS
        // manda emails reales a operaciones y al cliente. El tap corto no
        // hace nada; mantener presionado llena la barra y dispara.
        var sosPressing by remember { mutableStateOf(false) }
        var sosProgress by remember { mutableStateOf(0f) }
        LaunchedEffect(sosPressing) {
            if (sosPressing) {
                val steps = 20
                repeat(steps) {                       // B7: 20 × 50 ms = 1 s
                    kotlinx.coroutines.delay(50)
                    sosProgress = (it + 1) / steps.toFloat()
                }
                viewModel.fireSOS()                   // vibra al disparar
                sosPressing = false
                sosProgress = 0f
            } else {
                sosProgress = 0f                      // soltó antes: se cancela
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(14.dp))
                    .background(SOSRed.copy(alpha = 0.55f))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                sosPressing = true
                                tryAwaitRelease()
                                sosPressing = false
                            }
                        )
                    }
            ) {
                // Relleno progresivo: el conductor VE que está pasando algo.
                Box(
                    Modifier.fillMaxWidth(sosProgress).fillMaxHeight()
                        .background(SOSRed)
                )
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        feedback?.takeIf { it.contains("SOS") } ?: stringResource(R.string.events_sos),
                        fontSize = 16.sp, fontWeight = FontWeight.W500, color = Color.White
                    )
                    if (feedback?.contains("SOS") != true) {
                        Text(
                            stringResource(R.string.events_sos_hold),
                            fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            // B5: emergencia telefónica — abre el MARCADOR con 911 cargado.
            // ACTION_DIAL muestra el número sin llamar solo: cero permisos,
            // cero problemas con Play (ACTION_CALL exige permiso auditado).
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_DIAL,
                                android.net.Uri.parse("tel:911")
                            )
                        )
                    }
                },
                modifier = Modifier.height(60.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, SOSRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SOSRed)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Phone, contentDescription = "Llamar al 911", Modifier.size(18.dp))
                    Text("911", fontSize = 12.sp, fontWeight = FontWeight.W600)
                }
            }
        }
        // Acciones rápidas
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction(Icons.Default.Phone, stringResource(R.string.events_communication), InfoBlue, Modifier.weight(1f)) {
                viewModel.fireEvent(EventType.COMMUNICATION_REQUEST) }
            QuickAction(Icons.Default.LocationOn, stringResource(R.string.events_checkpoint), SuccessGreen, Modifier.weight(1f)) {
                pendingMetaEvent = EventType.CHECKPOINT }  // B6: pide el tipo
            QuickAction(Icons.Default.Warning, stringResource(R.string.events_incident), WarningAmber, Modifier.weight(1f)) {
                pendingMetaEvent = EventType.INCIDENT }    // B6: propio/tercero
        }
        // Feedback
        AnimatedVisibility(feedback != null && !feedback!!.contains("SOS"), enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = SuccessGreen.copy(alpha = 0.15f)) {
                Text(feedback ?: "", Modifier.fillMaxWidth().padding(12.dp), SuccessGreen, 14.sp, textAlign = TextAlign.Center)
            }
        }
        if (pendingCount > 0) Text("$pendingCount eventos pendientes", fontSize = 12.sp, color = WarningAmber)
        // Historial
        Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(14.dp), border = BorderStroke(0.5.dp, SurfaceBorder)) {
            Column(Modifier.padding(14.dp).fillMaxSize()) {
                Text(stringResource(R.string.events_recent_history), fontSize = 13.sp, fontWeight = FontWeight.W500, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                if (recentEvents.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.events_no_events), fontSize = 14.sp, color = TextMuted)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) { items(recentEvents, key = { it.id }) { event ->
                        EventRow(event)
                        if (event != recentEvents.last()) HorizontalDivider(thickness = 0.5.dp, color = SurfaceBorder)
                    } }
                }
            }
        }
    }
}

@Composable private fun QuickAction(icon: ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(onClick, modifier, shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, SurfaceBorder),
        colors = CardDefaults.outlinedCardColors(containerColor = SurfaceCard)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
            Text(label, fontSize = 11.sp, color = TextPrimary, textAlign = TextAlign.Center, lineHeight = 14.sp)
        }
    }
}

@Composable private fun EventRow(event: EventEntity) {
    // B1: color centralizado en el theme — MOB_STOP en ámbar (señal de
    // seguridad), paradas declaradas en azul, reanudación en verde.
    val dotColor = eventColor(event.type)
    val timeStr = remember(event.timestamp) { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(8.dp).background(dotColor, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(EventType.fromCode(event.type)?.displayName ?: event.type, fontSize = 14.sp, fontWeight = FontWeight.W600, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            // B7: un evento encolado sin fix no tiene coordenadas reales aún —
            // mostrar la verdad en vez de "0.0000, 0.0000".
            Text(
                if (event.awaitingFix) "$timeStr · Esperando ubicación"
                else "$timeStr · ${"%.4f".format(event.latitude)}, ${"%.4f".format(event.longitude)}",
                fontSize = 12.sp, color = TextSecondary
            )
        }
        if (!event.isSynced) Box(Modifier.size(6.dp).background(WarningAmber, CircleShape))
    }
}
