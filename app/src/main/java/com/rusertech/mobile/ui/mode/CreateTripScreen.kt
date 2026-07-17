package com.rusertech.mobile.ui.mode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rusertech.mobile.ui.common.GradientButton
import com.rusertech.mobile.ui.common.RusertechTextField
import com.rusertech.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTripScreen(
    onTripCreated: () -> Unit,
    onBack: () -> Unit,
    viewModel: CreateTripViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generar Viaje", fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver", tint = TextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSpaceTop)
            )
        },
        containerColor = DeepSpaceBottom
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(deepSpaceGradient())
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))
            
            Text(
                "Ingresá los detalles del viaje. El origen y destino son obligatorios.",
                color = TextSecondary,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(24.dp))
            
            RusertechTextField(
                value = viewModel.origin,
                onValueChange = viewModel::onOriginChange,
                label = "Origen",
                placeholder = "Ej: Calle Falsa 123, Ciudad",
                error = viewModel.originError,
                capitalization = KeyboardCapitalization.Sentences
            )
            Spacer(Modifier.height(16.dp))
            
            RusertechTextField(
                value = viewModel.destination,
                onValueChange = viewModel::onDestinationChange,
                label = "Destino",
                placeholder = "Ej: Av. Siempreviva 742, Ciudad",
                error = viewModel.destinationError,
                capitalization = KeyboardCapitalization.Sentences
            )
            Spacer(Modifier.height(16.dp))
            
            RusertechTextField(
                value = viewModel.cargoType,
                onValueChange = viewModel::onCargoTypeChange,
                label = "Tipo de Carga (Opcional)",
                placeholder = "Ej: Refrigerado, Seco, Peligroso",
                capitalization = KeyboardCapitalization.Words
            )
            Spacer(Modifier.height(16.dp))
            
            RusertechTextField(
                value = viewModel.notes,
                onValueChange = viewModel::onNotesChange,
                label = "Notas (Opcional)",
                placeholder = "Ej: Instrucciones de entrega",
                capitalization = KeyboardCapitalization.Sentences,
                singleLine = false,
                maxLines = 4,
                modifier = Modifier.heightIn(min = 100.dp)
            )
            Spacer(Modifier.height(32.dp))
            
            if (viewModel.networkError != null) {
                Text(viewModel.networkError!!, color = SOSRed, fontSize = 13.sp, fontWeight = FontWeight.W500)
                Spacer(Modifier.height(16.dp))
            }
            
            GradientButton(
                text = "Iniciar Viaje", 
                enabled = viewModel.isValid, 
                loading = viewModel.isLoading
            ) {
                viewModel.createTrip(onTripCreated)
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}
