package com.rusertech.mobile.ui.attachments

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
// Item 4: la fila de categorías necesita scroll horizontal — estos dos
// símbolos viven directo en .foundation (el comodín de layout no los trae).
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rusertech.mobile.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        val pendingPhoto = viewModel.pendingPhotoUri
        if (pendingPhoto != null) {
            // ----------------------------------------------------------
            // Item 6: PREVIEW con confirmación explícita. Nada se encola
            // hasta que el conductor toque "Enviar"; "Descartar" borra el
            // original y permite repetir la toma.
            // ----------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.5.dp, TechGlowCyan.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PhotoPreview(pendingPhoto)
                    Text(
                        "${viewModel.selectedType.displayName}" +
                            if (viewModel.notes.isNotBlank()) " · ${viewModel.notes}" else "",
                        fontSize = 12.sp, color = TextSecondary
                    )
                    if (viewModel.isSaving) {
                        // Item 3: el conductor VE que algo está pasando.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp), color = TechGlowCyan, strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.attachments_saving), fontSize = 13.sp, color = TextSecondary)
                        }
                    } else {
                        Text(stringResource(R.string.attachments_preview_hint), fontSize = 11.sp, color = TextMuted)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.discardPendingPhoto() },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.5.dp, SurfaceBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                            ) { Text(stringResource(R.string.attachments_discard), fontSize = 14.sp) }
                            Box(
                                modifier = Modifier.weight(1f).height(48.dp)
                                    .background(techGlowGradient(), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(onClick = { viewModel.sendPendingPhoto() }, Modifier.fillMaxSize()) {
                                    Text(stringResource(R.string.attachments_send), color = DeepSpaceTop, fontWeight = FontWeight.W600)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Selector de tipo — item 4: con scroll horizontal, las CUATRO
            // categorías son alcanzables incluso en una pantalla de 720p
            // (antes "Otro" quedaba cortado fuera de pantalla).
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                TextButton(
                    enabled = !viewModel.isSaving,
                    onClick = {
                        val uri = createImageUri(context)
                        pendingCaptureUri = uri
                        cameraLauncher.launch(uri)
                    }
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = DeepSpaceTop)
                    Spacer(Modifier.width(8.dp))
                    Text("Tomar foto", color = DeepSpaceTop, fontWeight = FontWeight.W500)
                }
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

/**
 * Preview de la foto tomada. El decode va SIEMPRE a Dispatchers.IO con
 * downsampling a ~800 px — decodificar 8 MP en el hilo principal congela
 * la UI hasta el umbral de ANR.
 */
@Composable
private fun PhotoPreview(uri: Uri) {
    val context = LocalContext.current
    // A3: decode compartido con el compresor — downsampleado, en IO
    // y CON la rotación EXIF aplicada (una foto vertical se ve vertical acá).
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            com.rusertech.mobile.util.ImageCompressor.decodeOriented(context, uri, 800)
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Foto tomada",
            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            Modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(color = TechGlowCyan, strokeWidth = 2.dp) }
    }
}

/** Crea un Uri temporal vía FileProvider para que la cámara del sistema escriba la foto. */
private fun createImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "cargo_photos").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
