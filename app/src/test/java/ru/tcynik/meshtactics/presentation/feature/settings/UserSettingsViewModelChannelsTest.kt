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
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
import ru.tcynik.meshtactics.domain.channel.usecase.DeleteContourUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SaveContourUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SetContourActiveUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SlotResolution
import ru.tcynik.meshtactics.domain.channel.usecase.SyncContoursOnConnectUseCase
import ru.tcynik.meshtactics.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.meshtactics.domain.channel.usecase.CheckContourSyncUseCase
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.emergency.usecase.CancelEmergencyUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.ObserveEmergencyModeUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.TriggerEmergencyUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.DisableNodePositionBroadcastUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.EnableNodePositionBroadcastReadyUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveGpsBroadcastEnabledUseCase
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.RebootNodeUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.SetGpsBroadcastEnabledUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.user.usecase.SaveAppUserUseCase
import ru.tcynik.meshtactics.presentation.feature.settings.models.NodeWriteEvent
import java.util.Base64
import java.util.UUID

class UserSettingsViewModelChannelsTest {

    private val observeAppUser: ObserveAppUserUseCase = mockk()
    private val saveAppUser: SaveAppUserUseCase = mockk(relaxed = true)
    private val observeContours: ObserveContoursUseCase = mockk()
    private val saveContour: SaveContourUseCase = mockk(relaxed = true)
    private val deleteContour: DeleteContourUseCase = mockk(relaxed = true)
    private val setContourActive: SetContourActiveUseCase = mockk(relaxed = true)
    private val observeNodeChannels: ObserveNodeChannelsUseCase = mockk()
    private val writeChannel: WriteChannelUseCase = mockk(relaxed = true)
    private val resolveSlot: ResolveChannelSlotUseCase = mockk()
    private val observeConnectionStatus: ObserveConnectionStatusUseCase = mockk()
    private val channelSlotResolver: ChannelSlotResolver = mockk()
    private val syncContoursOnConnect: SyncContoursOnConnectUseCase = mockk(relaxed = true)
    private val enableNodePositionBroadcastReady: EnableNodePositionBroadcastReadyUseCase = mockk(relaxed = true)
    private val disableNodePositionBroadcast: DisableNodePositionBroadcastUseCase = mockk(relaxed = true)
    private val observeEmergencyMode: ObserveEmergencyModeUseCase = mockk()
    private val triggerEmergency: TriggerEmergencyUseCase = mockk(relaxed = true)
    private val cancelEmergency: CancelEmergencyUseCase = mockk(relaxed = true)
    private val checkContourSync: CheckContourSyncUseCase = mockk(relaxed = true)
    private val syncStateRepository: ContourSyncStateRepository = mockk(relaxed = true)
    private val rebootNode: RebootNodeUseCase = mockk(relaxed = true)
    private val rebootStateRepository: RebootStateRepository = mockk(relaxed = true)
    private val observeGpsBroadcastEnabled: ObserveGpsBroadcastEnabledUseCase = mockk()
    private val setGpsBroadcastEnabled: SetGpsBroadcastEnabledUseCase = mockk(relaxed = true)
    private val observeDeviceConfig: ObserveDeviceConfigUseCase = mockk()
    private val writeOwner: WriteOwnerUseCase = mockk(relaxed = true)

    private val contoursFlow = MutableStateFlow<List<Contour>>(emptyList())
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
        every { observeContours.invoke(any()) } returns contoursFlow
        every { observeNodeChannels.invoke(any()) } returns nodeChannelsFlow
        every { observeConnectionStatus.invoke(any()) } returns connectionStatusFlow
        every { channelSlotResolver.hashToSlot } returns emptyMap()
        every { resolveSlot.invoke(any(), any()) } returns SlotResolution.NoFreeSlot
        every { observeEmergencyMode.invoke() } returns flowOf(false)
        every { observeGpsBroadcastEnabled.invoke() } returns flowOf(true)
        every { observeDeviceConfig.invoke(any()) } returns flowOf(null)
        every { syncStateRepository.syncRequired } returns MutableStateFlow(false)
        viewModel = UserSettingsViewModel(
            observeAppUser = observeAppUser,
            saveAppUser = saveAppUser,
            observeContours = observeContours,
            saveContour = saveContour,
            deleteContour = deleteContour,
            setContourActive = setContourActive,
            observeNodeChannels = observeNodeChannels,
            writeChannel = writeChannel,
            resolveSlot = resolveSlot,
            observeConnectionStatus = observeConnectionStatus,
            channelSlotResolver = channelSlotResolver,
            syncContoursOnConnect = syncContoursOnConnect,
            enableNodePositionBroadcastReady = enableNodePositionBroadcastReady,
            disableNodePositionBroadcast = disableNodePositionBroadcast,
            observeEmergencyMode = observeEmergencyMode,
            triggerEmergency = triggerEmergency,
            cancelEmergency = cancelEmergency,
            checkContourSync = checkContourSync,
            syncStateRepository = syncStateRepository,
            rebootNode = rebootNode,
            rebootStateRepository = rebootStateRepository,
            observeGpsBroadcastEnabled = observeGpsBroadcastEnabled,
            setGpsBroadcastEnabled = setGpsBroadcastEnabled,
            observeDeviceConfig = observeDeviceConfig,
            writeOwner = writeOwner,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(android.util.Base64::class)
    }

