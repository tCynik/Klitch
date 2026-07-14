package ru.tcynik.klitch.domain.track.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tcynik.klitch.domain.track.repository.TrackFileRepository

class ExportTrackUseCaseTest {

    private val repository: TrackFileRepository = mockk()
    private val useCase = ExportTrackUseCase(repository)

    @Test
    fun `delegates to repository and returns its result`() = runTest {
        coEvery { repository.export("t1", "content://dest") } returns Result.success(Unit)

        val result = useCase("t1", "content://dest")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.export("t1", "content://dest") }
    }

    @Test
    fun `propagates repository failure`() = runTest {
        val error = IllegalStateException("Cannot export an unfinished track")
        coEvery { repository.export("t1", "content://dest") } returns Result.failure(error)

        val result = useCase("t1", "content://dest")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() === error)
    }
}
