package ru.tcynik.meshtactics.data.local.mapper

import kotlinx.datetime.Instant
// Node — генерируется SQLDelight из Node.sq после первого билда
import ru.tcynik.meshtactics.data.local.Node
import ru.tcynik.meshtactics.domain.model.NodeModel

fun Node.toDomain(): NodeModel = NodeModel(
    id = id,
    name = name,
    address = address,
    rssi = rssi.toInt(),
    isConnected = is_connected == 1L,
    lastSeen = Instant.fromEpochMilliseconds(last_seen),
)
