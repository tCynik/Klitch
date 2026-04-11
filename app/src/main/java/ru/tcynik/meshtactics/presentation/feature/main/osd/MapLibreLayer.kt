package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.value.FloatValue
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.sources.rememberRasterSource
import org.maplibre.compose.style.BaseStyle
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.domain.map.model.MapCameraPosition
import ru.tcynik.meshtactics.domain.marker.model.NodeMarkerModel
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.MarkerSizeConfig

// BaseStyle.Empty has no `glyphs` URL — SymbolLayer text rendering fails without it and breaks
// all other layers too. This style adds the MapLibre demotiles glyph server.
private val BASE_STYLE_WITH_GLYPHS = BaseStyle.Json(
    """{"version":8,"glyphs":"https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf","sources":{},"layers":[]}"""
)

@Composable
fun MapLibreLayer(
    modifier: Modifier = Modifier,
    tileUrlTemplate: String,
    initialCameraPosition: MapCameraPosition,
    onCameraPositionChanged: (MapCameraPosition) -> Unit,
    nodeMarkers: ImmutableList<NodeMarkerModel> = persistentListOf(),
    cameraState: CameraState,
    markerSizeLevel: Int = 5,
) {
    var hasUserMoved by remember { mutableStateOf(false) }

    LaunchedEffect(cameraState.isCameraMoving) {
        if (cameraState.isCameraMoving) {
            hasUserMoved = true
        } else if (hasUserMoved) {
            val pos = cameraState.position
            onCameraPositionChanged(
                MapCameraPosition(
                    lat     = pos.target.latitude,
                    lon     = pos.target.longitude,
                    zoom    = pos.zoom,
                    bearing = pos.bearing,
                )
            )
        }
    }

    MaplibreMap(
        modifier = modifier,
        baseStyle = BASE_STYLE_WITH_GLYPHS,
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

        val peerOnlineJson = remember(nodeMarkers) {
            buildNodeGeoJson(nodeMarkers.filter { it.isOnline })
        }
        val peerOfflineJson = remember(nodeMarkers) {
            buildNodeGeoJson(nodeMarkers.filter { !it.isOnline })
        }

        val peerOnlineSource  = rememberGeoJsonSource(GeoJsonData.JsonString(peerOnlineJson))
        val peerOfflineSource = rememberGeoJsonSource(GeoJsonData.JsonString(peerOfflineJson))

        val markerSize = MarkerSizeConfig.fromLevel(markerSizeLevel)
        val nodeMarkerRadius = markerSize / 2f
        val nodeMarkerStrokeWidth = nodeMarkerRadius / 4f
        val nodeIconSize = markerSize

        val stationaryPainter = painterResource(R.drawable.ic_node_marker_stationary)
        val movingPainter = painterResource(R.drawable.ic_node_marker_moving)

        // Online stationary nodes — diamond with 4 rounded corners (heading unknown)
        SymbolLayer(
            id = "node-online-stationary",
            source = peerOnlineSource,
            filter = !feature.has("bearing_known"),
            iconImage = image(stationaryPainter, size = DpSize(nodeIconSize, nodeIconSize)),
            iconSize = const(1f),
            iconRotationAlignment = const(IconRotationAlignment.Map),
            iconAllowOverlap = const(true),
        )

        // Online moving nodes — sharp top corner rotated to direction of travel
        SymbolLayer(
            id = "node-online-moving",
            source = peerOnlineSource,
            filter = feature.has("bearing_known"),
            iconImage = image(movingPainter, size = DpSize(nodeIconSize, nodeIconSize)),
            iconSize = const(1f),
            iconRotate = feature["bearing"].cast<FloatValue>(),
            iconRotationAlignment = const(IconRotationAlignment.Map),
            iconAllowOverlap = const(true),
        )

        CircleLayer(
            id = "node-remote-offline-dot",
            source = peerOfflineSource,
            color = const(Color(0xFF9E9E9E)),
            radius = const(nodeMarkerRadius),
            strokeColor = const(Color.White),
            strokeWidth = const(nodeMarkerStrokeWidth),
        )

        SymbolLayer(
            id = "node-remote-online-label",
            source = peerOnlineSource,
            textField = format(span(feature["longName"].asString())),
            textAnchor = const(SymbolAnchor.Bottom),
            textOffset = offset(0f.em, (-1.2f).em),
            textSize = const(12.sp),
            textColor = const(Color.White),
            textHaloColor = const(Color.Black),
            textHaloWidth = const(1.5.dp),
            textAllowOverlap = const(true),
        )
        SymbolLayer(
            id = "node-remote-offline-label",
            source = peerOfflineSource,
            textField = format(span(feature["longName"].asString())),
            textAnchor = const(SymbolAnchor.Bottom),
            textOffset = offset(0f.em, (-1.2f).em),
            textSize = const(12.sp),
            textColor = const(Color(0xFFBDBDBD)),
            textHaloColor = const(Color.Black),
            textHaloWidth = const(1.5.dp),
            textAllowOverlap = const(true),
        )

        // User location arrow is rendered as a Compose overlay in MainScreen.
    }
}

private fun buildNodeGeoJson(nodes: List<NodeMarkerModel>): String {
    val features = nodes.joinToString(",") { node ->
        val lon  = node.position.longitude
        val lat  = node.position.latitude
        val name = node.longName.replace("\\", "\\\\").replace("\"", "\\\"")
        val bearingProps = if (node.heading != null) {
            ""","bearing":${node.heading},"bearing_known":true"""
        } else {
            ""
        }
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"longName":"$name"$bearingProps}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}
