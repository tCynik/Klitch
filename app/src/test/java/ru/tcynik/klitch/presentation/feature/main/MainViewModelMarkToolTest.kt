package ru.tcynik.klitch.presentation.feature.main

import android.os.SystemClock
import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.klitch.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.klitch.domain.marker.model.GeoMarkFormPreferences
import ru.tcynik.klitch.domain.marker.model.GeoMarkShape
import ru.tcynik.klitch.domain.marker.model.GeoMarkType
import ru.tcynik.klitch.domain.marker.repository.GeoMarkPreferencesRepository
import ru.tcynik.klitch.domain.marker.usecase.AutoExpireGeoMarksUseCase
import ru.tcynik.klitch.domain.marker.usecase.DeleteGeoMarksUseCase
import ru.tcynik.klitch.domain.marker.usecase.IngestReceivedGeoMarksUseCase
import ru.tcynik.klitch.domain.marker.usecase.ObserveGeoMarksUseCase
import ru.tcynik.klitch.domain.marker.usecase.SendGeoMarkUseCase
import ru.tcynik.klitch.domain.marker.usecase.ToggleGeoMarkVisibilityUseCase
import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.klitch.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.klitch.presentation.feature.main.osd.models.DraftPointContextMenuEvent

/** Step between [SystemClock.uptimeMillis] calls — must exceed [GeoMarkViewModel] 80 ms tap dedupe. */
private const val MOCK_UPTIME_STEP_MS = 100L

class GeoMarkViewModelMarkToolTest {

    private val observeGeoMarks: ObserveGeoMarksUseCase = mockk()
    private val ingestReceivedGeoMarks: IngestReceivedGeoMarksUseCase = mockk()
    private val autoExpireGeoMarks: AutoExpireGeoMarksUseCase = mockk(relaxed = true)
    private val observeContours: ObserveContoursUseCase = mockk()
    private val observeConnectionStatus: ObserveConnectionStatusUseCase = mockk()
    private val toggleGeoMarkVisibility: ToggleGeoMarkVisibilityUseCase = mockk(relaxed = true)
    private val deleteGeoMarks: DeleteGeoMarksUseCase = mockk(relaxed = true)
    private val sendGeoMark: SendGeoMarkUseCase = mockk(relaxed = true)
    private val geoMarkPrefsRepository: GeoMarkPreferencesRepository = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: GeoMarkViewModel
    private var mockUptimeMs = 0L

    @Before
    fun setUp() {
        mockkStatic(SystemClock::class)
        mockUptimeMs = 0L
        every { SystemClock.uptimeMillis() } answers {
            mockUptimeMs += MOCK_UPTIME_STEP_MS
            mockUptimeMs
        }
        Dispatchers.setMain(testDispatcher)
        every { observeGeoMarks.invoke(any()) } returns flowOf(emptyList())
        every { ingestReceivedGeoMarks.observe() } returns flowOf(Unit)
        every { autoExpireGeoMarks.observe() } returns flowOf(Unit)
        every { observeContours.invoke(any()) } returns flowOf(emptyList())
        every { observeConnectionStatus.invoke(any()) } returns flowOf(MeshConnectionStatus.Disconnected)
        every { geoMarkPrefsRepository.observePreferences() } returns flowOf(GeoMarkFormPreferences())
        every { geoMarkPrefsRepository.observePresets() } returns flowOf(emptyList())
        viewModel = GeoMarkViewModel(
            observeGeoMarks = observeGeoMarks,
            ingestReceivedGeoMarks = ingestReceivedGeoMarks,
            autoExpireGeoMarks = autoExpireGeoMarks,
            observeContours = observeContours,
            observeConnectionStatus = observeConnectionStatus,
            toggleGeoMarkVisibility = toggleGeoMarkVisibility,
            deleteGeoMarks = deleteGeoMarks,
            sendGeoMark = sendGeoMark,
            geoMarkPrefsRepository = geoMarkPrefsRepository,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(SystemClock::class)
        Dispatchers.resetMain()
    }

    // ── toggleMarkTool ────────────────────────────────────────────────────────

    @Test
    fun `toggleMarkTool — activates mark tool`() {
        viewModel.toggleMarkTool()
        assertTrue(viewModel.uiState.value.markToolActive)
    }

    @Test
    fun `toggleMarkTool twice — deactivates mark tool`() {
        viewModel.toggleMarkTool()
        viewModel.toggleMarkTool()
        assertFalse(viewModel.uiState.value.markToolActive)
    }

    @Test
    fun `toggleMarkTool deactivate — clears pending mark points`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapClick(55.75, 37.62, 0f, 0f)
        assertEquals(1, viewModel.uiState.value.pendingMarkPoints.size)

        viewModel.toggleMarkTool()
        assertTrue(viewModel.uiState.value.pendingMarkPoints.isEmpty())
    }

    // ── onMapClick ────────────────────────────────────────────────────────────

