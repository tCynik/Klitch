package ru.tcynik.meshtactics.presentation.feature.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
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

// MapLibre uses 512 px tiles: mpp = 40_075_016 / (512 * 2^zoom) = 78_271 / 2^zoom
private fun metersPerPixel(latRad: Double, zoom: Double): Double =
    78271.51696 * cos(latRad) / 2.0.pow(zoom)

@Composable
fun MainScreen(
    uiState: MainUiState,
    hudConfig: HudConfig,
    hudUiState: HudUiState,
    onCameraPositionChanged: (MapCameraPosition) -> Unit,
    locationProvider: LocationProvider,
    orientationProvider: DeviceOrientationProvider,
    onMapClick: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onMapDoubleClick: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onMapLongClick: (lat: Double, lon: Double, screenX: Float, screenY: Float) -> Unit = { _, _, _, _ -> },
    contextMenuEvents: Flow<GeoMarkContextMenuEvent> = emptyFlow(),
    menuDrawerUiState: MenuDrawerUiState,
    geoMarksSheetUiState: GeoMarksSheetUiState,
    onFollowMeDeactivated: () -> Unit = {},
    resetBearingEvents: Flow<Unit> = emptyFlow(),
    restoreZoomEvents: Flow<Double> = emptyFlow(),
    onMapBearingChanged: (Double) -> Unit = {},
    onMapRotatedByUser: (Double) -> Unit = {},
    onCourseUpToggle: (Double) -> Unit = {},
    onFollowMeRestoreZoom: () -> Unit = {},
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
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
        if (uiState.isFollowMeActive && !uiState.isCourseUpActive) {
            val pos = currentLocation?.position ?: return@LaunchedEffect
            cameraState.animateTo(
                finalPosition = CameraPosition(
                    target = pos,
                    zoom = cameraState.position.zoom,
                    bearing = cameraState.position.bearing,
                ),
                duration = 500.milliseconds,
            )
        }
    }

    LaunchedEffect(cameraState.moveReason) {
        if (cameraState.moveReason == CameraMoveReason.GESTURE) {
            if (uiState.isFollowMeActive && !uiState.isCourseUpActive) onFollowMeDeactivated()
            // course-up: scroll disabled, zoom gestures are intentional — never deactivate
        }
    }

    LaunchedEffect(resetBearingEvents) {
        resetBearingEvents.collect {
            val pos = cameraState.position
            cameraState.animateTo(
                finalPosition = CameraPosition(bearing = 0.0, target = pos.target, zoom = pos.zoom),
                duration = 300.milliseconds,
            )
        }
    }

    LaunchedEffect(restoreZoomEvents) {
        restoreZoomEvents.collect { zoom ->
            val pos = cameraState.position
            cameraState.animateTo(
                finalPosition = CameraPosition(target = pos.target, zoom = zoom, bearing = pos.bearing),
                duration = 300.milliseconds,
            )
        }
    }

    LaunchedEffect(cameraState.position.bearing) {
        val b = cameraState.position.bearing
        // pan leaves bearing unchanged → this effect won't fire for pan
        // animations (reset bearing, course-up) have non-GESTURE moveReason → don't clear north lock
        if (cameraState.moveReason == CameraMoveReason.GESTURE) onMapRotatedByUser(b)
        else onMapBearingChanged(b)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Actual rendered composable dimensions — more reliable than LocalConfiguration
        // (accounts for multi-window, foldables, insets not reflected in Configuration)
        val mapWidthDp  = maxWidth.value   // dp
        val mapHeightDp = maxHeight.value  // dp
        val mapHeightPx = with(density) { maxHeight.toPx() }  // physical px, for pan-as-zoom

        // Course-up: map rotates with device bearing; user marker stays at lower-third anchor
        LaunchedEffect(bearing, uiState.isCourseUpActive, currentLocation, cameraState.position.zoom) {
            if (!uiState.isCourseUpActive) return@LaunchedEffect
            val userPos = currentLocation?.position ?: return@LaunchedEffect
            val zoom = cameraState.position.zoom
            val userLat = userPos.latitude
            val userLon = userPos.longitude
            val userLatRad = Math.toRadians(userLat)
            val bearingRad = Math.toRadians(bearing.toDouble())
            val mpp = metersPerPixel(userLatRad, zoom)
            // offset in metres: anchor (W/2, H − W/2) is (H/2 − W/2) dp below screen centre
            val offsetM = (mapHeightDp / 2f - mapWidthDp / 2f) * mpp
            val newLat = userLat + offsetM * cos(bearingRad) / 111320.0
            val newLon = userLon + offsetM * sin(bearingRad) / (111320.0 * cos(userLatRad))
            cameraState.position = CameraPosition(
                bearing = bearing.toDouble(),
                target  = Position(longitude = newLon, latitude = newLat),
                zoom    = zoom,
            )
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
                geoMarkSizeLevel = uiState.geoMarkSizeLevel,
                showGeoMarkNames = uiState.showGeoMarkNames,
                userPosition = currentLocation?.position,
                userBearing = bearing,
                selectedOverlays = uiState.selectedOverlays,
                geoMarks = uiState.geoMarks,
                pendingMarkPoints = uiState.pendingMarkPoints,
                pendingMarkColor = geoMarksSheetUiState.selectedColor,
                pendingMarkShape = geoMarksSheetUiState.selectedShape,
                markToolActive = uiState.markToolActive,
                isCourseUpActive = uiState.isCourseUpActive,
                onMapClick = onMapClick,
                onMapDoubleClick = onMapDoubleClick,
                onMapLongClick = onMapLongClick,
            )
        }

        if (uiState.isCourseUpActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .courseUpMapGestures(
                        mapHeightPx = mapHeightPx,
                        markToolActive = uiState.markToolActive,
                        cameraState = cameraState,
                        onMapClick = onMapClick,
                        onMapDoubleClick = onMapDoubleClick,
                    ),
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
                onCompassLongClick = {
                    if (currentLocation?.position != null) {
                        onCourseUpToggle(cameraState.position.zoom)
                    }
                },
                onFollowMeClick = {
                    if (uiState.isCourseUpActive) onFollowMeRestoreZoom() else hudUiState.target.button.onClick()
                },
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
        } // Box
    } // BoxWithConstraints
}
