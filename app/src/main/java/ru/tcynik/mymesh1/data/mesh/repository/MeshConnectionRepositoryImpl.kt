package ru.tcynik.mymesh1.data.mesh.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import ru.tcynik.mymesh1.data.mesh.mapper.toMeshConnectionStatus
import ru.tcynik.mymesh1.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.mymesh1.domain.mesh.model.MeshDeviceModel
import ru.tcynik.mymesh1.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.mymesh1.mesh.ble.BleScanner
import ru.tcynik.mymesh1.mesh.model.InterfaceId
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
            serviceRepository.connectionState.onEach { Log.d("MeshRepo", "DBG connectionStatus: serviceRepo state -> $it") },
            nodeRepository.ourNodeInfo,
            radioInterfaceService.bleRssi,
        ) { state, node, bleRssi ->
            val status = state.toMeshConnectionStatus(node, pendingDeviceName, bleRssi)
            Log.d("MeshRepo", "DBG connectionStatus: combined -> $status")
            status
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

    override suspend fun connect(address: String, deviceName: String) {
        Log.i("MeshRepo", "DBG connect: address=$address name=$deviceName")
        pendingDeviceName = deviceName
        val bleAddress = radioInterfaceService.toInterfaceAddress(InterfaceId.BLUETOOTH, address)
        Log.i("MeshRepo", "DBG connect: calling setDeviceAddress($bleAddress)")
        radioInterfaceService.setDeviceAddress(bleAddress)
        Log.i("MeshRepo", "DBG connect: calling radioInterfaceService.connect()")
        radioInterfaceService.connect()
        Log.i("MeshRepo", "DBG connect: connect() returned")
    }

    override suspend fun disconnect() {
        pendingDeviceName = ""
        radioInterfaceService.setDeviceAddress(null)
    }
}
