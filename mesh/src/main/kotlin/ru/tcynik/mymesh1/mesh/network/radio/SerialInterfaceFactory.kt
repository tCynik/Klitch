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
package ru.tcynik.mymesh1.mesh.network.radio

import org.koin.core.annotation.Single
import ru.tcynik.mymesh1.mesh.network.repository.UsbRepository
import ru.tcynik.mymesh1.mesh.repository.RadioInterfaceService

/** Factory for creating `SerialInterface` instances. */
@Single
class SerialInterfaceFactory(private val usbRepository: UsbRepository) {
    fun create(rest: String, service: RadioInterfaceService): SerialInterface =
        SerialInterface(service, usbRepository, rest)
}
