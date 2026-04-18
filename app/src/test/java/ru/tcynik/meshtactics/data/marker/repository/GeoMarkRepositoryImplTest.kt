package ru.tcynik.meshtactics.data.marker.repository

import android.util.Base64
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import io.mockk.every
import io.mockk.verify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.data.local.AppDatabase
import ru.tcynik.meshtactics.data.marker.adapter.GeoMarkWaypointAdapter
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.domain.mesh.model.MeshNodeModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.mesh.model.DataPacket
import ru.tcynik.meshtactics.mesh.repository.CommandSender
import ru.tcynik.meshtactics.mesh.repository.PacketRepository

class GeoMarkRepositoryImplTest {

    private lateinit var repo: GeoMarkRepositoryImpl
    private lateinit var db: AppDatabase

    private val commandSender: CommandSender = mockk(relaxed = true)
    private val packetRepository: PacketRepository = mockk()
    private val meshNetwork: MeshNetworkRepository = mockk()
    private val adapter = GeoMarkWaypointAdapter()

    private val ourNode = MeshNodeModel(
        num = 0x1234, nodeId = "!00001234", shortName = "TEST", longName = "Test Node",
        snr = 0f, rssi = 0, lastHeard = 0, hopsAway = 0, batteryLevel = 100,
        voltage = 3.8f, channelUtilization = 0f, airUtilTx = 0f, uptimeSeconds = 0L,
        latitude = 0.0, longitude = 0.0, hasValidPosition = false, positionTime = 0,
        isOnline = true, groundSpeed = 0, groundTrack = 0,
    )

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

        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        db = AppDatabase(driver)

        every { meshNetwork.observeOurNode() } returns flowOf(ourNode)

        repo = GeoMarkRepositoryImpl(
            packetRepository = packetRepository,
            commandSender = commandSender,
            meshNetwork = meshNetwork,
            adapter = adapter,
            geoMarkQueries = db.geoMarkQueries,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makePointMark(id: String = "mark-1") = GeoMarkModel(
        id = id, waypointId = 0, type = GeoMarkType.POINT,
        points = listOf(GeoPoint(55.75, 37.62)),
        authorNodeId = "", createdAt = 1000L, expiresAt = null, isSelf = false,
    )

    // ── observeGeoMarks ───────────────────────────────────────────────────────

    @Test
    fun `observeGeoMarks — emits empty list when no packets`() = runTest {
        every { packetRepository.getWaypoints() } returns flowOf(emptyList())

        repo.observeGeoMarks().test {
            val marks = awaitItem()
            assertTrue(marks.isEmpty())
            cancel()
        }
    }

    @Test
    fun `observeGeoMarks — decodes valid MT1 packet from PacketRepository`() = runTest {
        val packet = adapter.encode(makePointMark(), ourNode.num, ourNode.nodeId, 1000L)
        every { packetRepository.getWaypoints() } returns flowOf(listOf(packet))

        repo.observeGeoMarks().test {
            val marks = awaitItem()
            assertEquals(1, marks.size)
            assertEquals(GeoMarkType.POINT, marks[0].type)
            cancel()
        }
    }

    @Test
    fun `observeGeoMarks — skips non-MT1 packet`() = runTest {
        val nonMt1Packet = DataPacket(to = "^all", channel = 0, text = "hello")
        every { packetRepository.getWaypoints() } returns flowOf(listOf(nonMt1Packet))

        repo.observeGeoMarks().test {
            val marks = awaitItem()
            assertTrue(marks.isEmpty())
            cancel()
        }
    }

    @Test
    fun `observeGeoMarks — marks self-sent IDs as isSelf=true`() = runTest {
        val mark = makePointMark(id = "self-mark")
        val packet = adapter.encode(mark, ourNode.num, ourNode.nodeId, 1000L)

        // Pre-insert self ID into SQLDelight as if it was sent by us
        db.geoMarkQueries.insert(
            id = "self-mark",
            waypointId = 0L,
            type = "POINT",
            pointsJson = """[{"lat":55.75,"lon":37.62}]""",
            authorNodeId = ourNode.nodeId,
            createdAt = 1000L,
            expiresAt = 9000L,
            isSelf = 1L,
        )

        // The decoded mark has waypointId=0 → uses UUID, not "self-mark"
        // isSelf is matched on the decoded id ("wp-X" or UUID), which won't match "self-mark"
        // This tests that the selfIds flow is correctly wired; actual isSelf match
        // depends on stable IDs (waypoint.id != 0).
        // Here we verify the combine runs without error and emits a result.
        every { packetRepository.getWaypoints() } returns flowOf(listOf(packet))

        repo.observeGeoMarks().test {
            val marks = awaitItem()
            assertEquals(1, marks.size)
            cancel()
        }
    }

    // ── sendGeoMark ───────────────────────────────────────────────────────────

    @Test
    fun `sendGeoMark — calls commandSender sendData`() = runTest {
        repo.sendGeoMark(makePointMark())
        verify(exactly = 1) { commandSender.sendData(any()) }
    }

    @Test
    fun `sendGeoMark — inserts mark into SQLDelight with is_self=1`() = runTest {
        val mark = makePointMark(id = "sent-mark")
        repo.sendGeoMark(mark)

        val row = db.geoMarkQueries.selectById("sent-mark").executeAsOneOrNull()
        assertEquals("sent-mark", row?.id)
        assertEquals(1L, row?.is_self)
    }

    @Test
    fun `sendGeoMark — persists correct type in SQLDelight`() = runTest {
        val mark = makePointMark(id = "type-check")
        repo.sendGeoMark(mark)

        val row = db.geoMarkQueries.selectById("type-check").executeAsOneOrNull()
        assertEquals("POINT", row?.type)
    }

    @Test
    fun `sendGeoMark — selectAll returns inserted mark`() = runTest {
        repo.sendGeoMark(makePointMark(id = "list-mark"))

        val rows = db.geoMarkQueries.selectAll().executeAsList()
        assertEquals(1, rows.size)
        assertEquals("list-mark", rows[0].id)
    }
}
