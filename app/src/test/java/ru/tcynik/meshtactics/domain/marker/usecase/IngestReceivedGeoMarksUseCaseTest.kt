package ru.tcynik.meshtactics.domain.marker.usecase

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import ru.tcynik.meshtactics.logger.NoOpLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.data.marker.adapter.GeoMarkWaypointAdapter
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.ChannelSlotMaps
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository
import ru.tcynik.meshtactics.mesh.model.DataPacket
import ru.tcynik.meshtactics.mesh.repository.PacketRepository

class IngestReceivedGeoMarksUseCaseTest {

    private val packetRepository: PacketRepository = mockk()
    private val channelRepository: ContourRepository = mockk()
    private val geoMarkRepository: GeoMarkRepository = mockk()
    private val adapter = GeoMarkWaypointAdapter()
    private val channelSlotResolver: ChannelSlotResolver = mockk()

    private lateinit var useCase: IngestReceivedGeoMarksUseCase

    // ── fixtures ──────────────────────────────────────────────────────────────

    private val psk = byteArrayOf(0x11, 0x22)
    private val pskBase64 = java.util.Base64.getEncoder().encodeToString(psk)
    private val contourId = ContourId("geo-ch-uuid")
    private val hash = ContourHash.compute("LongFast", psk)
    private val contour = Contour(
        id = contourId,
        name = "LongFast",
        description = null,
        expiration = null,
        exclusivityTime = null,
        isActive = true,
        transport = ContourTransport(meshtastic = MeshtasticChannel(psk = pskBase64, channelHash = hash)),
    )
    private val resolvedMaps = ChannelSlotMaps(
        slotToHash = mapOf(1 to hash),
        hashToSlot = mapOf(hash to 1),
    )

    private val senderNodeNum = 0x00ab_c123
    private val fixtureNowSeconds = System.currentTimeMillis() / 1_000
    private val mt1SourceMark = GeoMarkModel(
        id = "550e8400-e29b-41d4-a716-446655440000",
        waypointId = GeoMarkWaypointAdapter.waypointIdFromMarkId("550e8400-e29b-41d4-a716-446655440000"),
        type = GeoMarkType.POINT,
        points = listOf(GeoPoint(55.75, 37.62)),
        authorNodeId = DataPacket.nodeNumToDefaultId(senderNodeNum),
        createdAt = fixtureNowSeconds,
        expiresAt = null,
        isSelf = false,
    )
    // MT1 waypoint on slot 1 (slot 0 is reserved for Emergency)
    private val packet = adapter.encode(mt1SourceMark, senderNodeNum, mt1SourceMark.authorNodeId, fixtureNowSeconds)
        .apply {
            from = mt1SourceMark.authorNodeId
            channel = 1
            id = 99
            time = fixtureNowSeconds * 1_000
        }

    private val geoMark get() = adapter.decode(packet)!!

