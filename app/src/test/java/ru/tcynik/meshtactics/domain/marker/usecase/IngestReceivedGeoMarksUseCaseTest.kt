package ru.tcynik.meshtactics.domain.marker.usecase

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.data.marker.adapter.GeoMarkWaypointAdapter
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.ChannelMetadata
import ru.tcynik.meshtactics.domain.channel.model.ChannelSlotMaps
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannel
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelHash
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticBinding
import ru.tcynik.meshtactics.domain.channel.repository.LogicalChannelRepository
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository
import ru.tcynik.meshtactics.mesh.model.DataPacket
import ru.tcynik.meshtactics.mesh.repository.PacketRepository

class IngestReceivedGeoMarksUseCaseTest {

    private val packetRepository: PacketRepository = mockk()
    private val channelRepository: LogicalChannelRepository = mockk()
    private val geoMarkRepository: GeoMarkRepository = mockk()
    private val adapter: GeoMarkWaypointAdapter = mockk()
    private val channelSlotResolver: ChannelSlotResolver = mockk()

    private lateinit var useCase: IngestReceivedGeoMarksUseCase

    // ── fixtures ──────────────────────────────────────────────────────────────

    private val psk = byteArrayOf(0x11, 0x22)
    private val channelId = LogicalChannelId("geo-ch-uuid")
    private val hash = LogicalChannelHash.compute("LongFast", psk)
    private val channel = LogicalChannel(
        id = channelId,
        metadata = ChannelMetadata(name = "LongFast"),
        transports = listOf(MeshtasticBinding(psk = psk, channelHash = hash)),
    )
    private val resolvedMaps = ChannelSlotMaps(
        slotToHash = mapOf(0 to hash),
        hashToSlot = mapOf(hash to 0),
    )

    // DataPacket with no waypoint data — adapter decides how to decode
    private val packet = DataPacket(bytes = null, dataType = 0, channel = 0)

    private val geoMark = GeoMarkModel(
        id = "mark-1",
        waypointId = 42,
        type = GeoMarkType.POINT,
        points = listOf(GeoPoint(55.75, 37.62)),
        authorNodeId = "!abc123",
        createdAt = 1000L,
        expiresAt = null,
        isSelf = false,
    )

    @Before
    fun setUp() {
        coEvery { geoMarkRepository.persistReceived(any(), any()) } just Runs

        useCase = IngestReceivedGeoMarksUseCase(
            packetRepository = packetRepository,
            channelRepository = channelRepository,
            geoMarkRepository = geoMarkRepository,
            adapter = adapter,
            channelSlotResolver = channelSlotResolver,
        )
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    fun `packet on resolved slot with valid model — persistReceived called`() = runTest {
        setupMocks(packets = listOf(packet), maps = resolvedMaps)
        every { adapter.decode(packet, any()) } returns geoMark

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { geoMarkRepository.persistReceived(geoMark, channelId) }
    }

    // ── unresolved slot ───────────────────────────────────────────────────────

    @Test
    fun `packet on slot absent from slotToHash — dropped`() = runTest {
        val packetOnSlot3 = DataPacket(bytes = null, dataType = 0, channel = 3)
        // slot 3 not in maps
        setupMocks(packets = listOf(packetOnSlot3), maps = resolvedMaps)
        every { adapter.decode(any(), any()) } returns geoMark

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    @Test
    fun `packet when slotToHash is empty — dropped`() = runTest {
        setupMocks(packets = listOf(packet), maps = ChannelSlotMaps())
        every { adapter.decode(any(), any()) } returns geoMark

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    // ── slot resolved but no matching channel ─────────────────────────────────

    @Test
    fun `slot resolves to hash with no matching channel — dropped`() = runTest {
        val orphanHash = LogicalChannelHash.compute("Orphan", byteArrayOf(0xFF.toByte()))
        val orphanMaps = ChannelSlotMaps(
            slotToHash = mapOf(0 to orphanHash),
            hashToSlot = mapOf(orphanHash to 0),
        )
        setupMocks(packets = listOf(packet), channels = emptyList(), maps = orphanMaps)
        every { adapter.decode(any(), any()) } returns geoMark

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    // ── adapter returns null ──────────────────────────────────────────────────

    @Test
    fun `adapter decode returns null — dropped`() = runTest {
        setupMocks(packets = listOf(packet), maps = resolvedMaps)
        every { adapter.decode(packet, any()) } returns null

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    // ── expiry ────────────────────────────────────────────────────────────────

    @Test
    fun `expired model — dropped`() = runTest {
        // expiresAt = 1 second (already expired in any reasonable test run)
        val expiredMark = geoMark.copy(expiresAt = 1L)
        setupMocks(packets = listOf(packet), maps = resolvedMaps)
        every { adapter.decode(packet, any()) } returns expiredMark

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { geoMarkRepository.persistReceived(any(), any()) }
    }

    @Test
    fun `model with null expiresAt — not filtered by expiry check`() = runTest {
        val noExpiry = geoMark.copy(expiresAt = null)
        setupMocks(packets = listOf(packet), maps = resolvedMaps)
        every { adapter.decode(packet, any()) } returns noExpiry

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { geoMarkRepository.persistReceived(noExpiry, channelId) }
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

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun setupMocks(
        packets: List<DataPacket>,
        channels: List<LogicalChannel> = listOf(channel),
        maps: ChannelSlotMaps,
    ) {
        every { packetRepository.getWaypoints() } returns flowOf(packets)
        every { channelRepository.observeChannels() } returns flowOf(channels)
        every { channelSlotResolver.mapsFlow } returns MutableStateFlow(maps)
    }
}
