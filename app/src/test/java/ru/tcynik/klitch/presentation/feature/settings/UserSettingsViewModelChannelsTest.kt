package ru.tcynik.klitch.presentation.feature.settings

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
import ru.tcynik.klitch.logger.NoOpLogger
import ru.tcynik.klitch.domain.channel.ChannelSlotResolver
import ru.tcynik.klitch.domain.channel.model.Contour
import ru.tcynik.klitch.domain.channel.model.ContourHash
import ru.tcynik.klitch.domain.channel.model.ContourId
import ru.tcynik.klitch.domain.channel.model.ContourTransport
import ru.tcynik.klitch.domain.channel.model.MeshtasticChannel
import ru.tcynik.klitch.domain.channel.model.NodeChannelSlot
import ru.tcynik.klitch.domain.channel.usecase.DeleteContourUseCase
import ru.tcynik.klitch.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.klitch.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.klitch.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.klitch.domain.channel.usecase.SaveContourUseCase
import ru.tcynik.klitch.domain.channel.model.DefaultActiveContour
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.channel.usecase.SetContourActiveUseCase
import ru.tcynik.klitch.domain.channel.usecase.SetPrimaryContourUseCase
import ru.tcynik.klitch.domain.channel.usecase.SlotResolution
import ru.tcynik.klitch.domain.channel.usecase.ConfirmChannelSyncUseCase
import ru.tcynik.klitch.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.klitch.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.klitch.domain.emergency.usecase.CancelEmergencyUseCase
import ru.tcynik.klitch.domain.emergency.usecase.ObserveEmergencyModeUseCase
import ru.tcynik.klitch.domain.emergency.usecase.TriggerEmergencyUseCase
import ru.tcynik.klitch.domain.mesh.usecase.DisconnectFromMeshUseCase
import ru.tcynik.klitch.domain.mesh.usecase.DisableNodePositionBroadcastUseCase
import ru.tcynik.klitch.domain.mesh.usecase.BeginSettingsEditUseCase
import ru.tcynik.klitch.domain.mesh.usecase.CommitSettingsEditUseCase
import ru.tcynik.klitch.domain.mesh.usecase.PrepareNodeForAppDrivenBroadcastUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveGpsBroadcastEnabledUseCase
import ru.tcynik.klitch.domain.mesh.repository.RebootStateRepository
import ru.tcynik.klitch.domain.mesh.usecase.RebootNodeUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ReconnectAfterNodeRebootUseCase
import ru.tcynik.klitch.domain.mesh.usecase.SetGpsBroadcastEnabledUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.klitch.domain.mesh.usecase.CheckOwnPkcHealthUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RefreshNodePublicKeysUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RegeneratePkcKeysUseCase
import ru.tcynik.klitch.domain.user.model.AppUser
import ru.tcynik.klitch.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.klitch.domain.user.usecase.SaveAppUserUseCase
import ru.tcynik.klitch.presentation.feature.settings.models.NodeWriteEvent
import java.util.Base64
import java.util.UUID

class UserSettingsViewModelChannelsTest {

    private val observeAppUser: ObserveAppUserUseCase = mockk()
    private val saveAppUser: SaveAppUserUseCase = mockk(relaxed = true)
    private val observeContours: ObserveContoursUseCase = mockk()
    private val saveContour: SaveContourUseCase = mockk(relaxed = true)
    private val deleteContour: DeleteContourUseCase = mockk(relaxed = true)
    private val setContourActive: SetContourActiveUseCase = mockk(relaxed = true)
    private val setPrimaryContour: SetPrimaryContourUseCase = mockk(relaxed = true)
    private val contourRepository: ContourRepository = mockk()
    private val observeNodeChannels: ObserveNodeChannelsUseCase = mockk()
    private val beginSettingsEdit: BeginSettingsEditUseCase = mockk(relaxed = true)
    private val commitSettingsEdit: CommitSettingsEditUseCase = mockk(relaxed = true)
    private val writeChannel: WriteChannelUseCase = mockk(relaxed = true)
    private val resolveSlot: ResolveChannelSlotUseCase = mockk()
    private val observeConnectionStatus: ObserveConnectionStatusUseCase = mockk()
    private val channelSlotResolver: ChannelSlotResolver = mockk()
    private val confirmChannelSync: ConfirmChannelSyncUseCase = mockk(relaxed = true)
    private val prepareNodeForAppDrivenBroadcast: PrepareNodeForAppDrivenBroadcastUseCase = mockk(relaxed = true)
    private val disableNodePositionBroadcast: DisableNodePositionBroadcastUseCase = mockk(relaxed = true)
    private val observeEmergencyMode: ObserveEmergencyModeUseCase = mockk()
    private val triggerEmergency: TriggerEmergencyUseCase = mockk(relaxed = true)
    private val cancelEmergency: CancelEmergencyUseCase = mockk(relaxed = true)
    private val checkContourSync: CheckNodeSyncUseCase = mockk(relaxed = true)
    private val syncStateRepository: ContourSyncStateRepository = mockk(relaxed = true)
    private val disconnectFromMesh: DisconnectFromMeshUseCase = mockk(relaxed = true)
    private val rebootNode: RebootNodeUseCase = mockk(relaxed = true)
    private val reconnectAfterNodeReboot: ReconnectAfterNodeRebootUseCase = mockk(relaxed = true)
    private val rebootStateRepository: RebootStateRepository = mockk(relaxed = true)
    private val observeGpsBroadcastEnabled: ObserveGpsBroadcastEnabledUseCase = mockk()
    private val setGpsBroadcastEnabled: SetGpsBroadcastEnabledUseCase = mockk(relaxed = true)
    private val observeDeviceConfig: ObserveDeviceConfigUseCase = mockk()
    private val writeOwner: WriteOwnerUseCase = mockk(relaxed = true)
    private val checkOwnPkcHealth: CheckOwnPkcHealthUseCase = mockk()
    private val refreshNodePublicKeys: RefreshNodePublicKeysUseCase = mockk(relaxed = true)
    private val regeneratePkcKeys: RegeneratePkcKeysUseCase = mockk(relaxed = true)

