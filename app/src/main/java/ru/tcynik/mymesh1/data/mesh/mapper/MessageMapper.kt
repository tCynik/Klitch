package ru.tcynik.mymesh1.data.mesh.mapper

import ru.tcynik.mymesh1.domain.mesh.model.MeshMessageDelivery
import ru.tcynik.mymesh1.domain.mesh.model.MeshMessageModel
import ru.tcynik.mymesh1.mesh.model.Message
import ru.tcynik.mymesh1.mesh.model.MessageStatus

fun Message.toMeshMessageModel(): MeshMessageModel = MeshMessageModel(
    uuid = uuid,
    text = text,
    fromNodeId = node.user.id,
    formattedTime = time,
    isOutgoing = fromLocal,
    deliveryStatus = status.toDelivery(),
)

private fun MessageStatus?.toDelivery(): MeshMessageDelivery = when (this) {
    MessageStatus.QUEUED -> MeshMessageDelivery.Pending
    MessageStatus.ENROUTE -> MeshMessageDelivery.Sent
    MessageStatus.DELIVERED, MessageStatus.RECEIVED,
    MessageStatus.SFPP_CONFIRMED -> MeshMessageDelivery.Delivered
    MessageStatus.ERROR -> MeshMessageDelivery.Failed
    else -> MeshMessageDelivery.Pending
}
