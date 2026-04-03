package ru.tcynik.meshtactics.presentation.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.tcynik.meshtactics.presentation.feature.main.osd.HudControlsLayer
import ru.tcynik.meshtactics.presentation.feature.main.osd.MapLibreLayer

@Composable
fun MainScreen(
    uiState: MainUiState,
    onChatClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNodeStatusClick: () -> Unit,
    onMarkerManagementClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1–5: all spatial content inside MapLibre
        if (uiState.tileUrlTemplate.isNotEmpty()) {
            MapLibreLayer(
                modifier = Modifier.fillMaxSize(),
                tileUrlTemplate = uiState.tileUrlTemplate,
            )
        }

        // Layer 6: HUD button columns (left: map tools, right: menu)
        HudControlsLayer(
            modifier = Modifier.fillMaxSize(),
            onChatClick = onChatClick,
            onSettingsClick = onSettingsClick,
            onNodeStatusClick = onNodeStatusClick,
            onMarkerManagementClick = onMarkerManagementClick,
        )
    }
}
