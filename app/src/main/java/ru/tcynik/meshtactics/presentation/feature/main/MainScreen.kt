package ru.tcynik.meshtactics.presentation.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import org.koin.compose.viewmodel.koinViewModel
import ru.tcynik.meshtactics.navigation.Route
import ru.tcynik.meshtactics.presentation.feature.main.osd.HudControlsLayer
import ru.tcynik.meshtactics.presentation.feature.main.osd.MapLibreLayer

@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel = koinViewModel(),
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1–5: all spatial content inside MapLibre
        MapLibreLayer(modifier = Modifier.fillMaxSize())

        // Layer 6: HUD button columns (left: map tools, right: menu)
        HudControlsLayer(
            modifier = Modifier.fillMaxSize(),
            onChatClick = { navController.navigate(Route.Chat) },
            onSettingsClick = { navController.navigate(Route.Settings) },
            onNodeStatusClick = { navController.navigate(Route.NodeStatus) },
            onMarkerManagementClick = { navController.navigate(Route.MarkerManagement) },
        )
    }
}
