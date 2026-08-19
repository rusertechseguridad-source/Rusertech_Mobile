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
                onNavigateToTracking = { navController.navigate("tracking") { popUpTo("splash") { inclusive = true } } },
                onNavigateToModeSelection = { navController.navigate("mode_selection") { popUpTo("splash") { inclusive = true } } },
                onNeedsRegistration = { navController.navigate("registration") { popUpTo("splash") { inclusive = true } } }
            )
        }
        composable("registration") {
            RegistrationScreen(
                onRegistered = { navController.navigate("mode_selection") { popUpTo("registration") { inclusive = true } } }
            )
        }
        composable("mode_selection") {
            com.rusertech.mobile.ui.mode.ModeSelectionScreen(
                onNavigateToFreeTracking = { navController.navigate("tracking") { popUpTo("mode_selection") { inclusive = true } } },
                onNavigateToCreateTrip = { navController.navigate("create_trip") }
            )
        }
        composable("create_trip") {
            com.rusertech.mobile.ui.mode.CreateTripScreen(
                // B7: a create_trip se llega desde la selección de modo Y
                // desde Tracking Libre. popUpTo(0) limpia el back stack en
                // ambos caminos: queda [tracking] solo, sin pantallas viejas
                // debajo (un popUpTo por ruta fallaría en el camino que no
                // la tiene en el stack).
                onTripCreated = { navController.navigate("tracking") { popUpTo(0) { inclusive = true } } },
                onBack = { navController.popBackStack() }
            )
        }
        composable("tracking") {
            TrackingScreen(
                onLogout = { navController.navigate("registration") { popUpTo("tracking") { inclusive = true } } },
                // I2: al finalizar un viaje se vuelve a la selección de modo,
                // con la sesión intacta — jamás a la pantalla de registro.
                onTripFinished = { navController.navigate("mode_selection") { popUpTo("tracking") { inclusive = true } } },
                onNavigateToEvents = { navController.navigate("events") },
                onNavigateToAttachments = { navController.navigate("attachments") },
                onNavigateToMap = { navController.navigate("map") },
                onNavigateToCreateTrip = { navController.navigate("create_trip") }
            )
        }
        composable("events") { EventsScreen(onBack = { navController.popBackStack() }) }
        composable("attachments") { AttachmentsScreen(onBack = { navController.popBackStack() }) }
        composable("map") { com.rusertech.mobile.ui.map.MapScreen(onBack = { navController.popBackStack() }) }
    }
}
