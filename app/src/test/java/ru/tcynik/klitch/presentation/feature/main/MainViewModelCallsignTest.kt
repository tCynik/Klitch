package ru.tcynik.klitch.presentation.feature.main

import androidx.lifecycle.ViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import ru.tcynik.klitch.domain.channel.model.NodeSyncResult
import ru.tcynik.klitch.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.klitch.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.klitch.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.klitch.domain.mesh.model.MeshDeviceModel
import ru.tcynik.klitch.domain.mesh.model.NodeSyncCyclePhase
import ru.tcynik.klitch.domain.mesh.repository.RebootStateRepository
import ru.tcynik.klitch.domain.mesh.usecase.ConnectToMeshDeviceParams
import ru.tcynik.klitch.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.klitch.domain.mesh.usecase.GetLastConnectedDeviceUseCase
import ru.tcynik.klitch.domain.mesh.usecase.NodeProvisioningUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveCallsignChangesUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RefreshNodePublicKeyUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveNetworkEnabledUseCase
import ru.tcynik.klitch.domain.user.model.AppUser
import ru.tcynik.klitch.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.klitch.presentation.feature.main.osd.HudStateMapper

class ConnectionViewModelCallsignTest {

    private val observeConnectionStatus: ObserveConnectionStatusUseCase = mockk()
    private val observeNetworkEnabled: ObserveNetworkEnabledUseCase = mockk()
    private val scanDevices: ScanMeshDevicesUseCase = mockk()
    private val connectToDevice: ConnectToMeshDeviceUseCase = mockk(relaxed = true)
    private val getLastConnectedDevice: GetLastConnectedDeviceUseCase = mockk()
    private val nodeProvisioning: NodeProvisioningUseCase = mockk(relaxed = true)
    private val checkNodeSync: CheckNodeSyncUseCase = mockk(relaxed = true)
    private val observeNodeChannels: ObserveNodeChannelsUseCase = mockk()
    private val syncStateRepository: ContourSyncStateRepository = mockk(relaxed = true)
    private val rebootStateRepository: RebootStateRepository = mockk(relaxed = true)
    private val observeCallsignChanges: ObserveCallsignChangesUseCase = mockk()
    private val refreshNodePublicKey: RefreshNodePublicKeyUseCase = mockk(relaxed = true)
    private val observeAppUser: ObserveAppUserUseCase = mockk()

    private val connectionStatusFlow = MutableStateFlow<MeshConnectionStatus>(MeshConnectionStatus.Disconnected)
    private val appUserFlow = MutableStateFlow(AppUser(displayName = ""))

    private val lastDevice = MeshDeviceModel(address = "AA:BB:CC:DD:EE:FF", name = "TestNode", rssi = -70)

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: ConnectionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { observeConnectionStatus.invoke(any()) } returns connectionStatusFlow
        every { observeNetworkEnabled.invoke(any()) } returns flowOf(true)
        every { scanDevices.invoke(any()) } returns flow { kotlinx.coroutines.awaitCancellation() }
        every { getLastConnectedDevice.invoke() } returns lastDevice
        every { observeNodeChannels.invoke(any()) } returns flowOf(emptyList())
        every { syncStateRepository.syncRequired } returns MutableStateFlow(false)
        every { rebootStateRepository.isRebooting } returns MutableStateFlow(false)
        every { rebootStateRepository.syncCyclePhase } returns MutableStateFlow(NodeSyncCyclePhase.Idle)
        every { observeCallsignChanges.invoke(any()) } returns emptyFlow()
        coEvery { checkNodeSync.invoke() } returns NodeSyncResult.InSync
        coEvery { connectToDevice.invoke(any()) } returns Unit
        every { observeAppUser.invoke(any()) } returns appUserFlow
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            val onCleared = ViewModel::class.java.getDeclaredMethod("onCleared")
            onCleared.isAccessible = true
            onCleared.invoke(viewModel)
        }
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = ConnectionViewModel(
            observeConnectionStatus = observeConnectionStatus,
            observeNetworkEnabled = observeNetworkEnabled,
            scanDevices = scanDevices,
            connectToDevice = connectToDevice,
            getLastConnectedDevice = getLastConnectedDevice,
            nodeProvisioning = nodeProvisioning,
            checkNodeSync = checkNodeSync,
            observeNodeChannels = observeNodeChannels,
            syncStateRepository = syncStateRepository,
            rebootStateRepository = rebootStateRepository,
            observeCallsignChanges = observeCallsignChanges,
            refreshNodePublicKey = refreshNodePublicKey,
            observeAppUser = observeAppUser,
        )
    }

    @Test
    fun `пустой позывной — callsignRequired true`() {
        appUserFlow.value = AppUser(displayName = "")
        createViewModel()
        assertTrue(viewModel.uiState.value.callsignRequired)
    }

    @Test
    fun `пустой позывной — авто-подключение пропускается`() {
        coEvery { connectToDevice.invoke(any()) } coAnswers {
            fail("auto-connect must be skipped when callsign is blank")
        }
        appUserFlow.value = AppUser(displayName = "")
        createViewModel()
    }

    @Test
    fun `пустой позывной при Scanning — HUD показывает установите позывной`() {
        appUserFlow.value = AppUser(displayName = "")
        createViewModel()
        connectionStatusFlow.value = MeshConnectionStatus.Scanning

        val infoSlot = HudStateMapper.buildConnectionInfoSlot(viewModel.uiState.value)
        assertEquals("установите позывной", infoSlot.content)
    }

    @Test
    fun `пустой позывной при Disconnected — HUD info пустой`() {
        appUserFlow.value = AppUser(displayName = "")
        createViewModel()
        connectionStatusFlow.value = MeshConnectionStatus.Disconnected

        val infoSlot = HudStateMapper.buildConnectionInfoSlot(viewModel.uiState.value)
        assertNull(infoSlot.content)
    }

    @Test
    fun `непустой позывной — callsignRequired false и авто-подключение выполняется`() {
        appUserFlow.value = AppUser(displayName = "Alpha")
        createViewModel()
        assertFalse(viewModel.uiState.value.callsignRequired)
        coVerify { connectToDevice.invoke(ConnectToMeshDeviceParams(lastDevice.address, lastDevice.name)) }
    }

    @Test
    fun `повторное подключение с InSync сбрасывает syncRequired через clear`() {
        val connected = MeshConnectionStatus.Connected(
            nodeId = "!aabbccdd",
            shortName = "TS",
            deviceName = "Meshtastic TS",
            rssi = -70,
            batteryLevel = 80,
        )
        appUserFlow.value = AppUser(displayName = "Alpha")
        coEvery { checkNodeSync.invoke() } returns NodeSyncResult.NeedsSync
        createViewModel()

        connectionStatusFlow.value = connected
        coVerify { syncStateRepository.setSyncRequired(true) }

        connectionStatusFlow.value = MeshConnectionStatus.Disconnected

        coEvery { checkNodeSync.invoke() } returns NodeSyncResult.InSync
        connectionStatusFlow.value = connected

        verify { syncStateRepository.clear() }
    }
}
