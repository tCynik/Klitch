package ru.tcynik.meshtactics.data.marker.adapter

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meshtastic.proto.Waypoint
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.mesh.model.DataPacket

class GeoMarkWaypointAdapterTest {

    private val adapter = GeoMarkWaypointAdapter()

    @Before
    fun setUp() {
        // Redirect android.util.Base64 to java.util.Base64 for JVM tests
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun pointMark() = GeoMarkModel(
        id = "test-id", waypointId = 0, type = GeoMarkType.POINT,
        points = listOf(GeoPoint(55.7500000, 37.6200000)),
        authorNodeId = "", createdAt = 1000L, expiresAt = null, isSelf = false,
    )

    private fun trackMark(extraCount: Int): GeoMarkModel {
        val anchor = GeoPoint(55.750, 37.620)
        val extras = List(extraCount) { i -> GeoPoint(55.750 + i * 0.001, 37.620 + i * 0.001) }
        return GeoMarkModel(
            id = "track-id", waypointId = 0, type = GeoMarkType.TRACK,
            points = listOf(anchor) + extras,
            authorNodeId = "", createdAt = 1000L, expiresAt = null, isSelf = false,
        )
    }

    // ── encode: icon ──────────────────────────────────────────────────────────

    @Test
    fun `encode point — namespace byte in icon is 0x4D`() {
        val packet = adapter.encode(pointMark(), 0, "", 1000L)
        val icon = packet.waypoint!!.icon
        assertEquals(0x4D, (icon ushr 24) and 0xFF)
    }

    @Test
    fun `encode point — type byte in icon is 0 (POINT typeCode)`() {
        val packet = adapter.encode(pointMark(), 0, "", 1000L)
        val icon = packet.waypoint!!.icon
        assertEquals(0, (icon ushr 16) and 0xFF)
    }

    @Test
    fun `encode track — type byte in icon is 1 (TRACK typeCode)`() {
        val packet = adapter.encode(trackMark(2), 0, "", 1000L)
        val icon = packet.waypoint!!.icon
        assertEquals(1, (icon ushr 16) and 0xFF)
    }

    // ── encode: coordinates ───────────────────────────────────────────────────

    @Test
    fun `encode point — anchor latitude preserved in waypoint`() {
        val anchor = GeoPoint(55.7500000, 37.6200000)
        val packet = adapter.encode(pointMark().copy(points = listOf(anchor)), 0, "", 1000L)
        val wp = packet.waypoint!!
        assertEquals(anchor.latitude, wp.latitude_i!! / 1e7, 1e-6)
    }

    @Test
    fun `encode point — anchor longitude preserved in waypoint`() {
        val anchor = GeoPoint(55.7500000, 37.6200000)
        val packet = adapter.encode(pointMark().copy(points = listOf(anchor)), 0, "", 1000L)
        val wp = packet.waypoint!!
        assertEquals(anchor.longitude, wp.longitude_i!! / 1e7, 1e-6)
    }

    // ── encode: expiry ────────────────────────────────────────────────────────

    @Test
    fun `encode — expiry equals nowSeconds plus EXPIRE_TTL_SECONDS`() {
        val now = 10_000L
        val packet = adapter.encode(pointMark(), 0, "", now)
        val expected = (now + GeoMarkWaypointAdapter.EXPIRE_TTL_SECONDS).toInt()
        assertEquals(expected, packet.waypoint!!.expire)
    }

    // ── encode: description ───────────────────────────────────────────────────

    @Test
    fun `encode point — description is exactly MT1 prefix`() {
        val packet = adapter.encode(pointMark(), 0, "", 1000L)
        assertEquals("MT1:", packet.waypoint!!.description)
    }

    @Test
    fun `encode track — description starts with MT1 prefix and has payload`() {
        val packet = adapter.encode(trackMark(3), 0, "", 1000L)
        val desc = packet.waypoint!!.description
        assertTrue("Expected MT1: prefix, got: $desc", desc.startsWith("MT1:"))
        assertTrue("Expected payload after prefix", desc.length > "MT1:".length)
    }

    // ── encode: validation ────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `encode — throws when points list is empty`() {
        adapter.encode(pointMark().copy(points = emptyList()), 0, "", 1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encode track — throws when extra points exceed MAX_POINTS`() {
        adapter.encode(trackMark(GeoMarkWaypointAdapter.MAX_POINTS + 1), 0, "", 1000L)
    }

    @Test
    fun `encode track — MAX_POINTS extra points is accepted`() {
        // Should not throw
        val packet = adapter.encode(trackMark(GeoMarkWaypointAdapter.MAX_POINTS), 0, "", 1000L)
        assertNotNull(packet.waypoint)
    }

    // ── decode: null guards ───────────────────────────────────────────────────

    @Test
    fun `decode — returns null when packet has no waypoint`() {
        val packet = DataPacket(to = "^all", channel = 0, text = "hello")
        assertNull(adapter.decode(packet))
    }

    @Test
    fun `decode — returns null when namespace byte is not 0x4D`() {
        val originalPacket = adapter.encode(pointMark(), 0, "", 1000L)
        val wp = originalPacket.waypoint!!
        val tamperedWp = Waypoint(
            id = wp.id, latitude_i = wp.latitude_i, longitude_i = wp.longitude_i,
            expire = wp.expire, locked_to = wp.locked_to,
            name = wp.name, description = wp.description,
            icon = 0xAB000000.toInt(), // wrong namespace
        )
        val tamperedPacket = DataPacket(to = "^all", channel = 0, waypoint = tamperedWp)
        assertNull(adapter.decode(tamperedPacket))
    }

    @Test
    fun `decode — returns null when description does not start with MT1 prefix`() {
        val originalPacket = adapter.encode(pointMark(), 0, "", 1000L)
        val wp = originalPacket.waypoint!!
        val tamperedWp = Waypoint(
            id = wp.id, latitude_i = wp.latitude_i, longitude_i = wp.longitude_i,
            expire = wp.expire, locked_to = wp.locked_to,
            name = wp.name, description = "OTHER:data",
            icon = wp.icon,
        )
        val tamperedPacket = DataPacket(to = "^all", channel = 0, waypoint = tamperedWp)
        assertNull(adapter.decode(tamperedPacket))
    }

    // ── roundtrip: POINT ──────────────────────────────────────────────────────

    @Test
    fun `roundtrip POINT — type preserved`() {
        val packet = adapter.encode(pointMark(), 0, "", 1000L)
        val decoded = adapter.decode(packet)!!
        assertEquals(GeoMarkType.POINT, decoded.type)
    }

    @Test
    fun `roundtrip POINT — exactly one point`() {
        val packet = adapter.encode(pointMark(), 0, "", 1000L)
        val decoded = adapter.decode(packet)!!
        assertEquals(1, decoded.points.size)
    }

    @Test
    fun `roundtrip POINT — anchor latitude within 1e-6 degrees`() {
        val anchor = GeoPoint(55.7500000, 37.6200000)
        val packet = adapter.encode(pointMark().copy(points = listOf(anchor)), 0, "", 1000L)
        val decoded = adapter.decode(packet)!!
        assertEquals(anchor.latitude, decoded.points[0].latitude, 1e-6)
    }

    @Test
    fun `roundtrip POINT — anchor longitude within 1e-6 degrees`() {
        val anchor = GeoPoint(55.7500000, 37.6200000)
        val packet = adapter.encode(pointMark().copy(points = listOf(anchor)), 0, "", 1000L)
        val decoded = adapter.decode(packet)!!
        assertEquals(anchor.longitude, decoded.points[0].longitude, 1e-6)
    }

    // ── roundtrip: TRACK ──────────────────────────────────────────────────────

    @Test
    fun `roundtrip TRACK — type preserved`() {
        val packet = adapter.encode(trackMark(3), 0, "", 1000L)
        val decoded = adapter.decode(packet)!!
        assertEquals(GeoMarkType.TRACK, decoded.type)
    }

    @Test
    fun `roundtrip TRACK — point count preserved (anchor + extras)`() {
        val extraCount = 5
        val packet = adapter.encode(trackMark(extraCount), 0, "", 1000L)
        val decoded = adapter.decode(packet)!!
        assertEquals(extraCount + 1, decoded.points.size)
    }

    @Test
    fun `roundtrip TRACK — anchor preserved within 1e-6 degrees`() {
        val anchor = GeoPoint(55.750, 37.620)
        val extras = listOf(GeoPoint(55.751, 37.621), GeoPoint(55.752, 37.622))
        val mark = trackMark(2).copy(points = listOf(anchor) + extras)
        val packet = adapter.encode(mark, 0, "", 1000L)
        val decoded = adapter.decode(packet)!!
        assertEquals(anchor.latitude,  decoded.points[0].latitude,  1e-6)
        assertEquals(anchor.longitude, decoded.points[0].longitude, 1e-6)
    }

    @Test
    fun `roundtrip TRACK — extra points within 2 metre tolerance`() {
        // int16 metre quantization introduces up to ~1m error
        val anchor = GeoPoint(55.750, 37.620)
        val extras = listOf(
            GeoPoint(55.751, 37.621),
            GeoPoint(55.752, 37.622),
            GeoPoint(55.753, 37.623),
        )
        val mark = trackMark(3).copy(points = listOf(anchor) + extras)
        val packet = adapter.encode(mark, 0, "", 1000L)
        val decoded = adapter.decode(packet)!!
        for (i in extras.indices) {
            assertEquals("lat[$i]", extras[i].latitude,  decoded.points[i + 1].latitude,  2e-5)
            assertEquals("lon[$i]", extras[i].longitude, decoded.points[i + 1].longitude, 2e-5)
        }
    }

    @Test
    fun `roundtrip TRACK — MAX_POINTS extras survive encode-decode`() {
        val packet = adapter.encode(trackMark(GeoMarkWaypointAdapter.MAX_POINTS), 0, "", 1000L)
        val decoded = adapter.decode(packet)!!
        assertEquals(GeoMarkWaypointAdapter.MAX_POINTS + 1, decoded.points.size)
    }

    // ── isSelf ────────────────────────────────────────────────────────────────

    @Test
    fun `decode — isSelf is false when mark id not in selfIds`() {
        val packet = adapter.encode(pointMark(), 0, "", 1000L)
        val decoded = adapter.decode(packet, selfIds = emptySet())!!
        assertFalse(decoded.isSelf)
    }

    @Test
    fun `encode — assigns non-zero waypoint id from mark id`() {
        val mark = pointMark().copy(id = "550e8400-e29b-41d4-a716-446655440000")
        val packet = adapter.encode(mark, 0, "", 1000L)
        assertTrue(packet.waypoint!!.id != 0)
    }

    @Test
    fun `encode — different mark ids produce different waypoint ids`() {
        val id1 = "550e8400-e29b-41d4-a716-446655440000"
        val id2 = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
        val wp1 = adapter.encode(pointMark().copy(id = id1), 0, "", 1000L).waypoint!!.id
        val wp2 = adapter.encode(pointMark().copy(id = id2), 0, "", 1000L).waypoint!!.id
        assertNotEquals(wp1, wp2)
    }

    @Test
    fun `decode — same packet yields stable mark id on repeated decode`() {
        val mark = pointMark().copy(id = "550e8400-e29b-41d4-a716-446655440000")
        val packet = adapter.encode(mark, 0, "", 1000L).copy(id = 42)
        val first = adapter.decode(packet)!!
        val second = adapter.decode(packet)!!
        assertEquals(first.id, second.id)
        assertEquals("wp-${packet.waypoint!!.id}", first.id)
    }
}
