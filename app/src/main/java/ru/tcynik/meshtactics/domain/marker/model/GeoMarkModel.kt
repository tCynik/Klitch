package ru.tcynik.meshtactics.domain.marker.model

data class GeoMarkModel(
    /** UUID, stable key for UI and SQLDelight primary key. */
    val id: String,
    /** Meshtastic waypoint ID (0 for new draft, filled on decode). */
    val waypointId: Int,
    val type: GeoMarkType,
    /** points[0] = anchor (lat/lon). points[1..N] = additional track points. */
    val points: List<GeoPoint>,
    /** nodeId string, e.g. "!ab12cd34" */
    val authorNodeId: String,
    /** Unix seconds. */
    val createdAt: Long,
    /** Unix seconds. null = no expiry. */
    val expiresAt: Long?,
    /** true if sent by this device. */
    val isSelf: Boolean,
)
