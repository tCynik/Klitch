package ru.tcynik.mymesh1.domain.mesh.model

data class MeshMessageModel(
    val uuid: Long,
    val text: String,
    val fromNodeId: String,
    val formattedTime: String,
    val isOutgoing: Boolean,
    val deliveryStatus: MeshMessageDelivery,
)

enum class MeshMessageDelivery { Pending, Sent, Delivered, Failed }
