package ru.tcynik.klitch.data.gps

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.meshtastic.proto.Position as ProtoPosition
import ru.tcynik.klitch.domain.gps.model.PositionSourceMode
import ru.tcynik.klitch.mesh.model.Node
import ru.tcynik.klitch.mesh.repository.NodeRepository

class NodeGpsPositionSourceTest {

    private val nodeRepository: NodeRepository = mockk()
    private val ourNodeFlow = MutableStateFlow<Node?>(null)

    private fun source(): NodeGpsPositionSource {
        every { nodeRepository.ourNodeInfo } returns ourNodeFlow
        return NodeGpsPositionSource(nodeRepository)
    }

    @Test
    fun `mode is NODE_GPS`() {
        assertEquals(PositionSourceMode.NODE_GPS, source().mode)
    }

    @Test
    fun `no valid position — nothing emitted`() = runTest {
        val src = source()
        ourNodeFlow.value = nodeWithPosition(latitudeI = 0, longitudeI = 0)

        src.observePosition().test {
            expectNoEvents()
        }
    }

    @Test
    fun `valid position — maps lat, lon, bearing, speed, time`() = runTest {
        val src = source()
        ourNodeFlow.value = nodeWithPosition(
            latitudeI = 550000000,
            longitudeI = 370000000,
            groundTrack = 90,
            groundSpeed = 5,
            time = 1_000,
        )

        src.observePosition().test {
            val result = awaitItem()
            assertEquals(55.0, result.latitude, 0.0001)
            assertEquals(37.0, result.longitude, 0.0001)
            assertEquals(90f, result.bearing)
            assertEquals(5f, result.speed)
            assertEquals(1_000_000L, result.time)
        }
    }

    private fun nodeWithPosition(
        latitudeI: Int,
        longitudeI: Int,
        groundTrack: Int = 0,
        groundSpeed: Int = 0,
        time: Int = 0,
    ) = Node(
        num = 1,
        position = ProtoPosition(
            latitude_i = latitudeI,
            longitude_i = longitudeI,
            ground_track = groundTrack,
            ground_speed = groundSpeed,
            time = time,
        ),
    )
}
