package ru.tcynik.klitch.presentation.feature.main

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.klitch.domain.chat.usecase.IngestReceivedChatMessagesUseCase
import ru.tcynik.klitch.domain.chat.usecase.ObserveTotalUnreadChatCountUseCase
import ru.tcynik.klitch.domain.location.model.GpsStatusModel
import ru.tcynik.klitch.domain.location.usecase.ObserveGpsStatusUseCase
import ru.tcynik.klitch.domain.map.usecase.GetLastMapPositionUseCase
import ru.tcynik.klitch.domain.map.usecase.GetTileUrlUseCase
import ru.tcynik.klitch.domain.map.usecase.ObserveNodeMarkersUseCase
import ru.tcynik.klitch.domain.map.usecase.ObserveSelectedOverlaysUseCase
import ru.tcynik.klitch.domain.map.usecase.SaveLastMapPositionUseCase
import ru.tcynik.klitch.domain.settings.usecase.GetGeoMarkSizeLevelUseCase
import ru.tcynik.klitch.domain.settings.usecase.GetMarkerSizeLevelUseCase
import ru.tcynik.klitch.domain.settings.usecase.GetShowGeoMarkNamesUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveGeoMarkSizeLevelUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveMarkerSizeLevelUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveShowGeoMarkNamesUseCase
import ru.tcynik.klitch.domain.track.model.TrackRecordingState
import ru.tcynik.klitch.domain.track.usecase.ObserveRecordedTrackPointsUseCase
import ru.tcynik.klitch.domain.track.usecase.ObserveRecordedTracksUseCase
import ru.tcynik.klitch.domain.track.usecase.ObserveTrackRecordingStateUseCase
import ru.tcynik.klitch.presentation.feature.main.osd.HudStateMapper

class MainViewModelMenuDrawerTest {

