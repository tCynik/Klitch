package ru.tcynik.klitch.domain.marker.util

import java.util.UUID

object WaypointIdConverter {
    /** Derives a non-zero Meshtastic waypoint id from the app mark UUID. */
    fun waypointIdFromMarkId(markId: String): Int {
        val raw = try {
            (UUID.fromString(markId).leastSignificantBits and 0x7FFF_FFFFL).toInt()
        } catch (_: IllegalArgumentException) {
            markId.hashCode() and 0x7FFF_FFFF
        }
        return if (raw == 0) 1 else raw
    }
}
