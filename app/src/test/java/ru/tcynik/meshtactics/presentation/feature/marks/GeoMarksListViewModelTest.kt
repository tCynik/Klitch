package ru.tcynik.meshtactics.presentation.feature.marks

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
    private val observeGeoMarks: ObserveGeoMarksUseCase = mockk()
    private val observeContours: ObserveContoursUseCase = mockk()
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
        every { observeGeoMarks.invoke(any()) } returns marksFlow
        every { observeContours.invoke(any()) } returns contoursFlow
        viewModel = GeoMarksListViewModel(
            observeGeoMarks = observeGeoMarks,
            observeContours = observeContours,
            toggleVisibility = toggleVisibility,
            deleteGeoMarks = deleteGeoMarks,
            extendGeoMark = extendGeoMark,
            sendGeoMark = sendGeoMark,
            logger = logger,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `maps marks to list items sorted by createdAt descending`() = runTest {
        val older = makeMark(id = "old", createdAt = 100L, name = "Old")
        val newer = makeMark(id = "new", createdAt = 200L, name = "New")

        viewModel.uiState.test {
            awaitItem()

            marksFlow.value = listOf(older, newer)

            val state = awaitItem()
            assertEquals(2, state.items.size)
            assertEquals("new", state.items[0].id)
            assertEquals("old", state.items[1].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `maps self author to Я and other to short node id`() = runTest {
        val self = makeMark(id = "self", isSelf = true, authorNodeId = "!aaaa1111")
        val remote = makeMark(id = "remote", isSelf = false, authorNodeId = "!bbbb2222")

        viewModel.uiState.test {
            awaitItem()
            marksFlow.value = listOf(self, remote)

            val state = awaitItem()
            assertEquals("Я", state.items.find { it.id == "self" }?.authorLabel)
            assertEquals("!bbbb2", state.items.find { it.id == "remote" }?.authorLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `blank name becomes dash`() = runTest {
        marksFlow.value = listOf(makeMark(id = "blank", name = ""))

        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            assertEquals("—", state.items.single().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `visibility toggle delegates to use case`() = runTest {
        coEvery { toggleVisibility("mark-1", false) } returns Unit

        viewModel.onVisibilityToggle("mark-1", false)

        coVerify(exactly = 1) { toggleVisibility("mark-1", false) }
    }

    @Test
    fun `maps delivery state from isSelf and logicalChannelId`() = runTest {
        marksFlow.value = listOf(
            makeMark(id = "local", isSelf = true, logicalChannelId = "", authorNodeId = ""),
            makeMark(id = "sent", isSelf = true, logicalChannelId = "ch-1", authorNodeId = "!aaaa1111"),
            makeMark(id = "rcv", isSelf = false, logicalChannelId = "ch-2", authorNodeId = "!bbbb2222"),
        )

        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            assertEquals(GeoMarkDeliveryState.LOCAL, state.items.find { it.id == "local" }?.deliveryState)
            assertEquals(GeoMarkDeliveryState.SENT, state.items.find { it.id == "sent" }?.deliveryState)
            assertEquals(GeoMarkDeliveryState.RECEIVED, state.items.find { it.id == "rcv" }?.deliveryState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delivery filters selected for present types on load`() = runTest {
        marksFlow.value = listOf(
            makeMark(id = "local", isSelf = true, logicalChannelId = "", authorNodeId = ""),
            makeMark(id = "rcv", isSelf = false, authorNodeId = "!bbbb2222"),
        )

        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            assertEquals(GeoMarkDeliveryFilterStatus.SELECTED, filterStatus(state, GeoMarkDeliveryState.LOCAL))
            assertEquals(GeoMarkDeliveryFilterStatus.INACTIVE, filterStatus(state, GeoMarkDeliveryState.SENT))
            assertEquals(GeoMarkDeliveryFilterStatus.SELECTED, filterStatus(state, GeoMarkDeliveryState.RECEIVED))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling delivery filter hides marks of that type`() = runTest {
        marksFlow.value = listOf(
            makeMark(id = "local", isSelf = true, logicalChannelId = "", authorNodeId = ""),
            makeMark(id = "rcv", isSelf = false, authorNodeId = "!bbbb2222"),
        )

        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onDeliveryFilterToggle(GeoMarkDeliveryState.LOCAL)

        val state = viewModel.uiState.value
        assertEquals(1, state.items.size)
        assertEquals("rcv", state.items.single().id)
        assertEquals(GeoMarkDeliveryFilterStatus.UNSELECTED, filterStatus(state, GeoMarkDeliveryState.LOCAL))
        assertEquals(GeoMarkDeliveryFilterStatus.SELECTED, filterStatus(state, GeoMarkDeliveryState.RECEIVED))
    }

    @Test
    fun `bulk visibility selects all filtered marks when not all visible`() = runTest {
        marksFlow.value = listOf(
            makeMark(id = "a", isVisible = true),
            makeMark(id = "b", isVisible = false),
        )

        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onToggleAllFilteredVisibility()

        coVerify { toggleVisibility("b", true) }
        coVerify(exactly = 0) { toggleVisibility("a", any()) }
    }

    @Test
    fun `bulk visibility hides all filtered marks when all visible`() = runTest {
        marksFlow.value = listOf(
            makeMark(id = "a", isVisible = true),
            makeMark(id = "b", isVisible = true),
        )

        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onToggleAllFilteredVisibility()

        coVerify { toggleVisibility("a", false) }
        coVerify { toggleVisibility("b", false) }
    }

    @Test
    fun `allFilteredVisible reflects checkbox state of filtered items`() = runTest {
        marksFlow.value = listOf(
            makeMark(id = "a", isVisible = true),
            makeMark(id = "b", isVisible = false),
        )

        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            assertFalse(state.allFilteredVisible)
            assertTrue(state.bulkVisibilityEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteEnabled when at least one visible mark in filter`() = runTest {
        marksFlow.value = listOf(
            makeMark(id = "on", isVisible = true),
            makeMark(id = "off", isVisible = false),
        )

        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            assertTrue(state.deleteEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDeleteClick — single mark confirmation message`() = runTest {
        marksFlow.value = listOf(makeMark(id = "one", name = "Alpha", isSelf = true, authorNodeId = ""))

        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onDeleteClick()

        val confirm = viewModel.uiState.value.deleteConfirm
        assertEquals("Удалить метку Alpha(Я)?", confirm?.message)
        assertEquals(listOf("one"), confirm?.markIds)
    }

    @Test
    fun `onDeleteClick — multiple marks confirmation message`() = runTest {
        marksFlow.value = listOf(
            makeMark(id = "a", isVisible = true),
            makeMark(id = "b", isVisible = true),
        )

        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onDeleteClick()

        assertEquals("Удалить выбранные метки(2)?", viewModel.uiState.value.deleteConfirm?.message)
    }

    @Test
    fun `onConfirmDelete — invokes use case and closes dialog`() = runTest {
        marksFlow.value = listOf(makeMark(id = "del", isVisible = true))

        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onDeleteClick()
        viewModel.onConfirmDelete()

        coVerify { deleteGeoMarks(listOf("del")) }
        assertNull(viewModel.uiState.value.deleteConfirm)
    }

    @Test
    fun `hasMarks true when filters hide all items`() = runTest {
        marksFlow.value = listOf(
            makeMark(id = "local", isSelf = true, logicalChannelId = "", authorNodeId = ""),
        )

        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onDeliveryFilterToggle(GeoMarkDeliveryState.LOCAL)

        val state = viewModel.uiState.value
        assertTrue(state.hasMarks)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `onItemDeleteClick — single mark confirmation`() = runTest {
        marksFlow.value = listOf(makeMark(id = "one", name = "Alpha", isSelf = true, authorNodeId = ""))

        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onItemDeleteClick("one")

        val confirm = viewModel.uiState.value.deleteConfirm
        assertEquals("Удалить метку Alpha(Я)?", confirm?.message)
        assertEquals(listOf("one"), confirm?.markIds)
    }

    @Test
    fun `onItemExtendClick — delegates to use case`() = runTest {
        marksFlow.value = listOf(makeMark(id = "ext"))

        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onItemExtendClick("ext")

        coVerify { extendGeoMark("ext") }
    }

    @Test
    fun `onItemSendClick — opens contour picker with active contours`() = runTest {
        marksFlow.value = listOf(makeMark(id = "send", name = "SendMe"))
        contoursFlow.value = listOf(
            makeContour(id = "ch-1", name = "Alpha"),
            makeContour(id = "ch-2", name = "Beta", isActive = false),
        )

        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onItemSendClick("send")

        val picker = viewModel.uiState.value.sendContourPicker
        assertEquals("send", picker?.markId)
        assertEquals("SendMe", picker?.markName)
        assertEquals(listOf("ch-1", "__local__"), picker?.contours?.map { it.contourId })
    }

    @Test
    fun `onSendContourSelected — sends mark to contour`() = runTest {
        val mark = makeMark(id = "send", isSelf = true)
        marksFlow.value = listOf(mark)
        contoursFlow.value = listOf(makeContour(id = "ch-1", name = "Alpha"))

        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onItemSendClick("send")
        viewModel.onSendContourSelected("ch-1")

        coVerify {
            sendGeoMark(match { params ->
                params.mark.id == "send" && params.contourId == ContourId("ch-1") && !params.localOnly
            })
        }
        assertNull(viewModel.uiState.value.sendContourPicker)
    }

    @Test
    fun `reflects isVisible from domain model`() = runTest {
        marksFlow.value = listOf(
            makeMark(id = "hidden", isVisible = false),
            makeMark(id = "shown", isVisible = true),
        )

        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            assertFalse(state.items.find { it.id == "hidden" }!!.isVisible)
            assertTrue(state.items.find { it.id == "shown" }!!.isVisible)
            cancelAndIgnoreRemainingEvents()
        }
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
}
