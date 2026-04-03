package ru.tcynik.meshtactics.domain.map.usecase

import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import ru.tcynik.meshtactics.domain.map.model.MapCameraPosition
import ru.tcynik.meshtactics.domain.map.repository.LastMapPositionRepository

class SaveLastMapPositionUseCaseTest {

    private val repository: LastMapPositionRepository = mockk()
    private val useCase = SaveLastMapPositionUseCase(repository)

    @Test
    fun `delegates save to repository with correct position`() {
        val position = MapCameraPosition(lat = 51.5074, lon = -0.1278, zoom = 14.0)
        justRun { repository.save(position) }

        useCase(position)

        verify(exactly = 1) { repository.save(position) }
    }

    @Test
    fun `saves each call independently`() {
        val first  = MapCameraPosition(lat = 55.75, lon = 37.61, zoom = 10.0)
        val second = MapCameraPosition(lat = 56.01, lon = 92.86, zoom = 12.0)
        justRun { repository.save(any()) }

        useCase(first)
        useCase(second)

        verify(exactly = 1) { repository.save(first) }
        verify(exactly = 1) { repository.save(second) }
    }
}
