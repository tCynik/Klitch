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
    /** Index into GeoMarkColor.palette (0–15). Packed into icon field at transport. */
    val color: Int = 0,
    /** User-visible label. Packed into Waypoint.name at transport. */
    val name: String = "",
    /** Track end marker style. Only relevant for TRACK type. */
    val trackEndType: TrackEndType = TrackEndType.NONE,
    /** Shape of the point marker. */
    val shape: GeoMarkShape = GeoMarkShape.CIRCLE,
)
