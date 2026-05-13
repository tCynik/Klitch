package ru.tcynik.meshtactics.data.chat.adapter

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.ChannelSlotMaps
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.mesh.model.DataPacket
import ru.tcynik.meshtactics.mesh.model.Node
import ru.tcynik.meshtactics.mesh.repository.CommandSender
import ru.tcynik.meshtactics.mesh.repository.NodeRepository
import ru.tcynik.meshtactics.mesh.repository.PacketRepository
import org.meshtastic.proto.User

class MeshToChatAdapterTest {

    private val packetRepository: PacketRepository = mockk()
    private val nodeRepository: NodeRepository = mockk()
    private val commandSender: CommandSender = mockk()
    private val channelRepository: ContourRepository = mockk()
    private val channelSlotResolver: ChannelSlotResolver = mockk()

    private val adapter = MeshToChatAdapter(
        packetRepository = packetRepository,
        nodeRepository = nodeRepository,
        commandSender = commandSender,
        channelRepository = channelRepository,
        channelSlotResolver = channelSlotResolver,
    )

    // ── fixtures ──────────────────────────────────────────────────────────────

    private val psk = byteArrayOf(0x11, 0x22)
    private val pskBase64 = java.util.Base64.getEncoder().encodeToString(psk)
    private val hash = ContourHash.compute("TestChannel", psk)
    private val contourId = ContourId("test-contour-uuid")

    private fun makeContour(isActive: Boolean) = Contour(
        id = contourId,
        name = "TestChannel",
        description = null,
        expiration = null,
        exclusivityTime = null,
        isActive = isActive,
        transport = ContourTransport(meshtastic = MeshtasticChannel(psk = pskBase64, channelHash = hash)),
    )

    private val resolvedMaps = ChannelSlotMaps(
        slotToHash = mapOf(1 to hash),
        hashToSlot = mapOf(hash to 1),
    )

    private val channelContactKey = "1^all"
    private val lastPacket = DataPacket(to = "^all", channel = 1, text = "hi")

    private fun setupContours(
        contours: List<Contour>,
        maps: ChannelSlotMaps = resolvedMaps,
    ) {
        every { packetRepository.getContacts() } returns flowOf(mapOf(channelContactKey to lastPacket))
        every { packetRepository.getContactSettings() } returns flowOf(emptyMap())
        every { channelRepository.observeContours() } returns flowOf(contours)
        every { channelSlotResolver.mapsFlow } returns MutableStateFlow(maps)
        every { packetRepository.getUnreadCountFlow(channelContactKey) } returns flowOf(0)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())
        every { nodeRepository.myId } returns MutableStateFlow("!me")
    }

    // ── isActive propagation: CHANNEL ─────────────────────────────────────────

    @Test
    fun `channel contact with active contour — isActive is true in dto`() = runTest {
        setupContours(contours = listOf(makeContour(isActive = true)))

        adapter.observeContactsAsFlow().test {
            val contacts = awaitItem()
            assertEquals(1, contacts.size)
            assertTrue(contacts.first().isActive)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `channel contact with inactive contour — isActive is false in dto`() = runTest {
        setupContours(contours = listOf(makeContour(isActive = false)))

        adapter.observeContactsAsFlow().test {
            val contacts = awaitItem()
            assertEquals(1, contacts.size)
            assertEquals(false, contacts.first().isActive)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── channel contact filtering ─────────────────────────────────────────────

    @Test
    fun `contour without resolved slot stays visible in list`() = runTest {
        val emptyMaps = ChannelSlotMaps(slotToHash = emptyMap(), hashToSlot = emptyMap())
        setupContours(contours = listOf(makeContour(isActive = true)), maps = emptyMaps)

        adapter.observeContactsAsFlow().test {
            val contacts = awaitItem()
            assertEquals(1, contacts.size)
            assertEquals(contourId.value, contacts.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty contour list returns empty contacts`() = runTest {
        setupContours(contours = emptyList())

        adapter.observeContactsAsFlow().test {
            val contacts = awaitItem()
            assertTrue(contacts.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── contour name is used as contact display name ──────────────────────────

    @Test
    fun `channel contact — shortName equals contour name`() = runTest {
        setupContours(contours = listOf(makeContour(isActive = true)))

        adapter.observeContactsAsFlow().test {
            val contacts = awaitItem()
            assertEquals("TestChannel", contacts.first().shortName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `online node without history appears as private contact`() = runTest {
        val onlineNode = Node(
            num = 100,
            user = User(id = "!abc123", short_name = "A1", long_name = "Alpha"),
            lastHeard = Int.MAX_VALUE,
        )
        setupContours(contours = listOf(makeContour(isActive = true)))
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(100 to onlineNode))

        adapter.observeContactsAsFlow().test {
            val contacts = awaitItem()
            val privateContact = contacts.firstOrNull { it.type == ru.tcynik.meshtactics.domain.chat.model.ContactType.PRIVATE }
            assertEquals("!abc123", privateContact?.id)
            assertEquals("A1", privateContact?.shortName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `offline node without history is not shown`() = runTest {
        val offlineNode = Node(
            num = 101,
            user = User(id = "!off001", short_name = "OFF", long_name = "Offline"),
            lastHeard = 0,
        )
        setupContours(contours = listOf(makeContour(isActive = true)))
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(101 to offlineNode))

        adapter.observeContactsAsFlow().test {
            val contacts = awaitItem()
            assertTrue(contacts.none { it.id == "!off001" })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
