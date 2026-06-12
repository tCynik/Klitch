package ru.tcynik.klitch.domain.marker.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.tcynik.klitch.domain.usecase.base.NoParams

private const val CHECK_INTERVAL_MS = 60_000L

class AutoExpireGeoMarksUseCase(
    private val deleteExpiredGeoMarks: DeleteExpiredGeoMarksUseCase,
) {
    fun observe(): Flow<Unit> = flow {
        while (true) {
            deleteExpiredGeoMarks(NoParams)
            emit(Unit)
            delay(CHECK_INTERVAL_MS)
        }
    }
}
