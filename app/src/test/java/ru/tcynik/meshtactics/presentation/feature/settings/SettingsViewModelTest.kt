package ru.tcynik.meshtactics.presentation.feature.settings

import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.domain.map.usecase.DeleteImportedMapUseCase
import ru.tcynik.meshtactics.domain.map.usecase.HideImportedMapUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ImportMapFileUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveImportedMapsUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ToggleImportedMapSelectionUseCase
import ru.tcynik.meshtactics.domain.settings.model.ScreenOrientationMode
import ru.tcynik.meshtactics.domain.settings.model.TileCacheMode
import ru.tcynik.meshtactics.domain.settings.repository.MarkerSettingsRepository
import ru.tcynik.meshtactics.domain.settings.usecase.GetScreenOrientationLockedUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetScreenOrientationModeUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetTileCacheModeUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveTileCacheModeUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.SetScreenOrientationLockedUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.SetScreenOrientationModeUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.SetTileCacheModeUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class SettingsViewModelTest {

    private val repository: MarkerSettingsRepository = mockk(relaxed = true)
    private val observeImportedMaps: ObserveImportedMapsUseCase = mockk()
    private val importMapFile: ImportMapFileUseCase = mockk(relaxed = true)
    private val hideImportedMap: HideImportedMapUseCase = mockk(relaxed = true)
    private val deleteImportedMap: DeleteImportedMapUseCase = mockk(relaxed = true)
    private val toggleImportedMapSelection: ToggleImportedMapSelectionUseCase = mockk(relaxed = true)
    private val getTileCacheMode: GetTileCacheModeUseCase = mockk()
    private val observeTileCacheMode: ObserveTileCacheModeUseCase = mockk()
    private val setTileCacheMode: SetTileCacheModeUseCase = mockk(relaxed = true)
    private val getScreenOrientationLocked: GetScreenOrientationLockedUseCase = mockk()
    private val getScreenOrientationMode: GetScreenOrientationModeUseCase = mockk()
    private val setScreenOrientationLocked: SetScreenOrientationLockedUseCase = mockk(relaxed = true)
    private val setScreenOrientationMode: SetScreenOrientationModeUseCase = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getMarkerSizeLevel() } returns 5
        every { repository.getGeoMarkSizeLevel() } returns 5
        every { observeImportedMaps() } returns flowOf(emptyList())
        every { getTileCacheMode() } returns TileCacheMode.DEFAULT
        every { observeTileCacheMode(NoParams) } returns flowOf(TileCacheMode.DEFAULT)
        every { getScreenOrientationLocked() } returns false
        every { getScreenOrientationMode() } returns ScreenOrientationMode.PORTRAIT
        viewModel = SettingsViewModel(
            repository = repository,
            observeImportedMaps = observeImportedMaps,
            importMapFile = importMapFile,
            hideImportedMap = hideImportedMap,
            deleteImportedMap = deleteImportedMap,
            toggleImportedMapSelection = toggleImportedMapSelection,
            getTileCacheMode = getTileCacheMode,
            observeTileCacheMode = observeTileCacheMode,
            setTileCacheMode = setTileCacheMode,
            getScreenOrientationLocked = getScreenOrientationLocked,
            getScreenOrientationMode = getScreenOrientationMode,
            setScreenOrientationLocked = setScreenOrientationLocked,
            setScreenOrientationMode = setScreenOrientationMode,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Marker size ──────────────────────────────────────────────────────────

    @Test
    fun `initialisation reads level from repository`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(5, state.markerSizeLevel)
            assertEquals(5, state.markerSizeLevelPending)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onMarkerSizeLevelChange updates pending only, not committed level`() = runTest {
        viewModel.onMarkerSizeLevelChange(8)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(5, state.markerSizeLevel)
            assertEquals(8, state.markerSizeLevelPending)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSave commits pending and calls repository setMarkerSizeLevel`() = runTest {
        viewModel.onMarkerSizeLevelChange(7)
        viewModel.onSave()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(7, state.markerSizeLevel)
            assertEquals(7, state.markerSizeLevelPending)
            cancelAndIgnoreRemainingEvents()
        }
        verify(exactly = 1) { repository.setMarkerSizeLevel(7) }
    }

    // ── Map operations ───────────────────────────────────────────────────────

    @Test
    fun `onHideMap delegates to hideImportedMap use case`() = runTest {
        viewModel.onHideMap("map-1")
        coVerify(exactly = 1) { hideImportedMap("map-1") }
    }

    @Test
    fun `onRequestDeleteMap sets deleteConfirmId`() = runTest {
        viewModel.onRequestDeleteMap("map-2")

        viewModel.uiState.test {
            assertEquals("map-2", awaitItem().deleteConfirmId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onConfirmDelete calls deleteImportedMap and clears confirmId`() = runTest {
        viewModel.onRequestDeleteMap("map-3")
        viewModel.onConfirmDelete()

        viewModel.uiState.test {
            assertNull(awaitItem().deleteConfirmId)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { deleteImportedMap("map-3") }
    }

    @Test
    fun `onDismissDeleteDialog clears deleteConfirmId`() = runTest {
        viewModel.onRequestDeleteMap("map-4")
        viewModel.onDismissDeleteDialog()

        viewModel.uiState.test {
            assertNull(awaitItem().deleteConfirmId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onToggleSelection delegates to toggleImportedMapSelection use case`() = runTest {
        viewModel.onToggleSelection("map-5", true)
        coVerify(exactly = 1) { toggleImportedMapSelection("map-5", true) }
    }

    // ── Screen orientation ─────────────────────────────────────────────────────

    @Test
    fun `onOrientationLockedChange updates orientationLockedPending`() = runTest {
        viewModel.onOrientationLockedChange(true)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(true, state.orientationLockedPending)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSave calls orientation use cases with pending values`() = runTest {
        viewModel.onOrientationLockedChange(true)
        viewModel.onOrientationModeChange(ScreenOrientationMode.LANDSCAPE)
        viewModel.onSave()

        verify(exactly = 1) { setScreenOrientationLocked(true) }
        verify(exactly = 1) { setScreenOrientationMode(ScreenOrientationMode.LANDSCAPE) }
    }

    // ── Tile cache mode ──────────────────────────────────────────────────────

    @Test
    fun `initialisation reads tile cache mode via getTileCacheMode`() = runTest {
        viewModel.uiState.test {
            assertEquals(TileCacheMode.DEFAULT, awaitItem().tileCacheMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeTileCacheMode emission updates tileCacheMode in state`() = runTest {
        every { observeTileCacheMode(NoParams) } returns flowOf(TileCacheMode.MONTH)
        viewModel = SettingsViewModel(
            repository = repository,
            observeImportedMaps = observeImportedMaps,
            importMapFile = importMapFile,
            hideImportedMap = hideImportedMap,
            deleteImportedMap = deleteImportedMap,
            toggleImportedMapSelection = toggleImportedMapSelection,
            getTileCacheMode = getTileCacheMode,
            observeTileCacheMode = observeTileCacheMode,
            setTileCacheMode = setTileCacheMode,
            getScreenOrientationLocked = getScreenOrientationLocked,
            getScreenOrientationMode = getScreenOrientationMode,
            setScreenOrientationLocked = setScreenOrientationLocked,
            setScreenOrientationMode = setScreenOrientationMode,
        )

        viewModel.uiState.test {
            assertEquals(TileCacheMode.MONTH, awaitItem().tileCacheMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onTileCacheModeSelected delegates to setTileCacheMode use case`() {
        viewModel.onTileCacheModeSelected(TileCacheMode.MAXIMUM)
        verify(exactly = 1) { setTileCacheMode(TileCacheMode.MAXIMUM) }
    }

    @Test
    fun `onTileCacheModeSelected for each mode calls setTileCacheMode with correct argument`() {
        TileCacheMode.entries.forEach { mode ->
            viewModel.onTileCacheModeSelected(mode)
            verify(exactly = 1) { setTileCacheMode(mode) }
        }
    }
}
