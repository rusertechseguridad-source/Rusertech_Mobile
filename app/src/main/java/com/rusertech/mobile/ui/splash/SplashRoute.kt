package com.rusertech.mobile.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.rusertech.mobile.ui.theme.deepSpaceGradient
import com.rusertech.mobile.ui.tracking.TrackingViewModel

@Composable
fun SplashRoute(onRegistered: () -> Unit, onNeedsRegistration: () -> Unit,
    viewModel: TrackingViewModel = hiltViewModel()) {
    val identity by viewModel.userIdentity.collectAsState()
    LaunchedEffect(identity) {
        if (identity != null) { onRegistered() }
        else { kotlinx.coroutines.delay(300); if (viewModel.userIdentity.value == null) onNeedsRegistration() }
    }
    Box(Modifier.fillMaxSize().background(deepSpaceGradient()))
}
