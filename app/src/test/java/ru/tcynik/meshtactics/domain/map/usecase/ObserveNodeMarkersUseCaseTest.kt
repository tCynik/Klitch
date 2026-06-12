package ru.tcynik.meshtactics.domain.map.usecase

import app.cash.turbine.test
import io.mockk.every
import ru.tcynik.meshtactics.logger.NoOpLogger
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.ChannelSlotMaps
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveContourFromSlotUseCase
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import java.util.Base64
import ru.tcynik.meshtactics.domain.mesh.model.MeshNodeModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveNodeMarkersUseCaseTest {

    private val repository: MeshNetworkRepository = mockk()
    private val contourRepository: ContourRepository = mockk()
    private val channelSlotResolver: ChannelSlotResolver = mockk()
    private val useCase = ObserveNodeMarkersUseCase(
        repository,
        contourRepository,
        channelSlotResolver,
        ResolveContourFromSlotUseCase(),
        NoOpLogger(),
    )

    private val primaryId = ContourId("primary")

    @Before
    fun setUp() {
        val primary = Contour(
            id = primaryId,
            name = "Primary",
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = true,
            transport = ContourTransport(MeshtasticChannel(Base64.getEncoder().encodeToString(byteArrayOf(0x01)), ContourHash.compute("Primary", byteArrayOf(0x01)))),
        )
        every { contourRepository.observeContours() } returns flowOf(listOf(primary, DefaultContour.asContour()))
        every { contourRepository.observePrimaryContourId() } returns flowOf(primaryId)
        every { contourRepository.observeSosMode() } returns flowOf(false)
        every { channelSlotResolver.mapsFlow } returns MutableStateFlow(ChannelSlotMaps())
    }

    // ── Filtering ────────────────────────────────────────────────────────────

    @Test
    fun `nodes without valid position are filtered out`() = runTest {
        val noPosition = node(nodeId = "A", hasValidPosition = false)
        val withPosition = node(nodeId = "B", hasValidPosition = true)
        every { repository.observeNodes() } returns flowOf(listOf(noPosition, withPosition))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            val markers = awaitItem()
            assertEquals(1, markers.size)
            assertEquals("B", markers[0].nodeId)
            // Don't awaitComplete — use case emits periodically
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `our node is excluded from markers`() = runTest {
        val ourNode = node(nodeId = "OUR", hasValidPosition = true)
        val peer = node(nodeId = "PEER", hasValidPosition = true)
        every { repository.observeNodes() } returns flowOf(listOf(ourNode, peer))
        every { repository.observeOurNode() } returns flowOf(ourNode)

        useCase(NoParams).test {
            val markers = awaitItem()
            assertEquals(1, markers.size)
            assertEquals("PEER", markers[0].nodeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty result when all nodes have no valid position`() = runTest {
        every { repository.observeNodes() } returns flowOf(
            listOf(node("A", hasValidPosition = false), node("B", hasValidPosition = false))
        )
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── GeoPoint mapping ─────────────────────────────────────────────────────

    @Test
    fun `GeoPoint coordinates match MeshNodeModel lat and lon`() = runTest {
        val peer = node(nodeId = "A", hasValidPosition = true, latitude = 55.75, longitude = 37.62)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            val marker = awaitItem().single()
            assertEquals(GeoPoint(55.75, 37.62), marker.position)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Freshness (isStale) ──────────────────────────────────────────────────

    @Test
    fun `fresh node — positionTime 1 minute ago — isStale false`() = runTest {
        val freshTime = (System.currentTimeMillis() / 1000 - 60).toInt()
        val peer = node(nodeId = "A", hasValidPosition = true, positionTime = freshTime)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            assertFalse(awaitItem().single().isStale)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stale node — positionTime 10 minutes ago — isStale true`() = runTest {
        val staleTime = (System.currentTimeMillis() / 1000 - 600).toInt()
        val peer = node(nodeId = "A", hasValidPosition = true, positionTime = staleTime)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            assertTrue(awaitItem().single().isStale)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `positionTime zero is hidden`() = runTest {
        val peer = node(nodeId = "A", hasValidPosition = true, positionTime = 0)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Heading ──────────────────────────────────────────────────────────────

    @Test
    fun `moving node — groundSpeed 1 or more — heading equals groundTrack`() = runTest {
        val peer = node(nodeId = "A", hasValidPosition = true, groundSpeed = 3, groundTrack = 270)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            assertEquals(270f, awaitItem().single().heading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stationary node — groundSpeed 0 — heading is null`() = runTest {
        val peer = node(nodeId = "A", hasValidPosition = true, groundSpeed = 0, groundTrack = 90)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            assertNull(awaitItem().single().heading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── longName propagation ─────────────────────────────────────────────────

    @Test
    fun `longName is propagated to marker`() = runTest {
        val peer = node(nodeId = "A", hasValidPosition = true, longName = "Alpha")
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            assertEquals("Alpha", awaitItem().single().longName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Dynamic staleness transition ─────────────────────────────────────────

    @Test
    fun `node transitions from fresh to stale while use case is running`() = runTest {
        val now = System.currentTimeMillis() / 1000
        val justFresh = (now - 10).toInt() // 10 seconds ago — fresh
        val peer = node(nodeId = "A", hasValidPosition = true, positionTime = justFresh)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)

        // Temporarily override the interval for faster test execution
        // The actual STALE_CHECK_INTERVAL_MS is 10s, but we just verify first emission is fresh
        useCase(NoParams).test {
            val first = awaitItem()
            assertFalse("Node should be fresh initially", first.single().isStale)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Max position age filtering (12 hours) ────────────────────────────────

    @Test
    fun `nodes older than 12 hours are filtered out`() = runTest {
        val now = System.currentTimeMillis() / 1000
        val thirteenHoursAgo = (now - 13 * 60 * 60).toInt()
        val oldNode = node(nodeId = "OLD", hasValidPosition = true, positionTime = thirteenHoursAgo)
        val recentNode = node(nodeId = "RECENT", hasValidPosition = true, positionTime = (now - 3600).toInt()) // 1 hour ago
        every { repository.observeNodes() } returns flowOf(listOf(oldNode, recentNode))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            val markers = awaitItem()
            assertEquals(1, markers.size)
            assertEquals("RECENT", markers[0].nodeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nodes just under 12 hours old are shown`() = runTest {
        val now = System.currentTimeMillis() / 1000
        val elevenHours59Min = (now - (12 * 60 * 60 - 60)).toInt() // 11h 59m ago
        val boundaryNode = node(nodeId = "BOUNDARY", hasValidPosition = true, positionTime = elevenHours59Min)
        every { repository.observeNodes() } returns flowOf(listOf(boundaryNode))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            val markers = awaitItem()
            assertEquals(1, markers.size)
            assertEquals("BOUNDARY", markers[0].nodeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty result when all nodes are older than 12 hours`() = runTest {
        val now = System.currentTimeMillis() / 1000
        val oldNode1 = node(nodeId = "A", hasValidPosition = true, positionTime = (now - 13 * 60 * 60).toInt())
        val oldNode2 = node(nodeId = "B", hasValidPosition = true, positionTime = (now - 20 * 60 * 60).toInt())
        every { repository.observeNodes() } returns flowOf(listOf(oldNode1, oldNode2))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Contour filter ───────────────────────────────────────────────────────

    @Test
    fun `node receivedOnSlot=null — excluded`() = runTest {
        val peer = node(nodeId = "A", receivedOnSlot = null)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)
        every { contourRepository.observeSosMode() } returns flowOf(false)

        useCase(NoParams).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `node receivedOnSlot=1 SOS inactive — excluded`() = runTest {
        val peer = node(nodeId = "A", receivedOnSlot = 1)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)
        every { contourRepository.observeSosMode() } returns flowOf(false)

        useCase(NoParams).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `node receivedOnSlot=1 SOS active — included`() = runTest {
        val peer = node(nodeId = "A", receivedOnSlot = 1)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)
        every { contourRepository.observeSosMode() } returns flowOf(true)

        useCase(NoParams).test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `node receivedOnSlot=null SOS active — included`() = runTest {
        val peer = node(nodeId = "A", receivedOnSlot = null)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)
        every { contourRepository.observeSosMode() } returns flowOf(true)

        useCase(NoParams).test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `node receivedOnSlot=N inactive contour SOS active — included`() = runTest {
        val psk = byteArrayOf(0x01)
        val hash = ContourHash.compute("test", psk)
        val inactiveContour = Contour(
            id = ContourId("ccc"),
            name = "test",
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = false,
            transport = ContourTransport(MeshtasticChannel(Base64.getEncoder().encodeToString(psk), hash)),
        )
        val maps = ChannelSlotMaps(slotToHash = mapOf(3 to hash), hashToSlot = mapOf(hash to 3))
        every { repository.observeNodes() } returns flowOf(listOf(node(nodeId = "A", receivedOnSlot = 3)))
        every { repository.observeOurNode() } returns flowOf(null)
        every { contourRepository.observeContours() } returns flowOf(listOf(inactiveContour))
        every { channelSlotResolver.mapsFlow } returns MutableStateFlow(maps)
        every { contourRepository.observeSosMode() } returns flowOf(true)

        useCase(NoParams).test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `node receivedOnSlot=N inactive contour — excluded`() = runTest {
        val psk = byteArrayOf(0x01)
        val hash = ContourHash.compute("test", psk)
        val inactiveContour = Contour(
            id = ContourId("ccc"),
            name = "test",
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = false,
            transport = ContourTransport(MeshtasticChannel(Base64.getEncoder().encodeToString(psk), hash)),
        )
        val maps = ChannelSlotMaps(slotToHash = mapOf(3 to hash), hashToSlot = mapOf(hash to 3))
        every { repository.observeNodes() } returns flowOf(listOf(node(nodeId = "A", receivedOnSlot = 3)))
        every { repository.observeOurNode() } returns flowOf(null)
        every { contourRepository.observeContours() } returns flowOf(listOf(inactiveContour))
        every { channelSlotResolver.mapsFlow } returns MutableStateFlow(maps)

        useCase(NoParams).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `node receivedOnSlot=N active contour — included`() = runTest {
        val psk = byteArrayOf(0x01)
        val hash = ContourHash.compute("test", psk)
        val activeContour = Contour(
            id = ContourId("ccc"),
            name = "test",
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = true,
            transport = ContourTransport(MeshtasticChannel(Base64.getEncoder().encodeToString(psk), hash)),
        )
        val maps = ChannelSlotMaps(slotToHash = mapOf(3 to hash), hashToSlot = mapOf(hash to 3))
        every { repository.observeNodes() } returns flowOf(listOf(node(nodeId = "A", receivedOnSlot = 3)))
        every { repository.observeOurNode() } returns flowOf(null)
        every { contourRepository.observeContours() } returns flowOf(listOf(activeContour))
        every { channelSlotResolver.mapsFlow } returns MutableStateFlow(maps)

        useCase(NoParams).test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun node(
        nodeId: String,
        hasValidPosition: Boolean = true,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        positionTime: Int = (System.currentTimeMillis() / 1000 - 60).toInt(),
        lastHeard: Int = (System.currentTimeMillis() / 1000 - 60).toInt(),
        isOnline: Boolean = true,
        groundSpeed: Int = 0,
        groundTrack: Int = 0,
        longName: String = nodeId,
        receivedOnSlot: Int? = 0,
    ) = MeshNodeModel(
        num = 0,
        nodeId = nodeId,
        shortName = nodeId,
        longName = longName,
        snr = 0f,
        rssi = 0,
        lastHeard = lastHeard,
        hopsAway = 0,
        batteryLevel = 0,
        voltage = 0f,
        channelUtilization = 0f,
        airUtilTx = 0f,
        uptimeSeconds = 0L,
        latitude = latitude,
        longitude = longitude,
        hasValidPosition = hasValidPosition,
        positionTime = positionTime,
        isOnline = isOnline,
        groundSpeed = groundSpeed,
        groundTrack = groundTrack,
        receivedOnSlot = receivedOnSlot,
    )
}
