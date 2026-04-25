package ru.tcynik.meshtactics.domain.channel.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveContoursUseCase(
    private val repository: ContourRepository,
) : FlowUseCase<NoParams, List<Contour>>() {
    override fun invoke(params: NoParams): Flow<List<Contour>> =
        repository.observeContours()
}
