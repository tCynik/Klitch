package ru.tcynik.meshtactics.presentation.feature.network

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.model.NodeSyncResult
import ru.tcynik.meshtactics.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.meshtactics.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SyncContoursOnConnectUseCase
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceParams
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.DisconnectFromMeshUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveMeshNodesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveOurNodeUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RebootNodeUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveNetworkEnabledUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.SetNetworkEnabledUseCase
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.user.usecase.SaveAppUserUseCase
import ru.tcynik.meshtactics.presentation.feature.network.state.models.PendingAction

class NetworkViewModelCallsignGateTest {

    private val observeConnectionStatus: ObserveConnectionStatusUseCase = mockk()
    private val scanDevices: ScanMeshDevicesUseCase = mockk(relaxed = true)
    private val connectToDevice: ConnectToMeshDeviceUseCase = mockk(relaxed = true)
    private val disconnectFromMesh: DisconnectFromMeshUseCase = mockk(relaxed = true)
    private val observeNodes: ObserveMeshNodesUseCase = mockk()
    private val observeOurNode: ObserveOurNodeUseCase = mockk()
    private val checkContourSync: CheckNodeSyncUseCase = mockk(relaxed = true)
    private val syncContoursOnConnect: SyncContoursOnConnectUseCase = mockk(relaxed = true)
    private val rebootNode: RebootNodeUseCase = mockk(relaxed = true)
    private val syncStateRepository: ContourSyncStateRepository = mockk(relaxed = true)
    private val rebootStateRepository: RebootStateRepository = mockk(relaxed = true)
    private val observeAppUser: ObserveAppUserUseCase = mockk()
    private val saveAppUser: SaveAppUserUseCase = mockk(relaxed = true)
    private val observeNetworkEnabled: ObserveNetworkEnabledUseCase = mockk()
    private val setNetworkEnabled: SetNetworkEnabledUseCase = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)

    private val appUserFlow = MutableStateFlow(AppUser(displayName = ""))
    private val networkEnabledFlow = MutableStateFlow(true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: NetworkViewModel

    private val deviceAddress = "AA:BB:CC:DD:EE:FF"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeConnectionStatus.invoke(any()) } returns flowOf(MeshConnectionStatus.Disconnected)
        every { observeNodes.invoke(any()) } returns flowOf(emptyList())
        every { observeOurNode.invoke(any()) } returns flowOf(null)
        every { rebootStateRepository.isRebooting } returns MutableStateFlow(false)
        every { observeAppUser.invoke(any()) } returns appUserFlow
        every { observeNetworkEnabled.invoke(any()) } returns networkEnabledFlow
        coEvery { connectToDevice.invoke(any()) } returns Unit
        coEvery { saveAppUser.invoke(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = NetworkViewModel(
            observeConnectionStatus = observeConnectionStatus,
            scanDevices = scanDevices,
            connectToDevice = connectToDevice,
            disconnectFromMesh = disconnectFromMesh,
            observeNodes = observeNodes,
            observeOurNode = observeOurNode,
            checkContourSync = checkContourSync,
            syncContoursOnConnect = syncContoursOnConnect,
            rebootNode = rebootNode,
            syncStateRepository = syncStateRepository,
            rebootStateRepository = rebootStateRepository,
            observeAppUser = observeAppUser,
            saveAppUser = saveAppUser,
            observeNetworkEnabled = observeNetworkEnabled,
            setNetworkEnabled = setNetworkEnabled,
            logger = logger,
        )
    }

    @Test
    fun `пустой позывной при входе — показывается диалог`() = runTest(testDispatcher) {
        appUserFlow.value = AppUser(displayName = "")
        createViewModel()
        runCurrent()
        assertNotNull(viewModel.uiState.value.callsignGateDialog)
    }

    @Test
    fun `непустой позывной при Connect — connect вызывается напрямую`() = runTest(testDispatcher) {
        appUserFlow.value = AppUser(displayName = "Alpha")
        createViewModel()
        runCurrent()
        viewModel.onConnectClick(deviceAddress)
        runCurrent()
        coVerify { connectToDevice.invoke(ConnectToMeshDeviceParams(deviceAddress, deviceAddress)) }
        assertNull(viewModel.uiState.value.callsignGateDialog)
    }

    @Test
    fun `пустой позывной при Connect — диалог с pendingConnect без connect`() = runTest(testDispatcher) {
        appUserFlow.value = AppUser(displayName = "")
        createViewModel()
        runCurrent()
        viewModel.onConnectClick(deviceAddress)
        runCurrent()
        val dialog = viewModel.uiState.value.callsignGateDialog
        assertNotNull(dialog)
        assertTrue(dialog!!.pendingAction is PendingAction.Connect)
        assertEquals(deviceAddress, (dialog.pendingAction as PendingAction.Connect).address)
        coVerify(exactly = 0) { connectToDevice.invoke(any()) }
    }

    @Test
    fun `onCallsignConfirmed сохраняет пользователя и подключается`() = runTest(testDispatcher) {
        appUserFlow.value = AppUser(displayName = "")
        createViewModel()
        runCurrent()
        viewModel.onConnectClick(deviceAddress)
        runCurrent()
        viewModel.onCallsignInput("Bravo")
        viewModel.onCallsignConfirmed()
        runCurrent()
        coVerify { saveAppUser.invoke(AppUser(displayName = "Bravo")) }
        coVerify { connectToDevice.invoke(ConnectToMeshDeviceParams(deviceAddress, deviceAddress)) }
        assertNull(viewModel.uiState.value.callsignGateDialog)
    }

    @Test
    fun `пустой позывной при Scan — диалог с pendingScan без скана`() = runTest(testDispatcher) {
        appUserFlow.value = AppUser(displayName = "")
        createViewModel()
        runCurrent()
        viewModel.onCallsignDismissed()
        runCurrent()
        viewModel.onScanClick()
        runCurrent()
        val dialog = viewModel.uiState.value.callsignGateDialog
        assertNotNull(dialog)
        assertEquals(PendingAction.Scan, dialog!!.pendingAction)
    }

    @Test
    fun `scan blocked when network disabled`() = runTest(testDispatcher) {
        appUserFlow.value = AppUser(displayName = "Alpha")
        createViewModel()
        runCurrent()
        networkEnabledFlow.value = false
        runCurrent()
        viewModel.onScanClick()
        runCurrent()
        coVerify(exactly = 0) { scanDevices.invoke(any()) }
    }

    @Test
    fun `onCallsignDismissed закрывает диалог без connect`() = runTest(testDispatcher) {
        appUserFlow.value = AppUser(displayName = "")
        createViewModel()
        runCurrent()
        viewModel.onCallsignDismissed()
        runCurrent()
        assertNull(viewModel.uiState.value.callsignGateDialog)
        coVerify(exactly = 0) { connectToDevice.invoke(any()) }
    }

    @Test
    fun `onDismissChannelSync отключается от ноды`() = runTest(testDispatcher) {
        createViewModel()
        runCurrent()
        viewModel.onDismissChannelSync()
        runCurrent()
        coVerify(exactly = 1) { disconnectFromMesh.invoke(any()) }
    }
}
