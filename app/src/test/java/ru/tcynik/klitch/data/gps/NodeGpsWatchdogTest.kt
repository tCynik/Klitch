package ru.tcynik.klitch.data.gps

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meshtastic.proto.Position as ProtoPosition
import ru.tcynik.klitch.data.channel.repository.ContourSyncStateRepositoryImpl
import ru.tcynik.klitch.domain.gps.model.PositionSourceMode
import ru.tcynik.klitch.domain.gps.usecase.ObservePositionSourceModeUseCase
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.mesh.model.Node
import ru.tcynik.klitch.mesh.repository.NodeRepository
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class NodeGpsWatchdogTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val ourNodeFlow = MutableStateFlow<Node?>(null)
    private val nodeRepository: NodeRepository = mockk()
    private val observePositionSourceMode: ObservePositionSourceModeUseCase = mockk()
    private val syncStateRepository = ContourSyncStateRepositoryImpl()
    private val logger: Logger = mockk(relaxed = true)
    private var clockMs = 0L

    @Before
    fun setUp() {
        every { nodeRepository.ourNodeInfo } returns ourNodeFlow
    }

    private fun createWatchdog(mode: PositionSourceMode) = NodeGpsWatchdog(
        nodeRepository = nodeRepository,
        observePositionSourceMode = observePositionSourceMode.also {
            every { it.invoke(any()) } returns flowOf(mode)
        },
        syncStateRepository = syncStateRepository,
        logger = logger,
        scope = testScope.backgroundScope,
        nowMs = { clockMs },
    )

    private fun nodeWithPosition(time: Int, satsInView: Int) = Node(
        num = 1,
        position = ProtoPosition(latitude_i = 550000000, longitude_i = 370000000, time = time, sats_in_view = satsInView),
    )

    /** Advances the virtual clock and the injected wall clock together, tick by tick (one tick == one watchdog check). */
    private suspend fun TestScope.advanceTicks(count: Int, tickMs: Long = TICK_MS) = repeat(count) {
        clockMs += tickMs
        advanceTimeBy(tickMs.milliseconds)
    }

    @Test
    fun `PHONE_GPS mode — never triggers syncRequired`() = testScope.runTest {
        createWatchdog(PositionSourceMode.PHONE_GPS)
        ourNodeFlow.value = nodeWithPosition(time = 100, satsInView = 0)

        advanceTicks(count = 30)

        assertFalse(syncStateRepository.syncRequired.value)
    }

    @Test
    fun `NODE_GPS — frozen position time triggers syncRequired and stale event`() = testScope.runTest {
        val watchdog = createWatchdog(PositionSourceMode.NODE_GPS)
        var staleEventCount = 0
        val collectJob = testScope.backgroundScope.launch {
            watchdog.staleEvent.collect { staleEventCount++ }
        }

        ourNodeFlow.value = nodeWithPosition(time = 100, satsInView = 5)
        advanceTicks(count = 25) // 25 * 30s = 12.5min > 10min threshold

        assertTrue(syncStateRepository.syncRequired.value)
        assertTrue("expected exactly one stale event, got $staleEventCount", staleEventCount == 1)
        collectJob.cancel()
    }

    @Test
    fun `NODE_GPS — advancing position time does not trigger syncRequired`() = testScope.runTest {
        createWatchdog(PositionSourceMode.NODE_GPS)

        repeat(25) { i ->
            ourNodeFlow.value = nodeWithPosition(time = 100 + i, satsInView = 5)
            advanceTicks(count = 1)
        }

        assertFalse(syncStateRepository.syncRequired.value)
    }

    @Test
    fun `NODE_GPS — zero satellites held stale triggers syncRequired`() = testScope.runTest {
        createWatchdog(PositionSourceMode.NODE_GPS)

        ourNodeFlow.value = nodeWithPosition(time = 100, satsInView = 0)
        advanceTicks(count = 25)

        assertTrue(syncStateRepository.syncRequired.value)
    }

    @Test
    fun `NODE_GPS — already syncRequired does not re-emit stale event`() = testScope.runTest {
        syncStateRepository.setSyncRequired(true)
        val watchdog = createWatchdog(PositionSourceMode.NODE_GPS)
        var staleEventCount = 0
        val collectJob = testScope.backgroundScope.launch {
            watchdog.staleEvent.collect { staleEventCount++ }
        }

        ourNodeFlow.value = nodeWithPosition(time = 100, satsInView = 0)
        advanceTicks(count = 25)

        assertTrue("expected no stale event while already syncRequired, got $staleEventCount", staleEventCount == 0)
        collectJob.cancel()
    }

    private companion object {
        const val TICK_MS = 30_000L
    }
}
