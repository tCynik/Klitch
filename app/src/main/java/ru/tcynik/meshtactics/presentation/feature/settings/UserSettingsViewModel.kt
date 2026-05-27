package ru.tcynik.meshtactics.presentation.feature.settings

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.collections.immutable.toImmutableList
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.ChannelSyncStatus
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.channel.usecase.DeleteContourUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SaveContourUseCase
import ru.tcynik.meshtactics.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.meshtactics.domain.channel.model.NodeSyncResult
import ru.tcynik.meshtactics.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SetContourActiveUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SlotResolution
import ru.tcynik.meshtactics.domain.channel.usecase.SyncContoursOnConnectUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.CancelEmergencyUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.ObserveEmergencyModeUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.TriggerEmergencyUseCase
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.usecase.CheckOwnPkcHealthUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.DisconnectFromMeshUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.DisableNodePositionBroadcastUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.EnableNodePositionBroadcastReadyUseCase
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveGpsBroadcastEnabledUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RebootNodeUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RefreshNodePublicKeysUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RegeneratePkcKeysUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.SetGpsBroadcastEnabledUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.user.usecase.SaveAppUserUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.presentation.feature.settings.models.ContourItem
import ru.tcynik.meshtactics.presentation.feature.settings.models.NodeWriteEvent
import java.util.UUID

