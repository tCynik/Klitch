package ru.tcynik.klitch.presentation.feature.network

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.klitch.domain.channel.model.NodeSyncResult
import ru.tcynik.klitch.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.klitch.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.klitch.domain.channel.usecase.ConfirmChannelSyncUseCase
import ru.tcynik.klitch.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.domain.mesh.model.ContourNodeModel
import ru.tcynik.klitch.domain.mesh.model.GpsMode
import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.klitch.domain.mesh.model.MeshNodeModel
import ru.tcynik.klitch.domain.mesh.model.NodeSyncCyclePhase
import ru.tcynik.klitch.mesh.ble.toMeshtasticDisplayShortName
import ru.tcynik.klitch.domain.mesh.repository.RebootStateRepository
import ru.tcynik.klitch.domain.mesh.usecase.ConnectToMeshDeviceParams
import ru.tcynik.klitch.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.klitch.domain.mesh.usecase.DisconnectFromMeshUseCase
import ru.tcynik.klitch.domain.mesh.usecase.GetGpsModeUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveContourNodesUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveLocationConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveOurNodeUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RequestTelemetryUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.klitch.domain.mesh.usecase.SetDesiredGpsModeUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveNetworkEnabledUseCase
import ru.tcynik.klitch.domain.settings.usecase.SetNetworkEnabledUseCase
import ru.tcynik.klitch.domain.user.model.AppUser
import ru.tcynik.klitch.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.klitch.domain.user.usecase.SaveAppUserUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams
import ru.tcynik.klitch.presentation.feature.network.state.BleDeviceUi
import ru.tcynik.klitch.presentation.feature.network.state.DeviceMetricsUi
import ru.tcynik.klitch.presentation.feature.network.state.MeshConnectionStatusUi
import ru.tcynik.klitch.presentation.feature.network.state.models.CallsignGateDialogState
import ru.tcynik.klitch.presentation.feature.network.state.models.GpsModeUi
import ru.tcynik.klitch.presentation.feature.network.state.models.PendingAction
import ru.tcynik.klitch.presentation.feature.network.state.models.toUi

private const val TELEMETRY_REFRESH_TIMEOUT_MS = 10_000L

