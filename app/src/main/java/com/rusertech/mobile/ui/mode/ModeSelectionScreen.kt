package com.rusertech.mobile.ui.mode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rusertech.mobile.ui.common.GradientButton
import com.rusertech.mobile.ui.theme.*

@Composable
fun ModeSelectionScreen(
    onNavigateToFreeTracking: () -> Unit,
    onNavigateToCreateTrip: () -> Unit
) {
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
