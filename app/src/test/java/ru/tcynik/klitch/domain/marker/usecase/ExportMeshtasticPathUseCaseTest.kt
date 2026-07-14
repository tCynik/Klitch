package ru.tcynik.klitch.domain.marker.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tcynik.klitch.domain.marker.repository.MeshtasticPathFileRepository

class ExportMeshtasticPathUseCaseTest {

    private val repository: MeshtasticPathFileRepository = mockk()
    private val useCase = ExportMeshtasticPathUseCase(repository)

    @Test
    fun `delegates to repository and returns its result`() = runTest {
        coEvery { repository.export("m1", "content://dest") } returns Result.success(Unit)

        val result = useCase("m1", "content://dest")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.export("m1", "content://dest") }
    }

    @Test
    fun `propagates repository failure`() = runTest {
        val error = IllegalStateException("Not a track")
        coEvery { repository.export("m1", "content://dest") } returns Result.failure(error)

        val result = useCase("m1", "content://dest")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() === error)
    }
}
