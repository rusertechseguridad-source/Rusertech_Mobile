package com.rusertech.mobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush

@Composable
fun deepSpaceGradient(): Brush = remember {
    Brush.verticalGradient(colors = listOf(DeepSpaceTop, DeepSpaceBottom))
}

@Composable
fun techGlowGradient(): Brush = remember {
    Brush.linearGradient(colors = listOf(TechGlowGreen, TechGlowCyan, TechGlowBlue))
}
