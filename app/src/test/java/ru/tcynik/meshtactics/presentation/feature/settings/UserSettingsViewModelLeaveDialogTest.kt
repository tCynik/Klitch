package ru.tcynik.meshtactics.presentation.feature.settings

import app.cash.turbine.test
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.logger.NoOpLogger
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
import ru.tcynik.meshtactics.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.meshtactics.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.DeleteContourUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SaveContourUseCase
import ru.tcynik.meshtactics.domain.channel.model.DefaultActiveContour
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.channel.usecase.SetContourActiveUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SetPrimaryContourUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SlotResolution
import ru.tcynik.meshtactics.domain.channel.usecase.ConfirmChannelSyncUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.CancelEmergencyUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.ObserveEmergencyModeUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.TriggerEmergencyUseCase
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceConfigModel
import ru.tcynik.meshtactics.domain.mesh.usecase.DisconnectFromMeshUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.DisableNodePositionBroadcastUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.BeginSettingsEditUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.CommitSettingsEditUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.PrepareNodeForAppDrivenBroadcastUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveGpsBroadcastEnabledUseCase
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.RebootNodeUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ReconnectAfterNodeRebootUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.SetGpsBroadcastEnabledUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.CheckOwnPkcHealthUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RefreshNodePublicKeysUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RegeneratePkcKeysUseCase
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.model.DISPLAY_NAME_MAX_LENGTH
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.user.usecase.SaveAppUserUseCase

class UserSettingsViewModelLeaveDialogTest {

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

    private val connectionStatusFlow = MutableStateFlow<MeshConnectionStatus>(MeshConnectionStatus.Disconnected)
    private val gpsBroadcastFlow = MutableStateFlow(true)
    private val primaryIdFlow = MutableStateFlow(DefaultActiveContour.ID)

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: UserSettingsViewModel

    private val connectedStatus = MeshConnectionStatus.Connected(
        nodeId = "!aabbccdd",
        shortName = "ТЕ",
        deviceName = "Meshtastic TE",
        rssi = -70,
        batteryLevel = 80,
    )

