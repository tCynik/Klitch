package ru.tcynik.meshtactics.presentation.feature.settings

import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId
import ru.tcynik.meshtactics.presentation.feature.settings.models.ChannelItem

data class UserSettingsUiState(
    val displayName: String = "",
    val hasUnsavedUserChanges: Boolean = false,
    val channels: List<ChannelItem> = emptyList(),
    val editorSheet: ChannelEditorState? = null,
    val deleteConfirmId: LogicalChannelId? = null,
)

data class ChannelEditorState(
    val id: LogicalChannelId?,
    val name: String,
    val slotIndex: Int,
    val pskBase64: String,
)
