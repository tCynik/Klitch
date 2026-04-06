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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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
import ru.tcynik.meshtactics.domain.marker.model.NodeMarkerModel

@Composable
fun MapLibreLayer(
    modifier: Modifier = Modifier,
    tileUrlTemplate: String,
    initialCameraPosition: MapCameraPosition,
    onCameraPositionChanged: (MapCameraPosition) -> Unit,
    locationProvider: LocationProvider,
    nodeMarkers: ImmutableList<NodeMarkerModel> = persistentListOf(),
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

        // --- Node markers ---
        // Two separate GeoJSON sources to render remote nodes and our node with different colours.
        // Uses raw GeoJSON string construction — same spatialk-bypass pattern as user location dot.
        // TODO: replace CircleLayer with custom icon sprites when SymbolLayer iconImage API
        //       (style.addImage / rememberStyleImage) is confirmed in maplibre-compose.
        // TODO: add text labels (longName) via SymbolLayer once textField expression API is verified.
        // TODO: add tap behaviour (node detail popup / navigation).

        val remoteNodesJson = remember(nodeMarkers) {
            buildNodeGeoJson(nodeMarkers.filter { !it.isOurNode })
        }
        val ourNodeJson = remember(nodeMarkers) {
            buildNodeGeoJson(nodeMarkers.filter { it.isOurNode })
        }

        val remoteNodesSource = rememberGeoJsonSource(GeoJsonData.JsonString(remoteNodesJson))
        val ourNodeSource     = rememberGeoJsonSource(GeoJsonData.JsonString(ourNodeJson))

        // Remote nodes — grey dot
        CircleLayer(
            id = "node-remote-dot",
            source = remoteNodesSource,
            color = const(Color(0xFF9E9E9E)),   // Material Grey 500
            radius = const(6.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(1.5.dp),
        )
        // Our node — green dot
        // TODO: replace with directional icon when device-heading feature is implemented.
        CircleLayer(
            id = "node-our-dot",
            source = ourNodeSource,
            color = const(Color(0xFF4CAF50)),   // Material Green 500
            radius = const(8.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
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

/**
 * Builds a GeoJSON FeatureCollection string from a list of node markers.
 * Uses raw string construction — same spatialk-bypass pattern as user location dot.
 * The [NodeMarkerModel.longName] is included as a feature property for future SymbolLayer text labels.
 */
private fun buildNodeGeoJson(nodes: List<NodeMarkerModel>): String {
    val features = nodes.joinToString(",") { node ->
        val lon  = node.position.longitude
        val lat  = node.position.latitude
        val name = node.longName.replace("\\", "\\\\").replace("\"", "\\\"")
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"longName":"$name"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}
