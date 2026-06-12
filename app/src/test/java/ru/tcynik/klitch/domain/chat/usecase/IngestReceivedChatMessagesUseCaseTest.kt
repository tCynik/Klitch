package ru.tcynik.klitch.domain.chat.usecase

import app.cash.turbine.test
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import ru.tcynik.klitch.logger.NoOpLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import ru.tcynik.klitch.data.chat.adapter.MeshToChatAdapter
import ru.tcynik.klitch.domain.channel.ChannelSlotResolver
import ru.tcynik.klitch.domain.channel.model.ChannelSlotMaps
import ru.tcynik.klitch.domain.channel.model.Contour
import ru.tcynik.klitch.domain.channel.model.ContourHash
import ru.tcynik.klitch.domain.channel.model.ContourId
import ru.tcynik.klitch.domain.channel.model.ContourTransport
import ru.tcynik.klitch.domain.channel.model.MeshtasticChannel
import ru.tcynik.klitch.domain.channel.model.DefaultContour
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.channel.usecase.ApplyDeliveryPolicyUseCase
import ru.tcynik.klitch.domain.channel.usecase.ResolveContourFromSlotUseCase
import ru.tcynik.klitch.domain.chat.model.ChatMessageModel
import ru.tcynik.klitch.domain.chat.repository.ChatMessageRepository

class IngestReceivedChatMessagesUseCaseTest {

    private val adapter: MeshToChatAdapter = mockk()
    private val channelRepository: ContourRepository = mockk()
    private val chatMessageRepository: ChatMessageRepository = mockk()
    private val channelSlotResolver: ChannelSlotResolver = mockk()

    private lateinit var useCase: IngestReceivedChatMessagesUseCase

    // ── fixtures ──────────────────────────────────────────────────────────────

