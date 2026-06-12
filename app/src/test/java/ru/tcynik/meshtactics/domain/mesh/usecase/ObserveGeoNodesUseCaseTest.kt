package ru.tcynik.meshtactics.domain.mesh.usecase

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
import ru.tcynik.meshtactics.domain.mesh.model.MeshNodeModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import java.util.Base64

class ObserveGeoNodesUseCaseTest {

    private val repository: MeshNetworkRepository = mockk()
    private val contourRepository: ContourRepository = mockk()
    private val channelSlotResolver: ChannelSlotResolver = mockk()
    private val useCase = ObserveGeoNodesUseCase(
        repository,
        contourRepository,
        channelSlotResolver,
        ResolveContourFromSlotUseCase(),
    )

    private val primaryId = ContourId("primary")

    @Before
    fun setUp() {
        val primary = contour(ContourHash.compute("Primary", byteArrayOf(0x01)), isActive = true).copy(id = primaryId, name = "Primary")
        every { contourRepository.observeContours() } returns flowOf(listOf(primary, DefaultContour.asContour()))
        every { contourRepository.observePrimaryContourId() } returns flowOf(primaryId)
        every { contourRepository.observeSosMode() } returns flowOf(false)
        every { channelSlotResolver.mapsFlow } returns MutableStateFlow(ChannelSlotMaps())
        every { repository.observeOurNode() } returns flowOf(null)
    }

    // ── Contour filter ───────────────────────────────────────────────────────

    @Test
    fun `node receivedOnSlot=null — excluded`() = runTest {
        val peer = node(nodeId = "A", receivedOnSlot = null)
        every { repository.observeNodes() } returns flowOf(listOf(peer))

        useCase(NoParams).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `node receivedOnSlot=1 SOS inactive — excluded`() = runTest {
        val peer = node(nodeId = "A", receivedOnSlot = 1)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
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
        val inactiveContour = contour(hash, isActive = false)
        val maps = ChannelSlotMaps(slotToHash = mapOf(3 to hash), hashToSlot = mapOf(hash to 3))
        every { repository.observeNodes() } returns flowOf(listOf(node(nodeId = "A", receivedOnSlot = 3)))
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
        val inactiveContour = contour(hash, isActive = false)
        val maps = ChannelSlotMaps(slotToHash = mapOf(3 to hash), hashToSlot = mapOf(hash to 3))
        every { repository.observeNodes() } returns flowOf(listOf(node(nodeId = "A", receivedOnSlot = 3)))
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
        val activeContour = contour(hash, isActive = true)
        val maps = ChannelSlotMaps(slotToHash = mapOf(3 to hash), hashToSlot = mapOf(hash to 3))
        every { repository.observeNodes() } returns flowOf(listOf(node(nodeId = "A", receivedOnSlot = 3)))
        every { contourRepository.observeContours() } returns flowOf(listOf(activeContour))
        every { channelSlotResolver.mapsFlow } returns MutableStateFlow(maps)

        useCase(NoParams).test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Basic filtering ──────────────────────────────────────────────────────

    @Test
    fun `nodes without valid position filtered out`() = runTest {
        every { repository.observeNodes() } returns flowOf(listOf(
            node(nodeId = "A", hasValidPosition = false),
            node(nodeId = "B", hasValidPosition = true),
        ))

        useCase(NoParams).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("B", result[0].nodeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `own node excluded`() = runTest {
        val ourNode = node(nodeId = "SELF")
        every { repository.observeNodes() } returns flowOf(listOf(ourNode, node(nodeId = "PEER")))
        every { repository.observeOurNode() } returns flowOf(ourNode)

        useCase(NoParams).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("PEER", result[0].nodeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun node(
        nodeId: String,
        hasValidPosition: Boolean = true,
        receivedOnSlot: Int? = 0,
        positionTime: Int = (System.currentTimeMillis() / 1000 - 60).toInt(),
    ) = MeshNodeModel(
        num = 0,
        nodeId = nodeId,
        shortName = nodeId,
        longName = nodeId,
        snr = 0f,
        rssi = 0,
        lastHeard = positionTime,
        hopsAway = 0,
        batteryLevel = 0,
        voltage = 0f,
        channelUtilization = 0f,
        airUtilTx = 0f,
        uptimeSeconds = 0L,
        latitude = 55.0,
        longitude = 37.0,
        hasValidPosition = hasValidPosition,
        positionTime = positionTime,
        isOnline = true,
        groundSpeed = 0,
        groundTrack = 0,
        receivedOnSlot = receivedOnSlot,
    )

    private fun contour(hash: ContourHash, isActive: Boolean) = Contour(
        id = ContourId("test-id"),
        name = "test",
        description = null,
        expiration = null,
        exclusivityTime = null,
        isActive = isActive,
        transport = ContourTransport(
            MeshtasticChannel(Base64.getEncoder().encodeToString(byteArrayOf(0x01)), hash)
        ),
    )
}
