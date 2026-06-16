// Copyright (c) 2025 tCynik — modifications under GPL-3.0

package ru.tcynik.klitch.mesh.model

/**
 * Type-safe wrapper for the DB contact key format: "${slot}${nodeId}".
 * Examples: "0^all" (slot 0, broadcast), "2!ab1234cd" (slot 2, direct), "8!ab1234cd" (PKC DM).
 */
@JvmInline value class MeshContactKey(val raw: String) {
    val slot: Int get() = raw.first().digitToInt()
    val nodeId: String get() = raw.dropWhile { it.isDigit() }
    val isBroadcast: Boolean get() = nodeId == DataPacket.ID_BROADCAST
    val isDirect: Boolean get() = nodeId.startsWith("!")

    companion object {
        fun broadcast(slot: Int): MeshContactKey = MeshContactKey("$slot${DataPacket.ID_BROADCAST}")
        fun direct(slot: Int, nodeId: String): MeshContactKey = MeshContactKey("$slot$nodeId")
    }
}
