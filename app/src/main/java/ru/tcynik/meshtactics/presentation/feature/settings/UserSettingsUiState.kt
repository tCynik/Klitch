package ru.tcynik.meshtactics.presentation.feature.settings

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.presentation.feature.settings.models.ContourItem
import ru.tcynik.meshtactics.presentation.feature.settings.models.NodeWriteEvent

sealed interface EmergencyEvent {
    data object Triggered : EmergencyEvent
}

data class UserSettingsUiState(
    val displayName: String = "",
    val hasUnsavedUserChanges: Boolean = false,
    val contours: ImmutableList<ContourItem> = persistentListOf(),
    val editorSheet: ContourEditorState? = null,
    val deleteConfirmId: ContourId? = null,
    val nodeWriteEvent: NodeWriteEvent? = null,
    val emergencyMode: Boolean = false,
    val isNodeConnected: Boolean = false,
    val showTriggerDialog: Boolean = false,
    val showCancelDialog: Boolean = false,
    val emergencyEvent: EmergencyEvent? = null,
    val showSyncDialog: Boolean = false,
)

data class ContourEditorState(
    val id: ContourId?,
    val name: String,
    val pskBase64: String,
)
