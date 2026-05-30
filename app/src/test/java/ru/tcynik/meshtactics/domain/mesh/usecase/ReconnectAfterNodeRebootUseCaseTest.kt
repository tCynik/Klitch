package ru.tcynik.meshtactics.domain.mesh.usecase

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.data.mesh.repository.RebootStateRepositoryImpl
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceModel
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ReconnectAfterNodeRebootUseCaseTest {

    private val disconnectFromMesh: DisconnectFromMeshUseCase = mockk(relaxed = true)
    private val connectToDevice: ConnectToMeshDeviceUseCase = mockk(relaxed = true)
    private val getLastConnectedDevice: GetLastConnectedDeviceUseCase = mockk()
    private val observeConnectionStatus: ObserveConnectionStatusUseCase = mockk()
    private val rebootStateRepository = RebootStateRepositoryImpl()

    private val connectionStatusFlow = MutableStateFlow<MeshConnectionStatus>(MeshConnectionStatus.Disconnected)

    private val device = MeshDeviceModel(address = "AA:BB:CC:DD:EE:FF", name = "Meshtastic TE", rssi = 0)
    private val connectParams = ConnectToMeshDeviceParams(device.address, device.name)

    private lateinit var useCase: ReconnectAfterNodeRebootUseCase

    @Before
    fun setUp() {
        rebootStateRepository.setRebooting(true)
        every { getLastConnectedDevice() } returns device
        every { observeConnectionStatus.invoke(NoParams) } returns connectionStatusFlow
        useCase = ReconnectAfterNodeRebootUseCase(
            disconnectFromMesh = disconnectFromMesh,
            connectToDevice = connectToDevice,
            getLastConnectedDevice = getLastConnectedDevice,
            observeConnectionStatus = observeConnectionStatus,
            rebootStateRepository = rebootStateRepository,
        )
    }

    @Test
    fun `успех на первой попытке — reboot не сбрасывается use case`() = runTest {
        val job = launch {
            useCase(NoParams)
        }
        advanceTimeBy(ReconnectAfterNodeRebootUseCase.REBOOT_WAIT_MS + ReconnectAfterNodeRebootUseCase.POST_DISCONNECT_DELAY_MS)
        connectionStatusFlow.value = MeshConnectionStatus.Connected("!abc", "TE", device.name, -70, 80)
        job.join()

        coVerify(exactly = 1) { connectToDevice.invoke(connectParams) }
        assertTrue(rebootStateRepository.isRebooting.value)
    }

    @Test
    fun `три неудачные попытки — disconnect и сброс reboot`() = runTest {
        useCase(NoParams)

        coVerify(exactly = 3) { connectToDevice.invoke(connectParams) }
        coVerify(atLeast = 3) { disconnectFromMesh.invoke(NoParams) }
        assertFalse(rebootStateRepository.isRebooting.value)
    }

    @Test
    fun `нет сохранённого устройства — сброс reboot без connect`() = runTest {
        every { getLastConnectedDevice() } returns null

        useCase(NoParams)

        coVerify(exactly = 0) { connectToDevice.invoke(any()) }
        assertFalse(rebootStateRepository.isRebooting.value)
    }
}
