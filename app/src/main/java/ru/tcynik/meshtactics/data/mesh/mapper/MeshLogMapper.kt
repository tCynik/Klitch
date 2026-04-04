package ru.tcynik.meshtactics.data.mesh.mapper

import org.meshtastic.proto.PortNum
import ru.tcynik.meshtactics.domain.mesh.model.MeshPacketLogModel
import ru.tcynik.meshtactics.mesh.model.MeshLog

fun MeshLog.toMeshPacketLogModel(): MeshPacketLogModel = MeshPacketLogModel(
    uuid = uuid,
    timestamp = received_date,
    messageType = message_type,
    fromNum = fromNum,
    portNum = portNum,
    rawMessage = raw_message,
)

/** Human-readable port name for display in Log tab. */
fun Int.toPortNumName(): String =
    PortNum.fromValue(this)?.name ?: "PORT_$this"