    @Test
    fun `onMapClick when tool inactive — does not add pending point`() = runTest(testDispatcher) {
        assertFalse(viewModel.uiState.value.markToolActive)
        viewModel.onMapClick(55.75, 37.62, 0f, 0f)
        assertTrue(viewModel.uiState.value.pendingMarkPoints.isEmpty())
    }

    @Test
    fun `onMapClick when tool active — adds pending point`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapClick(55.75, 37.62, 0f, 0f)
        assertEquals(1, viewModel.uiState.value.pendingMarkPoints.size)
    }

    @Test
    fun `onMapClick — preserves tap coordinates in pending point`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapClick(55.750, 37.620, 0f, 0f)
        val pt = viewModel.uiState.value.pendingMarkPoints.first()
        assertEquals(55.750, pt.latitude, 1e-9)
        assertEquals(37.620, pt.longitude, 1e-9)
    }

    @Test
    fun `onMapClick multiple times — accumulates points after each tap`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setMarkType(GeoMarkType.TRACK)
        viewModel.onMapClick(55.750, 37.620, 0f, 0f)
        viewModel.onMapClick(55.751, 37.621, 0f, 0f)
        val marksSize = viewModel.uiState.value.pendingMarkPoints.size
        assertEquals(2, marksSize)
    }

    @Test
    fun `setMarkType TRACK to POINT — keeps only last vertex as point draft`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setMarkType(GeoMarkType.TRACK)
        viewModel.onMapClick(55.750, 37.620, 0f, 0f)
        viewModel.onMapClick(55.751, 37.621, 0f, 0f)
        viewModel.setMarkType(GeoMarkType.POINT)
        val draft = viewModel.uiState.value.pendingMarkPoints
        assertEquals(1, draft.size)
        assertEquals(55.751, draft.first().latitude, 1e-9)
        assertEquals(37.621, draft.first().longitude, 1e-9)
    }

    @Test
    fun `onMapClick — after TRACK to POINT switch replaces draft with single tap`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setMarkType(GeoMarkType.TRACK)
        viewModel.onMapClick(55.750, 37.620, 0f, 0f)
        viewModel.onMapClick(55.751, 37.621, 0f, 0f)
        viewModel.setMarkType(GeoMarkType.POINT)
        viewModel.onMapClick(55.752, 37.622, 0f, 0f)
        val draft = viewModel.uiState.value.pendingMarkPoints
        assertEquals(1, draft.size)
        assertEquals(55.752, draft.first().latitude, 1e-9)
        assertEquals(37.622, draft.first().longitude, 1e-9)
    }

    // ── onMapDoubleClick ──────────────────────────────────────────────────────

    @Test
    fun `onMapDoubleClick when tool inactive — does not send`() = runTest(testDispatcher) {
        assertFalse(viewModel.uiState.value.markToolActive)
        viewModel.onMapDoubleClick(55.75, 37.62)
        coVerify(exactly = 0) { sendGeoMark(any()) }
    }

    @Test
    fun `onMapDoubleClick clears draft from preceding single tap`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapClick(55.75, 37.62, 0f, 0f)
        viewModel.onMapDoubleClick(55.75, 37.62)
        assertTrue(viewModel.uiState.value.pendingMarkPoints.isEmpty())
    }

    @Test
    fun `onMapDoubleClick sends a POINT mark via sendGeoMark`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapDoubleClick(55.75, 37.62)
        coVerify(exactly = 1) { sendGeoMark(match { it.mark.type == GeoMarkType.POINT }) }
    }

    @Test
    fun `onMapDoubleClick TRACK — appends tap point then sends all vertices`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setMarkType(GeoMarkType.TRACK)
        viewModel.onMapClick(55.750, 37.620, 0f, 0f)
        viewModel.onMapClick(55.751, 37.621, 0f, 0f)
        viewModel.onMapDoubleClick(55.752, 37.622)
        coVerify(exactly = 1) {
            sendGeoMark(match {
                it.mark.type == GeoMarkType.TRACK &&
                    it.mark.points.size == 3 &&
                    it.mark.points[2].latitude == 55.752
            })
        }
        assertTrue(viewModel.uiState.value.pendingMarkPoints.isEmpty())
    }

    @Test
    fun `onMapDoubleClick TRACK with no pending points — does not send, tap point stays in draft`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setMarkType(GeoMarkType.TRACK)
        viewModel.onMapDoubleClick(55.750, 37.620)
        coVerify(exactly = 0) { sendGeoMark(any()) }
        assertEquals(1, viewModel.uiState.value.pendingMarkPoints.size)
    }

    @Test
    fun `onMapDoubleClick TRACK with one pending point — adds second and sends`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setMarkType(GeoMarkType.TRACK)
        viewModel.onMapClick(55.750, 37.620, 0f, 0f)
        viewModel.onMapDoubleClick(55.751, 37.621)
        coVerify(exactly = 1) {
            sendGeoMark(match {
                it.mark.type == GeoMarkType.TRACK && it.mark.points.size == 2
            })
        }
        assertTrue(viewModel.uiState.value.pendingMarkPoints.isEmpty())
    }

    @Test
    fun `onMapDoubleClick — uses form color shape and tap coordinates`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setMarkColor(3)
        viewModel.setMarkShape(GeoMarkShape.TRIANGLE)
        viewModel.onMapDoubleClick(55.750, 37.620)
        coVerify(exactly = 1) {
            sendGeoMark(match {
                it.mark.type == GeoMarkType.POINT &&
                    it.mark.color == 3 &&
                    it.mark.shape == GeoMarkShape.TRIANGLE &&
                    it.mark.points.single().let { p ->
                        p.latitude == 55.750 && p.longitude == 37.620
                    }
            })
        }
    }

    // ── onMapLongClick ────────────────────────────────────────────────────────

    @Test
    fun `onMapLongClick when tool inactive — emits no context menu event`() = runTest(testDispatcher) {
        viewModel.contextMenuEvent.test {
            viewModel.onMapLongClick(55.75, 37.62, 100f, 200f)
            expectNoEvents()
            cancel()
        }
    }

    @Test
    fun `onMapLongClick near pending point — emits context menu event with correct index`() =
        runTest(testDispatcher) {
            viewModel.toggleMarkTool()
            viewModel.onMapClick(55.750, 37.620, 0f, 0f)

            viewModel.contextMenuEvent.test {
                // Long-tap within ~10m of the pending point
                viewModel.onMapLongClick(55.7501, 37.6201, 150f, 250f)
                val event = awaitItem() as DraftPointContextMenuEvent
                assertEquals(0, event.pointIndex)
                assertEquals(150f, event.screenX)
                assertEquals(250f, event.screenY)
                cancel()
            }
        }

    @Test
    fun `onMapLongClick far from pending point — emits no context menu event`() =
        runTest(testDispatcher) {
            viewModel.toggleMarkTool()
            viewModel.onMapClick(55.750, 37.620, 0f, 0f)

            var received = false
            val collectJob = backgroundScope.launch {
                viewModel.contextMenuEvent.collect { received = true }
            }
            viewModel.onMapLongClick(55.760, 37.630, 100f, 200f)
            runCurrent()
            assertFalse(received)
            collectJob.cancel()
        }

    // ── sendPendingMark ───────────────────────────────────────────────────────

    @Test
    fun `sendPendingMark — clears pending points`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapClick(55.750, 37.620, 0f, 0f)
        assertEquals(1, viewModel.uiState.value.pendingMarkPoints.size)

        viewModel.sendPendingMark()
        assertTrue(viewModel.uiState.value.pendingMarkPoints.isEmpty())
    }

    @Test
    fun `sendPendingMark single point — sends POINT type`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapClick(55.750, 37.620, 0f, 0f)

        viewModel.sendPendingMark()
        coVerify(exactly = 1) { sendGeoMark(match { it.mark.type == GeoMarkType.POINT }) }
    }

    @Test
    fun `sendPendingMark two points — sends TRACK type`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setMarkType(GeoMarkType.TRACK)
        viewModel.onMapClick(55.750, 37.620, 0f, 0f)
        viewModel.onMapClick(55.751, 37.621, 0f, 0f)

        viewModel.sendPendingMark()
        coVerify(exactly = 1) { sendGeoMark(match { it.mark.type == GeoMarkType.TRACK }) }
    }

    @Test
    fun `sendPendingMark when empty — does not call sendGeoMark`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.sendPendingMark()
        coVerify(exactly = 0) { sendGeoMark(any()) }
    }

    @Test
    fun `clear name counter — sends mark without number and keeps counter empty`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setNameCounter(null)
        viewModel.onMapClick(55.750, 37.620, 0f, 0f)

        viewModel.sendPendingMark()

        coVerify(exactly = 1) { sendGeoMark(match { it.mark.name == "точка" }) }
        assertEquals(null, viewModel.geoMarksSheetUiState.value.nameCounter)
    }

    @Test
    fun `send with name counter — increments counter for next mark`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapClick(55.750, 37.620, 0f, 0f)

        viewModel.sendPendingMark()

        assertEquals(2, viewModel.geoMarksSheetUiState.value.nameCounter)
    }

    // ── deletePendingPoint ────────────────────────────────────────────────────

    @Test
    fun `deletePendingPoint — removes point at specified index`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setMarkType(GeoMarkType.TRACK)
        viewModel.onMapClick(55.750, 37.620, 0f, 0f)
        viewModel.onMapClick(55.751, 37.621, 0f, 0f)
        assertEquals(2, viewModel.uiState.value.pendingMarkPoints.size)

        viewModel.deletePendingPoint(0)

        val remaining = viewModel.uiState.value.pendingMarkPoints
        assertEquals(1, remaining.size)
        assertEquals(55.751, remaining[0].latitude, 1e-9)
    }

    @Test
    fun `deletePendingPoint last point — list becomes empty`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapClick(55.750, 37.620, 0f, 0f)

        viewModel.deletePendingPoint(0)
        assertTrue(viewModel.uiState.value.pendingMarkPoints.isEmpty())
    }
}
