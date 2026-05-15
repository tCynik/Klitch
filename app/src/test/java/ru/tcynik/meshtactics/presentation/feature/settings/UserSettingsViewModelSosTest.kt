package ru.tcynik.meshtactics.presentation.feature.settings

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
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
import ru.tcynik.meshtactics.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.meshtactics.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.CheckOwnPkcHealthUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.DisableNodePositionBroadcastUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.EnableNodePositionBroadcastReadyUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveGpsBroadcastEnabledUseCase
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.RebootNodeUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RefreshNodePublicKeysUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RegeneratePkcKeysUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.SetGpsBroadcastEnabledUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.user.usecase.SaveAppUserUseCase

class UserSettingsViewModelSosTest {

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
    private val checkContourSync: CheckNodeSyncUseCase = mockk(relaxed = true)
    private val syncStateRepository: ContourSyncStateRepository = mockk(relaxed = true)
    private val rebootNode: RebootNodeUseCase = mockk(relaxed = true)
    private val rebootStateRepository: RebootStateRepository = mockk(relaxed = true)
    private val observeGpsBroadcastEnabled: ObserveGpsBroadcastEnabledUseCase = mockk()
    private val setGpsBroadcastEnabled: SetGpsBroadcastEnabledUseCase = mockk(relaxed = true)
    private val observeDeviceConfig: ObserveDeviceConfigUseCase = mockk()
    private val writeOwner: WriteOwnerUseCase = mockk(relaxed = true)
    private val checkOwnPkcHealth: CheckOwnPkcHealthUseCase = mockk()
    private val refreshNodePublicKeys: RefreshNodePublicKeysUseCase = mockk(relaxed = true)
    private val regeneratePkcKeys: RegeneratePkcKeysUseCase = mockk(relaxed = true)

    private val emergencyModeFlow = MutableStateFlow(false)
    private val connectionStatusFlow = MutableStateFlow<MeshConnectionStatus>(MeshConnectionStatus.Disconnected)

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: UserSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } returns "AAEC"
        every { observeAppUser.invoke(any()) } returns flowOf(AppUser(""))
        every { observeContours.invoke(any()) } returns MutableStateFlow(emptyList())
        every { observeNodeChannels.invoke(any()) } returns MutableStateFlow<List<NodeChannelSlot>>(emptyList())
        every { observeConnectionStatus.invoke(any()) } returns connectionStatusFlow
        every { channelSlotResolver.hashToSlot } returns emptyMap()
        every { resolveSlot.invoke(any(), any()) } returns SlotResolution.NoFreeSlot
        every { observeEmergencyMode.invoke() } returns emergencyModeFlow
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

    // ── emergencyMode state ───────────────────────────────────────────────────

    @Test
    fun `emergencyMode false по умолчанию`() = runTest(testDispatcher) {
        assertFalse(viewModel.uiState.value.emergencyMode)
    }

    @Test
    fun `emergencyMode обновляется при эмите из ObserveEmergencyModeUseCase`() = runTest(testDispatcher) {
        emergencyModeFlow.value = true

        assertTrue(viewModel.uiState.value.emergencyMode)
    }

    @Test
    fun `emergencyMode сбрасывается при повторном эмите false`() = runTest(testDispatcher) {
        emergencyModeFlow.value = true
        emergencyModeFlow.value = false

        assertFalse(viewModel.uiState.value.emergencyMode)
    }

    // ── onSosClick ────────────────────────────────────────────────────────────

    @Test
    fun `onSosClick при неактивном режиме открывает диалог подтверждения тревоги`() = runTest(testDispatcher) {
        emergencyModeFlow.value = false

        viewModel.onSosClick()

        assertTrue(viewModel.uiState.value.showTriggerDialog)
        assertFalse(viewModel.uiState.value.showCancelDialog)
    }

    @Test
    fun `onSosClick при активном режиме открывает диалог отмены тревоги`() = runTest(testDispatcher) {
        emergencyModeFlow.value = true

        viewModel.onSosClick()

        assertTrue(viewModel.uiState.value.showCancelDialog)
        assertFalse(viewModel.uiState.value.showTriggerDialog)
    }

    // ── dismiss dialogs ───────────────────────────────────────────────────────

    @Test
    fun `onDismissTriggerDialog скрывает диалог`() = runTest(testDispatcher) {
        viewModel.onSosClick()
        assertTrue(viewModel.uiState.value.showTriggerDialog)

        viewModel.onDismissTriggerDialog()

        assertFalse(viewModel.uiState.value.showTriggerDialog)
    }

    @Test
    fun `onDismissCancelDialog скрывает диалог`() = runTest(testDispatcher) {
        emergencyModeFlow.value = true
        viewModel.onSosClick()
        assertTrue(viewModel.uiState.value.showCancelDialog)

        viewModel.onDismissCancelDialog()

        assertFalse(viewModel.uiState.value.showCancelDialog)
    }

    // ── onTriggerEmergencyConfirm ─────────────────────────────────────────────

    @Test
    fun `onTriggerEmergencyConfirm закрывает диалог`() = runTest(testDispatcher) {
        viewModel.onSosClick()

        viewModel.onTriggerEmergencyConfirm()
        runCurrent()

        assertFalse(viewModel.uiState.value.showTriggerDialog)
    }

    @Test
    fun `onTriggerEmergencyConfirm вызывает TriggerEmergencyUseCase`() = runTest(testDispatcher) {
        viewModel.onTriggerEmergencyConfirm()
        runCurrent()

        coVerify(exactly = 1) { triggerEmergency.invoke() }
    }

    @Test
    fun `onTriggerEmergencyConfirm устанавливает emergencyEvent Triggered`() = runTest(testDispatcher) {
        viewModel.onTriggerEmergencyConfirm()
        runCurrent()

        assertEquals(EmergencyEvent.Triggered, viewModel.uiState.value.emergencyEvent)
    }

    // ── onCancelEmergencyConfirm ──────────────────────────────────────────────

    @Test
    fun `onCancelEmergencyConfirm закрывает диалог`() = runTest(testDispatcher) {
        emergencyModeFlow.value = true
        viewModel.onSosClick()

        viewModel.onCancelEmergencyConfirm()
        runCurrent()

        assertFalse(viewModel.uiState.value.showCancelDialog)
    }

    @Test
    fun `onCancelEmergencyConfirm вызывает CancelEmergencyUseCase`() = runTest(testDispatcher) {
        viewModel.onCancelEmergencyConfirm()
        runCurrent()

        coVerify(exactly = 1) { cancelEmergency.invoke() }
    }

    // ── onEmergencyEventConsumed ──────────────────────────────────────────────

    @Test
    fun `onEmergencyEventConsumed очищает emergencyEvent`() = runTest(testDispatcher) {
        viewModel.onTriggerEmergencyConfirm()
        runCurrent()
        assertNotNull(viewModel.uiState.value.emergencyEvent)

        viewModel.onEmergencyEventConsumed()

        assertNull(viewModel.uiState.value.emergencyEvent)
    }
}
