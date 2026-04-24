package ru.tcynik.meshtactics.presentation.feature.settings

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toImmutableList
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.ChannelMetadata
import ru.tcynik.meshtactics.domain.channel.model.ChannelSyncStatus
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannel
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelHash
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticBinding
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
import ru.tcynik.meshtactics.domain.channel.usecase.DeleteLogicalChannelUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveLogicalChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SaveLogicalChannelUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SlotResolution
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.user.usecase.SaveAppUserUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.presentation.feature.settings.models.ChannelItem
import ru.tcynik.meshtactics.presentation.feature.settings.models.NodeWriteEvent
import java.security.SecureRandom
import java.util.UUID

class UserSettingsViewModel(
    private val observeAppUser: ObserveAppUserUseCase,
    private val saveAppUser: SaveAppUserUseCase,
    private val observeLogicalChannels: ObserveLogicalChannelsUseCase,
    private val saveLogicalChannel: SaveLogicalChannelUseCase,
    private val deleteLogicalChannel: DeleteLogicalChannelUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
    private val writeChannel: WriteChannelUseCase,
    private val resolveSlot: ResolveChannelSlotUseCase,
    private val observeConnectionStatus: ObserveConnectionStatusUseCase,
    private val channelSlotResolver: ChannelSlotResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserSettingsUiState())
    val uiState: StateFlow<UserSettingsUiState> = _uiState.asStateFlow()

    private var cachedChannels: List<LogicalChannel> = emptyList()
    private var cachedNodeChannels: List<NodeChannelSlot> = emptyList()
    private var connectionStatus: MeshConnectionStatus = MeshConnectionStatus.Disconnected

    init {
        observeAppUser(NoParams)
            .onEach { user ->
                _uiState.update { state ->
                    if (!state.hasUnsavedUserChanges) state.copy(displayName = user.displayName)
                    else state
                }
            }
            .launchIn(viewModelScope)

        combine(
            observeLogicalChannels(NoParams),
            observeNodeChannels(NoParams),
            observeConnectionStatus(NoParams),
        ) { channels, nodeChannels, status ->
            cachedChannels = channels
            cachedNodeChannels = nodeChannels
            connectionStatus = status

            val items = channels.map { ch ->
                ChannelItem(
                    id = ch.id,
                    name = ch.metadata.name,
                    isAutoSync = ch.isAutoSync,
                    syncStatus = computeSyncStatus(ch, nodeChannels, status),
                )
            }
            _uiState.update { it.copy(channels = items.toImmutableList()) }

            if (status is MeshConnectionStatus.Connected) {
                channels.filter { it.isAutoSync }.forEach { ch ->
                    val resolution = resolveSlot(ch, nodeChannels)
                    if (resolution is SlotResolution.FreeSlot) {
                        pushChannelToNode(ch, resolution.slot)
                    }
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun computeSyncStatus(
        channel: LogicalChannel,
        nodeChannels: List<NodeChannelSlot>,
        status: MeshConnectionStatus,
    ): ChannelSyncStatus {
        if (status !is MeshConnectionStatus.Connected) return ChannelSyncStatus.NotConnected
        val binding = channel.transports.filterIsInstance<MeshtasticBinding>().firstOrNull()
            ?: return ChannelSyncStatus.NotOnNode
        val matched = nodeChannels.find { slot ->
            slot.index != 0 && slot.isEnabled &&
                slot.name == channel.metadata.name && slot.psk.contentEquals(binding.psk)
        }
        if (matched != null) return ChannelSyncStatus.OnNode(matched.index)
        val hasFreeSlot = nodeChannels.any { it.index != 0 && !it.isEnabled }
        return if (hasFreeSlot) ChannelSyncStatus.NotOnNode else ChannelSyncStatus.NoFreeSlot
    }

    private fun pushChannelToNode(channel: LogicalChannel, slot: Int) {
        val binding = channel.transports.filterIsInstance<MeshtasticBinding>().firstOrNull() ?: return
        val pskBase64 = Base64.encodeToString(binding.psk, Base64.NO_WRAP)
        writeChannel(slot, channel.metadata.name, pskBase64)
    }

    fun onPushToNode(id: LogicalChannelId) {
        val channel = cachedChannels.find { it.id == id } ?: return
        if (connectionStatus !is MeshConnectionStatus.Connected) {
            _uiState.update { it.copy(nodeWriteEvent = NodeWriteEvent.NotConnected) }
            return
        }
        when (val resolution = resolveSlot(channel, cachedNodeChannels)) {
            is SlotResolution.AlreadySynced -> {
                _uiState.update { it.copy(nodeWriteEvent = NodeWriteEvent.Sent(channel.metadata.name)) }
            }
            is SlotResolution.FreeSlot -> {
                pushChannelToNode(channel, resolution.slot)
                _uiState.update { it.copy(nodeWriteEvent = NodeWriteEvent.Sent(channel.metadata.name)) }
            }
            is SlotResolution.NoFreeSlot -> {
                _uiState.update { it.copy(nodeWriteEvent = NodeWriteEvent.NoFreeSlot) }
            }
        }
    }

    fun onDeleteFromNode(id: LogicalChannelId) {
        val channel = cachedChannels.find { it.id == id } ?: return
        val binding = channel.transports.filterIsInstance<MeshtasticBinding>().firstOrNull() ?: return
        val slot = channelSlotResolver.hashToSlot[binding.channelHash] ?: return
        if (slot == 0) return
        writeChannel(slot, "", "")
    }

    fun onToggleAutoSync(id: LogicalChannelId, enabled: Boolean) {
        val channel = cachedChannels.find { it.id == id } ?: return
        viewModelScope.launch {
            saveLogicalChannel(channel.copy(isAutoSync = enabled))
        }
    }

    fun onNodeWriteEventConsumed() {
        _uiState.update { it.copy(nodeWriteEvent = null) }
    }

    fun onDisplayNameChange(value: String) {
        _uiState.update { it.copy(displayName = value, hasUnsavedUserChanges = true) }
    }

    fun onSaveUser() {
        viewModelScope.launch {
            saveAppUser(AppUser(displayName = _uiState.value.displayName))
            _uiState.update { it.copy(hasUnsavedUserChanges = false) }
        }
    }

    fun onAddChannelClick() {
        _uiState.update {
            it.copy(editorSheet = ChannelEditorState(id = null, name = "", pskBase64 = ""))
        }
    }

    fun onEditChannelClick(id: LogicalChannelId) {
        val item = _uiState.value.channels.find { it.id == id } ?: return
        _uiState.update {
            it.copy(
                editorSheet = ChannelEditorState(id = id, name = item.name, pskBase64 = "")
            )
        }
    }

    fun onDeleteChannelRequest(id: LogicalChannelId) {
        _uiState.update { it.copy(deleteConfirmId = id) }
    }

    fun onConfirmDelete() {
        val id = _uiState.value.deleteConfirmId ?: return
        _uiState.update { it.copy(deleteConfirmId = null) }
        viewModelScope.launch { deleteLogicalChannel(id) }
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
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        _uiState.update { it.copy(editorSheet = it.editorSheet?.copy(pskBase64 = base64)) }
    }

    fun onEditorSave() {
        val editor = _uiState.value.editorSheet ?: return
        val pskBytes = runCatching {
            Base64.decode(editor.pskBase64, Base64.DEFAULT)
        }.getOrNull() ?: return

        val id = editor.id ?: LogicalChannelId(UUID.randomUUID().toString())
        val existing = cachedChannels.find { it.id == id }
        val channel = LogicalChannel(
            id = id,
            metadata = ChannelMetadata(name = editor.name),
            transports = listOf(
                MeshtasticBinding(
                    psk = pskBytes,
                    channelHash = LogicalChannelHash.compute(editor.name, pskBytes),
                )
            ),
            isAutoSync = existing?.isAutoSync ?: false,
        )
        viewModelScope.launch {
            saveLogicalChannel(channel)
            _uiState.update { it.copy(editorSheet = null) }
        }
    }

    fun onEditorDismiss() {
        _uiState.update { it.copy(editorSheet = null) }
    }
}

// TODO(channels): write confirmation dialog before pushing to node
// TODO(channels): loading + result indication requires ACK tracking in CommandSender
// TODO(channels): "manage node slots" sheet for NoFreeSlot case
// TODO(channels): user-to-user channel import/export
