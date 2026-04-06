package ru.tcynik.meshtactics.presentation.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.compose.location.LocationProvider
import ru.tcynik.meshtactics.domain.map.model.MapCameraPosition
import ru.tcynik.meshtactics.presentation.feature.main.osd.HudControlsLayer
import ru.tcynik.meshtactics.presentation.feature.main.osd.MapLibreLayer

@Composable
fun MainScreen(
    uiState: MainUiState,
    onCameraPositionChanged: (MapCameraPosition) -> Unit,
    onChatClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNodeStatusClick: () -> Unit,
    onMarkerManagementClick: () -> Unit,
    locationProvider: LocationProvider,
) {
    // Tracks the last position reported by MapLibreLayer.
    // Used by strategy C (onPause) to persist position when app goes to background.
    var lastKnownPosition by remember { mutableStateOf(uiState.initialCameraPosition) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                onCameraPositionChanged(lastKnownPosition)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1–5: all spatial content inside MapLibre
        if (uiState.tileUrlTemplate.isNotEmpty()) {
            MapLibreLayer(
                modifier = Modifier.fillMaxSize(),
                tileUrlTemplate = uiState.tileUrlTemplate,
                initialCameraPosition = uiState.initialCameraPosition,
                onCameraPositionChanged = { position ->
                    lastKnownPosition = position      // update tracker for strategy C
                    onCameraPositionChanged(position) // strategy A save
                },
                locationProvider = locationProvider,
                nodeMarkers = uiState.nodeMarkers,
            )
        }

        // Layer 6: HUD button columns (left: map tools, right: menu)
        HudControlsLayer(
            modifier = Modifier.fillMaxSize(),
            connectionStatus = uiState.connectionStatus,
            nodesWithPositionCount = uiState.nodeMarkers.size,
            onChatClick = onChatClick,
            onSettingsClick = onSettingsClick,
            onNodeStatusClick = onNodeStatusClick,
            onMarkerManagementClick = onMarkerManagementClick,
        )
    }
}
