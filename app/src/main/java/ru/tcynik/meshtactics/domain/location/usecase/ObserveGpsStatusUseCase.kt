package ru.tcynik.meshtactics.domain.location.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.domain.location.model.GpsRawStatus
import ru.tcynik.meshtactics.domain.location.model.GpsSignalLevel
import ru.tcynik.meshtactics.domain.location.model.GpsStatusModel
import ru.tcynik.meshtactics.domain.location.repository.GpsStatusRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

// Minimum number of satellites used in fix to reach Strong level.
private const val MIN_SATELLITES_STRONG = 4

// Maximum horizontal accuracy (metres) to reach Strong level.
private const val MAX_ACCURACY_STRONG_METERS = 50f

class ObserveGpsStatusUseCase(
    private val repository: GpsStatusRepository,
) : FlowUseCase<NoParams, GpsStatusModel>() {

    override fun invoke(params: NoParams): Flow<GpsStatusModel> =
        repository.observeRaw().map { raw -> raw.toStatusModel() }

    private fun GpsRawStatus.toStatusModel(): GpsStatusModel = GpsStatusModel(
        satelliteCount = satelliteCount,
        accuracyMeters = accuracyMeters,
        signalLevel = classify(satelliteCount, accuracyMeters),
    )

    private fun classify(satellites: Int, accuracy: Float?): GpsSignalLevel {
        if (accuracy == null || satellites < 1) return GpsSignalLevel.None
        if (satellites < MIN_SATELLITES_STRONG || accuracy > MAX_ACCURACY_STRONG_METERS) return GpsSignalLevel.Weak
        return GpsSignalLevel.Strong
    }
}
