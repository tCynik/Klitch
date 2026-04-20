package ru.tcynik.meshtactics.presentation.feature.settings

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.domain.channel.model.ChannelMetadata
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannel
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticBinding
import ru.tcynik.meshtactics.domain.channel.usecase.DeleteLogicalChannelUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveLogicalChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SaveLogicalChannelUseCase
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.user.usecase.SaveAppUserUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.presentation.feature.settings.models.ChannelItem
import java.security.SecureRandom
import java.util.UUID

class UserSettingsViewModel(
    private val observeAppUser: ObserveAppUserUseCase,
    private val saveAppUser: SaveAppUserUseCase,
    private val observeLogicalChannels: ObserveLogicalChannelsUseCase,
    private val saveLogicalChannel: SaveLogicalChannelUseCase,
    private val deleteLogicalChannel: DeleteLogicalChannelUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserSettingsUiState())
    val uiState: StateFlow<UserSettingsUiState> = _uiState.asStateFlow()

    init {
        observeAppUser(NoParams)
            .onEach { user ->
                _uiState.update { state ->
                    if (!state.hasUnsavedUserChanges) {
                        state.copy(displayName = user.displayName)
                    } else state
                }
            }
            .launchIn(viewModelScope)

        observeLogicalChannels(NoParams)
            .onEach { channels ->
                _uiState.update { state ->
                    state.copy(
                        channels = channels.map { ch ->
                            val mtBinding = ch.transports.filterIsInstance<MeshtasticBinding>().firstOrNull()
                            ChannelItem(
                                id = ch.id,
                                name = ch.metadata.name,
                                transportLabel = if (mtBinding != null) "MT · слот ${mtBinding.channelIndex}" else "",
                            )
                        }
                    )
                }
            }
            .launchIn(viewModelScope)
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
            it.copy(editorSheet = ChannelEditorState(id = null, name = "", slotIndex = 0, pskBase64 = ""))
        }
    }

    fun onEditChannelClick(id: LogicalChannelId) {
        val item = _uiState.value.channels.find { it.id == id } ?: return
        // Extract slot index from transportLabel if present
        val slotIndex = Regex("слот (\\d+)").find(item.transportLabel)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        _uiState.update {
            it.copy(
                editorSheet = ChannelEditorState(
                    id = id,
                    name = item.name,
                    slotIndex = slotIndex,
                    pskBase64 = "",
                )
            )
        }
    }

    fun onDeleteChannelRequest(id: LogicalChannelId) {
        _uiState.update { it.copy(deleteConfirmId = id) }
    }

    fun onConfirmDelete() {
        val id = _uiState.value.deleteConfirmId ?: return
        _uiState.update { it.copy(deleteConfirmId = null) }
        viewModelScope.launch {
            deleteLogicalChannel(id)
        }
    }

    fun onDismissDelete() {
        _uiState.update { it.copy(deleteConfirmId = null) }
    }

    fun onEditorNameChange(value: String) {
        _uiState.update { it.copy(editorSheet = it.editorSheet?.copy(name = value)) }
    }

    fun onEditorSlotChange(slot: Int) {
        _uiState.update { it.copy(editorSheet = it.editorSheet?.copy(slotIndex = slot)) }
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
        val channel = LogicalChannel(
            id = id,
            metadata = ChannelMetadata(name = editor.name),
            transports = listOf(
                MeshtasticBinding(channelIndex = editor.slotIndex, psk = pskBytes)
            ),
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
