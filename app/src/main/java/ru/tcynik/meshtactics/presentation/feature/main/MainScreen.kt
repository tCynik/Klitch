package ru.tcynik.meshtactics.presentation.feature.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import kotlin.time.Duration.Companion.milliseconds
import org.maplibre.compose.location.LocationProvider
import org.maplibre.spatialk.geojson.Position
import ru.tcynik.meshtactics.di.orientation.DeviceOrientationProvider
import ru.tcynik.meshtactics.domain.map.model.MapCameraPosition
import ru.tcynik.meshtactics.presentation.feature.main.osd.GeoMarksSheet
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.GeoMarkContextMenuEvent
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.GeoMarksSheetUiState
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.HudUiState
import ru.tcynik.meshtactics.presentation.feature.main.osd.HudControlsLayer
import ru.tcynik.meshtactics.presentation.feature.main.osd.HudPortraitControlsLayer
import ru.tcynik.meshtactics.presentation.feature.main.osd.MapLibreLayer
import ru.tcynik.meshtactics.presentation.feature.main.osd.MenuDrawer
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.MenuDrawerUiState
@Composable
fun MainScreen(
    uiState: MainUiState,
    hudConfig: HudConfig,
    hudUiState: HudUiState,
    onCameraPositionChanged: (MapCameraPosition) -> Unit,
    locationProvider: LocationProvider,
    orientationProvider: DeviceOrientationProvider,
    onMapClick: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onMapLongClick: (lat: Double, lon: Double, screenX: Float, screenY: Float) -> Unit = { _, _, _, _ -> },
    contextMenuEvents: Flow<GeoMarkContextMenuEvent> = emptyFlow(),
    menuDrawerUiState: MenuDrawerUiState,
    geoMarksSheetUiState: GeoMarksSheetUiState,
    onFollowMeDeactivated: () -> Unit = {},
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var lastKnownPosition by remember { mutableStateOf(uiState.initialCameraPosition) }
    var contextMenu by remember { mutableStateOf<GeoMarkContextMenuEvent?>(null) }

    LaunchedEffect(contextMenuEvents) {
        contextMenuEvents.collect { event -> contextMenu = event }
    }

    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(
                longitude = uiState.initialCameraPosition.lon,
                latitude  = uiState.initialCameraPosition.lat,
            ),
            zoom = uiState.initialCameraPosition.zoom,
        ),
    )

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

    LaunchedEffect(currentLocation, uiState.isFollowMeActive) {
        if (uiState.isFollowMeActive) {
            val pos = currentLocation?.position ?: return@LaunchedEffect
            cameraState.animateTo(
                finalPosition = CameraPosition(
                    target = pos,
                    zoom = cameraState.position.zoom,
                ),
                duration = 500.milliseconds,
            )
        }
    }

    LaunchedEffect(cameraState.moveReason) {
        if (cameraState.moveReason == CameraMoveReason.GESTURE && uiState.isFollowMeActive) {
            onFollowMeDeactivated()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                markerSizeLevel = uiState.markerSizeLevel,
                userPosition = currentLocation?.position,
                userBearing = bearing,
                selectedOverlays = uiState.selectedOverlays,
                geoMarks = uiState.geoMarks,
                pendingMarkPoints = uiState.pendingMarkPoints,
                pendingMarkColor = geoMarksSheetUiState.selectedColor,
                markToolActive = uiState.markToolActive,
                onMapClick = onMapClick,
                onMapLongClick = onMapLongClick,
            )
        }

        // HUD button columns
        if (isLandscape) {
            HudControlsLayer(
                config = hudConfig,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            HudPortraitControlsLayer(
                state = hudUiState,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // z3 — menu drawer overlay (portrait only)
        if (!isLandscape) {
            MenuDrawer(state = menuDrawerUiState)
        }

        // z4 — geo marks sheet (portrait only)
        if (!isLandscape) {
            GeoMarksSheet(
                state = geoMarksSheetUiState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        contextMenu?.let { event ->
            Box(Modifier.offset(event.screenX.dp, event.screenY.dp).size(0.dp)) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { contextMenu = null },
                ) {
                    DropdownMenuItem(
                        text = { Text("Удалить точку") },
                        onClick = {
                            geoMarksSheetUiState.onDeletePendingPoint(event.pointIndex)
                            contextMenu = null
                        },
                    )
                }
            }
        }
    }
}
