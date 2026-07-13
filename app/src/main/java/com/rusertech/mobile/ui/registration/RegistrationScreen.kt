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

@Composable
fun RegistrationScreen(
    onRegistered: () -> Unit,
    viewModel: RegistrationViewModel = hiltViewModel()
) {
    Column(
        modifier = Modifier.fillMaxSize().background(deepSpaceGradient())
            .padding(24.dp).systemBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        Box(modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)).background(techGlowGradient()),
            contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.rusertech_logo), contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(72.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.app_name), fontSize = 24.sp, fontWeight = FontWeight.W500, color = TextPrimary)
        Text(stringResource(R.string.app_tagline), fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(28.dp))

        // Campos del conductor
        RusertechTextField(viewModel.documentId, viewModel::onDocumentChange,
            stringResource(R.string.register_dni_label), stringResource(R.string.register_dni_placeholder),
            error = viewModel.documentError, keyboardType = KeyboardType.Text)
        Spacer(Modifier.height(12.dp))
        RusertechTextField(viewModel.plate, viewModel::onPlateChange,
            stringResource(R.string.register_plate_label), stringResource(R.string.register_plate_placeholder),
            error = viewModel.plateError, capitalization = KeyboardCapitalization.Characters)

        Spacer(Modifier.height(20.dp))
        Text("Configuración de flota", fontSize = 13.sp, fontWeight = FontWeight.W500, color = TextSecondary)
        Text("Estos datos los provee el operador desde el panel web", fontSize = 11.sp, color = TextMuted)
        Spacer(Modifier.height(8.dp))

        // Campos de configuración de flota (provistos por el operador)
        RusertechTextField(viewModel.avlUserCode, viewModel::onAvlCodeChange,
            "Código AVL", "Código del dispositivo mobile")
        Spacer(Modifier.height(12.dp))
        RusertechTextField(viewModel.apiKey, viewModel::onApiKeyChange,
            "API Key", "Clave de autenticación")

        Spacer(Modifier.height(28.dp))
        GradientButton(stringResource(R.string.register_save), viewModel.isValid, viewModel.isLoading) {
            viewModel.save(onRegistered)
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.register_disclaimer), fontSize = 11.sp, color = TextMuted, textAlign = TextAlign.Center)
    }
}
