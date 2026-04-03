package ru.tcynik.meshtactics.domain.mesh.model

data class MeshPacketLogModel(
    val uuid: String,
    val timestamp: Long,
    val messageType: String,
    val fromNum: Int,
    val portNum: Int,
    val rawMessage: String,
)
