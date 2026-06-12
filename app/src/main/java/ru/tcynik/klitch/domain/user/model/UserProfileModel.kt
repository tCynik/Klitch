package ru.tcynik.klitch.domain.user.model

import ru.tcynik.klitch.domain.group.model.GroupRole

data class UserProfileModel(
    val callsign: String,
    val nodeId: String,
    val role: GroupRole,
)
