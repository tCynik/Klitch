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
package ru.tcynik.meshtactics.mesh.network.radio

import android.content.Context
import org.koin.core.annotation.Single
import ru.tcynik.meshtactics.mesh.ble.BleConnectionFactory
import ru.tcynik.meshtactics.mesh.ble.BleScanner
import ru.tcynik.meshtactics.mesh.ble.BluetoothRepository
import ru.tcynik.meshtactics.mesh.di.CoroutineDispatchers
import ru.tcynik.meshtactics.mesh.model.DeviceType
import ru.tcynik.meshtactics.mesh.repository.RadioInterfaceService
import ru.tcynik.meshtactics.mesh.repository.RadioTransport
import ru.tcynik.meshtactics.mesh.repository.RadioTransportFactory

/**
 * Android implementation of [RadioTransportFactory]. Handles pure-KMP transports (BLE) via [BaseRadioTransportFactory]
 * while delegating legacy platform-specific connections (like USB/Serial, TCP, and Mocks) to the Android-specific
 * [InterfaceFactory].
 */
@Single(binds = [RadioTransportFactory::class])
@Suppress("LongParameterList")
class AndroidRadioTransportFactory(
    private val context: Context,
    private val interfaceFactory: Lazy<InterfaceFactory>,
    scanner: BleScanner,
    bluetoothRepository: BluetoothRepository,
    connectionFactory: BleConnectionFactory,
    dispatchers: CoroutineDispatchers,
) : BaseRadioTransportFactory(scanner, bluetoothRepository, connectionFactory, dispatchers) {

    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.BLE, DeviceType.TCP, DeviceType.USB)

    override fun isPlatformAddressValid(address: String): Boolean = interfaceFactory.value.addressValid(address)

    override fun createPlatformTransport(address: String, service: RadioInterfaceService): RadioTransport =
        interfaceFactory.value.createInterface(address, service)
}
