package ru.tcynik.meshtactics.presentation.feature.settings

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId
import ru.tcynik.meshtactics.presentation.feature.settings.models.ChannelItem
import ru.tcynik.meshtactics.presentation.feature.settings.models.NodeWriteEvent

data class UserSettingsUiState(
    val displayName: String = "",
    val hasUnsavedUserChanges: Boolean = false,
    val channels: ImmutableList<ChannelItem> = persistentListOf(),
    val editorSheet: ChannelEditorState? = null,
    val deleteConfirmId: LogicalChannelId? = null,
    val nodeWriteEvent: NodeWriteEvent? = null,
)

data class ChannelEditorState(
    val id: LogicalChannelId?,
    val name: String,
    val pskBase64: String,
)
