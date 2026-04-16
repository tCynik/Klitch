package ru.tcynik.meshtactics.data.mesh.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import ru.tcynik.meshtactics.data.mesh.mapper.toMeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.meshtactics.mesh.ble.BleScanner
import ru.tcynik.meshtactics.mesh.ble.MeshtasticBleConstants
import ru.tcynik.meshtactics.mesh.model.ConnectionState
import ru.tcynik.meshtactics.mesh.model.InterfaceId
import ru.tcynik.meshtactics.mesh.repository.NodeRepository
import ru.tcynik.meshtactics.mesh.repository.RadioInterfaceService
import ru.tcynik.meshtactics.mesh.repository.ServiceRepository
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

    private val _isScanning = MutableStateFlow(false)

    override val connectionStatus: Flow<MeshConnectionStatus> =
        combine(
            serviceRepository.connectionState.onEach { Log.d("MeshRepo", "DBG connectionStatus: serviceRepo state -> $it") },
            nodeRepository.ourNodeInfo,
            radioInterfaceService.bleRssi,
            _isScanning,
        ) { state, node, bleRssi, isScanning ->
            val status = if (isScanning && state == ConnectionState.Disconnected) {
                MeshConnectionStatus.Scanning
            } else {
                state.toMeshConnectionStatus(node, pendingDeviceName, bleRssi)
            }
            Log.d("MeshRepo", "DBG connectionStatus: combined -> $status")
            status
        }

    @OptIn(ExperimentalUuidApi::class)
    override fun scanDevices(): Flow<List<MeshDeviceModel>> = flow {
        _isScanning.value = true
        try {
            val discovered = mutableListOf<MeshDeviceModel>()
            bleScanner.scan(timeout = 30.seconds, serviceUuid = MeshtasticBleConstants.SERVICE_UUID).collect { device ->
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
        } finally {
            _isScanning.value = false
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
