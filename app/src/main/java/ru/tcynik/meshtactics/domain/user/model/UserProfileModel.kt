package ru.tcynik.meshtactics.domain.user.model

import ru.tcynik.meshtactics.domain.group.model.GroupRole

data class UserProfileModel(
    val callsign: String,
    val nodeId: String,
    val role: GroupRole,
)
