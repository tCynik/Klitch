package ru.tcynik.meshtactics.presentation.feature.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.domain.mesh.model.GpsMode
import ru.tcynik.meshtactics.domain.mesh.model.LocationConfigModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveLocationConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveOurNodeUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RemoveFixedPositionUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RequestDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.SetProvideLocationUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelPositionPrecisionUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.BeginSettingsEditUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.CommitSettingsEditUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WritePositionConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.util.PskValidator
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.presentation.feature.network.state.ChannelConfigUi
import ru.tcynik.meshtactics.presentation.feature.network.state.DeviceConfigUi
import ru.tcynik.meshtactics.presentation.feature.network.state.MeshConnectionStatusUi
import ru.tcynik.meshtactics.presentation.feature.network.state.models.GpsModeUi
import ru.tcynik.meshtactics.presentation.feature.network.state.models.LocationConfigUi

class NetworkSettingsViewModel(
    private val observeConnectionStatus: ObserveConnectionStatusUseCase,
    private val observeDeviceConfig: ObserveDeviceConfigUseCase,
    private val requestDeviceConfig: RequestDeviceConfigUseCase,
    private val beginSettingsEdit: BeginSettingsEditUseCase,
    private val commitSettingsEdit: CommitSettingsEditUseCase,
    private val writeOwner: WriteOwnerUseCase,
    private val writeChannel: WriteChannelUseCase,
    private val observeOurNode: ObserveOurNodeUseCase,
    private val observeLocationConfig: ObserveLocationConfigUseCase,
    private val setProvideLocation: SetProvideLocationUseCase,
    private val writePositionConfig: WritePositionConfigUseCase,
    private val writeChannelPositionPrecision: WriteChannelPositionPrecisionUseCase,
    private val removeFixedPosition: RemoveFixedPositionUseCase,
    private val logger: Logger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkSettingsUiState())
    val uiState: StateFlow<NetworkSettingsUiState> = _uiState.asStateFlow()

    private val myNodeNumFlow = MutableStateFlow<Int?>(null)
    private var wasConnected = false
    private var readConfigTimeoutJob: Job? = null

    init {
        observeConnectionStatus(NoParams)
            .onEach { status ->
                val isConnected = status is MeshConnectionStatus.Connected
                _uiState.update { state ->
                    state.copy(
                        connectionStatus = status.toUi(),
                        settings = if (!isConnected && state.settings.isLoading) {
                            state.settings.copy(isLoading = false)
                        } else {
                            state.settings
                        },
                    )
                }
                if (isConnected && !wasConnected) {
                    val settings = _uiState.value.settings
                    if (!settings.isLoading && settings.originalDeviceConfig == null) {
                        requestConfigFromDevice()
                    }
                }
                wasConnected = isConnected
            }
            .launchIn(viewModelScope)

        observeDeviceConfig(NoParams).onEach { config ->
            logger.i("App", "DBG observeDeviceConfig emitted: config=${config?.let { "longName=${it.longName}" } ?: "null"}")
            if (config != null) {
                _uiState.update { state ->
                    val mapped = config.channels.map { ch ->
                        ChannelConfigUi(index = ch.index, channelName = ch.name, pskBase64 = ch.pskBase64)
                    }
                    // После reconnect handleMyInfo очищает channelSet до завершения handshake.
                    val safeChannels = if (mapped.isEmpty() && state.settings.originalChannels.isNotEmpty()) {
                        state.settings.originalChannels
                    } else {
                        mapped
                    }
                    val newDeviceConfig = DeviceConfigUi(
                        longName = config.longName,
                        shortName = config.shortName,
                        loraPreset = config.loraPreset,
                        txPowerDbm = config.txPowerDbm,
                        region = config.region,
                    )
                    val hasUnsaved = state.settings.hasChanges
                    state.copy(
                        settings = state.settings.copy(
                            isLoading = false,
                            deviceConfig = if (hasUnsaved) state.settings.deviceConfig else newDeviceConfig,
                            channels = if (hasUnsaved) state.settings.channels else safeChannels,
                            originalDeviceConfig = newDeviceConfig,
                            originalChannels = safeChannels,
                        )
                    )
                }
                readConfigTimeoutJob?.cancel()
            }
        }.launchIn(viewModelScope)

        observeOurNode(NoParams).onEach { node ->
            myNodeNumFlow.value = node?.num
        }.launchIn(viewModelScope)

        @OptIn(ExperimentalCoroutinesApi::class)
        myNodeNumFlow
            .filterNotNull()
            .flatMapLatest { nodeNum -> observeLocationConfig(nodeNum) }
            .onEach { config ->
                _uiState.update { state ->
                    state.copy(settings = state.settings.copy(locationConfig = config.toLocationUi()))
                }
            }
            .launchIn(viewModelScope)
    }

    fun onReadConfigClick() {
        requestConfigFromDevice()
    }

    private fun requestConfigFromDevice() {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(isLoading = true))
        }
        requestDeviceConfig()
        readConfigTimeoutJob?.cancel()
        readConfigTimeoutJob = viewModelScope.launch {
            delay(READ_CONFIG_TIMEOUT_MS)
            _uiState.update { state ->
                if (state.settings.isLoading) {
                    state.copy(settings = state.settings.copy(isLoading = false))
                } else {
                    state
                }
            }
        }
    }

    fun onConfigLongNameChange(value: String) {
        _uiState.update { state ->
            val cfg = state.settings.deviceConfig ?: return@update state
            state.copy(settings = state.settings.copy(deviceConfig = cfg.copy(longName = value)))
        }
    }

    fun onConfigShortNameChange(value: String) {
        _uiState.update { state ->
            val cfg = state.settings.deviceConfig ?: return@update state
            state.copy(settings = state.settings.copy(deviceConfig = cfg.copy(shortName = value.take(4))))
        }
    }

    fun onWriteConfigClick() {
        val settings = _uiState.value.settings
        val cfg = settings.deviceConfig ?: return
        viewModelScope.launch {
            logger.i("Node", "onWriteConfigClick: writing owner='${cfg.longName}' + ${settings.channels.count { it.pskError == null }} channels — firmware reboot expected")
            beginSettingsEdit()
            writeOwner(cfg.longName, cfg.shortName)
            settings.channels.forEach { ch ->
                if (ch.pskError == null) {
                    writeChannel(ch.index, ch.channelName, ch.pskBase64)
                }
            }
            commitSettingsEdit()
        }
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(
                    originalDeviceConfig = state.settings.deviceConfig,
                    originalChannels = state.settings.channels,
                )
            )
        }
    }

    fun onChannelNameChange(index: Int, value: String) {
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(
                    channels = state.settings.channels.map { ch ->
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
                settings = state.settings.copy(
                    channels = state.settings.channels.map { ch ->
                        if (ch.index == index) ch.copy(pskBase64 = value, pskError = error) else ch
                    }
                )
            )
        }
    }

    fun onAddChannelClick() {
        _uiState.update { state ->
            val channels = state.settings.channels
            if (channels.size >= 8) return@update state
            val nextIndex = channels.size
            val newChannel = ChannelConfigUi(index = nextIndex, channelName = "", pskBase64 = "")
            state.copy(settings = state.settings.copy(channels = channels + newChannel))
        }
    }

    fun onProvideLocationToggle(enabled: Boolean) {
        val nodeNum = myNodeNumFlow.value ?: return
        setProvideLocation(nodeNum, enabled)
        if (enabled) removeFixedPosition(nodeNum)
    }

    fun onGpsModeChange(mode: GpsModeUi) {
        val nodeNum = myNodeNumFlow.value ?: return
        val config = _uiState.value.settings.locationConfig ?: return
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
        val config = _uiState.value.settings.locationConfig ?: return
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
        val config = _uiState.value.settings.locationConfig ?: return
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
        val config = _uiState.value.settings.locationConfig ?: return
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

    private companion object {
        const val READ_CONFIG_TIMEOUT_MS = 15_000L
    }
}
