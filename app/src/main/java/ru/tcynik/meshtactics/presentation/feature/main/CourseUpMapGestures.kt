package ru.tcynik.meshtactics.presentation.feature.main

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.DpOffset
import kotlin.math.abs
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState

private const val ZOOM_LEVELS_PER_SCREEN_HEIGHT = 3.0
private const val MIN_ZOOM = 1.0
private const val MAX_ZOOM = 20.0

/**
 * Course-up overlay: single-finger vertical drag → zoom; short release under touch slop → map tap
 * (geo mark placement when [markToolActive]). Multi-touch is never classified as a tap.
 *
 * See `.claude/docs/map-orientation.md` — «Course-up + добавление геометок».
 */
fun Modifier.courseUpMapGestures(
    mapHeightPx: Float,
    markToolActive: Boolean,
    cameraState: CameraState,
    onMapClick: (lat: Double, lon: Double) -> Unit,
): Modifier = pointerInput(mapHeightPx, markToolActive, cameraState) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (currentEvent.changes.count { it.pressed } > 1) return@awaitEachGesture

        val pointerId = down.id
        val startY = down.position.y
        var lastY = startY
        var maxAbsDy = 0f
        var isZoomGesture = false

        val downOffset = DpOffset(
            x = with(density) { down.position.x.toDp() },
            y = with(density) { down.position.y.toDp() },
        )

        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } > 1) {
                isZoomGesture = true
                break
            }

            val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
            if (!change.pressed) {
                if (event.changes.none { it.pressed }) break
                continue
            }

            if (change.positionChange() != Offset.Zero) {
                val y = change.position.y
                maxAbsDy = maxOf(maxAbsDy, abs(y - startY))

                if (maxAbsDy > viewConfiguration.touchSlop) {
                    isZoomGesture = true
                    val delta = -(y - lastY) / mapHeightPx * ZOOM_LEVELS_PER_SCREEN_HEIGHT
                    lastY = y
                    val current = cameraState.position
                    cameraState.position = CameraPosition(
                        target = current.target,
                        zoom = (current.zoom + delta).coerceIn(MIN_ZOOM, MAX_ZOOM),
                        bearing = current.bearing,
                    )
                    change.consume()
                }
            }
        }

        if (!isZoomGesture && markToolActive) {
            val projection = cameraState.projection ?: return@awaitEachGesture
            val position = projection.positionFromScreenLocation(downOffset)
            onMapClick(position.latitude, position.longitude)
        }
    }
}