class UserSettingsViewModel(
    private val observeAppUser: ObserveAppUserUseCase,
    private val saveAppUser: SaveAppUserUseCase,
    private val observeContours: ObserveContoursUseCase,
    private val saveContour: SaveContourUseCase,
    private val deleteContour: DeleteContourUseCase,
    private val setContourActive: SetContourActiveUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
    private val writeChannel: WriteChannelUseCase,
    private val resolveSlot: ResolveChannelSlotUseCase,
    private val observeConnectionStatus: ObserveConnectionStatusUseCase,
    private val channelSlotResolver: ChannelSlotResolver,
    private val syncContoursOnConnect: SyncContoursOnConnectUseCase,
    private val enableNodePositionBroadcastReady: EnableNodePositionBroadcastReadyUseCase,
    private val disableNodePositionBroadcast: DisableNodePositionBroadcastUseCase,
    private val observeEmergencyMode: ObserveEmergencyModeUseCase,
    private val triggerEmergency: TriggerEmergencyUseCase,
    private val cancelEmergency: CancelEmergencyUseCase,
    private val checkContourSync: CheckNodeSyncUseCase,
    private val syncStateRepository: ContourSyncStateRepository,
    private val disconnectFromMesh: DisconnectFromMeshUseCase,
    private val rebootNode: RebootNodeUseCase,
    private val rebootStateRepository: RebootStateRepository,
    private val observeGpsBroadcastEnabled: ObserveGpsBroadcastEnabledUseCase,
    private val setGpsBroadcastEnabled: SetGpsBroadcastEnabledUseCase,
    private val observeDeviceConfig: ObserveDeviceConfigUseCase,
    private val writeOwner: WriteOwnerUseCase,
    private val checkOwnPkcHealth: CheckOwnPkcHealthUseCase,
    private val refreshNodePublicKeys: RefreshNodePublicKeysUseCase,
    private val regeneratePkcKeys: RegeneratePkcKeysUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserSettingsUiState())
    val uiState: StateFlow<UserSettingsUiState> = _uiState.asStateFlow()

    private val _navigateBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateBack: SharedFlow<Unit> = _navigateBack.asSharedFlow()

    private var cachedContours: List<Contour> = emptyList()
    private var cachedNodeChannels: List<NodeChannelSlot> = emptyList()
    private var connectionStatus: MeshConnectionStatus = MeshConnectionStatus.Disconnected
    private var initialized = false
    private var needsPkcRegen: Boolean = false

    init {
        observeAppUser(NoParams)
            .onEach { user ->
                _uiState.update { state ->
                    if (!state.hasUnsavedUserChanges) state.copy(displayName = user.displayName)
                    else state
                }
            }
            .launchIn(viewModelScope)

        observeEmergencyMode()
            .onEach { isActive -> _uiState.update { it.copy(emergencyMode = isActive) } }
            .launchIn(viewModelScope)

        observeGpsBroadcastEnabled()
            .onEach { enabled -> _uiState.update { it.copy(isGpsBroadcastEnabled = enabled) } }
            .launchIn(viewModelScope)

        combine(
            observeContours(NoParams),
            observeNodeChannels(NoParams),
            observeConnectionStatus(NoParams),
        ) { contours, nodeChannels, status ->
            cachedContours = contours
            cachedNodeChannels = nodeChannels
            val wasConnected = connectionStatus is MeshConnectionStatus.Connected
            connectionStatus = status

            val items = contours.map { contour ->
                ContourItem(
                    id = contour.id,
                    name = contour.name,
                    description = contour.description,
                    expiration = contour.expiration,
                    exclusivityTime = contour.exclusivityTime,
                    isActive = contour.isActive,
                    isEmergency = contour.isEmergency,
                    syncStatus = computeSyncStatus(contour, nodeChannels, status),
                )
            }
            _uiState.update {
                it.copy(
                    contours = items.toImmutableList(),
                    isNodeConnected = status is MeshConnectionStatus.Connected,
                )
            }

            val justConnected = initialized && !wasConnected && status is MeshConnectionStatus.Connected
            initialized = true
            if (justConnected) {
                onConnected(contours)
            }
        }.launchIn(viewModelScope)
    }

    private fun onConnected(contours: List<Contour>) {
        viewModelScope.launch {
            val emergencyActive = contours.find { it.isEmergency }?.isActive ?: false
            val broadcastEnabled = observeGpsBroadcastEnabled().first()
            if (emergencyActive || !broadcastEnabled) {
                disableNodePositionBroadcast()
            } else {
                enableNodePositionBroadcastReady()
            }

            needsPkcRegen = checkOwnPkcHealth()
            refreshNodePublicKeys()
            delay(30_000)
            refreshNodePublicKeys()
        }
    }

    private fun computeSyncStatus(
        contour: Contour,
        nodeChannels: List<NodeChannelSlot>,
        status: MeshConnectionStatus,
    ): ChannelSyncStatus {
        if (contour.id == DefaultContour.ID) return ChannelSyncStatus.OnNode(0)
        if (status !is MeshConnectionStatus.Connected) return ChannelSyncStatus.NotConnected
        val hash = contour.transport.meshtastic.channelHash
        val matched = nodeChannels.find { slot ->
            slot.index != 0 && slot.isEnabled &&
                ContourHash.compute(slot.name, slot.psk) == hash
        }
        if (matched != null) return ChannelSyncStatus.OnNode(matched.index)
        val hasFreeSlot = nodeChannels.any { it.index != 0 && !it.isEnabled }
        return if (hasFreeSlot) ChannelSyncStatus.NotOnNode else ChannelSyncStatus.NoFreeSlot
    }

    private fun pushContourToNode(contour: Contour, slot: Int) {
        writeChannel(slot, contour.name, contour.transport.meshtastic.psk)
    }

    fun onPushToNode(id: ContourId) {
        val contour = cachedContours.find { it.id == id } ?: return
        if (connectionStatus !is MeshConnectionStatus.Connected) {
            _uiState.update { it.copy(nodeWriteEvent = NodeWriteEvent.NotConnected) }
            return
        }
        when (val resolution = resolveSlot(contour, cachedNodeChannels)) {
            is SlotResolution.AlreadySynced -> {
                _uiState.update { it.copy(nodeWriteEvent = NodeWriteEvent.Sent(contour.name)) }
            }
            is SlotResolution.FreeSlot -> {
                pushContourToNode(contour, resolution.slot)
                _uiState.update { it.copy(nodeWriteEvent = NodeWriteEvent.Sent(contour.name)) }
            }
            is SlotResolution.NoFreeSlot -> {
                _uiState.update { it.copy(nodeWriteEvent = NodeWriteEvent.NoFreeSlot) }
            }
        }
    }

    fun onDeleteFromNode(id: ContourId) {
        val contour = cachedContours.find { it.id == id } ?: return
        val hash = contour.transport.meshtastic.channelHash
        val slot = channelSlotResolver.hashToSlot[hash] ?: return
        if (slot == 0) return
        writeChannel(slot, "", "")
    }

    fun onToggleActive(id: ContourId, isActive: Boolean) {
        viewModelScope.launch {
            setContourActive(id, isActive)
            if (isActive && connectionStatus is MeshConnectionStatus.Connected) {
                if (checkContourSync() is NodeSyncResult.NeedsSync) {
                    _uiState.update { it.copy(showSyncDialog = true) }
                }
            }
        }
    }

    fun onConfirmChannelSync() {
        _uiState.update { it.copy(showSyncDialog = false) }
        viewModelScope.launch {
            if (needsPkcRegen) {
                regeneratePkcKeys()
                needsPkcRegen = false
            }
            syncContoursOnConnect()
            rebootStateRepository.setRebooting(true)
            rebootNode()
            syncStateRepository.clear()
        }
    }

    fun onDismissChannelSync() {
        _uiState.update { it.copy(showSyncDialog = false) }
        syncStateRepository.setSyncRequired(true)
        viewModelScope.launch { disconnectFromMesh(NoParams) }
    }

    fun onNodeWriteEventConsumed() {
        _uiState.update { it.copy(nodeWriteEvent = null) }
    }

    fun onDisplayNameChange(value: String) {
        _uiState.update { it.copy(displayName = value, hasUnsavedUserChanges = true, displayNameError = false) }
    }

    fun onGpsBroadcastToggle(enabled: Boolean) {
        viewModelScope.launch {
            setGpsBroadcastEnabled(enabled)
            if (connectionStatus is MeshConnectionStatus.Connected) {
                if (enabled) enableNodePositionBroadcastReady()
                else disableNodePositionBroadcast()
            }
        }
    }

    fun onNavigateBackRequested() {
        if (_uiState.value.hasUnsavedUserChanges && _uiState.value.isNodeConnected) {
            _uiState.update { it.copy(showLeaveDialog = true) }
        } else {
            if (_uiState.value.hasUnsavedUserChanges) {
                viewModelScope.launch { saveAppUser(AppUser(displayName = _uiState.value.displayName)) }
            }
            _navigateBack.tryEmit(Unit)
        }
    }

    fun onSaveAndReboot() {
        _uiState.update { it.copy(showLeaveDialog = false) }
        viewModelScope.launch {
            if (_uiState.value.displayName.isBlank()) {
                _uiState.update { it.copy(displayNameError = true) }
                return@launch
            }
            val shortName = withTimeoutOrNull(5_000) {
                observeDeviceConfig(NoParams).first { it != null }
            }?.shortName ?: ""
            writeOwner(_uiState.value.displayName, shortName)
            regeneratePkcKeys()
            saveAppUser(AppUser(displayName = _uiState.value.displayName))
            _uiState.update { it.copy(hasUnsavedUserChanges = false) }
            rebootStateRepository.setRebooting(true)
            rebootNode()
            _navigateBack.tryEmit(Unit)
        }
    }

    fun onDiscardAndLeave() {
        _uiState.update { it.copy(showLeaveDialog = false, hasUnsavedUserChanges = false) }
        viewModelScope.launch {
            val saved = observeAppUser(NoParams).first()
            _uiState.update { it.copy(displayName = saved.displayName) }
            _navigateBack.tryEmit(Unit)
        }
    }

    fun onDismissLeaveDialog() {
        _uiState.update { it.copy(showLeaveDialog = false) }
    }

    fun onAddContourClick() {
        _uiState.update {
            it.copy(editorSheet = ContourEditorState(id = null, name = "", pskBase64 = ""))
        }
    }

    fun onEditContourClick(id: ContourId) {
        val item = _uiState.value.contours.find { it.id == id } ?: return
        _uiState.update {
            it.copy(editorSheet = ContourEditorState(id = id, name = item.name, pskBase64 = ""))
        }
    }

    fun onDeleteContourRequest(id: ContourId) {
        val contour = cachedContours.find { it.id == id } ?: return
        if (contour.isEmergency) return
        _uiState.update { it.copy(deleteConfirmId = id) }
    }

    fun onConfirmDelete() {
        val id = _uiState.value.deleteConfirmId ?: return
        _uiState.update { it.copy(deleteConfirmId = null) }
        viewModelScope.launch { deleteContour(id) }
    }

    fun onDismissDelete() {
        _uiState.update { it.copy(deleteConfirmId = null) }
    }

    fun onEditorNameChange(value: String) {
        _uiState.update { it.copy(editorSheet = it.editorSheet?.copy(name = value)) }
    }

    fun onEditorPskChange(value: String) {
        _uiState.update { it.copy(editorSheet = it.editorSheet?.copy(pskBase64 = value)) }
    }

    fun onEditorGeneratePsk() {
        val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        _uiState.update { it.copy(editorSheet = it.editorSheet?.copy(pskBase64 = base64)) }
    }

    fun onEditorSave() {
        val editor = _uiState.value.editorSheet ?: return
        val pskBytes = runCatching {
            Base64.decode(editor.pskBase64, Base64.DEFAULT)
        }.getOrNull() ?: return

        val id = editor.id ?: ContourId(UUID.randomUUID().toString())
        val existing = cachedContours.find { it.id == id }
        val hash = ContourHash.compute(editor.name, pskBytes)
        val contour = Contour(
            id = id,
            name = editor.name,
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = existing?.isActive ?: true,
            transport = ContourTransport(
                meshtastic = MeshtasticChannel(
                    psk = editor.pskBase64,
                    channelHash = hash,
                ),
            ),
        )
        viewModelScope.launch {
            saveContour(contour)
            _uiState.update { it.copy(editorSheet = null) }
        }
    }

    fun onEditorDismiss() {
        _uiState.update { it.copy(editorSheet = null) }
    }

    fun onSosClick() {
        if (_uiState.value.emergencyMode) {
            _uiState.update { it.copy(showCancelDialog = true) }
        } else {
            _uiState.update { it.copy(showTriggerDialog = true) }
        }
    }

    fun onTriggerEmergencyConfirm() {
        _uiState.update { it.copy(showTriggerDialog = false) }
        viewModelScope.launch {
            triggerEmergency()
            _uiState.update { it.copy(emergencyEvent = EmergencyEvent.Triggered) }
        }
    }

    fun onCancelEmergencyConfirm() {
        _uiState.update { it.copy(showCancelDialog = false) }
        viewModelScope.launch { cancelEmergency() }
    }

    fun onDismissTriggerDialog() {
        _uiState.update { it.copy(showTriggerDialog = false) }
    }

    fun onDismissCancelDialog() {
        _uiState.update { it.copy(showCancelDialog = false) }
    }

    fun onEmergencyEventConsumed() {
        _uiState.update { it.copy(emergencyEvent = null) }
    }
}

// TODO(contour): write confirmation dialog before pushing to node
// TODO(contour): loading + result indication requires ACK tracking in CommandSender
// TODO(contour): "manage node slots" sheet for NoFreeSlot case
// TODO(contour): unblock Custom Контур creation after sharing is implemented
