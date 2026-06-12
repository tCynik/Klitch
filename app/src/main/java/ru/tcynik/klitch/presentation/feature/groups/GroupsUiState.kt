package ru.tcynik.klitch.presentation.feature.groups

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.tcynik.klitch.domain.group.model.GroupModel

// Beta 1.0 feature
data class GroupsUiState(
    val groups: ImmutableList<GroupModel> = persistentListOf(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
