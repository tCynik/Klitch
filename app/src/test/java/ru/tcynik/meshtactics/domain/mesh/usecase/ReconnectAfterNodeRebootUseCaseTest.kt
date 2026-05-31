package ru.tcynik.meshtactics.domain.mesh.usecase



import io.mockk.coEvery

import io.mockk.coVerify

import io.mockk.every

import io.mockk.mockk

import io.mockk.verify

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.launch

import kotlinx.coroutines.test.advanceTimeBy

import kotlinx.coroutines.test.runTest

import org.junit.Assert.assertFalse

import org.junit.Assert.assertTrue

import org.junit.Before

import org.junit.Test

import ru.tcynik.meshtactics.data.channel.repository.ContourSyncStateRepositoryImpl

import ru.tcynik.meshtactics.data.mesh.repository.RebootStateRepositoryImpl

import ru.tcynik.meshtactics.domain.channel.model.NodeSyncResult

import ru.tcynik.meshtactics.logger.NoOpLogger

import ru.tcynik.meshtactics.domain.channel.usecase.CheckNodeSyncUseCase

import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus

import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceModel

import ru.tcynik.meshtactics.domain.mesh.model.NodeSyncCyclePhase

import ru.tcynik.meshtactics.domain.usecase.base.NoParams



class ReconnectAfterNodeRebootUseCaseTest {



    private val reconnectViaBleScan: ReconnectViaBleScanUseCase = mockk()

    private val disconnectFromMesh: DisconnectFromMeshUseCase = mockk(relaxed = true)

    private val observeConnectionStatus: ObserveConnectionStatusUseCase = mockk()

    private val requestDeviceConfig: RequestDeviceConfigUseCase = mockk(relaxed = true)

    private val checkNodeSync: CheckNodeSyncUseCase = mockk()

    private val syncStateRepository = ContourSyncStateRepositoryImpl()

    private val rebootStateRepository = RebootStateRepositoryImpl(NoOpLogger())



    private val connectionStatusFlow = MutableStateFlow<MeshConnectionStatus>(MeshConnectionStatus.Disconnected)



    private val device = MeshDeviceModel(address = "AA:BB:CC:DD:EE:FF", name = "Meshtastic TE", rssi = 0)



    private lateinit var useCase: ReconnectAfterNodeRebootUseCase



    @Before

    fun setUp() {

        rebootStateRepository.setSyncCyclePhase(NodeSyncCyclePhase.Rebooting)

        rebootStateRepository.markSyncAppliedBeforeReboot()

        every { observeConnectionStatus.invoke(NoParams) } returns connectionStatusFlow

        coEvery { reconnectViaBleScan.invoke(any()) } returns true

        coEvery { checkNodeSync.invoke() } returns NodeSyncResult.InSync

        useCase = ReconnectAfterNodeRebootUseCase(

            disconnectFromMesh = disconnectFromMesh,

            reconnectViaBleScan = reconnectViaBleScan,

            observeConnectionStatus = observeConnectionStatus,

            requestDeviceConfig = requestDeviceConfig,

            checkNodeSync = checkNodeSync,

            syncStateRepository = syncStateRepository,

            rebootStateRepository = rebootStateRepository,

            logger = NoOpLogger(),

        )

    }



    @Test

    fun `после reconnect с InSync — skip сброшен и syncRequired очищен`() = runTest {

        val job = launch {

            useCase(NoParams)

        }

        advanceTimeBy(ReconnectAfterNodeRebootUseCase.REBOOT_GRACE_MS)

        job.join()



        coVerify(exactly = 1) { reconnectViaBleScan.invoke(any()) }

        verify(exactly = 1) { requestDeviceConfig.invoke() }

        assertFalse(rebootStateRepository.shouldSkipSyncCheckAfterReboot())

        assertFalse(rebootStateRepository.isRebooting.value)

        assertFalse(syncStateRepository.syncRequired.value)

    }



    @Test

    fun `после reconnect NeedsSync — syncRequired=true`() = runTest {

        coEvery { checkNodeSync.invoke() } returns NodeSyncResult.NeedsSync



        val job = launch { useCase(NoParams) }

        advanceTimeBy(ReconnectAfterNodeRebootUseCase.REBOOT_GRACE_MS)

        job.join()



        assertTrue(syncStateRepository.syncRequired.value)

        assertFalse(rebootStateRepository.shouldSkipSyncCheckAfterReboot())

    }



    @Test

    fun `неудачный BLE reconnect — сброс reboot`() = runTest {

        coEvery { reconnectViaBleScan.invoke(any()) } returns false



        useCase(NoParams)



        coVerify(exactly = 1) { reconnectViaBleScan.invoke(any()) }

        assertFalse(rebootStateRepository.isRebooting.value)

    }

}


