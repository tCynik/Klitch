package ru.tcynik.meshtactics.presentation.feature.main.osd

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
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
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.di.orientation.DeviceOrientationProvider
import ru.tcynik.meshtactics.domain.map.model.MapCameraPosition
import ru.tcynik.meshtactics.domain.marker.model.NodeMarkerModel

@Composable
fun MapLibreLayer(
    modifier: Modifier = Modifier,
    tileUrlTemplate: String,
    initialCameraPosition: MapCameraPosition,
    onCameraPositionChanged: (MapCameraPosition) -> Unit,
    locationProvider: LocationProvider,
    orientationProvider: DeviceOrientationProvider,
    nodeMarkers: ImmutableList<NodeMarkerModel> = persistentListOf(),
    cameraState: CameraState,
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

        val peerOnlineJson = remember(nodeMarkers) {
            buildNodeGeoJson(nodeMarkers.filter { it.isOnline })
        }
        val peerOfflineJson = remember(nodeMarkers) {
            buildNodeGeoJson(nodeMarkers.filter { !it.isOnline })
        }

        val peerOnlineSource  = rememberGeoJsonSource(GeoJsonData.JsonString(peerOnlineJson))
        val peerOfflineSource = rememberGeoJsonSource(GeoJsonData.JsonString(peerOfflineJson))

        CircleLayer(
            id = "node-remote-online-dot",
            source = peerOnlineSource,
            color = const(Color(0xFF4CAF50)),
            radius = const(6.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(1.5.dp),
        )
        CircleLayer(
            id = "node-remote-offline-dot",
            source = peerOfflineSource,
            color = const(Color(0xFF9E9E9E)),
            radius = const(6.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(1.5.dp),
        )

        // ── User location dot ─────────────────────────────────────────────
        val currentLocation by locationProvider.location.collectAsStateWithLifecycle()
        val bearing by orientationProvider.bearing.collectAsStateWithLifecycle()

        val locationGeoJson = remember(currentLocation, bearing) {
            val loc = currentLocation
            if (loc != null) {
                val lon = loc.position.longitude
                val lat = loc.position.latitude
                val bearingValue = bearing
                """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"bearing":$bearingValue}}]}"""
            } else {
                """{"type":"FeatureCollection","features":[]}"""
            }
        }
        val locationSource = rememberGeoJsonSource(GeoJsonData.JsonString(locationGeoJson))

        // User location arrow is rendered as an overlay in MainScreen — no dot needed.
    }
}

private fun buildNodeGeoJson(nodes: List<NodeMarkerModel>): String {
    val features = nodes.joinToString(",") { node ->
        val lon  = node.position.longitude
        val lat  = node.position.latitude
        val name = node.longName.replace("\\", "\\\\").replace("\"", "\\\"")
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"longName":"$name"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

@Suppress("unused")
private fun Context.drawableToImageBitmap(resId: Int): ImageBitmap {
    val drawable = ContextCompat.getDrawable(this, resId)!!
    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth,
        drawable.intrinsicHeight,
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}
