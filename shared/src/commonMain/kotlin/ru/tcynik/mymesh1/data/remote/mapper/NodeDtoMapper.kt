package ru.tcynik.mymesh1.data.remote.mapper

import kotlinx.datetime.Instant
import ru.tcynik.mymesh1.data.remote.dto.NodeDto
import ru.tcynik.mymesh1.domain.model.NodeModel

fun NodeDto.toDomain(): NodeModel = NodeModel(
    id = id,
    name = name,
    address = address,
    rssi = rssi,
    isConnected = isConnected,
    lastSeen = Instant.fromEpochMilliseconds(lastSeen),
)

fun List<NodeDto>.toDomain(): List<NodeModel> = map { it.toDomain() }
