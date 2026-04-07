package ru.tcynik.meshtactics.presentation.feature.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.location.LocationProvider
import org.maplibre.spatialk.geojson.Position
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.di.orientation.DeviceOrientationProvider
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
    orientationProvider: DeviceOrientationProvider,
) {
    var lastKnownPosition by remember { mutableStateOf(uiState.initialCameraPosition) }
    val density = LocalDensity.current

    // CameraState created here so both MapLibreLayer and overlay can use it
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(
                longitude = uiState.initialCameraPosition.lon,
                latitude  = uiState.initialCameraPosition.lat,
            ),
            zoom = uiState.initialCameraPosition.zoom,
        ),
    )

    // Track the size of the map container to convert projection coordinates to
    // overlay (Box-relative) offsets.  Projection returns DP offsets from the
    // top-left of the map view; we convert to pixels using the measured size.
    var mapSize by remember { mutableStateOf(IntSize.Zero) }

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

    val bearing by orientationProvider.bearing.collectAsStateWithLifecycle()
    val currentLocation by locationProvider.location.collectAsStateWithLifecycle()

    // Camera bearing — used to correct the arrow rotation when the map is rotated
    val cameraBearing by remember {
        derivedStateOf { cameraState.position.bearing.toFloat() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { mapSize = it },
    ) {
        if (uiState.tileUrlTemplate.isNotEmpty()) {
            MapLibreLayer(
                modifier = Modifier.fillMaxSize(),
                tileUrlTemplate = uiState.tileUrlTemplate,
                initialCameraPosition = uiState.initialCameraPosition,
                onCameraPositionChanged = { position ->
                    lastKnownPosition = position
                    onCameraPositionChanged(position)
                },
                nodeMarkers = uiState.nodeMarkers,
                cameraState = cameraState,
            )
        }

        // ── User location arrow overlay ──────────────────────────────────
        val projection = cameraState.projection
        if (projection != null && mapSize != IntSize.Zero && currentLocation != null) {
            val screenOffset: DpOffset = projection.screenLocationFromPosition(
                Position(
                    longitude = currentLocation!!.position.longitude,
                    latitude  = currentLocation!!.position.latitude,
                ),
            )

            // Convert DP to pixels using the map container density
            val arrowOffsetX = with(density) { screenOffset.x.roundToPx() }
            val arrowOffsetY = with(density) { screenOffset.y.roundToPx() }

            // Center the arrow on the point (icon is 24dp, center = 12dp offset)
            val halfIconPx = with(density) { 18.dp.roundToPx() }

            Image(
                painter = painterResource(R.drawable.ic_navigation_arrow),
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset(arrowOffsetX - halfIconPx, arrowOffsetY - halfIconPx) }
                    .size(36.dp)
                    .rotate(bearing + 180f - cameraBearing),
            )
        }

        // HUD button columns
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
