package ru.tcynik.meshtactics.domain.map.usecase

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.domain.mesh.model.MeshNodeModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveNodeMarkersUseCaseTest {

    private val repository: MeshNetworkRepository = mockk()
    private val useCase = ObserveNodeMarkersUseCase(repository)

    // ── Filtering ────────────────────────────────────────────────────────────

    @Test
    fun `nodes without valid position are filtered out`() = runTest {
        val noPosition = node(nodeId = "A", hasValidPosition = false)
        val withPosition = node(nodeId = "B", hasValidPosition = true)
        every { repository.observeNodes() } returns flowOf(listOf(noPosition, withPosition))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            val markers = awaitItem()
            assertEquals(1, markers.size)
            assertEquals("B", markers[0].nodeId)
            awaitComplete()
        }
    }

    @Test
    fun `our node is excluded from markers`() = runTest {
        val ourNode = node(nodeId = "OUR", hasValidPosition = true)
        val peer = node(nodeId = "PEER", hasValidPosition = true)
        every { repository.observeNodes() } returns flowOf(listOf(ourNode, peer))
        every { repository.observeOurNode() } returns flowOf(ourNode)

        useCase(NoParams).test {
            val markers = awaitItem()
            assertEquals(1, markers.size)
            assertEquals("PEER", markers[0].nodeId)
            awaitComplete()
        }
    }

    @Test
    fun `empty result when all nodes have no valid position`() = runTest {
        every { repository.observeNodes() } returns flowOf(
            listOf(node("A", hasValidPosition = false), node("B", hasValidPosition = false))
        )
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            assertTrue(awaitItem().isEmpty())
            awaitComplete()
        }
    }

    // ── GeoPoint mapping ─────────────────────────────────────────────────────

    @Test
    fun `GeoPoint coordinates match MeshNodeModel lat and lon`() = runTest {
        val peer = node(nodeId = "A", hasValidPosition = true, latitude = 55.75, longitude = 37.62)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            val marker = awaitItem().single()
            assertEquals(GeoPoint(55.75, 37.62), marker.position)
            awaitComplete()
        }
    }

    // ── Freshness (isStale) ──────────────────────────────────────────────────

    @Test
    fun `fresh node — positionTime 1 minute ago — isStale false`() = runTest {
        val freshTime = (System.currentTimeMillis() / 1000 - 60).toInt()
        val peer = node(nodeId = "A", hasValidPosition = true, positionTime = freshTime)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            assertFalse(awaitItem().single().isStale)
            awaitComplete()
        }
    }

    @Test
    fun `stale node — positionTime 5 minutes ago — isStale true`() = runTest {
        val staleTime = (System.currentTimeMillis() / 1000 - 300).toInt()
        val peer = node(nodeId = "A", hasValidPosition = true, positionTime = staleTime)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            assertTrue(awaitItem().single().isStale)
            awaitComplete()
        }
    }

    @Test
    fun `positionTime zero falls back to lastHeard for freshness`() = runTest {
        val freshLastHeard = (System.currentTimeMillis() / 1000 - 60).toInt()
        val peer = node(nodeId = "A", hasValidPosition = true, positionTime = 0, lastHeard = freshLastHeard)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            assertFalse(awaitItem().single().isStale)
            awaitComplete()
        }
    }

    // ── Heading ──────────────────────────────────────────────────────────────

    @Test
    fun `moving node — groundSpeed 1 or more — heading equals groundTrack`() = runTest {
        val peer = node(nodeId = "A", hasValidPosition = true, groundSpeed = 3, groundTrack = 270)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            assertEquals(270f, awaitItem().single().heading)
            awaitComplete()
        }
    }

    @Test
    fun `stationary node — groundSpeed 0 — heading is null`() = runTest {
        val peer = node(nodeId = "A", hasValidPosition = true, groundSpeed = 0, groundTrack = 90)
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            assertNull(awaitItem().single().heading)
            awaitComplete()
        }
    }

    // ── longName propagation ─────────────────────────────────────────────────

    @Test
    fun `longName is propagated to marker`() = runTest {
        val peer = node(nodeId = "A", hasValidPosition = true, longName = "Alpha")
        every { repository.observeNodes() } returns flowOf(listOf(peer))
        every { repository.observeOurNode() } returns flowOf(null)

        useCase(NoParams).test {
            assertEquals("Alpha", awaitItem().single().longName)
            awaitComplete()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun node(
        nodeId: String,
        hasValidPosition: Boolean = true,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        positionTime: Int = (System.currentTimeMillis() / 1000 - 60).toInt(),
        lastHeard: Int = (System.currentTimeMillis() / 1000 - 60).toInt(),
        isOnline: Boolean = true,
        groundSpeed: Int = 0,
        groundTrack: Int = 0,
        longName: String = nodeId,
    ) = MeshNodeModel(
        num = 0,
        nodeId = nodeId,
        shortName = nodeId,
        longName = longName,
        snr = 0f,
        rssi = 0,
        lastHeard = lastHeard,
        hopsAway = 0,
        batteryLevel = 0,
        voltage = 0f,
        channelUtilization = 0f,
        airUtilTx = 0f,
        uptimeSeconds = 0L,
        latitude = latitude,
        longitude = longitude,
        hasValidPosition = hasValidPosition,
        positionTime = positionTime,
        isOnline = isOnline,
        groundSpeed = groundSpeed,
        groundTrack = groundTrack,
    )
}
