package ru.tcynik.meshtactics.presentation.feature.settings

import io.mockk.coEvery
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.NodeSyncResult
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
import ru.tcynik.meshtactics.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.meshtactics.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.DeleteContourUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SaveContourUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SetContourActiveUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SlotResolution
import ru.tcynik.meshtactics.domain.channel.usecase.SyncContoursOnConnectUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.CancelEmergencyUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.ObserveEmergencyModeUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.TriggerEmergencyUseCase
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.usecase.DisconnectFromMeshUseCase
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
import ru.tcynik.meshtactics.domain.mesh.usecase.CheckOwnPkcHealthUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RefreshNodePublicKeysUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RegeneratePkcKeysUseCase
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.user.usecase.SaveAppUserUseCase
import java.util.Base64
import java.util.UUID

class UserSettingsViewModelSyncDialogTest {

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
    private val checkContourSync: CheckNodeSyncUseCase = mockk()
    private val syncStateRepository: ContourSyncStateRepository = mockk(relaxed = true)
    private val disconnectFromMesh: DisconnectFromMeshUseCase = mockk(relaxed = true)
    private val rebootNode: RebootNodeUseCase = mockk(relaxed = true)
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
    private val syncRequiredFlow = MutableStateFlow(false)

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: UserSettingsViewModel

    private val connectedStatus = MeshConnectionStatus.Connected(
        nodeId = "!aabbccdd",
        shortName = "ТЕ",
        deviceName = "Meshtastic TE",
        rssi = -70,
        batteryLevel = 80,
    )

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
        every { syncStateRepository.syncRequired } returns syncRequiredFlow
        every { checkOwnPkcHealth.invoke() } returns false
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
            disconnectFromMesh = disconnectFromMesh,
            rebootNode = rebootNode,
            rebootStateRepository = rebootStateRepository,
            observeGpsBroadcastEnabled = observeGpsBroadcastEnabled,
            setGpsBroadcastEnabled = setGpsBroadcastEnabled,
            observeDeviceConfig = observeDeviceConfig,
            writeOwner = writeOwner,
            checkOwnPkcHealth = checkOwnPkcHealth,
            refreshNodePublicKeys = refreshNodePublicKeys,
            regeneratePkcKeys = regeneratePkcKeys,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(android.util.Base64::class)
    }

    private fun makeContour(name: String, psk: ByteArray, isActive: Boolean = true): Contour {
        val pskBase64 = Base64.getEncoder().encodeToString(psk)
        val hash = ContourHash.compute(name, psk)
        return Contour(
            id = ContourId(UUID.randomUUID().toString()),
            name = name,
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = isActive,
            transport = ContourTransport(meshtastic = MeshtasticChannel(psk = pskBase64, channelHash = hash)),
        )
    }

    // ── onToggleActive → showSyncDialog ───────────────────────────────────────

    @Test
    fun `onToggleActive isActive=true подключён NeedsSync — showSyncDialog=true`() = runTest(testDispatcher) {
        val contour = makeContour("Alpha", byteArrayOf(0x01), isActive = false)
        contoursFlow.value = listOf(contour)
        connectionStatusFlow.value = connectedStatus
        coEvery { checkContourSync.invoke() } returns NodeSyncResult.NeedsSync

        viewModel.onToggleActive(contour.id, true)
        runCurrent()

        assertTrue(viewModel.uiState.value.showSyncDialog)
    }

    @Test
    fun `onToggleActive isActive=true подключён InSync — showSyncDialog не меняется`() = runTest(testDispatcher) {
        val contour = makeContour("Alpha", byteArrayOf(0x01), isActive = false)
        contoursFlow.value = listOf(contour)
        connectionStatusFlow.value = connectedStatus
        coEvery { checkContourSync.invoke() } returns NodeSyncResult.InSync

        viewModel.onToggleActive(contour.id, true)
        runCurrent()

        assertFalse(viewModel.uiState.value.showSyncDialog)
    }

    @Test
    fun `onToggleActive isActive=true не подключён — checkContourSync не вызывается`() = runTest(testDispatcher) {
        val contour = makeContour("Alpha", byteArrayOf(0x01), isActive = false)
        contoursFlow.value = listOf(contour)
        connectionStatusFlow.value = MeshConnectionStatus.Disconnected

        viewModel.onToggleActive(contour.id, true)
        runCurrent()

        coVerify(exactly = 0) { checkContourSync.invoke() }
        assertFalse(viewModel.uiState.value.showSyncDialog)
    }

    @Test
    fun `onToggleActive isActive=false — checkContourSync не вызывается`() = runTest(testDispatcher) {
        val contour = makeContour("Alpha", byteArrayOf(0x01), isActive = true)
        contoursFlow.value = listOf(contour)
        connectionStatusFlow.value = connectedStatus

        viewModel.onToggleActive(contour.id, false)
        runCurrent()

        coVerify(exactly = 0) { checkContourSync.invoke() }
    }

    // ── onConfirmChannelSync ──────────────────────────────────────────────────

    @Test
    fun `onConfirmChannelSync — скрывает диалог`() = runTest(testDispatcher) {
        val contour = makeContour("Alpha", byteArrayOf(0x01), isActive = false)
        contoursFlow.value = listOf(contour)
        connectionStatusFlow.value = connectedStatus
        coEvery { checkContourSync.invoke() } returns NodeSyncResult.NeedsSync
        viewModel.onToggleActive(contour.id, true)
        runCurrent()
        assertTrue(viewModel.uiState.value.showSyncDialog)

        viewModel.onConfirmChannelSync()
        runCurrent()

        assertFalse(viewModel.uiState.value.showSyncDialog)
    }

    @Test
    fun `onConfirmChannelSync — вызывает syncContoursOnConnect и rebootNode`() = runTest(testDispatcher) {
        viewModel.onConfirmChannelSync()
        runCurrent()

        coVerify(exactly = 1) { syncContoursOnConnect.invoke() }
        verify(exactly = 1) { rebootNode.invoke() }
    }

    @Test
    fun `onConfirmChannelSync — вызывает syncStateRepository clear`() = runTest(testDispatcher) {
        viewModel.onConfirmChannelSync()
        runCurrent()

        verify(exactly = 1) { syncStateRepository.clear() }
    }

    // ── onDismissChannelSync ──────────────────────────────────────────────────

    @Test
    fun `onDismissChannelSync — скрывает диалог`() = runTest(testDispatcher) {
        val contour = makeContour("Alpha", byteArrayOf(0x01), isActive = false)
        contoursFlow.value = listOf(contour)
        connectionStatusFlow.value = connectedStatus
        coEvery { checkContourSync.invoke() } returns NodeSyncResult.NeedsSync
        viewModel.onToggleActive(contour.id, true)
        runCurrent()
        assertTrue(viewModel.uiState.value.showSyncDialog)

        viewModel.onDismissChannelSync()

        assertFalse(viewModel.uiState.value.showSyncDialog)
    }

    @Test
    fun `onDismissChannelSync — устанавливает syncRequired через репозиторий`() = runTest(testDispatcher) {
        viewModel.onDismissChannelSync()
        runCurrent()

        verify(exactly = 1) { syncStateRepository.setSyncRequired(true) }
    }

    @Test
    fun `onDismissChannelSync — отключается от ноды`() = runTest(testDispatcher) {
        viewModel.onDismissChannelSync()
        runCurrent()

        coVerify(exactly = 1) { disconnectFromMesh.invoke(any()) }
    }
}
