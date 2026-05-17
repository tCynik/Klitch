package ru.tcynik.meshtactics.data.local.mesh

import com.russhwolf.settings.Settings
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceModel
import ru.tcynik.meshtactics.domain.mesh.repository.LastConnectedDeviceRepository

private const val KEY_ADDRESS = "last_ble_address"
private const val KEY_NAME    = "last_ble_name"

class LastConnectedDeviceRepositoryImpl(
    private val settings: Settings,
) : LastConnectedDeviceRepository {

    override fun get(): MeshDeviceModel? {
        val address = settings.getStringOrNull(KEY_ADDRESS) ?: return null
        val name    = settings.getStringOrNull(KEY_NAME) ?: address
        return MeshDeviceModel(address = address, name = name, rssi = 0)
    }

    override suspend fun save(device: MeshDeviceModel) {
        settings.putString(KEY_ADDRESS, device.address)
        settings.putString(KEY_NAME, device.name)
    }
}