    private val psk = byteArrayOf(0x11, 0x22)
    private val pskBase64 = java.util.Base64.getEncoder().encodeToString(psk)
    private val contourId = ContourId("chat-ch-uuid")
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
        slotToHash = mapOf(2 to hash),
        hashToSlot = mapOf(hash to 2),
    )

    // contactKey format: "{channelIndex}{nodeId}", channel broadcast = "^all"
    private val channelMsg = ChatMessageModel(
        id = "msg-1",
        senderNodeId = "!abc123",
        senderCallsign = "Alice",
        text = "Hello",
        sentAt = 1_000_000L,
        channelId = "2^all",
        isFromMe = false,
    )

    @Before
    fun setUp() {
        coEvery {
            chatMessageRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any())
        } just Runs

        useCase = IngestReceivedChatMessagesUseCase(
            adapter = adapter,
            channelRepository = channelRepository,
            chatMessageRepository = chatMessageRepository,
            channelSlotResolver = channelSlotResolver,
            resolveContourFromSlot = ResolveContourFromSlotUseCase(),
            applyDeliveryPolicy = ApplyDeliveryPolicyUseCase(),
            logger = NoOpLogger(),
        )
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    fun `channel message on resolved slot with active contour — inserted with contour id`() = runTest {
        setupMocks(messages = listOf(channelMsg), maps = resolvedMaps)

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) {
            chatMessageRepository.insertIfAbsent(
                id = channelMsg.id,
                logicalChannelId = contourId.value,
                senderNodeId = channelMsg.senderNodeId,
                senderCallsign = channelMsg.senderCallsign,
                text = channelMsg.text,
                sentAt = channelMsg.sentAt / 1_000L,
                isSelf = channelMsg.isFromMe,
            )
        }
    }

    // ── slot 0 routing (Primary) ──────────────────────────────────────────────

    @Test
    fun `slot 0 — inserted with primary contour id`() = runTest {
        val slot0Msg = channelMsg.copy(channelId = "0^all")
        setupMocks(messages = listOf(slot0Msg), primaryId = contourId, maps = resolvedMaps)

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) {
            chatMessageRepository.insertIfAbsent(
                id = slot0Msg.id,
                logicalChannelId = contourId.value,
                senderNodeId = any(),
                senderCallsign = any(),
                text = any(),
                sentAt = any(),
                isSelf = any(),
            )
        }
    }

    @Test
    fun `slot 0 primary contour not found — dropped`() = runTest {
        val slot0Msg = channelMsg.copy(channelId = "0^all")
        val unknownPrimaryId = ContourId("unknown-primary")
        setupMocks(messages = listOf(slot0Msg), primaryId = unknownPrimaryId, maps = resolvedMaps)

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { chatMessageRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any()) }
    }

    // ── unresolved slot ───────────────────────────────────────────────────────

    @Test
    fun `slot not in slotToHash — dropped`() = runTest {
        val msgOnSlot3 = channelMsg.copy(channelId = "3^all")
        setupMocks(messages = listOf(msgOnSlot3), maps = resolvedMaps)

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { chatMessageRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any()) }
    }

    // ── hash resolves but no matching contour ─────────────────────────────────

    @Test
    fun `slot resolves to hash with no matching contour — dropped`() = runTest {
        setupMocks(messages = listOf(channelMsg), contours = emptyList(), maps = resolvedMaps)

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { chatMessageRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any()) }
    }

    // ── slot 1 Emergency ──────────────────────────────────────────────────────

    @Test
    fun `slot 1 SOS active — inserted with emergency contour id`() = runTest {
        val slot1Msg = channelMsg.copy(channelId = "1^all")
        setupMocks(
            messages = listOf(slot1Msg),
            contours = listOf(contour, DefaultContour.asContour()),
            maps = resolvedMaps,
            sosMode = true,
        )

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) {
            chatMessageRepository.insertIfAbsent(
                id = slot1Msg.id,
                logicalChannelId = DefaultContour.ID.value,
                senderNodeId = any(),
                senderCallsign = any(),
                text = any(),
                sentAt = any(),
                isSelf = any(),
            )
        }
    }

    @Test
    fun `slot 1 SOS inactive — silently stored`() = runTest {
        val slot1Msg = channelMsg.copy(channelId = "1^all")
        setupMocks(
            messages = listOf(slot1Msg),
            contours = listOf(contour, DefaultContour.asContour()),
            maps = resolvedMaps,
            sosMode = false,
        )

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) {
            chatMessageRepository.insertIfAbsent(
                id = slot1Msg.id,
                logicalChannelId = DefaultContour.ID.value,
                senderNodeId = any(),
                senderCallsign = any(),
                text = any(),
                sentAt = any(),
                isSelf = any(),
            )
        }
    }

    // ── isActive filter ───────────────────────────────────────────────────────

    @Test
    fun `slot N contour isActive=false — dropped`() = runTest {
        val inactiveContour = contour.copy(isActive = false)
        setupMocks(messages = listOf(channelMsg), contours = listOf(inactiveContour), maps = resolvedMaps)

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { chatMessageRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any()) }
    }

    // ── private (DM) message bypass ───────────────────────────────────────────

    @Test
    fun `DM message — inserted with original contactKey, no contour routing`() = runTest {
        val dmMsg = channelMsg.copy(channelId = "0!abc123")
        setupMocks(messages = listOf(dmMsg), maps = resolvedMaps)

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) {
            chatMessageRepository.insertIfAbsent(
                id = dmMsg.id,
                logicalChannelId = "0!abc123",
                senderNodeId = any(),
                senderCallsign = any(),
                text = any(),
                sentAt = any(),
                isSelf = any(),
            )
        }
    }

    // ── empty message list ────────────────────────────────────────────────────

    @Test
    fun `empty message list — nothing inserted`() = runTest {
        setupMocks(messages = emptyList(), maps = resolvedMaps)

        useCase.observe().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { chatMessageRepository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any()) }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun setupMocks(
        messages: List<ChatMessageModel>,
        contours: List<Contour> = listOf(contour),
        primaryId: ContourId = contourId,
        maps: ChannelSlotMaps,
        sosMode: Boolean = false,
    ) {
        every { adapter.observeMessagesAsFlow(emptySet(), "") } returns flowOf(messages)
        every { channelRepository.observeContours() } returns flowOf(contours)
        every { channelRepository.observePrimaryContourId() } returns flowOf(primaryId)
        every { channelRepository.observeSosMode() } returns flowOf(sosMode)
        every { channelSlotResolver.mapsFlow } returns MutableStateFlow(maps)
    }
}
