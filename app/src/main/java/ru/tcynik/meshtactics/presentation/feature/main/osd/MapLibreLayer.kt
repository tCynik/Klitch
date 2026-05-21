package ru.tcynik.meshtactics.presentation.feature.main.osd

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.spatialk.geojson.Position
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.FloatValue
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.sources.rememberImageSource
import org.maplibre.compose.sources.rememberRasterSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.PositionQuad
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkColor
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkShape
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.TrackEndType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import ru.tcynik.meshtactics.presentation.feature.main.markToolMapTapGestures
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.domain.map.model.MapCameraPosition
import ru.tcynik.meshtactics.domain.marker.model.NodeMarkerModel
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.MarkerSizeConfig
import ru.tcynik.meshtactics.presentation.feature.main.osd.models.OverlayRenderModel

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
    geoMarkSizeLevel: Int = 5,
    showGeoMarkNames: Boolean = false,
    userPosition: Position? = null,
    userBearing: Float = 0f,
    selectedOverlays: ImmutableList<OverlayRenderModel> = persistentListOf(),
    geoMarks: ImmutableList<GeoMarkModel> = persistentListOf(),
    pendingMarkPoints: ImmutableList<ru.tcynik.meshtactics.domain.marker.model.GeoPoint> = persistentListOf(),
    pendingMarkColor: Int = 0,
    pendingMarkShape: GeoMarkShape = GeoMarkShape.CIRCLE,
    markToolActive: Boolean = false,
    isCourseUpActive: Boolean = false,
    onMapClick: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onMapDoubleClick: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onMapLongClick: (lat: Double, lon: Double, screenX: Float, screenY: Float) -> Unit = { _, _, _, _ -> },
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

    val mapModifier = modifier.then(
        if (markToolActive && !isCourseUpActive) {
            Modifier.markToolMapTapGestures(
                cameraState = cameraState,
                onMapClick = onMapClick,
                onMapDoubleClick = onMapDoubleClick,
                onMapLongClick = onMapLongClick,
            )
        } else {
            Modifier
        },
    )

    MaplibreMap(
        modifier = mapModifier,
        baseStyle = BASE_STYLE_WITH_GLYPHS,
        cameraState = cameraState,
        options = when {
            markToolActive && isCourseUpActive -> MapOptions(
                gestureOptions = GestureOptions(
                    isScrollEnabled = false,
                    isDoubleTapEnabled = false,
                    isQuickZoomEnabled = false,
                ),
                ornamentOptions = OrnamentOptions(isCompassEnabled = false),
            )
            markToolActive -> MapOptions(
                gestureOptions = GestureOptions(isDoubleTapEnabled = false, isQuickZoomEnabled = false),
                ornamentOptions = OrnamentOptions(isCompassEnabled = false),
            )
            isCourseUpActive -> MapOptions(
                gestureOptions = GestureOptions.PositionLocked,
                ornamentOptions = OrnamentOptions(isCompassEnabled = false),
            )
            else -> MapOptions(ornamentOptions = OrnamentOptions(isCompassEnabled = false))
        },
        onMapClick = { position, _ ->
            if (!markToolActive) {
                onMapClick(position.latitude, position.longitude)
            }
            ClickResult.Pass
        },
        onMapLongClick = { position, dpOffset ->
            if (!markToolActive) {
                onMapLongClick(
                    position.latitude, position.longitude,
                    dpOffset.x.value, dpOffset.y.value,
                )
            }
            ClickResult.Pass
        },
    ) {
        val circleBitmap = remember { createShapeBitmap(64, GeoMarkShape.CIRCLE).asImageBitmap() }
        val squareBitmap = remember { createShapeBitmap(64, GeoMarkShape.SQUARE).asImageBitmap() }
        val triangleBitmap = remember { createShapeBitmap(64, GeoMarkShape.TRIANGLE).asImageBitmap() }
        val arrowBitmap = remember { createArrowBitmap(64).asImageBitmap() }

        val tileSource = rememberRasterSource(
            tiles = listOf(tileUrlTemplate),
            tileSize = 256,
        )
        RasterLayer(
            id = "base-raster-layer",
            source = tileSource,
        )

        // ── Overlay layers (above base map, below node markers) ──────────────
        for (overlay in selectedOverlays) {
            // GroundOverlay raster
            val bitmap = overlay.groundOverlayBitmap
            val bounds = overlay.groundOverlayBounds
            if (bitmap != null && bounds != null) {
                val quad = PositionQuad(
                    topLeft     = Position(longitude = bounds.west, latitude = bounds.north),
                    topRight    = Position(longitude = bounds.east, latitude = bounds.north),
                    bottomRight = Position(longitude = bounds.east, latitude = bounds.south),
                    bottomLeft  = Position(longitude = bounds.west, latitude = bounds.south),
                )
                val imageSource = rememberImageSource(
                    position = quad,
                    bitmap = bitmap.asImageBitmap(),
                )
                RasterLayer(
                    id = "overlay-ground-${overlay.id}",
                    source = imageSource,
                )
            }

            // GeoJSON vector
            val geoJson = overlay.geoJson
            if (geoJson != null) {
                val geoSource = rememberGeoJsonSource(GeoJsonData.JsonString(geoJson))
                FillLayer(
                    id = "overlay-fill-${overlay.id}",
                    source = geoSource,
                )
                LineLayer(
                    id = "overlay-line-${overlay.id}",
                    source = geoSource,
                )
            }
        }

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

        // User location arrow — rendered as SymbolLayer so the Box stays at 2 layers.
        val userLocationSource = rememberGeoJsonSource(GeoJsonData.JsonString(buildUserLocationGeoJson(userPosition)))
        val navigationArrowPainter = painterResource(R.drawable.ic_navigation_arrow)
        SymbolLayer(
            id = "user-location-arrow",
            source = userLocationSource,
            iconImage = image(navigationArrowPainter, size = DpSize(nodeIconSize, nodeIconSize)),
            iconSize = const(1f),
            iconRotate = const(userBearing),
            iconRotationAlignment = const(IconRotationAlignment.Map),
            iconAllowOverlap = const(true),
        )

        // ── Geo marks ────────────────────────────────────────────────────────
        val geoMarkIconSize = (36 + (geoMarkSizeLevel - 1) * 6) / 64f

        // Draft (unsent) marks — shape icon in selected color + line
        val draftPointsSource = rememberGeoJsonSource(
            GeoJsonData.JsonString(buildDraftPointsGeoJson(pendingMarkPoints.toList()))
        )
        val draftColor = Color(GeoMarkColor.colorAt(pendingMarkColor))
        val pendingShapeBitmap = when (pendingMarkShape) {
            GeoMarkShape.CIRCLE   -> circleBitmap
            GeoMarkShape.SQUARE   -> squareBitmap
            GeoMarkShape.TRIANGLE -> triangleBitmap
        }
        val draftLastPointSource = rememberGeoJsonSource(
            GeoJsonData.JsonString(buildDraftLastPointGeoJson(pendingMarkPoints.toList()))
        )
        val draftRingRadius = ((36 + (geoMarkSizeLevel - 1) * 6) / 4).dp
        CircleLayer(
            id = "geo-draft-points-ring",
            source = draftLastPointSource,
            color = const(Color(0x00000000)),
            radius = const(draftRingRadius),
            strokeColor = const(Color.Red),
            strokeWidth = const(3.dp),
        )
        SymbolLayer(
            id = "geo-draft-points",
            source = draftPointsSource,
            iconImage = image(pendingShapeBitmap, isSdf = true),
            iconColor = const(draftColor),
            iconSize = const(geoMarkIconSize),
            iconHaloColor = const(Color.White),
            iconHaloWidth = const(1.dp),
            iconAllowOverlap = const(true),
        )
        val draftLineSource = rememberGeoJsonSource(
            GeoJsonData.JsonString(buildDraftLineGeoJson(pendingMarkPoints.toList()))
        )
        LineLayer(
            id = "geo-draft-line-outline",
            source = draftLineSource,
            color = const(Color(0x80000000)),
            width = const(4.dp),
        )
        LineLayer(
            id = "geo-draft-line",
            source = draftLineSource,
            color = const(draftColor),
            width = const(2.dp),
        )

        val receivedPoints = geoMarks.filter { it.type == GeoMarkType.POINT && it.isVisible }
        val receivedTracks = geoMarks.filter { it.type == GeoMarkType.TRACK && it.isVisible }

        val receivedPointsSource = rememberGeoJsonSource(
            GeoJsonData.JsonString(buildReceivedPointsGeoJson(receivedPoints))
        )
        SymbolLayer(
            id = "geo-received-points",
            source = receivedPointsSource,
            iconImage = switch(
                input = feature["shapeOrdinal"].cast<FloatValue>(),
                case(GeoMarkShape.SQUARE.ordinal, image(squareBitmap, isSdf = true)),
                case(GeoMarkShape.TRIANGLE.ordinal, image(triangleBitmap, isSdf = true)),
                fallback = image(circleBitmap, isSdf = true),
            ),
            iconColor = feature["color"].convertToColor(const(Color(0xFF1E88E5))),
            iconSize = const(geoMarkIconSize),
            iconHaloColor = const(Color.White),
            iconHaloWidth = const(1.dp),
            iconAllowOverlap = const(true),
        )
        if (showGeoMarkNames) {
            SymbolLayer(
                id = "geo-received-point-labels",
                source = receivedPointsSource,
                textField = format(span(feature["name"].asString())),
                textAnchor = const(SymbolAnchor.Top),
                textOffset = offset(0f.em, 1.2f.em),
                textSize = const(11.sp),
                textColor = const(Color.White),
                textHaloColor = const(Color.Black),
                textHaloWidth = const(1.5.dp),
                textOptional = const(true),
                textAllowOverlap = const(false),
            )
        }

        val receivedTracksSource = rememberGeoJsonSource(
            GeoJsonData.JsonString(buildReceivedTracksGeoJson(receivedTracks))
        )
        LineLayer(
            id = "geo-received-tracks-outline",
            source = receivedTracksSource,
            color = const(Color(0x80000000)),
            width = const(5.dp),
        )
        LineLayer(
            id = "geo-received-tracks",
            source = receivedTracksSource,
            color = feature["color"].convertToColor(const(Color(0xFFE53935))),
            width = const(3.dp),
        )
        val trackAnchorsSource = rememberGeoJsonSource(
            GeoJsonData.JsonString(buildTrackAnchorsGeoJson(receivedTracks))
        )
        SymbolLayer(
            id = "geo-received-track-anchors",
            source = trackAnchorsSource,
            iconImage = image(circleBitmap, isSdf = true),
            iconColor = feature["color"].convertToColor(const(Color(0xFF1E88E5))),
            iconSize = const(0.35f),
            iconHaloColor = const(Color.White),
            iconHaloWidth = const(1.dp),
            iconAllowOverlap = const(true),
        )

        val trackEndpointsSource = rememberGeoJsonSource(
            GeoJsonData.JsonString(buildTrackEndpointsGeoJson(receivedTracks))
        )
        SymbolLayer(
            id = "geo-track-endpoints",
            source = trackEndpointsSource,
            iconImage = switch(
                input = feature["endType"].cast<FloatValue>(),
                case(TrackEndType.ARROW.ordinal, image(arrowBitmap, isSdf = true)),
                case(TrackEndType.SMALL_FILLED_CIRCLE.ordinal, image(circleBitmap, isSdf = true)),
                fallback = image(circleBitmap, isSdf = true),
            ),
            iconColor = feature["color"].convertToColor(const(Color(0xFFE53935))),
            iconSize = const(0.9f),
            iconRotate = feature["bearing"].cast<FloatValue>(),
            iconRotationAlignment = const(IconRotationAlignment.Map),
            iconHaloColor = const(Color.White),
            iconHaloWidth = const(1.dp),
            iconAllowOverlap = const(true),
        )
    }
}

