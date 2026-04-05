package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.location.LocationProvider
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.sources.rememberRasterSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import ru.tcynik.meshtactics.domain.map.model.MapCameraPosition

@Composable
fun MapLibreLayer(
    modifier: Modifier = Modifier,
    tileUrlTemplate: String,
    initialCameraPosition: MapCameraPosition,
    onCameraPositionChanged: (MapCameraPosition) -> Unit,
    locationProvider: LocationProvider,
) {
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(
                longitude = initialCameraPosition.lon,
                latitude  = initialCameraPosition.lat,
            ),
            zoom = initialCameraPosition.zoom,
        ),
    )

    // Guard: isCameraMoving is false on initial composition before any user interaction.
    // Only save after the user has actually moved the map at least once.
    var hasUserMoved by remember { mutableStateOf(false) }

    LaunchedEffect(cameraState.isCameraMoving) {
        if (cameraState.isCameraMoving) {
            hasUserMoved = true
        } else if (hasUserMoved) {
            // Strategy A: camera stopped after user interaction — persist position.
            val pos = cameraState.position
            onCameraPositionChanged(
                MapCameraPosition(
                    lat  = pos.target.latitude,
                    lon  = pos.target.longitude,
                    zoom = pos.zoom,
                )
            )
        }
    }

    // All rememberXxxSource / layer calls must be inside MaplibreMap content lambda —
    // they read LocalStyleNode which is only provided within that scope.
    MaplibreMap(
        modifier = modifier,
        baseStyle = BaseStyle.Empty,
        cameraState = cameraState,
    ) {
        val tileSource = rememberRasterSource(
            tiles = listOf(tileUrlTemplate),
            tileSize = 256,
        )
        RasterLayer(
            id = "base-raster-layer",
            source = tileSource,
        )

        // User location dot — manual implementation that avoids LocationPuck/rememberUserLocationState.
        // Root cause: spatialk:geojson:0.6.0 crashes when serializing an empty FeatureCollection()
        // (firstNotNullOf throws on LocationPuck's initial null-location path).
        // Fix: use GeoJsonData.JsonString to bypass spatialk polymorphic serialization entirely.
        // TODO: add bearing arrow when Visual Language phase is implemented.
        val currentLocation by locationProvider.location.collectAsStateWithLifecycle()
        val locationGeoJson = remember(currentLocation) {
            val loc = currentLocation
            if (loc != null) {
                val lon = loc.position.longitude
                val lat = loc.position.latitude
                """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{}}]}"""
            } else {
                """{"type":"FeatureCollection","features":[]}"""
            }
        }
        val locationSource = rememberGeoJsonSource(GeoJsonData.JsonString(locationGeoJson))
        CircleLayer(
            id = "user-location-dot",
            source = locationSource,
            color = const(Color(0xFF2196F3)),
            radius = const(8.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
        )
    }
}
