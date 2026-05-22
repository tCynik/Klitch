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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.data.local.AppDatabase
import ru.tcynik.meshtactics.data.marker.adapter.GeoMarkWaypointAdapter
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.ChannelSlotMaps
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import kotlinx.coroutines.flow.MutableStateFlow
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.domain.mesh.model.MeshNodeModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.mesh.repository.CommandSender

class GeoMarkRepositoryImplTest {

    private lateinit var repo: GeoMarkRepositoryImpl
    private lateinit var db: AppDatabase

    private val commandSender: CommandSender = mockk(relaxed = true)
    private val meshNetwork: MeshNetworkRepository = mockk()
    private val channelRepository: ContourRepository = mockk()
    private val channelSlotResolver: ChannelSlotResolver = mockk {
        every { slotToHash } returns emptyMap()
        every { hashToSlot } returns emptyMap()
        every { mapsFlow } returns MutableStateFlow(ChannelSlotMaps())
    }
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
        every { channelRepository.observeContours() } returns flowOf(emptyList())

        repo = GeoMarkRepositoryImpl(
            commandSender      = commandSender,
            meshNetwork        = meshNetwork,
            channelRepository  = channelRepository,
            channelSlotResolver = channelSlotResolver,
            adapter            = adapter,
            geoMarkQueries     = db.geoMarkQueries,
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
    fun `observeGeoMarks — emits empty list when no marks in SQLDelight`() = runTest {
        repo.observeGeoMarks().test {
            val marks = awaitItem()
            assertTrue(marks.isEmpty())
            cancel()
        }
    }

    @Test
    fun `observeGeoMarks — emits mark after persistReceived`() = runTest {
        repo.observeGeoMarks().test {
            awaitItem() // initial empty

            repo.persistReceived(makePointMark("rcv-1"), ContourId("ch-1"))

            val marks = awaitItem()
            assertEquals(1, marks.size)
            assertEquals("rcv-1", marks[0].id)
            assertEquals(GeoMarkType.POINT, marks[0].type)
            cancel()
        }
    }

    @Test
    fun `observeGeoMarks — local send has empty logicalChannelId`() = runTest {
        val mark = makePointMark(id = "local-only")
        repo.sendGeoMark(mark, contourId = null, localOnly = true)

        repo.observeGeoMarks().test {
            val item = awaitItem().single()
            assertEquals("", item.logicalChannelId)
            assertEquals(true, item.isSelf)
            cancel()
        }
    }

    @Test
    fun `observeGeoMarks — network send sets authorNodeId`() = runTest {
        repo.sendGeoMark(makePointMark(id = "sent-net"))

        repo.observeGeoMarks().test {
            awaitItem()
            val item = awaitItem().single { it.id == "sent-net" }
            assertEquals(true, item.isSelf)
            assertEquals("!00001234", item.authorNodeId)
            cancel()
        }
    }

    @Test
    fun `observeGeoMarks — isSelf=false for persistReceived`() = runTest {
        repo.persistReceived(makePointMark("rcv-self"), ContourId("ch-1"))

        repo.observeGeoMarks().test {
            val marks = awaitItem()
            assertEquals(false, marks[0].isSelf)
            cancel()
        }
    }

    // ── persistReceived ───────────────────────────────────────────────────────

    @Test
    fun `persistReceived — INSERT OR IGNORE does not overwrite existing mark`() = runTest {
        val mark = makePointMark("dup-mark")
        repo.persistReceived(mark, ContourId("ch-1"))
        repo.persistReceived(mark.copy(authorNodeId = "other"), ContourId("ch-2"))

        val rows = db.geoMarkQueries.selectAll().executeAsList()
        assertEquals(1, rows.size)
        assertEquals("", rows[0].author_node_id) // first insert wins
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

    // ── deleteExpired ─────────────────────────────────────────────────────────

    @Test
    fun `toggleVisibility — updates is_visible and observeGeoMarks reflects change`() = runTest {
        repo.persistReceived(makePointMark("vis-1"), ContourId("ch-1"))

        repo.observeGeoMarks().test {
            assertTrue(awaitItem().single().isVisible)

            repo.toggleVisibility("vis-1", false)

            assertFalse(awaitItem().single().isVisible)

            repo.toggleVisibility("vis-1", true)

            assertTrue(awaitItem().single().isVisible)
            cancel()
        }
    }

    @Test
    fun `deleteExpired — removes expired marks`() = runTest {
        db.geoMarkQueries.insert(
            id = "expired", waypointId = 0L, type = "POINT",
            pointsJson = """[{"lat":0.0,"lon":0.0}]""",
            authorNodeId = "", createdAt = 100L, expiresAt = 500L, isSelf = 0L,
            logicalChannelId = "ch-1",
            color = 0L, name = "", trackEndType = 0L, shape = 0L,
        )
        db.geoMarkQueries.insert(
            id = "live", waypointId = 0L, type = "POINT",
            pointsJson = """[{"lat":0.0,"lon":0.0}]""",
            authorNodeId = "", createdAt = 100L, expiresAt = null, isSelf = 0L,
            logicalChannelId = "ch-1",
            color = 0L, name = "", trackEndType = 0L, shape = 0L,
        )

        repo.deleteExpired(nowSeconds = 1000L)

        val rows = db.geoMarkQueries.selectAll().executeAsList()
        assertEquals(1, rows.size)
        assertEquals("live", rows[0].id)
    }
}