    private fun makeContour(
        name: String,
        psk: ByteArray,
        id: String = UUID.randomUUID().toString(),
        isActive: Boolean = true,
    ): Contour {
        val pskBase64 = Base64.getEncoder().encodeToString(psk)
        val hash = ContourHash.compute(name, psk)
        return Contour(
            id = ContourId(id),
            name = name,
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = isActive,
            transport = ContourTransport(
                meshtastic = MeshtasticChannel(psk = pskBase64, channelHash = hash),
            ),
        )
    }

    private val connectedStatus = MeshConnectionStatus.Connected(
        nodeId = "!aabbccdd",
        shortName = "ТЕ",
        rssi = -70,
        batteryLevel = 80,
    )

    private fun populateCache(
        contours: List<Contour>,
        nodeSlots: List<NodeChannelSlot> = emptyList(),
        status: MeshConnectionStatus = MeshConnectionStatus.Disconnected,
    ) {
        contoursFlow.value = contours
        nodeChannelsFlow.value = nodeSlots
        connectionStatusFlow.value = status
    }

    // ── onPushToNode ──────────────────────────────────────────────────────────

    @Test
    fun `onPushToNode not connected — emits NotConnected event`() = runTest(testDispatcher) {
        val contour = makeContour("Alpha", byteArrayOf(0x01))
        populateCache(listOf(contour), status = MeshConnectionStatus.Disconnected)

        viewModel.onPushToNode(contour.id)

        assertEquals(NodeWriteEvent.NotConnected, viewModel.uiState.value.nodeWriteEvent)
    }

    @Test
    fun `onPushToNode connected AlreadySynced — no writeChannel call Sent event`() = runTest(testDispatcher) {
        val contour = makeContour("Alpha", byteArrayOf(0x01))
        every { resolveSlot.invoke(any(), any()) } returns SlotResolution.AlreadySynced(2)
        populateCache(listOf(contour), status = connectedStatus)

        viewModel.onPushToNode(contour.id)

        verify(exactly = 0) { writeChannel.invoke(any(), any(), any()) }
        assertEquals(NodeWriteEvent.Sent("Alpha"), viewModel.uiState.value.nodeWriteEvent)
    }

    @Test
    fun `onPushToNode connected FreeSlot — calls writeChannel with correct slot and emits Sent`() = runTest(testDispatcher) {
        val contour = makeContour("Alpha", byteArrayOf(0x01))
        // Populate with default NoFreeSlot so auto-sync does not write
        populateCache(listOf(contour), status = connectedStatus)
        // Now configure FreeSlot for the manual push
        every { resolveSlot.invoke(any(), any()) } returns SlotResolution.FreeSlot(3)

        viewModel.onPushToNode(contour.id)

        verify(exactly = 1) { writeChannel.invoke(eq(3), eq("Alpha"), any()) }
        assertEquals(NodeWriteEvent.Sent("Alpha"), viewModel.uiState.value.nodeWriteEvent)
    }

    @Test
    fun `onPushToNode connected NoFreeSlot — emits NoFreeSlot event`() = runTest(testDispatcher) {
        val contour = makeContour("Alpha", byteArrayOf(0x01))
        every { resolveSlot.invoke(any(), any()) } returns SlotResolution.NoFreeSlot
        populateCache(listOf(contour), status = connectedStatus)

        viewModel.onPushToNode(contour.id)

        assertEquals(NodeWriteEvent.NoFreeSlot, viewModel.uiState.value.nodeWriteEvent)
    }