    private val contoursFlow = MutableStateFlow<List<Contour>>(emptyList())
    private val nodeChannelsFlow = MutableStateFlow<List<NodeChannelSlot>>(emptyList())
    private val connectionStatusFlow = MutableStateFlow<MeshConnectionStatus>(MeshConnectionStatus.Disconnected)
    private val primaryIdFlow = MutableStateFlow(DefaultActiveContour.ID)
    private val emergencyModeFlow = MutableStateFlow(false)

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
        every { observeEmergencyMode.invoke() } returns emergencyModeFlow
        every { contourRepository.observePrimaryContourId() } returns primaryIdFlow
        every { observeGpsBroadcastEnabled.invoke() } returns flowOf(true)
        every { observeDeviceConfig.invoke(any()) } returns flowOf(null)
        every { syncStateRepository.syncRequired } returns MutableStateFlow(false)
        every { checkOwnPkcHealth.invoke() } returns false
        viewModel = UserSettingsViewModel(
            observeAppUser = observeAppUser,
            saveAppUser = saveAppUser,
            observeContours = observeContours,
            saveContour = saveContour,
            deleteContour = deleteContour,
            setContourActive = setContourActive,
            setPrimaryContour = setPrimaryContour,
            contourRepository = contourRepository,
            observeNodeChannels = observeNodeChannels,
            beginSettingsEdit = beginSettingsEdit,
            commitSettingsEdit = commitSettingsEdit,
            writeChannel = writeChannel,
            resolveSlot = resolveSlot,
            observeConnectionStatus = observeConnectionStatus,
            channelSlotResolver = channelSlotResolver,
            confirmChannelSync = confirmChannelSync,
            prepareNodeForAppDrivenBroadcast = prepareNodeForAppDrivenBroadcast,
            disableNodePositionBroadcast = disableNodePositionBroadcast,
            observeEmergencyMode = observeEmergencyMode,
            triggerEmergency = triggerEmergency,
            cancelEmergency = cancelEmergency,
            checkContourSync = checkContourSync,
            syncStateRepository = syncStateRepository,
            disconnectFromMesh = disconnectFromMesh,
            rebootNode = rebootNode,
            reconnectAfterNodeReboot = reconnectAfterNodeReboot,
            rebootStateRepository = rebootStateRepository,
            observeGpsBroadcastEnabled = observeGpsBroadcastEnabled,
            setGpsBroadcastEnabled = setGpsBroadcastEnabled,
            observeDeviceConfig = observeDeviceConfig,
            writeOwner = writeOwner,
            checkOwnPkcHealth = checkOwnPkcHealth,
            refreshNodePublicKeys = refreshNodePublicKeys,
            regeneratePkcKeys = regeneratePkcKeys,
            logger = NoOpLogger(),
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
        deviceName = "Meshtastic TE",
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

        coVerify(exactly = 0) { writeChannel.invoke(any(), any(), any()) }
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

        coVerify(exactly = 1) { writeChannel.invoke(eq(3), eq("Alpha"), any()) }
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

        coVerify(exactly = 0) { writeChannel.invoke(any(), any(), any()) }
    }

    @Test
    fun `onDeleteFromNode slot 3 — calls writeChannel with empty name and psk`() = runTest(testDispatcher) {
        val psk = byteArrayOf(0x03)
        val contour = makeContour("Bravo", psk)
        val hash = ContourHash.compute("Bravo", psk)
        every { channelSlotResolver.hashToSlot } returns mapOf(hash to 3)
        populateCache(listOf(contour))

        viewModel.onDeleteFromNode(contour.id)

        coVerify(exactly = 1) { writeChannel.invoke(eq(3), eq(""), eq("")) }
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
