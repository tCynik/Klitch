package ru.tcynik.klitch.domain.mesh.model

data class MeshMessageModel(
    val uuid: Long,
    val text: String,
    val fromNodeId: String,
    val fromNodeName: String,
    val formattedTime: String,
    val isOutgoing: Boolean,
    val deliveryStatus: MeshMessageDelivery,
)

enum class MeshMessageDelivery { Pending, Sent, Delivered, Failed }
