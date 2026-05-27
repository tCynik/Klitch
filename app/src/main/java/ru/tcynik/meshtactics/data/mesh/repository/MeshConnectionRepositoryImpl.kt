package ru.tcynik.meshtactics.data.mesh.repository

import kotlinx.coroutines.delay
import ru.tcynik.meshtactics.domain.logger.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicInteger
import ru.tcynik.meshtactics.data.mesh.mapper.toMeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.meshtactics.mesh.ble.BleDevice
import ru.tcynik.meshtactics.mesh.ble.BleScanner
import ru.tcynik.meshtactics.mesh.ble.BluetoothRepository
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
    private val bluetoothRepository: BluetoothRepository,
    private val radioInterfaceService: RadioInterfaceService,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val logger: Logger,
) : MeshConnectionRepository {

    /**
     * Current device name being connected to — updated externally by [connect].
     * Stored here to pass into Connecting state (ConnectionState.Connecting has no device name).
     */
    @Volatile private var pendingDeviceName: String = ""

    // Counts active scanDevices() collectors. Multiple callers (e.g. MainViewModel + NetworkViewModel)
    // can scan concurrently; _isScanning stays true until ALL of them finish.
    private val activeScanCount = AtomicInteger(0)
    private val _isScanning = MutableStateFlow(false)

    override val connectionStatus: Flow<MeshConnectionStatus> =
        combine(
            serviceRepository.connectionState.onEach { logger.d("BLE", "connectionStatus: serviceRepo state -> $it") },
            nodeRepository.ourNodeInfo,
            radioInterfaceService.bleRssi,
            _isScanning,
        ) { state, node, bleRssi, isScanning ->
            val status = if (isScanning && state == ConnectionState.Disconnected) {
                MeshConnectionStatus.Scanning
            } else {
                state.toMeshConnectionStatus(node, pendingDeviceName, bleRssi)
            }
            logger.d("BLE", "connectionStatus: combined -> $status")
            status
        }

    @OptIn(ExperimentalUuidApi::class)
    override fun scanDevices(): Flow<List<MeshDeviceModel>> = flow {
        if (!bluetoothRepository.state.value.enabled) {
            logger.w("BLE", "scanDevices: Bluetooth is disabled, aborting scan")
            return@flow
        }
        activeScanCount.incrementAndGet()
        _isScanning.value = true
        try {
            val discovered = mutableMapOf<String, Pair<MeshDeviceModel, Long>>()
            val expiryMs = 10_000L
            merge(
                bleScanner.scan(timeout = 30.seconds, serviceUuid = MeshtasticBleConstants.SERVICE_UUID)
                    .catch { e -> logger.w("BLE", "BLE scan terminated: ${e.message}") }
                    .map { it as BleDevice? },
                flow { while (true) { delay(5_000); emit(null) } },
            ).collect { device ->
                val now = System.currentTimeMillis()
                if (device != null) {
                    discovered[device.address] = MeshDeviceModel(
                        address = device.address,
                        name = device.name ?: device.address,
                        rssi = device.rssi,
                    ) to now
                }
                discovered.entries.removeIf { (_, pair) -> now - pair.second > expiryMs }
                emit(discovered.values.map { it.first }.sortedBy { it.address })
            }
        } finally {
            if (activeScanCount.decrementAndGet() == 0) {
                _isScanning.value = false
            }
        }
    }.distinctUntilChanged()

    override suspend fun connect(address: String, deviceName: String) {
        logger.i("BLE", "connect: address='$address' name='$deviceName'")
        pendingDeviceName = deviceName
        val bleAddress = radioInterfaceService.toInterfaceAddress(InterfaceId.BLUETOOTH, address)
        logger.i("BLE", "connect: setDeviceAddress bleAddress='$bleAddress'")
        radioInterfaceService.setDeviceAddress(bleAddress)
        logger.i("BLE", "connect: calling radioInterfaceService.connect()")
        radioInterfaceService.connect()
        logger.i("BLE", "connect: connect() returned")
    }

    override suspend fun disconnect() {
        pendingDeviceName = ""
        radioInterfaceService.setBleRssi(0)
        radioInterfaceService.setDeviceAddress(null)
    }
}