class NetworkViewModel(
    private val observeConnectionStatus: ObserveConnectionStatusUseCase,
    private val scanDevices: ScanMeshDevicesUseCase,
    private val connectToDevice: ConnectToMeshDeviceUseCase,
    private val disconnectFromMesh: DisconnectFromMeshUseCase,
    private val observeNodes: ObserveContourNodesUseCase,
    private val observeOurNode: ObserveOurNodeUseCase,
    private val checkContourSync: CheckNodeSyncUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
    private val confirmChannelSync: ConfirmChannelSyncUseCase,
    private val syncStateRepository: ContourSyncStateRepository,
    private val rebootStateRepository: RebootStateRepository,
    private val observeAppUser: ObserveAppUserUseCase,
    private val saveAppUser: SaveAppUserUseCase,
    private val observeNetworkEnabled: ObserveNetworkEnabledUseCase,
    private val setNetworkEnabled: SetNetworkEnabledUseCase,
    private val observeDeviceConfig: ObserveDeviceConfigUseCase,
    private val observeLocationConfig: ObserveLocationConfigUseCase,
    private val setDesiredGpsMode: SetDesiredGpsModeUseCase,
    private val getGpsMode: GetGpsModeUseCase,
    private val requestTelemetry: RequestTelemetryUseCase,
    private val logger: Logger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkUiState())
    val uiState: StateFlow<NetworkUiState> = _uiState.asStateFlow()

    private val _callsignRequired = MutableStateFlow(true)
    private val myNodeNumFlow = MutableStateFlow<Int?>(null)

    private var scanJob: Job? = null
    private var wasConnected = false
    private var userStoppedScan = false
    private var pendingConnectAddress: String? = null
    private var telemetryTimeoutJob: Job? = null
    private var lastMeshStatus: MeshConnectionStatus = MeshConnectionStatus.Disconnected

    init {
        observeDeviceConfig(NoParams)
            .onEach { config ->
                _uiState.update { it.copy(hasNodeConfig = config != null) }
            }
            .launchIn(viewModelScope)

        observeNetworkEnabled(NoParams)
            .onEach { enabled ->
                val wasEnabled = _uiState.value.networkEnabled
                _uiState.update { it.copy(networkEnabled = enabled) }
                if (wasEnabled && !enabled) {
                    stopScanInternal()
                    viewModelScope.launch { disconnectFromMesh(NoParams) }
                }
            }
            .launchIn(viewModelScope)

        rebootStateRepository.syncCyclePhase
            .onEach { phase ->
                _uiState.update { state ->
                    val uiStatus = resolveConnectionStatusUi(
                        meshStatus = lastMeshStatus,
                        syncPhase = phase,
                        currentUiStatus = state.connectionStatus,
                    ) ?: lastMeshStatus.toUi()
                    state.copy(
                        isRebooting = phase != NodeSyncCyclePhase.Idle,
                        connectionStatus = uiStatus,
                    )
                }
            }
            .launchIn(viewModelScope)

        observeConnectionStatus(NoParams).onEach { status ->
            if (!_uiState.value.networkEnabled) return@onEach
            lastMeshStatus = status
            val syncPhase = rebootStateRepository.syncCyclePhase.value
            val uiStatus = resolveConnectionStatusUi(
                meshStatus = status,
                syncPhase = syncPhase,
                currentUiStatus = _uiState.value.connectionStatus,
            ) ?: status.toUi()
            if (syncPhase != NodeSyncCyclePhase.Idle) {
                logger.i("Node", "syncUi: mesh=$status phase=$syncPhase ui=$uiStatus")
            }
            _uiState.update { state ->
                val isScanInProgress = (status is MeshConnectionStatus.Scanning && !userStoppedScan || scanJob != null)
                    && state.connection.connectingAddress == null
                state.copy(
                    connectionStatus = uiStatus,
                    lastConnectedNodeName = when (status) {
                        is MeshConnectionStatus.Connecting -> status.deviceName
                        is MeshConnectionStatus.Connected -> status.deviceName
                            .ifBlank { state.lastConnectedNodeName }
                        else -> state.lastConnectedNodeName
                    },
                    connection = state.connection.copy(
                        isScanning = isScanInProgress,
                    ),
                )
            }
            if (status is MeshConnectionStatus.Connected) {
                pendingConnectAddress?.let { connectedAddress ->
                    _uiState.update { state ->
                        state.copy(
                            connection = state.connection.copy(
                                scannedDevices = state.connection.scannedDevices
                                    .filterNot { it.address == connectedAddress }
                                    .toImmutableList(),
                                connectingAddress = null,
                            )
                        )
                    }
                    pendingConnectAddress = null
                }
                if (!wasConnected) {
                    viewModelScope.launch {
                        if (rebootStateRepository.shouldSkipSyncCheckAfterReboot()) {
                            return@launch
                        }
                        if (rebootStateRepository.isRebooting.value) return@launch
                        withTimeoutOrNull(10_000) {
                            observeNodeChannels(NoParams).filter { it.isNotEmpty() }.firstOrNull()
                        }
                        if (checkContourSync() is NodeSyncResult.NeedsSync) {
                            _uiState.update { it.copy(showSyncDialog = true) }
                        }
                    }
                }
            }
            wasConnected = status is MeshConnectionStatus.Connected
            val isSyncCycleActive = syncPhase != NodeSyncCyclePhase.Idle
            if (status is MeshConnectionStatus.Scanning && scanJob == null && !isSyncCycleActive && !userStoppedScan) {
                startScan()
            }
            if (status is MeshConnectionStatus.Connecting) {
                scanJob?.cancel()
                scanJob = null
            }
        }.launchIn(viewModelScope)

        observeNodes(NoParams).onEach { nodes ->
            _uiState.update { state ->
                state.copy(
                    telemetry = state.telemetry.copy(
                        meshNodes = nodes.map { it.toNodeUi() }.toImmutableList(),
                        isLoading = false,
                    )
                )
            }
        }.launchIn(viewModelScope)

        observeOurNode(NoParams).onEach { node ->
            myNodeNumFlow.value = node?.num
            telemetryTimeoutJob?.cancel()
            telemetryTimeoutJob = null
            val newMetrics = node?.let {
                DeviceMetricsUi(
                    batteryLevel = it.batteryLevel.takeIf { v -> v > 0 },
                    voltage = if (it.voltage > 0f) "%.2f V".format(it.voltage) else null,
                    channelUtilization = if (it.channelUtilization > 0f)
                        "%.1f%%".format(it.channelUtilization) else null,
                    airUtilTx = if (it.airUtilTx > 0f)
                        "%.1f%%".format(it.airUtilTx) else null,
                    uptimeFormatted = it.uptimeSeconds.takeIf { s -> s > 0 }
                        ?.let { s -> formatUptime(s) },
                )
            }
            _uiState.update { state ->
                state.copy(
                    telemetry = state.telemetry.copy(
                        isLoading = false,
                        deviceMetrics = newMetrics,
                        lastUpdatedAtMillis = if (newMetrics != null) {
                            System.currentTimeMillis()
                        } else {
                            state.telemetry.lastUpdatedAtMillis
                        },
                    )
                )
            }
        }.launchIn(viewModelScope)

        @OptIn(ExperimentalCoroutinesApi::class)
        myNodeNumFlow
            .filterNotNull()
            .flatMapLatest { nodeNum -> observeLocationConfig(nodeNum) }
            .onEach { config ->
                val mode = config.gpsMode.toUi()
                _uiState.update { state ->
                    // hide toggle when chip absent (NOT_PRESENT) — binary phone/node choice only
                    state.copy(gpsSourceMode = mode.takeIf { it != GpsModeUi.NOT_PRESENT })
                }
            }
            .launchIn(viewModelScope)

        var initGateShown = false
        observeAppUser(NoParams)
            .onEach { user ->
                val required = user.displayName.isBlank()
                _callsignRequired.value = required
                if (!initGateShown) {
                    initGateShown = true
                    if (required && _uiState.value.networkEnabled) {
                        _uiState.update {
                            it.copy(callsignGateDialog = CallsignGateDialogState(PendingAction.None, ""))
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onNetworkEnabledToggle(enabled: Boolean) {
        setNetworkEnabled(enabled)
    }

    fun onCallsignInput(text: String) {
        _uiState.update { state ->
            val dialog = state.callsignGateDialog ?: return@update state
            state.copy(callsignGateDialog = dialog.copy(callsignInput = text))
        }
    }

    fun onCallsignConfirmed() {
        val dialog = _uiState.value.callsignGateDialog ?: return
        val callsign = dialog.callsignInput.trim()
        if (callsign.isBlank()) return
        viewModelScope.launch {
            saveAppUser(AppUser(displayName = callsign))
            _uiState.update { it.copy(callsignGateDialog = null) }
            when (val action = dialog.pendingAction) {
                PendingAction.None -> Unit
                PendingAction.Scan -> startScan()
                is PendingAction.Connect -> connectToPendingDevice(action.address, action.deviceName)
            }
        }
    }

    fun onCallsignDismissed() {
        _uiState.update { it.copy(callsignGateDialog = null) }
    }

    fun onConfirmChannelSync() {
        _uiState.update { it.copy(showSyncDialog = false) }
        viewModelScope.launch {
            logger.i("Node", "syncConfirm: start name=${_uiState.value.lastConnectedNodeName}")
            confirmChannelSync(NoParams)
        }
    }

    fun onDismissChannelSync() {
        _uiState.update { it.copy(showSyncDialog = false) }
        syncStateRepository.setSyncRequired(true)
        viewModelScope.launch { disconnectFromMesh(NoParams) }
    }

    fun onScanClick() {
        if (!_uiState.value.networkEnabled) return
        userStoppedScan = false
        if (_callsignRequired.value) {
            _uiState.update {
                it.copy(callsignGateDialog = CallsignGateDialogState(PendingAction.Scan, ""))
            }
            return
        }
        startScan()
    }

    private fun startScan() {
        if (!_uiState.value.networkEnabled) return
        scanJob?.cancel()
        val shouldSwitchToScanningState = _uiState.value.connectionStatus !is MeshConnectionStatusUi.Connected
        _uiState.update { state ->
            state.copy(
                connectionStatus = if (shouldSwitchToScanningState) MeshConnectionStatusUi.Scanning else state.connectionStatus,
                connection = state.connection.copy(isScanning = true),
            )
        }
        scanJob = scanDevices(NoParams)
            .onEach { devices ->
                _uiState.update { state ->
                    state.copy(
                        connection = state.connection.copy(
                            scannedDevices = devices.map { BleDeviceUi(it.address, it.name, it.rssi) }
                                .toImmutableList()
                        )
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onStopScanClick() {
        stopScanInternal()
    }

    private fun stopScanInternal() {
        userStoppedScan = true
        scanJob?.cancel()
        scanJob = null
        _uiState.update { state ->
            state.copy(
                connectionStatus = if (state.connectionStatus is MeshConnectionStatusUi.Connected) {
                    state.connectionStatus
                } else {
                    MeshConnectionStatusUi.Disconnected
                },
                connection = state.connection.copy(isScanning = false),
            )
        }
    }

    fun onConnectClick(address: String) {
        if (!_uiState.value.networkEnabled) return
        scanJob?.cancel()
        scanJob = null
        val deviceName = _uiState.value.connection.scannedDevices
            .find { it.address == address }?.name ?: address
        if (_callsignRequired.value) {
            _uiState.update {
                it.copy(callsignGateDialog = CallsignGateDialogState(
                    PendingAction.Connect(address, deviceName), ""
                ))
            }
            return
        }
        viewModelScope.launch {
            val currentConnecting = _uiState.value.connection.connectingAddress
            if (currentConnecting != null && currentConnecting != address) {
                disconnectFromMesh(NoParams)
            }
            connectToPendingDevice(address, deviceName)
        }
    }

    private suspend fun connectToPendingDevice(address: String, deviceName: String) {
        if (!_uiState.value.networkEnabled) return
        pendingConnectAddress = address
        logger.i("App", "DBG onConnectClick: address=$address name=$deviceName")
        _uiState.update { state ->
            state.copy(
                connectionStatus = MeshConnectionStatusUi.Connecting(deviceName.toMeshtasticDisplayShortName()),
                connection = state.connection.copy(isScanning = false, connectingAddress = address),
            )
        }
        runCatching { connectToDevice(ConnectToMeshDeviceParams(address, deviceName)) }
            .onFailure { e ->
                pendingConnectAddress = null
                logger.e("App", "DBG onConnectClick: connectToDevice failed: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        connectionStatus = MeshConnectionStatusUi.Error(e.message ?: "Connection failed"),
                        connection = it.connection.copy(connectingAddress = null),
                    )
                }
            }
    }

    fun onDisconnectClick() {
        when (_uiState.value.connectionStatus) {
            is MeshConnectionStatusUi.Connected ->
                _uiState.update { it.copy(showDisconnectDialog = true) }
            is MeshConnectionStatusUi.Connecting ->
                viewModelScope.launch { disconnectFromMesh(NoParams) }
            else -> Unit
        }
    }

    fun onDisconnectConfirmed() {
        _uiState.update { it.copy(showDisconnectDialog = false) }
        viewModelScope.launch { disconnectFromMesh(NoParams) }
    }

    fun onDisconnectDismissed() {
        _uiState.update { it.copy(showDisconnectDialog = false) }
    }

    fun onGpsSourceToggle(useNodeGps: Boolean) {
        val nodeNum = myNodeNumFlow.value ?: return
        val mode = if (useNodeGps) GpsMode.ENABLED else GpsMode.DISABLED
        setDesiredGpsMode(nodeNum, mode)
        viewModelScope.launch {
            val actual = getGpsMode()
            if (actual != null && actual != mode) {
                syncStateRepository.setSyncRequired(true)
            }
        }
    }

    fun onRefreshTelemetryClick() {
        _uiState.update { state ->
            state.copy(telemetry = state.telemetry.copy(isLoading = true))
        }
        requestTelemetry()
        telemetryTimeoutJob?.cancel()
        telemetryTimeoutJob = viewModelScope.launch {
            delay(TELEMETRY_REFRESH_TIMEOUT_MS)
            _uiState.update { state ->
                state.copy(telemetry = state.telemetry.copy(isLoading = false))
            }
        }
    }

    private fun resolveConnectionStatusUi(
        meshStatus: MeshConnectionStatus?,
        syncPhase: NodeSyncCyclePhase,
        currentUiStatus: MeshConnectionStatusUi,
    ): MeshConnectionStatusUi? {
        if (meshStatus is MeshConnectionStatus.Connecting && syncPhase != NodeSyncCyclePhase.Idle) {
            return meshStatus.toUi()
        }
        return when (syncPhase) {
            NodeSyncCyclePhase.Syncing -> MeshConnectionStatusUi.Syncing
            NodeSyncCyclePhase.Rebooting -> MeshConnectionStatusUi.Rebooting
            NodeSyncCyclePhase.WaitingForNode -> MeshConnectionStatusUi.WaitingForNode
            NodeSyncCyclePhase.Idle -> when {
                meshStatus == null -> null
                userStoppedScan && meshStatus is MeshConnectionStatus.Scanning -> MeshConnectionStatusUi.Disconnected
                else -> null
            }
        }
    }

    private fun MeshConnectionStatus.toUi(): MeshConnectionStatusUi = when (this) {
        MeshConnectionStatus.Disconnected -> MeshConnectionStatusUi.Disconnected
        MeshConnectionStatus.Scanning -> MeshConnectionStatusUi.Scanning
        is MeshConnectionStatus.Connecting -> MeshConnectionStatusUi.Connecting(deviceName)
        is MeshConnectionStatus.Connected -> MeshConnectionStatusUi.Connected(
            nodeId = nodeId,
            deviceName = deviceName,
            rssi = rssi,
            batteryLevel = batteryLevel,
        )
        MeshConnectionStatus.DeviceSleep -> MeshConnectionStatusUi.Connecting("Sleeping…")
        is MeshConnectionStatus.Error -> MeshConnectionStatusUi.Error(message)
    }

    private fun ContourNodeModel.toNodeUi() = ru.tcynik.klitch.presentation.feature.network.state.MeshNodeUi(
        nodeId = node.nodeId,
        shortName = node.shortName,
        longName = node.longName,
        snr = if (node.snr == 0f) "—" else "%.1f dB".format(node.snr),
        lastHeardFormatted = if (node.lastHeard > 0)
            DateUtils.getRelativeTimeSpanString(
                node.lastHeard * 1000L,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString()
        else "never",
        lastPositionFormatted = if (node.positionTime > 0)
            DateUtils.getRelativeTimeSpanString(
                node.positionTime * 1000L,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString()
        else null,
        hopsAway = if (node.hopsAway > 0) node.hopsAway else null,
        contourName = contourName,
    )

    private fun formatUptime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%dh %02dm".format(h, m)
        else if (m > 0) "%dm %02ds".format(m, s)
        else "${s}s"
    }
}
