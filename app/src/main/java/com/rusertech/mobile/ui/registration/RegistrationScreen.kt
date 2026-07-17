package com.rusertech.mobile.ui.registration

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rusertech.mobile.R
import com.rusertech.mobile.ui.common.GradientButton
import com.rusertech.mobile.ui.common.RusertechTextField
import com.rusertech.mobile.ui.theme.*

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
// ... 

@Composable
fun RegistrationScreen(
    onRegistered: () -> Unit,
    onNavigateToDebug: () -> Unit,
    viewModel: RegistrationViewModel = hiltViewModel()
) {
    Column(
        modifier = Modifier.fillMaxSize().background(deepSpaceGradient())
            .padding(24.dp).systemBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        Image(
            painterResource(R.drawable.rusertech_logo), 
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { 
                            if (com.rusertech.mobile.BuildConfig.DEBUG) onNavigateToDebug() 
                        }
                    )
                }
        )
        Spacer(Modifier.height(20.dp))
        Text("Rusertech Mobile®", fontSize = 24.sp, fontWeight = FontWeight.W500, color = TextPrimary)
        Text("Seguridad & Logística", fontSize = 13.sp, color = TextSecondary)
        
        Spacer(Modifier.height(16.dp))
        // Banner de Mock
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            color = WarningAmber.copy(alpha = 0.2f),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber)
        ) {
            Text(
                "[MODO DESARROLLO — login simulado]",
                modifier = Modifier.padding(12.dp),
                color = WarningAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.W600,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(Modifier.height(28.dp))

        // Campos del conductor
        RusertechTextField(viewModel.documentId, viewModel::onDocumentChange,
            stringResource(R.string.register_dni_label), stringResource(R.string.register_dni_placeholder),
            error = viewModel.documentError, keyboardType = KeyboardType.Text)
        Spacer(Modifier.height(12.dp))
        RusertechTextField(viewModel.plate, viewModel::onPlateChange,
            stringResource(R.string.register_plate_label), stringResource(R.string.register_plate_placeholder),
            error = viewModel.plateError, capitalization = KeyboardCapitalization.Characters)
        Spacer(Modifier.height(12.dp))
        RusertechTextField(viewModel.activationCode, viewModel::onActivationCodeChange,
            "Código de activación", "PIN provisto por el operador",
            error = viewModel.activationError, capitalization = KeyboardCapitalization.Characters)

        Spacer(Modifier.height(28.dp))
        if (viewModel.networkError != null) {
            Text(viewModel.networkError!!, color = SOSRed, fontSize = 13.sp, fontWeight = FontWeight.W500)
            Spacer(Modifier.height(16.dp))
        }
        GradientButton(stringResource(R.string.register_save), viewModel.isValid, viewModel.isLoading) {
            viewModel.save(onRegistered)
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.register_disclaimer), fontSize = 11.sp, color = TextMuted, textAlign = TextAlign.Center)
    }
}
