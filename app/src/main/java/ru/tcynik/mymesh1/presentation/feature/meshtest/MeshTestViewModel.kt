package ru.tcynik.mymesh1.presentation.feature.meshtest

import android.text.format.DateUtils
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tcynik.mymesh1.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.mymesh1.domain.mesh.model.MeshMessageDelivery
import ru.tcynik.mymesh1.domain.mesh.model.MeshNodeModel
import ru.tcynik.mymesh1.domain.mesh.model.MeshPacketLogModel
import ru.tcynik.mymesh1.domain.mesh.usecase.ConnectToMeshDeviceParams
import ru.tcynik.mymesh1.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.DisconnectFromMeshUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.RequestDeviceConfigUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.mymesh1.domain.mesh.util.PskValidator
import ru.tcynik.mymesh1.domain.mesh.usecase.ObserveMeshNodesUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.ObserveMessagesUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.ObserveOurNodeUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.ObservePacketLogUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.mymesh1.domain.mesh.usecase.SendMeshMessageParams
import ru.tcynik.mymesh1.domain.mesh.usecase.SendMeshMessageUseCase
import ru.tcynik.mymesh1.domain.usecase.base.NoParams
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.BleDeviceUi
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.ConfigTabState
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.ChannelConfigUi
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.DeviceConfigUi
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.DeviceMetricsUi
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.LogDirection
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.LogEntryUi
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.LogFilter
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.MeshConnectionStatusUi
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.MeshMessageUi
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.MeshNodeUi
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.MeshTestTab
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.MessageDirection
import ru.tcynik.mymesh1.presentation.feature.meshtest.state.MessageStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeshTestViewModel(
    private val observeConnectionStatus: ObserveConnectionStatusUseCase,
    private val scanDevices: ScanMeshDevicesUseCase,
    private val connectToDevice: ConnectToMeshDeviceUseCase,
    private val disconnectFromMesh: DisconnectFromMeshUseCase,
    private val observeNodes: ObserveMeshNodesUseCase,
    private val observeOurNode: ObserveOurNodeUseCase,
    private val observeMessages: ObserveMessagesUseCase,
    private val sendMessage: SendMeshMessageUseCase,
    private val observePacketLog: ObservePacketLogUseCase,
    private val observeDeviceConfig: ObserveDeviceConfigUseCase,
    private val requestDeviceConfig: RequestDeviceConfigUseCase,
    private val writeOwner: WriteOwnerUseCase,
    private val writeChannel: WriteChannelUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeshTestUiState())
    val uiState: StateFlow<MeshTestUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var messagesJob: Job? = null
    private var frozenLogEntries = emptyList<LogEntryUi>()

    /** Contact key currently observed for messages (broadcast by default). */
    private var activeContactKey: String = "^all"

    private val logTimeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    init {
        observeConnectionStatus(NoParams).onEach { status ->
            Log.i("MeshTestVM", "DBG connectionStatus flow emitted: $status")
            _uiState.update { it.copy(connectionStatus = status.toUi()) }
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

        observePacketLog(NoParams).onEach { packets ->
            val entries = packets.map { it.toLogEntryUi() }
            if (!_uiState.value.logTab.isPaused) {
                _uiState.update { state ->
                    state.copy(
                        logTab = state.logTab.copy(
                            entries = applyLogFilter(entries, state.logTab.activeFilter).toImmutableList()
                        )
                    )
                }
            } else {
                frozenLogEntries = entries
            }
        }.launchIn(viewModelScope)

        observeDeviceConfig(NoParams).onEach { config ->
            Log.i("MeshTestVM", "DBG observeDeviceConfig emitted: config=${config?.let { "longName=${it.longName} region=${it.region} lora=${it.loraPreset}" } ?: "null"}")
            if (config != null) {
                _uiState.update { state ->
                    // Don't overwrite unsaved channel edits while user is editing
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

        startObservingMessages(activeContactKey)
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    fun onTabSelected(tab: MeshTestTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    // ── Connection Tab ────────────────────────────────────────────────────────

    fun onScanClick() {
        scanJob?.cancel()
        _uiState.update { state ->
            state.copy(
                connectionStatus = MeshConnectionStatusUi.Scanning,
                connectionTab = state.connectionTab.copy(isScanning = true),
            )
        }
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
            .onCompletion {
                _uiState.update { state ->
                    state.copy(connectionTab = state.connectionTab.copy(isScanning = false))
                }
            }
            .launchIn(viewModelScope)
    }

    fun onStopScanClick() {
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
        val deviceName = _uiState.value.connectionTab.scannedDevices
            .find { it.address == address }?.name ?: address
        Log.i("MeshTestVM", "DBG onConnectClick: address=$address name=$deviceName")
        _uiState.update { it.copy(connectionStatus = MeshConnectionStatusUi.Connecting(deviceName)) }
        viewModelScope.launch {
            Log.i("MeshTestVM", "DBG onConnectClick: calling connectToDevice...")
            runCatching { connectToDevice(ConnectToMeshDeviceParams(address, deviceName)) }
                .onSuccess { Log.i("MeshTestVM", "DBG onConnectClick: connectToDevice returned OK") }
                .onFailure { e ->
                    Log.e("MeshTestVM", "DBG onConnectClick: connectToDevice failed: ${e.message}", e)
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

    // ── Telemetry Tab ─────────────────────────────────────────────────────────

    fun onRefreshTelemetryClick() {
        _uiState.update { state ->
            state.copy(telemetryTab = state.telemetryTab.copy(isLoading = true))
        }
        // Nodes are observed reactively — isLoading resets when the next emission arrives
    }

    // ── Log Tab ───────────────────────────────────────────────────────────────

    fun onLogFilterChange(filter: LogFilter) {
        _uiState.update { state ->
            val currentEntries = if (state.logTab.isPaused) frozenLogEntries
            else state.logTab.entries.toList()
            state.copy(
                logTab = state.logTab.copy(
                    activeFilter = filter,
                    entries = applyLogFilter(currentEntries, filter).toImmutableList(),
                )
            )
        }
    }

    fun onLogPauseToggle() {
        val wasPaused = _uiState.value.logTab.isPaused
        if (wasPaused) {
            // Resume: apply current frozen entries
            val currentFilter = _uiState.value.logTab.activeFilter
            _uiState.update { state ->
                state.copy(
                    logTab = state.logTab.copy(
                        isPaused = false,
                        entries = applyLogFilter(frozenLogEntries, currentFilter).toImmutableList(),
                    )
                )
            }
        } else {
            frozenLogEntries = _uiState.value.logTab.entries.toList()
            _uiState.update { state ->
                state.copy(logTab = state.logTab.copy(isPaused = true))
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun startObservingMessages(contactKey: String) {
        messagesJob?.cancel()
        messagesJob = observeMessages(contactKey)
            .onEach { messages ->
                _uiState.update { state ->
                    state.copy(
                        messagesTab = state.messagesTab.copy(
                            messages = messages.map { msg ->
                                MeshMessageUi(
                                    id = msg.uuid.toString(),
                                    text = msg.text,
                                    fromNodeId = msg.fromNodeId,
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

    private fun MeshPacketLogModel.toLogEntryUi(): LogEntryUi = LogEntryUi(
        formattedTime = logTimeFmt.format(Date(timestamp)),
        direction = when {
            messageType == "fromRadio" && fromNum == 0 -> LogDirection.System
            fromNum == 0 -> LogDirection.Out
            else -> LogDirection.In
        },
        packetType = messageType,
        summary = "from=$fromNum port=$portNum",
        rawHex = rawMessage.take(64).takeIf { it.isNotBlank() },
    )

    private fun MeshMessageDelivery.toUiStatus(): MessageStatus = when (this) {
        MeshMessageDelivery.Pending -> MessageStatus.Pending
        MeshMessageDelivery.Sent -> MessageStatus.Sent
        MeshMessageDelivery.Delivered -> MessageStatus.Acked
        MeshMessageDelivery.Failed -> MessageStatus.Failed
    }

    private fun applyLogFilter(entries: List<LogEntryUi>, filter: LogFilter): List<LogEntryUi> =
        when (filter) {
            LogFilter.All -> entries
            LogFilter.Incoming -> entries.filter { it.direction == LogDirection.In }
            LogFilter.Outgoing -> entries.filter { it.direction == LogDirection.Out }
            LogFilter.System -> entries.filter { it.direction == LogDirection.System }
            LogFilter.Errors -> entries.filter { it.summary.contains("error", ignoreCase = true) }
        }

    private fun formatUptime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%dh %02dm" .format(h, m)
        else if (m > 0) "%dm %02ds".format(m, s)
        else "${s}s"
    }
}
