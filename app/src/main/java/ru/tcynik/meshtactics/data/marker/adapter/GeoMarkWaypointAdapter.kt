package ru.tcynik.meshtactics.data.marker.adapter

import android.util.Base64
import org.meshtastic.proto.Waypoint
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.mesh.model.DataPacket
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * The only class in the app module that imports org.meshtastic.proto.Waypoint.
 * Encapsulates all encode/decode logic for the MT1 geo-mark transport format.
 */
class GeoMarkWaypointAdapter {

    companion object {
        /** Max additional points beyond the anchor for Track type. */
        const val MAX_POINTS = 27

        /** Max raw payload bytes before base64 encoding (LoRa budget). */
        const val MAX_PAYLOAD_BYTES = 145

        /** Expiry TTL in seconds (8 hours). */
        const val EXPIRE_TTL_SECONDS = 8 * 3600L

        private const val NAMESPACE = 0x4D
        private const val MT1_PREFIX = "MT1:"
        private const val LAT_LON_SCALE = 1e7
        private const val METERS_PER_DEG_LAT = 111_320.0

        // Transport-level type codes — kept in the adapter, not in the domain enum.
        private val TYPE_CODES = mapOf(GeoMarkType.POINT to 0, GeoMarkType.TRACK to 1)
        private val TYPE_BY_CODE = TYPE_CODES.entries.associate { (k, v) -> v to k }
    }

    private fun typeCode(type: GeoMarkType): Int = TYPE_CODES[type] ?: 0
    private fun typeFromCode(code: Int): GeoMarkType? = TYPE_BY_CODE[code]

    /**
     * Encodes a [GeoMarkModel] into a broadcast [DataPacket] (WAYPOINT_APP port).
     *
     * @param mark       The mark to encode. Must pass validation (extra points ≤ MAX_POINTS).
     * @param ourNodeNum Sender's node number — written to [Waypoint.locked_to].
     * @param ourNodeId  Sender's node id string — written to [GeoMarkModel.authorNodeId].
     * @param nowSeconds Current Unix time in seconds — used to compute expiry.
     * @throws IllegalArgumentException if point count exceeds MAX_POINTS.
     */
    fun encode(mark: GeoMarkModel, ourNodeNum: Int, ourNodeId: String, nowSeconds: Long): DataPacket {
        require(mark.points.isNotEmpty()) { "mark must have at least one point" }
        if (mark.type == GeoMarkType.TRACK) {
            val extraPoints = mark.points.size - 1
            require(extraPoints <= MAX_POINTS) {
                "Track has $extraPoints extra points; max is $MAX_POINTS"
            }
        }

        val anchor = mark.points.first()
        val icon = buildIcon(mark.type, color = 0, variant = 0)
        val description = buildDescription(mark)

        val waypoint = Waypoint(
            id = 0,
            latitude_i = (anchor.latitude * LAT_LON_SCALE).roundToInt(),
            longitude_i = (anchor.longitude * LAT_LON_SCALE).roundToInt(),
            expire = (nowSeconds + EXPIRE_TTL_SECONDS).toInt(),
            locked_to = ourNodeNum,
            name = "",
            description = description,
            icon = icon,
        )
        return DataPacket(
            to = DataPacket.ID_BROADCAST,
            channel = 0,
            waypoint = waypoint,
        )
    }

