package ru.tcynik.mymesh1.data.mesh.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import ru.tcynik.mymesh1.data.mesh.mapper.toMeshConnectionStatus
import ru.tcynik.mymesh1.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.mymesh1.domain.mesh.model.MeshDeviceModel
import ru.tcynik.mymesh1.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.mymesh1.mesh.ble.BleScanner
import ru.tcynik.mymesh1.mesh.repository.NodeRepository
import ru.tcynik.mymesh1.mesh.repository.RadioInterfaceService
import ru.tcynik.mymesh1.mesh.repository.ServiceRepository
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

class MeshConnectionRepositoryImpl(
    private val bleScanner: BleScanner,
    private val radioInterfaceService: RadioInterfaceService,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
) : MeshConnectionRepository {

    /**
     * Current device name being connected to — updated externally by [connect].
     * Stored here to pass into Connecting state (ConnectionState.Connecting has no device name).
     */
    @Volatile private var pendingDeviceName: String = ""

    override val connectionStatus: Flow<MeshConnectionStatus> =
        combine(
            serviceRepository.connectionState,
            nodeRepository.ourNodeInfo,
        ) { state, node ->
            state.toMeshConnectionStatus(node, pendingDeviceName)
        }

    @OptIn(ExperimentalUuidApi::class)
    override fun scanDevices(): Flow<List<MeshDeviceModel>> = flow {
        val discovered = mutableListOf<MeshDeviceModel>()
        bleScanner.scan(timeout = 30.seconds).collect { device ->
            if (discovered.none { it.address == device.address }) {
                discovered.add(
                    MeshDeviceModel(
                        address = device.address,
                        name = device.name ?: device.address,
                        rssi = 0,
                    )
                )
                emit(discovered.toList())
            }
        }
    }

    override suspend fun connect(address: String) {
        pendingDeviceName = address
        // Format: "bt:AA:BB:CC:DD:EE:FF" for BLE devices
        val bleAddress = if (address.startsWith("bt:")) address else "bt:$address"
        radioInterfaceService.setDeviceAddress(bleAddress)
        radioInterfaceService.connect()
    }

    override suspend fun disconnect() {
        pendingDeviceName = ""
        radioInterfaceService.setDeviceAddress(null)
    }
}
