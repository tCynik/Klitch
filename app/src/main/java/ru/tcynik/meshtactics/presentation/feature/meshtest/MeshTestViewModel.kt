package ru.tcynik.meshtactics.presentation.feature.meshtest

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import ru.tcynik.meshtactics.domain.logger.Logger
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.domain.mesh.model.GpsMode
import ru.tcynik.meshtactics.domain.mesh.model.LocationConfigModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.model.MeshMessageDelivery
import ru.tcynik.meshtactics.domain.mesh.model.MeshNodeModel
import ru.tcynik.meshtactics.domain.channel.model.NodeSyncResult
import ru.tcynik.meshtactics.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.meshtactics.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SyncContoursOnConnectUseCase
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceParams
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.DisconnectFromMeshUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RebootNodeUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveGeoNodesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveLocationConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RemoveFixedPositionUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RequestDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.SetProvideLocationUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelPositionPrecisionUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WritePositionConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveMeshNodesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveMessagesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveOurNodeUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.SendMeshMessageParams
import ru.tcynik.meshtactics.domain.mesh.usecase.SendMeshMessageUseCase
import ru.tcynik.meshtactics.domain.mesh.util.PskValidator
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.BleDeviceUi
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.ConfigTabState
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.ChannelConfigUi
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.DeviceConfigUi
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.DeviceMetricsUi
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.GeoNodesTabState
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.MeshConnectionStatusUi
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.MeshMessageUi
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.MeshNodeUi
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.MeshTestTab
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.MessageDirection
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.MessageStatus
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.models.GeoNodeUi
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.models.GpsModeUi
import ru.tcynik.meshtactics.presentation.feature.meshtest.state.models.LocationConfigUi