    @Before
    fun setUp() {
        coEvery { geoMarkRepository.persistReceived(any(), any()) } just Runs
        coEvery { geoMarkRepository.getActiveMarkIds() } returns emptySet()
        coEvery { geoMarkRepository.getActiveWaypointIds() } returns emptySet()
        coEvery { geoMarkRepository.getDismissedMarkIds() } returns emptySet()

        useCase = IngestReceivedGeoMarksUseCase(
            packetRepository = packetRepository,
            channelRepository = channelRepository,
            geoMarkRepository = geoMarkRepository,
            adapter = adapter,
            channelSlotResolver = channelSlotResolver,
            logger = NoOpLogger(),
        )
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    fun `packet on resolved slot with valid model — persistReceived called`() = runTest {
        setupMocks(packets = listOf(packet), maps = resolvedMaps)

        useCase.observe().test {
            awaitItem()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { geoMarkRepository.persistReceived(geoMark, contourId) }
    }

    // ── unresolved slot ───────────────────────────────────────────────────────

    @Test
    fun `packet on slot absent from slotToHash — dropped`() = runTest {
        val packetOnSlot3 = DataPacket(bytes = null, dataType = 0, channel = 3)
        // slot 3 not in maps
        setupMocks(packets = listOf(packetOnSlot3), maps = resolvedMaps)
        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    @Test
    fun `packet when slotToHash is empty — dropped`() = runTest {
        setupMocks(packets = listOf(packet), maps = ChannelSlotMaps())
        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    // ── slot resolved but no matching contour ─────────────────────────────────

    @Test
    fun `slot resolves to hash with no matching contour — dropped`() = runTest {
        val orphanHash = ContourHash.compute("Orphan", byteArrayOf(0xFF.toByte()))
        val orphanMaps = ChannelSlotMaps(
            slotToHash = mapOf(0 to orphanHash),
            hashToSlot = mapOf(orphanHash to 0),
        )
        setupMocks(packets = listOf(packet), contours = emptyList(), maps = orphanMaps)
        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    // ── adapter returns null ──────────────────────────────────────────────────

    @Test
    fun `adapter decode returns null — dropped`() = runTest {
        val nonMt1 = DataPacket(bytes = null, dataType = 0, channel = 1)
        setupMocks(packets = listOf(nonMt1), maps = resolvedMaps)

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    // ── expiry ────────────────────────────────────────────────────────────────

    @Test
    fun `expired model — dropped`() = runTest {
        val expiredPacket = adapter.encode(
            mt1SourceMark.copy(expiresAt = 1L),
            senderNodeNum,
            mt1SourceMark.authorNodeId,
            fixtureNowSeconds,
        ).apply {
            from = mt1SourceMark.authorNodeId
            channel = 1
            id = 100
        }
        setupMocks(packets = listOf(expiredPacket), maps = resolvedMaps)

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    @Test
    fun `model with null expiresAt — not filtered by expiry check`() = runTest {
        val noExpiryPacket = adapter.encode(
            mt1SourceMark.copy(expiresAt = 0L),
            senderNodeNum,
            mt1SourceMark.authorNodeId,
            fixtureNowSeconds,
        ).apply {
            from = mt1SourceMark.authorNodeId
            channel = 1
            id = 100
            time = fixtureNowSeconds * 1_000
        }
        setupMocks(packets = listOf(noExpiryPacket), maps = resolvedMaps)

        useCase.observe().test {
            awaitItem()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        val expectedMark = adapter.decode(noExpiryPacket)!!
        coVerify(exactly = 1) { geoMarkRepository.persistReceived(expectedMark, contourId) }
    }

    // ── empty packet list ─────────────────────────────────────────────────────

    @Test
    fun `empty packet list — nothing persisted`() = runTest {
        setupMocks(packets = emptyList(), maps = resolvedMaps)

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    // ── slot 0 routing (Emergency) ────────────────────────────────────────────

    @Test
    fun `slot 0 Emergency isActive=true — persistReceived called`() = runTest {
        val slot0Packet = adapter.encode(mt1SourceMark, senderNodeNum, mt1SourceMark.authorNodeId, fixtureNowSeconds)
            .apply {
                from = mt1SourceMark.authorNodeId
                channel = 0
                id = 101
                time = fixtureNowSeconds * 1_000
            }
        val activeEmergency = makeEmergencyContour(isActive = true)
        setupMocks(packets = listOf(slot0Packet), contours = listOf(activeEmergency), maps = resolvedMaps)

        useCase.observe().test {
            awaitItem()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { geoMarkRepository.persistReceived(geoMark, DefaultContour.ID) }
    }

    @Test
    fun `slot 0 Emergency isActive=false — packet dropped`() = runTest {
        val slot0Packet = DataPacket(bytes = null, dataType = 0, channel = 0)
        val inactiveEmergency = makeEmergencyContour(isActive = false)
        setupMocks(packets = listOf(slot0Packet), contours = listOf(inactiveEmergency), maps = resolvedMaps)
        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    @Test
    fun `dismissed mark id — persistReceived not called`() = runTest {
        setupMocks(packets = listOf(packet), maps = resolvedMaps)
        coEvery { geoMarkRepository.getDismissedMarkIds() } returns setOf(geoMark.id)

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    @Test
    fun `active mark id — persistReceived not called again`() = runTest {
        setupMocks(packets = listOf(packet), maps = resolvedMaps)
        coEvery { geoMarkRepository.getActiveMarkIds() } returns setOf(geoMark.id)

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    @Test
    fun `active waypoint id with different mark id — persistReceived not called`() = runTest {
        setupMocks(packets = listOf(packet), maps = resolvedMaps)
        coEvery { geoMarkRepository.getActiveWaypointIds() } returns setOf(geoMark.waypointId)

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    @Test
    fun `dismissed wp alias — persistReceived not called`() = runTest {
        setupMocks(packets = listOf(packet), maps = resolvedMaps)
        coEvery { geoMarkRepository.getDismissedMarkIds() } returns setOf("wp-${geoMark.waypointId}")

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    // ── isActive filter ───────────────────────────────────────────────────────

    @Test
    fun `slot N contour isActive=false — packet dropped`() = runTest {
        val inactiveContour = contour.copy(isActive = false)
        setupMocks(packets = listOf(packet), contours = listOf(inactiveContour), maps = resolvedMaps)
        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeEmergencyContour(isActive: Boolean) = Contour(
        id = DefaultContour.ID,
        name = DefaultContour.DISPLAY_NAME,
        description = null,
        expiration = null,
        exclusivityTime = null,
        isActive = isActive,
        transport = DefaultContour.TRANSPORT,
    )

    private fun setupMocks(
        packets: List<DataPacket>,
        contours: List<Contour> = listOf(contour),
        maps: ChannelSlotMaps,
    ) {
        every { packetRepository.getWaypoints() } returns flowOf(packets)
        every { channelRepository.observeContours() } returns flowOf(contours)
        every { channelSlotResolver.mapsFlow } returns MutableStateFlow(maps)
    }
}