    private val fakeDeviceConfig = MeshDeviceConfigModel(
        longName = "Test",
        shortName = "TS",
        loraPreset = "",
        txPowerDbm = "",
        region = "",
        channels = emptyList(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } returns "AAEC"
        every { observeAppUser.invoke(any()) } returns flowOf(AppUser("Иван"))
        every { observeContours.invoke(any()) } returns MutableStateFlow(emptyList())
        every { observeNodeChannels.invoke(any()) } returns MutableStateFlow<List<NodeChannelSlot>>(emptyList())
        every { observeConnectionStatus.invoke(any()) } returns connectionStatusFlow
        every { channelSlotResolver.hashToSlot } returns emptyMap()
        every { resolveSlot.invoke(any(), any()) } returns SlotResolution.NoFreeSlot
        every { observeEmergencyMode.invoke() } returns flowOf(false)
        every { contourRepository.observePrimaryContourId() } returns primaryIdFlow
        every { observeGpsBroadcastEnabled.invoke() } returns gpsBroadcastFlow
        every { observeDeviceConfig.invoke(any()) } returns flowOf(fakeDeviceConfig)
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

    // ── onNavigateBackRequested ───────────────────────────────────────────────

    @Test
    fun `onNavigateBackRequested connected и unsaved показывает LeaveDialog`() = runTest(testDispatcher) {
        connectionStatusFlow.value = connectedStatus
        runCurrent()
        viewModel.onDisplayNameChange("Новый")
        runCurrent()

        viewModel.onNavigateBackRequested()
        runCurrent()

        assertTrue(viewModel.uiState.value.showLeaveDialog)
    }

    @Test
    fun `onNavigateBackRequested disconnected и unsaved сохраняет локально и эмитит navigateBack`() = runTest(testDispatcher) {
        viewModel.onDisplayNameChange("Новый")
        runCurrent()

        viewModel.navigateBack.test {
            viewModel.onNavigateBackRequested()
            runCurrent()
            awaitItem()
            assertFalse(viewModel.uiState.value.showLeaveDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onNavigateBackRequested без изменений эмитит navigateBack без диалога`() = runTest(testDispatcher) {
        viewModel.navigateBack.test {
            viewModel.onNavigateBackRequested()
            runCurrent()
            awaitItem()
            assertFalse(viewModel.uiState.value.showLeaveDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── onSaveAndReboot ───────────────────────────────────────────────────────

    @Test
    fun `onSaveAndReboot с пустым позывным — displayNameError без writeOwner`() = runTest(testDispatcher) {
        viewModel.onDisplayNameChange("")
        runCurrent()

        viewModel.onSaveAndReboot()
        runCurrent()

        assertTrue(viewModel.uiState.value.displayNameError)
        coVerify(exactly = 0) { writeOwner.invoke(any(), any()) }
    }

    @Test
    fun `onSaveAndReboot вызывает writeOwner и rebootNode и эмитит navigateBack`() = runTest(testDispatcher) {
        viewModel.onDisplayNameChange("Новый позывной")
        runCurrent()

        viewModel.navigateBack.test {
            viewModel.onSaveAndReboot()
            runCurrent()
            awaitItem()
            coVerify { writeOwner.invoke("Новый позывной", "TS") }
            verify { rebootNode.invoke() }
            assertFalse(viewModel.uiState.value.showLeaveDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── onDiscardAndLeave ─────────────────────────────────────────────────────

    @Test
    fun `onDiscardAndLeave сбрасывает displayName и остаётся на экране`() = runTest(testDispatcher) {
        viewModel.onDisplayNameChange("Изменено")
        runCurrent()

        viewModel.onDiscardAndLeave()
        runCurrent()

        assertEquals("Иван", viewModel.uiState.value.displayName)
        assertFalse(viewModel.uiState.value.hasUnsavedUserChanges)
        assertFalse(viewModel.uiState.value.showLeaveDialog)
    }

    // ── LengthExceededDialog ──────────────────────────────────────────────────

    @Test
    fun `onNavigateBackRequested connected & превышение длины — показывает LengthExceededDialog а не LeaveDialog`() = runTest(testDispatcher) {
        connectionStatusFlow.value = connectedStatus
        runCurrent()
        val tooLong = "A".repeat(DISPLAY_NAME_MAX_LENGTH + 1)
        viewModel.onDisplayNameChange(tooLong)
        runCurrent()

        viewModel.onNavigateBackRequested()
        runCurrent()

        assertTrue(viewModel.uiState.value.showLengthExceededDialog)
        assertFalse(viewModel.uiState.value.showLeaveDialog)
    }

    @Test
    fun `onNavigateBackRequested disconnected & превышение длины — показывает LengthExceededDialog`() = runTest(testDispatcher) {
        val tooLong = "A".repeat(DISPLAY_NAME_MAX_LENGTH + 1)
        viewModel.onDisplayNameChange(tooLong)
        runCurrent()

        viewModel.onNavigateBackRequested()
        runCurrent()

        assertTrue(viewModel.uiState.value.showLengthExceededDialog)
        assertFalse(viewModel.uiState.value.showLeaveDialog)
    }

    @Test
    fun `onLengthExceededReset сбрасывает имя до сохранённого и остаётся на экране`() = runTest(testDispatcher) {
        val tooLong = "A".repeat(DISPLAY_NAME_MAX_LENGTH + 1)
        viewModel.onDisplayNameChange(tooLong)
        runCurrent()
        viewModel.onNavigateBackRequested()
        runCurrent()

        viewModel.onLengthExceededReset()
        runCurrent()

        assertEquals("Иван", viewModel.uiState.value.displayName)
        assertFalse(viewModel.uiState.value.showLengthExceededDialog)
        assertFalse(viewModel.uiState.value.hasUnsavedUserChanges)
        coVerify(exactly = 0) { saveAppUser.invoke(any()) }
    }

    @Test
    fun `onLengthExceededDismiss закрывает диалог и остаётся на экране`() = runTest(testDispatcher) {
        val tooLong = "A".repeat(DISPLAY_NAME_MAX_LENGTH + 1)
        viewModel.onDisplayNameChange(tooLong)
        runCurrent()
        viewModel.onNavigateBackRequested()
        runCurrent()

        viewModel.onLengthExceededDismiss()
        runCurrent()

        assertFalse(viewModel.uiState.value.showLengthExceededDialog)
        assertEquals(tooLong, viewModel.uiState.value.displayName)
    }

    // ── onGpsBroadcastToggle ──────────────────────────────────────────────────

    @Test
    fun `onGpsBroadcastToggle false при подключённой ноде вызывает disableNodePositionBroadcast`() = runTest(testDispatcher) {
        connectionStatusFlow.value = connectedStatus
        runCurrent()

        viewModel.onGpsBroadcastToggle(false)
        runCurrent()

        coVerify { setGpsBroadcastEnabled.invoke(false) }
        coVerify { disableNodePositionBroadcast.invoke() }
    }

    @Test
    fun `onGpsBroadcastToggle true при подключённой ноде вызывает prepareNodeForAppDrivenBroadcast`() = runTest(testDispatcher) {
        connectionStatusFlow.value = connectedStatus
        runCurrent()

        viewModel.onGpsBroadcastToggle(true)
        runCurrent()

        coVerify { setGpsBroadcastEnabled.invoke(true) }
        coVerify { prepareNodeForAppDrivenBroadcast.invoke() }
    }

    @Test
    fun `onGpsBroadcastToggle false без подключения не вызывает disableNodePositionBroadcast`() = runTest(testDispatcher) {
        viewModel.onGpsBroadcastToggle(false)
        runCurrent()

        coVerify { setGpsBroadcastEnabled.invoke(false) }
        coVerify(exactly = 0) { disableNodePositionBroadcast.invoke() }
    }
}
