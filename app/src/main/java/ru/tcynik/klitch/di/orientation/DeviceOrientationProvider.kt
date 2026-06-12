package ru.tcynik.klitch.di.orientation

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Sensor-based orientation provider using [Sensor.TYPE_ROTATION_VECTOR].
 *
 * Emits bearing in degrees [0, 360), filtered with a low-pass filter (alpha = 0.95)
 * for smooth rotation. Auto-manages sensor lifecycle via callbackFlow + awaitClose.
 *
 * Cold-start calibration: the filter seed ([lastFiltered]) is updated on every event
 * until sensor accuracy reaches [SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM]. This
 * prevents locking in an inaccurate initial azimuth while the fusion algorithm converges.
 * Once accuracy is sufficient, [initialized] is set and subsequent events use the
 * low-pass filter over the shortest arc to avoid 0°/360° wrap-around jumps.
 *
 * Display rotation: the rotation matrix is remapped to the current screen orientation
 * so that the azimuth always reflects the direction the top of the screen faces,
 * regardless of portrait/landscape mode.
 *
 * If the rotation vector sensor is unavailable, emits constant 0f (graceful degradation).
 */
class DeviceOrientationProvider(
    private val context: Application,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Suppress("DEPRECATION")
    private val windowManager = context.getSystemService(WindowManager::class.java)

    val bearing: StateFlow<Float> = callbackFlow {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (sensor == null) {
            trySend(0f)
            awaitClose()
            return@callbackFlow
        }

        val rotationMatrix = FloatArray(9)
        val remappedMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        var lastFiltered = 0f
        var initialized = false
        var currentAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                // Remap rotation matrix to account for screen orientation so that
                // azimuth always corresponds to the direction the screen top faces.
                @Suppress("DEPRECATION")
                val displayRotation = windowManager.defaultDisplay.rotation
                val matrix = when (displayRotation) {
                    Surface.ROTATION_90 -> {
                        SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remappedMatrix)
                        remappedMatrix
                    }
                    Surface.ROTATION_180 -> {
                        SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, remappedMatrix)
                        remappedMatrix
                    }
                    Surface.ROTATION_270 -> {
                        SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, remappedMatrix)
                        remappedMatrix
                    }
                    else -> rotationMatrix // Surface.ROTATION_0 — no remap needed
                }

                SensorManager.getOrientation(matrix, orientationAngles)

                var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (azimuth < 0) azimuth += 360f

                if (!initialized) {
                    // Keep updating the seed until the fusion algorithm has converged.
                    // Locking in on SENSOR_STATUS_UNRELIABLE or ACCURACY_LOW causes a
                    // persistent session-long offset because the first events are inaccurate.
                    lastFiltered = azimuth
                    if (currentAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
                        initialized = true
                    }
                    trySend(azimuth)
                    return
                }

                // Low-pass filter over the shortest angular arc to handle 0°/360° wrap-around
                val alpha = 0.95f
                var delta = azimuth - lastFiltered
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                var filtered = lastFiltered + alpha * delta
                if (filtered < 0f) filtered += 360f
                if (filtered >= 360f) filtered -= 360f
                lastFiltered = filtered
                trySend(filtered)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                currentAccuracy = accuracy
            }
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = 0f,
    )
}
