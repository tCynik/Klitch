package ru.tcynik.klitch.data.chat.adapter

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tcynik.klitch.domain.channel.ChannelSlotResolver
import ru.tcynik.klitch.domain.channel.model.ChannelSlotMaps
import ru.tcynik.klitch.domain.channel.model.Contour
import ru.tcynik.klitch.domain.channel.model.ContourHash
import ru.tcynik.klitch.domain.channel.model.ContourId
import ru.tcynik.klitch.domain.channel.model.ContourTransport
import ru.tcynik.klitch.domain.channel.model.DefaultContour
import ru.tcynik.klitch.domain.channel.model.MeshtasticChannel
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.mesh.model.DataPacket
import ru.tcynik.klitch.mesh.model.Node
import ru.tcynik.klitch.mesh.repository.CommandSender
import ru.tcynik.klitch.mesh.repository.NodeRepository
import ru.tcynik.klitch.mesh.repository.PacketRepository
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
        myNode: Node? = null,
        sosMode: Boolean = false,
    ) {
        every { packetRepository.getContacts() } returns flowOf(mapOf(channelContactKey to lastPacket))
        every { packetRepository.getContactSettings() } returns flowOf(emptyMap())
        every { channelRepository.observeContours() } returns flowOf(contours)
        every { channelRepository.observeSosMode() } returns flowOf(sosMode)
        every { channelSlotResolver.mapsFlow } returns MutableStateFlow(maps)
        every { packetRepository.getUnreadCountFlow(channelContactKey) } returns flowOf(0)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())
        every { nodeRepository.myId } returns MutableStateFlow("!me")
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(myNode)
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
        every { packetRepository.getUnreadCountFlow("0!abc123") } returns flowOf(0)

        adapter.observeContactsAsFlow().test {
            val contacts = awaitItem()
            val privateContact = contacts.firstOrNull { it.type == ru.tcynik.klitch.domain.chat.model.ContactType.PRIVATE }
            assertEquals("0!abc123", privateContact?.id)
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

    @Test
    fun `send private message uses channel encoded in contact id`() = runTest {
        val packetSlot = slot<DataPacket>()
        every { commandSender.sendData(capture(packetSlot)) } returns Unit
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(Node(num = 77))
        coEvery {
            packetRepository.savePacket(
                myNodeNum = any(),
                contactKey = any(),
                packet = any(),
                receivedTime = any(),
                read = any(),
                filtered = any(),
            )
        } returns Unit

        adapter.sendMessage(
            text = "ping",
            contactId = "3!abc123",
            channel = 7,
        )

        verify(exactly = 1) { commandSender.sendData(any()) }
        assertEquals("!abc123", packetSlot.captured.to)
        assertEquals(3, packetSlot.captured.channel)
        assertEquals("ping", packetSlot.captured.text)

        coVerify(exactly = 1) {
            packetRepository.savePacket(
                myNodeNum = 77,
                contactKey = "3!abc123",
                packet = any(),
                receivedTime = any(),
                read = true,
                filtered = false,
            )
        }
    }

    @Test
    fun `online node without PKC — fallback contact id uses channel 0`() = runTest {
        val onlineNode = Node(
            num = 200,
            user = User(id = "!xyz999", short_name = "X1", long_name = "Xray"),
            lastHeard = Int.MAX_VALUE,
        )
        setupContours(contours = listOf(makeContour(isActive = true)))
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(200 to onlineNode))
        every { packetRepository.getUnreadCountFlow("0!xyz999") } returns flowOf(0)

        adapter.observeContactsAsFlow().test {
            val contacts = awaitItem()
            val privateContact = contacts.firstOrNull { it.type == ru.tcynik.klitch.domain.chat.model.ContactType.PRIVATE }
            assertEquals("0!xyz999", privateContact?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `both nodes have PKC — fallback contact id uses PKC channel 8`() = runTest {
        val pkcKey = okio.ByteString.of(1, 2, 3, 4)
        val onlineNode = Node(
            num = 300,
            user = User(id = "!pkc001", short_name = "P1", long_name = "PKCNode", public_key = pkcKey),
            lastHeard = Int.MAX_VALUE,
        )
        val myNode = Node(
            num = 1,
            user = User(id = "!me", public_key = pkcKey),
        )
        setupContours(contours = listOf(makeContour(isActive = true)), myNode = myNode)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(300 to onlineNode))
        every { packetRepository.getUnreadCountFlow("8!pkc001") } returns flowOf(0)

        adapter.observeContactsAsFlow().test {
            val contacts = awaitItem()
            val privateContact = contacts.firstOrNull { it.type == ru.tcynik.klitch.domain.chat.model.ContactType.PRIVATE }
            assertEquals("8!pkc001", privateContact?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `PKC history wins over non-PKC when both exist for same node`() = runTest {
        val nonPkcKey = "0!nodeA"
        val pkcKey = "8!nodeA"
        val pkcBytes = okio.ByteString.of(1, 2, 3, 4)
        val onlineNode = Node(
            num = 400,
            user = User(id = "!nodeA", short_name = "NA", long_name = "NodeA", public_key = pkcBytes),
            lastHeard = Int.MAX_VALUE,
        )
        val myNode = Node(num = 1, user = User(id = "!me", public_key = pkcBytes))
        val nonPkcPacket = DataPacket(to = "!nodeA", channel = 0, text = "sent")
        val pkcPacket = DataPacket(to = "!me", channel = 8, text = "received")
        every { packetRepository.getContacts() } returns flowOf(
            mapOf(channelContactKey to lastPacket, nonPkcKey to nonPkcPacket, pkcKey to pkcPacket)
        )
        every { packetRepository.getContactSettings() } returns flowOf(emptyMap())
        every { channelRepository.observeContours() } returns flowOf(listOf(makeContour(isActive = true)))
        every { channelRepository.observeSosMode() } returns flowOf(false)
        every { channelSlotResolver.mapsFlow } returns MutableStateFlow(resolvedMaps)
        every { packetRepository.getUnreadCountFlow(channelContactKey) } returns flowOf(0)
        every { packetRepository.getUnreadCountFlow(pkcKey) } returns flowOf(0)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(400 to onlineNode))
        every { nodeRepository.myId } returns MutableStateFlow("!me")
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(myNode)

        adapter.observeContactsAsFlow().test {
            val contacts = awaitItem()
            val privateContact = contacts.firstOrNull { it.type == ru.tcynik.klitch.domain.chat.model.ContactType.PRIVATE }
            assertEquals("8!nodeA", privateContact?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Emergency silent mode ─────────────────────────────────────────────────

    @Test
    fun `Emergency contact unreadCount is 0 when SOS inactive`() = runTest {
        val emergencyHash = DefaultContour.CHANNEL_HASH
        val emergencyContactKey = "1^all"
        val emergencyMaps = ChannelSlotMaps(
            slotToHash = mapOf(1 to emergencyHash),
            hashToSlot = mapOf(emergencyHash to 1),
        )
        val emergencyContour = Contour(
            id = DefaultContour.ID,
            name = DefaultContour.DISPLAY_NAME,
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = true,
            transport = DefaultContour.TRANSPORT,
        )
        every { packetRepository.getContacts() } returns flowOf(mapOf(emergencyContactKey to lastPacket))
        every { packetRepository.getContactSettings() } returns flowOf(emptyMap())
        every { channelRepository.observeContours() } returns flowOf(listOf(emergencyContour))
        every { channelRepository.observeSosMode() } returns flowOf(false)
        every { channelSlotResolver.mapsFlow } returns MutableStateFlow(emergencyMaps)
        every { packetRepository.getUnreadCountFlow(emergencyContactKey) } returns flowOf(5)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())
        every { nodeRepository.myId } returns MutableStateFlow("!me")
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(null)

        adapter.observeContactsAsFlow().test {
            val contacts = awaitItem()
            val emergency = contacts.firstOrNull { it.id == DefaultContour.ID.value }
            assertEquals(0, emergency?.unreadCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Emergency contact unreadCount visible when SOS active`() = runTest {
        val emergencyHash = DefaultContour.CHANNEL_HASH
        val emergencyContactKey = "1^all"
        val emergencyMaps = ChannelSlotMaps(
            slotToHash = mapOf(1 to emergencyHash),
            hashToSlot = mapOf(emergencyHash to 1),
        )
        val emergencyContour = Contour(
            id = DefaultContour.ID,
            name = DefaultContour.DISPLAY_NAME,
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = true,
            transport = DefaultContour.TRANSPORT,
        )
        every { packetRepository.getContacts() } returns flowOf(mapOf(emergencyContactKey to lastPacket))
        every { packetRepository.getContactSettings() } returns flowOf(emptyMap())
        every { channelRepository.observeContours() } returns flowOf(listOf(emergencyContour))
        every { channelRepository.observeSosMode() } returns flowOf(true)
        every { channelSlotResolver.mapsFlow } returns MutableStateFlow(emergencyMaps)
        every { packetRepository.getUnreadCountFlow(emergencyContactKey) } returns flowOf(5)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())
        every { nodeRepository.myId } returns MutableStateFlow("!me")
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(null)

        adapter.observeContactsAsFlow().test {
            val contacts = awaitItem()
            val emergency = contacts.firstOrNull { it.id == DefaultContour.ID.value }
            assertEquals(5, emergency?.unreadCount)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