    /**
     * Decodes a [DataPacket] into a [GeoMarkModel].
     * Returns null if the packet is not a valid MT1 waypoint.
     *
     * @param packet  The incoming DataPacket (WAYPOINT_APP port).
     * @param selfIds Set of mark IDs stored as self-sent (from SQLDelight) for isSelf flag.
     */
    fun decode(packet: DataPacket, selfIds: Set<String> = emptySet()): GeoMarkModel? {
        val waypoint = packet.waypoint ?: return null
        if (!isMeshTacticsWaypoint(waypoint)) return null
        if (!waypoint.description.startsWith(MT1_PREFIX)) return null

        val type = typeFromIcon(waypoint.icon) ?: return null
        val anchorLatI = waypoint.latitude_i ?: return null
        val anchorLonI = waypoint.longitude_i ?: return null
        val anchor = GeoPoint(
            latitude = anchorLatI / LAT_LON_SCALE,
            longitude = anchorLonI / LAT_LON_SCALE,
        )

        val points = when (type) {
            GeoMarkType.POINT -> listOf(anchor)
            GeoMarkType.TRACK -> decodeTrackPoints(anchor, waypoint.description) ?: return null
        }

        // Use waypointId as part of stable ID to avoid UUID churn on repeated emissions
        val markId = if (waypoint.id != 0) {
            "wp-${waypoint.id}"
        } else {
            UUID.randomUUID().toString()
        }

        return GeoMarkModel(
            id = markId,
            waypointId = waypoint.id,
            type = type,
            points = points,
            authorNodeId = DataPacket.nodeNumToDefaultId(waypoint.locked_to),
            createdAt = packet.time / 1_000,
            expiresAt = waypoint.expire.takeIf { it > 0 }?.toLong(),
            isSelf = markId in selfIds,
        )
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun buildIcon(type: GeoMarkType, color: Int, variant: Int): Int =
        (NAMESPACE shl 24) or (typeCode(type) shl 16) or (color shl 8) or variant

    private fun isMeshTacticsWaypoint(wp: Waypoint): Boolean =
        (wp.icon ushr 24) and 0xFF == NAMESPACE

    private fun typeFromIcon(icon: Int): GeoMarkType? {
        val code = (icon ushr 16) and 0xFF
        return typeFromCode(code)
    }

    /** MT1 description format: "MT1:<base64(payload)>". Point payload is empty. */
    private fun buildDescription(mark: GeoMarkModel): String {
        if (mark.type == GeoMarkType.POINT) return MT1_PREFIX

        val anchor = mark.points.first()
        val extras = mark.points.drop(1)
        val buf = ByteBuffer.allocate(2 + extras.size * 4)
        buf.put(extras.size.toByte())  // count: u8
        buf.put(0)                     // ends: u8 (reserved for MVP)
        for (pt in extras) {
            val (x, y) = toLocal(anchor, pt)
            buf.putShort(x.toShort())
            buf.putShort(y.toShort())
        }
        val encoded = Base64.encodeToString(buf.array(), Base64.NO_WRAP)
        return "$MT1_PREFIX$encoded"
    }

    private fun decodeTrackPoints(anchor: GeoPoint, description: String): List<GeoPoint>? {
        val b64 = description.removePrefix(MT1_PREFIX)
        if (b64.isEmpty()) return listOf(anchor)
        val bytes = try {
            Base64.decode(b64, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (bytes.size < 2) return null
        val buf = ByteBuffer.wrap(bytes)
        val count = buf.get().toInt() and 0xFF
        buf.get() // skip ends
        if (buf.remaining() < count * 4) return null
        val result = mutableListOf(anchor)
        repeat(count) {
            val x = buf.short.toInt()
            val y = buf.short.toInt()
            result.add(fromLocal(anchor, x, y))
        }
        return result
    }

    private fun toLocal(anchor: GeoPoint, point: GeoPoint): Pair<Int, Int> {
        val mpdLon = METERS_PER_DEG_LAT * cos(Math.toRadians(anchor.latitude))
        val x = ((point.longitude - anchor.longitude) * mpdLon).roundToInt()
        val y = ((point.latitude - anchor.latitude) * METERS_PER_DEG_LAT).roundToInt()
        return x to y
    }

    private fun fromLocal(anchor: GeoPoint, x: Int, y: Int): GeoPoint {
        val mpdLon = METERS_PER_DEG_LAT * cos(Math.toRadians(anchor.latitude))
        return GeoPoint(
            latitude = anchor.latitude + y / METERS_PER_DEG_LAT,
            longitude = anchor.longitude + x / mpdLon,
        )
    }
}
