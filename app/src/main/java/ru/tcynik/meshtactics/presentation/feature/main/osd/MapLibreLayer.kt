package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.rememberRasterSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

// Default camera: Moscow. Replaced by GPS position once location is available.
private val DEFAULT_POSITION = Position(longitude = 37.6173, latitude = 55.7558)
private const val DEFAULT_ZOOM = 10.0

@Composable
fun MapLibreLayer(
    modifier: Modifier = Modifier,
    tileUrlTemplate: String,
) {
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = DEFAULT_POSITION,
            zoom = DEFAULT_ZOOM,
        ),
    )

    // rememberRasterSource must be called inside MaplibreMap content lambda —
    // it reads LocalStyleNode which is only provided within that scope.
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
    }
}
