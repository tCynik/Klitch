package ru.tcynik.meshtactics.presentation.feature.main.osd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.map.MaplibreMap
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
