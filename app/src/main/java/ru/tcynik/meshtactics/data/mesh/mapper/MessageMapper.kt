package ru.tcynik.meshtactics.data.mesh.mapper

import ru.tcynik.meshtactics.domain.mesh.model.MeshMessageDelivery
import ru.tcynik.meshtactics.domain.mesh.model.MeshMessageModel
import ru.tcynik.meshtactics.mesh.model.Message
import ru.tcynik.meshtactics.mesh.model.MessageStatus

fun Message.toMeshMessageModel(): MeshMessageModel = MeshMessageModel(
    uuid = uuid,
    text = text,
    fromNodeId = node.user.id,
    fromNodeName = node.user.long_name.ifBlank { node.user.id },
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
