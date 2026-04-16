package ru.tcynik.meshtactics.domain.map.usecase

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tcynik.meshtactics.domain.map.model.ImportedMapOverlay
import ru.tcynik.meshtactics.domain.map.repository.ImportedMapRepository

class ObserveImportedMapsUseCaseTest {

    private val repository: ImportedMapRepository = mockk()
    private val useCase = ObserveImportedMapsUseCase(repository)

    @Test
    fun `invoke delegates to repository observeAll`() = runTest {
        val maps = listOf(
            ImportedMapOverlay(id = "1", name = "test.kmz", uri = "content://test", createdAt = 1000L, isSelected = false),
        )
        every { repository.observeAll() } returns flowOf(maps)

        useCase().test {
            assertEquals(maps, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty list emitted when repository returns no items`() = runTest {
        every { repository.observeAll() } returns flowOf(emptyList())

        useCase().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple emissions are forwarded in order`() = runTest {
        val first = listOf(ImportedMapOverlay("1", "a.kmz", "uri://1", 1000L, false))
        val second = listOf(
            ImportedMapOverlay("1", "a.kmz", "uri://1", 1000L, true),
            ImportedMapOverlay("2", "b.kml", "uri://2", 2000L, false),
        )
        every { repository.observeAll() } returns kotlinx.coroutines.flow.flow {
            emit(first)
            emit(second)
        }

        useCase().test {
            assertEquals(first, awaitItem())
            assertEquals(second, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
