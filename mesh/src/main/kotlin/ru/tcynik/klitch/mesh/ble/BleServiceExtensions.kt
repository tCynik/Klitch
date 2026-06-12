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
package ru.tcynik.klitch.mesh.ble

private val MESHTASTIC_BLE_NAME_REGEX =
    Regex("^Meshtastic_([0-9a-fA-F]{4})$", RegexOption.IGNORE_CASE)
private val MESHTASTIC_DEFAULT_LONG_NAME_REGEX =
    Regex("^Meshtastic ([0-9a-fA-F]{4})$", RegexOption.IGNORE_CASE)

fun String.toMeshtasticDisplayShortName(): String {
    val trimmed = trim()
    for (regex in listOf(MESHTASTIC_BLE_NAME_REGEX, MESHTASTIC_DEFAULT_LONG_NAME_REGEX)) {
        regex.find(trimmed)?.groupValues?.get(1)?.lowercase()?.let { return it }
    }
    return trimmed
}

/** Extension to convert a [BleService] to a [MeshtasticRadioProfile]. */
fun BleService.toMeshtasticRadioProfile(): MeshtasticRadioProfile {
    val kableService = this as KableBleService
    return KableMeshtasticRadioProfile(kableService.peripheral)
}
