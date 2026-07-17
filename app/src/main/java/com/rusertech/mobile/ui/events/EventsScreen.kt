package com.rusertech.mobile.ui.events

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
        // SOS
        Button(onClick = { viewModel.fireSOS() }, Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = SOSRed)) {
            Text(feedback?.takeIf { it.contains("SOS") } ?: stringResource(R.string.events_sos),
                fontSize = 17.sp, fontWeight = FontWeight.W500, color = Color.White)
        }
        // Acciones rápidas
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction(Icons.Default.Phone, stringResource(R.string.events_communication), InfoBlue, Modifier.weight(1f)) {
                viewModel.fireEvent(EventType.COMMUNICATION_REQUEST) }
            QuickAction(Icons.Default.LocationOn, stringResource(R.string.events_checkpoint), SuccessGreen, Modifier.weight(1f)) {
                viewModel.fireEvent(EventType.CHECKPOINT) }
            QuickAction(Icons.Default.Warning, stringResource(R.string.events_incident), WarningAmber, Modifier.weight(1f)) {
                viewModel.fireEvent(EventType.INCIDENT) }
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
    val dotColor = when (event.type) {
        "MOB_SOS" -> SOSRed; "MOB_COMM" -> InfoBlue; "MOB_CHKPT" -> SuccessGreen
        "MOB_INCIDENT" -> WarningAmber; "MOB_LOWBAT" -> WarningAmber; "MOB_STOP" -> TextMuted
        else -> TextSecondary
    }
    val timeStr = remember(event.timestamp) { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(8.dp).background(dotColor, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(EventType.fromCode(event.type)?.displayName ?: event.type, fontSize = 14.sp, fontWeight = FontWeight.W600, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("$timeStr · ${"%.4f".format(event.latitude)}, ${"%.4f".format(event.longitude)}", fontSize = 12.sp, color = TextSecondary)
        }
        if (!event.isSynced) Box(Modifier.size(6.dp).background(WarningAmber, CircleShape))
    }
}
