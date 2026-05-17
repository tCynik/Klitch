/*
 * Copyright (c) 2026 Meshtastic LLC
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
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package ru.tcynik.meshtactics.mesh.ble

import com.juul.kable.Scanner
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single
import kotlin.time.Duration
import kotlin.uuid.Uuid

@Single
class KableBleScanner : BleScanner {
    override fun scan(timeout: Duration, serviceUuid: Uuid?, address: String?): Flow<BleDevice> {
        // Service UUID is intentionally kept OUT of the native ScanFilter.
        // Android's hardware BLE filter is applied before the device's scan response is received.
        // If the service UUID is in the scan response (or Android hasn't cached the response yet),
        // the hardware filter silently drops the device on the first scan pass.
        // On restart the device IS found because Android has the scan response cached from the
        // previous pass and the hardware filter succeeds.
        //
        // Fix: scan with address-only hardware filter (always reliable), and filter by service UUID
        // in software against advertisement.uuids — which Android assembles from BOTH the primary
        // advertisement and the scan response, making it immune to this timing issue.
        val scanner = Scanner {
            if (address != null) {
                filters {
                    match {
                        this.address = address
                    }
                }
            }
        }

        // Kable's Scanner doesn't enforce timeout internally; it runs until the Flow is cancelled.
        // channelFlow + withTimeoutOrNull enforces the BleScanner timeout contract cleanly.
        return kotlinx.coroutines.flow.channelFlow {
            kotlinx.coroutines.withTimeoutOrNull(timeout) {
                scanner.advertisements.collect { advertisement ->
                    val matchesService = serviceUuid == null || advertisement.uuids.any { it == serviceUuid }
                    if (matchesService) {
                        send(KableBleDevice(advertisement))
                    }
                }
            }
        }
    }
}
