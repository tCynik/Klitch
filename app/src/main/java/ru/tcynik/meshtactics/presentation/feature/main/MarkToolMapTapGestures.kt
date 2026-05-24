package ru.tcynik.meshtactics.presentation.feature.main

import android.view.ViewConfiguration
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.DpOffset
import org.maplibre.compose.camera.CameraState

/**
 * Mark-tool taps (non–course-up): single tap after double-tap timeout, double tap on 2nd release.
 *
 * Attached only to the map surface (not HUD/sheet). [requireUnconsumed] is false so MapLibre
 * does not block taps after the map view is recreated; HUD/sheet are siblings and unaffected.
 */
fun Modifier.markToolMapTapGestures(
    cameraState: CameraState,
    onMapClick: (lat: Double, lon: Double, screenX: Float, screenY: Float) -> Unit,
    onMapDoubleClick: (lat: Double, lon: Double) -> Unit,
    onMapLongClick: (lat: Double, lon: Double, screenX: Float, screenY: Float) -> Unit,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val tapDispatcher = remember(onMapClick, onMapDoubleClick) {
        MarkToolTapDispatcher(
            scope = scope,
            doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong(),
            onSingleTap = onMapClick,
            onDoubleTap = { lat, lon, _, _ -> onMapDoubleClick(lat, lon) },
        )
    }
    Modifier.pointerInput(tapDispatcher) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            val downOffset = DpOffset(
                x = with(density) { down.position.x.toDp() },
                y = with(density) { down.position.y.toDp() },
            )
            var totalMovement = Offset.Zero
            var longPressFired = false
            val downUptimeMs = android.os.SystemClock.uptimeMillis()
            val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                if (!change.pressed) {
                    if (event.changes.none { it.pressed }) {
                        if (!longPressFired && totalMovement.getDistance() <= viewConfiguration.touchSlop) {
                            change.consume()
                            val projection = cameraState.projection ?: return@awaitEachGesture
                            val position = projection.positionFromScreenLocation(downOffset)
                            tapDispatcher.onTapRelease(
                                position.latitude,
                                position.longitude,
                                downOffset.x.value,
                                downOffset.y.value,
                            )
                        }
                        break
                    }
                    continue
                }
                totalMovement += change.positionChange()
                if (totalMovement.getDistance() > viewConfiguration.touchSlop) {
                    tapDispatcher.reset()
                }

                if (!longPressFired &&
                    totalMovement.getDistance() <= viewConfiguration.touchSlop &&
                    android.os.SystemClock.uptimeMillis() - downUptimeMs >= longPressTimeoutMs
                ) {
                    longPressFired = true
                    tapDispatcher.reset()
                    change.consume()
                    val projection = cameraState.projection ?: return@awaitEachGesture
                    val position = projection.positionFromScreenLocation(downOffset)
                    onMapLongClick(
                        position.latitude,
                        position.longitude,
                        downOffset.x.value,
                        downOffset.y.value,
                    )
                }
            }
        }
    }
}
