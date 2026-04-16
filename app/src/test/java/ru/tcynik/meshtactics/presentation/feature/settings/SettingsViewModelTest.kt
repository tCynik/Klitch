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
import ru.tcynik.meshtactics.domain.settings.repository.MarkerSettingsRepository

class SettingsViewModelTest {

    private val repository: MarkerSettingsRepository = mockk(relaxed = true)
    private val observeImportedMaps: ObserveImportedMapsUseCase = mockk()
    private val importMapFile: ImportMapFileUseCase = mockk(relaxed = true)
    private val hideImportedMap: HideImportedMapUseCase = mockk(relaxed = true)
    private val deleteImportedMap: DeleteImportedMapUseCase = mockk(relaxed = true)
    private val toggleImportedMapSelection: ToggleImportedMapSelectionUseCase = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getMarkerSizeLevel() } returns 5
        every { observeImportedMaps() } returns flowOf(emptyList())
        viewModel = SettingsViewModel(
            repository = repository,
            observeImportedMaps = observeImportedMaps,
            importMapFile = importMapFile,
            hideImportedMap = hideImportedMap,
            deleteImportedMap = deleteImportedMap,
            toggleImportedMapSelection = toggleImportedMapSelection,
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
}
