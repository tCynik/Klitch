package ru.tcynik.klitch.domain.track.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.tcynik.klitch.domain.track.model.RecordedTrack
import ru.tcynik.klitch.domain.track.repository.TrackFileRepository

class ImportTrackUseCaseTest {

    private val repository: TrackFileRepository = mockk()
    private val useCase = ImportTrackUseCase(repository)

    @Test
    fun `delegates to repository and returns imported track`() = runTest {
        val imported = RecordedTrack(
            id = "new-id", name = "Imported", startedAt = 100, finishedAt = 100,
            totalDistanceMeters = 42.0, color = 0, isVisible = true, hasTimestamps = false,
        )
        coEvery { repository.import("content://source") } returns Result.success(imported)

        val result = useCase("content://source")

        assertEquals(imported, result.getOrNull())
        coVerify(exactly = 1) { repository.import("content://source") }
    }
}
