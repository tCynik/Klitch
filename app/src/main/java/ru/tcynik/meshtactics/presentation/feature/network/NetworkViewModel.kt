package ru.tcynik.meshtactics.presentation.feature.network

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.meshtactics.domain.channel.model.NodeSyncResult
import ru.tcynik.meshtactics.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.meshtactics.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SyncContoursOnConnectUseCase
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.model.MeshNodeModel
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceParams
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.DisconnectFromMeshUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveMeshNodesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveOurNodeUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RebootNodeUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveNetworkEnabledUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.SetNetworkEnabledUseCase
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.user.usecase.SaveAppUserUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.presentation.feature.network.state.BleDeviceUi
import ru.tcynik.meshtactics.presentation.feature.network.state.DeviceMetricsUi
import ru.tcynik.meshtactics.presentation.feature.network.state.MeshConnectionStatusUi
import ru.tcynik.meshtactics.presentation.feature.network.state.models.CallsignGateDialogState
import ru.tcynik.meshtactics.presentation.feature.network.state.models.PendingAction

class NetworkViewModel(
    private val observeConnectionStatus: ObserveConnectionStatusUseCase,
    private val scanDevices: ScanMeshDevicesUseCase,
    private val connectToDevice: ConnectToMeshDeviceUseCase,
    private val disconnectFromMesh: DisconnectFromMeshUseCase,
    private val observeNodes: ObserveMeshNodesUseCase,
    private val observeOurNode: ObserveOurNodeUseCase,
    private val checkContourSync: CheckNodeSyncUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
    private val syncContoursOnConnect: SyncContoursOnConnectUseCase,
    private val rebootNode: RebootNodeUseCase,
    private val syncStateRepository: ContourSyncStateRepository,
    private val rebootStateRepository: RebootStateRepository,
    private val observeAppUser: ObserveAppUserUseCase,
    private val saveAppUser: SaveAppUserUseCase,
    private val observeNetworkEnabled: ObserveNetworkEnabledUseCase,
    private val setNetworkEnabled: SetNetworkEnabledUseCase,
    private val logger: Logger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkUiState())
    val uiState: StateFlow<NetworkUiState> = _uiState.asStateFlow()

    private val _callsignRequired = MutableStateFlow(true)

    private var scanJob: Job? = null
    private var wasConnected = false
    private var rebootDisconnectObserved = false
    private var userStoppedScan = false
    private var pendingConnectAddress: String? = null

    init {
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

        rebootStateRepository.isRebooting
            .onEach { rebooting ->
                _uiState.update { state ->
                    state.copy(
                        isRebooting = rebooting,
                        connectionStatus = if (rebooting) {
                            MeshConnectionStatusUi.Rebooting
                        } else {
                            state.connectionStatus
                        },
                    )
                }
            }
            .launchIn(viewModelScope)

        observeConnectionStatus(NoParams).onEach { status ->
            if (!_uiState.value.networkEnabled) return@onEach

            logger.i("App", "DBG connectionStatus flow emitted: $status")
            val isRebooting = rebootStateRepository.isRebooting.value
            if (isRebooting && status !is MeshConnectionStatus.Connected) {
                rebootDisconnectObserved = true
            }
            if (isRebooting && rebootDisconnectObserved && status is MeshConnectionStatus.Connected) {
                rebootStateRepository.setRebooting(false)
                rebootDisconnectObserved = false
            }
            val uiStatus = when {
                isRebooting -> MeshConnectionStatusUi.Rebooting
                userStoppedScan && status is MeshConnectionStatus.Scanning -> MeshConnectionStatusUi.Disconnected
                else -> status.toUi()
            }
            _uiState.update { state ->
                val isScanInProgress = status is MeshConnectionStatus.Scanning && !userStoppedScan || scanJob != null
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
                            )
                        )
                    }
                    pendingConnectAddress = null
                }
                if (!wasConnected && !isRebooting) {
                    viewModelScope.launch {
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
            if (status is MeshConnectionStatus.Scanning && scanJob == null && !isRebooting && !userStoppedScan) {
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
            _uiState.update { state ->
                state.copy(
                    telemetry = state.telemetry.copy(
                        deviceMetrics = node?.let {
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
                    )
                )
            }
        }.launchIn(viewModelScope)

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
            rebootDisconnectObserved = false
            rebootStateRepository.setRebooting(true)
            syncContoursOnConnect()
            rebootNode()
            syncStateRepository.clear()
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
        viewModelScope.launch { connectToPendingDevice(address, deviceName) }
    }

    private suspend fun connectToPendingDevice(address: String, deviceName: String) {
        if (!_uiState.value.networkEnabled) return
        pendingConnectAddress = address
        logger.i("App", "DBG onConnectClick: address=$address name=$deviceName")
        _uiState.update { state ->
            state.copy(
                connectionStatus = MeshConnectionStatusUi.Connecting(deviceName),
                connection = state.connection.copy(isScanning = false),
            )
        }
        runCatching { connectToDevice(ConnectToMeshDeviceParams(address, deviceName)) }
            .onFailure { e ->
                pendingConnectAddress = null
                logger.e("App", "DBG onConnectClick: connectToDevice failed: ${e.message}", e)
                _uiState.update {
                    it.copy(connectionStatus = MeshConnectionStatusUi.Error(e.message ?: "Connection failed"))
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

    fun onRefreshTelemetryClick() {
        _uiState.update { state ->
            state.copy(telemetry = state.telemetry.copy(isLoading = true))
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

    private fun MeshNodeModel.toNodeUi() = ru.tcynik.meshtactics.presentation.feature.network.state.MeshNodeUi(
        nodeId = nodeId,
        shortName = shortName,
        longName = longName,
        snr = if (snr == 0f) "—" else "%.1f dB".format(snr),
        lastHeardFormatted = if (lastHeard > 0)
            DateUtils.getRelativeTimeSpanString(
                lastHeard * 1000L,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString()
        else "never",
        hopsAway = if (hopsAway > 0) hopsAway else null,
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
