package ru.tcynik.meshtactics.domain.map.usecase

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.tcynik.meshtactics.domain.map.model.MapCameraPosition
import ru.tcynik.meshtactics.domain.map.repository.LastMapPositionRepository

class GetLastMapPositionUseCaseTest {

    private val repository: LastMapPositionRepository = mockk()
    private val useCase = GetLastMapPositionUseCase(repository)

    @Test
    fun `returns null when repository has no saved position`() {
        every { repository.get() } returns null

        val result = useCase()

        assertNull(result)
    }

    @Test
    fun `returns saved position from repository`() {
        val saved = MapCameraPosition(lat = 56.0184, lon = 92.8672, zoom = 12.0)
        every { repository.get() } returns saved

        val result = useCase()

        assertEquals(saved, result)
    }
}
