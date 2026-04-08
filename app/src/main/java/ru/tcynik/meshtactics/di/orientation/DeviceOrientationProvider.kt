package ru.tcynik.meshtactics.di.orientation

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
 * Cold-start: the first event seeds [lastFiltered] directly so the initial bearing is
 * accurate. Subsequent events use angular interpolation over the shortest arc to avoid
 * wrap-around jumps at the 0°/360° boundary.
 *
 * If the rotation vector sensor is unavailable, emits constant 0f (graceful degradation).
 */
class DeviceOrientationProvider(
    private val context: Application,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val bearing: StateFlow<Float> = callbackFlow {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (sensor == null) {
            trySend(0f)
            awaitClose()
            return@callbackFlow
        }

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        var lastFiltered = 0f
        var initialized = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (azimuth < 0) azimuth += 360f

                if (!initialized) {
                    // Seed the filter with the real value — no cold-start drift from 0°
                    lastFiltered = azimuth
                    initialized = true
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
                // No-op — we don't filter by accuracy for this use case
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
