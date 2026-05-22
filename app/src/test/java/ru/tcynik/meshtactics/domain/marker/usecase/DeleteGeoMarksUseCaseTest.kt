package ru.tcynik.meshtactics.domain.marker.usecase

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository

class DeleteGeoMarksUseCaseTest {

    private val repository: GeoMarkRepository = mockk(relaxed = true)
    private val useCase = DeleteGeoMarksUseCase(repository)

    @Test
    fun `delegates delete for each id`() = runTest {
        useCase(listOf("a", "b"))

        coVerify(exactly = 1) { repository.deleteById("a") }
        coVerify(exactly = 1) { repository.deleteById("b") }
    }
}
