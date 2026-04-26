package ru.tcynik.meshtactics.presentation.feature.settings

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.presentation.feature.settings.models.ContourItem
import ru.tcynik.meshtactics.presentation.feature.settings.models.NodeWriteEvent

data class UserSettingsUiState(
    val displayName: String = "",
    val hasUnsavedUserChanges: Boolean = false,
    val contours: ImmutableList<ContourItem> = persistentListOf(),
    val editorSheet: ContourEditorState? = null,
    val deleteConfirmId: ContourId? = null,
    val nodeWriteEvent: NodeWriteEvent? = null,
)

data class ContourEditorState(
    val id: ContourId?,
    val name: String,
    val pskBase64: String,
)
