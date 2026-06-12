package ru.tcynik.klitch.data.marker.adapter

import android.util.Base64
import org.meshtastic.proto.Waypoint
import ru.tcynik.klitch.domain.marker.model.GeoMarkModel
import ru.tcynik.klitch.domain.marker.model.GeoMarkType
import ru.tcynik.klitch.domain.marker.model.GeoPoint
import ru.tcynik.klitch.domain.marker.model.TrackEndType
import ru.tcynik.klitch.mesh.model.DataPacket
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

        /** Default expiry TTL in seconds (8 hours). Used when no TTL is specified. */
        const val EXPIRE_TTL_SECONDS = 8 * 3600L

        private const val NAMESPACE = 0x4D
        private const val MT1_PREFIX = "MT1:"
        private const val LAT_LON_SCALE = 1e7
        private const val METERS_PER_DEG_LAT = 111_320.0

        // Transport-level type codes — kept in the adapter, not in the domain enum.
        private val TYPE_CODES = mapOf(GeoMarkType.POINT to 0, GeoMarkType.TRACK to 1)
        private val TYPE_BY_CODE = TYPE_CODES.entries.associate { (k, v) -> v to k }

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

    private fun typeCode(type: GeoMarkType): Int = TYPE_CODES[type] ?: 0
    private fun typeFromCode(code: Int): GeoMarkType? = TYPE_BY_CODE[code]

    /**
     * Encodes a [GeoMarkModel] into a broadcast [DataPacket] (WAYPOINT_APP port).
     *
     * @param mark         The mark to encode. Must pass validation (extra points ≤ MAX_POINTS).
     * @param ourNodeNum   Sender's node number — written to [Waypoint.locked_to].
     * @param ourNodeId    Sender's node id string — written to [GeoMarkModel.authorNodeId].
     * @param nowSeconds   Current Unix time in seconds.
     * @throws IllegalArgumentException if point count exceeds MAX_POINTS.
     *
     * Expiry: uses [GeoMarkModel.expiresAt] if set; falls back to nowSeconds + [EXPIRE_TTL_SECONDS].
     * Channel routing is intentionally excluded — caller (repo) overrides DataPacket.channel
     * based on the selected contour after encoding.
     */
    fun encode(
        mark: GeoMarkModel,
        ourNodeNum: Int,
        ourNodeId: String,
        nowSeconds: Long,
    ): DataPacket {
        require(mark.points.isNotEmpty()) { "mark must have at least one point" }
        if (mark.type == GeoMarkType.TRACK) {
            val extraPoints = mark.points.size - 1
            require(extraPoints <= MAX_POINTS) {
                "Track has $extraPoints extra points; max is $MAX_POINTS"
            }
        }

        val anchor = mark.points.first()
        val icon = buildIcon(mark.type, color = mark.color, variant = 0)
        val description = buildDescription(mark)
        val expireSeconds = mark.expiresAt ?: (nowSeconds + EXPIRE_TTL_SECONDS)

        val waypointId = mark.waypointId.takeIf { it != 0 } ?: waypointIdFromMarkId(mark.id)
        val waypoint = Waypoint(
            id = waypointId,
            latitude_i = (anchor.latitude * LAT_LON_SCALE).roundToInt(),
            longitude_i = (anchor.longitude * LAT_LON_SCALE).roundToInt(),
            expire = expireSeconds.toInt(),
            locked_to = ourNodeNum,
            name = mark.name,
            description = description,
            icon = icon,
        )
        return DataPacket(
            to = DataPacket.ID_BROADCAST,
            channel = 0,
            waypoint = waypoint,
        ).apply {
            // Broadcast waypoints do not get per-hop ACKs; want_ack=true blocks the radio
            // send queue (~5s timeout per packet) and drops rapid consecutive sends.
            wantAck = false
        }
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
        if (!isKlitchWaypoint(waypoint)) return null
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

        val markId = resolveMarkId(waypoint, packet)

        val colorIndex = (waypoint.icon ushr 8) and 0xF
        val endsByte = extractEndsByte(waypoint.description, type)

        return GeoMarkModel(
            id = markId,
            waypointId = waypoint.id,
            type = type,
            points = points,
            authorNodeId = DataPacket.nodeNumToDefaultId(waypoint.locked_to),
            createdAt = packet.time / 1_000,
            expiresAt = waypoint.expire.takeIf { it > 0 }?.toLong(),
            isSelf = markId in selfIds,
            color = colorIndex,
            name = waypoint.name.orEmpty(),
            trackEndType = TrackEndType.fromByte(endsByte),
        )
    }

    // ── Points JSON serialisation ─────────────────────────────────────────────

    fun encodePointsJson(points: List<GeoPoint>): String {
        val items = points.joinToString(",") { pt ->
            """{"lat":${pt.latitude},"lon":${pt.longitude}}"""
        }
        return "[$items]"
    }

    fun decodePointsJson(json: String): List<GeoPoint> {
        return Regex(""""lat":([-\d.E]+).*?"lon":([-\d.E]+)""")
            .findAll(json)
            .mapNotNull { m ->
                val lat = m.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                val lon = m.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
                GeoPoint(lat, lon)
            }.toList()
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun resolveMarkId(waypoint: Waypoint, packet: DataPacket): String {
        if (waypoint.id != 0) return "wp-${waypoint.id}"
        if (packet.id != 0) return "pkt-${packet.id}"
        return buildContentFingerprintId(waypoint)
    }

    /** Stable id for legacy packets with waypoint.id = 0 and mesh packet id not yet assigned. */
    private fun buildContentFingerprintId(waypoint: Waypoint): String {
        val author = DataPacket.nodeNumToDefaultId(waypoint.locked_to)
        return "mt1-$author-${waypoint.latitude_i}-${waypoint.longitude_i}-" +
            "${waypoint.expire}-${waypoint.icon}-${waypoint.name}-${waypoint.description}"
    }

    private fun buildIcon(type: GeoMarkType, color: Int, variant: Int): Int =
        (NAMESPACE shl 24) or (typeCode(type) shl 16) or (color shl 8) or variant

    private fun isKlitchWaypoint(wp: Waypoint): Boolean =
        (wp.icon ushr 24) and 0xFF == NAMESPACE

    private fun typeFromIcon(icon: Int): GeoMarkType? {
        val code = (icon ushr 16) and 0xFF
        return typeFromCode(code)
    }

    /** MT1 description format: "MT1:<base64(payload)>". Point payload is empty. */
    private fun buildDescription(mark: GeoMarkModel): String {
        if (mark.type == GeoMarkType.POINT) return MT1_PREFIX

        val anchor = mark.points.first()
        val maxExtras = (MAX_PAYLOAD_BYTES - 2) / 4
        val extras = mark.points.drop(1).take(maxExtras)
        val buf = ByteBuffer.allocate(2 + extras.size * 4)
        buf.put(extras.size.toByte())       // count: u8
        buf.put(mark.trackEndType.ends)     // ends: u8
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
        buf.get() // ends byte — consumed here; extracted separately via extractEndsByte()
        if (buf.remaining() < count * 4) return null
        val result = mutableListOf(anchor)
        repeat(count) {
            val x = buf.short.toInt()
            val y = buf.short.toInt()
            result.add(fromLocal(anchor, x, y))
        }
        return result
    }

    private fun extractEndsByte(description: String, type: GeoMarkType): Byte {
        if (type != GeoMarkType.TRACK) return 0
        val b64 = description.removePrefix(MT1_PREFIX)
        if (b64.isEmpty()) return 0
        return try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            if (bytes.size >= 2) bytes[1] else 0
        } catch (e: IllegalArgumentException) {
            0
        }
    }

    private fun toLocal(anchor: GeoPoint, point: GeoPoint): Pair<Int, Int> {
        val mpdLon = METERS_PER_DEG_LAT * cos(Math.toRadians(anchor.latitude))
        val x = ((point.longitude - anchor.longitude) * mpdLon).roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        val y = ((point.latitude - anchor.latitude) * METERS_PER_DEG_LAT).roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
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
