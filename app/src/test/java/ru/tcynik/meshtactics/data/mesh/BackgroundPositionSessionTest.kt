package ru.tcynik.meshtactics.data.mesh

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.meshtastic.proto.Position as ProtoPosition
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.gps.repository.GpsLifecycleController
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.mesh.model.MyNodeInfo
import ru.tcynik.meshtactics.mesh.repository.CommandSender
import ru.tcynik.meshtactics.mesh.repository.GeoSendPolicy
import ru.tcynik.meshtactics.mesh.repository.MeshLocationManager
import ru.tcynik.meshtactics.mesh.repository.NodeRepository
import ru.tcynik.meshtactics.mesh.repository.UiPrefs

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundPositionSessionTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val myNodeInfoFlow = MutableStateFlow<MyNodeInfo?>(null)
    private val nodeRepository: NodeRepository = mockk()
    private val locationManager: MeshLocationManager = mockk(relaxed = true)
    private val commandSender: CommandSender = mockk(relaxed = true)
    private val uiPrefs: UiPrefs = mockk()
    private val geoSendPolicy: GeoSendPolicy = mockk()
    private val contourRepository: ContourRepository = mockk()
    private val channelSlotResolver: ChannelSlotResolver = mockk()
    private val gpsLifecycleController: GpsLifecycleController = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { nodeRepository.myNodeInfo } returns myNodeInfoFlow
        every { contourRepository.observeContours() } returns flowOf(emptyList())
    }

    private fun createSession() = BackgroundPositionSession(
        nodeRepository = nodeRepository,
        locationManager = locationManager,
        commandSender = commandSender,
        uiPrefs = uiPrefs,
        geoSendPolicy = geoSendPolicy,
        contourRepository = contourRepository,
        channelSlotResolver = channelSlotResolver,
        gpsLifecycleController = gpsLifecycleController,
        logger = logger,
        scope = testScope,
    )

    private fun setupGeoAllowed(nodeNum: Int, provideLocation: Boolean = true, geoAllowed: Boolean = true) {
        every { uiPrefs.shouldProvideNodeLocation(nodeNum) } returns MutableStateFlow(provideLocation)
        every { geoSendPolicy.observeAllowed() } returns MutableStateFlow(geoAllowed)
    }

    private fun buildNode(nodeNum: Int = 123) = MyNodeInfo(
        myNodeNum = nodeNum,
        hasGPS = false,
        model = null,
        firmwareVersion = null,
        couldUpdate = false,
        shouldUpdate = false,
        currentPacketId = 0L,
        messageTimeoutMsec = 0,
        minAppVersion = 0,
        maxChannels = 8,
        hasWifi = false,
        channelUtilization = 0f,
        airUtilTx = 0f,
        deviceId = null,
    )

    @Test
    fun `when node available and geo allowed, starts location manager`() = testScope.runTest {
        setupGeoAllowed(123)
        createSession()

        myNodeInfoFlow.value = buildNode(123)
        advanceUntilIdle()

        verify { locationManager.start(any(), any()) }
    }

    @Test
    fun `when node available and geo allowed, starts gps lifecycle`() = testScope.runTest {
        setupGeoAllowed(123)
        createSession()

        myNodeInfoFlow.value = buildNode(123)
        advanceUntilIdle()

        verify { gpsLifecycleController.start() }
    }

    @Test
    fun `when geoSendPolicy disallows, stops location manager`() = testScope.runTest {
        val geoAllowedFlow = MutableStateFlow(true)
        every { uiPrefs.shouldProvideNodeLocation(123) } returns MutableStateFlow(true)
        every { geoSendPolicy.observeAllowed() } returns geoAllowedFlow
        createSession()

        myNodeInfoFlow.value = buildNode(123)
        advanceUntilIdle()

        geoAllowedFlow.value = false
        advanceUntilIdle()

        verify { locationManager.stop() }
    }

    @Test
    fun `when uiPrefs disallows, stops location manager`() = testScope.runTest {
        val provideFlow = MutableStateFlow(true)
        every { uiPrefs.shouldProvideNodeLocation(123) } returns provideFlow
        every { geoSendPolicy.observeAllowed() } returns MutableStateFlow(true)
        createSession()

        myNodeInfoFlow.value = buildNode(123)
        advanceUntilIdle()

        provideFlow.value = false
        advanceUntilIdle()

        verify { locationManager.stop() }
    }

    @Test
    fun `when myNodeInfo becomes null, stops location manager`() = testScope.runTest {
        setupGeoAllowed(123)
        createSession()

        myNodeInfoFlow.value = buildNode(123)
        advanceUntilIdle()

        myNodeInfoFlow.value = null
        advanceUntilIdle()

        verify { locationManager.stop() }
    }

    @Test
    fun `when position arrives, sends to primary channel`() = testScope.runTest {
        setupGeoAllowed(123)
        createSession()

        myNodeInfoFlow.value = buildNode(123)
        advanceUntilIdle()

        val fnSlot = slot<(ProtoPosition) -> Unit>()
        verify { locationManager.start(any(), capture(fnSlot)) }

        val pos = ProtoPosition(latitude_i = 550000000, longitude_i = 370000000)
        fnSlot.captured.invoke(pos)
        advanceUntilIdle()

        verify { commandSender.sendPosition(pos) }
    }
}
