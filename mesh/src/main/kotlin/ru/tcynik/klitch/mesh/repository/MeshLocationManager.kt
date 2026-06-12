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
package ru.tcynik.klitch.mesh.repository

import kotlinx.coroutines.CoroutineScope
import org.meshtastic.proto.Position

/** Interface for managing the local node's location updates and reporting. */
interface MeshLocationManager {
    /** Starts location updates and reports them via the given function. */
    fun start(scope: CoroutineScope, sendPositionFn: (Position) -> Unit)

    /** Stops location updates. */
    fun stop()

    /** Re-sends the last reported position, if any (e.g. after BLE reconnect from DeviceSleep). */
    fun flushLastPosition() {}
}
