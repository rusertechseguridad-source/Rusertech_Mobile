package com.rusertech.mobile.ui.attachments

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rusertech.mobile.data.local.db.AttachmentEntity
import com.rusertech.mobile.domain.model.AttachmentType
import com.rusertech.mobile.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AttachmentsScreen(
    onBack: () -> Unit,
    viewModel: AttachmentsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) pendingCaptureUri?.let { viewModel.onPhotoCaptured(it) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(deepSpaceGradient()).padding(20.dp).systemBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextPrimary)
            }
            Text("Fotos de carga", fontSize = 20.sp, fontWeight = FontWeight.W500, color = TextPrimary)
        }

        // Selector de tipo
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AttachmentType.entries.forEach { type ->
                FilterChip(
                    selected = viewModel.selectedType == type,
                    onClick = { viewModel.onTypeSelected(type) },
                    label = { Text(type.displayName, fontSize = 11.sp) }
                )
            }
        }

        OutlinedTextField(
            value = viewModel.notes, onValueChange = viewModel::onNotesChange,
            label = { Text("Notas (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )

        // Botón de cámara — gradiente Tech Glow
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(techGlowGradient(), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            TextButton(onClick = {
                val uri = createImageUri(context)
                pendingCaptureUri = uri
                cameraLauncher.launch(uri)
            }) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = DeepSpaceTop)
                Spacer(Modifier.width(8.dp))
                Text("Tomar foto", color = DeepSpaceTop, fontWeight = FontWeight.W500)
            }
        }

        if (pendingCount > 0) {
            Text("$pendingCount fotos pendientes de subir", fontSize = 12.sp, color = WarningAmber)
        }

        // Historial reciente
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(0.5.dp, SurfaceBorder)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Historial", fontSize = 13.sp, fontWeight = FontWeight.W500, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                if (recent.isEmpty()) {
                    Text("Sin fotos registradas", fontSize = 14.sp, color = TextMuted,
                        modifier = Modifier.padding(vertical = 24.dp))
                } else {
                    LazyColumn { items(recent, key = { it.id }) { AttachmentRow(it) } }
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(attachment: AttachmentEntity) {
    val type = AttachmentType.entries.find { it.code == attachment.type }
    val timeStr = remember(attachment.timestamp) {
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(attachment.timestamp))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(8.dp).background(
            if (attachment.isUploaded) SuccessGreen else WarningAmber, CircleShape
        ))
        Column(Modifier.weight(1f)) {
            Text(type?.displayName ?: attachment.type, fontSize = 13.sp, fontWeight = FontWeight.W500, color = TextPrimary)
            Text(
                if (attachment.notes.isNotBlank()) "$timeStr · ${attachment.notes}" else timeStr,
                fontSize = 11.sp, color = TextMuted
            )
        }
        Text(
            if (attachment.isUploaded) "Subida" else "Pendiente",
            fontSize = 11.sp,
            color = if (attachment.isUploaded) SuccessGreen else WarningAmber
        )
    }
}

/** Crea un Uri temporal vía FileProvider para que la cámara del sistema escriba la foto. */
private fun createImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "cargo_photos").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
