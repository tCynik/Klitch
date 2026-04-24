package ru.tcynik.meshtactics.presentation.feature.settings

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.ChannelMetadata
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannel
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelHash
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticBinding
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
import ru.tcynik.meshtactics.domain.channel.usecase.DeleteLogicalChannelUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveLogicalChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SaveLogicalChannelUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SlotResolution
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.user.usecase.SaveAppUserUseCase
import ru.tcynik.meshtactics.presentation.feature.settings.models.NodeWriteEvent
import java.util.UUID

class UserSettingsViewModelChannelsTest {

    private val observeAppUser: ObserveAppUserUseCase = mockk()
    private val saveAppUser: SaveAppUserUseCase = mockk(relaxed = true)
    private val observeLogicalChannels: ObserveLogicalChannelsUseCase = mockk()
    private val saveLogicalChannel: SaveLogicalChannelUseCase = mockk(relaxed = true)
    private val deleteLogicalChannel: DeleteLogicalChannelUseCase = mockk(relaxed = true)
    private val observeNodeChannels: ObserveNodeChannelsUseCase = mockk()
    private val writeChannel: WriteChannelUseCase = mockk(relaxed = true)
    private val resolveSlot: ResolveChannelSlotUseCase = mockk()
    private val observeConnectionStatus: ObserveConnectionStatusUseCase = mockk()
    private val channelSlotResolver: ChannelSlotResolver = mockk()

