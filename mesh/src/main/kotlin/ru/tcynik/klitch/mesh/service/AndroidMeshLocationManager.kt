/*
 * Copyright (c) 2025-2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package ru.tcynik.klitch.mesh.service

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location as AndroidLocation
import androidx.core.location.LocationCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single
import ru.tcynik.klitch.mesh.common.hasLocationPermission
import ru.tcynik.klitch.mesh.model.Position
import ru.tcynik.klitch.mesh.repository.Location
import ru.tcynik.klitch.mesh.repository.LocationRepository
import ru.tcynik.klitch.mesh.repository.MeshLocationManager
import kotlin.time.Duration.Companion.milliseconds
import org.meshtastic.proto.Position as ProtoPosition

@Single
class AndroidMeshLocationManager(private val context: Application, private val locationRepository: LocationRepository) :
    MeshLocationManager {
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var locationFlow: Job? = null
    @Volatile private var sendPositionFn: ((ProtoPosition) -> Unit)? = null
    @Volatile private var lastPosition: ProtoPosition? = null
    @Volatile private var lastSentAtMs = 0L
    @Volatile private var lastSentLat = Double.NaN
    @Volatile private var lastSentLon = Double.NaN

    @SuppressLint("MissingPermission")
    override fun start(scope: CoroutineScope, sendPositionFn: (ProtoPosition) -> Unit) {
        this.scope = scope
        this.sendPositionFn = sendPositionFn
        if (locationFlow?.isActive == true) return

        if (context.hasLocationPermission()) {
            locationFlow =
                locationRepository
                    .getLocations()
                    .onEach { location ->
                        val pos = buildProtoPosition(location)
                        lastPosition = pos
                        smartSend(location, pos, sendPositionFn)
                    }
                    .launchIn(scope)
        }
    }

    override fun stop() {
        if (locationFlow?.isActive == true) {
            Logger.i { "Stopping location requests" }
            locationFlow?.cancel()
            locationFlow = null
        }
        sendPositionFn = null
        lastSentAtMs = 0L
        lastSentLat = Double.NaN
        lastSentLon = Double.NaN
    }

    override fun flushLastPosition() {
        val pos = lastPosition ?: run {
            Logger.i("MT/PhoneGPS→radio") { "flushLastPosition: lastPosition=null, пропуск" }
            return
        }
        Logger.i("MT/PhoneGPS→radio") {
            "flushLastPosition time=${pos.time} lat=${Position.degD(pos.latitude_i ?: 0)} lon=${Position.degD(pos.longitude_i ?: 0)}"
        }
        sendPositionFn?.invoke(pos)
        lastSentAtMs = System.currentTimeMillis()
        lastSentLat = Position.degD(pos.latitude_i ?: 0)
        lastSentLon = Position.degD(pos.longitude_i ?: 0)
    }

    private fun buildProtoPosition(location: Location): ProtoPosition {
        val nowMs = System.currentTimeMillis()
        val fixAgeMs = nowMs - location.time
        val fixTimeSeconds = if (fixAgeMs <= MAX_FIX_AGE_MS) {
            (location.time.milliseconds.inWholeSeconds).toInt()
        } else {
            Logger.i("MT/PhoneGPS→radio") { "GPS fix stale by ${fixAgeMs / 1000}s, using nowMs as position.time" }
            (nowMs / 1_000L).toInt()
        }
        return ProtoPosition(
            latitude_i = Position.degI(location.latitude),
            longitude_i = Position.degI(location.longitude),
            altitude =
            if (LocationCompat.hasMslAltitude(location)) {
                LocationCompat.getMslAltitudeMeters(location).toInt()
            } else {
                null
            },
            altitude_hae = location.altitude.toInt(),
            time = fixTimeSeconds,
            ground_speed = location.speed.toInt(),
            ground_track = location.bearing.toInt(),
            location_source = ProtoPosition.LocSource.LOC_EXTERNAL,
        )
    }

    private fun smartSend(
        location: Location,
        pos: ProtoPosition,
        sendFn: (ProtoPosition) -> Unit,
    ) {
        val nowMs = System.currentTimeMillis()
        val elapsedMs = nowMs - lastSentAtMs

        if (lastSentAtMs > 0L && elapsedMs < MOBILE_INTERVAL_MS) {
            Logger.d("MT/SmartPos") { "skip gate: elapsed=${elapsedMs / 1000}s < ${MOBILE_INTERVAL_MS / 1000}s" }
            return
        }

        val distanceM = distanceBetween(lastSentLat, lastSentLon, location.latitude, location.longitude)
        val accuracyM = location.accuracy.coerceAtLeast(1f)
        val hasMoved = distanceM > accuracyM
        val stationaryExpired = lastSentAtMs == 0L || elapsedMs >= STATIONARY_INTERVAL_MS

        if (hasMoved || stationaryExpired) {
            if (lastSentAtMs == 0L) {
                Logger.i("MT/SmartPos") { "send first: acc=${"%.1f".format(accuracyM)}m" }
            } else {
                val reason = if (hasMoved) "distance" else "heartbeat"
                Logger.d("MT/SmartPos") {
                    "send $reason: dist=${"%.1f".format(distanceM)}m acc=${"%.1f".format(accuracyM)}m elapsed=${elapsedMs / 1000}s"
                }
            }
            sendFn(pos)
            lastSentAtMs = nowMs
            lastSentLat = location.latitude
            lastSentLon = location.longitude
        } else {
            Logger.d("MT/SmartPos") {
                "skip noise: dist=${"%.1f".format(distanceM)}m <= acc=${"%.1f".format(accuracyM)}m elapsed=${elapsedMs / 1000}s"
            }
        }
    }

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        if (lat1.isNaN() || lon1.isNaN()) return Float.MAX_VALUE
        val results = FloatArray(1)
        AndroidLocation.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    /**
     * константы с таймингами отправки геопозиции в сеть.
     * Служат для целей:
     * - обеспечение актуальности меток геопозиций на карте
     * - предотвращение спама (когда двиггаемся слишком частые метки будут вредить, а когда стоишь
     * они должны быть по возможности более редкими)
     * - своевременное определение, что нода больше не передает позицию (например, отключилась)
     */
    companion object {
        // время таймаута между отправками позиции если кординаты не меняются.
        // Служит для обновления статуса, чтобы убедиться, что нода не уснула
        private const val MAX_FIX_AGE_MS: Long = ((2*60) //todo: возможно, стоит поставить 3 минуты
                *1000)

        // время, не ранее которого происходит отправка очередных координат во время перемещения
        private const val MOBILE_INTERVAL_MS = 30_000L

        // таймаут, спустя который нода признается протухшей (серый цвет)
        // двукратное максимальное время отправки + запас на получение и обработку
        private const val STATIONARY_INTERVAL_MS: Long = MAX_FIX_AGE_MS * 2 + 10000
    }
}
