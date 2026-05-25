package ru.tcynik.meshtactics.domain.marker.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository

class ToggleGeoMarkVisibilityUseCaseTest {

    private val repository: GeoMarkRepository = mockk(relaxed = true)
    private val useCase = ToggleGeoMarkVisibilityUseCase(repository)

    @Test
    fun `delegates visibility toggle to repository`() = runTest {
        coEvery { repository.toggleVisibility("mark-1", false) } returns Unit

        useCase("mark-1", false)

        coVerify(exactly = 1) { repository.toggleVisibility("mark-1", false) }
    }
}