    private val channelsFlow = MutableStateFlow<List<LogicalChannel>>(emptyList())
    private val nodeChannelsFlow = MutableStateFlow<List<NodeChannelSlot>>(emptyList())
    private val connectionStatusFlow = MutableStateFlow<MeshConnectionStatus>(MeshConnectionStatus.Disconnected)

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: UserSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } returns "AAEC"
        every { observeAppUser.invoke(any()) } returns flowOf(AppUser(""))
        every { observeLogicalChannels.invoke(any()) } returns channelsFlow
        every { observeNodeChannels.invoke(any()) } returns nodeChannelsFlow
        every { observeConnectionStatus.invoke(any()) } returns connectionStatusFlow
        every { channelSlotResolver.hashToSlot } returns emptyMap()
        every { resolveSlot.invoke(any(), any()) } returns SlotResolution.NoFreeSlot
        viewModel = UserSettingsViewModel(
            observeAppUser = observeAppUser,
            saveAppUser = saveAppUser,
            observeLogicalChannels = observeLogicalChannels,
            saveLogicalChannel = saveLogicalChannel,
            deleteLogicalChannel = deleteLogicalChannel,
            observeNodeChannels = observeNodeChannels,
            writeChannel = writeChannel,
            resolveSlot = resolveSlot,
            observeConnectionStatus = observeConnectionStatus,
            channelSlotResolver = channelSlotResolver,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(android.util.Base64::class)
    }

    private fun makeChannel(
        name: String,
        psk: ByteArray,
        id: String = UUID.randomUUID().toString(),
        isAutoSync: Boolean = false,
    ): LogicalChannel {
        val hash = LogicalChannelHash.compute(name, psk)
        return LogicalChannel(
            id = LogicalChannelId(id),
            metadata = ChannelMetadata(name = name),
            transports = listOf(MeshtasticBinding(psk = psk, channelHash = hash)),
            isAutoSync = isAutoSync,
        )
    }

    private val connectedStatus = MeshConnectionStatus.Connected(
        nodeId = "!aabbccdd",
        shortName = "ТЕ",
        rssi = -70,
        batteryLevel = 80,
    )

    private fun populateCache(
        channels: List<LogicalChannel>,
        nodeSlots: List<NodeChannelSlot> = emptyList(),
        status: MeshConnectionStatus = MeshConnectionStatus.Disconnected,
    ) {
        channelsFlow.value = channels
        nodeChannelsFlow.value = nodeSlots
        connectionStatusFlow.value = status
    }

    // ── onPushToNode ──────────────────────────────────────────────────────────

    @Test
    fun `onPushToNode not connected — emits NotConnected event`() = runTest(testDispatcher) {
        val channel = makeChannel("Alpha", byteArrayOf(0x01))
        populateCache(listOf(channel), status = MeshConnectionStatus.Disconnected)

        viewModel.onPushToNode(channel.id)

        assertEquals(NodeWriteEvent.NotConnected, viewModel.uiState.value.nodeWriteEvent)
    }

    @Test
    fun `onPushToNode connected AlreadySynced — no writeChannel call Sent event`() = runTest(testDispatcher) {
        val channel = makeChannel("Alpha", byteArrayOf(0x01))
        every { resolveSlot.invoke(any(), any()) } returns SlotResolution.AlreadySynced(2)
        populateCache(listOf(channel), status = connectedStatus)

        viewModel.onPushToNode(channel.id)

        verify(exactly = 0) { writeChannel.invoke(any(), any(), any()) }
        assertEquals(NodeWriteEvent.Sent("Alpha"), viewModel.uiState.value.nodeWriteEvent)
    }

    @Test
    fun `onPushToNode connected FreeSlot — calls writeChannel with correct slot and emits Sent`() = runTest(testDispatcher) {
        val channel = makeChannel("Alpha", byteArrayOf(0x01))
        every { resolveSlot.invoke(any(), any()) } returns SlotResolution.FreeSlot(3)
        populateCache(listOf(channel), status = connectedStatus)

        viewModel.onPushToNode(channel.id)

        verify(exactly = 1) { writeChannel.invoke(eq(3), eq("Alpha"), any()) }
        assertEquals(NodeWriteEvent.Sent("Alpha"), viewModel.uiState.value.nodeWriteEvent)
    }

    @Test
    fun `onPushToNode connected NoFreeSlot — emits NoFreeSlot event`() = runTest(testDispatcher) {
        val channel = makeChannel("Alpha", byteArrayOf(0x01))
        every { resolveSlot.invoke(any(), any()) } returns SlotResolution.NoFreeSlot
        populateCache(listOf(channel), status = connectedStatus)

        viewModel.onPushToNode(channel.id)

        assertEquals(NodeWriteEvent.NoFreeSlot, viewModel.uiState.value.nodeWriteEvent)
    }

    // ── onDeleteFromNode ──────────────────────────────────────────────────────

    @Test
    fun `onDeleteFromNode slot 0 — writeChannel not called`() = runTest(testDispatcher) {
        val psk = byteArrayOf(0x02)
        val channel = makeChannel("Primary", psk)
        val hash = LogicalChannelHash.compute("Primary", psk)
        every { channelSlotResolver.hashToSlot } returns mapOf(hash to 0)
        populateCache(listOf(channel))

        viewModel.onDeleteFromNode(channel.id)

        verify(exactly = 0) { writeChannel.invoke(any(), any(), any()) }
    }

    @Test
    fun `onDeleteFromNode slot 3 — calls writeChannel with empty name and psk`() = runTest(testDispatcher) {
        val psk = byteArrayOf(0x03)
        val channel = makeChannel("Bravo", psk)
        val hash = LogicalChannelHash.compute("Bravo", psk)
        every { channelSlotResolver.hashToSlot } returns mapOf(hash to 3)
        populateCache(listOf(channel))

        viewModel.onDeleteFromNode(channel.id)

        verify(exactly = 1) { writeChannel.invoke(eq(3), eq(""), eq("")) }
    }

    // ── onToggleAutoSync ──────────────────────────────────────────────────────

    @Test
    fun `onToggleAutoSync enables — saveLogicalChannel called with isAutoSync true`() = runTest(testDispatcher) {
        val channel = makeChannel("Gamma", byteArrayOf(0x04), isAutoSync = false)
        populateCache(listOf(channel))

        viewModel.onToggleAutoSync(channel.id, true)
        runCurrent()

        coVerify(exactly = 1) {
            saveLogicalChannel.invoke(match { it.isAutoSync && it.id == channel.id })
        }
    }

    @Test
    fun `onToggleAutoSync disables — saveLogicalChannel called with isAutoSync false`() = runTest(testDispatcher) {
        val channel = makeChannel("Gamma", byteArrayOf(0x04), isAutoSync = true)
        populateCache(listOf(channel))

        viewModel.onToggleAutoSync(channel.id, false)
        runCurrent()

        coVerify(exactly = 1) {
            saveLogicalChannel.invoke(match { !it.isAutoSync && it.id == channel.id })
        }
    }

    // ── onNodeWriteEventConsumed ──────────────────────────────────────────────

    @Test
    fun `onNodeWriteEventConsumed — clears nodeWriteEvent`() = runTest(testDispatcher) {
        val channel = makeChannel("Alpha", byteArrayOf(0x01))
        populateCache(listOf(channel), status = MeshConnectionStatus.Disconnected)
        viewModel.onPushToNode(channel.id)
        assertEquals(NodeWriteEvent.NotConnected, viewModel.uiState.value.nodeWriteEvent)

        viewModel.onNodeWriteEventConsumed()

        assertNull(viewModel.uiState.value.nodeWriteEvent)
    }
}
