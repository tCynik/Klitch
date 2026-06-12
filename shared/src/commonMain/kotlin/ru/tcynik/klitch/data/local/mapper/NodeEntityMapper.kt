package ru.tcynik.klitch.data.local.mapper

import kotlinx.datetime.Instant
// Node — генерируется SQLDelight из Node.sq после первого билда
import ru.tcynik.klitch.data.local.Node
import ru.tcynik.klitch.domain.model.NodeModel

fun Node.toDomain(): NodeModel = NodeModel(
    id = id,
    name = name,
    address = address,
    rssi = rssi.toInt(),
    isConnected = is_connected == 1L,
    lastSeen = Instant.fromEpochMilliseconds(last_seen),
)