    private val getTileUrl: GetTileUrlUseCase = mockk()
    private val getLastPosition: GetLastMapPositionUseCase = mockk()
    private val saveLastPosition: SaveLastMapPositionUseCase = mockk(relaxed = true)
    private val observeNodeMarkers: ObserveNodeMarkersUseCase = mockk()
    private val observeGpsStatus: ObserveGpsStatusUseCase = mockk()
    private val getMarkerSizeLevel: GetMarkerSizeLevelUseCase = mockk()
    private val observeMarkerSizeLevel: ObserveMarkerSizeLevelUseCase = mockk()
    private val getGeoMarkSizeLevel: GetGeoMarkSizeLevelUseCase = mockk()
    private val observeGeoMarkSizeLevel: ObserveGeoMarkSizeLevelUseCase = mockk()
    private val getShowGeoMarkNames: GetShowGeoMarkNamesUseCase = mockk()
    private val observeShowGeoMarkNames: ObserveShowGeoMarkNamesUseCase = mockk()
    private val observeSelectedOverlays: ObserveSelectedOverlaysUseCase = mockk()
    private val observeTotalUnreadChatCount: ObserveTotalUnreadChatCountUseCase = mockk()
    private val ingestReceivedChatMessages: IngestReceivedChatMessagesUseCase = mockk()
    private val observeRecordedTracks: ObserveRecordedTracksUseCase = mockk()
    private val observeRecordedTrackPoints: ObserveRecordedTrackPointsUseCase = mockk()
    private val observeTrackRecordingState: ObserveTrackRecordingStateUseCase = mockk()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getTileUrl.invoke() } returns ""
        every { getLastPosition.invoke() } returns null
        every { observeNodeMarkers.invoke(any()) } returns flowOf(emptyList())
        every { observeGpsStatus.invoke(any()) } returns flowOf(GpsStatusModel.None)
        every { getMarkerSizeLevel.invoke() } returns 5
        every { observeMarkerSizeLevel.invoke(any()) } returns flowOf(5)
        every { getGeoMarkSizeLevel.invoke() } returns 5
        every { observeGeoMarkSizeLevel.invoke(any()) } returns flowOf(5)
        every { getShowGeoMarkNames.invoke() } returns false
        every { observeShowGeoMarkNames.invoke(any()) } returns flowOf(false)
        every { observeSelectedOverlays.invoke(any()) } returns flowOf(emptyList())
        every { observeTotalUnreadChatCount.invoke(any()) } returns flowOf(0)
        every { ingestReceivedChatMessages.observe() } returns flowOf(Unit)
        every { observeRecordedTracks.invoke(any()) } returns flowOf(emptyList())
        every { observeRecordedTrackPoints.invoke(any()) } returns flowOf(emptyList())
        every { observeTrackRecordingState.invoke(any()) } returns flowOf(TrackRecordingState.Idle)
        viewModel = MainViewModel(
            getTileUrl = getTileUrl,
            getLastPosition = getLastPosition,
            saveLastPosition = saveLastPosition,
            observeNodeMarkers = observeNodeMarkers,
            observeGpsStatus = observeGpsStatus,
            getMarkerSizeLevel = getMarkerSizeLevel,
            observeMarkerSizeLevel = observeMarkerSizeLevel,
            getGeoMarkSizeLevel = getGeoMarkSizeLevel,
            observeGeoMarkSizeLevel = observeGeoMarkSizeLevel,
            getShowGeoMarkNames = getShowGeoMarkNames,
            observeShowGeoMarkNames = observeShowGeoMarkNames,
            observeSelectedOverlays = observeSelectedOverlays,
            observeTotalUnreadChatCount = observeTotalUnreadChatCount,
            ingestReceivedChatMessages = ingestReceivedChatMessages,
            observeRecordedTracks = observeRecordedTracks,
            observeRecordedTrackPoints = observeRecordedTrackPoints,
            observeTrackRecordingState = observeTrackRecordingState,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── toggleMenuDrawer ──────────────────────────────────────────────────────

    @Test
    fun `toggleMenuDrawer — opens drawer`() {
        viewModel.toggleMenuDrawer()
        assertTrue(viewModel.uiState.value.menuDrawerOpen)
    }

    @Test
    fun `toggleMenuDrawer twice — closes drawer`() {
        viewModel.toggleMenuDrawer()
        viewModel.toggleMenuDrawer()
        assertFalse(viewModel.uiState.value.menuDrawerOpen)
    }

    // ── menuDrawerUiState via HudStateMapper ──────────────────────────────────

    @Test
    fun `menuDrawerUiState — isOpen reflects toggle`() = runTest(testDispatcher) {
        assertFalse(HudStateMapper.buildMenuDrawerUiState(viewModel.uiState.value, false, HudNavCallbacks()).isOpen)
        viewModel.toggleMenuDrawer()
        assertTrue(HudStateMapper.buildMenuDrawerUiState(viewModel.uiState.value, false, HudNavCallbacks()).isOpen)
        viewModel.toggleMenuDrawer()
        assertFalse(HudStateMapper.buildMenuDrawerUiState(viewModel.uiState.value, false, HudNavCallbacks()).isOpen)
    }

    // ── drawer item onClick ───────────────────────────────────────────────────

    @Test
    fun `drawer radio onClick — invokes nav callback and closes drawer`() {
        var radioCalled = false
        val nav = HudNavCallbacks(
            onRadioClick = { radioCalled = true },
            onToggleMenuDrawer = { viewModel.toggleMenuDrawer() },
        )
        viewModel.toggleMenuDrawer()
        assertTrue(viewModel.uiState.value.menuDrawerOpen)

        val menuState = HudStateMapper.buildMenuDrawerUiState(viewModel.uiState.value, false, nav)
        menuState.items.first { it.label == "радио" }.onClick()

        assertTrue(radioCalled)
        assertFalse(viewModel.uiState.value.menuDrawerOpen)
    }

    @Test
    fun `drawer main settings onClick — invokes nav callback and closes drawer`() {
        var settingsCalled = false
        val nav = HudNavCallbacks(
            onMainSettingsClick = { settingsCalled = true },
            onToggleMenuDrawer = { viewModel.toggleMenuDrawer() },
        )
        viewModel.toggleMenuDrawer()
        assertTrue(viewModel.uiState.value.menuDrawerOpen)

        val menuState = HudStateMapper.buildMenuDrawerUiState(viewModel.uiState.value, false, nav)
        menuState.items.first { it.label == "Главная" }.onClick()

        assertTrue(settingsCalled)
        assertFalse(viewModel.uiState.value.menuDrawerOpen)
    }

    @Test
    fun `drawer onDismiss — closes drawer`() {
        val nav = HudNavCallbacks(
            onToggleMenuDrawer = { viewModel.toggleMenuDrawer() },
        )
        viewModel.toggleMenuDrawer()
        assertTrue(viewModel.uiState.value.menuDrawerOpen)

        val menuState = HudStateMapper.buildMenuDrawerUiState(viewModel.uiState.value, false, nav)
        menuState.onDismiss()

        assertFalse(viewModel.uiState.value.menuDrawerOpen)
    }
}
