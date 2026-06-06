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
package ru.tcynik.meshtactics.mesh.service

import android.annotation.SuppressLint
import android.app.Application
import androidx.core.location.LocationCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single
import ru.tcynik.meshtactics.mesh.common.hasLocationPermission
import ru.tcynik.meshtactics.mesh.model.Position
import ru.tcynik.meshtactics.mesh.repository.LocationRepository
import ru.tcynik.meshtactics.mesh.repository.MeshLocationManager
import kotlin.time.Duration.Companion.milliseconds
import org.meshtastic.proto.Position as ProtoPosition

@Single
class AndroidMeshLocationManager(private val context: Application, private val locationRepository: LocationRepository) :
    MeshLocationManager {
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var locationFlow: Job? = null
    private var sendPositionFn: ((ProtoPosition) -> Unit)? = null
    private var lastPosition: ProtoPosition? = null

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
                        val nowMs = System.currentTimeMillis()
                        val fixAgeMs = nowMs - location.time
                        val fixTimeSeconds = if (fixAgeMs <= MAX_FIX_AGE_MS) {
                            (location.time.milliseconds.inWholeSeconds).toInt()
                        } else {
                            Logger.i("MT/PhoneGPS→radio") { "GPS fix stale by ${fixAgeMs / 1000}s, using nowMs as position.time" }
                            (nowMs / 1_000L).toInt()
                        }
                        val pos = ProtoPosition(
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
                        lastPosition = pos
                        Logger.i("MT/PhoneGPS→radio") {
                            "sendPosition time=${pos.time} lat=${Position.degD(pos.latitude_i ?: 0)} " +
                                "lon=${Position.degD(pos.longitude_i ?: 0)}"
                        }
                        sendPositionFn(pos)
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
    }

    companion object {
        // If the GPS fix is older than this, use current time so the mesh packet is not
        // immediately stale on the receiver (stationary-device cache problem).
        private const val MAX_FIX_AGE_MS = 90_000L
    }
}
