package ru.tcynik.meshtactics.presentation.feature.marks

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkModel
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkShape
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.mesh.model.MeshNodeModel
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveMeshNodesUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.DeleteGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ExtendGeoMarkUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ObserveGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.SendGeoMarkParams
import ru.tcynik.meshtactics.domain.marker.usecase.SendGeoMarkUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ToggleGeoMarkVisibilityUseCase
import ru.tcynik.meshtactics.logger.NoOpLogger
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkDeliveryFilterStatus
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarkDeliveryState
import ru.tcynik.meshtactics.presentation.feature.marks.models.GeoMarksListUiState

class GeoMarksListViewModelTest {

    private val marksFlow = MutableStateFlow<List<GeoMarkModel>>(emptyList())
    private val contoursFlow = MutableStateFlow<List<Contour>>(emptyList())
    private val nodesFlow = MutableStateFlow<List<MeshNodeModel>>(emptyList())
    private val observeGeoMarks: ObserveGeoMarksUseCase = mockk()
    private val observeContours: ObserveContoursUseCase = mockk()
    private val observeMeshNodes: ObserveMeshNodesUseCase = mockk()
    private val toggleVisibility: ToggleGeoMarkVisibilityUseCase = mockk(relaxed = true)
    private val deleteGeoMarks: DeleteGeoMarksUseCase = mockk(relaxed = true)
    private val extendGeoMark: ExtendGeoMarkUseCase = mockk(relaxed = true)
    private val sendGeoMark: SendGeoMarkUseCase = mockk(relaxed = true)
    private val logger: Logger = NoOpLogger()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: GeoMarksListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        marksFlow.value = emptyList()
        contoursFlow.value = emptyList()
        nodesFlow.value = emptyList()
        every { observeGeoMarks.invoke(any()) } returns marksFlow
        every { observeContours.invoke(any()) } returns contoursFlow
        every { observeMeshNodes.invoke(any()) } returns nodesFlow
        viewModel = GeoMarksListViewModel(
            observeGeoMarks = observeGeoMarks,
            observeContours = observeContours,
            observeMeshNodes = observeMeshNodes,
            toggleVisibility = toggleVisibility,
            deleteGeoMarks = deleteGeoMarks,
            extendGeoMark = extendGeoMark,
            sendGeoMark = sendGeoMark,
            logger = logger,
            refreshTtlLabels = false,
        )
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            viewModel.viewModelScope.cancel()
        }
        Dispatchers.resetMain()
    }

    /** Runs [body] on [testDispatcher]; cancels [viewModel] scope before runTest waits for idle. */
    private fun viewModelCoroutineTest(body: suspend TestScope.() -> Unit) = runTest(testDispatcher) {
        try {
            body()
        } finally {
            if (::viewModel.isInitialized) {
                viewModel.viewModelScope.cancel()
            }
        }
    }

    /** Lets [viewModelScope.launch] bodies finish before [coVerify]. */
    private suspend fun TestScope.awaitLaunchedCoroutines() {
        runCurrent()
        runCurrent()
    }

    @Test
    fun `maps marks to list items sorted by createdAt descending`() {
        val older = makeMark(id = "old", createdAt = 100L, name = "Old")
        val newer = makeMark(id = "new", createdAt = 200L, name = "New")
        marksFlow.value = listOf(older, newer)

        val state = viewModel.uiState.value
        assertEquals(2, state.items.size)
        assertEquals("new", state.items[0].id)
        assertEquals("old", state.items[1].id)
    }

    @Test
    fun `maps self author to Я and other to short node id when no node names`() {
        val self = makeMark(id = "self", isSelf = true, authorNodeId = "!aaaa1111")
        val remote = makeMark(id = "remote", isSelf = false, authorNodeId = "!bbbb2222")
        marksFlow.value = listOf(self, remote)

        val state = viewModel.uiState.value
        assertEquals("Я", state.items.find { it.id == "self" }?.authorLabel)
        assertEquals("!bbbb2", state.items.find { it.id == "remote" }?.authorLabel)
    }

    @Test
    fun `shows callsign instead of node id when node name is known`() {
        marksFlow.value = listOf(makeMark(id = "remote", isSelf = false, authorNodeId = "!bbbb2222"))
        nodesFlow.value = listOf(makeNode(nodeId = "!bbbb2222", longName = "Alpha-1"))

        assertEquals("Alpha-1", viewModel.uiState.value.items.single().authorLabel)
    }

    @Test
    fun `blank name becomes dash`() {
        marksFlow.value = listOf(makeMark(id = "blank", name = ""))
        assertEquals("—", viewModel.uiState.value.items.single().name)
    }

    @Test
    fun `visibility toggle delegates to use case`() = viewModelCoroutineTest {
        coEvery { toggleVisibility("mark-1", false) } returns Unit

        viewModel.onVisibilityToggle("mark-1", false)
        awaitLaunchedCoroutines()

        coVerify(exactly = 1) { toggleVisibility("mark-1", false) }
    }

    @Test
    fun `maps delivery state from isSelf and logicalChannelId`() {
        marksFlow.value = listOf(
            makeMark(id = "local", isSelf = true, logicalChannelId = "", authorNodeId = ""),
            makeMark(id = "sent", isSelf = true, logicalChannelId = "ch-1", authorNodeId = "!aaaa1111"),
            makeMark(id = "rcv", isSelf = false, logicalChannelId = "ch-2", authorNodeId = "!bbbb2222"),
        )
        val state = viewModel.uiState.value
        assertEquals(GeoMarkDeliveryState.LOCAL, state.items.find { it.id == "local" }?.deliveryState)
        assertEquals(GeoMarkDeliveryState.SENT, state.items.find { it.id == "sent" }?.deliveryState)
        assertEquals(GeoMarkDeliveryState.RECEIVED, state.items.find { it.id == "rcv" }?.deliveryState)
    }

    @Test
    fun `delivery filters selected for present types on load`() {
        marksFlow.value = listOf(
            makeMark(id = "local", isSelf = true, logicalChannelId = "", authorNodeId = ""),
            makeMark(id = "rcv", isSelf = false, authorNodeId = "!bbbb2222"),
        )
        val state = viewModel.uiState.value
        assertEquals(GeoMarkDeliveryFilterStatus.SELECTED, filterStatus(state, GeoMarkDeliveryState.LOCAL))
        assertEquals(GeoMarkDeliveryFilterStatus.INACTIVE, filterStatus(state, GeoMarkDeliveryState.SENT))
        assertEquals(GeoMarkDeliveryFilterStatus.SELECTED, filterStatus(state, GeoMarkDeliveryState.RECEIVED))
    }

    @Test
    fun `toggling delivery filter hides marks of that type`() {
        marksFlow.value = listOf(
            makeMark(id = "local", isSelf = true, logicalChannelId = "", authorNodeId = ""),
            makeMark(id = "rcv", isSelf = false, authorNodeId = "!bbbb2222"),
        )

        viewModel.onDeliveryFilterToggle(GeoMarkDeliveryState.LOCAL)

        val state = viewModel.uiState.value
        assertEquals(1, state.items.size)
        assertEquals("rcv", state.items.single().id)
        assertEquals(GeoMarkDeliveryFilterStatus.UNSELECTED, filterStatus(state, GeoMarkDeliveryState.LOCAL))
        assertEquals(GeoMarkDeliveryFilterStatus.SELECTED, filterStatus(state, GeoMarkDeliveryState.RECEIVED))
    }

    @Test
    fun `bulk visibility selects all filtered marks when not all visible`() = viewModelCoroutineTest {
        marksFlow.value = listOf(
            makeMark(id = "a", isVisible = true),
            makeMark(id = "b", isVisible = false),
        )

        viewModel.onToggleAllFilteredVisibility()
        awaitLaunchedCoroutines()

        coVerify { toggleVisibility("b", true) }
        coVerify(exactly = 0) { toggleVisibility("a", any()) }
    }

    @Test
    fun `bulk visibility hides all filtered marks when all visible`() = viewModelCoroutineTest {
        marksFlow.value = listOf(
            makeMark(id = "a", isVisible = true),
            makeMark(id = "b", isVisible = true),
        )

        viewModel.onToggleAllFilteredVisibility()
        awaitLaunchedCoroutines()

        coVerify { toggleVisibility("a", false) }
        coVerify { toggleVisibility("b", false) }
    }

    @Test
    fun `allFilteredVisible reflects checkbox state of filtered items`() {
        marksFlow.value = listOf(
            makeMark(id = "a", isVisible = true),
            makeMark(id = "b", isVisible = false),
        )
        val state = viewModel.uiState.value
        assertFalse(state.allFilteredVisible)
        assertTrue(state.bulkVisibilityEnabled)
    }

    @Test
    fun `deleteEnabled when at least one visible mark in filter`() {
        marksFlow.value = listOf(
            makeMark(id = "on", isVisible = true),
            makeMark(id = "off", isVisible = false),
        )
        assertTrue(viewModel.uiState.value.deleteEnabled)
    }

    @Test
    fun `onDeleteClick — single mark confirmation message`() {
        marksFlow.value = listOf(makeMark(id = "one", name = "Alpha", isSelf = true, authorNodeId = ""))

        viewModel.onDeleteClick()

        val confirm = viewModel.uiState.value.deleteConfirm
        assertEquals("Удалить метку Alpha (от Я)?", confirm?.message)
        assertEquals(listOf("one"), confirm?.markIds)
    }

    @Test
    fun `onDeleteClick — multiple marks confirmation message`() {
        marksFlow.value = listOf(
            makeMark(id = "a", isVisible = true),
            makeMark(id = "b", isVisible = true),
        )

        viewModel.onDeleteClick()

        assertEquals("Удалить выбранные метки(2)?", viewModel.uiState.value.deleteConfirm?.message)
    }

    @Test
    fun `onConfirmDelete — invokes use case and closes dialog`() = viewModelCoroutineTest {
        marksFlow.value = listOf(makeMark(id = "del", isVisible = true))

        viewModel.onDeleteClick()
        viewModel.onConfirmDelete()
        awaitLaunchedCoroutines()

        coVerify { deleteGeoMarks(listOf("del")) }
        assertNull(viewModel.uiState.value.deleteConfirm)
    }

    @Test
    fun `hasMarks true when filters hide all items`() {
        marksFlow.value = listOf(
            makeMark(id = "local", isSelf = true, logicalChannelId = "", authorNodeId = ""),
        )

        viewModel.onDeliveryFilterToggle(GeoMarkDeliveryState.LOCAL)

        val state = viewModel.uiState.value
        assertTrue(state.hasMarks)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `onItemDeleteClick — single mark confirmation`() {
        marksFlow.value = listOf(makeMark(id = "one", name = "Alpha", isSelf = true, authorNodeId = ""))

        viewModel.onItemDeleteClick("one")

        val confirm = viewModel.uiState.value.deleteConfirm
        assertEquals("Удалить метку Alpha (от Я)?", confirm?.message)
        assertEquals(listOf("one"), confirm?.markIds)
    }

    @Test
    fun `onItemExtendClick — delegates to use case`() = viewModelCoroutineTest {
        marksFlow.value = listOf(makeMark(id = "ext"))

        viewModel.onItemExtendClick("ext")
        awaitLaunchedCoroutines()

        coVerify { extendGeoMark("ext") }
    }

    @Test
    fun `onItemSendClick — opens contour picker with active contours`() {
        marksFlow.value = listOf(makeMark(id = "send", name = "SendMe"))
        contoursFlow.value = listOf(
            makeContour(id = "ch-1", name = "Alpha"),
            makeContour(id = "ch-2", name = "Beta", isActive = false),
        )

        viewModel.onItemSendClick("send")

        val picker = viewModel.uiState.value.sendContourPicker
        assertEquals("send", picker?.markId)
        assertEquals("SendMe", picker?.markName)
        assertEquals(listOf("ch-1", "__local__"), picker?.contours?.map { it.contourId })
    }

    @Test
    fun `onSendContourSelected — sends mark to contour`() = viewModelCoroutineTest {
        val mark = makeMark(id = "send", isSelf = true)
        marksFlow.value = listOf(mark)
        contoursFlow.value = listOf(makeContour(id = "ch-1", name = "Alpha"))

        viewModel.onItemSendClick("send")
        viewModel.onSendContourSelected("ch-1")
        awaitLaunchedCoroutines()

        coVerify {
            sendGeoMark(match { params ->
                params.mark.id == "send" && params.contourId == ContourId("ch-1") && !params.localOnly
            })
        }
        assertNull(viewModel.uiState.value.sendContourPicker)
    }

    @Test
    fun `reflects isVisible from domain model`() {
        marksFlow.value = listOf(
            makeMark(id = "hidden", isVisible = false),
            makeMark(id = "shown", isVisible = true),
        )
        val state = viewModel.uiState.value
        assertFalse(state.items.find { it.id == "hidden" }!!.isVisible)
        assertTrue(state.items.find { it.id == "shown" }!!.isVisible)
    }

    @Test
    fun `maps queued delivery state`() {
        marksFlow.value = listOf(
            makeMark(id = "queued", isSelf = true, logicalChannelId = "ch-1", authorNodeId = ""),
        )
        assertEquals(GeoMarkDeliveryState.QUEUED, viewModel.uiState.value.items.single().deliveryState)
    }

    @Test
    fun `filter becomes inactive when all marks of that type are removed`() {
        marksFlow.value = listOf(
            makeMark(id = "local", isSelf = true, logicalChannelId = "", authorNodeId = ""),
            makeMark(id = "rcv", isSelf = false, authorNodeId = "!bbbb2222"),
        )
        assertEquals(GeoMarkDeliveryFilterStatus.SELECTED, filterStatus(viewModel.uiState.value, GeoMarkDeliveryState.LOCAL))

        marksFlow.value = listOf(
            makeMark(id = "rcv", isSelf = false, authorNodeId = "!bbbb2222"),
        )

        val state = viewModel.uiState.value
        assertEquals(GeoMarkDeliveryFilterStatus.INACTIVE, filterStatus(state, GeoMarkDeliveryState.LOCAL))
        assertEquals(GeoMarkDeliveryFilterStatus.SELECTED, filterStatus(state, GeoMarkDeliveryState.RECEIVED))
    }

    @Test
    fun `onDismissDeleteDialog clears deleteConfirm`() {
        marksFlow.value = listOf(makeMark(id = "one", isVisible = true))
        viewModel.onDeleteClick()
        assertFalse(viewModel.uiState.value.deleteConfirm == null)

        viewModel.onDismissDeleteDialog()

        assertNull(viewModel.uiState.value.deleteConfirm)
    }

    @Test
    fun `onDismissSendContourPicker clears sendContourPicker`() {
        marksFlow.value = listOf(makeMark(id = "send", name = "SendMe"))
        contoursFlow.value = listOf(makeContour(id = "ch-1", name = "Alpha"))
        viewModel.onItemSendClick("send")
        assertFalse(viewModel.uiState.value.sendContourPicker == null)

        viewModel.onDismissSendContourPicker()

        assertNull(viewModel.uiState.value.sendContourPicker)
    }

    private fun filterStatus(state: GeoMarksListUiState, type: GeoMarkDeliveryState): GeoMarkDeliveryFilterStatus =
        state.deliveryFilters.first { it.deliveryState == type }.status

    private fun makeContour(
        id: String,
        name: String,
        isActive: Boolean = true,
    ) = Contour(
        id = ContourId(id),
        name = name,
        description = null,
        expiration = null,
        exclusivityTime = null,
        isActive = isActive,
        transport = ContourTransport(meshtastic = MeshtasticChannel(psk = "", channelHash = ContourHash("hash-$id"))),
    )

    private fun makeMark(
        id: String,
        createdAt: Long = 1_000L,
        name: String = "Test",
        isSelf: Boolean = false,
        authorNodeId: String = "",
        logicalChannelId: String = "",
        isVisible: Boolean = true,
    ) = GeoMarkModel(
        id = id,
        waypointId = 0,
        type = GeoMarkType.POINT,
        points = listOf(GeoPoint(55.0, 37.0)),
        authorNodeId = authorNodeId,
        logicalChannelId = logicalChannelId,
        createdAt = createdAt,
        expiresAt = null,
        isSelf = isSelf,
        name = name,
        shape = GeoMarkShape.CIRCLE,
        isVisible = isVisible,
    )

    private fun makeNode(nodeId: String, longName: String) = MeshNodeModel(
        num = 0,
        nodeId = nodeId,
        shortName = "",
        longName = longName,
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
    )
}
