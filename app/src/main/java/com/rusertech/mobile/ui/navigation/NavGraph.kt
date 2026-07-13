package com.rusertech.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rusertech.mobile.ui.attachments.AttachmentsScreen
import com.rusertech.mobile.ui.events.EventsScreen
import com.rusertech.mobile.ui.registration.RegistrationScreen
import com.rusertech.mobile.ui.splash.SplashRoute
import com.rusertech.mobile.ui.tracking.TrackingScreen

@Composable
fun RusertechNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController, startDestination = "splash") {
        composable("splash") {
            SplashRoute(
                onRegistered = { navController.navigate("tracking") { popUpTo("splash") { inclusive = true } } },
                onNeedsRegistration = { navController.navigate("registration") { popUpTo("splash") { inclusive = true } } }
            )
        }
        composable("registration") {
            RegistrationScreen(onRegistered = {
                navController.navigate("tracking") { popUpTo("registration") { inclusive = true } }
            })
        }
        composable("tracking") {
            TrackingScreen(
                onLogout = { navController.navigate("registration") { popUpTo("tracking") { inclusive = true } } },
                onNavigateToEvents = { navController.navigate("events") },
                onNavigateToAttachments = { navController.navigate("attachments") }  // Sección 29
            )
        }
        composable("events") { EventsScreen(onBack = { navController.popBackStack() }) }
        composable("attachments") { AttachmentsScreen(onBack = { navController.popBackStack() }) }  // Sección 29
    }
}
