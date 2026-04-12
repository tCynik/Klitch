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
import kotlinx.coroutines.delay
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.eq
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

// Animation duration for marker position interpolation, in milliseconds.
private const val MARKER_ANIMATION_MS = 500L

// Frame interval targeting ~60 FPS.
private const val FRAME_INTERVAL_MS = 16L

/**
 * Quadratic ease-in-out for smooth start and stop.
 */
private fun quadraticEaseInOut(t: Float): Float {
    return if (t < 0.5f) 2f * t * t else -1f + (4f - 2f * t) * t
}

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

        val (animatedOnlineJson, animatedOfflineJson, animatedStaleJson) = animateGeoJsonInterpolation(nodeMarkers)

        val peerOnlineSource  = rememberGeoJsonSource(GeoJsonData.JsonString(animatedOnlineJson))
        val peerOfflineSource = rememberGeoJsonSource(GeoJsonData.JsonString(animatedOfflineJson))
        val peerStaleSource   = rememberGeoJsonSource(GeoJsonData.JsonString(animatedStaleJson))

        val markerSize = MarkerSizeConfig.fromLevel(markerSizeLevel)
        val nodeMarkerRadius = MarkerSizeConfig.nodeMarkerRadius(markerSize)
        val nodeMarkerStrokeWidth = MarkerSizeConfig.nodeMarkerStrokeWidth(markerSize)
        val nodeIconSize = markerSize

        // Stale nodes (position older than 2 min) — grey circle + grey label
        CircleLayer(
            id = "node-stale-dot",
            source = peerStaleSource,
            color = const(Color(0xFF9E9E9E)),
            radius = const(nodeMarkerRadius),
            strokeColor = const(Color.White),
            strokeWidth = const(nodeMarkerStrokeWidth),
        )

        SymbolLayer(
            id = "node-stale-label",
            source = peerStaleSource,
            textField = format(span(feature["longName"].asString())),
            textAnchor = const(SymbolAnchor.Bottom),
            textOffset = offset(0f.em, (-1.2f).em),
            textSize = const(12.sp),
            textColor = const(Color(0xFF9E9E9E)),
            textHaloColor = const(Color.Black),
            textHaloWidth = const(1.5.dp),
            textAllowOverlap = const(true),
        )

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
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"longName":"$name","isStale":${node.isStale}$bearingProps}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

/**
 * Holds the previous and current marker lists to interpolate between.
 */
private data class MarkerAnimationState(
    val previous: List<NodeMarkerModel> = emptyList(),
    val target: List<NodeMarkerModel> = emptyList(),
)

/**
 * Interpolates marker positions between the previous and current [nodeMarkers] list.
 *
 * When the list changes (positions or contents), this launches a coroutine that emits
 * intermediate GeoJSON strings at ~60 fps, smoothly transitioning from old to new
 * coordinates using quadratic ease-in-out.
 *
 * New markers (present in target but not in previous) appear instantly at their target
 * position. Removed markers vanish instantly. Only existing markers with changed positions
 * are animated.
 *
 * Returns a triple of `(onlineGeoJson, offlineGeoJson, staleGeoJson)` that updates every animation frame.
 */
@Composable
private fun animateGeoJsonInterpolation(
    nodeMarkers: ImmutableList<NodeMarkerModel>,
): Triple<String, String, String> {
    var animationState by remember { mutableStateOf(MarkerAnimationState()) }
    var animatedOnlineJson by remember { mutableStateOf(buildNodeGeoJson(nodeMarkers.filter { it.isOnline && !it.isStale })) }
    var animatedOfflineJson by remember { mutableStateOf(buildNodeGeoJson(nodeMarkers.filter { !it.isOnline && !it.isStale })) }
    var animatedStaleJson by remember { mutableStateOf(buildNodeGeoJson(nodeMarkers.filter { it.isStale })) }

    // Detect changes in the nodeMarkers list and start animation.
    LaunchedEffect(nodeMarkers) {
        val previous = animationState.target
        val target = nodeMarkers.toList()
        animationState = MarkerAnimationState(previous, target)

        // Build lookup: previous positions keyed by nodeId.
        val previousPositions = previous.associateBy { it.nodeId }

        val totalFrames = (MARKER_ANIMATION_MS / FRAME_INTERVAL_MS).toInt()
        for (frame in 0..totalFrames) {
            val rawT = frame.toFloat() / totalFrames
            val t = quadraticEaseInOut(rawT)

            // Interpolate each marker's position.
            val interpolated = target.map { targetNode ->
                val prev = previousPositions[targetNode.nodeId]
                if (prev != null) {
                    // Only interpolate if the position actually changed.
                    val startLon = prev.position.longitude
                    val startLat = prev.position.latitude
                    val endLon = targetNode.position.longitude
                    val endLat = targetNode.position.latitude
                    if (startLon != endLon || startLat != endLat) {
                        val interpLon = startLon + (endLon - startLon) * t
                        val interpLat = startLat + (endLat - startLat) * t
                        targetNode.copy(
                            position = ru.tcynik.meshtactics.domain.marker.model.GeoPoint(interpLat, interpLon),
                        )
                    } else {
                        targetNode
                    }
                } else {
                    // New marker — no previous position to interpolate from.
                    targetNode
                }
            }

            animatedOnlineJson = buildNodeGeoJson(interpolated.filter { it.isOnline && !it.isStale })
            animatedOfflineJson = buildNodeGeoJson(interpolated.filter { !it.isOnline && !it.isStale })
            animatedStaleJson = buildNodeGeoJson(interpolated.filter { it.isStale })

            if (frame < totalFrames) {
                delay(FRAME_INTERVAL_MS)
            }
        }
    }

    return Triple(animatedOnlineJson, animatedOfflineJson, animatedStaleJson)
}
