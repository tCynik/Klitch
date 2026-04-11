package ru.tcynik.meshtactics.presentation.feature.settings

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.domain.settings.repository.MarkerSettingsRepository

class SettingsViewModelTest {

    private val repository: MarkerSettingsRepository = mockk(relaxed = true)
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        every { repository.getMarkerSizeLevel() } returns 5
        every { repository.markerSizeLevelFlow } returns MutableStateFlow(5)
        viewModel = SettingsViewModel(repository)
    }

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
}
