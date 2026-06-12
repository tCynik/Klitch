package ru.tcynik.klitch.domain.group.model

data class GroupModel(
    val id: String,
    val name: String,
    val channelId: String,
    val memberNodeIds: List<String>,
)
