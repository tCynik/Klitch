package ru.tcynik.klitch.presentation.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.klitch.domain.channel.model.NodeSyncResult
import ru.tcynik.klitch.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.klitch.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.klitch.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.klitch.domain.mesh.model.MeshDeviceModel
import ru.tcynik.klitch.domain.mesh.model.NodeSyncCyclePhase
import ru.tcynik.klitch.domain.mesh.repository.RebootStateRepository
import ru.tcynik.klitch.domain.mesh.usecase.ConnectToMeshDeviceParams
import ru.tcynik.klitch.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.klitch.domain.mesh.usecase.GetLastConnectedDeviceUseCase
import ru.tcynik.klitch.domain.mesh.usecase.NodeProvisioningUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveCallsignChangesUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RefreshNodePublicKeyUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.klitch.domain.service.GpsServiceController
import ru.tcynik.klitch.domain.settings.usecase.ObserveNetworkEnabledUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams
import ru.tcynik.klitch.domain.user.usecase.ObserveAppUserUseCase

data class ConnectionUiState(
    val connectionStatus: MeshConnectionStatus = MeshConnectionStatus.Disconnected,
    val showConnectionLabel: Boolean = false,
    val foundDevices: ImmutableList<MeshDeviceModel> = persistentListOf(),
    val syncRequired: Boolean = false,
    val callsignRequired: Boolean = false,
    val isRebooting: Boolean = false,
    val syncCyclePhase: NodeSyncCyclePhase = NodeSyncCyclePhase.Idle,
    val networkEnabled: Boolean = true,
)

class ConnectionViewModel(
    observeConnectionStatus: ObserveConnectionStatusUseCase,
    observeNetworkEnabled: ObserveNetworkEnabledUseCase,
    private val scanDevices: ScanMeshDevicesUseCase,
    private val connectToDevice: ConnectToMeshDeviceUseCase,
    private val getLastConnectedDevice: GetLastConnectedDeviceUseCase,
    private val nodeProvisioning: NodeProvisioningUseCase,
    private val checkNodeSync: CheckNodeSyncUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
    private val syncStateRepository: ContourSyncStateRepository,
    private val rebootStateRepository: RebootStateRepository,
    private val observeCallsignChanges: ObserveCallsignChangesUseCase,
    private val refreshNodePublicKey: RefreshNodePublicKeyUseCase,
    private val observeAppUser: ObserveAppUserUseCase,
    private val gpsServiceController: GpsServiceController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private var connectedLabelJob: Job? = null
    private var scanJob: Job? = null

    init {
        rebootStateRepository.isRebooting
            .onEach { rebooting -> _uiState.update { it.copy(isRebooting = rebooting) } }
            .launchIn(viewModelScope)

        rebootStateRepository.syncCyclePhase
            .onEach { phase -> _uiState.update { it.copy(syncCyclePhase = phase) } }
            .launchIn(viewModelScope)

        observeConnectionStatus(NoParams)
            .onEach { status ->
                if (status is MeshConnectionStatus.Connected) {
                    scanJob?.cancel()
                    scanJob = null
                    _uiState.update { it.copy(foundDevices = persistentListOf()) }
                    val wasConnected = _uiState.value.connectionStatus is MeshConnectionStatus.Connected
                    _uiState.update { it.copy(connectionStatus = status) }
                    if (!wasConnected) {
                        gpsServiceController.onNodeConnected()
                        val skipSyncCheck = rebootStateRepository.shouldSkipSyncCheckAfterReboot()
                        if (!skipSyncCheck) {
                            viewModelScope.launch { nodeProvisioning.provision() }
                            viewModelScope.launch {
                                withTimeoutOrNull(10_000) {
                                    observeNodeChannels(NoParams).filter { it.isNotEmpty() }.firstOrNull()
                                }
                                if (checkNodeSync() is NodeSyncResult.NeedsSync)
                                    syncStateRepository.setSyncRequired(true)
                                else
                                    syncStateRepository.clear()
                            }
                        }
                        _uiState.update { it.copy(showConnectionLabel = true) }
                        connectedLabelJob?.cancel()
                        connectedLabelJob = viewModelScope.launch {
                            delay(2_000)
                            _uiState.update { it.copy(showConnectionLabel = false) }
                        }
                    }
                } else {
                    val wasConnected = _uiState.value.connectionStatus is MeshConnectionStatus.Connected
                    connectedLabelJob?.cancel()
                    connectedLabelJob = null
                    _uiState.update { it.copy(connectionStatus = status, showConnectionLabel = false) }
                    if (wasConnected && _uiState.value.isRebooting && !rebootStateRepository.shouldSkipSyncCheckAfterReboot()) {
                        viewModelScope.launch {
                            delay(3_000)
                            startAutoConnectIfEnabled()
                        }
                    }
                }
            }
            .launchIn(viewModelScope)

        observeNetworkEnabled(NoParams)
            .onEach { enabled ->
                val wasEnabled = _uiState.value.networkEnabled
                _uiState.update { it.copy(networkEnabled = enabled) }
                if (enabled && !wasEnabled) {
                    startAutoConnectIfEnabled()
                } else if (!enabled && wasEnabled) {
                    gpsServiceController.onNetworkDisabled()
                    scanJob?.cancel()
                    scanJob = null
                    _uiState.update { it.copy(foundDevices = persistentListOf()) }
                }
            }
            .launchIn(viewModelScope)

        syncStateRepository.syncRequired
            .onEach { required -> _uiState.update { it.copy(syncRequired = required) } }
            .launchIn(viewModelScope)

        observeAppUser(NoParams)
            .onEach { user -> _uiState.update { it.copy(callsignRequired = user.displayName.isBlank()) } }
            .launchIn(viewModelScope)

        observeCallsignChanges(NoParams)
            .onEach { nodeNum ->
                viewModelScope.launch {
                    refreshNodePublicKey(nodeNum)
                    delay(10_000)
                    refreshNodePublicKey(nodeNum)
                }
            }
            .launchIn(viewModelScope)

        startAutoConnectIfEnabled()
    }

    private fun startAutoConnectIfEnabled() {
        if (!_uiState.value.networkEnabled) return
        startAutoConnect()
    }

    private fun startAutoConnect() {
        val lastDevice = getLastConnectedDevice()
        scanJob?.cancel()

        val currentStatus = _uiState.value.connectionStatus
        if (lastDevice != null &&
            currentStatus !is MeshConnectionStatus.Connecting &&
            currentStatus !is MeshConnectionStatus.Connected
        ) {
            viewModelScope.launch {
                if (!observeAppUser(NoParams).first().displayName.isBlank()) {
                    connectToDevice(ConnectToMeshDeviceParams(lastDevice.address, lastDevice.name))
                }
            }
        }

        scanJob = scanDevices(NoParams)
            .onEach { devices ->
                val status = _uiState.value.connectionStatus
                if (status is MeshConnectionStatus.Connected) return@onEach
                _uiState.update { current ->
                    val merged = (current.foundDevices + devices)
                        .distinctBy { it.address }
                        .toImmutableList()
                    current.copy(foundDevices = merged)
                }
            }
            .onCompletion { cause ->
                val status = _uiState.value.connectionStatus
                if (cause == null &&
                    status !is MeshConnectionStatus.Connected &&
                    status !is MeshConnectionStatus.Connecting
                ) {
                    startAutoConnectIfEnabled()
                }
            }
            .catch { }
            .launchIn(viewModelScope)
    }
}
