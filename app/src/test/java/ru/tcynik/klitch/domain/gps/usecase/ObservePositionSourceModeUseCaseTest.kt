package ru.tcynik.klitch.domain.gps.usecase

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.tcynik.klitch.domain.gps.model.PositionSourceMode
import ru.tcynik.klitch.domain.mesh.model.GpsMode
import ru.tcynik.klitch.domain.mesh.model.LocationConfigModel
import ru.tcynik.klitch.domain.mesh.model.MeshNodeModel
import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.klitch.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObservePositionSourceModeUseCaseTest {

    private val meshNetworkRepository: MeshNetworkRepository = mockk()
    private val meshConfigRepository: MeshConfigRepository = mockk()
    private val useCase = ObservePositionSourceModeUseCase(meshNetworkRepository, meshConfigRepository)

    @Test
    fun `no connected node — PHONE_GPS`() = runTest {
        every { meshNetworkRepository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            assertEquals(PositionSourceMode.PHONE_GPS, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `gps_mode ENABLED — NODE_GPS`() = runTest {
        every { meshNetworkRepository.observeOurNode() } returns flowOf(node())
        every { meshConfigRepository.observeLocationConfig(1) } returns flowOf(locationConfig(GpsMode.ENABLED))

        useCase(NoParams).test {
            assertEquals(PositionSourceMode.NODE_GPS, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `gps_mode DISABLED — PHONE_GPS`() = runTest {
        every { meshNetworkRepository.observeOurNode() } returns flowOf(node())
        every { meshConfigRepository.observeLocationConfig(1) } returns flowOf(locationConfig(GpsMode.DISABLED))

        useCase(NoParams).test {
            assertEquals(PositionSourceMode.PHONE_GPS, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `gps_mode NOT_PRESENT — PHONE_GPS`() = runTest {
        every { meshNetworkRepository.observeOurNode() } returns flowOf(node())
        every { meshConfigRepository.observeLocationConfig(1) } returns flowOf(locationConfig(GpsMode.NOT_PRESENT))

        useCase(NoParams).test {
            assertEquals(PositionSourceMode.PHONE_GPS, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `node reconnect — re-reads config for new node`() = runTest {
        val ourNodeFlow = MutableStateFlow(node(num = 1))
        every { meshNetworkRepository.observeOurNode() } returns ourNodeFlow
        every { meshConfigRepository.observeLocationConfig(1) } returns flowOf(locationConfig(GpsMode.ENABLED))
        every { meshConfigRepository.observeLocationConfig(2) } returns flowOf(locationConfig(GpsMode.DISABLED))

        useCase(NoParams).test {
            assertEquals(PositionSourceMode.NODE_GPS, awaitItem())
            ourNodeFlow.value = node(num = 2)
            assertEquals(PositionSourceMode.PHONE_GPS, awaitItem())
        }
    }

    private fun node(num: Int = 1) = MeshNodeModel(
        num = num,
        nodeId = "A",
        shortName = "A",
        longName = "A",
        snr = 0f,
        rssi = 0,
        lastHeard = 0,
        hopsAway = 0,
        batteryLevel = 0,
        voltage = 0f,
        channelUtilization = 0f,
        airUtilTx = 0f,
        uptimeSeconds = 0L,
        latitude = 0.0,
        longitude = 0.0,
        hasValidPosition = false,
        positionTime = 0,
        isOnline = true,
        groundSpeed = 0,
        groundTrack = 0,
        receivedOnSlot = 0,
    )

    private fun locationConfig(gpsMode: GpsMode) = LocationConfigModel(
        provideLocationToMesh = true,
        hasLocationPermission = true,
        gpsMode = gpsMode,
        fixedPositionEnabled = false,
        broadcastIntervalSecs = 180,
        smartBroadcastEnabled = true,
        smartBroadcastMinDistanceM = 0,
        positionFlags = 0,
        primaryChannelPositionPrecision = 32,
    )
}
