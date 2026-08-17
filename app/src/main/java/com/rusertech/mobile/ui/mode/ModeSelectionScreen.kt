package com.rusertech.mobile.ui.mode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rusertech.mobile.R
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.ui.common.GradientButton
import com.rusertech.mobile.ui.theme.*
import com.rusertech.mobile.util.BatteryOptimizationUtil
import com.rusertech.mobile.util.OemUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * I7: estado del diálogo de configuración OEM. El endurecimiento existía
 * (OemUtil, BatteryOptimizationUtil) pero era código muerto: ninguna pantalla
 * lo mostraba — el riesgo #1 del proyecto (MIUI mata el servicio) sin
 * mitigación visible para el conductor.
 */
@HiltViewModel
class ModeSelectionViewModel @Inject constructor(
    private val prefs: UserPreferences
) : ViewModel() {
    val oemSetupDismissed = prefs.oemSetupDismissed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun dismissForever() {
        viewModelScope.launch { prefs.setOemSetupDismissed() }
    }
}

@Composable
fun ModeSelectionScreen(
    onNavigateToFreeTracking: () -> Unit,
    onNavigateToCreateTrip: () -> Unit,
    viewModel: ModeSelectionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val oemDismissed by viewModel.oemSetupDismissed.collectAsStateWithLifecycle()
    // Cerrado en esta sesión (sin persistir): botón "Entendido".
    var oemClosedThisTime by remember { mutableStateOf(false) }

    // I7: instrucciones por fabricante, solo donde hacen falta. Fabricantes
    // en needsSpecialSetup() sin instrucciones escritas (huawei/vivo/oneplus)
    // devuelven null y no muestran diálogo.
    val appName = stringResource(R.string.app_name)
    val oemInstructions = remember(appName) { OemUtil.getSetupInstructions(appName) }
    if (OemUtil.needsSpecialSetup() && !oemDismissed && !oemClosedThisTime && oemInstructions != null) {
        AlertDialog(
            onDismissRequest = { oemClosedThisTime = true },
            title = { Text(stringResource(R.string.oem_dialog_title), color = TextPrimary, fontSize = 17.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(oemInstructions, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    oemClosedThisTime = true
                    BatteryOptimizationUtil.openBatteryOptimizationSettings(context)
                }) { Text(stringResource(R.string.oem_dialog_open_settings), color = TechGlowCyan) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.dismissForever() }) {
                        Text(stringResource(R.string.oem_dialog_dont_show), color = TextMuted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { oemClosedThisTime = true }) {
                        Text(stringResource(R.string.oem_dialog_ok), color = TextPrimary)
                    }
                }
            },
            containerColor = DeepSpaceTop
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(deepSpaceGradient())
            .padding(24.dp)
            .systemBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "¿Cómo querés trackear?",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Seleccioná el modo operativo. Si inicias un viaje, el tracking quedará atado al mismo hasta que lo finalices.",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(48.dp))

        GradientButton(
            text = "Generar Viaje",
            enabled = true,
            loading = false,
            onClick = onNavigateToCreateTrip
        )

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = onNavigateToFreeTracking,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TechGlowCyan
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, TechGlowCyan),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
        ) {
            Text(
                text = "Seguimiento Libre",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
