package ru.tcynik.meshtactics.domain.marker.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class AutoExpireGeoMarksUseCase(
    private val observeGeoMarks: ObserveGeoMarksUseCase,
    private val deleteExpiredGeoMarks: DeleteExpiredGeoMarksUseCase,
) {
    fun observe(): Flow<Unit> = observeGeoMarks(NoParams)
        .map { marks -> marks.mapNotNull { it.expiresAt }.minOrNull() }
        .distinctUntilChanged()
        .transformLatest { nearestExpiry ->
            if (nearestExpiry != null) {
                val delayMs = (nearestExpiry * 1000L - System.currentTimeMillis()).coerceAtLeast(0L)
                delay(delayMs)
                deleteExpiredGeoMarks(NoParams)
            }
            emit(Unit)
        }
}