class MeshTestViewModel(
    private val observeConnectionStatus: ObserveConnectionStatusUseCase,
    private val scanDevices: ScanMeshDevicesUseCase,
    private val connectToDevice: ConnectToMeshDeviceUseCase,
    private val disconnectFromMesh: DisconnectFromMeshUseCase,
    private val observeNodes: ObserveMeshNodesUseCase,
    private val observeOurNode: ObserveOurNodeUseCase,
    private val observeMessages: ObserveMessagesUseCase,
    private val sendMessage: SendMeshMessageUseCase,
    private val observeGeoNodes: ObserveGeoNodesUseCase,
    private val observeDeviceConfig: ObserveDeviceConfigUseCase,
    private val requestDeviceConfig: RequestDeviceConfigUseCase,
    private val writeOwner: WriteOwnerUseCase,
    private val writeChannel: WriteChannelUseCase,
    private val observeLocationConfig: ObserveLocationConfigUseCase,
    private val setProvideLocation: SetProvideLocationUseCase,
    private val writePositionConfig: WritePositionConfigUseCase,
    private val writeChannelPositionPrecision: WriteChannelPositionPrecisionUseCase,
    private val removeFixedPosition: RemoveFixedPositionUseCase,
    private val checkContourSync: CheckNodeSyncUseCase,
    private val syncContoursOnConnect: SyncContoursOnConnectUseCase,
    private val rebootNode: RebootNodeUseCase,
    private val syncStateRepository: ContourSyncStateRepository,
    private val rebootStateRepository: RebootStateRepository,
    private val logger: Logger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeshTestUiState())
    val uiState: StateFlow<MeshTestUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var messagesJob: Job? = null
    private var wasConnected = false
    private var rebootDisconnectObserved = false
    private var userStoppedScan = false

    private val myNodeNumFlow = MutableStateFlow<Int?>(null)

    /** Contact key currently observed for messages (broadcast ch0 by default).
     *  Format matches mesh layer: "${channel}${nodeId}", e.g. "0^all" for ch0 broadcast. */
    private var activeContactKey: String = "0^all"

    init {
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
            logger.i("App","DBG connectionStatus flow emitted: $status")
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
                state.copy(
                    connectionStatus = uiStatus,
                    lastConnectedNodeName = when (status) {
                        is MeshConnectionStatus.Connected -> status.nodeId
                        else -> state.lastConnectedNodeName
                    },
                    connectionTab = state.connectionTab.copy(
                        isScanning = status is MeshConnectionStatus.Scanning && !userStoppedScan,
                    ),
                )
            }
            if (status is MeshConnectionStatus.Connected) {
                if (!wasConnected && !isRebooting) {
                    viewModelScope.launch {
                        if (checkContourSync() is NodeSyncResult.NeedsSync) {
                            _uiState.update { it.copy(showSyncDialog = true) }
                        }
                    }
                }
            }
            wasConnected = status is MeshConnectionStatus.Connected
            // Auto-start scan when the app is already scanning (MainViewModel auto-scan)
            // but this VM hasn't started collecting devices yet.
            // Skip during reboot: extra scan competes with GATT auto-connect and breaks reconnect.
            if (status is MeshConnectionStatus.Scanning && scanJob == null && !isRebooting && !userStoppedScan) {
                onScanClick()
            }
            // Stop scan when auto-connect from MainViewModel kicks in.
            if (status is MeshConnectionStatus.Connecting || status is MeshConnectionStatus.Connected) {
                scanJob?.cancel()
                scanJob = null
            }
        }.launchIn(viewModelScope)

        observeNodes(NoParams).onEach { nodes ->
            _uiState.update { state ->
                state.copy(
                    telemetryTab = state.telemetryTab.copy(
                        meshNodes = nodes.map { it.toNodeUi() }.toImmutableList(),
                        isLoading = false,
                    )
                )
            }
        }.launchIn(viewModelScope)

        observeOurNode(NoParams).onEach { node ->
            myNodeNumFlow.value = node?.num
            _uiState.update { state ->
                state.copy(
                    telemetryTab = state.telemetryTab.copy(
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

        observeGeoNodes(NoParams).onEach { nodes ->
            _uiState.update { state ->
                state.copy(
                    geoNodesTab = GeoNodesTabState(
                        nodes = nodes.map { node ->
                            GeoNodeUi(
                                nodeId = node.nodeId,
                                shortName = node.shortName,
                                distanceFormatted = node.distanceMeters?.let { formatDistance(it) } ?: "—",
                                positionTime = node.positionTime,
                                groundSpeed = node.groundSpeed,
                                groundTrack = node.groundTrack,
                            )
                        }.toImmutableList()
                    )
                )
            }
        }.launchIn(viewModelScope)

        observeDeviceConfig(NoParams).onEach { config ->
            logger.i("App","DBG observeDeviceConfig emitted: config=${config?.let { "longName=${it.longName} region=${it.region} lora=${it.loraPreset}" } ?: "null"}")
            if (config != null) {
                _uiState.update { state ->
                    val updatedChannels = if (state.configTab.isEditing) {
                        state.configTab.channels
                    } else {
                        config.channels.map { ch ->
                            ChannelConfigUi(
                                index = ch.index,
                                channelName = ch.name,
                                pskBase64 = ch.pskBase64,
                            )
                        }
                    }
                    state.copy(
                        configTab = state.configTab.copy(
                            isLoading = false,
                            deviceConfig = DeviceConfigUi(
                                longName = config.longName,
                                shortName = config.shortName,
                                loraPreset = config.loraPreset,
                                txPowerDbm = config.txPowerDbm,
                                region = config.region,
                            ),
                            channels = updatedChannels,
                        )
                    )
                }
            }
        }.launchIn(viewModelScope)

        @OptIn(ExperimentalCoroutinesApi::class)
        myNodeNumFlow
            .filterNotNull()
            .flatMapLatest { nodeNum -> observeLocationConfig(nodeNum) }
            .onEach { config ->
                _uiState.update { state ->
                    state.copy(configTab = state.configTab.copy(locationConfig = config.toLocationUi()))
                }
            }
            .launchIn(viewModelScope)

        startObservingMessages(activeContactKey)
    }

    // ── Sync Dialog ───────────────────────────────────────────────────────────

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
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    fun onTabSelected(tab: MeshTestTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    // ── Connection Tab ────────────────────────────────────────────────────────

    fun onScanClick() {
        userStoppedScan = false
        scanJob?.cancel()
        _uiState.update { state ->
            state.copy(
                connectionStatus = MeshConnectionStatusUi.Scanning,
                connectionTab = state.connectionTab.copy(isScanning = true),
            )
        }
        // isScanning driven by observeConnectionStatus → _isScanning in repo
        scanJob = scanDevices(NoParams)
            .onEach { devices ->
                _uiState.update { state ->
                    state.copy(
                        connectionTab = state.connectionTab.copy(
                            scannedDevices = devices.map { BleDeviceUi(it.address, it.name, it.rssi) }
                                .toImmutableList()
                        )
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onStopScanClick() {
        userStoppedScan = true
        scanJob?.cancel()
        scanJob = null
        _uiState.update { state ->
            state.copy(
                connectionStatus = MeshConnectionStatusUi.Disconnected,
                connectionTab = state.connectionTab.copy(isScanning = false),
            )
        }
    }

    fun onConnectClick(address: String) {
        scanJob?.cancel()
        scanJob = null
        val deviceName = _uiState.value.connectionTab.scannedDevices
            .find { it.address == address }?.name ?: address
        logger.i("App","DBG onConnectClick: address=$address name=$deviceName")
        _uiState.update { state ->
            state.copy(
                connectionStatus = MeshConnectionStatusUi.Connecting(deviceName),
                connectionTab = state.connectionTab.copy(isScanning = false),
            )
        }
        viewModelScope.launch {
            logger.i("App","DBG onConnectClick: calling connectToDevice...")
            runCatching { connectToDevice(ConnectToMeshDeviceParams(address, deviceName)) }
                .onSuccess { logger.i("App","DBG onConnectClick: connectToDevice returned OK") }
                .onFailure { e ->
                    logger.e("App","DBG onConnectClick: connectToDevice failed: ${e.message}", e)
                    _uiState.update {
                        it.copy(connectionStatus = MeshConnectionStatusUi.Error(e.message ?: "Connection failed"))
                    }
                }
        }
    }

    fun onDisconnectClick() {
        viewModelScope.launch { disconnectFromMesh(NoParams) }
    }

    // ── Messages Tab ──────────────────────────────────────────────────────────

    fun onInputChange(text: String) {
        _uiState.update { state ->
            state.copy(messagesTab = state.messagesTab.copy(inputText = text))
        }
    }

    fun onSendClick() {
        val text = _uiState.value.messagesTab.inputText.trim()
        if (text.isEmpty()) return
        _uiState.update { state ->
            state.copy(messagesTab = state.messagesTab.copy(inputText = "", isSending = true))
        }
        viewModelScope.launch {
            runCatching {
                sendMessage(SendMeshMessageParams(text = text, contactKey = activeContactKey))
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(messagesTab = state.messagesTab.copy(isSending = false))
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(messagesTab = state.messagesTab.copy(isSending = false))
                }
            }
        }
    }

    fun onChannelSelected(channel: Int) {
        activeContactKey = "${channel}^all"
        _uiState.update { state ->
            state.copy(messagesTab = state.messagesTab.copy(selectedChannel = channel))
        }
        startObservingMessages(activeContactKey)
    }

    // ── Config Tab ────────────────────────────────────────────────────────────

    fun onReadConfigClick() {
        _uiState.update { state ->
            state.copy(configTab = state.configTab.copy(isLoading = true, isEditing = false))
        }
        requestDeviceConfig()
    }

    fun onEditConfigClick() {
        _uiState.update { state ->
            state.copy(configTab = state.configTab.copy(isEditing = true))
        }
    }

    fun onConfigLongNameChange(value: String) {
        _uiState.update { state ->
            val cfg = state.configTab.deviceConfig ?: return@update state
            state.copy(configTab = state.configTab.copy(deviceConfig = cfg.copy(longName = value)))
        }
    }

    fun onConfigShortNameChange(value: String) {
        _uiState.update { state ->
            val cfg = state.configTab.deviceConfig ?: return@update state
            state.copy(configTab = state.configTab.copy(deviceConfig = cfg.copy(shortName = value)))
        }
    }

    fun onWriteConfigClick() {
        val configTab = _uiState.value.configTab
        val cfg = configTab.deviceConfig ?: return
        writeOwner(cfg.longName, cfg.shortName)
        configTab.channels.forEach { ch ->
            if (ch.pskError == null) {
                writeChannel(ch.index, ch.channelName, ch.pskBase64)
            }
        }
        _uiState.update { state ->
            state.copy(configTab = state.configTab.copy(isEditing = false))
        }
    }

    fun onChannelNameChange(index: Int, value: String) {
        _uiState.update { state ->
            state.copy(
                configTab = state.configTab.copy(
                    channels = state.configTab.channels.map { ch ->
                        if (ch.index == index) ch.copy(channelName = value) else ch
                    }
                )
            )
        }
    }

    fun onChannelPskChange(index: Int, value: String) {
        val error = when (val result = PskValidator.validate(value)) {
            is PskValidator.Result.Invalid -> result.reason
            is PskValidator.Result.Valid -> null
        }
        _uiState.update { state ->
            state.copy(
                configTab = state.configTab.copy(
                    channels = state.configTab.channels.map { ch ->
                        if (ch.index == index) ch.copy(pskBase64 = value, pskError = error) else ch
                    }
                )
            )
        }
    }

    fun onAddChannelClick() {
        _uiState.update { state ->
            val channels = state.configTab.channels
            if (channels.size >= 8) return@update state
            val nextIndex = channels.size
            val newChannel = ChannelConfigUi(index = nextIndex, channelName = "", pskBase64 = "")
            state.copy(configTab = state.configTab.copy(channels = channels + newChannel))
        }
    }

    // ── Location Config Handlers ──────────────────────────────────────────────

    fun onProvideLocationToggle(enabled: Boolean) {
        val nodeNum = myNodeNumFlow.value ?: return
        setProvideLocation(nodeNum, enabled)
        if (enabled) removeFixedPosition(nodeNum)
    }

    fun onGpsModeChange(mode: GpsModeUi) {
        val nodeNum = myNodeNumFlow.value ?: return
        val config = _uiState.value.configTab.locationConfig ?: return
        writePositionConfig(
            nodeNum,
            mode.toDomain(),
            config.broadcastIntervalSecs,
            config.smartBroadcastEnabled,
            config.smartBroadcastMinDistanceM,
            config.positionFlags,
        )
    }

    fun onRemoveFixedPosition() {
        val nodeNum = myNodeNumFlow.value ?: return
        removeFixedPosition(nodeNum)
    }

    fun onBroadcastIntervalChange(secs: Int) {
        val nodeNum = myNodeNumFlow.value ?: return
        val config = _uiState.value.configTab.locationConfig ?: return
        writePositionConfig(
            nodeNum,
            config.gpsMode.toDomain(),
            secs,
            config.smartBroadcastEnabled,
            config.smartBroadcastMinDistanceM,
            config.positionFlags,
        )
    }

    fun onSmartBroadcastToggle(enabled: Boolean) {
        val nodeNum = myNodeNumFlow.value ?: return
        val config = _uiState.value.configTab.locationConfig ?: return
        writePositionConfig(
            nodeNum,
            config.gpsMode.toDomain(),
            config.broadcastIntervalSecs,
            enabled,
            config.smartBroadcastMinDistanceM,
            config.positionFlags,
        )
    }

    fun onPositionFlagsChange(flags: Int) {
        val nodeNum = myNodeNumFlow.value ?: return
        val config = _uiState.value.configTab.locationConfig ?: return
        writePositionConfig(
            nodeNum,
            config.gpsMode.toDomain(),
            config.broadcastIntervalSecs,
            config.smartBroadcastEnabled,
            config.smartBroadcastMinDistanceM,
            flags,
        )
    }

    fun onChannelPositionPrecisionChange(precision: Int) {
        val nodeNum = myNodeNumFlow.value ?: return
        writeChannelPositionPrecision(nodeNum, 0, precision)
    }

    // ── Telemetry Tab ─────────────────────────────────────────────────────────

    fun onRefreshTelemetryClick() {
        _uiState.update { state ->
            state.copy(telemetryTab = state.telemetryTab.copy(isLoading = true))
        }
        // Nodes are observed reactively — isLoading resets when the next emission arrives
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun startObservingMessages(contactKey: String) {
        messagesJob?.cancel()
        logger.i("App","DBG startObservingMessages: contactKey=$contactKey")
        messagesJob = observeMessages(contactKey)
            .onEach { messages ->
                logger.i("App","DBG messages flow emitted: count=${messages.size}")
                _uiState.update { state ->
                    state.copy(
                        messagesTab = state.messagesTab.copy(
                            messages = messages.map { msg ->
                                MeshMessageUi(
                                    id = msg.uuid.toString(),
                                    text = msg.text,
                                    fromNodeId = msg.fromNodeId,
                                    fromNodeName = msg.fromNodeName,
                                    toNodeId = contactKey,
                                    formattedTime = msg.formattedTime,
                                    direction = if (msg.isOutgoing) MessageDirection.Outgoing
                                    else MessageDirection.Incoming,
                                    status = msg.deliveryStatus.toUiStatus(),
                                )
                            }.toImmutableList()
                        )
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun MeshConnectionStatus.toUi(): MeshConnectionStatusUi = when (this) {
        MeshConnectionStatus.Disconnected -> MeshConnectionStatusUi.Disconnected
        MeshConnectionStatus.Scanning -> MeshConnectionStatusUi.Scanning
        is MeshConnectionStatus.Connecting -> MeshConnectionStatusUi.Connecting(deviceName)
        is MeshConnectionStatus.Connected -> MeshConnectionStatusUi.Connected(nodeId, rssi, batteryLevel)
        MeshConnectionStatus.DeviceSleep -> MeshConnectionStatusUi.Connecting("Sleeping…")
        is MeshConnectionStatus.Error -> MeshConnectionStatusUi.Error(message)
    }

    private fun MeshNodeModel.toNodeUi(): MeshNodeUi = MeshNodeUi(
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

    private fun MeshMessageDelivery.toUiStatus(): MessageStatus = when (this) {
        MeshMessageDelivery.Pending -> MessageStatus.Pending
        MeshMessageDelivery.Sent -> MessageStatus.Sent
        MeshMessageDelivery.Delivered -> MessageStatus.Acked
        MeshMessageDelivery.Failed -> MessageStatus.Failed
    }

    private fun formatDistance(meters: Int): String =
        if (meters >= 1000) "${"%.1f".format(meters / 1000.0)} km" else "$meters m"

    private fun formatUptime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%dh %02dm".format(h, m)
        else if (m > 0) "%dm %02ds".format(m, s)
        else "${s}s"
    }

    private fun LocationConfigModel.toLocationUi() = LocationConfigUi(
        provideLocationToMesh = provideLocationToMesh,
        hasLocationPermission = hasLocationPermission,
        gpsMode = gpsMode.toUi(),
        fixedPositionEnabled = fixedPositionEnabled,
        broadcastIntervalSecs = broadcastIntervalSecs,
        smartBroadcastEnabled = smartBroadcastEnabled,
        smartBroadcastMinDistanceM = smartBroadcastMinDistanceM,
        positionFlags = positionFlags,
        primaryChannelPositionPrecision = primaryChannelPositionPrecision,
    )

    private fun GpsMode.toUi(): GpsModeUi = when (this) {
        GpsMode.DISABLED -> GpsModeUi.DISABLED
        GpsMode.ENABLED -> GpsModeUi.ENABLED
        GpsMode.NOT_PRESENT -> GpsModeUi.NOT_PRESENT
    }

    private fun GpsModeUi.toDomain(): GpsMode = when (this) {
        GpsModeUi.DISABLED -> GpsMode.DISABLED
        GpsModeUi.ENABLED -> GpsMode.ENABLED
        GpsModeUi.NOT_PRESENT -> GpsMode.NOT_PRESENT
    }
}