    // ── onDeleteFromNode ──────────────────────────────────────────────────────

    @Test
    fun `onDeleteFromNode slot 0 — writeChannel not called`() = runTest(testDispatcher) {
        val psk = byteArrayOf(0x02)
        val contour = makeContour("Primary", psk)
        val hash = ContourHash.compute("Primary", psk)
        every { channelSlotResolver.hashToSlot } returns mapOf(hash to 0)
        populateCache(listOf(contour))

        viewModel.onDeleteFromNode(contour.id)

        verify(exactly = 0) { writeChannel.invoke(any(), any(), any()) }
    }

    @Test
    fun `onDeleteFromNode slot 3 — calls writeChannel with empty name and psk`() = runTest(testDispatcher) {
        val psk = byteArrayOf(0x03)
        val contour = makeContour("Bravo", psk)
        val hash = ContourHash.compute("Bravo", psk)
        every { channelSlotResolver.hashToSlot } returns mapOf(hash to 3)
        populateCache(listOf(contour))

        viewModel.onDeleteFromNode(contour.id)

        verify(exactly = 1) { writeChannel.invoke(eq(3), eq(""), eq("")) }
    }

    // ── onToggleActive ────────────────────────────────────────────────────────

    @Test
    fun `onToggleActive enables — setContourActive called with isActive true`() = runTest(testDispatcher) {
        val contour = makeContour("Gamma", byteArrayOf(0x04), isActive = false)
        populateCache(listOf(contour))

        viewModel.onToggleActive(contour.id, true)
        runCurrent()

        coVerify(exactly = 1) { setContourActive.invoke(contour.id, true) }
    }

    @Test
    fun `onToggleActive disables — setContourActive called with isActive false`() = runTest(testDispatcher) {
        val contour = makeContour("Gamma", byteArrayOf(0x04), isActive = true)
        populateCache(listOf(contour))

        viewModel.onToggleActive(contour.id, false)
        runCurrent()

        coVerify(exactly = 1) { setContourActive.invoke(contour.id, false) }
    }

    // ── onConnected geo config ────────────────────────────────────────────────

    private fun makeEmergencyContour(isActive: Boolean) = Contour(
        id = DefaultContour.ID,
        name = DefaultContour.DISPLAY_NAME,
        description = null,
        expiration = null,
        exclusivityTime = null,
        isActive = isActive,
        transport = ContourTransport(
            meshtastic = MeshtasticChannel(psk = DefaultContour.OPEN_PSK, channelHash = DefaultContour.CHANNEL_HASH),
        ),
    )

    @Test
    fun `Connected with Emergency isActive=false — enableNodePositionBroadcastReady called`() = runTest(testDispatcher) {
        contoursFlow.value = listOf(makeEmergencyContour(isActive = false))
        connectionStatusFlow.value = connectedStatus
        runCurrent()

        verify(exactly = 1) { enableNodePositionBroadcastReady.invoke() }
        verify(exactly = 0) { disableNodePositionBroadcast.invoke() }
    }

    @Test
    fun `Connected with Emergency isActive=true — disableNodePositionBroadcast called`() = runTest(testDispatcher) {
        contoursFlow.value = listOf(makeEmergencyContour(isActive = true))
        connectionStatusFlow.value = connectedStatus
        runCurrent()

        verify(exactly = 1) { disableNodePositionBroadcast.invoke() }
        verify(exactly = 0) { enableNodePositionBroadcastReady.invoke() }
    }

    @Test
    fun `Connected with no Emergency contour — enableNodePositionBroadcastReady called (default false)`() = runTest(testDispatcher) {
        contoursFlow.value = emptyList()
        connectionStatusFlow.value = connectedStatus
        runCurrent()

        verify(exactly = 1) { enableNodePositionBroadcastReady.invoke() }
    }

    // ── onNodeWriteEventConsumed ──────────────────────────────────────────────

    @Test
    fun `onNodeWriteEventConsumed — clears nodeWriteEvent`() = runTest(testDispatcher) {
        val contour = makeContour("Alpha", byteArrayOf(0x01))
        populateCache(listOf(contour), status = MeshConnectionStatus.Disconnected)
        viewModel.onPushToNode(contour.id)
        assertEquals(NodeWriteEvent.NotConnected, viewModel.uiState.value.nodeWriteEvent)

        viewModel.onNodeWriteEventConsumed()

        assertNull(viewModel.uiState.value.nodeWriteEvent)
    }
}