private fun buildUserLocationGeoJson(position: Position?): String {
    if (position == null) return """{"type":"FeatureCollection","features":[]}"""
    return """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${position.longitude},${position.latitude}]},"properties":{}}]}"""
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

// ── Geo mark GeoJSON builders ─────────────────────────────────────────────────

private fun buildDraftPointsGeoJson(
    points: List<ru.tcynik.meshtactics.domain.marker.model.GeoPoint>,
): String {
    if (points.isEmpty()) return """{"type":"FeatureCollection","features":[]}"""
    val features = points.joinToString(",") { pt ->
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${pt.longitude},${pt.latitude}]},"properties":{}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

private fun buildDraftLastPointGeoJson(
    points: List<ru.tcynik.meshtactics.domain.marker.model.GeoPoint>,
): String {
    val last = points.lastOrNull() ?: return """{"type":"FeatureCollection","features":[]}"""
    return """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${last.longitude},${last.latitude}]},"properties":{}}]}"""
}

private fun buildDraftLineGeoJson(
    points: List<ru.tcynik.meshtactics.domain.marker.model.GeoPoint>,
): String {
    if (points.size < 2) return """{"type":"FeatureCollection","features":[]}"""
    val coords = points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    return """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{}}]}"""
}

private fun buildReceivedPointsGeoJson(marks: List<GeoMarkModel>): String {
    if (marks.isEmpty()) return """{"type":"FeatureCollection","features":[]}"""
    val features = marks.joinToString(",") { mark ->
        val anchor = mark.points.first()
        val hex = markColorHex(mark.color)
        val shapeOrdinal = mark.shape.ordinal
        val name = mark.name.jsonEscape()
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${anchor.longitude},${anchor.latitude}]},"properties":{"color":"$hex","shapeOrdinal":$shapeOrdinal,"name":"$name"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

private fun String.jsonEscape(): String = replace("\\", "\\\\").replace("\"", "\\\"")

private fun buildReceivedTracksGeoJson(marks: List<GeoMarkModel>): String {
    if (marks.isEmpty()) return """{"type":"FeatureCollection","features":[]}"""
    val features = marks
        .filter { it.points.size >= 2 }
        .joinToString(",") { mark ->
            val coords = mark.points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
            val hex = markColorHex(mark.color)
            """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{"color":"$hex"}}"""
        }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

/** Start (anchor) point of each track — rendered as a small circle. */
private fun buildTrackAnchorsGeoJson(marks: List<GeoMarkModel>): String {
    if (marks.isEmpty()) return """{"type":"FeatureCollection","features":[]}"""
    val features = marks.mapNotNull { mark ->
        val hex = markColorHex(mark.color)
        mark.points.firstOrNull()?.let { pt ->
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[${pt.longitude},${pt.latitude}]},"properties":{"color":"$hex"}}"""
        }
    }.joinToString(",")
    return """{"type":"FeatureCollection","features":[$features]}"""
}

private fun bearingDegrees(
    from: ru.tcynik.meshtactics.domain.marker.model.GeoPoint,
    to: ru.tcynik.meshtactics.domain.marker.model.GeoPoint,
): Double {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

private fun buildTrackEndpointsGeoJson(marks: List<GeoMarkModel>): String {
    if (marks.isEmpty()) return """{"type":"FeatureCollection","features":[]}"""
    val features = marks
        .filter { it.trackEndType != TrackEndType.NONE && it.points.size >= 2 }
        .joinToString(",") { mark ->
            val last = mark.points.last()
            val prev = mark.points[mark.points.size - 2]
            val bearing = bearingDegrees(prev, last)
            val hex = markColorHex(mark.color)
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[${last.longitude},${last.latitude}]},"properties":{"color":"$hex","endType":${mark.trackEndType.ordinal},"bearing":$bearing}}"""
        }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

private fun markColorHex(colorIndex: Int): String {
    val argb = GeoMarkColor.colorAt(colorIndex)
    return "#%02X%02X%02X".format(
        (argb shr 16) and 0xFF,
        (argb shr 8) and 0xFF,
        argb and 0xFF,
    )
}

private fun createArrowBitmap(size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = size * 0.18f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val f = size.toFloat()
    val cx = f / 2f
    val tipY = f * 0.12f
    val baseY = f * 0.85f
    val wingY = f * 0.48f
    val wingX = f * 0.33f
    canvas.drawLine(cx, baseY, cx, tipY, paint)
    canvas.drawLine(cx, tipY, cx - wingX, wingY, paint)
    canvas.drawLine(cx, tipY, cx + wingX, wingY, paint)
    return bitmap
}

private fun createShapeBitmap(size: Int, shape: GeoMarkShape): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val f = size.toFloat()
    val pad = f * 0.05f
    when (shape) {
        GeoMarkShape.CIRCLE -> canvas.drawCircle(f / 2f, f / 2f, f / 2f - pad, paint)
        GeoMarkShape.SQUARE -> canvas.drawRect(pad, pad, f - pad, f - pad, paint)
        GeoMarkShape.TRIANGLE -> {
            val path = Path().apply {
                moveTo(f / 2f, pad)
                lineTo(f - pad, f - pad)
                lineTo(pad, f - pad)
                close()
            }
            canvas.drawPath(path, paint)
        }
    }
    return bitmap
}
